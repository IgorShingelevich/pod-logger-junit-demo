package com.example.podlogger.store.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.example.podlogger.store.EnvironmentType;
import com.example.podlogger.store.StoreTime;
import com.example.podlogger.store.TestRunRepository;
import com.example.podlogger.store.dto.TestRunDto;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SqliteTestRunRepository implements TestRunRepository {

    private final DataSource dataSource;

    @Override
    public UUID insert(TestRunDto draft) {
        UUID id = draft.getId() == null ? UUID.randomUUID() : draft.getId();
        LocalDateTime startedAt = draft.getStartedAt() == null
                ? LocalDateTime.now(ZoneOffset.UTC)
                : draft.getStartedAt();
        JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO test_run (
                        id, test_run_name, test_suite_name, environment_type, service_type,
                        namespace, pod_label_selector, started_at, finished_at, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, id.toString());
                statement.setString(2, draft.getTestRunName());
                statement.setString(3, draft.getTestSuiteName());
                statement.setString(4, draft.getEnvironmentType().name());
                statement.setString(5, draft.getServiceType());
                statement.setString(6, draft.getNamespace());
                statement.setString(7, draft.getPodLabelSelector());
                statement.setString(8, StoreTime.format(startedAt));
                statement.setString(9, StoreTime.format(draft.getFinishedAt()));
                statement.setString(10, draft.getStatus() == null ? "STARTED" : draft.getStatus());
                statement.executeUpdate();
            }
            return null;
        });
        draft.setId(id);
        draft.setStartedAt(startedAt);
        if (draft.getStatus() == null) {
            draft.setStatus("STARTED");
        }
        return id;
    }

    @Override
    public void finish(UUID testRunId, LocalDateTime finishedAt) {
        JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE test_run
                    SET finished_at = COALESCE(finished_at, ?),
                        status = 'FINISHED'
                    WHERE id = ?
                    """)) {
                statement.setString(1, StoreTime.format(finishedAt));
                statement.setString(2, testRunId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<TestRunDto> findById(UUID testRunId) {
        return JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM test_run WHERE id = ?")) {
                statement.setString(1, testRunId.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(map(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<TestRunDto> findByName(String testRunName) {
        return JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM test_run WHERE test_run_name = ? ORDER BY started_at ASC")) {
                statement.setString(1, testRunName);
                return list(statement);
            }
        });
    }

    @Override
    public List<TestRunDto> findByStartedBetween(LocalDateTime from, LocalDateTime to) {
        return JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM test_run
                    WHERE started_at >= ? AND started_at <= ?
                    ORDER BY started_at ASC
                    """)) {
                statement.setString(1, StoreTime.format(from));
                statement.setString(2, StoreTime.format(to));
                return list(statement);
            }
        });
    }

    @Override
    public List<TestRunDto> findByEnvironment(EnvironmentType environmentType) {
        return JdbcSupport.withConnection(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM test_run WHERE environment_type = ? ORDER BY started_at ASC")) {
                statement.setString(1, environmentType.name());
                return list(statement);
            }
        });
    }

    @Override
    public int deleteClosedOlderThan(LocalDateTime cutoff) {
        return JdbcSupport.withConnection(dataSource, connection -> {
            String cutoffText = StoreTime.format(cutoff);
            try (PreparedStatement deleteLogs = connection.prepareStatement("""
                    DELETE FROM log_entry WHERE test_run_id IN (
                        SELECT id FROM test_run
                        WHERE finished_at IS NOT NULL AND started_at < ?
                    )
                    """);
                 PreparedStatement deleteRuns = connection.prepareStatement("""
                    DELETE FROM test_run
                    WHERE finished_at IS NOT NULL AND started_at < ?
                    """)) {
                deleteLogs.setString(1, cutoffText);
                deleteLogs.executeUpdate();
                deleteRuns.setString(1, cutoffText);
                return deleteRuns.executeUpdate();
            }
        });
    }

    private static List<TestRunDto> list(PreparedStatement statement) throws SQLException {
        List<TestRunDto> result = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }

    private static TestRunDto map(ResultSet rs) throws SQLException {
        return TestRunDto.builder()
                .id(UUID.fromString(rs.getString("id")))
                .testRunName(rs.getString("test_run_name"))
                .testSuiteName(rs.getString("test_suite_name"))
                .environmentType(EnvironmentType.valueOf(rs.getString("environment_type")))
                .serviceType(rs.getString("service_type"))
                .namespace(rs.getString("namespace"))
                .podLabelSelector(rs.getString("pod_label_selector"))
                .startedAt(StoreTime.parse(rs.getString("started_at")))
                .finishedAt(StoreTime.parse(rs.getString("finished_at")))
                .status(rs.getString("status"))
                .build();
    }
}
