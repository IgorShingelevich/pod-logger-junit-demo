package com.example.podlogger.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.example.podlogger.client.PodLogDto;

/**
 * SHA-256 fingerprint записи лога для идемпотентного INSERT.
 *
 * <p>В payload входят только {@code level}, {@code logger}, {@code message}, {@code stackTrace}.
 * {@code relevantEvents} и поля прогона намеренно не входят: иначе дедуп сломается
 * при повторном save того же stdout и Events не попадут в БД.
 */
public final class FingerprintUtil {

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private FingerprintUtil() {
    }

    /**
     * Считает hex SHA-256. {@code null}-поля считаются пустой строкой.
     *
     * @param log запись
     * @return 64 hex-символа
     */
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

    /**
     * {@code null} → {@code ""}.
     *
     * @param value исходная строка
     * @return не-{@code null}
     */
    private static String coalesce(String value) {
        return value == null ? "" : value;
    }
}
