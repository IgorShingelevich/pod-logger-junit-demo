package com.example.podlogger.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.podlogger.store.dto.TestRunDto;

/**
 * Lifecycle и metadata тестового прогона ({@code BeforeAll} → {@code AfterAll}).
 *
 * <p>Не читает логи: для логов есть {@link PodStoreService}. Не ходит в Kubernetes.
 */
public interface TestRunStore {

    /**
     * Создаёт прогон с минимальным набором полей.
     *
     * @param testRunName     имя (обязательно, может повторяться)
     * @param testSuiteName   suite
     * @param environmentType стенд (обязателен)
     * @param serviceType     сервис/под
     * @return UUID новой строки {@code test_run}
     */
    UUID startTestRun(
            String testRunName,
            String testSuiteName,
            EnvironmentType environmentType,
            String serviceType);

    /**
     * Создаёт прогон из draft (namespace, selector, startedAt). Пустой {@code startedAt}
     * заполняется now UTC, статус {@code STARTED}.
     *
     * @param draft данные прогона
     * @return UUID
     * @throws IllegalArgumentException нет имени или environment
     */
    UUID startTestRun(TestRunDto draft);

    /**
     * Ставит {@code finishedAt} (если ещё пуст) и статус {@code FINISHED}.
     *
     * @param testRunId id прогона
     */
    void finishTestRun(UUID testRunId);

    /**
     * Читает прогон по UUID.
     *
     * @param testRunId id
     * @return DTO или empty
     */
    Optional<TestRunDto> getTestRun(UUID testRunId);

    /**
     * Все прогоны с данным именем (имя неуникально).
     *
     * @param testRunName имя
     * @return список по возрастанию {@code startedAt}
     */
    List<TestRunDto> getTestRuns(String testRunName);

    /**
     * Прогоны, стартовавшие в закрытом интервале.
     *
     * @param from нижняя граница {@code startedAt}
     * @param to   верхняя граница {@code startedAt}
     * @return список
     */
    List<TestRunDto> getTestRuns(LocalDateTime from, LocalDateTime to);

    /**
     * Прогоны одного стенда.
     *
     * @param environmentType стенд
     * @return список
     */
    List<TestRunDto> getTestRuns(EnvironmentType environmentType);
}
