package com.example.podlogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Collects Kubernetes/OpenShift pod logs for each test method (including each
 * {@code @ParameterizedTest} invocation) and attaches the time-windowed slice to Allure.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({SpringExtension.class, PodLoggerExtension.class})
public @interface PodLogger {

    /**
     * When {@code true}, Allure attachments are added only if the invocation failed.
     * When {@code false}, logs are attached after every invocation.
     */
    boolean collectOnFailOnly() default true;

    String namespace() default "default";

    String podLabelSelector() default "app=demo-api";
}
