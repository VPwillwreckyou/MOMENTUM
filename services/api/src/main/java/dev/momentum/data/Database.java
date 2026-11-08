package dev.momentum.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite connection manager for MOMENTUM desktop.
 *
 * Database location:
 *   Windows: %LOCALAPPDATA%\MOMENTUM\data\momentum.db
 *   macOS/Linux: ~/.momentum/data/momentum.db (or $MOMENTUM_DATA_DIR)
 */
public final class Database implements AutoCloseable {

    private final String dbPath;
    private Connection connection;

    public Database(String dataDirectory) {
        File dir = new File(dataDirectory);
        if (!dir.exists()) dir.mkdirs();
        this.dbPath = new File(dir, "momentum.db").getAbsolutePath();
    }

    public String path() { return dbPath; }

    public Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found", e);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            // Pragmas must run on autocommit (SQLite rejects journal-mode changes inside a transaction).
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            connection.setAutoCommit(false);
        }
        return connection;
    }

    public void commit() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.commit();
    }

    public void rollback() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.rollback();
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            connection = null;
        }
    }
}
