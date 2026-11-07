# 📖 MOMENTUM — Tutorial: What Is What & How to Launch Each App

This file explains **every part of the project** and exactly **how to start it** on each platform.

---

## ⚡ Quick cheat sheet

> **TL;DR — which file to double-click**
> - **Windows app (own window) → `MOMENTUM.exe`**
> - **Website (browser tab) → `MOMENTUM Website.bat`**
>
> Both do the same hidden setup (start the Java service on `127.0.0.1:8787`); they only differ in how the app is shown.

| I want to... | File / command | Notes |
|---|---|---|
| **Open the WINDOWS APP (standalone window)** | Double-click **`MOMENTUM.exe`** ★ | The main one. Starts the local service (hidden) + opens the app in its own chromeless window. No console window. |
| **Open the WEBSITE (normal browser tab)** | Double-click **`MOMENTUM Website.bat`** ★ | Starts the local service (hidden) + opens `http://127.0.0.1:8787` in your default browser |
| Open the app window the old way | `Launch MOMENTUM.bat` | Same result as `MOMENTUM.exe`, just via PowerShell (a console window flashes briefly) |
| **Run the web version manually** | `npm start` (from this folder) | Runs the Java service and opens your browser |
| **Restart everything cleanly** | `npm run restart` | Stops the service, then launches again |
| Stop the background service | `npm stop` | Also cleans up leftover processes |
| **Check that everything is healthy** | `npm run doctor` | Verifies Java, service, web build, APK sync, launchers, config and Groq in one command |
| Just the browser service | `npm run api` | Java service only, `http://127.0.0.1:8787` |
| Web app in dev mode (programmers) | `npm run dev` | Vite dev server with hot reload |
| Build the web app | `npm run build` | Creates `apps/web/dist` |
| Run mobile apps | See "📱 Android / 🍎 iOS" section below | Needs Android Studio / Xcode |
| Run all tests | `npm test` (unit) · `npm run test:ui` (browser suite) · `npm run test:colors` (colors + sign-in helper e2e) | |

> **Key fact:** every version of MOMENTUM (web, Windows, Android, iOS) is the **same web app** (`apps/web/dist`), and every device talks to the same local Java service. So starting the service (`MOMENTUM.exe` or `MOMENTUM Website.bat` does it automatically) is always step one.
> **No internet is required to launch or use local features.** The Java service, web assets, fonts and icons all come from this folder; the PWA keeps a cached copy for reloads. Only AI replies (Groq) and optional Google/Apple/GitHub/Discord sign-in need a connection.
> For mobile, the web app is bundled *into* the mobile app, so the service must still be running on the computer for AI features and sync to work.

---

## 🗺️ File map — what is for what

```
MOMENTUM/
├── MOMENTUM.exe             ← ★★ THE file for the Windows app. Double-click it.
├── MOMENTUM Website.bat     ← ★★ THE file for the website. Double-click it.
├── Launch MOMENTUM.bat      ← Older PowerShell way to open the same app window (same as MOMENTUM.exe)
├── MOMENTUM-android.apk     ← ★ Installable Android app (works offline).
├── MOMENTUM.lnk             ← Desktop shortcut (now points at MOMENTUM.exe, same as double-clicking it)
├── TUTORIAL.md              ← This file
├── README.md                ← General project description
├── .env                     ← ★ Settings + your GROQ_API_KEY + OAuth keys. Never share this.
├── .env.example             ← Template showing every possible setting (safe to share)
├── package.json             ← Root list of commands (npm start, npm test, ...)
│
├── apps/
│   ├── web/                 ← ★ The actual MOMENTUM app (UI code)
│   │   ├── src/             ←    App source code (pages, components, styles)
│   │   ├── dist/            ←    The BUILT app — the thing all devices load
│   │   └── ...              ←    Config files (Vite, TypeScript)
│   ├── launcher/            ← Source code of MOMENTUM.exe (plain C#, no Electron)
│   │   ├── Program.cs       ←    Starts the Java service if needed, then opens the app window
│   │   └── build.ps1        ←    Rebuilds MOMENTUM.exe after changes
│   ├── desktop/             ← Windows desktop shell (Electron) + scripts to build it
│   │   ├── main.cjs         ←    Electron window code
│   │   ├── backend.cjs      ←    Starts the Java service in the background
│   │   ├── runtime.env      ←    Groq key copy for the packaged .exe
│   │   └── release/win-unpacked/MOMENTUM.exe  ← Packaged Electron app (doesn't run on this PC — see note below)
│   └── mobile/              ← Android & iOS shells (Capacitor)
│       ├── capacitor.config.ts ←    Name, app ID, which web folder to bundle
│       ├── android/         ←    Android project (open in Android Studio)
│       └── ios/             ←    iOS project (open in Xcode, needs a Mac)
│
├── services/
│   └── api/                 ← ★ The "engine": Java service every app talks to
│       ├── src/main/java/   ←    Java source code (auth, goals, AI proxy, sync, OAuth)
│       ├── build/momentum-api.jar ← The runnable service (this is what `npm start` runs)
│       └── scripts/         ←    build.ps1 / run.ps1 / test.ps1
│
├── scripts/                 ← Launch & build helpers (PowerShell + test scripts)
│   ├── start.ps1            ←    Runs web build check + service + browser (npm start uses this)
│   ├── launch-app.ps1       ←    The Windows-app launcher used by Launch MOMENTUM.bat
│   ├── build-windows.ps1    ←    Packages the Electron .exe (desktop app)
│   ├── run-windows.ps1      ←    Runs the packaged Electron .exe
│   └── e2e-*.mjs            ←    Automated browser tests (safe to run, they don't break data you care about... they create test data!)
│
├── data/                    ← ★ Your goals, accounts, sync data (local service storage)
├── docs/                    ← Generated documentation + screenshots (API.md, ARCHITECTURE.md...)
├── node_modules/            ← Downloaded packages (auto-generated — never edit)
└── .npm-cache/              ← npm download cache (auto-generated — never edit)
```

---

## 🖥️ 1. Windows app (the app window)

**How to launch:**
- Double-click **`MOMENTUM.exe`** in this folder (recommended)
- Or double-click `Launch MOMENTUM.bat` — same result, older PowerShell implementation

**What it does, step by step:**
1. Loads your settings from `.env` (Groq key, ports, OAuth keys)
2. Checks `http://127.0.0.1:8787/api/health`; if the Java service is not running, starts it **hidden** (no black window)
3. Waits until the service reports healthy
4. Opens the app as a **standalone window** (`--app` mode). The window profile lives at `%LOCALAPPDATA%\MOMENTUMAppProfile` — shared by both launchers, so one login session (falls back to your default browser if Edge/Chrome are missing)
5. Launching again while the window is open just **focuses the existing window** (single-instance) — never a duplicate
6. Closing the window is safe — the service stays for the next launch

**If `MOMENTUM.exe` needs to be rebuilt** after editing `apps/launcher/Program.cs`:
```powershell
powershell -ExecutionPolicy Bypass -File apps\launcher\build.ps1
```
It compiles a native C# `.exe` with the Windows .NET compiler and writes `MOMENTUM.exe` to this folder.

**To fully stop the local service:** close all MOMENTUM windows, then run:
```
npm stop          ← from the MOMENTUM folder (or: stop the javaw.exe process in Task Manager)
```

> **Note about the Electron .exe:** `apps/desktop/release/win-unpacked/MOMENTUM.exe` is the classic packaged desktop app, but **Electron (the framework it's made with) can't start on your Windows 26H2 build yet** — it crashes even for a blank test app. That's not a bug in your app. That's why `MOMENTUM.exe` at the root of this folder is a small native launcher that just starts the service and opens the same app in a Chrome/Edge app window.

---

## 🌐 2. Web version

**Easiest way:** double-click **`MOMENTUM Website.bat`** — it starts the hidden service and opens `http://127.0.0.1:8787` in your normal browser. You can also open `http://127.0.0.1:8787` in **any browser** yourself once the service is running (Chrome, Firefox, your phone's browser on the same Wi-Fi — but AI/sync need the service running on the computer). This is the same app as the Windows window, only as a regular browser tab.

**Manual control (programmers):**
```
npm start          → build check + start service + open browser (press Ctrl+C to stop)
npm run build      → regenerate apps/web/dist after changing the app code
npm run dev        → developer mode with live reload at http://127.0.0.1:5173
```

### 🐧 Linux / 🍎 Mac
There are no `.bat` files there — use the shell launcher instead (works on both Linux and macOS):
```bash
./scripts/momentum.sh start     # build if needed, start service, open browser
./scripts/momentum.sh stop      # stop the service
./scripts/momentum.sh doctor    # full health check
```
Needs: Java 21+ (full JDK), Node.js 22+ with npm, and `curl` (macOS ships with all but the JDK). You can also "Install app" from Chrome to get a native-app-style window. Full details: `docs/PLATFORMS.md`, section 8.

---

## 📱 3. Android

The mobile app is the web app wrapped into a native Android shell (Capacitor).

**One-time setup:**
1. Install **Android Studio** ([developer.android.com/studio](https://developer.android.com/studio)) — it brings the Android SDK with it
2. Run Android Studio once and finish the setup wizard

**Ready-made APK:** `MOMENTUM-android.apk` in this folder is already built and signed (debug key). Copy it to the phone and install it (allow "install unknown apps" for the file manager). The full web app is bundled inside, so it opens and keeps goals offline; AI replies and account sync still need the MOMENTUM service running on a computer.

**Build & run:**
```powershell
# From the MOMENTUM folder:
npm --prefix apps/web run build         # 1. build the web app
npm --prefix apps/mobile run sync       # 2. copy it into the Android (and iOS) project
npm --prefix apps/mobile run open:android  # 3. open Android Studio
```
Then in Android Studio: press **▶ Run** (with a phone plugged in with USB debugging on, or an emulator).

**To rebuild the installable .apk:** in Android Studio use `Build → Build Bundle(s)/APK(s) → Build APK(s)`, or from this folder:
```powershell
npm --prefix apps/web run build
npm --prefix apps/mobile run sync
cd apps/mobile/android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
The result is `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk`.

> Enables AI features inside the app: the app talks to the service. Run the service (step 1 of the Windows section) on the same network BUT note the service binds to `127.0.0.1` — if you want AI from the phone, change `MOMENTUM_HOST=0.0.0.0` in `.env` and use your computer's IP in the app's AI settings.

---

## 🍎 4. iOS / iPhone

⚠️ **Apple requires a Mac with Xcode to compile and sign iOS apps.** The iOS project is already generated, configured and synced at `apps/mobile/ios` — it just cannot be compiled from Windows.

**On a Mac:**
1. Install **Xcode** from the App Store and open it once to finish its setup.
2. Install Node.js 22+ and, from the MOMENTUM folder, run:
   ```bash
   npm --prefix apps/web run build
   npm --prefix apps/mobile run sync
   npm --prefix apps/mobile run open:ios
   ```
   (`run:ios` opens Xcode; `ios/App/App.xcodeproj` can also be opened directly.)
3. In Xcode pick the `App` scheme, choose a simulator or a connected iPhone, then press **▶ Run**.
4. To run on your own iPhone: select the `App` target → **Signing & Capabilities** → choose your Apple ID team. Xcode creates a free development certificate; a paid developer account is only needed for App Store distribution.
5. To ship: **Product → Archive → Distribute App** (paid Apple Developer account required).

The app is fully offline: the web bundle is inside the app, goals stay on the device, and no server is needed. AI and account sign-in need the local Java service, which a phone cannot reach unless you deliberately expose the computer on your network.

Minimum iOS version: **15.0**. Bundle id: `com.momentum.app`.

---

## ⚙️ 5. The local service (the engine)

Location: `services/api/build/momentum-api.jar` — the same service serves:
- the app UI (`apps/web/dist`)
- account login/registration + **Google / GitHub / Discord / Apple sign-in**
- the **Groq AI proxy** (your key from `.env` is never sent to the browser)
- encrypted device sync

Health check: open `http://127.0.0.1:8787/api/health` — you should see `{"status":"ok",...}`.

---

## 🔑 Settings file (`.env`) — quick reference

| Setting | What it does |
|---|---|
| `GROQ_API_KEY` | Your Groq key (the AI brain) — already set ✅ |
| `GOOGLE_CLIENT_ID/SECRET`, `GITHUB_...`, `DISCORD_...`, `APPLE_...` | Sign-in buttons activate when both a lock and key are filled |
| `MOMENTUM_PORT` | Service port (default 8787) |
| `MOMENTUM_HOST` | Who can reach the service (`127.0.0.1` = only this PC) |

After changing `.env`, restart the service: `npm stop` → double-click `MOMENTUM.exe` again.

---

## 🔐 Google / GitHub / Discord / Apple sign-in (OAuth)

The login page has four sign-in buttons. They activate automatically once you create a free "OAuth app" on each provider and paste its ID + secret into `.env`. **Right now the four `*_CLIENT_ID` / `*_CLIENT_SECRET` values in `.env` are empty, so the buttons stay greyed ("Not configured") — the sign-in code is already complete and starts working the moment the values are filled in.** After editing `.env`, restart: `npm stop` then double-click `MOMENTUM.exe`.

| Provider | Where to create | Redirect URI to register |
|---|---|---|
| 🔵 Google | cloud.google.com → APIs & Services → Credentials | `http://127.0.0.1:8787/api/auth/oauth/google/callback` |
| 🖤 GitHub | github.com/settings/developers → New OAuth App | `http://127.0.0.1:8787/api/auth/oauth/github/callback` |
| 💜 Discord | discord.com/developers/applications → OAuth2 | `http://127.0.0.1:8787/api/auth/oauth/discord/callback` |
| 🍎 Apple | developer.apple.com ⚠️ needs HTTPS domain + paid Apple Developer Program | `https://YOUR-DOMAIN/api/auth/oauth/apple/callback` |

Accounts link automatically when the same verified email is used, so you can sign in with Google one day and Discord the next without losing data.

---

## 💾 Where your data lives (and how to back it up)

Everything you create inside MOMENTUM is stored **only on this computer**:

| Data | Location |
|---|---|
| Goals, tasks, attachments, accounts | `MOMENTUM/data/` (this folder) |
| App-window login session (`MOMENTUM.exe` and `Launch MOMENTUM.bat` — shared) | `%LOCALAPPDATA%\MOMENTUMAppProfile` |
| Service logs (if something misbehaves) | `%TEMP%\momentum\service-out.log` & `service-err.log` |

**Backup = copy the whole `MOMENTUM/data/` folder somewhere safe** (USB drive, cloud folder). Restoring = put the copy back and restart with `npm run restart`.

---

## 🌉 Use MOMENTUM from anywhere (not just home Wi-Fi)

By default the service listens on `127.0.0.1` — your PC only. From a **different network** (school, work, mobile data), the easiest safe option is **Tailscale** (free for personal use):

1. Install Tailscale on the PC **and** the phone: [tailscale.com/download](https://tailscale.com/download), sign in once, both devices join your private network.
2. Find the PC's Tailscale name (check `ipconfig` after connecting — it's the `100.x.x.x` address), or run `tailscale ip -4` in a terminal on the PC.
3. In the MOMENTUM folder's `.env`, add: `MOMENTUM_HOST=0.0.0.0`
4. `npm run restart` → now also listen on the Tailscale network.
5. On the phone, open the app and in **Settings → AI & connection** (or the app URL) use `http://<that-100.x.x.x>:8787` when not on your home Wi-Fi.

**Never port-forward 8787 to the public internet directly.** Tailscale keeps the connection private between your own devices only.

---

## 🧯 Troubleshooting

| Problem | Fix |
|---|---|
| Window opens but shows offline | Service not running: double-click `MOMENTUM.exe` again (it restarts the hidden service) |
| Double-clicked `MOMENTUM.exe` and nothing appears | It needs Chrome or Edge installed (uses Chrome app mode). For a visible error message, run `Launch MOMENTUM.bat` instead — its console shows what failed |
| `npm stop` says not running but app is offline | The app window may just be closed or stale — launch again |
| Port `8787` already in use | Run `npm stop`, wait 2 seconds, launch again |
| AI says provider unreachable | Check your internet; the Groq key is loaded automatically from `.env` |
| Changed `.env`, but nothing changed | Services only read `.env` at start — run `npm run restart` (or `npm stop`, then double-click `MOMENTUM.exe`) |
| Service died silently / weird behavior | Read the log at `%TEMP%\momentum\service-err.log` — the launcher writes crash reasons there |
| Forgot password? | Create a new account; local goals stay on each device, cloud sync is tied to login |
