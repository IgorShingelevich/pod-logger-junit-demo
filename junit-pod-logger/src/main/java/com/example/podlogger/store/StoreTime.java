package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Единый формат timestamp в SQLite TEXT-колонках: {@code yyyy-MM-dd'T'HH:mm:ss.SSS}
 * (лексикографически сравним как время).
 */
public final class StoreTime {

    /** Форматтер колонок {@code started_at}, {@code timestamp} и т.д. */
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private StoreTime() {
    }

    /**
     * {@link LocalDateTime} → TEXT.
     *
     * @param value момент; {@code null} → {@code null}
     * @return строка формата {@link #FORMATTER}
     */
    public static String format(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(FORMATTER);
    }

    /**
     * TEXT → {@link LocalDateTime}.
     *
     * @param value строка колонки; blank → {@code null}
     * @return момент
     */
    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, FORMATTER);
    }
}
