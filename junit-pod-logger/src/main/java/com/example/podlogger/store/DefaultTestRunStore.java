package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            throw new IllegalArgumentException("testRunName is required");
        }
        if (draft.getEnvironmentType() == null) {
            throw new IllegalArgumentException("environmentType is required");
        }
        if (draft.getStartedAt() == null) {
            draft.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        draft.setStatus("STARTED");
        return testRunRepository.insert(draft);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finishTestRun(UUID testRunId) {
        testRunRepository.finish(testRunId, LocalDateTime.now(ZoneOffset.UTC));
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
