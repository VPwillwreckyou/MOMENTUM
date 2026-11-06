# MOMENTUM Data Model

## 1. Storage boundaries

MOMENTUM separates four kinds of data:

| Boundary | Where it lives | Encrypted? | Syncs? |
| --- | --- | --- | --- |
| Goals, tasks, notes, categories, activities | IndexedDB (`momentum-local` → `documents` → `workspace`) | At rest via OS/browser profile; app-level encryption only for sync | Only inside user-encrypted envelope |
| Attachment blobs + metadata | IndexedDB (`attachments`) | OS/browser profile; not app-level | No (included in JSON backup only) |
| AI API keys | IndexedDB (`vault` holds non-extractable device AES key; ciphertext in settings) | AES-GCM device key | Never |
| Account identity, password hash, sessions | Java `data/accounts.json` | Password PBKDF2; session token hashed | N/A |
| Encrypted sync envelope | Java `data/sync/<userId>.json` | AES-256-GCM, passphrase-derived key | Yes, ciphertext only |

The **authentication database and the goal workspace are deliberately independent**. An account can exist with zero goals; local goals can exist with no account.

## 2. Workspace graph

```text
Workspace
├── installationId
├── goals[]
│   ├── categoryId ───────────────► Category.id
│   ├── milestones[]
│   ├── tasks[]
│   │   ├── milestoneId ──────────► Milestone.id
│   │   └── parentTaskId ─────────► Task.id (one level)
│   ├── recurrence
│   └── finance (optional)
├── categories[]
└── activities[]
```

### Goal

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string (UUID) | Stable local identifier |
| `title`, `description`, `notes` | string | User text; validation limits |
| `categoryId` | string \| null | Category is optional |
| `status` | `active` \| `completed` | Completed means the occurrence is done |
| `kind` | `one-time` \| `recurring` | Recurring creates a new occurrence on completion |
| `recurrence` | `{frequency: daily\|weekly\|monthly, interval: 1..52}` | Present for recurring goals |
| `priority` | `low` \| `medium` \| `high` \| `critical` | Signal only |
| `startDate`, `deadline` | `YYYY-MM-DD` \| null | Calendar dates in local time |
| `createdAt`, `updatedAt` | ISO timestamp | Sync conflict key |
| `completedAt`, `archivedAt`, `deletedAt` | ISO timestamp \| null | State flags; deleted is a tombstone |
| `seriesId`, `occurrence` | string / number | Links recurring occurrences |
| `milestones[]`, `tasks[]` | arrays | Nested for a single-document workspace |
| `finance` | `{enabled, currency, targetAmount, currentAmount, note}` \| null | Opt-in, goal-local |
| `aiGenerated` | boolean | Provenance marker for imported AI plans |

### Milestone

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `title`, `description` | Outcome-oriented checkpoint |
| `dueDate` | Optional calendar date |
| `completed`, `completedAt` | Independent from task completion |
| `order` | Display order |

### Task

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `goalId` | Denormalized for fast lookup |
| `milestoneId` | Optional grouping |
| `parentTaskId` | Optional subtask parent (one level) |
| `title`, `notes` | User text |
| `completed`, `completedAt` | Completion history |
| `dueDate` | Optional |
| `priority` | Task-level priority |
| `order`, `createdAt`, `updatedAt` | Ordering/conflict metadata |

### Category

`id`, `name`, `color` (`#rrggbb`), `icon` (one of eight keys), timestamps, optional `deletedAt`.
Deleting a category soft-deletes it and detaches goals (`categoryId = null`); goals are never deleted with a category.

### Activity

`id`, `goalId`, `type`, `message`, `at`. Up to 350 records are retained in the workspace. Activity is local-only and included in encrypted sync and local backup.

### Attachment

Metadata:
`id`, `goalId`, `name`, `mimeType`, `size`, `createdAt`.

Blob storage: separate IndexedDB object store keyed by attachment `id`, indexed by `goalId`. Maximum 15 MB per file. Deleting a goal permanently (or purging it) removes its blobs. Deleting a task does not delete goal attachments.

## 3. Progress formula

For an active goal:

```text
taskPercent      = completedTasks / totalTasks * 100
milestonePercent = completedMilestones / totalMilestones * 100

if tasks and milestones:
  progress = round(taskPercent * 0.65 + milestonePercent * 0.35)
else if tasks:
  progress = taskPercent
else if milestones:
  progress = milestonePercent
else:
  progress = 0
```

A completed goal displays 100%. Progress is derived, never stored, so it cannot drift out of sync.

Momentum score (dashboard signal):

```text
progressAverage = mean(active goal progress)
velocity        = min(100, completedTasksLast7Days / max(6, availableItems * 0.25) * 100)
momentum        = round(progressAverage * 0.72 + velocity * 0.28)
```

It is bounded 0–100 and recomputed locally on each render.

## 4. Lifecycle rules

### Goal deletion and recovery

1. Trash: set `deletedAt = now`, clear `archivedAt`, keep `status`.
2. Recently Deleted shows days remaining (`deletedAt + 30 days`).
3. Restore: set `deletedAt = null`.
4. Permanent deletion: remove the goal, its activity and attachment blobs.
5. Automatic purge runs at startup and hourly; expired goals are removed with attachments and activity.

Tasks and subtasks are not routed through this recovery window. They are deleted immediately after confirmation.

### Archiving

- `archivedAt` removes a goal from active overview/goals but keeps it searchable.
- Restore clears `archivedAt`.
- Archiving and restoring are recorded in activity.

### Recurring series

When an active recurring goal is completed:

1. The current goal becomes `completed`.
2. A new goal is created with a new `id`.
3. `seriesId` is set to the original goal's id (or its existing series id).
4. `occurrence` increments.
5. The deadline advances by the recurrence interval until it is after today.
6. Milestones and tasks are cloned with new IDs; completion is reset; due dates shift by the same calendar delta as the deadline.
7. Milestones' child task references are remapped to the cloned milestone IDs.
8. Financial `currentAmount` resets to 0 for the new occurrence.

The original occurrence remains in the series history and is read-only with respect to reopening (to avoid two active occurrences). One-time completed goals can be reopened.

### Duplicate prevention

- Exact duplicate active goal titles trigger a confirmation before manual creation, AI-plan import or manual planner draft creation.
- Duplicate category names are rejected.
- The UI does not silently create goals from empty or duplicate submissions.

## 5. Settings model

`AppSettings` is a separate document:

```text
schemaVersion, themeMode, accent, density, reduceMotion, weekStartsOn,
displayName, onboardingComplete, syncEnabled, lastSyncAt,
ai: { provider, model, baseUrl, apiKeyCipher (encrypted), hasKey,
      consentAcceptedAt, lastTestAt, lastTestOk }
```

`themeMode` may be `dark`, `light` or `system`. Accent is a validated `#rrggbb`. Density is comfortable/compact; week start is Sunday/Monday.

API keys are encrypted with a non-extractable AES-GCM device key stored in IndexedDB's `vault` store. They are decrypted only when making a local AI proxy request. The JSON backup strips the ciphertext and marks `hasKey: false`.

## 6. Sync envelope

Client-side plaintext payload before encryption:

```json
{
  "format": "momentum-workspace",
  "version": 1,
  "workspace": { "...": "goals, categories, activities, ids, timestamps" }
}
```

Encryption:

- PBKDF2-HMAC-SHA-256, 310,000 iterations, random 16-byte salt.
- AES-256-GCM, random 12-byte IV, additional authenticated data `momentum-sync-v1`.
- Envelope version 1, algorithm and KDF identifiers included.
- Passphrase must be at least 10 characters; it is never persisted or uploaded.

Server record:

```json
{
  "envelope": "{...client ciphertext JSON...}",
  "revision": 3,
  "updatedAt": "ISO timestamp"
}
```

**Merge rule:** for each goal/category ID, the record with the later `updatedAt` wins (string ISO comparison is lexicographic-safe for UTC timestamps). IDs present only locally or remotely are retained. Activities are unioned by ID and sorted newest-first. This intentionally preserves tombstones (`deletedAt`) so deletions propagate before purge.

**Conflict behavior:** PUT requires `baseRevision`. A stale revision returns HTTP 409 with the current envelope; the client decrypts, merges and retries once. If the same item changed concurrently on two devices, the last `updatedAt` wins.

## 7. Local JSON backup format

Version 2:

```json
{
  "app": "momentum",
  "version": 2,
  "exportedAt": "ISO timestamp",
  "workspace": { "...": "complete workspace document" },
  "settings": { "...": "settings without API-key ciphertext" },
  "attachments": [
    {
      "meta": { "id": "...", "goalId": "...", "name": "...", "mimeType": "...", "size": 123, "createdAt": "..." },
      "dataUrl": "data:mime;base64,..."
    }
  ]
}
```

Import:
- Validates `app === "momentum"` and normalizes workspace/settings.
- Restores attachment blobs up to 15 MB each; invalid/oversized entries are skipped.
- Replaces the current workspace and clears old local attachments after user confirmation.
- Does not restore API keys or sync passphrases.

Version 1 backups (without `attachments`) remain importable.

## 8. IndexedDB layout

Database: `momentum-local`, version 1.

| Store | Key | Contents |
| --- | --- | --- |
| `documents` | `{key}` | `workspace`, `settings` |
| `attachments` | `id` (index `goalId`) | metadata + Blob |
| `vault` | `key` | `device-key` → non-extractable AES-GCM CryptoKey |

The app opens the database with upgrade handling and rejects blocked upgrades with a clear message. Database deletion is used only by explicit "Erase all local data".

## 9. Versioning and migration

- Workspace/settings have `schemaVersion: 1`.
- The repository normalizes loaded data to tolerate missing optional fields.
- Future migrations should add explicit version steps before normalization; do not overwrite unknown fields silently.
- Sync envelope version and KDF iterations are explicitly encoded to support future upgrades.
- JSON backup version 2 is backward-compatible with version 1 imports.
