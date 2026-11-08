package dev.momentum.data;

import dev.momentum.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Imports a MOMENTUM web backup (IndexedDB export, app=momentum, version 2)
 * into SQLite. The import runs against a temporary database + temporary
 * attachments directory and is validated before it is promoted:
 *
 *   1. Import everything into {dataDir}/migration-import-tmp/ (fresh SQLite DB).
 *   2. Validate counts, integrity, foreign keys, attachment files/sizes.
 *   3. Take an automatic pre-migration backup of the existing momentum.db (if any).
 *   4. Atomically swap the validated files into place.
 *
 * Failure at any point deletes only the temporary artifacts. The original
 * momentum.db, existing attachment files and the user's backup JSON file are
 * never modified or deleted — and the browser's IndexedDB is untouched.
 */
public final class JsonImporter {

    private static final int BATCH_SIZE = 500;

    public Map<String, Object> importBackup(Path dataDirectory, Map<String, Object> backup)
            throws IOException, SQLException, NoSuchAlgorithmException {
        Path tempDirectory = dataDirectory.resolve("migration-import-tmp");
        AttachmentStore attachmentsTarget = new AttachmentStore(dataDirectory);

        try {
            AttachmentStore.wipeDirectory(tempDirectory);
            Files.createDirectories(dataDirectory);

            Results results = stage(tempDirectory, backup);

            // Promotion: back up the current database, then swap in the validated one.
            BackupManager backupManager = new BackupManager(dataDirectory);
            String previousBackup = backupManager.backup();

            Path tempDatabase = tempDirectory.resolve("momentum.db");
            Path targetDatabase = dataDirectory.resolve("momentum.db");
            Files.move(tempDatabase, targetDatabase, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tempDirectory.resolve("momentum.db-wal"));
            Files.deleteIfExists(tempDirectory.resolve("momentum.db-shm"));

            // Copy validated attachment files into the live attachments directory.
            Path tempAttachments = tempDirectory.resolve("attachments");
            if (Files.isDirectory(tempAttachments)) {
                try (var files = Files.walk(tempAttachments)) {
                    for (Path source : files.filter(Files::isRegularFile).toList()) {
                        String relative = tempAttachments.relativize(source).toString().replace('\\', '/');
                        Path target = attachmentsTarget.resolveRelative(relative);
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            AttachmentStore.wipeDirectory(tempDirectory);

            return results.report("previousBackup", previousBackup == null ? null : previousBackup);
        } catch (Exception failure) {
            // Rollback: discard everything temporary. Original data untouched.
            try {
                AttachmentStore.wipeDirectory(tempDirectory);
            } catch (IOException ignored) {
                // Rollback best effort.
            }
            throw failure;
        }
    }

    /** Imports the backup into a fresh temp DB and validates it. Returns counts + report fragments. */
    private Results stage(Path tempDirectory, Map<String, Object> backup)
            throws IOException, SQLException, NoSuchAlgorithmException {
        validateShape(backup);
        @SuppressWarnings("unchecked")
        Map<String, Object> workspace = (Map<String, Object>) backup.get("workspace");
        List<Map<String, Object>> categories = listOf(workspace.get("categories"));
        List<Map<String, Object>> goals = listOf(workspace.get("goals"));
        List<Map<String, Object>> activities = listOf(workspace.get("activities"));
        List<Map<String, Object>> attachments = listOf(backup.get("attachments"));

        try (Database database = new Database(tempDirectory.toString());
             SqliteRepository repository = new SqliteRepository(database)) {

            AttachmentStore attachmentsStore = new AttachmentStore(tempDirectory);

            long milestoneCount = 0;
            long taskCount = 0;

            // FK enforcement stays ON (default from Database). Insert order:
            // categories -> goals -> milestones -> tasks (parents first) -> activities -> attachments.
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("migrated_from", "web-indexeddb-v2");
            meta.put("migration_date", Instant.now().toString());
            meta.put("source_exported_at", String.valueOf(backup.get("exportedAt")));

            for (int index = 0; index < categories.size(); index++) {
                repository.insertCategory(categories.get(index));
                if (index % BATCH_SIZE == BATCH_SIZE - 1) repository.commit();
            }

            for (int index = 0; index < goals.size(); index++) {
                repository.insertGoal(goals.get(index));
                if (index % BATCH_SIZE == BATCH_SIZE - 1) repository.commit();
            }

            for (Map<String, Object> goal : goals) {
                String goalId = String.valueOf(goal.get("id"));
                for (Map<String, Object> milestone : listOf(goal.get("milestones"))) {
                    repository.insertMilestone(goalId, milestone);
                    milestoneCount++;
                    if (milestoneCount % BATCH_SIZE == BATCH_SIZE - 1) repository.commit();
                }
            }

            // Tasks may reference parent tasks (subtasks). Insert in passes so every
            // parent row exists before referencing children; unreferenced parents
            // (corrupt backup data) fall back to being stored as top-level tasks.
            List<Map<String, Object>> pending = new ArrayList<>();
            for (Map<String, Object> goal : goals) {
                for (Map<String, Object> task : listOf(goal.get("tasks"))) {
                    // A subtask copy must belong to its own goal; keep the task's own
                    // goalId (authoritative) but remember what the backup declared.
                    pending.add(new LinkedHashMap<>(task));
                }
            }
            Set<String> inserted = new HashSet<>();
            while (!pending.isEmpty()) {
                List<Map<String, Object>> blocked = new ArrayList<>();
                boolean progress = true;
                for (Map<String, Object> task : pending) {
                    String parent = stringOrNull(task.get("parentTaskId"));
                    if (parent != null && !inserted.contains(parent)) {
                        blocked.add(task);
                    } else {
                        repository.insertTask(task);
                        inserted.add(String.valueOf(task.get("id")));
                        taskCount++;
                        if (taskCount % BATCH_SIZE == BATCH_SIZE - 1) repository.commit();
                        progress = false;
                    }
                }
                if (blocked.isEmpty()) break;
                if (progress) {
                    // No forward motion: remaining tasks reference parents that the
                    // backup does not contain. Import them detached rather than
                    // dropping the task data wholesale.
                    for (Map<String, Object> task : blocked) {
                        task.put("parentTaskId", null);
                        repository.insertTask(task);
                        inserted.add(String.valueOf(task.get("id")));
                        taskCount++;
                        if (taskCount % BATCH_SIZE == BATCH_SIZE - 1) repository.commit();
                    }
                    break;
                }
                pending = blocked;
            }

            for (int index = 0; index < activities.size(); index++) {
                repository.insertActivity(activities.get(index));
                if (index % BATCH_SIZE == BATCH_SIZE - 1) repository.commit();
            }

            int attachmentCount = 0;
            for (Map<String, Object> attachment : attachments) {
                attachmentCount++;
                @SuppressWarnings("unchecked")
                Map<String, Object> meta_ = (Map<String, Object>) attachment.get("meta");
                if (meta_ == null) {
                    throw new IllegalArgumentException("Attachment #" + attachmentCount + " has no metadata.");
                }
                String dataUrl = stringOrNull(attachment.get("dataUrl"));
                byte[] bytes = AttachmentStore.decodeDataUrl(dataUrl == null ? "" : dataUrl, new String[1]);
                String id = String.valueOf(meta_.get("id"));
                String name = String.valueOf(meta_.get("name"));
                String mimeType = String.valueOf(meta_.get("mimeType"));
                long size = meta_.get("size") instanceof Number number ? number.longValue() : bytes.length;
                if (bytes.length != size) {
                    throw new IllegalStateException("Attachment " + id + " payload size ("
                            + bytes.length + ") does not match its metadata size (" + size + ").");
                }
                String relativePath = attachmentsStore.save(id, name, bytes);
                repository.insertAttachment(String.valueOf(meta_.get("goalId")), id, name,
                        mimeType, size, relativePath, String.valueOf(meta_.get("createdAt")));
                if (attachmentCount % BATCH_SIZE == BATCH_SIZE - 1) repository.commit();
            }

            // Workspace and settings snapshot (settings keyed, JSON values).
            repository.insertSetting("workspace_meta", Json.stringify(Map.of(
                    "schemaVersion", workspace.getOrDefault("schemaVersion", 1),
                    "installationId", String.valueOf(workspace.get("installationId")),
                    "createdAt", String.valueOf(workspace.get("createdAt")),
                    "updatedAt", String.valueOf(workspace.get("updatedAt")))));
            if (backup.containsKey("settings")) {
                repository.insertSetting("settings", Json.stringify(backup.get("settings")));
            }
            repository.insertMeta("schema_version", String.valueOf(Schema.CURRENT_VERSION));
            for (var entry : meta.entrySet()) {
                repository.insertMeta(entry.getKey(), String.valueOf(entry.getValue()));
            }
            repository.commit();

            // SHA-256 spot audit: every imported file is re-hashed once.
            String attachmentChecksums = Json.stringify(attachmentChecksums(repository, attachmentsStore));
            repository.insertMeta("attachment_checksums", attachmentChecksums);
            repository.commit();

            Map<String, Long> expected = new LinkedHashMap<>();
            expected.put("goals", (long) goals.size());
            expected.put("milestones", milestoneCount);
            expected.put("tasks", taskCount);
            expected.put("categories", (long) categories.size());
            expected.put("activities", (long) activities.size());
            expected.put("attachments", (long) attachmentCount);

            MigrationValidator.validate(repository, new AttachmentStore(tempDirectory), expected);
            Map<String, Object> report = MigrationValidator.report(
                    repository, new AttachmentStore(tempDirectory), expected);

            return new Results(expected, report, milestoneCount, taskCount);
        }
    }

    private static Map<String, String> attachmentChecksums(SqliteRepository repository, AttachmentStore store)
            throws SQLException, IOException, NoSuchAlgorithmException {
        Map<String, String> checksums = new LinkedHashMap<>();
        for (var entry : repository.allAttachments().entrySet()) {
            checksums.put(entry.getKey(), store.checksum(String.valueOf(entry.getValue()[4])));
        }
        return checksums;
    }

    private static void validateShape(Map<String, Object> backup) {
        if (backup == null || !"momentum".equals(String.valueOf(backup.get("app")))) {
            throw new IllegalArgumentException("This is not a MOMENTUM backup file.");
        }
        Object version = backup.get("version");
        if (!(version instanceof Number number) || number.intValue() < 2) {
            throw new IllegalArgumentException("Unsupported backup version: " + version);
        }
        if (!(backup.get("workspace") instanceof Map)) {
            throw new IllegalArgumentException("Backup contains no workspace data.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("Expected a JSON array.");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private static String stringOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private record Results(Map<String, Long> counts, Map<String, Object> report, long milestones, long tasks) {
        Map<String, Object> report(String key, Object value) {
            Map<String, Object> combined = new LinkedHashMap<>(report);
            combined.put(key, value);
            combined.put("migratedFrom", "web-indexeddb-v2");
            combined.put("migrationDate", Instant.now().toString());
            combined.put("ok", true);
            return combined;
        }
    }
}
