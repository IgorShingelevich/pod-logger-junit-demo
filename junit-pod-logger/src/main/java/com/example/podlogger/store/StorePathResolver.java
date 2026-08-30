package com.example.podlogger.store;

import java.nio.file.Path;
import java.util.Optional;

import com.example.podlogger.PodLoggerProperties;

/**
 * Резолв пути к файлу SQLite. Первый найденный источник выигрывает:
 * <ol>
 *   <li>system property {@value #SYSTEM_PROPERTY};</li>
 *   <li>env {@value #ENV_VARIABLE};</li>
 *   <li>{@link PodLoggerProperties#getStorePath()};</li>
 *   <li>дефолт {@code {user.dir}/target/pod-logger-store.sqlite}.</li>
 * </ol>
 */
public final class StorePathResolver {

    /** JVM property переопределения пути. */
    public static final String SYSTEM_PROPERTY = "pod.logger.store-path";
    /** Переменная окружения переопределения пути. */
    public static final String ENV_VARIABLE = "POD_LOGGER_STORE_PATH";

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private StorePathResolver() {
    }

    /**
     * Абсолютный путь к {@code *.sqlite}.
     *
     * @param properties fallback из Spring; может быть {@code null}
     * @return абсолютный Path
     */
    public static Path resolve(PodLoggerProperties properties) {
        String fromProperty = System.getProperty(SYSTEM_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Path.of(fromProperty).toAbsolutePath();
        }
        String fromEnv = Optional.ofNullable(System.getenv(ENV_VARIABLE)).orElse("");
        if (!fromEnv.isBlank()) {
            return Path.of(fromEnv).toAbsolutePath();
        }
        if (properties != null && properties.getStorePath() != null && !properties.getStorePath().isBlank()) {
            return Path.of(properties.getStorePath()).toAbsolutePath();
        }
        return Path.of(System.getProperty("user.dir"), "target", "pod-logger-store.sqlite").toAbsolutePath();
    }
}
