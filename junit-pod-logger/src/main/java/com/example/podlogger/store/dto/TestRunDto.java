package com.example.podlogger.store.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.podlogger.store.EnvironmentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata одного тестового прогона (строка {@code test_run}).
 * Имя {@link #testRunName} может повторяться; уникален {@link #id}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestRunDto {

    /** UUID прогона. */
    private UUID id;
    /** Человекочитаемое имя. */
    private String testRunName;
    /** Suite (обычно FQCN класса). */
    private String testSuiteName;
    /** Стенд. */
    private EnvironmentType environmentType;
    /** Сервис/под. */
    private String serviceType;
    /** Namespace поды. */
    private String namespace;
    /** Label selector поды. */
    private String podLabelSelector;
    /** UTC старта ({@code beforeAll}). */
    private LocalDateTime startedAt;
    /** UTC финиша ({@code afterAll}); {@code null} пока прогон открыт. */
    private LocalDateTime finishedAt;
    /** {@code STARTED} или {@code FINISHED}. */
    private String status;
}
