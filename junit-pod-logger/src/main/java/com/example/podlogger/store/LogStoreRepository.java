package com.example.podlogger.store;

import java.util.List;

import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.dto.LogQuery;

/**
 * Repository {@code log_entry}: INSERT и SELECT по {@link LogQuery}.
 * Реализация — {@code SqliteLogStoreRepository}.
 */
public interface LogStoreRepository {

    /**
     * Пакетная вставка. Дубли по unique index молча игнорируются ({@code INSERT OR IGNORE}).
     *
     * @param logs записи с {@code testRunId} и timestamp
     */
    void saveAll(List<PodLogDto> logs);

    /**
     * Выборка с JOIN на {@code test_run} (чтобы вернуть имя/suite/environment в DTO).
     *
     * @param query фильтры
     * @return записи по возрастанию timestamp
     */
    List<PodLogDto> find(LogQuery query);
}
