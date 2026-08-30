package com.example.podlogger.store.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.podlogger.client.PodLogDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Результат {@code PodLoggerService.collectAndMergeLogsForTestRun}:
 * что уже было в SQLite, что пришло из runtime dump, что получилось после дедупа.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergedLogResult {

    /** Прогон, для которого делали merge. */
    private UUID testRunId;
    /** Снимок из persistent store до дозаписи. */
    @Builder.Default
    private List<PodLogDto> fromPersistent = new ArrayList<>();
    /** Свежий runtime dump окна прогона. */
    @Builder.Default
    private List<PodLogDto> fromRuntime = new ArrayList<>();
    /** Объединение, отсортированное по timestamp. */
    @Builder.Default
    private List<PodLogDto> merged = new ArrayList<>();
    /** Сколько runtime-записей не было в persistent (по ключу дедупа). */
    private int insertedNewCount;
}
