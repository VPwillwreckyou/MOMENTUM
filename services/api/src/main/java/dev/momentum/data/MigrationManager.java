package dev.momentum.data;

import dev.momentum.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public entry point for the Phase 2 data layer, shared by the HTTP service
 * and the upcoming JavaFX desktop app.
 *
 * Responsibilities:
 *  - open/inspect the live SQLite database (status report)
 *  - import a MOMENTUM web backup (IndexedDB export) with validation + rollback
 *  - automatic database backups (daily on startup, pre-migration always)
 */
public final class MigrationManager implements AutoCloseable {

    private final Path dataDirectory;
    private final BackupManager backupManager;
    private final JsonImporter importer = new JsonImporter();

    public MigrationManager(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(this.dataDirectory);
        this.backupManager = new BackupManager(this.dataDirectory);
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    /** Creates a daily backup of momentum.db (at most one per calendar day). */
    public String ensureDailyBackup() throws IOException {
        if (!Files.isRegularFile(dataDirectory.resolve("momentum.db"))) return null;
        String stamp = LocalDate.now(ZoneId.systemDefault()).toString();
        boolean backedUpToday = false;
        try (Database database = new Database(dataDirectory.toString());
             SqliteRepository repository = new SqliteRepository(database)) {
            Optional<String> last = repository.meta("last_backup_date");
            backedUpToday = last.isPresent() && stamp.equals(last.get());
            if (!backedUpToday) {
                repository.insertMeta("last_backup_date", stamp);
                repository.commit();
            }
        } catch (SQLException e) {
            throw new IOException("Could not read database metadata.", e);
        }
        return backedUpToday ? null : backupManager.backup();
    }

    /** Full status report about the local database and its backups. */
    public Map<String, Object> status() throws IOException {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("dataDirectory", dataDirectory.toString());
        status.put("databaseExists", Files.isRegularFile(dataDirectory.resolve("momentum.db")));
        try (Database database = new Database(dataDirectory.toString());
             SqliteRepository repository = new SqliteRepository(database)) {
            status.put("schemaVersion", Schema.getVersion(repository.connection()));
            Map<String, Object> counts = new LinkedHashMap<>();
            for (String table : new String[] {"goals", "milestones", "tasks", "categories", "activities", "attachments"}) {
                counts.put(table, repository.count(table));
            }
            status.put("counts", counts);
            status.put("integrityCheck", repository.integrityCheck());
            status.put("foreignKeyViolations", repository.foreignKeyViolations());
            status.put("migratedFrom", repository.meta("migrated_from").orElse(null));
            status.put("migrationDate", repository.meta("migration_date").orElse(null));
        } catch (SQLException e) {
            status.put("databaseExists", false);
            status.put("error", "The local database could not be opened: " + e.getMessage());
        }
        return status;
    }

    /**
     * Imports a MOMENTUM web backup. On success returns a validation report;
     * on failure nothing on disk changes (the previous database and all
     * pre-existing attachment files survive untouched).
     */
    public Map<String, Object> importBackupJson(String json) throws IOException, SQLException, NoSuchAlgorithmException {
        Map<String, Object> backup = Json.parseObject(json);
        return importer.importBackup(dataDirectory, backup);
    }

    private void recordDailyBackupStamp() throws IOException {
        String stamp = LocalDate.now(ZoneId.systemDefault()).toString();
        try (Database database = new Database(dataDirectory.toString());
             SqliteRepository repository = new SqliteRepository(database)) {
            repository.insertMeta("last_backup_date", stamp);
            repository.commit();
        } catch (SQLException e) {
            throw new IOException("Could not stamp the backup date.", e);
        }
    }

    @Override
    public void close() {
        // Stateless facade; nothing persistent to release.
    }

    /** Convenience for callers that just need "does the DB exist and is it healthy". */
    public boolean isHealthy() throws IOException {
        Map<String, Object> status = status();
        return Boolean.TRUE.equals(status.get("databaseExists"))
                && "ok".equals(String.valueOf(status.get("integrityCheck")));
    }

    /** Timestamp used in reports. */
    public static String now() {
        return Instant.now().toString();
    }
}
