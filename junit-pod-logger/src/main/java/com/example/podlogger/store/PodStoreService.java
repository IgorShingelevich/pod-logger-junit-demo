package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.dto.LogQuery;

public interface PodStoreService {

    void saveLogs(List<PodLogDto> logs);

    void saveLogs(UUID testRunId, List<PodLogDto> logs);

    List<PodLogDto> getLogs();

    List<PodLogDto> getLogs(UUID testRunId);

    List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to);

    List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to, EnvironmentType environmentType);

    List<PodLogDto> getLogs(UUID testRunId, EnvironmentType environmentType);

    List<PodLogDto> getLogs(String testSuiteName, EnvironmentType environmentType);

    List<PodLogDto> getLogs(String testRunName, String testSuiteName, EnvironmentType environmentType);

    List<PodLogDto> getLogs(LogQuery query);

    List<PodLogDto> getLogsForWholeRun(UUID testRunId);

    int deleteOlderThan(int days);
}
