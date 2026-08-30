package com.example.podlogger;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.podlogger.event.PodEventReasons;
import com.example.podlogger.store.EnvironmentType;

import lombok.Getter;
import lombok.Setter;

/**
 * Runtime-конфигурация библиотеки: зеркало атрибутов {@link PodLogger} плюс
 * параметры store (путь к SQLite, лимит выборки, retention).
 *
 * <p>Экземпляр — Spring singleton. {@link PodLoggerService#applyAnnotation(PodLogger)}
 * перезаписывает поля из аннотации тестового класса в {@code beforeAll}/{@code beforeEach}.
 *
 * <p>Не содержит SQL, HTTP к кластеру и логики Allure.
 */
@Getter
@Setter
@Component
public class PodLoggerProperties {

    /** Gate логов: Allure+SQLite только при fail, если {@code true}. */
    private boolean collectOnFailOnly = true;

    /** Namespace целевой поды. */
    private String namespace = "default";

    /** Label selector {@code key=value}. */
    private String podLabelSelector = "app=demo-api";

    /**
     * Явный путь к файлу SQLite. Используется только если не заданы
     * system property {@code pod.logger.store-path} и env {@code POD_LOGGER_STORE_PATH}.
     */
    private String storePath = "";

    /** Стенд прогона. */
    private EnvironmentType environmentType = EnvironmentType.LOCAL;

    /** Имя прогона; пустое — генерируется в extension. */
    private String testRunName = "";

    /** Имя suite; пустое — FQCN тестового класса. */
    private String testSuiteName = "";

    /** Тип сервиса/поды. */
    private String serviceType = "";

    /**
     * Если {@code true}, {@code afterAll} дополнительно аттачит в Allure
     * смерженный run-level срез {@code pod-logs-run-*}. По умолчанию выключено:
     * per-invocation аттачи уже есть.
     */
    private boolean attachRunSummaryToAllure = false;

    /** Верхняя граница строк в {@code getLogs()} без фильтров. */
    private int queryLimit = 10_000;

    /** Подсказка retention; фактическая очистка — {@code PodStoreService.deleteOlderThan}. */
    private int retentionDays = 30;

    /** Публиковать ли {@code TestRunStarted}/{@code TestRunFinished}. */
    private boolean publishLifecycleEvents = true;

    /** Abort оставшихся тестов при stand-down Event. */
    private boolean failFastOnStandDownEvent = true;

    /** HTTP health URL; пусто — только Kubernetes Ready. */
    private String healthCheckUrl = "";

    /** Пользовательский список stand-down кодов; пустой — дефолт библиотеки. */
    private List<String> standDownEventCodes = List.of();

    /** Пользовательские подстроки stand-down; пустой — дефолт библиотеки. */
    private List<String> standDownMessagePatterns = List.of();

    /**
     * Коды stand-down, которые реально использует matcher.
     * Пустой пользовательский список заменяется {@link PodEventReasons#DEFAULT_STAND_DOWN_CODES}.
     *
     * @return непустой список кодов
     */
    public List<String> effectiveStandDownEventCodes() {
        if (standDownEventCodes == null || standDownEventCodes.isEmpty()) {
            return PodEventReasons.DEFAULT_STAND_DOWN_CODES;
        }
        return standDownEventCodes;
    }

    /**
     * Паттерны message/reason для stand-down.
     * Пустой пользовательский список заменяется {@link PodEventReasons#DEFAULT_MESSAGE_PATTERNS}.
     *
     * @return непустой список подстрок
     */
    public List<String> effectiveStandDownMessagePatterns() {
        if (standDownMessagePatterns == null || standDownMessagePatterns.isEmpty()) {
            return PodEventReasons.DEFAULT_MESSAGE_PATTERNS;
        }
        return standDownMessagePatterns;
    }
}
