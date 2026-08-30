package com.example.podlogger.store.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.EnvironmentType;
import com.example.podlogger.store.FingerprintUtil;
import com.example.podlogger.store.LogStoreRepository;
import com.example.podlogger.store.StoreTime;
import com.example.podlogger.store.dto.LogQuery;

import lombok.RequiredArgsConstructor;

/**
 * SQLite-реализация {@link LogStoreRepository}: {@code INSERT OR IGNORE} и динамический SELECT
 * по {@link LogQuery}. Колонку {@code relevantEvents} не читает и не пишет.
 */
@Repository
@RequiredArgsConstructor
public class SqliteLogStoreRepository implements LogStoreRepository {

    /** Потолок выборки, если {@link LogQuery#getLimit()} не задан. */
    static final int DEFAULT_LIMIT = 10_000;

    private final DataSource dataSource;

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveAll(List<PodLogDto> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO log_entry (
                        id, test_run_id, timestamp, level, logger, message, stack_trace,
                        thread_name, trace_id, span_id, pod_name, namespace, container_name,
                        service_type, related_test_class, related_test_method, test_display_name,
                        test_failed, fingerprint
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (PodLogDto log : logs) {
                    if (log.getTimestamp() == null || log.getTestRunId() == null) {
                        continue;
                    }
                    if (log.getId() == null) {
                        log.setId(UUID.randomUUID());
                    }
                    if (log.getFingerprint() == null || log.getFingerprint().isBlank()) {
                        log.setFingerprint(FingerprintUtil.compute(log));
                    }
                    statement.setString(1, log.getId().toString());
                    statement.setString(2, log.getTestRunId().toString());
                    statement.setString(3, StoreTime.format(log.getTimestamp()));
                    statement.setString(4, log.getLevel());
                    statement.setString(5, log.getLogger());
                    statement.setString(6, log.getMessage());
                    statement.setString(7, log.getStackTrace());
                    statement.setString(8, log.getThreadName());
                    statement.setString(9, log.getTraceId());
                    statement.setString(10, log.getSpanId());
                    statement.setString(11, log.getPodName());
                    statement.setString(12, log.getNamespace());
                    statement.setString(13, log.getContainerName());
                    statement.setString(14, log.getServiceType());
                    statement.setString(15, log.getRelatedTestClass());
                    statement.setString(16, log.getRelatedTestMethod());
                    statement.setString(17, log.getTestDisplayName());
                    if (log.getTestFailed() == null) {
                        statement.setNull(18, Types.INTEGER);
                    } else {
                        statement.setInt(18, Boolean.TRUE.equals(log.getTestFailed()) ? 1 : 0);
                    }
                    statement.setString(19, log.getFingerprint());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException если {@code from} позже {@code to}
     */
    @Override
    public List<PodLogDto> find(LogQuery query) {
        LogQuery q = query == null ? new LogQuery() : query;
        if (q.getFrom() != null && q.getTo() != null && q.getFrom().isAfter(q.getTo())) {
            throw new IllegalArgumentException("LogQuery.from must not be after LogQuery.to");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT e.*, r.test_run_name, r.test_suite_name, r.environment_type AS run_environment
                FROM log_entry e
                JOIN test_run r ON r.id = e.test_run_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        addEquals(sql, params, "e.test_run_id", q.getTestRunId() == null ? null : q.getTestRunId().toString());
        addEquals(sql, params, "r.test_run_name", q.effectiveRunName());
        addEquals(sql, params, "r.test_suite_name", q.getTestSuiteName());
        addEquals(sql, params, "e.related_test_class", q.getRelatedTestClass());
        addEquals(sql, params, "e.related_test_method", q.getRelatedTestMethod());
        addEquals(sql, params, "r.environment_type",
                q.getEnvironmentType() == null ? null : q.getEnvironmentType().name());
        addEquals(sql, params, "e.service_type", q.getServiceType());
        addEquals(sql, params, "e.level", q.getLevel());
        addEquals(sql, params, "e.logger", q.getLogger());
        addEquals(sql, params, "e.test_display_name", q.getTestDisplayName());
        addEquals(sql, params, "e.fingerprint", q.getFingerprint());
        if (q.getFrom() != null) {
            sql.append(" AND e.timestamp >= ?");
            params.add(StoreTime.format(q.getFrom()));
        }
        if (q.getTo() != null) {
            sql.append(" AND e.timestamp <= ?");
            params.add(StoreTime.format(q.getTo()));
        }
        if (q.getTestFailed() != null) {
            sql.append(" AND e.test_failed = ?");
            params.add(Boolean.TRUE.equals(q.getTestFailed()) ? 1 : 0);
        }
        if (q.getMessageContains() != null && !q.getMessageContains().isBlank()) {
            sql.append(" AND e.message LIKE ? ESCAPE '\\'");
            params.add('%' + escapeLike(q.getMessageContains()) + '%');
        }
        sql.append(" ORDER BY e.timestamp ASC, e.id ASC LIMIT ?");
        int limit = q.getLimit() == null || q.getLimit() <= 0 ? DEFAULT_LIMIT : q.getLimit();
        params.add(limit);

        return JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
                List<PodLogDto> result = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(map(rs));
                    }
                }
                return result;
            }
        });
    }

    /**
     * Добавляет {@code AND column = ?}, если value непустое.
     *
     * @param sql    копится WHERE
     * @param params копится bind
     * @param column квалифицированное имя колонки
     * @param value  значение или {@code null}
     */
    private static void addEquals(StringBuilder sql, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(value);
        }
    }

    /**
     * Экранирование {@code %}, {@code _} и {@code \} для LIKE ... ESCAPE '\\'.
     *
     * @param value сырая подстрока
     * @return безопасный паттерн
     */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Маппинг JOIN {@code log_entry} + {@code test_run} в DTO. {@code relevantEvents} остаётся null.
     *
     * @param rs текущая строка
     * @return DTO
     * @throws SQLException ошибка драйвера
     */
    private static PodLogDto map(ResultSet rs) throws SQLException {
        Integer failed = (Integer) rs.getObject("test_failed");
        String runName = rs.getString("test_run_name");
        return PodLogDto.builder()
                .id(UUID.fromString(rs.getString("id")))
                .testRunId(UUID.fromString(rs.getString("test_run_id")))
                .timestamp(StoreTime.parse(rs.getString("timestamp")))
                .level(rs.getString("level"))
                .logger(rs.getString("logger"))
                .message(rs.getString("message"))
                .stackTrace(rs.getString("stack_trace"))
                .threadName(rs.getString("thread_name"))
                .traceId(rs.getString("trace_id"))
                .spanId(rs.getString("span_id"))
                .podName(rs.getString("pod_name"))
                .namespace(rs.getString("namespace"))
                .containerName(rs.getString("container_name"))
                .serviceType(rs.getString("service_type"))
                .relatedTestClass(rs.getString("related_test_class"))
                .relatedTestMethod(rs.getString("related_test_method"))
                .testDisplayName(rs.getString("test_display_name"))
                .testFailed(failed == null ? null : failed == 1)
                .fingerprint(rs.getString("fingerprint"))
                .runName(runName)
                .testRunName(runName)
                .testSuiteName(rs.getString("test_suite_name"))
                .environmentType(EnvironmentType.valueOf(rs.getString("run_environment")))
                .build();
    }
}
