package com.example.podlogger.store.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

/**
 * Минимальный JDBC helper без Spring JdbcTemplate: одна connection на вызов,
 * SQLException → {@link IllegalStateException}.
 */
final class JdbcSupport {

    /**
     * Callback внутри открытой connection.
     *
     * @param <T> тип результата
     */
    @FunctionalInterface
    interface ConnectionCallback<T> {

        /**
         * Работа с уже открытой connection. Закрывать её не нужно.
         *
         * @param connection живая connection
         * @return результат
         * @throws SQLException проброс в обёртку
         */
        T execute(Connection connection) throws SQLException;
    }

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private JdbcSupport() {
    }

    /**
     * Открывает connection, вызывает callback, закрывает.
     *
     * @param dataSource SQLite DataSource
     * @param callback   работа
     * @param <T>        тип результата
     * @return то, что вернул callback
     * @throws IllegalStateException при SQLException
     */
    static <T> T withConnection(DataSource dataSource, ConnectionCallback<T> callback) {
        try (Connection connection = dataSource.getConnection()) {
            return callback.execute(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite operation failed", e);
        }
    }

    /**
     * Читает одну строковую колонку из ResultSet statement.
     *
     * @param statement уже заполненный SELECT
     * @param column    имя колонки
     * @return список значений
     * @throws SQLException ошибка драйвера
     */
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
