package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class StoreTime {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private StoreTime() {
    }

    public static String format(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(FORMATTER);
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, FORMATTER);
    }
}
