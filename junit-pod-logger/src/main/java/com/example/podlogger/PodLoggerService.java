package com.example.podlogger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.allure.LogAllureAttachmentService;
import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.FingerprintUtil;
import com.example.podlogger.store.PodStoreService;
import com.example.podlogger.store.TestRunStore;
import com.example.podlogger.store.dto.MergedLogResult;
import com.example.podlogger.store.dto.TestRunDto;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PodLoggerService {

    private static final Logger log = LoggerFactory.getLogger(PodLoggerService.class);
    static final int SKEW_SECONDS = 2;

    private final OpenshiftClient openshiftClient;
    private final PodLoggerProperties properties;
    private final PodStoreService podStoreService;
    private final TestRunStore testRunStore;
    private final LogAllureAttachmentService attachmentService;

    public void applyAnnotation(PodLogger annotation) {
        properties.setNamespace(annotation.namespace());
        properties.setPodLabelSelector(annotation.podLabelSelector());
        properties.setCollectOnFailOnly(annotation.collectOnFailOnly());
        if (!annotation.testRunName().isBlank()) {
            properties.setTestRunName(annotation.testRunName());
        }
        if (!annotation.testSuiteName().isBlank()) {
            properties.setTestSuiteName(annotation.testSuiteName());
        }
        properties.setEnvironmentType(annotation.environmentType());
        if (!annotation.serviceType().isBlank()) {
            properties.setServiceType(annotation.serviceType());
        }
    }

    public void attachLogsIfNeeded(
            ExtensionContext context,
            UUID testRunId,
            LocalDateTime start,
            LocalDateTime end,
            boolean failed) {
        if (!CollectGate.shouldCollect(properties.isCollectOnFailOnly(), failed)) {
            log.debug("Skip Allure+SQLite for {} because collectOnFailOnly={} and failed={}",
                    context.getDisplayName(), properties.isCollectOnFailOnly(), failed);
            return;
        }
        if (start == null) {
            log.warn("No start timestamp stored for {}", context.getDisplayName());
            return;
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<PodLogDto> window;
        try {
            window = collectRuntimeLogs(start.minusSeconds(SKEW_SECONDS), end.plusSeconds(SKEW_SECONDS));
        } catch (Exception e) {
            log.error("Failed to collect runtime pod logs for {}", context.getDisplayName(), e);
            return;
        }

        enrich(window, context, testRunId, failed);

        try {
            if (testRunId != null) {
                podStoreService.saveLogs(testRunId, window);
            }
        } catch (Exception e) {
            log.error("Failed to persist pod logs for {}", context.getDisplayName(), e);
        }

        attachmentService.attachJson(
                "pod-logs-" + LogAllureAttachmentService.sanitize(context.getDisplayName()),
                window);
        log.info("Attached {} pod log events for {} (window {} .. {})",
                window.size(), context.getDisplayName(),
                start.minusSeconds(SKEW_SECONDS), end.plusSeconds(SKEW_SECONDS));
    }

    public MergedLogResult collectAndMergeLogsForTestRun(UUID testRunId) {
        MergedLogResult empty = MergedLogResult.builder().testRunId(testRunId).build();
        try {
            TestRunDto run = testRunStore.getTestRun(testRunId)
                    .orElseThrow(() -> new IllegalStateException("Unknown testRunId " + testRunId));
            LocalDateTime from = run.getStartedAt().minusSeconds(SKEW_SECONDS);
            LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(SKEW_SECONDS);
            List<PodLogDto> fromPersistent = podStoreService.getLogs(testRunId);
            List<PodLogDto> fromRuntime = collectRuntimeLogs(from, to);
            enrichRunContext(fromRuntime, run, null, null, null, null);

            Map<String, PodLogDto> merged = new LinkedHashMap<>();
            for (PodLogDto entry : fromPersistent) {
                merged.put(dedupKey(entry), entry);
            }
            int inserted = 0;
            for (PodLogDto entry : fromRuntime) {
                String key = dedupKey(entry);
                if (!merged.containsKey(key)) {
                    merged.put(key, entry);
                    inserted++;
                }
            }
            if (inserted > 0) {
                podStoreService.saveLogs(testRunId, fromRuntime);
            }
            List<PodLogDto> mergedList = merged.values().stream()
                    .sorted(Comparator.comparing(PodLogDto::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
            if (properties.isAttachRunSummaryToAllure()) {
                attachmentService.attachJson("pod-logs-run-" + LogAllureAttachmentService.sanitize(run.getTestRunName()),
                        mergedList);
            }
            return MergedLogResult.builder()
                    .testRunId(testRunId)
                    .fromPersistent(fromPersistent)
                    .fromRuntime(fromRuntime)
                    .merged(mergedList)
                    .insertedNewCount(inserted)
                    .build();
        } catch (Exception e) {
            log.error("collectAndMergeLogsForTestRun failed for {}", testRunId, e);
            return empty;
        }
    }

    public List<PodLogDto> collectRuntimeLogs(LocalDateTime from, LocalDateTime to) {
        return openshiftClient.getLog().stream()
                .filter(entry -> entry.getTimestamp() != null)
                .filter(entry -> !entry.getTimestamp().isBefore(from) && !entry.getTimestamp().isAfter(to))
                .collect(Collectors.toList());
    }

    String resolveTestRunName(Class<?> testClass) {
        if (properties.getTestRunName() != null && !properties.getTestRunName().isBlank()) {
            return properties.getTestRunName();
        }
        String stamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return testClass.getSimpleName() + "-" + stamp;
    }

    String resolveTestSuiteName(Class<?> testClass) {
        if (properties.getTestSuiteName() != null && !properties.getTestSuiteName().isBlank()) {
            return properties.getTestSuiteName();
        }
        return testClass.getName();
    }

    String resolveServiceType() {
        if (properties.getServiceType() != null && !properties.getServiceType().isBlank()) {
            return properties.getServiceType();
        }
        String selector = properties.getPodLabelSelector();
        int eq = selector == null ? -1 : selector.indexOf('=');
        return eq > 0 ? selector.substring(eq + 1) : selector;
    }

    private void enrich(List<PodLogDto> logs, ExtensionContext context, UUID testRunId, boolean failed) {
        String testClass = context.getRequiredTestClass().getName();
        String testMethod = context.getTestMethod().map(method -> method.getName()).orElse(null);
        String displayName = context.getDisplayName();
        TestRunDto run = testRunId == null ? null : testRunStore.getTestRun(testRunId).orElse(null);
        enrichRunContext(logs, run, testClass, testMethod, displayName, failed);
    }

    private void enrichRunContext(
            List<PodLogDto> logs,
            TestRunDto run,
            String testClass,
            String testMethod,
            String displayName,
            Boolean failed) {
        for (PodLogDto entry : logs) {
            if (run != null) {
                entry.setTestRunId(run.getId());
                entry.setRunName(run.getTestRunName());
                entry.setTestRunName(run.getTestRunName());
                entry.setTestSuiteName(run.getTestSuiteName());
                entry.setEnvironmentType(run.getEnvironmentType());
                if (entry.getServiceType() == null) {
                    entry.setServiceType(run.getServiceType());
                }
                if (entry.getNamespace() == null) {
                    entry.setNamespace(run.getNamespace());
                }
                if (entry.getPodLabelSelector() == null) {
                    entry.setPodLabelSelector(run.getPodLabelSelector());
                }
            }
            if (testClass != null) {
                entry.setRelatedTestClass(testClass);
            }
            if (testMethod != null) {
                entry.setRelatedTestMethod(testMethod);
            }
            if (displayName != null) {
                entry.setTestDisplayName(displayName);
            }
            if (failed != null) {
                entry.setTestFailed(failed);
            }
            if (entry.getFingerprint() == null) {
                entry.setFingerprint(FingerprintUtil.compute(entry));
            }
        }
    }

    private static String dedupKey(PodLogDto entry) {
        String fingerprint = entry.getFingerprint() == null ? FingerprintUtil.compute(entry) : entry.getFingerprint();
        return StoreTimeSafe(entry) + "|" + fingerprint;
    }

    private static String StoreTimeSafe(PodLogDto entry) {
        return entry.getTimestamp() == null ? "" : entry.getTimestamp().toString();
    }
}
