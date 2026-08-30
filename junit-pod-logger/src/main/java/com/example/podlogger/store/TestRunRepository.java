package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.podlogger.store.dto.TestRunDto;

public interface TestRunRepository {

    UUID insert(TestRunDto draft);

    void finish(UUID testRunId, LocalDateTime finishedAt);

    Optional<TestRunDto> findById(UUID testRunId);

    List<TestRunDto> findByName(String testRunName);

    List<TestRunDto> findByStartedBetween(LocalDateTime from, LocalDateTime to);

    List<TestRunDto> findByEnvironment(EnvironmentType environmentType);

    int deleteClosedOlderThan(LocalDateTime cutoff);
}
