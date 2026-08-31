package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.dto.LogQuery;
import com.example.podlogger.store.dto.TestRunDto;
import com.example.podlogger.store.repository.LogStoreRepository;
import com.example.podlogger.store.repository.TestRunRepository;

import lombok.RequiredArgsConstructor;

/**
 * Реализация {@link PodStoreService}: валидация, обогащение контекстом run, делегат в repository.
 * SQL не пишет — только {@link LogStoreRepository} / {@link TestRunRepository}.
 */
@Service
@RequiredArgsConstructor
public class DefaultPodStoreService implements PodStoreService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPodStoreService.class);

    private final LogStoreRepository logStoreRepository;
    private final TestRunRepository testRunRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveLogs(List<PodLogDto> logs) {
        if (logs == null || logs.isEmpty()) {
            log.debug("saveLogs(logs) skipped: no log entries provided");
            return;
        }
        for (PodLogDto entry : logs) {
            if (entry.getTestRunId() == null) {
                log.debug("Reject saveLogs(logs): missing testRunId on entry {}", entry);
                throw new IllegalArgumentException("testRunId is required on each log entry");
            }
        }
        log.debug("saveLogs(logs): persisting {} log entries with embedded testRunId", logs.size());
        logStoreRepository.saveAll(logs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveLogs(UUID testRunId, List<PodLogDto> logs) {
        if (testRunId == null) {
            log.debug("Reject saveLogs(testRunId, logs): testRunId is null");
            throw new IllegalArgumentException("testRunId is required");
        }
        if (logs == null || logs.isEmpty()) {
            log.debug("saveLogs(testRunId, logs) skipped: testRunId={} no log entries provided", testRunId);
            return;
        }
        log.debug("saveLogs(testRunId, logs): resolving run metadata for testRunId={} logCount={}", testRunId, logs.size());
        TestRunDto run = testRunRepository.findById(testRunId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown testRunId " + testRunId));
        for (PodLogDto entry : logs) {
            entry.setTestRunId(testRunId);
            entry.setRunName(run.getTestRunName());
            entry.setTestRunName(run.getTestRunName());
            if (entry.getTestSuiteName() == null) {
                entry.setTestSuiteName(run.getTestSuiteName());
            }
            if (entry.getEnvironmentType() == null) {
                entry.setEnvironmentType(run.getEnvironmentType());
            }
            if (entry.getServiceType() == null) {
                entry.setServiceType(run.getServiceType());
            }
            if (entry.getNamespace() == null) {
                entry.setNamespace(run.getNamespace());
            }
            if (entry.getPodLabelSelector() == null) {
                entry.setPodLabelSelector(run.getPodLabelSelector());
            }
            if (entry.getFingerprint() == null) {
                entry.setFingerprint(FingerprintUtil.compute(entry));
            }
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID());
            }
        }
        log.debug("saveLogs(testRunId, logs): persisting {} enriched log entries for run {}", logs.size(), testRunId);
        logStoreRepository.saveAll(logs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs() {
        log.warn("getLogs() without filters scans up to {} rows", 10_000);
        return getLogs(LogQuery.builder().build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs(UUID testRunId) {
        return getLogs(LogQuery.builder().testRunId(testRunId).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to) {
        return getLogs(LogQuery.builder().from(from).to(to).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to, EnvironmentType environmentType) {
        return getLogs(LogQuery.builder().from(from).to(to).environmentType(environmentType).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs(UUID testRunId, EnvironmentType environmentType) {
        return getLogs(LogQuery.builder().testRunId(testRunId).environmentType(environmentType).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs(String testSuiteName, EnvironmentType environmentType) {
        return getLogs(LogQuery.builder().testSuiteName(testSuiteName).environmentType(environmentType).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs(String testRunName, String testSuiteName, EnvironmentType environmentType) {
        return getLogs(LogQuery.builder()
                .testRunName(testRunName)
                .runName(testRunName)
                .testSuiteName(testSuiteName)
                .environmentType(environmentType)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogs(LogQuery query) {
        return logStoreRepository.find(query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PodLogDto> getLogsForWholeRun(UUID testRunId) {
        return getLogs(testRunId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int deleteOlderThan(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be >= 1");
        }
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(days);
        return testRunRepository.deleteClosedOlderThan(cutoff);
    }
}
