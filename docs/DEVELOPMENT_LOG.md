# MOMENTUM Development Log

## 2026-11-05 — Groq + runnable Windows app

- Configured the supplied Groq key as a **server-side secret** in the ignored `.env`; it is not present in frontend source, web assets, docs or source control.
- Locked this build to Groq at both layers:
  - production frontend build flag `VITE_AI_PROVIDER_LOCK=groq` hides other providers and defaults to `openai/gpt-oss-120b`;
  - server `MOMENTUM_AI_PROVIDERS=groq` rejects other providers with HTTP 400.
- Updated the AI defaults from the retired `llama-3.3-70b-versatile` model to the currently available Groq `openai/gpt-oss-120b` model.
- Added `MOMENTUM_ALLOW_LOCAL_AI_KEY=true` for local desktop use so loopback AI requests can use the server-managed key without an account. This is explicitly disabled for remote deployment.
- Reworked the Windows shell to be standalone:
  - `backend.cjs` auto-starts the packaged Java service on `127.0.0.1:8799`;
  - the UI loads from that local same-origin server, preserving cookies, AI and sync behavior;
  - packaged resources include `web/`, `api/momentum-api.jar` and a generated `runtime.env`;
  - data lives under `%APPDATA%\MOMENTUM`;
  - an offline fallback window opens through `momentum://app/` if Java is unavailable.
- Added `npm run desktop:build`, `npm run desktop:run`, `Launch MOMENTUM.bat` and `MOMENTUM.lnk`.
- Added server-side response-size caps, provider allowlist and loopback-key support.
- Updated screenshots and docs for the Groq-only build.

### Verification

- Groq API key validated against the live Groq model list and chat endpoint.
- Real Groq plan generated through the UI: 4 milestones / 13 tasks imported successfully.
- Full E2E suite re-run with real Groq: onboarding, AI test, plan import, PDF, archive/trash, auth/session, encrypted sync, mobile layout, all key checks passing.
- The packaged Windows resources (jar + web + runtime.env) were started on an isolated port via the same backend module Electron uses:
  - health OK;
  - `groq / openai/gpt-oss-120b` request returned `DESKTOP GROQ READY`;
  - an OpenAI request was rejected by the provider lock;
  - clean shutdown.
- Electron window creation still cannot be tested in this headless host session; the PWA UI and packaged backend were tested separately.

### Security note

The packaged app is turnkey and therefore necessarily contains a locally extractable Groq key in `resources/runtime.env`. It must not be distributed as-is. Rotate the key or require per-user keys for a public/distributed build.

## 2026-11-04 → 2026-11-05 — v1.0 build

### Phase 1 — Foundation

- Inspected an otherwise empty workspace containing three brand-reference JPGs. No prior application code was discarded.
- Selected a local-first architecture with a React/TypeScript client, a Java 21 service, IndexedDB persistence, and PWA/Electron/Capacitor delivery.
- Established a monorepo with `apps/web`, `apps/desktop`, `apps/mobile`, `services/api`, `docs/` and verification scripts.
- Added architecture, data, security, API and platform documentation before implementation drift could erase decisions.
- Built the web client with Vite 7, React 19, TypeScript, self-hosted variable fonts, Lucide icons and a handcrafted CSS design system.

### Phase 2 — Core application

- Implemented the app shell: fixed command sidebar, contextual top bar, mobile bottom navigation and responsive layout.
- Implemented local workspace persistence in IndexedDB with atomic document writes, a serialized write queue, settings separation, attachment stores and a secure vault store.
- Implemented goals, tasks, subtasks, milestones, categories, notes, activity, optional financial tracking, archiving, Recently Deleted, 30-day purge and restore.
- Derived progress and momentum locally; added dashboard, goal cards, next-move queue and deadline view.
- Implemented recurrence: completing a recurring goal creates the next occurrence with new IDs, shifted dates, reset completion state and series history.
- Added duplicate-title safeguards for manual and AI plans, and duplicate-category rejection.
- Added global command search/filtering/sorting with ranked results across goals, tasks, subtasks, milestones, notes, categories and attachment names.

### Phase 3 — AI

- Built a provider-neutral AI proxy in Java supporting OpenAI-compatible providers, Gemini and Ollama wire formats.
- Built provider settings with encrypted user credentials, model/base URL controls, connection test and consent tracking.
- Built the Goal Architect interview: current reality, desired outcome, time budget, target date and constraints, followed by an editable plan preview.
- Added AI output validation/normalization so malformed or empty model output cannot corrupt local data.
- Added offline/error fallbacks and a manual drafting path.
- Enforced provider host allowlists and custom-provider opt-in to reduce SSRF risk.
- Added server-side env-key fallback only for authenticated users.

### Phase 4 — Advanced features

- Implemented Recently Deleted with 30-day purge and a tested browser-clock manipulation.
- Implemented PDF export using jsPDF with branded layout, progress, milestones, tasks, deadlines and notes.
- Implemented dark/light/system themes, accent presets/custom HEX, density and reduced-motion preferences.
- Implemented full JSON backup including attachment data, and restore with confirmation.
- Implemented encrypted snapshot sync: AES-GCM/PBKDF2 client-side, server opaque storage, revision conflicts, merge by `updatedAt`.

### Phase 5 — Auth and platforms

- Implemented email/password accounts with PBKDF2 hashing, hashed session tokens, secure cookies and rate limits.
- Implemented OAuth 2.0 + PKCE for Google, Apple, GitHub and Discord, including Apple `id_token` signature verification and verified-email-only account linking.
- Added installable PWA manifest/service worker and branded icons.
- Generated Android and iOS Capacitor projects with branded icons/splash.
- Added an Electron Windows shell, custom secure protocol, CSP, packaging config and single-instance handling.
- Fixed a security issue where GitHub public email could be linked without checking the verified-email endpoint.
- Made OAuth default redirect target the public app URL rather than a hard-coded Vite port.

### Phase 6 — QA

- 26 Vitest unit tests passing: dates, progress, search/filter, mutations/recurrence, crypto sync, validation.
- 8 Java tests passing: JSON, PBKDF2, rate limiting, accounts/sessions, OAuth linking, sync revisions.
- Playwright production-build suite passing: onboarding, goal/task/milestone creation, persistence reload, global search, AI settings, provider test, AI plan preview/import, categories, PDF download, archive/restore, trash/restore, theme switch, registration/login, encrypted sync and mobile viewport.
- Offline suite: service worker controls page; app shell loads offline; goal creation and persistence verified over two offline reloads.
- Two-device encrypted sync suite: device A upload, device B receives/decrypts, device B adds task, device A merges it.
- Backup import suite: exported backup with attachment restored in a fresh browser profile.
- 30-day purge: seeded a 31-day-old deleted timestamp and verified removal.
- Recurring flow: verified next occurrence date, occurrence count, task reset and series link.
- PDFs rendered successfully in Chromium's PDF viewer (`%PDF` header; 2-page plan export).

### Phase 7 — Final polish

- Readability pass to raise micro-typography and improve light-theme accent contrast.
- Brand mark redesigned to a rising M/arrow and applied to web, PWA, Android and iOS assets.
- Added skip links, focus-visible treatment, keyboard search navigation, focus trapping, dialog escape handling, labelled icon buttons and reduced-motion support.
- Added gzip compression for compressible static assets and stricter SPA fallback handling.
- Added explicit empty states, storage-error surface, request error states and destructive confirmations.

## Decisions

| Decision | Rationale |
| --- | --- |
| IndexedDB document workspace, not localStorage | Durable, transactional, large enough for attachments, offline-first |
| Client-side encrypted sync, not server plaintext | Server cannot read goals; explicit user control |
| Password/recovery trade-off | Lost sync passphrase is unrecoverable; this is required for privacy |
| Java built-in HTTP server | Mandatory Java core with no unavailable Maven/Gradle dependency; auditable and easy to run |
| AI proxy in Java | Keeps provider keys out of frontend code and centralizes SSRF limits |
| PWA + Electron + Capacitor | Honest reuse of one tested UI while respecting platform packaging |
| No seeded goals or fake data | First-run experience is real; empty states guide instead of pretending |

## Known issues / remaining work

1. **Electron launch not verified.** The GUI runtime cannot start in the headless automation session (GUI apps exit immediately). An unpacked Windows build was produced successfully, but the installer build hit a host file lock and the window was not launched. The PWA path is verified. Test on an interactive Windows desktop before shipping the Electron build.
2. **Android/iOS not compiled.** SDK/Xcode unavailable. Generated projects and assets need device testing, especially downloads and auth cookie behavior.
3. **OAuth exchange not exercised with real credentials.** Authorization URL generation, state/PKCE implementation and Apple verification are present; provider apps must be configured and tested.
4. **Local model plan latency can exceed cloud expectations.** Ollama requests allow up to 360 s; UX shows a cancel option. Use a smaller model or cloud provider for faster plans.
5. **Attachments excluded from encrypted sync.** Documented; JSON backup includes them.
6. **No password reset/email verification/MFA.**
7. **No multi-tab live merge.** Workspace saves are last-write-wins per document in multiple tabs.
8. **No native push notifications.**
9. **No accessibility audit with a screen reader** was possible; semantic labels, focus states and keyboard paths were implemented and spot-checked.
10. **No custom app icons for every Android adaptive density?** Icons and splashes were generated; verify on device.

## Next engineering steps

1. Run the Electron shell and native projects on interactive Windows, Android and iOS devices; add CI matrices for platform builds.
2. Add OAuth integration tests with test provider tenants.
3. Add a screen-reader/axe audit and high-contrast theme verification.
4. Add shared cross-tab workspace locking or a revision channel.
5. Add optional attachment encryption and sync.
6. Add password reset/email verification and MFA.
7. Improve local model streaming so long plans show partial progress.
8. Add automated screenshot regression coverage for key pages.
