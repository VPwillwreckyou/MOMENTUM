package dev.momentum.data;

import dev.momentum.core.Json;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    // ---------------------------------------------------------------- goal queries

    /** All non-trashed goals, newest first. status may be null (all), "active", "completed" or "archived". */
    public List<Map<String, Object>> listGoals(String status) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, description, notes, category_id, status, kind, priority, "
                        + "start_date, deadline, created_at, updated_at, completed_at, archived_at, deleted_at, "
                        + "series_id, occurrence, recurrence_json, finance_json, ai_generated "
                        + "FROM goals WHERE deleted_at IS NULL");
        if (status != null) sql.append(" AND status = ?");
        sql.append(" ORDER BY updated_at DESC");
        try (PreparedStatement stmt = connection().prepareStatement(sql.toString())) {
            if (status != null) stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> goals = new ArrayList<>();
                while (rs.next()) goals.add(goalRow(rs));
                return goals;
            }
        }
    }

    /** A single non-trashed goal by id. */
    public Optional<Map<String, Object>> getGoal(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT id, title, description, notes, category_id, status, kind, priority, "
                        + "start_date, deadline, created_at, updated_at, completed_at, archived_at, deleted_at, "
                        + "series_id, occurrence, recurrence_json, finance_json, ai_generated "
                        + "FROM goals WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(goalRow(rs)) : Optional.empty();
            }
        }
    }

    /** Create or replace a goal. created_at is preserved when present in the map. */
    public void upsertGoal(Map<String, Object> goal) throws SQLException {
        String id = str(goal.get("id"));
        String createdAt = nullable(goal.get("createdAt"));
        if (createdAt == null) createdAt = nowIso();
        String updatedAt = nowIso();
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT OR REPLACE INTO goals (id, title, description, notes, category_id, status, kind, priority, "
                        + "start_date, deadline, created_at, updated_at, completed_at, archived_at, deleted_at, "
                        + "series_id, occurrence, recurrence_json, finance_json, ai_generated) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, id);
            stmt.setString(2, strOr(goal.get("title"), ""));
            stmt.setString(3, strOr(goal.get("description"), ""));
            stmt.setString(4, strOr(goal.get("notes"), ""));
            stmt.setString(5, nullable(goal.get("categoryId")));
            stmt.setString(6, strOr(goal.get("status"), "active"));
            stmt.setString(7, strOr(goal.get("kind"), "one-time"));
            stmt.setString(8, strOr(goal.get("priority"), "medium"));
            stmt.setString(9, nullable(goal.get("startDate")));
            stmt.setString(10, nullable(goal.get("deadline")));
            stmt.setString(11, createdAt);
            stmt.setString(12, updatedAt);
            stmt.setString(13, nullable(goal.get("completedAt")));
            stmt.setString(14, nullable(goal.get("archivedAt")));
            stmt.setString(15, nullable(goal.get("deletedAt")));
            stmt.setString(16, nullable(goal.get("seriesId")));
            stmt.setObject(17, optionalInt(goal.get("occurrence")).orElse(null));
            stmt.setString(18, optionalJson(goal.get("recurrence")));
            stmt.setString(19, optionalJson(goal.get("finance")));
            stmt.setInt(20, Boolean.TRUE.equals(goal.get("aiGenerated")) ? 1 : 0);
            stmt.executeUpdate();
        }
    }

    /** Mark a goal archived (now) unless already archived. */
    public void archiveGoal(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE goals SET archived_at = ?, updated_at = ? WHERE id = ? AND archived_at IS NULL AND deleted_at IS NULL")) {
            stmt.setString(1, nowIso());
            stmt.setString(2, nowIso());
            stmt.setString(3, id);
            stmt.executeUpdate();
        }
    }

    /** Un-archive a goal. */
    public void restoreGoal(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE goals SET archived_at = NULL, updated_at = ? WHERE id = ? AND archived_at IS NOT NULL AND deleted_at IS NULL")) {
            stmt.setString(1, nowIso());
            stmt.setString(2, id);
            stmt.executeUpdate();
        }
    }

    /** Soft-delete a goal (trashed). Cascades to milestones, tasks, attachments and activities. */
    public void trashGoal(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE goals SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setString(1, nowIso());
            stmt.setString(2, nowIso());
            stmt.setString(3, id);
            stmt.executeUpdate();
        }
    }

    /** Restore a soft-deleted goal. */
    public void restoreGoalFromTrash(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE goals SET deleted_at = NULL, updated_at = ? WHERE id = ? AND deleted_at IS NOT NULL")) {
            stmt.setString(1, nowIso());
            stmt.setString(2, id);
            stmt.executeUpdate();
        }
    }

    /** Permanently remove a goal and its children. Use only after user confirmation. */
    public void purgeGoal(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement("DELETE FROM goals WHERE id = ?")) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }

    /** All soft-deleted goals (in the trash), newest first. */
    public List<Map<String, Object>> listTrashedGoals() throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT id, title, description, notes, category_id, status, kind, priority, "
                        + "start_date, deadline, created_at, updated_at, completed_at, archived_at, deleted_at, "
                        + "series_id, occurrence, recurrence_json, finance_json, ai_generated "
                        + "FROM goals WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> goals = new ArrayList<>();
                while (rs.next()) goals.add(goalRow(rs));
                return goals;
            }
        }
    }

    /** Number of non-trashed goals in a category. */
    public long goalsInCategory(String categoryId) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT COUNT(*) FROM goals WHERE category_id = ? AND deleted_at IS NULL")) {
            stmt.setString(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---------------------------------------------------------------- category queries

    /** All non-trashed categories, name ascending. */
    public List<Map<String, Object>> listCategories() throws SQLException {
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, name, color, icon, created_at, updated_at, deleted_at "
                             + "FROM categories WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE")) {
            List<Map<String, Object>> categories = new ArrayList<>();
            while (rs.next()) categories.add(categoryRow(rs));
            return categories;
        }
    }

    /** A single non-trashed category by id. */
    public Optional<Map<String, Object>> getCategory(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT id, name, color, icon, created_at, updated_at, deleted_at FROM categories WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(categoryRow(rs)) : Optional.empty();
            }
        }
    }

    /** Create or replace a category. */
    public void upsertCategory(Map<String, Object> category) throws SQLException {
        insertCategory(category);
    }

    /** Soft-delete a category (goals referencing it keep their category link nulled by ON DELETE SET NULL). */
    public void softDeleteCategory(String id) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE categories SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            stmt.setString(1, nowIso());
            stmt.setString(2, nowIso());
            stmt.setString(3, id);
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- milestone queries

    /** Milestones of a goal, sort order then id. */
    public List<Map<String, Object>> listMilestones(String goalId) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT id, goal_id, title, description, due_date, completed, completed_at, sort_order "
                        + "FROM milestones WHERE goal_id = ? ORDER BY sort_order, id")) {
            stmt.setString(1, goalId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> milestones = new ArrayList<>();
                while (rs.next()) milestones.add(milestoneRow(rs));
                return milestones;
            }
        }
    }

    /** Create or replace a milestone of a goal. */
    public void upsertMilestone(String goalId, Map<String, Object> milestone) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(milestone);
        row.put("goalId", goalId);
        insertMilestone(goalId, row);
    }

    /** Delete a milestone. */
    public void deleteMilestone(String milestoneId) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement("DELETE FROM milestones WHERE id = ?")) {
            stmt.setString(1, milestoneId);
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- task queries

    /** Tasks of a goal. completed may be null (all), "true" or "false". */
    public List<Map<String, Object>> listTasks(String goalId, String completed) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, goal_id, milestone_id, parent_task_id, title, notes, completed, "
                        + "completed_at, due_date, priority, sort_order, created_at, updated_at "
                        + "FROM tasks WHERE goal_id = ?");
        if (completed != null) sql.append(" AND completed = ?");
        sql.append(" ORDER BY sort_order, created_at");
        try (PreparedStatement stmt = connection().prepareStatement(sql.toString())) {
            stmt.setString(1, goalId);
            if (completed != null) stmt.setInt(2, Boolean.parseBoolean(completed) ? 1 : 0);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> tasks = new ArrayList<>();
                while (rs.next()) tasks.add(taskRow(rs));
                return tasks;
            }
        }
    }

    /** Create or replace a task. */
    public void upsertTask(Map<String, Object> task) throws SQLException {
        insertTask(task);
    }

    /** Delete a task (subtasks cascade). */
    public void deleteTask(String taskId) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            stmt.setString(1, taskId);
            stmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- activities

    /** Record an activity event. */
    public void recordActivity(String type, String goalId, String taskId, String message) throws SQLException {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO activities (id, type, goal_id, task_id, message, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, type);
            stmt.setString(3, nullable(goalId));
            stmt.setString(4, nullable(taskId));
            stmt.setString(5, str(message));
            stmt.setString(6, nowIso());
            stmt.executeUpdate();
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

    private static Map<String, Object> goalRow(ResultSet rs) throws SQLException {
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("id", rs.getString("id"));
        goal.put("title", rs.getString("title"));
        goal.put("description", rs.getString("description"));
        goal.put("notes", rs.getString("notes"));
        goal.put("categoryId", rs.getString("category_id"));
        goal.put("status", rs.getString("status"));
        goal.put("kind", rs.getString("kind"));
        goal.put("priority", rs.getString("priority"));
        goal.put("startDate", rs.getString("start_date"));
        goal.put("deadline", rs.getString("deadline"));
        goal.put("createdAt", rs.getString("created_at"));
        goal.put("updatedAt", rs.getString("updated_at"));
        goal.put("completedAt", rs.getString("completed_at"));
        goal.put("archivedAt", rs.getString("archived_at"));
        goal.put("deletedAt", rs.getString("deleted_at"));
        goal.put("seriesId", rs.getString("series_id"));
        goal.put("occurrence", rs.getObject("occurrence"));
        goal.put("recurrence", jsonOrNull(rs, "recurrence_json"));
        goal.put("finance", jsonOrNull(rs, "finance_json"));
        goal.put("aiGenerated", rs.getInt("ai_generated") == 1);
        return goal;
    }

    private static Map<String, Object> categoryRow(ResultSet rs) throws SQLException {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("id", rs.getString("id"));
        category.put("name", rs.getString("name"));
        category.put("color", rs.getString("color"));
        category.put("icon", rs.getString("icon"));
        category.put("createdAt", rs.getString("created_at"));
        category.put("updatedAt", rs.getString("updated_at"));
        category.put("deletedAt", rs.getString("deleted_at"));
        return category;
    }

    private static Map<String, Object> milestoneRow(ResultSet rs) throws SQLException {
        Map<String, Object> milestone = new LinkedHashMap<>();
        milestone.put("id", rs.getString("id"));
        milestone.put("goalId", rs.getString("goal_id"));
        milestone.put("title", rs.getString("title"));
        milestone.put("description", rs.getString("description"));
        milestone.put("dueDate", rs.getString("due_date"));
        milestone.put("completed", rs.getInt("completed") == 1);
        milestone.put("completedAt", rs.getString("completed_at"));
        milestone.put("order", rs.getInt("sort_order"));
        return milestone;
    }

    private static Map<String, Object> taskRow(ResultSet rs) throws SQLException {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", rs.getString("id"));
        task.put("goalId", rs.getString("goal_id"));
        task.put("milestoneId", rs.getString("milestone_id"));
        task.put("parentTaskId", rs.getString("parent_task_id"));
        task.put("title", rs.getString("title"));
        task.put("notes", rs.getString("notes"));
        task.put("completed", rs.getInt("completed") == 1);
        task.put("completedAt", rs.getString("completed_at"));
        task.put("dueDate", rs.getString("due_date"));
        task.put("priority", rs.getString("priority"));
        task.put("order", rs.getInt("sort_order"));
        task.put("createdAt", rs.getString("created_at"));
        task.put("updatedAt", rs.getString("updated_at"));
        return task;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonOrNull(ResultSet rs, String column) throws SQLException {
        String json = rs.getString(column);
        if (json == null || json.isBlank()) return null;
        Object parsed = Json.parse(json);
        return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String nowIso() {
        return Instant.now().toString();
    }
}
