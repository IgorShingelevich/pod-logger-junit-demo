package com.example.podlogger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class PodLoggerExtension implements BeforeEachCallback, AfterEachCallback, TestWatcher {

    static final Namespace STORE_NS = Namespace.create(PodLoggerExtension.class);
    static final String START_KEY = "testStartUtc";

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
        service(context).attachLogsIfNeeded(context, start, end, failed);
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
}
