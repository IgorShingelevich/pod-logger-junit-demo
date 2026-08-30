package com.example.podlogger.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.example.podlogger.client.PodLogDto;

public final class FingerprintUtil {

    private FingerprintUtil() {
    }

    public static String compute(PodLogDto log) {
        String payload = coalesce(log.getLevel()) + '\n'
                + coalesce(log.getLogger()) + '\n'
                + coalesce(log.getMessage()) + '\n'
                + coalesce(log.getStackTrace());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String coalesce(String value) {
        return value == null ? "" : value;
    }
}
