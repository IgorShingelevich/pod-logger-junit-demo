package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.podlogger.store.dto.TestRunDto;

public interface TestRunStore {

    UUID startTestRun(
            String testRunName,
            String testSuiteName,
            EnvironmentType environmentType,
            String serviceType);

    UUID startTestRun(TestRunDto draft);

    void finishTestRun(UUID testRunId);

    Optional<TestRunDto> getTestRun(UUID testRunId);

    List<TestRunDto> getTestRuns(String testRunName);

    List<TestRunDto> getTestRuns(LocalDateTime from, LocalDateTime to);

    List<TestRunDto> getTestRuns(EnvironmentType environmentType);
}
