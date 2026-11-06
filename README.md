# MOMENTUM

**A local-first goal command center.** Turn an ambition into a structured, editable plan and keep the next move visible.

![MOMENTUM overview](docs/screenshots/dashboard-light.png)

MOMENTUM is a complete, working application — not a mockup. Goals, tasks, milestones, categories, recurring series, notes, attachments, settings and deleted-item recovery are stored locally in IndexedDB and survive restarts. AI planning is provider-neutral, account authentication is optional, and cloud sync is explicit encrypted backup rather than silent upload.

> 📖 **New here? Read [`TUTORIAL.md`](TUTORIAL.md) — what every file is for and how to launch the web, Windows, Android and iOS versions.**

## 🚀 How to open MOMENTUM on each device

| Device | What to open | What happens |
| --- | --- | --- |
| **Windows (app window)** | Double-click the **`MOMENTUM`** desktop shortcut, or **`Launch MOMENTUM.bat`** or **`MOMENTUM.exe`** in this folder | Starts the local service quietly, then opens the app as its own window (no browser tab) |
| **Windows (browser tab)** | Double-click **`MOMENTUM Website.bat`** | Starts the service, opens `http://127.0.0.1:8787` in your normal browser |
| **Web (any computer)** | Open `http://127.0.0.1:8787` in a browser whenever the service is running | Same full app as a browser tab |
| **Linux / macOS** | In a terminal: `./scripts/momentum.sh start` | Builds if needed, starts the service, opens the browser. Use `stop`, `status` and `doctor` the same way |
| **Android phone** | Copy `MOMENTUM-android.apk` from this folder to the phone, tap it, allow "install unknown apps", open MOMENTUM | The full app bundled natively; goals work fully offline on the phone |
| **iPhone / iPad** | Open the site in Safari → Share → **"Add to Home Screen"** | ICON on the home screen; opens fullscreen like a native app (like Android, AI/sync need access to the service) |
| **Browser "install" (Chrome/Edge)** | Click the install icon in the address bar | App-like window, same as the Windows app experience |

### Keeping it happy (plain commands, from this folder in a terminal)

```
npm run doctor      → check everything is healthy (recommended after changes)
npm run restart     → stop and relaunch the service
npm stop	           → stop the background service
```

If something misbehaves: run `npm run doctor` — it names the problem and the command to fix it. Crash logs: `%TEMP%\momentum\`.


## What is included

| Area | Status |
| --- | --- |
| Manual goal creation, editing, archiving, restore, deletion | Working |
| Tasks, subtasks, milestones, priorities, due dates, notes | Working |
| Live progress and momentum signals | Working |
| Categories | Working |
| Recurring goals with next-occurrence generation | Working |
| Recently Deleted with 30-day recovery window | Working |
| Global search across goals, tasks, subtasks, notes, milestones, categories and attachment names | Working |
| Filtering and sorting | Working |
| AI Goal Architect interview, editable plan preview, provider settings | Working |
| AI provider | Groq is the provider configured for this build; the Java proxy architecture still supports DeepSeek, OpenAI, Gemini, Ollama and allowlisted OpenAI-compatible providers | Working |
| PDF export for individual goals | Working |
| Offline-first PWA with installable manifest and service worker | Working |
| Dark / light themes, accent presets, custom HEX accent | Working |
| Interactive onboarding with skip | Working |
| Email/password authentication | Working |
| Google / Apple / GitHub / Discord OAuth | Implemented; needs provider credentials |
| Explicit encrypted device sync | Working |
| Attachment storage | Working (local; included in JSON backup, excluded from cloud sync) |
| Windows desktop app | Runs locally with no internet: three launchers — the `MOMENTUM` desktop shortcut / `MOMENTUM.exe`, and `Launch MOMENTUM.bat` — all start the local Java service and open a standalone app window; `MOMENTUM Website.bat` opens it as a browser tab. The packaged Electron build is present but its GUI cannot start on this Windows build (see Known limitations) |
| Android / iOS apps | Same web app bundled natively via Capacitor. Signed debug APK at `MOMENTUM-android.apk`; the iOS Xcode project is generated and synced at `apps/mobile/ios` and builds on macOS |

## Architecture at a glance

```text
React + TypeScript web client (Vite)
  ├── IndexedDB local workspace (offline-first, source of truth)
  ├── Domain/validation/export/AI client
  └── Optional /api requests
             │
Java 21 service (no external Java dependencies)
  ├── email/password accounts + opaque sessions
  ├── OAuth 2.0 + PKCE (Google, Apple, GitHub, Discord)
  ├── provider-neutral AI proxy (keys are not exposed to cloud SDKs)
  ├── opaque encrypted sync envelope storage
  └── serves the production web build
             │
     Optional AI providers (cloud APIs or local Ollama)
```

- **Java is a required core service.** It owns accounts, OAuth, AI proxying, encrypted sync storage and the production web host.
- **Goal data is separate from authentication.** Signing in does not upload goals. Goals live locally until the user explicitly enables encrypted sync.
- **The web client is the same product on every platform.** Windows uses the installable PWA plus an Electron shell; Android and iOS are Capacitor shells around the same built web client. The Java API can be shared by all clients. See [Platform guide](docs/PLATFORMS.md).

More detail: [Architecture](docs/ARCHITECTURE.md) · [Data model](docs/DATA_MODEL.md) · [Security](docs/SECURITY.md) · [API](docs/API.md).

### Screens

| Goal detail | Goal Architect |
| --- | --- |
| ![Goal detail with milestones and tasks](docs/screenshots/goal-detail.png) | ![AI-assisted editable plan](docs/screenshots/planner.png) |

| Encrypted account sync | Mobile |
| --- | --- |
| ![Account and encrypted sync](docs/screenshots/account-sync.png) | ![Mobile dashboard](docs/screenshots/mobile-dashboard.png) |

![Groq-only AI settings](docs/screenshots/settings-ai.png)

## Quick start

### Prerequisites

- Node.js 22 or newer (Node 24 recommended)
- Java 21 or newer (`java` and `javac` on `PATH`)
- Optional: OAuth credentials for Google, Apple, GitHub or Discord

### Run the app

```powershell
npm install
npm run build
npm start
```

`npm start` builds any missing artifacts, opens `http://127.0.0.1:8787`, and keeps the Java service in the foreground. Press `Ctrl+C` to stop it.

Everything runs from this computer: the Java service, web bundle, fonts and icons are local, so MOMENTUM launches and works with **no internet connection**. Only AI replies (Groq) and optional OAuth sign-in require one. The production build also registers a service worker, so an already-opened PWA tab reloads even if the local service is stopped.

For a one-click browser launch on Windows, double-click `MOMENTUM Website.bat`; for a standalone window, use `Launch MOMENTUM.bat`, the `MOMENTUM` desktop shortcut or `MOMENTUM.exe`. On Linux/macOS, run `./scripts/momentum.sh start`.

For a clean rebuild:

```powershell
npm run start:build
```

### Development

Use two terminals:

```powershell
# terminal 1 — Java API and static server
npm run api

# terminal 2 — Vite dev server (proxies /api to the Java service)
npm run dev
```

Open `http://127.0.0.1:5173`. The PWA service worker is only registered in a production build.

### Windows desktop app

The packaged Windows app is standalone: it starts its own Java service on `127.0.0.1:8799`, stores desktop data under `%APPDATA%\MOMENTUM`, and loads the same local-first interface. It uses the Groq key from the packaged local-service configuration; the key is not present in frontend code.

Launch it by double-clicking:

- `MOMENTUM.lnk`
- `Launch MOMENTUM.bat`
- or `apps\desktop\release\win-unpacked\MOMENTUM.exe`

Rebuild the Windows app after changing source:

```powershell
npm run desktop:build
```

This rebuilds the web client and Java service, copies the packaged runtime configuration, and produces the unpacked app. Use `npm run desktop:run` to build if needed and launch it.

**Do not redistribute this packaged build with your embedded Groq key.** Anyone with the `.exe` can extract the local runtime configuration. For distribution, remove the embedded key and require each user to supply their own.

### Groq AI

The delivered build is locked to Groq and uses `openai/gpt-oss-120b`. The local Java service reads `GROQ_API_KEY` from the environment. For normal `npm start` usage, put it in `.env`; the desktop package reads it from its packaged runtime configuration.

- AI works on the local machine without signing in because loopback requests are allowed to use the server-side key (`MOMENTUM_ALLOW_LOCAL_AI_KEY=true`).
- Sign-in is still required for cloud-side server-key usage on a remote deployment.
- Settings still allows an optional per-device key override.
- Manual goal creation remains available if Groq is unreachable.

The provider architecture in the Java proxy still supports other providers, but this build hides them and the server rejects them so there is no ambiguity about which key is in use.

## Authentication

Email/password registration and login work out of the box. Passwords are hashed with PBKDF2-HMAC-SHA256 (210,000 iterations, per-user salt); sessions are opaque HttpOnly cookies stored as SHA-256 hashes server-side.

OAuth is enabled per provider when both credential variables are present. The redirect URI is:

```text
${MOMENTUM_PUBLIC_URL}/api/auth/oauth/<provider>/callback
```

Supported providers: `google`, `apple`, `github`, `discord`. See [Security](docs/SECURITY.md#oauth) for the Apple client-secret JWT requirement and provider-specific setup. OAuth exchange flows require real provider credentials and were not exercised in this environment; the implementation includes state validation, PKCE and Apple `id_token` signature verification.

## Encrypted sync

Sync is deliberately not automatic.

1. Sign in.
2. Go to **Account → Encrypted sync**.
3. Choose a passphrase of at least 10 characters. Use the same passphrase on every device.
4. Press **Enable & sync now**.

The browser encrypts the workspace with AES-256-GCM; the key is derived from the passphrase with PBKDF2-SHA-256 (310,000 iterations, random salt). Only the ciphertext envelope is uploaded. The server cannot read goal titles, tasks, notes or categories. A second device with the same account and passphrase decrypts, merges by per-item `updatedAt`, and uploads the merged envelope.

**The passphrase cannot be recovered.** If it is lost, the cloud copy is unreadable; the local workspace is unaffected. Attachments are not synced in this version — use the JSON backup for full-fidelity local transfer.

## Data ownership

- Local data lives in IndexedDB database `momentum-local`, document stores plus an attachments store and a non-exportable device key store.
- API keys are encrypted with a non-extractable AES-GCM key before being stored in IndexedDB.
- JSON backup includes goals, categories, tasks, milestones, notes, preferences and attachments. It never includes provider API keys or the sync passphrase.
- **Export JSON** is available in **Settings → Data & privacy**.
- **Erase all local data** requires typing `ERASE` and does not delete an account or encrypted cloud backup; those have separate controls.
- Deleted goals enter **Recently Deleted** for 30 days and are purged automatically after that window. Tasks and subtasks are removed immediately, with confirmation.

## Export

Each goal can be exported to a professional PDF containing its title, description, progress, deadlines, milestones, tasks, notes and optional financial tracking. The export is generated entirely in the browser; no goal content is sent anywhere.

## Tests and verification

```powershell
npm run test:web       # 26 unit tests: dates, progress, search, mutations, sync, validation
npm run test:api       # 8 Java tests: JSON, hashing, rate limiting, accounts, sync store
```

`npm run test:api` compiles test classes without rewriting the production jar, so it can run while the app is live.

Browser end-to-end suites in `scripts/` were run against the production build. See [Testing](docs/TESTING.md) for exact results and how to run them. Covered flows include onboarding, goal/task/milestone CRUD, **real Groq plan generation and import**, PDF download, archive/restore, trash/restore, 30-day purge, attachment search, theme switching, account registration, session persistence, encrypted two-device sync merge, backup import/export, offline reload and mutation, and mobile viewport layout.

The packaged Windows runtime was tested separately from the GUI: its embedded Java service started from `resources`, used the packaged Groq key, generated a Groq response, and rejected an unapproved provider.

## Project layout

```text
apps/
  web/       React + TypeScript + Vite client, tests, PWA assets
  desktop/   Electron Windows shell and packaging config
  mobile/    Capacitor Android/iOS shells
services/
  api/       Java 21 API, tests, build/run scripts
scripts/     start helper and Playwright integration suites
docs/        architecture, data, security, platform, API, testing, development log
```

## Configuration

Copy `.env.example` to `.env` for server configuration. `scripts/start.ps1` loads `.env` automatically; a direct `java -jar` run uses process environment variables. Never commit `.env`, OAuth secrets or API keys.

Key variables:

| Variable | Purpose |
| --- | --- |
| `MOMENTUM_PORT` | API/web port (default `8787`) |
| `MOMENTUM_HOST` | Bind address (default `127.0.0.1`) |
| `MOMENTUM_PUBLIC_URL` | Public origin used for OAuth callbacks |
| `MOMENTUM_APP_URL` | Frontend URL after OAuth (defaults to public URL) |
| `MOMENTUM_COOKIE_SECURE` | Set `true` behind HTTPS |
| `MOMENTUM_ALLOWED_ORIGINS` | Additional allowed browser origins, comma-separated |
| `MOMENTUM_DATA_DIR` | Server data directory (default `data`) |
| `MOMENTUM_AI_PROVIDERS` | Optional provider allowlist; the delivered local build uses `groq` |
| `MOMENTUM_ALLOW_LOCAL_AI_KEY` | Allow the local server key for loopback requests (local desktop mode) |
| `MOMENTUM_OLLAMA_URL` | Optional operator-managed Ollama endpoint |
| `MOMENTUM_ALLOW_CUSTOM_AI` | Enable custom OpenAI-compatible provider |
| `MOMENTUM_CUSTOM_AI_HOSTS` | Required host allowlist for custom providers |
| `DEEPSEEK_API_KEY` / `OPENAI_API_KEY` / `GEMINI_API_KEY` / `GROQ_API_KEY` | Server-side keys for authenticated users, or loopback requests when local-key mode is enabled |

Full list and OAuth variables: [.env.example](.env.example).

## Known limitations

- OAuth provider exchange flows were not tested end-to-end because no provider credentials were available. The code path, PKCE/state handling and Apple signature validation are implemented.
- Native Windows Electron window creation could not be verified in the headless automation session used to build the app. The packaged app's auto-start Java backend, web bundle, Groq key and provider lock were tested from the packaged resources. An unpacked app exists at `apps/desktop/release/win-unpacked/MOMENTUM.exe`; NSIS/portable installer generation hit a host file lock and should be retried on a normal build machine.
- The Windows package embeds the supplied Groq key in a local runtime configuration so it works turnkey. That file is readable by anyone who has the app. Do not distribute this build; rotate the key before sharing or rebuilding for others.
- Android: a signed debug APK is built at `MOMENTUM-android.apk` (package `com.momentum.app`, min SDK 24, target SDK 36). The web assets are bundled inside the APK, so the app opens and stores goals offline; hardware Back navigates within the app. AI and account sync still need the local service on a computer. Install it with `adb install MOMENTUM-android.apk` or by opening the file on the phone with unknown-source installs allowed.
- iOS: the Capacitor project at `apps/mobile/ios` is generated, configured (iOS 15+, light status bar, safe areas) and synced with the current web build, but Apple requires macOS + Xcode to compile/sign it. See the iOS section of `TUTORIAL.md` for the exact Mac steps. AI and account sync likewise need the local service.
- Cloud sync excludes attachments; the local JSON backup includes them.
- Multi-tab conflict resolution is last-write-wins at the workspace-document level; the sync envelope merge is per item.
- There is no password reset, email verification or MFA yet.

These are explicit product/engineering boundaries, not hidden placeholders. The development log records them alongside completed work: [Development log](docs/DEVELOPMENT_LOG.md).

## License

Released under the [MIT License](LICENSE). You are free to use, modify and distribute MOMENTUM — including for commercial purposes — with attribution.
