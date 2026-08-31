package com.example.podlogger.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMeta;

/**
 * Маппер fabric8 core/v1 {@link Event} → {@link PodEventDto}.
 *
 * <p>Маппер intentionally проецирует только библиотечный минимум. Если в другом кластере
 * или API-адаптере придут иные поля/типы времени, debug-лог должен показать,
 * на каком этапе теряется значение.
 *
 * <p>Без состояния и без кластера: юнит-тесты вызывают package-visible {@link #parseTime}.
 */
public final class PodEventMapper {

    private static final Logger log = LoggerFactory.getLogger(PodEventMapper.class);
    private static final int MAX_DEBUG_SNIPPET = 200;

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private PodEventMapper() {
    }

    /**
     * Проецирует Event в DTO. {@code code} и {@code reason} = {@code event.reason}.
     *
     * @param event     fabric8 Event; {@code null} → {@code null}
     * @param podName   имя поды (involvedObject)
     * @param namespace namespace
     * @return DTO или {@code null}
     */
    public static PodEventDto toDto(Event event, String podName, String namespace) {
        if (event == null) {
            log.debug("Skip Event -> DTO mapping: raw Event is null");
            return null;
        }
        String reason = event.getReason();
        ObjectMeta meta = event.getMetadata();
        PodEventDto dto = PodEventDto.builder()
                .code(reason)
                .reason(reason)
                .type(event.getType())
                .message(event.getMessage())
                .timestamp(timestampOf(event))
                .count(event.getCount())
                .podName(podName)
                .namespace(namespace)
                .uid(meta == null ? null : meta.getUid())
                .build();
        log.debug(
                "Mapped Event -> DTO: reason={}, type={}, timestamp={}, pod={}, namespace={}, message={}",
                dto.getReason(),
                dto.getType(),
                dto.getTimestamp(),
                dto.getPodName(),
                dto.getNamespace(),
                abbreviate(dto.getMessage()));
        return dto;
    }

    /**
     * Timestamp Event: {@code lastTimestamp}, иначе {@code eventTime}, иначе {@code creationTimestamp}.
     *
     * @param event fabric8 Event
     * @return UTC LocalDateTime или {@code null}
     */
    public static LocalDateTime timestampOf(Event event) {
        if (event == null) {
            log.debug("Event timestamp resolution skipped: raw Event is null");
            return null;
        }
        LocalDateTime fromLast = parseTime(event.getLastTimestamp());
        if (fromLast != null) {
            log.debug("Resolved Event timestamp from lastTimestamp: {}", fromLast);
            return fromLast;
        }
        if (event.getEventTime() != null && event.getEventTime().getTime() != null) {
            LocalDateTime fromEventTime = parseTime(event.getEventTime().getTime());
            if (fromEventTime != null) {
                log.debug("Resolved Event timestamp from eventTime: {}", fromEventTime);
                return fromEventTime;
            }
        }
        ObjectMeta meta = event.getMetadata();
        if (meta != null) {
            LocalDateTime fromCreation = parseTime(meta.getCreationTimestamp());
            if (fromCreation != null) {
                log.debug("Resolved Event timestamp from creationTimestamp: {}", fromCreation);
            } else {
                log.debug(
                        "Failed to resolve Event timestamp from all sources: lastTimestamp={}, eventTime={}, creationTimestamp={}",
                        event.getLastTimestamp(),
                        event.getEventTime() == null ? null : event.getEventTime().getTime(),
                        meta.getCreationTimestamp());
            }
            return fromCreation;
        }
        log.debug("Failed to resolve Event timestamp: metadata is null and no other timestamp parsed");
        return null;
    }

    /**
     * Входит ли timestamp DTO в закрытый интервал {@code [from, to]}.
     * {@code null} у любой стороны → {@code false}.
     *
     * @param dto  событие
     * @param from нижняя граница
     * @param to   верхняя граница
     * @return {@code true} если timestamp внутри окна
     */
    public static boolean inWindow(PodEventDto dto, LocalDateTime from, LocalDateTime to) {
        if (dto == null || dto.getTimestamp() == null || from == null || to == null) {
            return false;
        }
        return !dto.getTimestamp().isBefore(from) && !dto.getTimestamp().isAfter(to);
    }

    /**
     * Разбор RFC3339 / OffsetDateTime / LocalDateTime из строкового поля k8s.
     *
     * @param raw значение API
     * @return UTC LocalDateTime или {@code null}
     */
    static LocalDateTime parseTime(Object raw) {
        if (raw == null) {
            log.debug("Skip Event time parse: raw timestamp is null");
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty() || "null".equals(text)) {
            log.debug("Skip Event time parse: raw timestamp is blank or literal null");
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            log.debug("Unable to parse Event time value: {}", abbreviate(text));
            return null;
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        String singleLine = value.replace("\r", "\\r").replace("\n", "\\n");
        if (singleLine.length() <= MAX_DEBUG_SNIPPET) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_DEBUG_SNIPPET) + "...";
    }
}
