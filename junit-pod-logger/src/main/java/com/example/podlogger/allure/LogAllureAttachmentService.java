package com.example.podlogger.allure;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.client.PodEventDto;
import com.example.podlogger.client.PodLogDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LogAllureAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(LogAllureAttachmentService.class);

    private final ObjectMapper objectMapper;
    private final AllureSink allureSink;

    public void attachJson(String attachmentName, List<PodLogDto> logs) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logs);
            allureSink.addAttachment(attachmentName, "application/json", json, ".json");
        } catch (Exception e) {
            log.error("Failed to attach pod logs to Allure as {}", attachmentName, e);
        }
    }

    public void attachEvents(String attachmentName, List<PodEventDto> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(events);
            allureSink.addAttachment(attachmentName, "application/json", json, ".json");
        } catch (Exception e) {
            log.error("Failed to attach pod events to Allure as {}", attachmentName, e);
        }
    }

    public static String sanitize(String displayName) {
        return displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
