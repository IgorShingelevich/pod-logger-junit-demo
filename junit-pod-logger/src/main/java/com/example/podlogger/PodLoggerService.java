package com.example.podlogger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.allure.LogAllureAttachmentService;
import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.client.PodAvailability;
import com.example.podlogger.client.PodEventDto;
import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.event.PodEventReasons;
import com.example.podlogger.store.FingerprintUtil;
import com.example.podlogger.store.PodStoreService;
import com.example.podlogger.store.TestRunStore;
import com.example.podlogger.store.dto.MergedLogResult;
import com.example.podlogger.store.dto.TestRunDto;

import lombok.RequiredArgsConstructor;

/**
 * Оркестратор runtime-сбора: ходит в {@link OpenshiftClient}, фильтрует окно,
 * обогащает DTO контекстом прогона, пишет в {@link PodStoreService}, аттачит Allure,
 * публикует lifecycle Events.
 *
 * <p>Не содержит SQL, schema migration и query builder — это store.
 * Не содержит хуков JUnit — это {@link PodLoggerExtension}.
 *
 * <p>Два интервала: invocation window (±{@link #SKEW_SECONDS} с) и run window
 * ({@code startedAt} прогона → сейчас) в {@link #collectAndMergeLogsForTestRun}.
 *
 * <p>Ошибки collect/save/Allure глотаются и логируются; тест из-за store не краснеет.
 */
@Component
@RequiredArgsConstructor
public class PodLoggerService {

    private static final Logger log = LoggerFactory.getLogger(PodLoggerService.class);
    /**
     * Допуск по краям окна invocation/run, чтобы не потерять JSON-строку,
     * чей timestamp чуть раньше {@code beforeEach} или чуть позже {@code afterEach}.
     */
    static final int SKEW_SECONDS = 2;

    /** Runtime-клиент поды. */
    private final OpenshiftClient openshiftClient;
    /** Зеркало аннотации и путь store. */
    private final PodLoggerProperties properties;
    /** Persistent логи. */
    private final PodStoreService podStoreService;
    /** Metadata прогона. */
    private final TestRunStore testRunStore;
    /** Allure JSON-аттачи. */
    private final LogAllureAttachmentService attachmentService;

    /**
     * Копирует атрибуты {@link PodLogger} в {@link PodLoggerProperties}.
     * Пустые строки имени/suite/service/health и пустые массивы stand-down не затирают
     * уже заданные значения (кроме флагов и environment, которые ставятся всегда).
     *
     * @param annotation аннотация тестового класса
     */
    public void applyAnnotation(PodLogger annotation) {
        properties.setNamespace(annotation.namespace());
        properties.setPodLabelSelector(annotation.podLabelSelector());
        properties.setCollectOnFailOnly(annotation.collectOnFailOnly());
        if (!annotation.testRunName().isBlank()) {
            properties.setTestRunName(annotation.testRunName());
        }
        if (!annotation.testSuiteName().isBlank()) {
            properties.setTestSuiteName(annotation.testSuiteName());
        }
        properties.setEnvironmentType(annotation.environmentType());
        if (!annotation.serviceType().isBlank()) {
            properties.setServiceType(annotation.serviceType());
        }
        properties.setPublishLifecycleEvents(annotation.publishLifecycleEvents());
        properties.setFailFastOnStandDownEvent(annotation.failFastOnStandDownEvent());
        if (!annotation.healthCheckUrl().isBlank()) {
            properties.setHealthCheckUrl(annotation.healthCheckUrl());
        }
        if (annotation.standDownEventCodes().length > 0) {
            properties.setStandDownEventCodes(List.of(annotation.standDownEventCodes()));
        }
        if (annotation.standDownMessagePatterns().length > 0) {
            properties.setStandDownMessagePatterns(List.of(annotation.standDownMessagePatterns()));
        }
    }

    /**
     * Точка входа из {@code afterEach}: passed идёт через CollectGate без Events;
     * failed — через {@link #handleFailedInvocation}.
     *
     * @param context   invocation
     * @param testRunId id прогона или {@code null}, если {@code beforeAll} не дошёл до put
     * @param start     UTC-старт invocation
     * @param end       UTC-конец invocation
     * @param failed    был ли exception
     * @return доступность поды; для passed всегда {@link PodAvailability#up()}
     */
    public PodAvailability handleAfterEach(
            ExtensionContext context,
            UUID testRunId,
            LocalDateTime start,
            LocalDateTime end,
            boolean failed) {
        if (!failed) {
            attachLogsIfNeeded(context, testRunId, start, end, false);
            return PodAvailability.up();
        }
        return handleFailedInvocation(context, testRunId, start, end);
    }

    /**
     * Контракт fail: {@code getEvents(window)} → Allure Events если список непустой →
     * probe availability → runtime-логи окна → {@code relevantEvents} на каждую запись →
     * persist только если под доступна; Allure логов — если под доступна или хвост лога непустой.
     *
     * <p>CollectGate для failed всегда true. Ошибки get/probe/collect глотаются.
     *
     * @param context   упавший invocation
     * @param testRunId id прогона
     * @param start     UTC-старт; {@code null} → «сейчас минус skew»
     * @param end       UTC-конец
     * @return результат probe (для fail-fast в extension)
     */
    public PodAvailability handleFailedInvocation(
            ExtensionContext context,
            UUID testRunId,
            LocalDateTime start,
            LocalDateTime end) {
        LocalDateTime from = start == null ? LocalDateTime.now(ZoneOffset.UTC).minusSeconds(SKEW_SECONDS)
                : start.minusSeconds(SKEW_SECONDS);
        LocalDateTime to = end.plusSeconds(SKEW_SECONDS);

        List<PodEventDto> events = List.of();
        try {
            events = openshiftClient.getEvents(from, to);
        } catch (Exception e) {
            log.error("Failed to get pod events for {}", context.getDisplayName(), e);
        }
        if (events == null) {
            events = List.of();
        }

        PodAvailability availability = PodAvailability.up();
        try {
            availability = openshiftClient.probePodAvailability();
        } catch (Exception e) {
            log.error("Failed to probe pod availability for {}", context.getDisplayName(), e);
        }
        if (availability == null) {
            availability = PodAvailability.up();
        }

        if (!events.isEmpty()) {
            attachmentService.attachEvents(
                    "pod-events-" + LogAllureAttachmentService.sanitize(context.getDisplayName()),
                    events);
        }

        waitForLogFlush();

        List<PodLogDto> window = List.of();
        try {
            window = collectRuntimeLogs(from, to);
        } catch (Exception e) {
            log.error("Failed to collect runtime pod logs for {}", context.getDisplayName(), e);
        }
        enrich(window, context, testRunId, true);
        applyRelevantEvents(window, events);

        if (availability.isAvailable()) {
            persistLogs(testRunId, window, context);
            attachmentService.attachJson(
                    "pod-logs-" + LogAllureAttachmentService.sanitize(context.getDisplayName()),
                    window);
            log.info("Attached {} pod log events for {} (window {} .. {})",
                    window.size(), context.getDisplayName(), from, to);
        } else if (!window.isEmpty()) {
            attachmentService.attachJson(
                    "pod-logs-" + LogAllureAttachmentService.sanitize(context.getDisplayName()),
                    window);
            log.info("Attached {} pod logs without persist for unavailable pod {}",
                    window.size(), context.getDisplayName());
        }

        return availability;
    }

    /**
     * Путь passed-теста (и общий CollectGate для логов): skip если gate закрыт,
     * иначе collect → enrich → persist → Allure {@code pod-logs-*}. Events не трогает.
     *
     * @param context   invocation
     * @param testRunId id прогона
     * @param start     UTC-старт; {@code null} → warn и выход
     * @param end       UTC-конец
     * @param failed    для CollectGate и поля {@code testFailed} в DTO
     */
    public void attachLogsIfNeeded(
            ExtensionContext context,
            UUID testRunId,
            LocalDateTime start,
            LocalDateTime end,
            boolean failed) {
        if (!CollectGate.shouldCollect(properties.isCollectOnFailOnly(), failed)) {
            log.debug("Skip Allure+SQLite for {} because collectOnFailOnly={} and failed={}",
                    context.getDisplayName(), properties.isCollectOnFailOnly(), failed);
            return;
        }
        if (start == null) {
            log.warn("No start timestamp stored for {}", context.getDisplayName());
            return;
        }

        waitForLogFlush();

        List<PodLogDto> window;
        try {
            window = collectRuntimeLogs(start.minusSeconds(SKEW_SECONDS), end.plusSeconds(SKEW_SECONDS));
        } catch (Exception e) {
            log.error("Failed to collect runtime pod logs for {}", context.getDisplayName(), e);
            return;
        }

        enrich(window, context, testRunId, failed);
        persistLogs(testRunId, window, context);
        attachmentService.attachJson(
                "pod-logs-" + LogAllureAttachmentService.sanitize(context.getDisplayName()),
                window);
        log.info("Attached {} pod log events for {} (window {} .. {})",
                window.size(), context.getDisplayName(),
                start.minusSeconds(SKEW_SECONDS), end.plusSeconds(SKEW_SECONDS));
    }

    /**
     * Best-effort publish {@code TestRunStarted}. No-op если {@code publishLifecycleEvents=false}.
     * Исключение create Event наружу не выходит (клиент глотает).
     *
     * @param testRunId   UUID прогона
     * @param testRunName имя прогона
     * @param suiteName   имя suite
     */
    public void publishTestRunStarted(UUID testRunId, String testRunName, String suiteName) {
        if (!properties.isPublishLifecycleEvents()) {
            return;
        }
        String message = "testRunName=" + nullToEmpty(testRunName)
                + " testRunId=" + testRunId
                + " suite=" + nullToEmpty(suiteName);
        openshiftClient.publishPodEvent("Normal", PodEventReasons.TEST_RUN_STARTED, message);
    }

    /**
     * Best-effort publish {@code TestRunFinished} с {@code total/passed/failed}.
     * No-op если публикация выключена.
     *
     * @param testRunName имя прогона
     * @param total       passed + failed + disabled
     * @param passed      успешные
     * @param failed      failed + aborted
     */
    public void publishTestRunFinished(String testRunName, int total, int passed, int failed) {
        if (!properties.isPublishLifecycleEvents()) {
            return;
        }
        String message = "testRunName=" + nullToEmpty(testRunName)
                + " total=" + total
                + " passed=" + passed
                + " failed=" + failed;
        openshiftClient.publishPodEvent("Normal", PodEventReasons.TEST_RUN_FINISHED, message);
    }

    /**
     * Обёртка над {@link OpenshiftClient#probePodAvailability()} с swallow ошибок:
     * сбой list/health трактуется как «под доступна» (не лже-stand-down).
     *
     * @return availability; при ошибке {@link PodAvailability#up()}
     */
    public PodAvailability probeAvailability() {
        try {
            return openshiftClient.probePodAvailability();
        } catch (Exception e) {
            log.error("Failed to probe pod availability", e);
            return PodAvailability.up();
        }
    }

    /**
     * Run-level sync в {@code afterAll}: логи из SQLite + свежий runtime dump окна прогона,
     * дедуп по timestamp+fingerprint, дозапись новых строк. Не ходит в Events.
     * Ошибка возвращает пустой {@link MergedLogResult}, статус тестов не меняет.
     *
     * @param testRunId id прогона
     * @return слитый результат; пустой каркас при сбое
     */
    public MergedLogResult collectAndMergeLogsForTestRun(UUID testRunId) {
        MergedLogResult empty = MergedLogResult.builder().testRunId(testRunId).build();
        try {
            TestRunDto run = testRunStore.getTestRun(testRunId)
                    .orElseThrow(() -> new IllegalStateException("Unknown testRunId " + testRunId));
            LocalDateTime from = run.getStartedAt().minusSeconds(SKEW_SECONDS);
            LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(SKEW_SECONDS);
            List<PodLogDto> fromPersistent = podStoreService.getLogs(testRunId);
            List<PodLogDto> fromRuntime = collectRuntimeLogs(from, to);
            enrichRunContext(fromRuntime, run, null, null, null, null);

            Map<String, PodLogDto> merged = new LinkedHashMap<>();
            for (PodLogDto entry : fromPersistent) {
                merged.put(dedupKey(entry), entry);
            }
            int inserted = 0;
            for (PodLogDto entry : fromRuntime) {
                String key = dedupKey(entry);
                if (!merged.containsKey(key)) {
                    merged.put(key, entry);
                    inserted++;
                }
            }
            if (inserted > 0) {
                podStoreService.saveLogs(testRunId, fromRuntime);
            }
            List<PodLogDto> mergedList = merged.values().stream()
                    .sorted(Comparator.comparing(PodLogDto::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
            if (properties.isAttachRunSummaryToAllure()) {
                attachmentService.attachJson("pod-logs-run-" + LogAllureAttachmentService.sanitize(run.getTestRunName()),
                        mergedList);
            }
            return MergedLogResult.builder()
                    .testRunId(testRunId)
                    .fromPersistent(fromPersistent)
                    .fromRuntime(fromRuntime)
                    .merged(mergedList)
                    .insertedNewCount(inserted)
                    .build();
        } catch (Exception e) {
            log.error("collectAndMergeLogsForTestRun failed for {}", testRunId, e);
            return empty;
        }
    }

    /**
     * Runtime dump поды → фильтр по включительному интервалу timestamp.
     * Записи без timestamp отбрасываются.
     *
     * @param from нижняя граница UTC включительно
     * @param to   верхняя граница UTC включительно
     * @return срез окна, возможно пустой
     */
    public List<PodLogDto> collectRuntimeLogs(LocalDateTime from, LocalDateTime to) {
        return openshiftClient.getLog().stream()
                .filter(entry -> entry.getTimestamp() != null)
                .filter(entry -> !entry.getTimestamp().isBefore(from) && !entry.getTimestamp().isAfter(to))
                .collect(Collectors.toList());
    }

    /**
     * Имя прогона: из properties, иначе {@code SimpleName-yyyyMMddHHmmss} UTC.
     *
     * @param testClass тестовый класс
     * @return непустое имя
     */
    String resolveTestRunName(Class<?> testClass) {
        if (properties.getTestRunName() != null && !properties.getTestRunName().isBlank()) {
            return properties.getTestRunName();
        }
        String stamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return testClass.getSimpleName() + "-" + stamp;
    }

    /**
     * Имя suite: из properties, иначе FQCN класса.
     *
     * @param testClass тестовый класс
     * @return непустое имя
     */
    String resolveTestSuiteName(Class<?> testClass) {
        if (properties.getTestSuiteName() != null && !properties.getTestSuiteName().isBlank()) {
            return properties.getTestSuiteName();
        }
        return testClass.getName();
    }

    /**
     * Тип сервиса: из properties, иначе value label selector после {@code =}.
     *
     * @return serviceType для {@code test_run}
     */
    String resolveServiceType() {
        if (properties.getServiceType() != null && !properties.getServiceType().isBlank()) {
            return properties.getServiceType();
        }
        String selector = properties.getPodLabelSelector();
        int eq = selector == null ? -1 : selector.indexOf('=');
        return eq > 0 ? selector.substring(eq + 1) : selector;
    }

    /**
     * Persist окна; ошибка только логируется. {@code testRunId == null} — skip.
     *
     * @param testRunId id прогона
     * @param window    уже обогащённые DTO
     * @param context   для текста ошибки
     */
    private void persistLogs(UUID testRunId, List<PodLogDto> window, ExtensionContext context) {
        try {
            if (testRunId != null) {
                podStoreService.saveLogs(testRunId, window);
            }
        } catch (Exception e) {
            log.error("Failed to persist pod logs for {}", context.getDisplayName(), e);
        }
    }

    /**
     * Копирует один и тот же список Events на каждую запись окна (денормализация для Allure JSON).
     * Пустой список или пустые логи — no-op. В SQLite поле не пишется.
     *
     * @param logs   окно логов
     * @param events Events окна fail
     */
    private static void applyRelevantEvents(List<PodLogDto> logs, List<PodEventDto> events) {
        if (logs == null || events == null || events.isEmpty()) {
            return;
        }
        for (PodLogDto entry : logs) {
            entry.setRelevantEvents(events);
        }
    }

    /**
     * Пауза 500 мс, чтобы stdout поды успел дойти до kubelet перед {@code pods/log}.
     * Interrupted восстанавливает interrupt flag.
     */
    private static void waitForLogFlush() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Достаёт metadata прогона и тестового метода, затем {@link #enrichRunContext}.
     *
     * @param logs      мутируемый список
     * @param context   invocation
     * @param testRunId id прогона
     * @param failed    исход теста
     */
    private void enrich(List<PodLogDto> logs, ExtensionContext context, UUID testRunId, boolean failed) {
        String testClass = context.getRequiredTestClass().getName();
        String testMethod = context.getTestMethod().map(method -> method.getName()).orElse(null);
        String displayName = context.getDisplayName();
        TestRunDto run = testRunId == null ? null : testRunStore.getTestRun(testRunId).orElse(null);
        enrichRunContext(logs, run, testClass, testMethod, displayName, failed);
    }

    /**
     * Проставляет контекст прогона и теста на каждую запись. Поля, уже заполненные парсером
     * (timestamp/level/message), не затирает. Fingerprint считает, если пуст.
     *
     * @param logs        мутируемый список
     * @param run         metadata прогона или {@code null}
     * @param testClass   FQCN или {@code null}
     * @param testMethod  имя метода или {@code null}
     * @param displayName display name invocation или {@code null}
     * @param failed      {@code null} — не трогать {@code testFailed}
     */
    private void enrichRunContext(
            List<PodLogDto> logs,
            TestRunDto run,
            String testClass,
            String testMethod,
            String displayName,
            Boolean failed) {
        for (PodLogDto entry : logs) {
            if (run != null) {
                entry.setTestRunId(run.getId());
                entry.setRunName(run.getTestRunName());
                entry.setTestRunName(run.getTestRunName());
                entry.setTestSuiteName(run.getTestSuiteName());
                entry.setEnvironmentType(run.getEnvironmentType());
                if (entry.getServiceType() == null) {
                    entry.setServiceType(run.getServiceType());
                }
                if (entry.getNamespace() == null) {
                    entry.setNamespace(run.getNamespace());
                }
                if (entry.getPodLabelSelector() == null) {
                    entry.setPodLabelSelector(run.getPodLabelSelector());
                }
            }
            if (testClass != null) {
                entry.setRelatedTestClass(testClass);
            }
            if (testMethod != null) {
                entry.setRelatedTestMethod(testMethod);
            }
            if (displayName != null) {
                entry.setTestDisplayName(displayName);
            }
            if (failed != null) {
                entry.setTestFailed(failed);
            }
            if (entry.getFingerprint() == null) {
                entry.setFingerprint(FingerprintUtil.compute(entry));
            }
        }
    }

    /**
     * Ключ дедупа merge: {@code timestamp|fingerprint}.
     *
     * @param entry запись лога
     * @return строковый ключ
     */
    private static String dedupKey(PodLogDto entry) {
        String fingerprint = entry.getFingerprint() == null ? FingerprintUtil.compute(entry) : entry.getFingerprint();
        return StoreTimeSafe(entry) + "|" + fingerprint;
    }

    /**
     * Timestamp как строка для ключа дедупа; {@code null} → пустая строка.
     *
     * @param entry запись лога
     * @return ISO-текст или {@code ""}
     */
    private static String StoreTimeSafe(PodLogDto entry) {
        return entry.getTimestamp() == null ? "" : entry.getTimestamp().toString();
    }

    /**
     * {@code null} → {@code ""} для сборки message Event.
     *
     * @param value исходная строка
     * @return не-{@code null} строка
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
