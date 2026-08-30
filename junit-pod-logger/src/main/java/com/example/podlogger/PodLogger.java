package com.example.podlogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.podlogger.store.EnvironmentType;

/**
 * Collects Kubernetes/OpenShift pod logs for each test method (including each
 * {@code @ParameterizedTest} invocation). The same {@code collectOnFailOnly}
 * gate controls Allure attachments and SQLite persist.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({SpringExtension.class, PodLoggerExtension.class})
public @interface PodLogger {

    /**
     * When {@code true}, Allure attach and SQLite save happen only if the invocation failed.
     * When {@code false}, both happen after every invocation.
     */
    boolean collectOnFailOnly() default true;

    String namespace() default "default";

    String podLabelSelector() default "app=demo-api";

    String testRunName() default "";

    String testSuiteName() default "";

    EnvironmentType environmentType() default EnvironmentType.LOCAL;

    String serviceType() default "";
}
