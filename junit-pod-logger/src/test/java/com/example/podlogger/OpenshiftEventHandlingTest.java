package com.example.podlogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.client.PodAvailability;
import com.example.podlogger.client.PodEventDto;
import com.example.podlogger.client.PodEventMapper;
import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.event.PodEventReasons;
import com.example.podlogger.event.StandDownEventMatcher;
import com.example.podlogger.store.DefaultPodStoreService;
import com.example.podlogger.store.DefaultTestRunStore;
import com.example.podlogger.store.LogStoreRepository;
import com.example.podlogger.store.PodStoreService;
import com.example.podlogger.store.StorePathResolver;
import com.example.podlogger.store.TestRunRepository;
import com.example.podlogger.store.TestRunStore;
import com.example.podlogger.store.sqlite.SchemaMigrator;
import com.example.podlogger.store.sqlite.SqliteDataSourceFactory;
import com.example.podlogger.store.sqlite.SqliteLogStoreRepository;
import com.example.podlogger.store.sqlite.SqliteTestRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;

import javax.sql.DataSource;

@DisplayName("OpenShift Event Handling Test")
class OpenshiftEventHandlingTest {

    @BeforeEach
    void resetHarness() {
        OpenshiftEventHandlingHarness.reset();
    }

    @Nested
    @DisplayName("1. Publish/get с кодами")
    class PublishGetCodes {

        @Test
        @DisplayName("publishPodEvent затем getEvents возвращает тот же code")
        void publishThenGetReturnsCode() {
            OpenshiftClient client = new OpenshiftEventHandlingHarness.InMemoryOpenshiftClient();
            client.publishPodEvent("Warning", "Maintenance", "stand down");
            List<PodEventDto> events = client.getEvents();
            assertEquals(1, events.size());
            assertEquals("Maintenance", events.get(0).getCode());
            assertEquals("Maintenance", events.get(0).getReason());
            assertEquals("stand down", events.get(0).getMessage());
        }

        @Test
        @DisplayName("G1: mapper code равен Event.reason")
        void mapperCopiesReasonAsCode() {
            Event event = new EventBuilder()
                    .withNewMetadata().withUid("uid-1").withName("e1")
                    .withCreationTimestamp("2026-08-30T10:00:00Z").endMetadata()
                    .withReason("Maintenance")
                    .withType("Warning")
                    .withMessage("stand down")
                    .withCount(1)
                    .build();
            PodEventDto dto = PodEventMapper.toDto(event, "demo-api", "default");
            assertEquals("Maintenance", dto.getCode());
            assertEquals("Maintenance", dto.getReason());
            assertEquals("Warning", dto.getType());
            assertEquals("uid-1", dto.getUid());
        }

        @Test
        @DisplayName("G2: окно from..to отсекает события вне интервала")
        void windowExcludesEventsOutsideRange() {
            OpenshiftEventHandlingHarness.InMemoryOpenshiftClient client =
                    new OpenshiftEventHandlingHarness.InMemoryOpenshiftClient();
            client.publishPodEvent("Warning", "Maintenance", "stand down");
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            assertFalse(client.getEvents(now.minusMinutes(1), now.plusMinutes(1)).isEmpty());
            assertTrue(client.getEvents(now.minusDays(2), now.minusDays(1)).isEmpty());
        }
    }

    @Nested
    @DisplayName("2. Fail + Events → Allure")
    class FailWithEventsAttachesToAllure {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("упавший тест аттачит pod-events с code и relevantEvents в логах")
        void failedTestAttachesEventsWithCode() {
            OpenshiftEventHandlingHarness.eventsToReturn =
                    List.of(OpenshiftEventHandlingHarness.event("Maintenance", "stand down"));
            OpenshiftEventHandlingHarness.availability = PodAvailability.up();
            runSample(tempDir, OpenshiftEventHandlingHarness.FailWithEventsSample.class, true);

            assertTrue(OpenshiftEventHandlingHarness.ATTACHMENTS.stream()
                    .anyMatch(item -> item.name().startsWith("pod-events-")
                            && item.body().contains("Maintenance")));
            assertTrue(OpenshiftEventHandlingHarness.ATTACHMENTS.stream()
                    .anyMatch(item -> item.name().startsWith("pod-logs-")
                            && item.body().contains("Maintenance")
                            && item.body().contains("relevantEvents")));
            assertTrue(OpenshiftEventHandlingHarness.PUBLISHED.stream()
                    .anyMatch(item -> PodEventReasons.TEST_RUN_STARTED.equals(item.getCode())));
            assertTrue(OpenshiftEventHandlingHarness.PUBLISHED.stream()
                    .anyMatch(item -> PodEventReasons.TEST_RUN_FINISHED.equals(item.getCode())
                            && item.getMessage().contains("total=")
                            && item.getMessage().contains("passed=")
                            && item.getMessage().contains("failed=")));
            assertEquals(2, OpenshiftEventHandlingHarness.PUBLISHED.size());
        }
    }

    @Nested
    @DisplayName("3. Fail + нет Events → нет аттача")
    class FailWithoutEventsDoesNotAttach {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("пустой getEvents не создаёт pod-events аттач")
        void failedTestWithoutEventsSkipsEventsAttachment() {
            OpenshiftEventHandlingHarness.eventsToReturn = List.of();
            OpenshiftEventHandlingHarness.availability = PodAvailability.up();
            runSample(tempDir, OpenshiftEventHandlingHarness.FailWithoutEventsSample.class, true);

            assertTrue(OpenshiftEventHandlingHarness.ATTACHMENTS.stream()
                    .noneMatch(item -> item.name().startsWith("pod-events-")));
            assertTrue(OpenshiftEventHandlingHarness.ATTACHMENTS.stream()
                    .anyMatch(item -> item.name().startsWith("pod-logs-")));
        }
    }

    @Nested
    @DisplayName("4. Stand-down → fail-fast")
    class StandDownFailFast {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("stand-down Event останавливает второй тест, первый остаётся исходным fail")
        void standDownAbortsRemainingTests() {
            OpenshiftEventHandlingHarness.eventsToReturn =
                    List.of(OpenshiftEventHandlingHarness.event("Maintenance", "stand down"));
            OpenshiftEventHandlingHarness.availability = PodAvailability.standDown(
                    "Maintenance",
                    "stand down",
                    OpenshiftEventHandlingHarness.eventsToReturn);

            var events = runClass(tempDir, OpenshiftEventHandlingHarness.StandDownFailFastSample.class);
            events.assertThatEvents().haveExactly(1,
                    event(test("firstFails"), finishedWithFailure(instanceOf(AssertionError.class))));
            events.assertThatEvents().haveExactly(1,
                    event(test("secondMustNotRun"),
                            finishedWithFailure(message(value -> value != null && value.contains("Stand unavailable")))));
            assertFalse(OpenshiftEventHandlingHarness.SECOND_RAN.get());
            assertTrue(OpenshiftEventHandlingHarness.ATTACHMENTS.stream()
                    .anyMatch(item -> item.name().startsWith("pod-events-")
                            && item.body().contains("Maintenance")));
            assertTrue(OpenshiftEventHandlingHarness.ATTACHMENTS.stream()
                    .anyMatch(item -> item.name().startsWith("pod-logs-")));
        }
    }

    @Nested
    @DisplayName("5. Health-only red → нет fail-fast")
    class HealthRedNoFailFast {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("красный health без stand-down Event не abort'ит прогон и не persist'ит invocation")
        void healthRedDoesNotFailFastAndSkipsPersist() {
            OpenshiftEventHandlingHarness.eventsToReturn = List.of();
            OpenshiftEventHandlingHarness.availability = PodAvailability.healthFailed(
                    PodEventReasons.HEALTH_CHECK_FAILED, "HTTP 503");

            Path db = tempDir.resolve("pod-logger-store.sqlite");
            var events = runClass(tempDir, OpenshiftEventHandlingHarness.HealthRedNoFailFastSample.class);
            events.assertThatEvents().haveExactly(1,
                    event(test("firstFails"), finishedWithFailure(instanceOf(AssertionError.class))));
            events.assertThatEvents().haveExactly(1,
                    event(test("secondRuns"), finishedSuccessfully()));
            assertTrue(OpenshiftEventHandlingHarness.SECOND_RAN.get());
            assertTrue(OpenshiftEventHandlingHarness.ATTACHMENTS.stream()
                    .noneMatch(item -> item.name().startsWith("pod-events-")));

            DataSource dataSource = SqliteDataSourceFactory.create(db);
            SchemaMigrator.migrate(dataSource);
            TestRunRepository runRepository = new SqliteTestRunRepository(dataSource);
            LogStoreRepository logRepository = new SqliteLogStoreRepository(dataSource);
            PodStoreService store = new DefaultPodStoreService(logRepository, runRepository);
            TestRunStore testRunStore = new DefaultTestRunStore(runRepository);
            testRunStore.getTestRuns("event-handling-health-red").stream()
                    .findFirst()
                    .ifPresent(run -> {
                        List<PodLogDto> logs = store.getLogs(run.getId());
                        assertTrue(logs.stream().noneMatch(item -> "firstFails".equals(item.getRelatedTestMethod())));
                    });
        }
    }

    @Nested
    @DisplayName("StandDownEventMatcher")
    class MatcherCases {

        @Test
        @DisplayName("G4: Maintenance / StandUnavailable / Evicted — match")
        void standDownCodesMatch() {
            assertEquals(1, StandDownEventMatcher.match(List.of(podEvent("Maintenance"))).size());
            assertEquals(1, StandDownEventMatcher.match(List.of(podEvent("StandUnavailable"))).size());
            assertEquals(1, StandDownEventMatcher.match(List.of(podEvent("Evicted"))).size());
        }

        @Test
        @DisplayName("G3: TestRunStarted / TestRunFinished / Pulled — не stand-down")
        void lifecycleAndNoiseDoNotMatch() {
            assertTrue(StandDownEventMatcher.match(List.of(podEvent(PodEventReasons.TEST_RUN_STARTED))).isEmpty());
            assertTrue(StandDownEventMatcher.match(List.of(podEvent(PodEventReasons.TEST_RUN_FINISHED))).isEmpty());
            assertTrue(StandDownEventMatcher.match(List.of(podEvent("Pulled"))).isEmpty());
        }

        @Test
        @DisplayName("pattern maintenance в message")
        void messagePatternMatches() {
            PodEventDto dto = PodEventDto.builder()
                    .code("Custom")
                    .reason("Custom")
                    .message("entering maintenance window")
                    .build();
            assertEquals(1, StandDownEventMatcher.match(List.of(dto)).size());
        }
    }

    @Nested
    @DisplayName("PodLogDto.relevantEvents JSON")
    class DtoSerialization {

        @Test
        @DisplayName("поле relevantEvents сериализуется")
        void relevantEventsAppearInJson() throws Exception {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            PodLogDto log = PodLogDto.builder()
                    .message("Unknown SKU")
                    .relevantEvents(List.of(podEvent("Maintenance")))
                    .build();
            String json = mapper.writeValueAsString(log);
            assertTrue(json.contains("relevantEvents"));
            assertTrue(json.contains("Maintenance"));
        }
    }

    private static PodEventDto podEvent(String code) {
        return PodEventDto.builder().code(code).reason(code).message(code).build();
    }

    private void runSample(Path tempDir, Class<?> sample, boolean expectFailure) {
        var events = runClass(tempDir, sample);
        if (expectFailure) {
            events.assertThatEvents().haveExactly(1, event(test(), finishedWithFailure()));
        } else {
            events.assertThatEvents().haveExactly(1, event(test(), finishedSuccessfully()));
        }
    }

    private Events runClass(Path tempDir, Class<?> sample) {
        Path db = tempDir.resolve("pod-logger-store.sqlite");
        String previous = System.getProperty(StorePathResolver.SYSTEM_PROPERTY);
        System.setProperty(StorePathResolver.SYSTEM_PROPERTY, db.toAbsolutePath().toString());
        try {
            return EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(sample))
                    .execute()
                    .testEvents();
        } finally {
            if (previous == null) {
                System.clearProperty(StorePathResolver.SYSTEM_PROPERTY);
            } else {
                System.setProperty(StorePathResolver.SYSTEM_PROPERTY, previous);
            }
        }
    }
}
