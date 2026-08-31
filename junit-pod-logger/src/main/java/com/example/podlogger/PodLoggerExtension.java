package com.example.podlogger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.podlogger.client.PodAvailability;
import com.example.podlogger.store.TestRunStore;
import com.example.podlogger.store.dto.TestRunDto;

/**
 * JUnit 5 extension, подключаемый мета-аннотацией {@link PodLogger}.
 *
 * <p>Отвечает только за lifecycle тестового класса и invocation:
 * старт/финиш test run, UTC-окно кейса, счётчики TestWatcher, флаг stand-down.
 * SQL, парсинг dump и Fabric8-вызовы сюда не входят — их делает {@link PodLoggerService}.
 *
 * <p>Порядок хуков (контракт {@code docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md}):
 * <ol>
 *   <li>{@code beforeAll} — applyAnnotation, {@code startTestRun}, publish {@code TestRunStarted},
 *       {@code isPodAvailable}; stand-down → fail-fast класса;</li>
 *   <li>{@code beforeEach} — abort если стенд недоступен, иначе запомнить {@code testStartUtc};</li>
 *   <li>{@code afterEach} — {@code handleAfterEach}; при stand-down на fail выставить флаг,
 *       из хука исключение не бросать (чтобы не затереть исходный assertion);</li>
 *   <li>{@code afterAll} — merge логов прогона, publish {@code TestRunFinished}, {@code finishTestRun}.</li>
 * </ol>
 *
 * <p>Ошибка {@code startTestRun} в {@code beforeAll} — fail-fast с пошаговым SLF4J.
 * Ошибки collect/Allure/save статус текущего теста не меняют.
 */
public class PodLoggerExtension implements BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final Logger log = LoggerFactory.getLogger(PodLoggerExtension.class);

    /** Namespace JUnit Store, чтобы не пересекаться с SpringExtension. */
    static final Namespace STORE_NS = Namespace.create(PodLoggerExtension.class);
    /** UTC-старт текущего invocation. */
    static final String START_KEY = "testStartUtc";
    /** UUID строки {@code test_run}. */
    static final String TEST_RUN_ID_KEY = "testRunId";
    /** {@code true}, если пойман stand-down Event — следующие {@code beforeEach} abort. */
    static final String STAND_UNAVAILABLE_KEY = "standUnavailable";
    /** Код stand-down для текста {@code Stand unavailable: ...}. */
    static final String STAND_UNAVAILABLE_CODE_KEY = "standUnavailableCode";
    /** Счётчик TestWatcher: успешные. */
    static final String PASSED_COUNT_KEY = "passedCount";
    /** Счётчик TestWatcher: failed + aborted. */
    static final String FAILED_COUNT_KEY = "failedCount";
    /** Счётчик TestWatcher: disabled. */
    static final String DISABLED_COUNT_KEY = "disabledCount";
    /** Имя прогона для message {@code TestRunFinished}. */
    static final String TEST_RUN_NAME_KEY = "testRunName";

    /**
     * Старт прогона: metadata из аннотации, запись {@code test_run}, lifecycle Event, probe поды.
     * Stand-down Event при включённом {@link PodLogger#failFastOnStandDownEvent()} бросает
     * {@link IllegalStateException} уже здесь; {@code testRunId} к этому моменту уже в Store,
     * поэтому {@code afterAll} всё равно опубликует {@code TestRunFinished}.
     *
     * @param context class-level ExtensionContext
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        String step = "resolve-metadata";
        try {
            log.info("PodLogger beforeAll: resolving run metadata...");
            PodLogger annotation = annotation(context);
            PodLoggerService loggerService = service(context);
            loggerService.applyAnnotation(annotation);

            step = "open-datasource";
            log.info("PodLogger beforeAll: opening SQLite...");
            TestRunStore testRunStore = testRunStore(context);

            step = "start-test-run";
            Class<?> testClass = context.getRequiredTestClass();
            String runName = loggerService.resolveTestRunName(testClass);
            String suiteName = loggerService.resolveTestSuiteName(testClass);
            String serviceType = loggerService.resolveServiceType();
            log.info("PodLogger beforeAll: startTestRun name={} suite={} env={}",
                    runName, suiteName, annotation.environmentType());
            UUID testRunId = testRunStore.startTestRun(TestRunDto.builder()
                    .testRunName(runName)
                    .testSuiteName(suiteName)
                    .environmentType(annotation.environmentType())
                    .serviceType(serviceType)
                    .namespace(annotation.namespace())
                    .podLabelSelector(annotation.podLabelSelector())
                    .build());

            step = "put-testRunId";
            context.getStore(STORE_NS).put(TEST_RUN_ID_KEY, testRunId);
            context.getStore(STORE_NS).put(TEST_RUN_NAME_KEY, runName);
            log.info("PodLogger beforeAll: testRunId={} started", testRunId);

            step = "publish-started";
            loggerService.publishTestRunStarted(testRunId, runName, suiteName);

            step = "probe-availability";
            PodAvailability availability = loggerService.probeAvailability();
            if (availability != null && availability.isStandDownEventPresent()
                    && annotation.failFastOnStandDownEvent()) {
                context.getStore(STORE_NS).put(STAND_UNAVAILABLE_KEY, Boolean.TRUE);
                context.getStore(STORE_NS).put(STAND_UNAVAILABLE_CODE_KEY, availability.getCode());
                throw new IllegalStateException("Stand unavailable: " + availability.getCode());
            }
            if (availability != null && !availability.isAvailable()) {
                log.warn("PodLogger beforeAll: pod not available (health) code={}", availability.getCode());
            }
        } catch (RuntimeException e) {
            log.error("PodLogger beforeAll FAIL-FAST at step '{}': {}", step, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Финиш прогона: merge persistent+runtime логов, publish {@code TestRunFinished}
     * со счётчиками, {@code finishTestRun}. Если {@code testRunId} нет (упали до {@code put}) —
     * no-op. Каждый шаг в своём try, чтобы один сбой не отменил остальные.
     *
     * @param context class-level ExtensionContext
     */
    @Override
    public void afterAll(ExtensionContext context) {
        UUID testRunId = context.getStore(STORE_NS).get(TEST_RUN_ID_KEY, UUID.class);
        if (testRunId == null) {
            log.warn("PodLogger afterAll: skip because testRunId is missing for {}", context.getDisplayName());
            return;
        }
        log.debug("PodLogger afterAll: start testRunId={} displayName={}", testRunId, context.getDisplayName());
        try {
            log.debug("PodLogger afterAll step=collect-merge testRunId={}", testRunId);
            service(context).collectAndMergeLogsForTestRun(testRunId);
        } catch (Exception e) {
            log.error("PodLogger afterAll collect/merge failed for {}", testRunId, e);
        }
        try {
            int passed = count(context, PASSED_COUNT_KEY);
            int failed = count(context, FAILED_COUNT_KEY);
            int disabled = count(context, DISABLED_COUNT_KEY);
            int total = passed + failed + disabled;
            String runName = context.getStore(STORE_NS).get(TEST_RUN_NAME_KEY, String.class);
            log.debug("PodLogger afterAll step=publish-finished testRunId={} runName={} total={} passed={} failed={} disabled={}",
                    testRunId, runName, total, passed, failed, disabled);
            service(context).publishTestRunFinished(runName, total, passed, failed);
        } catch (Exception e) {
            log.error("PodLogger afterAll publish TestRunFinished failed for {}", testRunId, e);
        }
        try {
            log.debug("PodLogger afterAll step=finish-run testRunId={}", testRunId);
            testRunStore(context).finishTestRun(testRunId);
        } catch (Exception e) {
            log.error("PodLogger afterAll finishTestRun failed for {}", testRunId, e);
        }
    }

    /**
     * Если предыдущий fail поймал stand-down — бросает {@code Stand unavailable: <code>}
     * и тело теста не выполняется. Иначе пишет UTC-старт invocation в Store.
     *
     * @param context method/invocation ExtensionContext
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        Boolean standDown = classStore(context).get(STAND_UNAVAILABLE_KEY, Boolean.class);
        log.debug("PodLogger beforeEach: displayName={} standDown={}", context.getDisplayName(), standDown);
        if (Boolean.TRUE.equals(standDown)) {
            String code = classStore(context).get(STAND_UNAVAILABLE_CODE_KEY, String.class);
            log.debug("PodLogger beforeEach: abort displayName={} code={}", context.getDisplayName(), code);
            throw new IllegalStateException("Stand unavailable: " + code);
        }
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        context.getStore(STORE_NS).put(START_KEY, start);
        log.debug("PodLogger beforeEach: stored {}={} for {}", START_KEY, start, context.getDisplayName());
        log.debug("PodLogger beforeEach: re-applying @PodLogger annotation for {}", context.getDisplayName());
        service(context).applyAnnotation(annotation(context));
    }

    /**
     * Снимает окно кейса и отдаёт его в {@link PodLoggerService#handleAfterEach}.
     * При fail + stand-down Event выставляет {@code STAND_UNAVAILABLE} в class Store,
     * но исключение из этого хука не бросает.
     *
     * @param context method/invocation ExtensionContext
     */
    @Override
    public void afterEach(ExtensionContext context) {
        LocalDateTime start = context.getStore(STORE_NS).get(START_KEY, LocalDateTime.class);
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);
        boolean failed = context.getExecutionException().isPresent();
        UUID testRunId = classStore(context).get(TEST_RUN_ID_KEY, UUID.class);
        log.debug("PodLogger afterEach: displayName={} testRunId={} failed={} window=[{} .. {}]",
                context.getDisplayName(), testRunId, failed, start, end);
        PodAvailability availability = service(context).handleAfterEach(context, testRunId, start, end, failed);
        log.debug("PodLogger afterEach: availability for {} -> {}", context.getDisplayName(), availability);
        if (failed && availability != null && availability.isStandDownEventPresent()
                && annotation(context).failFastOnStandDownEvent()) {
            classStore(context).put(STAND_UNAVAILABLE_KEY, Boolean.TRUE);
            classStore(context).put(STAND_UNAVAILABLE_CODE_KEY, availability.getCode());
            log.debug("PodLogger afterEach: stand-down latched for next tests code={}", availability.getCode());
        }
    }

    /**
     * Увеличивает счётчик успешных тестов для message {@code TestRunFinished}.
     *
     * @param context завершившийся тест
     */
    @Override
    public void testSuccessful(ExtensionContext context) {
        increment(classStore(context), PASSED_COUNT_KEY);
    }

    /**
     * Увеличивает {@code failed} (включая падения из-за {@code STAND_UNAVAILABLE} в {@code beforeEach}).
     *
     * @param context завершившийся тест
     * @param cause   исходная причина fail
     */
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        increment(classStore(context), FAILED_COUNT_KEY);
    }

    /**
     * Aborted считается как failed в message {@code TestRunFinished}.
     *
     * @param context завершившийся тест
     * @param cause   причина abort
     */
    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        increment(classStore(context), FAILED_COUNT_KEY);
    }

    /**
     * Счётчик disabled; входит в {@code total = passed + failed + disabled}.
     *
     * @param context отключённый тест
     * @param reason  причина disabled
     */
    @Override
    public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
        increment(classStore(context), DISABLED_COUNT_KEY);
    }

    /**
     * Атомарно для хука увеличивает integer в Store (отсутствие ключа = 0).
     *
     * @param store class-level Store
     * @param key   ключ счётчика
     */
    private static void increment(ExtensionContext.Store store, String key) {
        Integer current = store.get(key, Integer.class);
        store.put(key, current == null ? 1 : current + 1);
    }

    /**
     * Читает счётчик из class Store; {@code null} трактуется как 0.
     *
     * @param context любой дочерний контекст класса
     * @param key     ключ счётчика
     * @return текущее значение
     */
    private static int count(ExtensionContext context, String key) {
        Integer value = context.getStore(STORE_NS).get(key, Integer.class);
        return value == null ? 0 : value;
    }

    /**
     * Store уровня тестового класса: parent контекста invocation, иначе сам context.
     *
     * @param context текущий хук
     * @return class-level Store в {@link #STORE_NS}
     */
    private static ExtensionContext.Store classStore(ExtensionContext context) {
        return context.getParent().orElse(context).getStore(STORE_NS);
    }

    /**
     * Читает {@link PodLogger} с тестового класса.
     *
     * @param context хук
     * @return аннотация
     * @throws IllegalStateException если аннотации нет
     */
    private static PodLogger annotation(ExtensionContext context) {
        PodLogger annotation = context.getRequiredTestClass().getAnnotation(PodLogger.class);
        if (annotation == null) {
            throw new IllegalStateException("@PodLogger must be present on the test class");
        }
        return annotation;
    }

    /**
     * Достаёт {@link PodLoggerService} из Spring TestContext.
     *
     * @param context хук со SpringExtension
     * @return singleton сервиса
     */
    private static PodLoggerService service(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(PodLoggerService.class);
    }

    /**
     * Достаёт {@link TestRunStore} из Spring TestContext.
     *
     * @param context хук со SpringExtension
     * @return singleton store прогонов
     */
    private static TestRunStore testRunStore(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(TestRunStore.class);
    }
}
