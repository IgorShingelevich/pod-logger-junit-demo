package com.example.podlogger.store;

/**
 * Стенд, на котором шёл прогон. Прогоны всех значений живут в одной SQLite-базе
 * и различаются этим полем плюс {@code testRunId}.
 *
 * <p>{@link #LOCAL} — демо/ноутбук (K3s). {@link #DEV}/{@link #ST}/{@link #FT} —
 * стенды закрытого контура.
 */
public enum EnvironmentType {
    /** Development-стенд. */
    DEV,
    /** System test / интеграционный стенд. */
    ST,
    /** Feature / функциональный стенд. */
    FT,
    /** Локальный кластер разработчика или CI-агента. */
    LOCAL
}
