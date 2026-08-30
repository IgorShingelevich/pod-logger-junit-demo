package com.example.podlogger.store.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

public final class SqliteDataSourceFactory {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataSourceFactory.class);

    private SqliteDataSourceFactory() {
    }

    public static DataSource create(Path storePath) {
        try {
            Path parent = storePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create SQLite parent directory for " + storePath, e);
        }
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + storePath.toAbsolutePath());
        dataSource.setBusyTimeout(5000);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialize SQLite at " + storePath, e);
        }
        log.info("Opened SQLite log store at {}", storePath.toAbsolutePath());
        return dataSource;
    }
}
