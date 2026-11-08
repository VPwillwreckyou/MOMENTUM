package dev.momentum.data;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Migration validation: count comparison, SQLite integrity, foreign key check
 * and attachment file verification. All checks must pass for an import to be
 * considered successful; any failure aborts the migration (temp artifacts are
 * rolled back, the original database and the original backup file stay intact).
 */
public final class MigrationValidator {

    private MigrationValidator() {}

    public static void validate(SqliteRepository repository, AttachmentStore attachments,
                                Map<String, Long> expectedCounts) throws SQLException, IOException, NoSuchAlgorithmException {
        // 1. SQLite engine-level integrity.
        String integrity = repository.integrityCheck();
        if (!"ok".equals(integrity)) {
            throw new IllegalStateException("SQLite integrity check failed: " + integrity);
        }

        // 2. Row count comparison against the backup.
        for (var entry : expectedCounts.entrySet()) {
            long actual = repository.count(entry.getKey());
            if (actual != entry.getValue()) {
                throw new IllegalStateException("Row count mismatch for " + entry.getKey()
                        + ": expected " + entry.getValue() + ", imported " + actual);
            }
        }

        // 3. Foreign key integrity of the imported dataset.
        long violations = repository.foreignKeyViolations();
        if (violations != 0) {
            throw new IllegalStateException("Foreign key violations in imported data: " + violations);
        }
        long orphans = repository.orphanedChildren();
        if (orphans != 0) {
            throw new IllegalStateException("Orphaned milestones/tasks/attachments: " + orphans);
        }

        // 4. Attachment metadata and file existence + size verification.
        Map<String, Object[]> rows = repository.allAttachments();
        for (var entry : rows.entrySet()) {
            Object[] row = entry.getValue();
            String relativePath = String.valueOf(row[4]);
            long expectedSize = ((Number) row[3]).longValue();
            if (!attachments.fileExists(relativePath)) {
                throw new IllegalStateException("Attachment file missing after import: " + relativePath);
            }
            if (attachments.fileSize(relativePath) != expectedSize) {
                throw new IllegalStateException("Attachment size mismatch: " + relativePath);
            }
        }
    }

    /** Buildable validation report for the API response. */
    public static Map<String, Object> report(SqliteRepository repository, AttachmentStore attachments,
                                             Map<String, Long> expectedCounts)
            throws SQLException, IOException, NoSuchAlgorithmException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("integrityCheck", repository.integrityCheck());
        report.put("foreignKeyViolations", repository.foreignKeyViolations());
        report.put("orphanedRows", repository.orphanedChildren());
        Map<String, Object> counts = new LinkedHashMap<>();
        for (var entry : expectedCounts.entrySet()) {
            counts.put(entry.getKey(), repository.count(entry.getKey()));
        }
        report.put("counts", counts);
        long attachmentFiles = 0;
        for (Object[] row : repository.allAttachments().values()) {
            if (attachments.fileExists(String.valueOf(row[4]))) attachmentFiles++;
        }
        report.put("attachmentFilesVerified", attachmentFiles);
        return report;
    }
}
