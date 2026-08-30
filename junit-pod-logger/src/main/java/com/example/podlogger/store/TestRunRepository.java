package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.podlogger.store.dto.TestRunDto;

/**
 * Repository таблицы {@code test_run}. SQL наружу не отдаёт.
 */
public interface TestRunRepository {

    /**
     * INSERT новой строки. Генерирует UUID, если draft без id.
     *
     * @param draft данные прогона
     * @return id вставленной строки
     */
    UUID insert(TestRunDto draft);

    /**
     * UPDATE {@code finished_at} (COALESCE — повторный finish не сдвигает время) и статус FINISHED.
     *
     * @param testRunId   id
     * @param finishedAt  момент закрытия UTC
     */
    void finish(UUID testRunId, LocalDateTime finishedAt);

    /**
     * SELECT по PK.
     *
     * @param testRunId id
     * @return DTO или empty
     */
    Optional<TestRunDto> findById(UUID testRunId);

    /**
     * SELECT по имени (неуникально).
     *
     * @param testRunName имя
     * @return список
     */
    List<TestRunDto> findByName(String testRunName);

    /**
     * SELECT по интервалу {@code started_at}.
     *
     * @param from нижняя граница
     * @param to   верхняя граница
     * @return список
     */
    List<TestRunDto> findByStartedBetween(LocalDateTime from, LocalDateTime to);

    /**
     * SELECT по стенду.
     *
     * @param environmentType стенд
     * @return список
     */
    List<TestRunDto> findByEnvironment(EnvironmentType environmentType);

    /**
     * Удаляет {@code log_entry} закрытых старых run, затем сами {@code test_run}.
     *
     * @param cutoff порог {@code started_at}; старше — удалить
     * @return число удалённых run
     */
    int deleteClosedOlderThan(LocalDateTime cutoff);
}
