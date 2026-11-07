# MOMENTUM Testing

## 1. Automated suites

### Web unit / integration (Vitest)

```powershell
npm run test:web
```

Result at delivery: **26 passed / 26**, 6 files.

| Suite | Coverage |
| --- | --- |
| `dates.test.ts` | Local-date formatting, month clamping, week start, overdue/deadline labels, day math |
| `progress.test.ts` | Weighted task+milestone progress, task-only/milestone-only, next task, weekly tasks, momentum bounds |
| `search.test.ts` | Cross-entity search ranking, note/task/subtask/category/attachment hits, filters, sorting, archived/deleted exclusion |
| `mutations.test.ts` | Goal creation, task/subtask CRUD, milestone ordering, recurring next occurrence, series IDs, date shift, duplicate detection |
| `sync.test.ts` | AES-GCM round trip, ciphertext does not contain plaintext, wrong passphrase, damaged envelope, last-write-wins merge, tombstones |
| `validation.test.ts` | Goal draft rules, auth validation, hex colors, untrusted AI output normalization, fenced/embedded JSON parsing |

Configuration: `apps/web/vite.config.ts` (Vitest uses jsdom), `apps/web/src/test/setup.ts`.

### Java service (custom zero-dependency runner)

```powershell
npm run test:api
```

The test script compiles classes without rebuilding the jar, so it can run while the production service is live.

Result at delivery: **8 passed / 8**.

| Test | Coverage |
| --- | --- |
| JSON | Round trip, escaping/unicode, malformed rejection |
| Password hashing | PBKDF2 verify/reject |
| Rate limiter | Window enforcement, key isolation |
| Accounts | Register/login/session/logout, duplicate email, bad password status, persistence across reopen, delete |
| OAuth accounts | Identity linking, verified email linking, unverified refusal |
| Sync store | Revision increments, stale revision conflict, deletion |
| Sync validation | Malformed envelope/path traversal rejection |

Source: `services/api/src/test/java/dev/momentum/TestRunner.java`.

## 2. Browser end-to-end suites

These use Playwright with the installed Chrome executable, against the production web build served by the Java API at `http://127.0.0.1:8787`. They live in `scripts/` and create their own throwaway browser profiles.

### Prerequisites

1. Build web and API.
2. Ensure `.env` contains the Groq key and the local provider lock:
   ```text
   MOMENTUM_AI_PROVIDERS=groq
   MOMENTUM_ALLOW_LOCAL_AI_KEY=true
   GROQ_API_KEY=...
   ```
3. Start the Java service with test-specific data if you do not want test accounts in your real `data/`:
   ```powershell
   $env:MOMENTUM_DATA_DIR = '.e2e-data'
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1 -NoBrowser
   ```
4. Ensure port 8787 is not occupied by another app.

The full suite uses the **real Groq API** through the local server-managed key. AI requests are small (one plan) and subject to Groq rate limits.

### Suites and verified results

Run individually:

```powershell
node scripts/e2e-full.mjs
node scripts/e2e-offline.mjs
node scripts/e2e-advanced.mjs
node scripts/e2e-backup-import.mjs   # run after e2e-advanced (uses its backup)
node scripts/e2e-sync.mjs
node scripts/e2e-colors.mjs          # dual color pickers + OAuth guidance banner (npm run test:colors)
node scripts/e2e-groq.mjs             # focused real-Groq plan test
node scripts/e2e-desktop.mjs          # requires an interactive Electron session
node scripts/test-desktop-backend.cjs dev
node scripts/test-desktop-backend.cjs packaged   # after packaging
```

Verified outputs:

- **e2e-full**
  - `ONBOARDING_CREATED_GOAL` — full five-step onboarding created a real goal.
  - `AI_TEST Provider responded` — real Groq connection test through the local service key.
  - `AI_PLAN_GENERATED` / `AI_PLAN_IMPORTED` — real Groq `openai/gpt-oss-120b` plan generated, previewed, edited in the UI and imported as a goal.
  - `CATEGORY_CREATED true`.
  - `PDF_EXPORTED 7346 %PDF-`; complex plan PDF separately rendered by Chromium (2 pages).
  - `ARCHIVED_VISIBLE true`, `ARCHIVE_EMPTY_AFTER_RESTORE true`.
  - `MOVED_TO_TRASH true true`, `TRASH_EMPTY_AFTER_RESTORE true`.
  - `LIGHT_THEME light`.
  - `ACCOUNT_REGISTERED`; `SYNC_COMPLETED`; server stored ciphertext only (`SYNC_CIPHERTEXT_ONLY true`).
  - `SESSION_PERSISTED true`; `LOGIN_AGAIN_OK`; `MOBILE_NAV_VISIBLE true`; `PLAN_GENERATED true`; no page errors.
- **e2e-offline**
  - Service worker supported, scope `/`, `controlled: true`.
  - Offline app shell loaded.
  - Goal created offline; `GOAL_PERSISTED_OFFLINE_RELOAD true`.
  - Manifest served with `application/manifest+json`.
  - No page errors.
- **e2e-advanced**
  - Recurring next occurrence created; `Occurrence 2` and deadline `Nov 17, 2026` verified; tasks reset.
  - Attachment upload, attachment-name search hit (`ATTACHMENT_SEARCHABLE true`) and backup inclusion verified.
  - `PURGED_AFTER_30_DAYS true` after seeding a 31-day-old `deletedAt`.
  - `MOBILE_OVERFLOW false` at 390×844.
- **e2e-backup-import**
  - `Backup restored with 1 attachment.`
  - Imported goal and attachment visible.
- **e2e-sync**
  - Device A registered and synced.
  - Device B started empty, signed in, decrypted and received the goal.
  - Device B added a task, synced; device A merged `Created on device B`.
  - Both devices had zero page errors.
- **e2e-groq**
  - `PROVIDER_PANEL` showed exactly `Groq openai/gpt-oss-120b Managed by service`.
  - A real Groq plan produced 4 milestones / 13 tasks, was editable, and imported successfully.
- **desktop backend — dev and packaged**
  - Started from both repository paths and the packaged `resources` directory on isolated ports.
  - `HEALTH true`.
  - `AI_STATUS 200 groq openai/gpt-oss-120b`; `AI_TEXT_EXPECTED true` (`DESKTOP GROQ READY`).
  - `PROVIDER_LOCK_STATUS 400` for an OpenAI request.
- **e2e-desktop**
  - Blocked by the environment: GUI processes cannot start (Notepad also exits immediately). Electron downloaded correctly and an unpacked Windows package was produced at `apps/desktop/release/win-unpacked/MOMENTUM.exe`, but Electron did not create a window. This is recorded as a verification limitation, not a pass.
  - Full NSIS/portable installer generation failed at a host `EBUSY` lock while rewriting executable resources.

### Packaged Windows verification

The actual packaged resources were tested without using the Electron GUI: `resources/api/momentum-api.jar`, `resources/web`, and `resources/runtime.env` were passed to the same `backend.cjs` module that Electron uses. The auto-started service loaded the Groq key, accepted a loopback AI request, and enforced the Groq-only provider lock. This verifies the packaged runtime and key path; only Chromium window creation on an interactive desktop remains unverified.

### PDF verification

- The exported PDF starts with `%PDF-` and downloads successfully.
- A goal without tasks produced a clean one-page record.
- A generated plan with milestones/tasks produced a two-page PDF.
- Both were rendered in Chromium's PDF viewer and inspected for layout and legibility.

## 3. Manual checks performed

- Desktop layout at 1440×1000 across dashboard, goals, goal detail, planner, settings, auth.
- Mobile viewport 390×844 across dashboard, goals, goal dialog; no horizontal overflow.
- Light and dark themes; accent color update; custom HEX validation.
- Keyboard: `Ctrl/Cmd+K` search, arrow/Enter navigation, Escape closing, `N` new goal when not typing.
- Empty states for zero goals, zero categories, archive, trash, search, notes and no AI provider.
- Data persistence after page reload.
- Backup file inspection: contains attachments, no API-key ciphertext.
- Server ciphertext inspection: no goal titles/notes in sync file.

## 4. What was not tested

- OAuth authorization/token exchange against real Google/Apple/GitHub/Discord apps (no credentials).
- Native Electron launch (headless/host limitation).
- Android emulator/device build (no Android SDK).
- iOS simulator/device build (no macOS/Xcode).
- Screen-reader audit and full WCAG conformance.
- Cross-browser Safari/Firefox manual pass.
- High-load/performance testing; this is a single-user local-first product.

These exclusions are explicit in [Platform guide](PLATFORMS.md), [Development log](DEVELOPMENT_LOG.md), and the README's known limitations.

## 5. Screenshot inventory

Captured verification images are stored in `screenshots/` during test runs (ignored by git). Curated product images are under `docs/screenshots/`.

- Onboarding
- Offline dashboard
- Goal detail and plan
- AI plan preview
- Groq-only AI settings
- Account/encrypted sync
- PDF record and plan
- Mobile viewport
- Light theme
