package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.podlogger.store.dto.TestRunDto;
import com.example.podlogger.store.repository.TestRunRepository;

import lombok.RequiredArgsConstructor;

/**
 * Реализация {@link TestRunStore}: валидация draft и делегат в {@link TestRunRepository}.
 */
@Service
@RequiredArgsConstructor
public class DefaultTestRunStore implements TestRunStore {

    private static final Logger log = LoggerFactory.getLogger(DefaultTestRunStore.class);

    private final TestRunRepository testRunRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID startTestRun(
            String testRunName,
            String testSuiteName,
            EnvironmentType environmentType,
            String serviceType) {
        return startTestRun(TestRunDto.builder()
                .testRunName(testRunName)
                .testSuiteName(testSuiteName)
                .environmentType(environmentType)
                .serviceType(serviceType)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID startTestRun(TestRunDto draft) {
        if (draft.getTestRunName() == null || draft.getTestRunName().isBlank()) {
            log.debug("Reject startTestRun: testRunName is blank");
            throw new IllegalArgumentException("testRunName is required");
        }
        if (draft.getEnvironmentType() == null) {
            log.debug("Reject startTestRun: environmentType is null for {}", draft.getTestRunName());
            throw new IllegalArgumentException("environmentType is required");
        }
        if (draft.getStartedAt() == null) {
            draft.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        draft.setStatus("STARTED");
        log.debug("startTestRun: name={} suite={} environment={} serviceType={} namespace={} selector={} startedAt={}",
                draft.getTestRunName(),
                draft.getTestSuiteName(),
                draft.getEnvironmentType(),
                draft.getServiceType(),
                draft.getNamespace(),
                draft.getPodLabelSelector(),
                draft.getStartedAt());
        return testRunRepository.insert(draft);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finishTestRun(UUID testRunId) {
        LocalDateTime finishedAt = LocalDateTime.now(ZoneOffset.UTC);
        log.debug("finishTestRun: testRunId={} finishedAt={}", testRunId, finishedAt);
        testRunRepository.finish(testRunId, finishedAt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TestRunDto> getTestRun(UUID testRunId) {
        return testRunRepository.findById(testRunId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TestRunDto> getTestRuns(String testRunName) {
        return testRunRepository.findByName(testRunName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TestRunDto> getTestRuns(LocalDateTime from, LocalDateTime to) {
        return testRunRepository.findByStartedBetween(from, to);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TestRunDto> getTestRuns(EnvironmentType environmentType) {
        return testRunRepository.findByEnvironment(environmentType);
    }
}
