# MOMENTUM Database Backup & Recovery

## Backup Strategy

MOMENTUM uses SQLite with WAL (Write-Ahead Logging) mode for its desktop
database (`%LOCALAPPDATA%\MOMENTUM\data/momentum.db` on Windows).

### Automatic Backups

- **Daily backup**: On application startup, `MigrationManager.ensureDailyBackup()`
  creates one timestamped backup per calendar day in
  `%LOCALAPPDATA%\MOMENTUM\data/backups/`.
- **Retention**: Only the 5 most recent backups are kept; older ones are
  pruned automatically (`BackupManager.KEEP = 5`).
- **Pre-migration backup**: Before any backup import (JSON restore), an
  automatic pre-migration backup of the current database is created.
- **WAL checkpoint**: Since Phase 5A.1, every backup checkpoints the WAL
  (`PRAGMA wal_checkpoint(TRUNCATE)`) before copying, ensuring all committed
  transactions are present in the backup file.

Backup file naming: `momentum-YYYYMMDD-HHMMSS.db` (UTC timestamp).

## Recovery Procedures

### Recovering from a Corrupted Database

If `momentum.db` is corrupted or unreadable:

1. **Stop the MOMENTUM desktop application** to prevent further writes.
2. **Locate your backups** in `%LOCALAPPDATA%\MOMENTUM\data/backups/`.
3. **Copy the most recent backup** over the corrupted database:
   ```powershell
   Copy-Item "$env:LOCALAPPDATA\MOMENTUM\data\backups\momentum-YYYYMMDD-HHMMSS.db" `
     "$env:LOCALAPPDATA\MOMENTUM\data\momentum.db"
   ```
4. **Restart MOMENTUM**. The application will initialize the database
   schema on first access if needed.

### Importing a JSON Backup

The dashboard includes an "Import backup..." option that imports a
JSON-format backup (web IndexedDB export format, version 2). This
replaces the entire current database:

1. Open MOMENTUM → Dashboard.
2. Click **"Import backup…"**.
3. Select the JSON backup file.
4. The import runs validation (integrity, foreign keys, attachment
   checksums) before promoting. If validation fails, the original
   database is untouched.

### Database Location

- **Windows**: `%LOCALAPPDATA%\MOMENTUM\data/`
- **macOS/Linux**: `~/.momentum/data/` (or `$MOMENTUM_DATA_DIR`)
- Override with the `MOMENTUM_DATA_DIR` environment variable or the
  `momentum.data.dir` system property.

## Important Notes

- **API keys**: AI API keys are encrypted with a device-bound key
  (Windows DPAPI or AES-GCM with machine-derived key since Phase 5A.2).
  A database copied to another machine will not be able to decrypt
  stored AI keys; users must re-enter them.
- **Auth sessions**: Session tokens are stored in `accounts.json`.
  Restoring an older database may invalidate existing sessions;
  users may need to sign in again.
- **Backup before import**: The import process automatically creates a
  pre-migration backup of the current database before replacing it.
