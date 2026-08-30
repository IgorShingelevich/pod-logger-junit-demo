package com.example.podlogger.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMeta;

/**
 * Маппер fabric8 core/v1 {@link Event} → {@link PodEventDto}.
 * Без состояния и без кластера: юнит-тесты вызывают package-visible {@link #parseTime}.
 */
public final class PodEventMapper {

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
            return null;
        }
        String reason = event.getReason();
        ObjectMeta meta = event.getMetadata();
        return PodEventDto.builder()
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
    }

    /**
     * Timestamp Event: {@code lastTimestamp}, иначе {@code eventTime}, иначе {@code creationTimestamp}.
     *
     * @param event fabric8 Event
     * @return UTC LocalDateTime или {@code null}
     */
    public static LocalDateTime timestampOf(Event event) {
        if (event == null) {
            return null;
        }
        LocalDateTime fromLast = parseTime(event.getLastTimestamp());
        if (fromLast != null) {
            return fromLast;
        }
        if (event.getEventTime() != null && event.getEventTime().getTime() != null) {
            LocalDateTime fromEventTime = parseTime(event.getEventTime().getTime());
            if (fromEventTime != null) {
                return fromEventTime;
            }
        }
        ObjectMeta meta = event.getMetadata();
        if (meta != null) {
            return parseTime(meta.getCreationTimestamp());
        }
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
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty() || "null".equals(text)) {
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
            return null;
        }
    }
}
