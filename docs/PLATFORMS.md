# MOMENTUM Platform Guide

MOMENTUM uses a shared web client for all platforms. The platform shells must not be confused with the local-first data model: the web/PWA client is the tested product; Electron and Capacitor wrap the same built client.

## 1. Support matrix

| Platform | Delivery | What is verified here | What still requires a platform environment |
| --- | --- | --- | --- |
| Web | Any modern Chromium/Firefox/Safari browser | Full desktop browser flows, responsive layout, offline reload | Cross-browser manual visual pass (especially Safari) |
| Windows | Installable PWA + packaged Electron app | PWA offline, desktop layout, packaged embedded Java backend, Groq key, provider lock | Electron window launch/UX on an interactive desktop; NSIS/portable installer |
| Android | Capacitor 8 native project | Web asset sync, generated project, icons/splash, responsive UI | Gradle build/emulator/device test; needs Android SDK |
| iOS | Capacitor 8 native project | Native project generated, icons/splash | Xcode build/simulator/device test; needs macOS + Xcode |

The Java API and web client are shared. Authentication, AI and encrypted sync require network access to a running Java service; goals, planning, search, backup and exports work offline.

## 2. Web / PWA (verified)

Production build is served by the Java service at `/`, or by any static host if you only need the local-first client.

PWA features:

- Web app manifest with name, icons, shortcuts, standalone display and theme colors.
- Service worker registered in production builds only.
- `index.html` and assets cache for offline navigation.
- API requests are never cached.
- Verified: service worker controls the page; reload while offline loads the app shell; creating a goal offline works and survives another offline reload.

To install on Windows/Chrome:

1. Open `http://127.0.0.1:8787` in Chrome or Edge.
2. Use **Install app** / **Apps → Install this site as an app**.
3. Launch from the Start menu or desktop shortcut.

The installed PWA stores its IndexedDB under the browser profile for that origin. Data is not portable between origins (`localhost` vs `127.0.0.1`) — use the same URL consistently or export/import a backup.

## 3. Windows desktop — Electron shell

Location: `apps/desktop`.

What exists:

- Secure Electron main process (`contextIsolation`, no Node integration, sandboxed renderer, permissions denied by default).
- **Embedded local Java service.** On launch, `backend.cjs` starts `momentum-api.jar` on `127.0.0.1:8799` with data under `%APPDATA%\MOMENTUM\data`, waits for health, then loads the app from that local server. Navigation and UI assets are same-origin, so auth cookies, AI proxying and encrypted sync work normally.
- If a MOMENTUM service is already healthy on 8799, the app reuses it. If Java is unavailable, the app opens the bundled offline UI through the `momentum://app/` scheme and explains the limitation.
- Packaged runtime assets: web build, Java jar and local `runtime.env` (Groq key + allowlists) as Electron extra resources.
- Native application menu, single-instance lock, external-link handling.
- Packaging config for NSIS installer and portable Windows builds. The unpacked app is built and tested at `apps/desktop/release/win-unpacked/MOMENTUM.exe`.

Run from the repository:

```powershell
# Builds web + Java + packages the Windows app
npm run desktop:build

# Launch the packaged app
npm run desktop:run
```

Or double-click `MOMENTUM.lnk` / `Launch MOMENTUM.bat` from the project root. The user's installed Java 21+ runtime is required for the embedded service; the UI still opens offline without it.

### Verification status

- The packaged auto-start backend was exercised from the **actual packaged resources** on port 8800 with a clean data directory: health OK, Groq `openai/gpt-oss-120b` request returned `DESKTOP GROQ READY`, and an `openai` provider request was rejected with HTTP 400 by the provider lock.
- A real Groq goal plan was generated end-to-end through the web UI (4 milestones, 13 tasks) and imported as a goal.
- Electron window creation cannot be tested in the headless automation session (`notepad.exe` also exits immediately). The packaged window is therefore not claimed as launch-tested here; the application code, packaged resources, local service and UI layer were tested separately.

The unpacked Windows app was packaged successfully. Building the NSIS/portable installers in this environment failed at an `EBUSY` file lock while `electron-builder` rewrote the executable's Windows resources; retry on a normal interactive build machine with antivirus exclusions configured.

### Embedded key boundary

The supplied Groq key is stored outside the renderer: in `.env` for local development and in `apps/desktop/runtime.env` for the packaged app, which is copied to the app's resources. It is never injected into frontend JavaScript or bundled into the web assets. However, a desktop app that runs without asking for a key cannot hide that key from its user. Do not distribute this packaged build with the embedded key; rotate the key or require per-user keys for a distributable build.

## 4. Android — Capacitor 8

Location: `apps/mobile`, native project in `apps/mobile/android`.

Tools required:

- Android Studio with Android SDK (compile SDK supported by Capacitor 8)
- JDK 21+
- Node 22+

Build:

```powershell
npm run build
npm --prefix apps/mobile install
npm --prefix apps/mobile run sync
```

Then:

```powershell
cd apps/mobile
npx cap open android
```

Build/run from Android Studio, or use Gradle from the generated project. The app id is `com.momentum.app`; the display name is `MOMENTUM`.

### Native API configuration

The bundled client makes same-origin `/api/*` calls by default. Inside the Capacitor WebView, `/api` points to the WebView origin, not the Java server. Build the web app with an API origin:

```powershell
$env:VITE_API_URL = 'https://api.your-domain.example'
npm run build
npm --prefix apps/mobile run sync
```

Then set server-side:

```text
MOMENTUM_PUBLIC_URL=https://api.your-domain.example
MOMENTUM_ALLOWED_ORIGINS=https://localhost,capacitor://localhost
MOMENTUM_COOKIE_SECURE=true
```

OAuth on mobile has additional considerations: `SameSite=Lax` cookies may not be sent from a custom WebView origin to a cross-site API. Use a same-site custom domain or a native OAuth bridge (not implemented). This must be tested on a device.

### Verification limitation

The Android project was generated successfully (Capacitor copied the production web assets, generated the Gradle project, launcher icons and branded splash screens). No Android SDK or emulator was available in this environment, so **no APK/AAB was compiled or installed**. The shared UI's mobile widths were verified in Chromium at 390×844 with no horizontal overflow.

## 5. iOS — Capacitor 8

Location: `apps/mobile`, native project in `apps/mobile/ios`.

Tools required:

- macOS with Xcode
- CocoaPods or Swift Package Manager support for Capacitor 8
- Node 22+

Build:

```bash
npm run build
npm --prefix apps/mobile install
npm --prefix apps/mobile run sync
cd apps/mobile
npx cap open ios
```

The Xcode project and app icon/splash assets are generated. **iOS compilation and simulator testing require macOS/Xcode and were not possible on this Windows machine.** PWA behavior on iOS Safari should be tested separately; iOS may evict website data under storage pressure, so encourage JSON backups and PWA installation.

## 6. Shared functionality by platform

| Capability | Web/PWA | Electron | Android WebView | iOS WKWebView |
| --- | --- | --- | --- | --- |
| Local goals/IndexedDB | Yes | Yes (custom secure scheme) | Yes | Yes |
| Offline shell | Yes (SW) | Bundled assets | Bundled assets | Bundled assets |
| Notifications | Browser permissions | Not implemented | Native plugin needed | Native plugin needed |
| PDF export | Yes (download) | Yes | Uses WebView download handling | Uses share sheet/WebView behavior (test needed) |
| Attachments | Yes | Yes | WebView file picker | WebView file picker |
| Auth/AI/sync | Yes when API reachable | Requires API base setup | Requires API base + CORS/cookie testing | Requires API base + CORS/cookie testing |
| Deep links | URLs | custom scheme | Capacitor scheme config | Universal links config |

## 7. Platform QA checklist

Before release:

- Install PWA from the exact production origin; verify offline after a hard reload.
- Test Android back button and iOS safe-area insets.
- Test file picker/attachment downloads on both native platforms.
- Test auth cookies and OAuth on native WebViews (or implement native auth bridge).
- Test PDF download/share on Android/iOS.
- Test screen readers (TalkBack, VoiceOver, Narrator) and 200% text size.
- Test low-memory/eviction behavior and encourage backups.
- Test dark/light/system theme behavior in native status bars.
- Confirm app icons and splash screens on device.
- Verify CORS and HTTPS with the exact production origins.
