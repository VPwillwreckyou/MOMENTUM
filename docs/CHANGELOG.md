# MOMENTUM Changelog

## 2026-11-07 — Public deployment
- Repository published: **https://github.com/VPwillwreckyou/MOMENTUM** (17 topics, MIT license).
- Release `v1.0.0` published with `MOMENTUM-android.apk` (5 MB) attached as a downloadable asset (the APK intentionally stays out of git history).
- Commit identities rewritten to the GitHub noreply address before push (per GitHub privacy defaults).
- Post-publish secret leak scan (GitHub code search for the Groq key): **0 hits**.

---

## 2026-11-06 — Launch & offline release (parallel agents: Claude/Qoder)

### Windows launchers — all working
- `Launch MOMENTUM.bat` → starts the local Java service (hidden, with crash logs at `%TEMP%\momentum\`) and opens the app as a standalone Edge window (`--app` mode, dedicated profile).
- `MOMENTUM Website.bat` → same service, opens the site in your default browser.
- `MOMENTUM.exe` (compiled native launcher, built by Qoder) → finds Java, cold-starts the service, opens the app window; graceful failure reporting built in.
- Desktop + folder `MOMENTUM.lnk` shortcuts point at the verified path and carry the app icon.
- Electron `.exe` path remains broken on Windows 26H2 build 26300 (Electron 45 startup crash) — known environment limitation; Edge app-mode window is the delivered substitute. (`npm run desktop` retriable when a fixed Electron ships.)

### New tooling
- `npm run doctor` — 14-point health check (Java, service, web build + service worker, jar, Groq AI round-trip, config, launchers, APK↔web build hash sync) with clean exit codes.
- `npm stop` — stops the hidden service, including zombie jar processes.
- `npm run restart` — stop + relaunch.
- `npm run launch` — launcher with `-Browser` (website mode) support.

### Android
- `MOMENTUM-android.apk` built from the packaged Capacitor shell, hash-verified to embed the current web build (`npm --prefix apps/mobile run copy` + `gradlew assembleDebug`).
- Works fully offline for goals/tasks/notes/search/PDF/backup; AI and sync need the PC service.
- `docs/` and `TUTORIAL.md` document one-time setup and the install flow.

### Internet-free
- Site and Windows app launch and run with zero internet (verified: service worker offline shell, offline goal creation + persistence, manifest 200).
- Only AI replies (Groq), OAuth sign-in providers and device sync require a connection.

### Design polish
- New final stylesheet layer `apps/web/src/styles/polish.css`: ambient accent aura, frosted-surface highlights, gradient momentum score, animated progress ring + halo, glowing 14-day bars with grow-in animation, category-colored card spines, hover-lift cards, button press/hover micro-interactions, `prefers-reduced-motion` respected, global focus rings, styled scrollbars.

### Verification performed
- Unit tests: 26/26 web, 8/8 API. e2e: full flow, Groq AI planner, advanced features, offline, 2-device sync, backup/restore, desktop-backend — all green, zero console errors. (`e2e-desktop` Electron-launch test remains environment-failed, documented above.)
- Full boot-cycle test: `npm stop` → bat relaunch → doctor HEALTHY, app window auto-opened.
- Fresh screenshots for README/docs (`docs/screenshots/`, all 10 regenerated post-redesign).

### Linux / macOS
- New `scripts/momentum.sh`: start/stop/restart/status/build/doctor with `.env` loading, health-wait loop, browser open (`xdg-open`/`open`), logs at `${TMPDIR:-/tmp}/momentum/`. Syntax-verified and exercised against a live instance via Git-bash. Documented in `docs/PLATFORMS.md` §8 and `TUTORIAL.md`.

### Cross-platform feature sync
- New custom-color system (primary + secondary pickers, composite gradient) propagated to every shell: web dist rebuilt → `cap copy` synced Android and iOS projects (hash-verified both) → `MOMENTUM-android.apk` rebuilt and re-delivered at the repo root. Windows/Linux/mac serve the fresh dist directly. Doctor reports all-green including the APK↔web hash check.

### Known issues / follow-ups
- ~~`MOMENTUM.exe` opens its window under the repo-local `.app-profile`~~ **Resolved 2026-11-07:** exe rebuilt from source — now uses `%LOCALAPPDATA%\MOMENTUMAppProfile` (same profile as the bat launchers, shared login), runs without `--no-sandbox`, and focuses the existing window instead of spawning a duplicate (single-instance verified: same window token on relaunch, single java process). Gate result: cold start → service 200 + window opens; double-click again → focuses existing window.
- OAuth providers configured in `.env` await user-supplied credentials; buttons activate automatically per provider once both ID and secret exist. Apple additionally requires an HTTPS domain + Apple Developer Program.
- Two transient `401` console entries in `e2e-groq` are expected pre-login probes, not bugs.

---

## Historical
See `docs/DEVELOPMENT_LOG.md` for the original delivery phases.
