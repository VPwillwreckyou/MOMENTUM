package dev.momentum.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Automatic SQLite database backups.
 *
 * - Backups live in {dataDir}/backups/
 * - Naming: momentum-{yyyyMMdd-HHmmss}.db
 * - The 5 most recent backups are retained; older ones are pruned after a new backup is added.
 * - A copy of momentum.db is always taken before a migration replaces the database file.
 */
public final class BackupManager {

    public static final int KEEP = 5;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private static final Pattern BACKUP_NAME = Pattern.compile("^momentum-\\d{8}-\\d{6}\\.db$");

    private final Path dataDirectory;
    private final Path backupsDirectory;
    private final Path databaseFile;

    public BackupManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.backupsDirectory = dataDirectory.resolve("backups");
        this.databaseFile = dataDirectory.resolve("momentum.db");
    }

    public Path backupsDirectory() {
        return backupsDirectory;
    }

    /** Creates one timestamped backup of momentum.db (no-op when the database does not exist yet). */
    public String backup() throws IOException {
        if (!Files.isRegularFile(databaseFile)) return null;
        Files.createDirectories(backupsDirectory);
        String name = "momentum-" + TIMESTAMP.format(Instant.now()) + ".db";
        Path target = backupsDirectory.resolve(name);
        // Checkpoint the WAL before copying so all committed transactions are flushed into the
        // main database file. A plain file copy of a WAL-mode database can miss transactions
        // that are still in the -wal file at copy time.
        try (Database database = new Database(dataDirectory.toString());
             Statement stmt = database.connection().createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException e) {
            throw new IOException("Could not checkpoint database before backup", e);
        }
        Files.copy(databaseFile, target, StandardCopyOption.REPLACE_EXISTING);
        prune();
        return name;
    }

    /** Keeps only the most recent KEEP backups. */
    public void prune() throws IOException {
        if (!Files.isDirectory(backupsDirectory)) return;
        try (var files = Files.list(backupsDirectory)) {
            var backups = files
                    .filter(path -> BACKUP_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted(java.util.Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
            for (int index = KEEP; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
    }
}
