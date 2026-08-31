package com.example.podlogger.allure;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.client.PodEventDto;
import com.example.podlogger.client.PodLogDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Сериализует логи и Events в pretty JSON и отдаёт в {@link AllureSink}.
 * Persistence не выполняет. Ошибки сериализации/аттача глотаются: тест из-за Allure не краснеет.
 */
@Component
@RequiredArgsConstructor
public class LogAllureAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(LogAllureAttachmentService.class);

    private final ObjectMapper objectMapper;
    private final AllureSink allureSink;

    /**
     * Аттач {@code pod-logs-*}. Пустой список всё равно сериализуется в {@code []}
     * (вызов идёт только если collect решил аттачить).
     *
     * @param attachmentName имя аттача
     * @param logs           срез окна; может содержать {@code relevantEvents}
     */
    public void attachJson(String attachmentName, List<PodLogDto> logs) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logs);
            log.debug("Attaching pod logs to Allure: attachmentName={} logCount={} jsonLength={}",
                    attachmentName, logs == null ? 0 : logs.size(), json.length());
            allureSink.addAttachment(attachmentName, "application/json", json, ".json");
        } catch (Exception e) {
            log.error("Failed to attach pod logs to Allure as {}", attachmentName, e);
        }
    }

    /**
     * Аттач {@code pod-events-*}. {@code null} или пустой список — no-op
     * (пустой Events-аттач запрещён контрактом).
     *
     * @param attachmentName имя аттача
     * @param events         Events окна fail
     */
    public void attachEvents(String attachmentName, List<PodEventDto> events) {
        if (events == null || events.isEmpty()) {
            log.debug("Skip Allure events attachment: attachmentName={} because event list is empty", attachmentName);
            return;
        }
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(events);
            log.debug("Attaching pod events to Allure: attachmentName={} eventCount={} jsonLength={}",
                    attachmentName, events.size(), json.length());
            allureSink.addAttachment(attachmentName, "application/json", json, ".json");
        } catch (Exception e) {
            log.error("Failed to attach pod events to Allure as {}", attachmentName, e);
        }
    }

    /**
     * Заменяет символы, небезопасные в имени аттача, на {@code _}.
     *
     * @param displayName JUnit display name
     * @return sanitized строка
     */
    public static String sanitize(String displayName) {
        return displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
