package dev.momentum;

import dev.momentum.data.MigrationManager;
import dev.momentum.data.SqliteRepository;
import dev.momentum.data.Database;
import dev.momentum.data.AttachmentStore;
import dev.momentum.data.BackupManager;
import dev.momentum.core.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Comparator;

/**
 * Phase 2 data-migration tests.
 *
 * All tests use SYNTHETIC copied test data inside temporary directories —
 * the real user database and the browser's IndexedDB are never touched.
 */
final class DataMigrationTest {

    private static final Random RANDOM = new Random(20261108L);

    private DataMigrationTest() {}

    /** Full round trip: 600 goals / ~2,100 subtasks & tasks / 40 categories / backup with checksums. */
    static void importRoundTrip() throws Exception {
        Path temp = Files.createTempDirectory("momentum-migration-ok-");
        try {
            Map<String, Object> backup = syntheticBackup(600, 40, 15);
            MigrationManager manager = new MigrationManager(temp);
            Map<String, Object> report = manager.importBackupJson(Json.stringify(backup));

            if (!Boolean.TRUE.equals(report.get("ok"))) throw new AssertionError("Import report not ok: " + report);
            @SuppressWarnings("unchecked")
            Map<String, Object> counts = (Map<String, Object>) report.get("counts");
            assertEquals(600.0, counts.get("goals"));
            assertEquals(40.0, counts.get("categories"));
            assertEquals(1200.0, counts.get("milestones"));   // 2 per goal
            assertEquals(3601.0, counts.get("tasks"));        // 5 + 1 subtask per goal + 1 tolerated orphan
            assertEquals(15.0, counts.get("attachments"));
            if (!"ok".equals(String.valueOf(report.get("integrityCheck")))) {
                throw new AssertionError("Integrity check failed: " + report);
            }
            assertEquals(0.0, report.get("foreignKeyViolations"));
            assertEquals(15.0, report.get("attachmentFilesVerified"));

            // Status report through the same facade the JavaFX app will use.
            Map<String, Object> status = manager.status();
            if (!Boolean.TRUE.equals(status.get("databaseExists"))) throw new AssertionError("db missing after import");
            @SuppressWarnings("unchecked")
            Map<String, Object> statusCounts = (Map<String, Object>) status.get("counts");
            assertEquals(600.0, statusCounts.get("goals"));

            // Field-level spot checks: recurrence + finance round trip, subtask parent link.
            try (Database database = new Database(temp.toString());
                 SqliteRepository repository = new SqliteRepository(database)) {

                try (PreparedStatement stmt = repository.connection().prepareStatement(
                        "SELECT recurrence_json, finance_json, ai_generated, status, kind FROM goals WHERE id = ?")) {
                    stmt.setString(1, "goal-20");
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        String recurrence = rs.getString(1);
                        if (!recurrence.contains("\"frequency\"") || !recurrence.contains("\"interval\"")) {
                            throw new AssertionError("Recurrence JSON was not preserved: " + recurrence);
                        }
                        if (!rs.getString(2).contains("\"currency\":\"USD\"")) {
                            throw new AssertionError("Finance JSON was not preserved.");
                        }
                        if (rs.getInt(3) != 1 || !"completed".equals(rs.getString(4))
                                || !"recurring".equals(rs.getString(5))) {
                            throw new AssertionError("Goal columns were not preserved.");
                        }
                    }
                }

                try (PreparedStatement stmt = repository.connection().prepareStatement(
                        "SELECT COUNT(*) FROM tasks WHERE parent_task_id = (SELECT id FROM tasks WHERE goal_id = 'goal-20' ORDER BY sort_order LIMIT 1)")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) < 1) throw new AssertionError("Subtask parent link lost.");
                    }
                }

                // Detached subtask fallback: a task with an unknown parent was kept.
                try (PreparedStatement stmt = repository.connection().prepareStatement(
                        "SELECT COUNT(*) FROM tasks WHERE parent_task_id IS NULL AND title = 'orphan-subtask'")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) != 1) throw new AssertionError("Orphan subtask was dropped.");
                    }
                }

                // Attachment checksum audit recorded in db_meta.
                String checksumsJson = repository.meta("attachment_checksums")
                        .orElseThrow(() -> new AssertionError("No attachment checksum audit stored."));
                @SuppressWarnings("unchecked")
                Map<String, Object> checksums = Json.parseObject(checksumsJson);
                String stored = String.valueOf(checksums.get("att-3"));
                AttachmentStore store = new AttachmentStore(temp);
                Path file = store.resolveRelative(String.valueOf(checksums.get("att-3")).isEmpty() ? "" : relative(
                        repository, "att-3"));
                String recomputed = sha256Hex(Files.readAllBytes(file));
                if (!stored.equals(recomputed)) {
                    throw new AssertionError("Attachment checksum mismatch: " + stored + " vs " + recomputed);
                }

                // Web backup settings snapshot survived.
                if (repository.setting("settings").isEmpty()) {
                    throw new AssertionError("Settings snapshot missing.");
                }
            }
            manager.close();
        } finally {
            wipe(temp);
        }
    }

    /** Failed import changes nothing: the old database and attachment files survive untouched. */
    static void rollbackOnCorruptBackup() throws Exception {
        Path temp = Files.createTempDirectory("momentum-migration-rb-");
        try {
            MigrationManager manager = new MigrationManager(temp);
            manager.importBackupJson(Json.stringify(syntheticBackup(20, 5, 4)));

            Path database = temp.resolve("momentum.db");
            Path checksumBefore = Path.of(temp.resolve("momentum.db") + ".copy");
            Files.copy(database, checksumBefore);

            // Corrupt attachment payload (broken base64) inside an otherwise valid backup.
            Map<String, Object> backup = syntheticBackup(30, 5, 4);
            @SuppressWarnings("unchecked")
            List<Object> attachments = (List<Object>) backup.get("attachments");
            @SuppressWarnings("unchecked")
            Map<String, Object> bad = (Map<String, Object>) attachments.get(1);
            bad.put("dataUrl", "data:image/png;base64,@@@not-base64@@@");

            String report = null;
            try {
                manager.importBackupJson(Json.stringify(backup));
                throw new AssertionError("Corrupt backup import should have failed.");
            } catch (IllegalArgumentException | IllegalStateException expected) {
                report = expected.getMessage();
            }
            if (report == null || report.isBlank()) throw new AssertionError("No failure reason reported.");

            // Original database bytes unchanged.
            if (!java.util.Arrays.equals(Files.readAllBytes(checksumBefore), Files.readAllBytes(database))) {
                throw new AssertionError("The database changed during a failed import!");
            }

            // Counts are still those of the first import.
            @SuppressWarnings("unchecked")
            Map<String, Object> counts = (Map<String, Object>) manager.status().get("counts");
            assertEquals(20.0, counts.get("goals"));
            assertEquals(4.0, counts.get("attachments"));

            // No temporary artifacts left behind.
            if (Files.exists(temp.resolve("migration-import-tmp"))) {
                throw new AssertionError("Temporary import artifacts were not cleaned up.");
            }

            // Original attachment files still present.
            try (Database database2 = new Database(temp.toString());
                 SqliteRepository repository = new SqliteRepository(database2)) {
                for (Map.Entry<String, Object[]> entry : repository.allAttachments().entrySet()) {
                    if (!new AttachmentStore(temp).fileExists(String.valueOf(entry.getValue()[4]))) {
                        throw new AssertionError("Original attachment file lost: " + entry.getKey());
                    }
                }
            }
            manager.close();
        } finally {
            wipe(temp);
        }
    }

    /** A size mismatch between attachment metadata and payload aborts the migration. */
    static void rollbackOnAttachmentSizeMismatch() throws Exception {
        Path temp = Files.createTempDirectory("momentum-migration-size-");
        try {
            MigrationManager manager = new MigrationManager(temp);
            Map<String, Object> backup = syntheticBackup(10, 3, 3);
            @SuppressWarnings("unchecked")
            List<Object> attachments = (List<Object>) backup.get("attachments");
            @SuppressWarnings("unchecked")
            Map<String, Object> firstItem = (Map<String, Object>) attachments.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) firstItem.get("meta");
            meta.put("size", ((Number) meta.get("size")).longValue() + 1);

            try {
                manager.importBackupJson(Json.stringify(backup));
                throw new AssertionError("Size mismatch should have failed.");
            } catch (IllegalStateException expected) {
                // Expected: payload does not match metadata.
            }
            if (Files.exists(temp.resolve("momentum.db"))) {
                throw new AssertionError("Database was created despite validation failure.");
            }
            manager.close();
        } finally {
            wipe(temp);
        }
    }

    /** The daily backup manager keeps the most recent five backups. */
    static void backupRetentionPolicy() throws Exception {
        Path temp = Files.createTempDirectory("momentum-migration-bak-");
        try {
            Files.write(temp.resolve("momentum.db"), new byte[] {1, 2, 3, 4});
            BackupManager manager = new BackupManager(temp);
            for (int index = 0; index < 7; index++) {
                String name = manager.backup();
                if (name == null) throw new AssertionError("Backup should not be null.");
                Thread.sleep(1100); // Timestamped names need distinct seconds.
            }
            try (var files = Files.list(temp.resolve("backups"))) {
                long count = files.filter(p -> p.getFileName().toString().endsWith(".db")).count();
                if (count > BackupManager.KEEP) {
                    throw new AssertionError("Backup retention violated: " + count + " backups kept.");
                }
            }
        } finally {
            wipe(temp);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Builds the exact shape the web app exports (app=momentum, version=2). */
    private static Map<String, Object> syntheticBackup(int goalCount, int categoryCount, int attachmentCount) {
        Map<String, Object> categories = new LinkedHashMap<>();
        for (int index = 0; index < categoryCount; index++) {
            Map<String, Object> category = new LinkedHashMap<>();
            category.put("id", "category-" + index);
            category.put("name", "Category " + index);
            category.put("color", "#3b82f6");
            category.put("icon", "target");
            category.put("createdAt", "2026-01-01T00:00:00Z");
            category.put("updatedAt", "2026-01-02T00:00:00Z");
            category.put("deletedAt", null);
            categories.put(String.valueOf(index), category);
        }

        Map<String, Object> goals = new LinkedHashMap<>();
        long milestoneCount = 0;
        long taskCount = 0;
        for (int index = 0; index < goalCount; index++) {
            Map<String, Object> goal = new LinkedHashMap<>();
            goal.put("id", "goal-" + index);
            goal.put("title", "Goal " + index);
            goal.put("description", "Description for goal " + index);
            goal.put("notes", "Notes with special chars \"<>& café\".");
            goal.put("categoryId", "category-" + (index % categoryCount));
            goal.put("status", index % 4 == 0 ? "completed" : "active");
            goal.put("kind", index % 10 == 0 ? "recurring" : "one-time");
            goal.put("priority", new String[] {"low", "medium", "high", "critical"}[index % 4]);
            goal.put("startDate", "2026-01-03");
            goal.put("deadline", "2026-12-31");
            goal.put("createdAt", "2026-01-01T00:00:00Z");
            goal.put("updatedAt", "2026-01-05T00:00:00Z");
            goal.put("completedAt", index % 4 == 0 ? "2026-02-01T00:00:00Z" : null);
            goal.put("archivedAt", null);
            goal.put("deletedAt", null);
            goal.put("seriesId", null);
            goal.put("occurrence", index % 10 == 0 ? index : null);

            List<Object> milestones = new java.util.ArrayList<>();
            for (int milestone = 0; milestone < 2; milestone++) {
                milestoneCount++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "ms-" + milestoneCount);
                item.put("title", "Milestone " + milestoneCount);
                item.put("description", "Description " + milestoneCount);
                item.put("dueDate", "2026-06-01");
                item.put("completed", milestone % 2 == 0);
                item.put("completedAt", milestone % 2 == 0 ? "2026-05-01T00:00:00Z" : null);
                item.put("order", milestone);
                milestones.add(item);
            }
            goal.put("milestones", milestones);

            List<Object> tasks = new java.util.ArrayList<>();
            String firstTaskId = null;
            for (int task = 0; task < 5; task++) {
                taskCount++;
                Map<String, Object> item = new LinkedHashMap<>();
                String id = "task-" + taskCount;
                item.put("id", id);
                item.put("goalId", "goal-" + index);
                item.put("milestoneId", task % 2 == 0 ? "ms-" + (milestoneCount - 1) : null);
                item.put("parentTaskId", null);
                item.put("title", "Task " + taskCount);
                item.put("notes", "Task notes " + taskCount);
                item.put("completed", task % 3 == 0);
                item.put("completedAt", task % 3 == 0 ? "2026-03-01T00:00:00Z" : null);
                item.put("dueDate", "2026-07-01");
                item.put("priority", new String[] {"low", "medium", "high", "critical"}[task % 4]);
                item.put("order", task);
                item.put("createdAt", "2026-01-01T00:00:00Z");
                item.put("updatedAt", "2026-01-04T00:00:00Z");
                if (firstTaskId == null) firstTaskId = id;
                tasks.add(item);
            }
            // One subtask per goal (parentTaskId must exist before the child row).
            taskCount++;
            Map<String, Object> subtask = new LinkedHashMap<>();
            subtask.put("id", "task-" + taskCount);
            subtask.put("goalId", "goal-" + index);
            subtask.put("milestoneId", null);
            subtask.put("parentTaskId", firstTaskId);
            subtask.put("title", "Subtask " + taskCount);
            subtask.put("completed", false);
            subtask.put("priority", "medium");
            subtask.put("order", 5);
            subtask.put("createdAt", "2026-01-01T00:00:00Z");
            subtask.put("updatedAt", "2026-01-04T00:00:00Z");
            tasks.add(subtask);
            // Every 25th goal carries an orphaned parent reference (corrupt backup tolerance).
            if (index == 24) {
                Map<String, Object> orphan = new LinkedHashMap<>(subtask);
                orphan.put("id", "task-orphan-1");
                orphan.put("parentTaskId", "task-unknown");
                orphan.put("title", "orphan-subtask");
                tasks.add(orphan);
            }

            goal.put("tasks", tasks);

            if (index % 10 == 0) {
                goal.put("recurrence", Map.of("frequency", "weekly", "interval", 2));
            } else {
                goal.put("recurrence", null);
            }
            goal.put("finance", index % 10 == 0
                    ? Map.of("enabled", true, "currency", "USD", "targetAmount", 1000, "currentAmount", 250)
                    : null);
            goal.put("aiGenerated", index % 20 == 0);
            goals.put(String.valueOf(index), goal);
        }

        Map<String, Object> activities = new LinkedHashMap<>();
        for (int index = 0; index < goalCount * 2; index++) {
            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("id", "activity-" + index);
            activity.put("goalId", "goal-" + (index % goalCount));
            activity.put("type", "goal-created");
            activity.put("message", "Activity " + index);
            activity.put("at", "2026-01-06T00:00:00Z");
            activities.put(String.valueOf(index), activity);
        }

        List<Object> attachments = new java.util.ArrayList<>();
        for (int index = 0; index < attachmentCount; index++) {
            byte[] payload = new byte[10_000 + RANDOM.nextInt(90_000)];
            RANDOM.nextBytes(payload);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", "att-" + index);
            meta.put("goalId", "goal-" + (index % goalCount));
            meta.put("name", index % 2 == 0 ? "report " + index + ".txt" : "image ünïcode " + index + ".png");
            meta.put("mimeType", index % 2 == 0 ? "text/plain" : "image/png");
            meta.put("size", payload.length);
            meta.put("createdAt", "2026-01-07T00:00:00Z");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("meta", meta);
            item.put("dataUrl", "data:text/plain;base64,"
                    + Base64.getMimeEncoder().encodeToString(payload));
            attachments.add(item);
        }

        Map<String, Object> workspace = new LinkedHashMap<>();
        workspace.put("schemaVersion", 1);
        workspace.put("installationId", "install-test-123");
        workspace.put("goals", new java.util.ArrayList<>(goals.values()));
        workspace.put("categories", new java.util.ArrayList<>(categories.values()));
        workspace.put("activities", new java.util.ArrayList<>(activities.values()));
        workspace.put("createdAt", "2026-01-01T00:00:00Z");
        workspace.put("updatedAt", "2026-01-08T00:00:00Z");

        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("app", "momentum");
        backup.put("version", 2);
        backup.put("exportedAt", "2026-01-08T00:00:00Z");
        backup.put("workspace", workspace);
        backup.put("settings", Map.of("schemaVersion", 1, "themeMode", "dark", "accent", "#3b82f6"));
        backup.put("attachments", attachments);
        return backup;
    }

    private static String relative(SqliteRepository repository, String id) throws Exception {
        try (PreparedStatement stmt = repository.connection().prepareStatement(
                "SELECT file_path FROM attachments WHERE id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void wipe(Path directory) throws Exception {
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Best effort cleanup.
                }
            });
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected instanceof Number number && actual instanceof Number other) {
            if (number.doubleValue() != other.doubleValue()) {
                throw new AssertionError("Expected " + expected + " but got " + actual);
            }
            return;
        }
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
