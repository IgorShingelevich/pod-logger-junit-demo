package com.example.podlogger.event;

import java.util.List;
import java.util.Set;

/**
 * Константы reason/code, которые библиотека публикует сама, и дефолтный allowlist stand-down.
 *
 * <p>{@link #LIFECYCLE_CODES} исключаются из stand-down, иначе старт/финиш прогона
 * сами себя триггерили бы. Kube-шум {@code Pulled}/{@code Created}/{@code Started}/{@code Scheduled}
 * в allowlist не входит.
 */
public final class PodEventReasons {

    /** Lifecycle Event в {@code beforeAll} после {@code startTestRun}. */
    public static final String TEST_RUN_STARTED = "TestRunStarted";
    /** Lifecycle Event в {@code afterAll} со счётчиками. */
    public static final String TEST_RUN_FINISHED = "TestRunFinished";

    /** Код health: под не Ready / не Running. */
    public static final String POD_NOT_READY = "PodNotReady";
    /** Код health: HTTP {@code healthCheckUrl} не 2xx или тело с maintenance-pattern. */
    public static final String HEALTH_CHECK_FAILED = "HealthCheckFailed";

    /**
     * Дефолтные коды stand-down ({@code Event.reason}), если аннотация не задала свои.
     */
    public static final List<String> DEFAULT_STAND_DOWN_CODES = List.of(
            "StandUnavailable",
            "Maintenance",
            "Evicted",
            "Killing",
            "FailedScheduling",
            "FailedMount",
            "NetworkNotReady",
            "Unhealthy",
            "NodeNotReady",
            "TaintManagerEviction",
            "DisruptionTarget");

    /**
     * Дефолтные подстроки (case-insensitive) в reason/message.
     */
    public static final List<String> DEFAULT_MESSAGE_PATTERNS = List.of(
            "maintenance",
            "unavailable",
            "shutting down",
            "drain",
            "preempt",
            "eviction");

    /** Коды, которые matcher никогда не считает stand-down. */
    public static final Set<String> LIFECYCLE_CODES = Set.of(TEST_RUN_STARTED, TEST_RUN_FINISHED);

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private PodEventReasons() {
    }
}
