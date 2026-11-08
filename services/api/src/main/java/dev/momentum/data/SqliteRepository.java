package dev.momentum.data;

import dev.momentum.core.Json;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC data-access layer over the MOMENTUM SQLite schema.
 *
 * Field names mirror apps/web/src/state/types.ts so the imported web backup
 * can be mapped 1:1. JSON-shaped columns (recurrence, finance) keep the exact
 * web JSON so that JavaFX serialization stays byte-compatible when syncing back.
 */
public final class SqliteRepository implements AutoCloseable {

    private final Database database;

    public SqliteRepository(Database database) throws SQLException {
        this.database = database;
        initialize();
    }

    public void initialize() throws SQLException {
        Connection conn = database.connection();
        Schema.createTables(conn);
        if (Schema.getVersion(conn) < Schema.CURRENT_VERSION) {
            Schema.setVersion(conn, Schema.CURRENT_VERSION);
        }
        database.commit();
    }

    public Connection connection() throws SQLException {
        return database.connection();
    }

    public void commit() throws SQLException {
        database.commit();
    }

    public void rollback() throws SQLException {
        database.rollback();
    }

    // ---------------------------------------------------------------- categories

    public void insertCategory(Map<String, Object> category) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO categories (id, name, color, icon, created_at, updated_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, str(category.get("id")));
            stmt.setString(2, str(category.get("name")));
            stmt.setString(3, str(category.get("color")));
            stmt.setString(4, str(category.get("icon")));
            stmt.setString(5, str(category.get("createdAt")));
            stmt.setString(6, str(category.get("updatedAt")));
            stmt.setString(7, nullable(category.get("deletedAt")));
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- goals

    public void insertGoal(Map<String, Object> goal) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO goals (id, title, description, notes, category_id, status, kind, priority, "
                        + "start_date, deadline, created_at, updated_at, completed_at, archived_at, deleted_at, "
                        + "series_id, occurrence, recurrence_json, finance_json, ai_generated) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, str(goal.get("id")));
            stmt.setString(2, str(goal.get("title")));
            stmt.setString(3, strOr(goal.get("description"), ""));
            stmt.setString(4, strOr(goal.get("notes"), ""));
            stmt.setString(5, nullable(goal.get("categoryId")));
            stmt.setString(6, strOr(goal.get("status"), "active"));
            stmt.setString(7, strOr(goal.get("kind"), "one-time"));
            stmt.setString(8, strOr(goal.get("priority"), "medium"));
            stmt.setString(9, nullable(goal.get("startDate")));
            stmt.setString(10, nullable(goal.get("deadline")));
            stmt.setString(11, str(goal.get("createdAt")));
            stmt.setString(12, str(goal.get("updatedAt")));
            stmt.setString(13, nullable(goal.get("completedAt")));
            stmt.setString(14, nullable(goal.get("archivedAt")));
            stmt.setString(15, nullable(goal.get("deletedAt")));
            stmt.setString(16, nullable(goal.get("seriesId")));
            stmt.setObject(17, optionalInt(goal.get("occurrence")));
            stmt.setObject(18, optionalJson(goal.get("recurrence")));
            stmt.setObject(19, optionalJson(goal.get("finance")));
            stmt.setInt(20, Boolean.TRUE.equals(goal.get("aiGenerated")) ? 1 : 0);
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- milestones

    public void insertMilestone(String goalId, Map<String, Object> milestone) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO milestones (id, goal_id, title, description, due_date, completed, completed_at, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, str(milestone.get("id")));
            stmt.setString(2, goalId);
            stmt.setString(3, str(milestone.get("title")));
            stmt.setString(4, nullable(milestone.get("description")));
            stmt.setString(5, nullable(milestone.get("dueDate")));
            stmt.setInt(6, Boolean.TRUE.equals(milestone.get("completed")) ? 1 : 0);
            stmt.setString(7, nullable(milestone.get("completedAt")));
            stmt.setInt(8, optionalInt(milestone.get("order")).orElse(0));
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- tasks

    public void insertTask(Map<String, Object> task) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO tasks (id, goal_id, milestone_id, parent_task_id, title, notes, completed, "
                        + "completed_at, due_date, priority, sort_order, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, str(task.get("id")));
            stmt.setString(2, str(task.get("goalId")));
            stmt.setString(3, nullable(task.get("milestoneId")));
            stmt.setString(4, nullable(task.get("parentTaskId")));
            stmt.setString(5, str(task.get("title")));
            stmt.setString(6, nullable(task.get("notes")));
            stmt.setInt(7, Boolean.TRUE.equals(task.get("completed")) ? 1 : 0);
            stmt.setString(8, nullable(task.get("completedAt")));
            stmt.setString(9, nullable(task.get("dueDate")));
            stmt.setString(10, strOr(task.get("priority"), "medium"));
            stmt.setInt(11, optionalInt(task.get("order")).orElse(0));
            stmt.setString(12, str(task.get("createdAt")));
            stmt.setString(13, str(task.get("updatedAt")));
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- activities

    public void insertActivity(Map<String, Object> activity) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO activities (id, type, goal_id, task_id, message, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, str(activity.get("id")));
            stmt.setString(2, str(activity.get("type")));
            stmt.setString(3, nullable(activity.get("goalId")));
            stmt.setString(4, nullable(activity.get("taskId")));
            stmt.setString(5, str(activity.get("message")));
            stmt.setString(6, str(activity.get("at")));
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- attachments

    public void insertAttachment(String goalId, String id, String name, String mimeType, long size,
                                 String relativePath, String createdAt) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO attachments (id, goal_id, name, mime_type, size, file_path, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, id);
            stmt.setString(2, goalId);
            stmt.setString(3, name);
            stmt.setString(4, mimeType);
            stmt.setLong(5, size);
            stmt.setString(6, relativePath);
            stmt.setString(7, createdAt);
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- settings & meta

    public void insertSetting(String key, String jsonValue) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            stmt.setString(1, key);
            stmt.setString(2, jsonValue);
            stmt.executeUpdate();
        }
    }

    public Optional<String> setting(String key) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void insertMeta(String key, String value) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO db_meta (key, value) VALUES (?, ?)")) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }

    public Optional<String> meta(String key) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement("SELECT value FROM db_meta WHERE key = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    // ---------------------------------------------------------------- validation helpers

    /** Row count of a whitelisted table. */
    public long count(String table) throws SQLException {
        if (!List.of("goals", "milestones", "tasks", "categories", "activities", "attachments", "settings").contains(table)) {
            throw new IllegalArgumentException("Unknown table: " + table);
        }
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Number of foreign key violations (should always be 0). */
    public long foreignKeyViolations() throws SQLException {
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
            int issues = 0;
            while (rs.next()) issues++;
            return issues;
        }
    }

    /** Integrity check report from SQLite itself (expected: "ok"). */
    public String integrityCheck() throws SQLException {
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** Attachment rows: id -> [goalId, name, mimeType, size, relativePath, createdAt]. */
    public Map<String, Object[]> allAttachments() throws SQLException {
        Map<String, Object[]> rows = new LinkedHashMap<>();
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, goal_id, name, mime_type, size, file_path, created_at FROM attachments")) {
            while (rs.next()) {
                rows.put(rs.getString(1), new Object[] {
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7) });
            }
        }
        return rows;
    }

    /** Orphaned child rows, if any (children whose goal no longer exists). */
    public long orphanedChildren() throws SQLException {
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM milestones WHERE goal_id NOT IN (SELECT id FROM goals)) + "
                             + "(SELECT COUNT(*) FROM tasks WHERE goal_id NOT IN (SELECT id FROM goals)) + "
                             + "(SELECT COUNT(*) FROM attachments WHERE goal_id NOT IN (SELECT id FROM goals))")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public Path databasePath() {
        return Path.of(database.path());
    }

    @Override
    public void close() throws SQLException {
        database.close();
    }

    // helpers

    private static String str(Object value) {
        if (value == null) throw new IllegalArgumentException("Required value missing.");
        return String.valueOf(value);
    }

    private static String strOr(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String nullable(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static Optional<Integer> optionalInt(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof Number number) return Optional.of(number.intValue());
        try {
            return Optional.of(Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String optionalJson(Object value) {
        if (value == null) return null;
        if (value instanceof String text) return text.isBlank() ? null : text;
        return Json.stringify(value);
    }
}
