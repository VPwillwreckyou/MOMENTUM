# MOMENTUM — SQLite Schema and Migration Strategy (Phase 2)

## 1. Overview

Phase 2 replaces IndexedDB (browser) with SQLite (desktop) for local structured data.
Attachments move from IndexedDB blobs to the local file system.
The existing encrypted sync envelope (AES-256-GCM, PBKDF2 310K iterations) is preserved exactly.

## 2. SQLite Schema

### 2.1 Database Location

- Windows: `%LOCALAPPDATA%\MOMENTUM\data\momentum.db`
- macOS/Linux: `~/.momentum/data/momentum.db` (or `$MOMENTUM_DATA_DIR/momentum.db`)

### 2.2 Schema (DDL)

```sql
-- Goals
CREATE TABLE goals (
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
    recurrence_json TEXT,        -- JSON serialized Recurrence
    finance_json TEXT,           -- JSON serialized FinancePlan
    ai_generated INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- Milestones (nested in goals)
CREATE TABLE milestones (
    id TEXT PRIMARY KEY,
    goal_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    due_date TEXT,
    completed INTEGER NOT NULL DEFAULT 0,
    completed_at TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
);

-- Tasks (nested in goals)
CREATE TABLE tasks (
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
);

-- Categories
CREATE TABLE categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    color TEXT NOT NULL,
    icon TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

-- Activities
CREATE TABLE activities (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    goal_id TEXT,
    task_id TEXT,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
);

-- Attachments metadata
CREATE TABLE attachments (
    id TEXT PRIMARY KEY,
    goal_id TEXT NOT NULL,
    name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size INTEGER NOT NULL,
    file_path TEXT NOT NULL,     -- relative path in data/attachments/
    created_at TEXT NOT NULL,
    FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
);

-- Settings (key-value)
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL          -- JSON serialized
);

-- Metadata
CREATE TABLE db_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
-- db_meta contains: schema_version, migrated_from, migration_date
```

### 2.3 Attachment Storage

Attachments are stored as files in:
- Windows: `%LOCALAPPDATA%\MOMENTUM\data\attachments\{attachment_id}\{filename}`
- macOS/Linux: `~/.momentum/data/attachments/{attachment_id}/{filename}`

Metadata (name, mime type, size, path) is stored in the `attachments` table.

### 2.4 Indexes

```sql
CREATE INDEX idx_goals_status ON goals(status);
CREATE INDEX idx_goals_category ON goals(category_id);
CREATE INDEX idx_goals_deleted ON goals(deleted_at);
CREATE INDEX idx_goals_archived ON goals(archived_at);
CREATE INDEX idx_milestones_goal ON milestones(goal_id);
CREATE INDEX idx_tasks_goal ON tasks(goal_id);
CREATE INDEX idx_tasks_milestone ON tasks(milestone_id);
CREATE INDEX idx_tasks_parent ON tasks(parent_task_id);
CREATE INDEX idx_activities_goal ON activities(goal_id);
CREATE INDEX idx_attachments_goal ON attachments(goal_id);
```

## 3. Migration Strategy

### 3.1 Overview

The migration is a **one-way, non-destructive** process:
1. Read existing IndexedDB data (exported as JSON backup)
2. Create SQLite database with the schema above
3. Insert all data into SQLite
4. Copy attachment blobs to file system
5. Validate migration (count checks, integrity checks)
6. Mark migration as complete in db_meta

**IndexedDB data is NEVER deleted.** The migration reads from a JSON backup file and creates a new SQLite database. The original IndexedDB remains untouched.

### 3.2 Migration Steps

1. **Export from browser:** User exports JSON backup from web app (includes goals, categories, tasks, milestones, attachments as base64)
2. **Import to desktop:** JavaFX app reads the JSON backup and inserts into SQLite
3. **Attachment extraction:** Base64-encoded attachment blobs are decoded and written to `data/attachments/`
4. **Validation:** Count all entities, verify foreign key integrity, verify attachment files exist
5. **Rollback:** If validation fails, delete the SQLite database and attachment files, keep the JSON backup

### 3.3 Encrypted Sync Compatibility

The sync envelope (AES-256-GCM, PBKDF2-SHA-256, 310,000 iterations) is **unchanged**. The server-side sync store (`SyncStore.java`) is not modified. The desktop app calls the same sync methods with the same encryption parameters. No migration needed for sync data.

### 3.4 Automatic Backups

- On startup: copy `momentum.db` to `momentum.db.backup` (if not already backed up today)
- Before migration: copy to `momentum.db.pre-migration`
- Keep last 5 backups in `data/backups/`
- Backup naming: `momentum-{timestamp}.db`

### 3.5 Migration Validation

After migration, verify:
- Goal count matches
- Milestone count matches (sum across all goals)
- Task count matches (sum across all goals)
- Category count matches
- Activity count matches
- Attachment count matches (and all files exist)
- Foreign key integrity (PRAGMA foreign_key_check)
- No orphaned milestones/tasks/attachments

### 3.6 Rollback

If migration fails:
1. Delete the SQLite database file
2. Delete the attachments directory
3. Keep the JSON backup file
4. User can retry migration or continue using web app

## 4. Java Implementation Plan

### 4.1 New Files

- `services/api/src/main/java/dev/momentum/data/Database.java` — SQLite connection management
- `services/api/src/main/java/dev/momentum/data/Schema.java` — DDL and schema version
- `services/api/src/main/java/dev/momentum/data/SqliteRepository.java` — data access layer
- `services/api/src/main/java/dev/momentum/data/AttachmentStore.java` — file-based attachment storage
- `services/api/src/main/java/dev/momentum/data/BackupManager.java` — automatic backups
- `services/api/src/main/java/dev/momentum/data/MigrationValidator.java` — validation checks
- `services/api/src/main/java/dev/momentum/data/JsonImporter.java` — JSON backup import

### 4.2 Modified Files

- `services/api/scripts/build.ps1` — add SQLite JDBC to classpath
- `services/api/scripts/test.ps1` — add SQLite JDBC to classpath
- `scripts/momentum.sh` — add SQLite JDBC to classpath

### 4.3 Gradle Dependencies (for JavaFX app, Phase 3)

```groovy
dependencies {
    implementation 'org.xerial:sqlite-jdbc:3.45.1.0'
    implementation 'com.google.code.gson:gson:2.10.1'  // JSON parsing
}
```

## 5. Test Plan

### 5.1 Unit Tests

- Database creation and schema validation
- CRUD operations for all entities
- Foreign key cascade (delete goal → delete milestones/tasks/attachments)
- Attachment file storage and retrieval
- Backup creation and restoration
- Migration validation checks

### 5.2 Integration Tests

- Import JSON backup → verify all entities migrated
- Large dataset (1000+ goals, 10000+ tasks)
- Attachment integrity (checksum verification)
- Sync envelope compatibility (encrypt on web, decrypt on desktop)

### 5.3 Migration Tests

- Empty workspace migration
- Single goal with milestones and tasks
- Goal with attachments
- Goal with finance plan
- Recurring goals
- Archived and deleted goals
- Category with custom colors
- Activity log entries

## 6. Implementation Order

1. Create Database.java (connection management)
2. Create Schema.java (DDL)
3. Create SqliteRepository.java (CRUD)
4. Create AttachmentStore.java (file storage)
5. Create BackupManager.java (automatic backups)
6. Create MigrationValidator.java (validation)
7. Create JsonImporter.java (JSON import)
8. Update build scripts
9. Write tests
10. Test migration with sample data

---

# 7. As-Built Implementation (Phase 2 final)

## 7.1 Changed files

| File | Change |
|---|---|
| `services/api/src/main/java/dev/momentum/data/Database.java` | NEW — SQLite connection manager (WAL, foreign_keys ON, manual commit). |
| `services/api/src/main/java/dev/momentum/data/Schema.java` | NEW — DDL, `schema_version` in `db_meta`, indexes. |
| `services/api/src/main/java/dev/momentum/data/SqliteRepository.java` | NEW — JDBC data-access layer matching `apps/web/src/state/types.ts` 1:1 (recurrence/finance stored as JSON columns). |
| `services/api/src/main/java/dev/momentum/data/AttachmentStore.java` | NEW — attachments as files under `attachments/{id}/{name}`, relative paths only (directory stays copyable), SHA-256 helper, data-URL decoder. |
| `services/api/src/main/java/dev/momentum/data/BackupManager.java` | NEW — timestamped backups in `backups/`, retention keeps the newest 5. |
| `services/api/src/main/java/dev/momentum/data/MigrationValidator.java` | NEW — SQLite integrity, row-count comparison, foreign-key check, orphan scan, attachment file/size verification. |
| `services/api/src/main/java/dev/momentum/data/JsonImporter.java` | NEW — imports the web backup (`app=momentum, version=2`) into a **temporary** DB, validates, then promotes. FK-safe insertion order (categories → goals → milestones → tasks in parent-first passes → activities → attachments). Subtasks referencing parents that don't exist in the backup are imported as top-level tasks (data preserved, no crash). |
| `services/api/src/main/java/dev/momentum/data/MigrationManager.java` | NEW — shared facade for the HTTP service and the JavaFX app: `status()`, `importBackupJson()`, `ensureDailyBackup()`. |
| `services/api/src/main/java/dev/momentum/MomentumServer.java` | Routes `GET /api/db/status`, `POST /api/db/import` (up to 180 MB body, mirroring the web 150 MB export cap); daily backup scheduled on startup. |
| `services/api/scripts/build.ps1`, `run.ps1`, `test.ps1`, `scripts/start.ps1`, `scripts/momentum.sh`, `apps/launcher/Program.cs` | Classpath wiring for `sqlite-jdbc.jar` + `slf4j-api.jar`; both are auto-downloaded from Maven Central when missing and are gitignored. |
| `.gitignore` | Ignores the two downloaded jars. |
| `TUTORIAL.md` | "Bringing your browser (IndexedDB) data to the desktop database" section. |
| `services/api/src/test/java/dev/momentum/DataMigrationTest.java` | NEW — 4 migration tests (below). |
| `services/api/src/test/java/dev/momentum/TestRunner.java` | Registers the 4 new tests. |

**Encrypted sync:** `auth/OAuthService`, `sync/SyncStore`, AES-256-GCM and PBKDF2
parameters are untouched — all sync tests still pass unchanged.

**React app:** not modified (`apps/web` diff is empty for this phase).

## 7.2 SQLite schema (final)

One deviation from the original draft, required by the real web data model:

- `goals.status` values are `active | completed` (default `active`), `goals.kind`
  values are `one-time | recurring` (default `one-time`).
- `activities.message` (not `description`).
- `categories.deleted_at` added.
- `occurrence` stored as INTEGER; `recurrence_json` / `finance_json` store the exact
  JSON from the web backup so desktop round-trips stay byte-identical.
- `db_meta` holds: `schema_version`, `migrated_from` (`web-indexeddb-v2`),
  `migration_date`, `source_exported_at`, `last_backup_date`,
  `attachment_checksums` (SHA-256 audit of every imported attachment).
- `settings` holds the workspace metadata + the web `AppSettings` snapshot as JSON.

## 7.3 Migration strategy (as built)

Staged, validate-then-promote:

1. `data/migration-import-tmp/` gets a fresh SQLite DB + attachments directory.
2. The web backup JSON is fully imported and committed in batches of 500.
3. SHA-256 of every attachment file is recorded in `db_meta.attachment_checksums`.
4. `MigrationValidator` must pass: `PRAGMA integrity_check` = ok, zero foreign-key
   violations, zero orphaned children, every table count equals the backup count,
   every attachment file exists with matching size.
5. Only then: pre-migration backup of the existing `momentum.db` → atomic file swap →
   attachment files copied into `data/attachments/`.

Failure anywhere inside steps 1–4 deletes **only** the temporary directory. The live
`momentum.db`, existing attachment files, the browser's IndexedDB and the user's
backup JSON file are never modified or deleted.

## 7.4 Test results

All 12 API tests pass (`npm run test:api`):

| Test | Scope |
|---|---|
| 8 pre-existing tests (json, password, rate limiter, accounts, oauth, sync ×2) | Regression — all green |
| `backup import round trips goals, tasks and attachments` | **600 goals** / 1,200 milestones / **3,601 tasks** (incl. subtask links) / 40 categories / 1,200 activities / 15 attachments (~10–100 KB each). Verifies: counts, integrity, FK, attachment SHA-256 checksum audit, recurrence + finance JSON preserved verbatim, subtask parent links, detached-subtask fallback, settings snapshot. |
| `failed import rolls back and keeps original data intact` | Second import with a corrupt attachment payload → fails with a clear reason; **the old DB bytes are byte-identical**, counts unchanged, no temp artifacts left, original attachment files intact. |
| `attachment size mismatch aborts migration` | Metadata size ≠ payload size → rejected, **no database file is even created**. |
| `database backups keep the newest five` | 7 backups → 5 retained. |

Separate HTTP end-to-end run against a live server (sandboxed data dir, deleted
afterwards): first import `ok:true`; second import produced a pre-migration backup
(`data/backups/momentum-{timestamp}.db`); daily startup backup observed; corrupt
import over HTTP returned HTTP 400 with the validation reason and left
`/api/db/status` unchanged.

## 7.5 How existing users import their data

React stays untouched; the web app's existing **Settings → Export backup** produces
the input file, and the desktop service imports it via `POST /api/db/import`
(documented in TUTORIAL.md). During staged testing, imports were done against
**copied synthetic data in temporary directories, never the real user database**.

## 7.6 Remaining issues / notes

- Import replaces the local desktop database (after a mandatory backup) — it is a
  mirror, not a merge. A merge/pill-over mode can be added later if two devices are
  edited independently.
- A 150 MB web backup means a large HTTP body; fine for a localhost-only service.
- The JavaFX app (Phase 3+) will call `MigrationManager` directly instead of HTTP.
- Orphaned attachment files from replaced imports are not garbage-collected yet.

