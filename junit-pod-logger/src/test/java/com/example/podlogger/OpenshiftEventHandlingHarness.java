package com.example.podlogger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import com.example.podlogger.allure.AllureSink;
import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.client.PodAvailability;
import com.example.podlogger.client.PodEventDto;
import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.parser.LogParser;
import com.example.podlogger.store.EnvironmentType;

/**
 * EngineTestKit sample classes and a stub OpenShift client driven by static state.
 */
final class OpenshiftEventHandlingHarness {

    static final List<CapturedAttachment> ATTACHMENTS = new CopyOnWriteArrayList<>();
    static final List<PodEventDto> PUBLISHED = new CopyOnWriteArrayList<>();
    static final AtomicBoolean SECOND_RAN = new AtomicBoolean(false);
    static final AtomicInteger PROBE_COUNT = new AtomicInteger();

    static volatile List<PodEventDto> eventsToReturn = List.of();
    static volatile PodAvailability startAvailability = PodAvailability.up();
    static volatile PodAvailability availability = PodAvailability.up();

    private OpenshiftEventHandlingHarness() {
    }

    static void reset() {
        ATTACHMENTS.clear();
        PUBLISHED.clear();
        SECOND_RAN.set(false);
        PROBE_COUNT.set(0);
        eventsToReturn = List.of();
        startAvailability = PodAvailability.up();
        availability = PodAvailability.up();
    }

    static List<PodLogDto> defaultLogs() {
        return List.of(PodLogDto.builder()
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .level("ERROR")
                .message("Unknown SKU")
                .logger("com.example.demoapp.OrderController")
                .podName("demo-api")
                .namespace("default")
                .build());
    }

    static PodEventDto event(String code, String message) {
        return PodEventDto.builder()
                .code(code)
                .reason(code)
                .type("Warning")
                .message(message)
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .count(1)
                .podName("demo-api")
                .namespace("default")
                .uid("evt-" + code)
                .build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "com.example.podlogger",
            excludeFilters = {
                    @ComponentScan.Filter(
                            type = FilterType.CUSTOM,
                            classes = org.springframework.boot.context.TypeExcludeFilter.class),
                    @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Harness.*")
            })
    static class App {
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        OpenshiftClient eventHandlingOpenshiftClient(PodLoggerProperties properties, LogParser logParser) {
            return new OpenshiftClient(null, properties, logParser) {
                @Override
                public List<PodLogDto> getLog() {
                    return defaultLogs();
                }

                @Override
                public List<PodEventDto> getEvents() {
                    return eventsToReturn;
                }

                @Override
                public List<PodEventDto> getEvents(LocalDateTime from, LocalDateTime to) {
                    return eventsToReturn;
                }

                @Override
                public PodEventDto publishPodEvent(String type, String reason, String message) {
                    PodEventDto dto = PodEventDto.builder()
                            .code(reason)
                            .reason(reason)
                            .type(type)
                            .message(message)
                            .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                            .count(1)
                            .build();
                    PUBLISHED.add(dto);
                    return dto;
                }

                @Override
                public boolean isPodAvailable() {
                    return probePodAvailability().isAvailable();
                }

                @Override
                public PodAvailability probePodAvailability() {
                    if (PROBE_COUNT.getAndIncrement() == 0) {
                        return startAvailability;
                    }
                    return availability;
                }
            };
        }

        @Bean
        @Primary
        AllureSink recordingAllureSink() {
            return (name, contentType, body, fileExtension) ->
                    ATTACHMENTS.add(new CapturedAttachment(name, body));
        }
    }

    record CapturedAttachment(String name, String body) {
    }

    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "event-handling-fail-with-events",
            testSuiteName = "event-handling-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubConfig.class}, properties = "pod.logger.harness=fail-events")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    public static class FailWithEventsSample {

        @Test
        void failsOnPurpose() {
            throw new AssertionError("forced failure");
        }
    }

    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "event-handling-fail-without-events",
            testSuiteName = "event-handling-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubConfig.class}, properties = "pod.logger.harness=fail-no-events")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    public static class FailWithoutEventsSample {

        @Test
        void failsOnPurpose() {
            throw new AssertionError("forced failure");
        }
    }

    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "event-handling-stand-down",
            testSuiteName = "event-handling-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubConfig.class}, properties = "pod.logger.harness=stand-down")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    public static class StandDownFailFastSample {

        @Test
        @Order(1)
        void firstFails() {
            throw new AssertionError("product assertion");
        }

        @Test
        @Order(2)
        void secondMustNotRun() {
            SECOND_RAN.set(true);
        }
    }

    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "event-handling-health-red",
            testSuiteName = "event-handling-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubConfig.class}, properties = "pod.logger.harness=health-red")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    public static class HealthRedNoFailFastSample {

        @Test
        @Order(1)
        void firstFails() {
            throw new AssertionError("product assertion");
        }

        @Test
        @Order(2)
        void secondRuns() {
            SECOND_RAN.set(true);
        }
    }

    /**
     * In-memory client for publish/get without a cluster.
     */
    static final class InMemoryOpenshiftClient extends OpenshiftClient {

        private final List<PodEventDto> store = new ArrayList<>();

        InMemoryOpenshiftClient() {
            super(null, new PodLoggerProperties(), raw -> List.of());
        }

        @Override
        public synchronized PodEventDto publishPodEvent(String type, String reason, String message) {
            PodEventDto dto = PodEventDto.builder()
                    .code(reason)
                    .reason(reason)
                    .type(type)
                    .message(message)
                    .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                    .count(1)
                    .podName("demo-api")
                    .namespace("default")
                    .build();
            store.add(dto);
            return dto;
        }

        @Override
        public synchronized List<PodEventDto> getEvents() {
            return List.copyOf(store);
        }

        @Override
        public synchronized List<PodEventDto> getEvents(LocalDateTime from, LocalDateTime to) {
            return store.stream()
                    .filter(event -> com.example.podlogger.client.PodEventMapper.inWindow(event, from, to))
                    .toList();
        }
    }
}
