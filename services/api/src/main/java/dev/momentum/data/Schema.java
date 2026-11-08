package dev.momentum.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite schema creation and version management.
 */
public final class Schema {

    public static final int CURRENT_VERSION = 1;

    private Schema() {}

    public static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS goals (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT '',
                    category_id TEXT,
                    status TEXT NOT NULL DEFAULT 'active',
                    kind TEXT NOT NULL DEFAULT 'one-time',
                    priority TEXT NOT NULL DEFAULT 'medium',
                    start_date TEXT,
                    deadline TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    completed_at TEXT,
                    archived_at TEXT,
                    deleted_at TEXT,
                    series_id TEXT,
                    occurrence INTEGER,
                    recurrence_json TEXT,
                    finance_json TEXT,
                    ai_generated INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS milestones (
                    id TEXT PRIMARY KEY,
                    goal_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    due_date TEXT,
                    completed INTEGER NOT NULL DEFAULT 0,
                    completed_at TEXT,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    goal_id TEXT NOT NULL,
                    milestone_id TEXT,
                    parent_task_id TEXT,
                    title TEXT NOT NULL,
                    notes TEXT,
                    completed INTEGER NOT NULL DEFAULT 0,
                    completed_at TEXT,
                    due_date TEXT,
                    priority TEXT NOT NULL DEFAULT 'medium',
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE,
                    FOREIGN KEY (milestone_id) REFERENCES milestones(id) ON DELETE SET NULL,
                    FOREIGN KEY (parent_task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    color TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    deleted_at TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS activities (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    goal_id TEXT,
                    task_id TEXT,
                    message TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS attachments (
                    id TEXT PRIMARY KEY,
                    goal_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    file_path TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS db_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_status ON goals(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_category ON goals(category_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_deleted ON goals(deleted_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_archived ON goals(archived_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_milestones_goal ON milestones(goal_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_goal ON tasks(goal_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_milestone ON tasks(milestone_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_parent ON tasks(parent_task_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activities_goal ON activities(goal_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_attachments_goal ON attachments(goal_id)");
        }
    }

    public static void setVersion(Connection conn, int version) throws SQLException {
        try (var stmt = conn.prepareStatement(
                "INSERT INTO db_meta (key, value) VALUES ('schema_version', ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            stmt.setString(1, String.valueOf(version));
            stmt.executeUpdate();
        }
    }

    public static int getVersion(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT value FROM db_meta WHERE key = 'schema_version'")) {
            if (rs.next()) return Integer.parseInt(rs.getString(1));
            return 0;
        }
    }
}
