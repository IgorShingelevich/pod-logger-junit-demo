package com.example.podlogger.event;

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

import com.example.podlogger.PodLogger;
import com.example.podlogger.PodLoggerProperties;
import com.example.podlogger.allure.AllureSink;
import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.client.PodAvailability;
import com.example.podlogger.client.PodEventDto;
import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.parser.LogParser;
import com.example.podlogger.store.EnvironmentType;

/**
 * EngineTestKit sample-классы и stub OpenShift-клиента на static-состоянии.
 * Кластер не нужен: {@code getLog}/{@code getEvents}/{@code publish}/{@code probe} переопределены.
 */
final class OpenshiftEventHandlingHarness {

    /** Allure-аттачи, перехваченные stub sink. */
    static final List<CapturedAttachment> ATTACHMENTS = new CopyOnWriteArrayList<>();
    /** Lifecycle Events, которые stub {@code publishPodEvent} положил в список. */
    static final List<PodEventDto> PUBLISHED = new CopyOnWriteArrayList<>();
    /** Дошёл ли второй тест до тела. */
    static final AtomicBoolean SECOND_RAN = new AtomicBoolean(false);
    /** Сколько раз вызывали probe (первый — beforeAll). */
    static final AtomicInteger PROBE_COUNT = new AtomicInteger();

    /** Events, которые вернёт stub {@code getEvents}. */
    static volatile List<PodEventDto> eventsToReturn = List.of();
    /** Availability на первом probe (beforeAll). */
    static volatile PodAvailability startAvailability = PodAvailability.up();
    /** Availability на последующих probe (afterEach). */
    static volatile PodAvailability availability = PodAvailability.up();

    /** Не инстанцируется. */
    private OpenshiftEventHandlingHarness() {
    }

    /**
     * Обнуляет аттачи, published Events, флаги второго теста и availability.
     */
    static void reset() {
        ATTACHMENTS.clear();
        PUBLISHED.clear();
        SECOND_RAN.set(false);
        PROBE_COUNT.set(0);
        eventsToReturn = List.of();
        startAvailability = PodAvailability.up();
        availability = PodAvailability.up();
    }

    /**
     * Один ERROR-лог «Unknown SKU» как runtime dump.
     *
     * @return список из одной записи
     */
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

    /**
     * Warning Event с заданным кодом.
     *
     * @param code    reason/code
     * @param message текст
     * @return DTO
     */
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

    /**
     * Минимальный Spring Boot для sample-классов.
     */
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

    /**
     * Primary stub: логи, Events, publish в {@link #PUBLISHED}, probe по счётчику.
     */
    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        /**
         * Stub {@link OpenshiftClient} для сценариев Event Handling.
         *
         * @param properties настройки
         * @param logParser  не используется
         * @return anonymous subclass
         */
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
        /**
         * Capturing {@link AllureSink}: кладёт имя и тело в {@link #ATTACHMENTS}.
         *
         * @return recording sink
         */
        AllureSink recordingAllureSink() {
            return (name, contentType, body, fileExtension) ->
                    ATTACHMENTS.add(new CapturedAttachment(name, body));
        }
    }

    /** Имя и JSON-тело Allure-аттача. */
    record CapturedAttachment(String name, String body) {
    }

    /**
     * Sample: один fail при непустых Events (сценарий 2).
     */
    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "event-handling-fail-with-events",
            testSuiteName = "event-handling-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubConfig.class}, properties = "pod.logger.harness=fail-events")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    public static class FailWithEventsSample {

        /**
         * Намеренный AssertionError.
         */
        @Test
        void failsOnPurpose() {
            throw new AssertionError("forced failure");
        }
    }

    /**
     * Sample: один fail при пустых Events (сценарий 3).
     */
    @PodLogger(
            collectOnFailOnly = true,
            testRunName = "event-handling-fail-without-events",
            testSuiteName = "event-handling-suite",
            environmentType = EnvironmentType.LOCAL,
            serviceType = "demo-api")
    @SpringBootTest(classes = {App.class, StubConfig.class}, properties = "pod.logger.harness=fail-no-events")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    public static class FailWithoutEventsSample {

        /**
         * Намеренный fail без Events.
         */
        @Test
        void failsOnPurpose() {
            throw new AssertionError("forced failure");
        }
    }

    /**
     * Sample: два теста, первый падает при stand-down (сценарий 4).
     */
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

        /**
         * Исходный product assertion (не должен быть затёрт stand-down).
         */
        @Test
        @Order(1)
        void firstFails() {
            throw new AssertionError("product assertion");
        }

        /**
         * Не должен выполниться при fail-fast.
         */
        @Test
        @Order(2)
        void secondMustNotRun() {
            SECOND_RAN.set(true);
        }
    }

    /**
     * Sample: два теста, health red без stand-down (сценарий 5).
     */
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

        /**
         * Первый fail; persist должен быть skip.
         */
        @Test
        @Order(1)
        void firstFails() {
            throw new AssertionError("product assertion");
        }

        /**
         * Должен выполниться: health red не abort.
         */
        @Test
        @Order(2)
        void secondRuns() {
            SECOND_RAN.set(true);
        }
    }

    /**
     * In-memory client: publish кладёт в список, get читает его. Без fabric8.
     */
    static final class InMemoryOpenshiftClient extends OpenshiftClient {

        private final List<PodEventDto> store = new ArrayList<>();

        /** Клиент без fabric8: parser no-op. */
        InMemoryOpenshiftClient() {
            super(null, new PodLoggerProperties(), raw -> List.of());
        }

        /**
         * Кладёт Event в память и возвращает DTO с {@code code=reason}.
         */
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

        /**
         * Все опубликованные Events.
         */
        @Override
        public synchronized List<PodEventDto> getEvents() {
            return List.copyOf(store);
        }

        /**
         * Фильтр по окну timestamp.
         */
        @Override
        public synchronized List<PodEventDto> getEvents(LocalDateTime from, LocalDateTime to) {
            return store.stream()
                    .filter(event -> com.example.podlogger.client.PodEventMapper.inWindow(event, from, to))
                    .toList();
        }
    }
}
