package com.example.podlogger.event;

import java.util.List;
import java.util.Set;

public final class PodEventReasons {

    public static final String TEST_RUN_STARTED = "TestRunStarted";
    public static final String TEST_RUN_FINISHED = "TestRunFinished";

    public static final String POD_NOT_READY = "PodNotReady";
    public static final String HEALTH_CHECK_FAILED = "HealthCheckFailed";

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

    public static final List<String> DEFAULT_MESSAGE_PATTERNS = List.of(
            "maintenance",
            "unavailable",
            "shutting down",
            "drain",
            "preempt",
            "eviction");

    public static final Set<String> LIFECYCLE_CODES = Set.of(TEST_RUN_STARTED, TEST_RUN_FINISHED);

    private PodEventReasons() {
    }
}
