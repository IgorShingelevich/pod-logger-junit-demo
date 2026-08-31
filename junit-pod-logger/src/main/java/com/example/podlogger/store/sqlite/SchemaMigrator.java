package com.example.podlogger.store.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Идемпотентная схема v1: таблицы {@code test_run} и {@code log_entry}, unique index дедупа
 * и поисковые индексы. Колонки Events нет — {@code relevantEvents} в БД не хранятся.
 *
 * <p>{@code CREATE TABLE IF NOT EXISTS} / {@code CREATE INDEX IF NOT EXISTS}: повторный
 * вызов на существующем файле безопасен.
 */
public final class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private SchemaMigrator() {
    }

    /**
     * Накатывает DDL. Падение — {@link IllegalStateException}.
     *
     * @param dataSource открытый SQLite DataSource
     */
    public static void migrate(DataSource dataSource) {
        log.debug("Starting SQLite schema migration");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS test_run (
                        id                 TEXT PRIMARY KEY,
                        test_run_name      TEXT NOT NULL,
                        test_suite_name    TEXT,
                        environment_type   TEXT NOT NULL,
                        service_type       TEXT,
                        namespace          TEXT,
                        pod_label_selector TEXT,
                        started_at         TEXT NOT NULL,
                        finished_at        TEXT,
                        status             TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS log_entry (
                        id                  TEXT PRIMARY KEY,
                        test_run_id         TEXT NOT NULL,
                        timestamp           TEXT NOT NULL,
                        level               TEXT,
                        logger              TEXT,
                        message             TEXT,
                        stack_trace         TEXT,
                        thread_name         TEXT,
                        trace_id            TEXT,
                        span_id             TEXT,
                        pod_name            TEXT,
                        namespace           TEXT,
                        container_name      TEXT,
                        service_type        TEXT,
                        related_test_class  TEXT,
                        related_test_method TEXT,
                        test_display_name   TEXT,
                        test_failed         INTEGER,
                        fingerprint         TEXT NOT NULL,
                        FOREIGN KEY (test_run_id) REFERENCES test_run(id)
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_log_dedup
                        ON log_entry(test_run_id, timestamp, fingerprint)
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_log_test_run ON log_entry(test_run_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_log_timestamp ON log_entry(timestamp)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_log_level ON log_entry(level)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_log_fingerprint ON log_entry(fingerprint)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_log_related_class ON log_entry(related_test_class)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_log_related_method ON log_entry(related_test_method)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_run_environment ON test_run(environment_type)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_run_suite ON test_run(test_suite_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_run_name ON test_run(test_run_name)");
            log.debug("SQLite schema migration completed successfully");
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite schema migration failed", e);
        }
    }
}
