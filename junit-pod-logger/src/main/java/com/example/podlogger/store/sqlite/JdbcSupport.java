package com.example.podlogger.store.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

final class JdbcSupport {

    @FunctionalInterface
    interface ConnectionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    private JdbcSupport() {
    }

    static <T> T withConnection(DataSource dataSource, ConnectionCallback<T> callback) {
        try (Connection connection = dataSource.getConnection()) {
            return callback.execute(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite operation failed", e);
        }
    }

    static List<String> stringList(PreparedStatement statement, String column) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(column));
            }
        }
        return values;
    }
}
