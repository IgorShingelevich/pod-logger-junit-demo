package com.example.podlogger.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMeta;

/**
 * Maps fabric8 core/v1 {@link Event} to {@link PodEventDto}. Package-visible helpers
 * are tested without a cluster.
 */
public final class PodEventMapper {

    private PodEventMapper() {
    }

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

    public static boolean inWindow(PodEventDto dto, LocalDateTime from, LocalDateTime to) {
        if (dto == null || dto.getTimestamp() == null || from == null || to == null) {
            return false;
        }
        return !dto.getTimestamp().isBefore(from) && !dto.getTimestamp().isAfter(to);
    }

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
