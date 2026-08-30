package com.example.podlogger.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineTestKit;

import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.dto.LogQuery;
import com.example.podlogger.store.dto.TestRunDto;
import com.example.podlogger.store.sqlite.SchemaMigrator;
import com.example.podlogger.store.sqlite.SqliteDataSourceFactory;
import com.example.podlogger.store.sqlite.SqliteLogStoreRepository;
import com.example.podlogger.store.sqlite.SqliteTestRunRepository;

@DisplayName("persistent log store test")
class PersistentLogStoreTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 10, 0, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 1, 11, 0, 0, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 1, 1, 12, 0, 0, 0);

    @TempDir
    Path tempDir;

    private DataSource dataSource;
    private TestRunStore testRunStore;
    private PodStoreService podStoreService;
    private UUID stRunId;
    private UUID devRunId;

    @BeforeEach
    void setUp() {
        Path db = tempDir.resolve("pod-logger-store.sqlite");
        dataSource = SqliteDataSourceFactory.create(db);
        SchemaMigrator.migrate(dataSource);
        TestRunRepository runRepository = new SqliteTestRunRepository(dataSource);
        LogStoreRepository logRepository = new SqliteLogStoreRepository(dataSource);
        testRunStore = new DefaultTestRunStore(runRepository);
        podStoreService = new DefaultPodStoreService(logRepository, runRepository);
        seed();
    }

    @Test
    @DisplayName("запись и чтение: start/finish test run")
    void shouldStartAndFinishTestRun() {
        TestRunDto run = testRunStore.getTestRun(stRunId).orElseThrow();
        assertNotNull(run.getStartedAt());
        assertEquals("STARTED", run.getStatus());
        testRunStore.finishTestRun(stRunId);
        TestRunDto finished = testRunStore.getTestRun(stRunId).orElseThrow();
        assertNotNull(finished.getFinishedAt());
        assertEquals("FINISHED", finished.getStatus());
    }

    @Test
    @DisplayName("запись и чтение: saveLogs / getLogs(testRunId)")
    void shouldSaveLogsAndGetByTestRunId() {
        List<PodLogDto> logs = podStoreService.getLogs(stRunId);
        assertEquals(2, logs.size());
        assertEquals("Unknown SKU", logs.get(0).getMessage());
        assertEquals("started", logs.get(1).getMessage());
        assertEquals("st-regression", logs.get(0).getRunName());
        assertEquals("st-regression", logs.get(0).getTestRunName());
        assertEquals(EnvironmentType.ST, logs.get(0).getEnvironmentType());
    }

    @Test
    @DisplayName("фильтр по времени")
    void shouldFilterByTimeRange() {
        List<PodLogDto> logs = podStoreService.getLogs(T1, T2);
        assertEquals(2, logs.size());
        assertTrue(logs.stream().noneMatch(item -> item.getTimestamp().isAfter(T2)));
    }

    @Test
    @DisplayName("фильтр по environment")
    void shouldFilterByEnvironment() {
        List<PodLogDto> st = podStoreService.getLogs(T1, T3, EnvironmentType.ST);
        List<PodLogDto> dev = podStoreService.getLogs(devRunId, EnvironmentType.DEV);
        List<PodLogDto> mismatch = podStoreService.getLogs(stRunId, EnvironmentType.DEV);
        assertEquals(2, st.size());
        assertTrue(st.stream().allMatch(item -> item.getEnvironmentType() == EnvironmentType.ST));
        assertEquals(1, dev.size());
        assertEquals("Payment declined", dev.get(0).getMessage());
        assertTrue(mismatch.isEmpty());
    }

    @Test
    @DisplayName("фильтр suite + environment")
    void shouldFilterBySuiteAndEnvironment() {
        List<PodLogDto> logs = podStoreService.getLogs("orders-suite", EnvironmentType.ST);
        assertEquals(2, logs.size());
    }

    @Test
    @DisplayName("фильтр runName + suite + environment")
    void shouldFilterByRunNameSuiteEnvironment() {
        List<PodLogDto> logs = podStoreService.getLogs("st-regression", "orders-suite", EnvironmentType.ST);
        assertEquals(2, logs.size());
        assertTrue(podStoreService.getLogs("st-regression", "orders-suite", EnvironmentType.DEV).isEmpty());
    }

    @Test
    @DisplayName("фильтр relatedTestClass / relatedTestMethod")
    void shouldFilterByRelatedTestClassAndMethod() {
        List<PodLogDto> logs = podStoreService.getLogs(LogQuery.builder()
                .relatedTestClass("com.example.demotest.OrderErrorIT")
                .relatedTestMethod("apiErrorIsLoggedOnPod")
                .build());
        assertEquals(2, logs.size());
    }

    @Test
    @DisplayName("комбинированный LogQuery")
    void shouldFilterByLogQueryCombined() {
        List<PodLogDto> logs = podStoreService.getLogs(LogQuery.builder()
                .environmentType(EnvironmentType.ST)
                .level("ERROR")
                .messageContains("SKU")
                .build());
        assertEquals(1, logs.size());
        assertEquals("Unknown SKU", logs.get(0).getMessage());
    }

    @Test
    @DisplayName("getLogsForWholeRun")
    void shouldGetLogsForWholeRun() {
        assertEquals(2, podStoreService.getLogsForWholeRun(stRunId).size());
        assertEquals(1, podStoreService.getLogsForWholeRun(devRunId).size());
    }

    @Test
    @DisplayName("повторный save не создаёт дубли")
    void shouldDeduplicateOnResave() {
        podStoreService.saveLogs(stRunId, List.of(stError()));
        assertEquals(2, podStoreService.getLogs(stRunId).size());
    }

    @Test
    @DisplayName("save без testRunId отклоняется")
    void shouldRejectSaveWithoutTestRunId() {
        PodLogDto orphan = PodLogDto.builder()
                .timestamp(T1)
                .level("ERROR")
                .message("orphan")
                .build();
        assertThrows(IllegalArgumentException.class, () -> podStoreService.saveLogs(List.of(orphan)));
    }

    @Test
    @DisplayName("deleteOlderThan удаляет только закрытые старые run")
    void shouldDeleteOlderThanRemovesClosedRunsOnly() throws Exception {
        testRunStore.finishTestRun(stRunId);
        UUID openOld = testRunStore.startTestRun("open-old", "orders-suite", EnvironmentType.FT, "demo-api");
        backdateStartedAt(stRunId, LocalDateTime.now(ZoneOffset.UTC).minusDays(40));
        backdateStartedAt(openOld, LocalDateTime.now(ZoneOffset.UTC).minusDays(40));

        int deleted = podStoreService.deleteOlderThan(30);
        assertEquals(1, deleted);
        assertTrue(testRunStore.getTestRun(stRunId).isEmpty());
        assertTrue(podStoreService.getLogs(stRunId).isEmpty());
        assertTrue(testRunStore.getTestRun(openOld).isPresent());
        assertTrue(testRunStore.getTestRun(devRunId).isPresent());
    }

    @Test
    @DisplayName("зелёный класс с collectOnFailOnly не пишет log_entry")
    void shouldSkipPersistWhenCollectOnFailOnlyAndPassed() {
        Path db = tempDir.resolve("passed.sqlite");
        runWithStore(db, PersistentLogStoreHarness.PassingLoggedSample.class, false);
        StoreBundle bundle = open(db);
        List<PodLogDto> logs = bundle.store.getLogs(LogQuery.builder()
                .relatedTestClass(PersistentLogStoreHarness.PassingLoggedSample.class.getName())
                .build());
        assertTrue(logs.isEmpty());
        assertFalse(bundle.runs.getTestRuns("persistent-log-store-passed").isEmpty());
    }

    @Test
    @DisplayName("упавший класс с @PodLogger пишет строки, которые читаются из SQLite")
    void extensionOnFailedClassPersistsLogsThatCanBeRead() {
        Path db = tempDir.resolve("failed.sqlite");
        runWithStore(db, PersistentLogStoreHarness.FailedLoggedSample.class, true);

        StoreBundle bundle = open(db);
        List<PodLogDto> logs = bundle.store.getLogs(LogQuery.builder()
                .relatedTestClass(PersistentLogStoreHarness.FailedLoggedSample.class.getName())
                .relatedTestMethod("failsOnPurpose")
                .environmentType(EnvironmentType.LOCAL)
                .build());
        assertFalse(logs.isEmpty(), "extension must persist pod logs for a failed test class");
        assertEquals("ERROR", logs.get(0).getLevel());
        assertEquals("Unknown SKU", logs.get(0).getMessage());
        assertEquals("persistent-log-store-failed", logs.get(0).getRunName());
        assertEquals(PersistentLogStoreHarness.FailedLoggedSample.class.getName(), logs.get(0).getRelatedTestClass());
        assertEquals("failsOnPurpose", logs.get(0).getRelatedTestMethod());
        assertEquals(Boolean.TRUE, logs.get(0).getTestFailed());

        List<TestRunDto> runs = bundle.runs.getTestRuns("persistent-log-store-failed");
        assertEquals(1, runs.size());
        assertEquals("FINISHED", runs.get(0).getStatus());
        assertNotNull(runs.get(0).getFinishedAt());
    }

    private void seed() {
        stRunId = testRunStore.startTestRun(TestRunDto.builder()
                .testRunName("st-regression")
                .testSuiteName("orders-suite")
                .environmentType(EnvironmentType.ST)
                .serviceType("demo-api")
                .namespace("default")
                .podLabelSelector("app=demo-api")
                .build());
        devRunId = testRunStore.startTestRun(TestRunDto.builder()
                .testRunName("dev-smoke")
                .testSuiteName("orders-suite")
                .environmentType(EnvironmentType.DEV)
                .serviceType("demo-api")
                .build());
        podStoreService.saveLogs(stRunId, List.of(stError(), stInfo()));
        podStoreService.saveLogs(devRunId, List.of(devError()));
    }

    private PodLogDto stError() {
        return PodLogDto.builder()
                .timestamp(T1)
                .level("ERROR")
                .message("Unknown SKU")
                .logger("com.example.demoapp.OrderController")
                .relatedTestClass("com.example.demotest.OrderErrorIT")
                .relatedTestMethod("apiErrorIsLoggedOnPod")
                .testDisplayName("UNKNOWN_SKU")
                .testFailed(true)
                .build();
    }

    private PodLogDto stInfo() {
        return PodLogDto.builder()
                .timestamp(T2)
                .level("INFO")
                .message("started")
                .logger("demo")
                .relatedTestClass("com.example.demotest.OrderErrorIT")
                .relatedTestMethod("apiErrorIsLoggedOnPod")
                .testDisplayName("UNKNOWN_SKU")
                .testFailed(true)
                .build();
    }

    private PodLogDto devError() {
        return PodLogDto.builder()
                .timestamp(T3)
                .level("ERROR")
                .message("Payment declined")
                .logger("com.example.demoapp.OrderController")
                .relatedTestClass("com.example.demotest.OtherIT")
                .relatedTestMethod("payment")
                .testFailed(true)
                .build();
    }

    private void backdateStartedAt(UUID testRunId, LocalDateTime startedAt) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE test_run SET started_at = ? WHERE id = ?")) {
            statement.setString(1, StoreTime.format(startedAt));
            statement.setString(2, testRunId.toString());
            statement.executeUpdate();
        }
    }

    private void runWithStore(Path db, Class<?> sample, boolean expectFailure) {
        String previous = System.getProperty(StorePathResolver.SYSTEM_PROPERTY);
        System.setProperty(StorePathResolver.SYSTEM_PROPERTY, db.toAbsolutePath().toString());
        try {
            var events = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(sample))
                    .execute()
                    .testEvents();
            if (expectFailure) {
                events.assertThatEvents().haveExactly(1, event(test(), finishedWithFailure()));
            } else {
                events.assertThatEvents().haveExactly(1, event(test(), finishedSuccessfully()));
            }
        } finally {
            if (previous == null) {
                System.clearProperty(StorePathResolver.SYSTEM_PROPERTY);
            } else {
                System.setProperty(StorePathResolver.SYSTEM_PROPERTY, previous);
            }
        }
    }

    private StoreBundle open(Path db) {
        DataSource ds = SqliteDataSourceFactory.create(db);
        SchemaMigrator.migrate(ds);
        TestRunRepository runRepository = new SqliteTestRunRepository(ds);
        LogStoreRepository logRepository = new SqliteLogStoreRepository(ds);
        return new StoreBundle(
                new DefaultTestRunStore(runRepository),
                new DefaultPodStoreService(logRepository, runRepository));
    }

    private record StoreBundle(TestRunStore runs, PodStoreService store) {
    }
}
