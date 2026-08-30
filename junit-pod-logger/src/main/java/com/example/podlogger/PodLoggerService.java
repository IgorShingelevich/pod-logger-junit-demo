package com.example.podlogger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.client.PodLogDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.qameta.allure.Allure;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PodLoggerService {

    private static final Logger log = LoggerFactory.getLogger(PodLoggerService.class);
    private static final int SKEW_SECONDS = 2;

    private final OpenshiftClient openshiftClient;
    private final PodLoggerProperties properties;
    private final ObjectMapper objectMapper;

    public void applyAnnotation(PodLogger annotation) {
        properties.setNamespace(annotation.namespace());
        properties.setPodLabelSelector(annotation.podLabelSelector());
        properties.setCollectOnFailOnly(annotation.collectOnFailOnly());
    }

    public void attachLogsIfNeeded(ExtensionContext context, LocalDateTime start, LocalDateTime end, boolean failed) {
        if (properties.isCollectOnFailOnly() && !failed) {
            log.debug("Skipping pod logs for {} because collectOnFailOnly=true and the test passed",
                    context.getDisplayName());
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
        List<PodLogDto> podLogs = openshiftClient.getLog();
        LocalDateTime from = start.minusSeconds(SKEW_SECONDS);
        LocalDateTime to = end.plusSeconds(SKEW_SECONDS);
        List<PodLogDto> window = podLogs.stream()
                .filter(entry -> entry.getTimestamp() != null)
                .filter(entry -> !entry.getTimestamp().isBefore(from) && !entry.getTimestamp().isAfter(to))
                .collect(Collectors.toList());

        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(window);
            String name = "pod-logs-" + sanitize(context.getDisplayName());
            Allure.addAttachment(name, "application/json", json, ".json");
            log.info("Attached {} pod log events for {} (window {} .. {})", window.size(),
                    context.getDisplayName(), from, to);
        } catch (Exception e) {
            log.error("Failed to attach pod logs to Allure", e);
        }
    }

    private static String sanitize(String displayName) {
        return displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
