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

public class PodLoggerExtension implements BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final Logger log = LoggerFactory.getLogger(PodLoggerExtension.class);

    static final Namespace STORE_NS = Namespace.create(PodLoggerExtension.class);
    static final String START_KEY = "testStartUtc";
    static final String TEST_RUN_ID_KEY = "testRunId";
    static final String STAND_UNAVAILABLE_KEY = "standUnavailable";
    static final String STAND_UNAVAILABLE_CODE_KEY = "standUnavailableCode";
    static final String PASSED_COUNT_KEY = "passedCount";
    static final String FAILED_COUNT_KEY = "failedCount";
    static final String DISABLED_COUNT_KEY = "disabledCount";
    static final String TEST_RUN_NAME_KEY = "testRunName";

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

    @Override
    public void afterAll(ExtensionContext context) {
        UUID testRunId = context.getStore(STORE_NS).get(TEST_RUN_ID_KEY, UUID.class);
        if (testRunId == null) {
            return;
        }
        try {
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
            service(context).publishTestRunFinished(runName, total, passed, failed);
        } catch (Exception e) {
            log.error("PodLogger afterAll publish TestRunFinished failed for {}", testRunId, e);
        }
        try {
            testRunStore(context).finishTestRun(testRunId);
        } catch (Exception e) {
            log.error("PodLogger afterAll finishTestRun failed for {}", testRunId, e);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Boolean standDown = classStore(context).get(STAND_UNAVAILABLE_KEY, Boolean.class);
        if (Boolean.TRUE.equals(standDown)) {
            String code = classStore(context).get(STAND_UNAVAILABLE_CODE_KEY, String.class);
            throw new IllegalStateException("Stand unavailable: " + code);
        }
        context.getStore(STORE_NS).put(START_KEY, LocalDateTime.now(ZoneOffset.UTC));
        service(context).applyAnnotation(annotation(context));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        LocalDateTime start = context.getStore(STORE_NS).get(START_KEY, LocalDateTime.class);
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);
        boolean failed = context.getExecutionException().isPresent();
        UUID testRunId = classStore(context).get(TEST_RUN_ID_KEY, UUID.class);
        PodAvailability availability = service(context).handleAfterEach(context, testRunId, start, end, failed);
        if (failed && availability != null && availability.isStandDownEventPresent()
                && annotation(context).failFastOnStandDownEvent()) {
            classStore(context).put(STAND_UNAVAILABLE_KEY, Boolean.TRUE);
            classStore(context).put(STAND_UNAVAILABLE_CODE_KEY, availability.getCode());
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        increment(classStore(context), PASSED_COUNT_KEY);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        increment(classStore(context), FAILED_COUNT_KEY);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        increment(classStore(context), FAILED_COUNT_KEY);
    }

    @Override
    public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
        increment(classStore(context), DISABLED_COUNT_KEY);
    }

    private static void increment(ExtensionContext.Store store, String key) {
        Integer current = store.get(key, Integer.class);
        store.put(key, current == null ? 1 : current + 1);
    }

    private static int count(ExtensionContext context, String key) {
        Integer value = context.getStore(STORE_NS).get(key, Integer.class);
        return value == null ? 0 : value;
    }

    private static ExtensionContext.Store classStore(ExtensionContext context) {
        return context.getParent().orElse(context).getStore(STORE_NS);
    }

    private static PodLogger annotation(ExtensionContext context) {
        PodLogger annotation = context.getRequiredTestClass().getAnnotation(PodLogger.class);
        if (annotation == null) {
            throw new IllegalStateException("@PodLogger must be present on the test class");
        }
        return annotation;
    }

    private static PodLoggerService service(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(PodLoggerService.class);
    }

    private static TestRunStore testRunStore(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(TestRunStore.class);
    }
}
