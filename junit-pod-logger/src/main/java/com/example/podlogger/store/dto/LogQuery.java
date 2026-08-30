package com.example.podlogger.store.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.podlogger.store.EnvironmentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Расширяемый фильтр выборки логов. Незаданные поля в SQL не попадают.
 * {@link #runName} и {@link #testRunName} — синонимы; {@link #effectiveRunName()}
 * предпочитает {@code runName}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogQuery {

    /** PK прогона. */
    private UUID testRunId;
    /** Имя прогона (предпочтительный синоним). */
    private String runName;
    /** Имя прогона (совместимость с бизнес-API). */
    private String testRunName;
    /** Имя suite. */
    private String testSuiteName;
    /** FQCN тестового класса. */
    private String relatedTestClass;
    /** Имя тестового метода. */
    private String relatedTestMethod;
    /** Стенд. */
    private EnvironmentType environmentType;
    /** Тип сервиса. */
    private String serviceType;
    /** Нижняя граница timestamp записи. */
    private LocalDateTime from;
    /** Верхняя граница timestamp записи. */
    private LocalDateTime to;
    /** Уровень лога. */
    private String level;
    /** Logger name. */
    private String logger;
    /** Подстрока message (LIKE, экранируется). */
    private String messageContains;
    /** JUnit display name. */
    private String testDisplayName;
    /** Только упавшие / только прошедшие invocation. */
    private Boolean testFailed;
    /** Точный fingerprint. */
    private String fingerprint;
    /** LIMIT; {@code null} или ≤0 → 10_000. */
    private Integer limit;

    /**
     * Имя прогона для SQL: {@code runName}, если не blank, иначе {@code testRunName}.
     *
     * @return имя или {@code null}
     */
    public String effectiveRunName() {
        if (runName != null && !runName.isBlank()) {
            return runName;
        }
        return testRunName;
    }
}
