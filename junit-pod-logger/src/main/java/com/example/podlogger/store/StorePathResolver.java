package com.example.podlogger.store;

import java.nio.file.Path;
import java.util.Optional;

import com.example.podlogger.PodLoggerProperties;

public final class StorePathResolver {

    public static final String SYSTEM_PROPERTY = "pod.logger.store-path";
    public static final String ENV_VARIABLE = "POD_LOGGER_STORE_PATH";

    private StorePathResolver() {
    }

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
