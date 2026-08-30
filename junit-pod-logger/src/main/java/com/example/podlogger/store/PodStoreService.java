package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.dto.LogQuery;

/**
 * Публичный application service persistent log store.
 *
 * <p>Сохраняет и ищет {@link PodLogDto}, скрывает SQL. Не знает про Kubernetes,
 * парсинг dump, Allure и оркестрацию JUnit. Overload-ы {@code getLogs(...)} —
 * переходный business API; расширяемый контракт — {@link #getLogs(LogQuery)}.
 *
 * <p>Запрещено на этом интерфейсе: {@code getLogsFromPod}, {@code syncRuntimeAndPersistentLogs}.
 * Merge runtime+persistent делает {@code PodLoggerService.collectAndMergeLogsForTestRun}.
 */
public interface PodStoreService {

    /**
     * Сохраняет записи, у которых уже заполнен {@code testRunId}.
     *
     * @param logs список; {@code null}/пустой — no-op
     * @throws IllegalArgumentException если у записи нет {@code testRunId}
     */
    void saveLogs(List<PodLogDto> logs);

    /**
     * Сохраняет записи в указанный прогон: проставляет контекст run, id, fingerprint.
     * Вставка идемпотентна по {@code (test_run_id, timestamp, fingerprint)}.
     *
     * @param testRunId существующий прогон
     * @param logs      список; {@code null}/пустой — no-op
     * @throws IllegalArgumentException неизвестный {@code testRunId}
     */
    void saveLogs(UUID testRunId, List<PodLogDto> logs);

    /**
     * Все записи до лимита (по умолчанию 10_000). Пишет warn в лог.
     *
     * @return записи по возрастанию timestamp
     */
    List<PodLogDto> getLogs();

    /**
     * Логи одного прогона.
     *
     * @param testRunId id {@code test_run}
     * @return записи прогона
     */
    List<PodLogDto> getLogs(UUID testRunId);

    /**
     * Логи в закрытом интервале timestamp.
     *
     * @param from нижняя граница
     * @param to   верхняя граница
     * @return записи в окне
     */
    List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to);

    /**
     * Окно времени + стенд.
     *
     * @param from            нижняя граница
     * @param to              верхняя граница
     * @param environmentType стенд
     * @return записи
     */
    List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to, EnvironmentType environmentType);

    /**
     * Прогон + стенд (несовпадение environment даёт пустой список).
     *
     * @param testRunId       id прогона
     * @param environmentType стенд
     * @return записи
     */
    List<PodLogDto> getLogs(UUID testRunId, EnvironmentType environmentType);

    /**
     * Suite + стенд.
     *
     * @param testSuiteName   имя suite
     * @param environmentType стенд
     * @return записи
     */
    List<PodLogDto> getLogs(String testSuiteName, EnvironmentType environmentType);

    /**
     * Имя прогона + suite + стенд. Имя неуникально — вернутся все совпавшие run.
     *
     * @param testRunName     имя прогона
     * @param testSuiteName   имя suite
     * @param environmentType стенд
     * @return записи
     */
    List<PodLogDto> getLogs(String testRunName, String testSuiteName, EnvironmentType environmentType);

    /**
     * Целевой query-контракт: все фильтры в одном объекте, незаданные поля игнорируются.
     *
     * @param query фильтры; {@code null} трактуется как пустой query
     * @return записи
     */
    List<PodLogDto> getLogs(LogQuery query);

    /**
     * Все сохранённые логи прогона. В v1 совпадает с {@link #getLogs(UUID)}.
     *
     * @param testRunId id прогона
     * @return записи
     */
    List<PodLogDto> getLogsForWholeRun(UUID testRunId);

    /**
     * Удаляет <em>закрытые</em> прогоны с {@code started_at} старше {@code days} суток
     * и их {@code log_entry}. Открытые run ({@code finished_at IS NULL}) не трогает.
     *
     * @param days порог возраста; должен быть ≥ 1
     * @return число удалённых строк {@code test_run}
     * @throws IllegalArgumentException если {@code days < 1}
     */
    int deleteOlderThan(int days);
}
