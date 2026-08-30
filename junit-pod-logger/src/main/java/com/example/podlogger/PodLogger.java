package com.example.podlogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.podlogger.store.EnvironmentType;

/**
 * Мета-аннотация тестового класса: включает Spring TestContext и {@link PodLoggerExtension}.
 *
 * <p>На каждый invocation (включая каждый кейс {@code @ParameterizedTest}) extension
 * фиксирует UTC-окно, забирает логи поды через Fabric8 {@code OpenShiftClient},
 * парсит JSON-строки в {@link com.example.podlogger.client.PodLogDto} и:
 * <ul>
 *   <li>прикладывает срез в Allure;</li>
 *   <li>сохраняет тот же срез в SQLite через {@link com.example.podlogger.store.PodStoreService}.</li>
 * </ul>
 *
 * <p>Один флаг {@link #collectOnFailOnly()} управляет обоими выходами логов.
 * Kubernetes Events публикуются в {@code beforeAll}/{@code afterAll} и читаются
 * только на упавшем invocation (см. OpenShift Event Handling PRD).
 *
 * <p>Ставится только на класс ({@link ElementType#TYPE}). Без этой аннотации
 * {@link PodLoggerExtension} бросает {@link IllegalStateException}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({SpringExtension.class, PodLoggerExtension.class})
public @interface PodLogger {

    /**
     * Gate логов: {@code true} — Allure и SQLite только при fail;
     * {@code false} — после каждого invocation. Events на passed-тесте не аттачатся в любом случае.
     *
     * @return значение gate; по умолчанию {@code true}
     */
    boolean collectOnFailOnly() default true;

    /**
     * Namespace целевой поды в Kubernetes/OpenShift.
     *
     * @return имя namespace; по умолчанию {@code default}
     */
    String namespace() default "default";

    /**
     * Label selector целевой поды в формате {@code key=value} (один equals).
     *
     * @return селектор; по умолчанию {@code app=demo-api}
     */
    String podLabelSelector() default "app=demo-api";

    /**
     * Человекочитаемое имя прогона. Может повторяться между запусками;
     * уникальность даёт {@code testRunId} (UUID). Пустая строка —
     * имя генерируется как {@code <SimpleClassName>-<yyyyMMddHHmmss>}.
     *
     * @return имя прогона или пустая строка
     */
    String testRunName() default "";

    /**
     * Имя test suite. Пустая строка — берётся FQCN тестового класса.
     *
     * @return имя suite или пустая строка
     */
    String testSuiteName() default "";

    /**
     * Стенд, на котором идёт прогон. Прогоны {@code DEV}/{@code ST}/{@code FT}/{@code LOCAL}
     * живут в одной SQLite-базе и различаются этим полем и {@code testRunId}.
     *
     * @return тип окружения; по умолчанию {@link EnvironmentType#LOCAL}
     */
    EnvironmentType environmentType() default EnvironmentType.LOCAL;

    /**
     * Какой сервис/под логируем (например {@code demo-api}). Пустая строка —
     * берётся value из {@link #podLabelSelector()} после {@code =}.
     *
     * @return тип сервиса или пустая строка
     */
    String serviceType() default "";

    /**
     * Публиковать ли lifecycle-Event {@code TestRunStarted} в {@code beforeAll}
     * и {@code TestRunFinished} в {@code afterAll}. {@code false} — не публиковать.
     * Ошибка create Event не валит suite.
     *
     * @return {@code true} по умолчанию
     */
    boolean publishLifecycleEvents() default true;

    /**
     * При stand-down Event на поде прерывать ли оставшиеся тесты класса
     * ({@code beforeEach} бросает {@link IllegalStateException}). Текущий упавший
     * тест исходную причину не теряет.
     *
     * @return {@code true} по умолчанию
     */
    boolean failFastOnStandDownEvent() default true;

    /**
     * HTTP GET URL проверки здоровья приложения в поде. Пустая строка —
     * шаг HTTP в {@code isPodAvailable()} пропускается, достаточно Kubernetes Ready.
     *
     * @return URL или пустая строка
     */
    String healthCheckUrl() default "";

    /**
     * Коды Kubernetes {@code Event.reason}, которые считаются stand-down.
     * Пустой массив — дефолт библиотеки ({@code Maintenance}, {@code Evicted}, …).
     * Пустой массив <em>не</em> означает «не матчить ничего».
     *
     * @return коды или пустой массив (дефолт библиотеки)
     */
    String[] standDownEventCodes() default {};

    /**
     * Подстроки (case-insensitive) в {@code reason} или {@code message} Event,
     * которые тоже считаются stand-down. Пустой массив — дефолт библиотеки.
     *
     * @return паттерны или пустой массив (дефолт библиотеки)
     */
    String[] standDownMessagePatterns() default {};
}
