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

import com.example.podlogger.store.TestRunStore;
import com.example.podlogger.store.dto.TestRunDto;

public class PodLoggerExtension implements BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final Logger log = LoggerFactory.getLogger(PodLoggerExtension.class);

    static final Namespace STORE_NS = Namespace.create(PodLoggerExtension.class);
    static final String START_KEY = "testStartUtc";
    static final String TEST_RUN_ID_KEY = "testRunId";

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
            log.info("PodLogger beforeAll: testRunId={} started", testRunId);
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
            testRunStore(context).finishTestRun(testRunId);
        } catch (Exception e) {
            log.error("PodLogger afterAll finishTestRun failed for {}", testRunId, e);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(STORE_NS).put(START_KEY, LocalDateTime.now(ZoneOffset.UTC));
        service(context).applyAnnotation(annotation(context));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        LocalDateTime start = context.getStore(STORE_NS).get(START_KEY, LocalDateTime.class);
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);
        boolean failed = context.getExecutionException().isPresent();
        UUID testRunId = classStore(context).get(TEST_RUN_ID_KEY, UUID.class);
        service(context).attachLogsIfNeeded(context, testRunId, start, end, failed);
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
