package com.example.podlogger.store;

import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import com.example.podlogger.PodLogger;
import com.example.podlogger.PodLoggerProperties;
import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.parser.LogParser;

final class PersistentLogStoreHarness {

    private PersistentLogStoreHarness() {
    }

    @SpringBootApplication(scanBasePackages = "com.example.podlogger")
    static class App {
    }

    @Configuration
    static class StubLogsConfig {

        @Bean
        @Primary
        OpenshiftClient stubOpenshiftClient(PodLoggerProperties properties, LogParser logParser) {
            return new OpenshiftClient(null, properties, logParser) {
                @Override
                public List<PodLogDto> getLog() {
                    return List.of(PodLogDto.builder()
                            .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                            .level("ERROR")
                            .message("Unknown SKU")
                            .logger("com.example.demoapp.OrderController")
                            .podName("demo-api")
                            .namespace("default")
                            .build());
                }
            };
        }
    }

    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "persistent-log-store-failed",
            testSuiteName = "persistent-log-store-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubLogsConfig.class}, properties = "pod.logger.harness=failed")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    public static class FailedLoggedSample {

        @Test
        void failsOnPurpose() {
            fail("collectOnFailOnly demo: force failure so extension persists pod logs");
        }
    }

    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "persistent-log-store-passed",
            testSuiteName = "persistent-log-store-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubLogsConfig.class}, properties = "pod.logger.harness=passed")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    public static class PassingLoggedSample {

        @Test
        void passes() {
            // gate must skip Allure and SQLite
        }
    }
}
