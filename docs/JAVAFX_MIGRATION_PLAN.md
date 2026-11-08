# MOMENTUM — JavaFX Native Desktop Migration Plan

## 1. Technical Audit

### 1.1 Codebase Summary

| Layer | Technology | Files | Lines |
|---|---|---|---|
| Frontend | React 19 + TypeScript + Vite | 85 | ~11,400 |
| Backend | Java 21 (JDK built-in HTTP server) | 12 | ~2,557 |
| Launcher | C# .NET 10 (WinExe) | 1 | ~200 |
| Styles | CSS (15 files) | 15 | ~4,000 |
| Tests | Vitest + custom Java runner | 9 | ~1,200 |

### 1.2 Frontend Architecture (React)

**Entry point:** `apps/web/src/main.tsx` → `App.tsx` → `AppShell.tsx` (layout with Sidebar, Topbar, MobileNav, Outlet, CommandPalette, Toasts, GoalForm modal)

**Pages (10):**
- `DashboardPage` — overview with stats, recent goals, activity
- `GoalsPage` — goal list with filtering/search
- `GoalDetailPage` — full goal detail with milestones, tasks, attachments, notes
- `ArchivePage` — archived goals
- `TrashPage` — deleted goals (30-day purge)
- `CategoriesPage` — category management
- `PlannerPage` — AI goal architect (interview → plan generation → import)
- `SettingsPage` — appearance, AI, account/sync, data/privacy, about
- `AuthPage` — login/register/OAuth
- `NotFoundPage` — 404

**State management:**
- `MomentumContext` — workspace (goals, categories, activities), settings, attachments, toasts, mutations
- `AuthContext` — user session, OAuth providers, login/register/logout

**Data persistence:**
- IndexedDB (`momentum-local` database) via `repository.ts`
- `localStorage` for settings overrides (API URL, notification prefs)
- All data is local-first; sync is opt-in encrypted

**UI components (25):** Badge, Button, EmptyState, Fields (Input, Select, Toggle, SegmentedControl), Loading, Logo, Modal, PageHeader, Progress, Toasts, plus domain components (GoalCard, GoalForm, MilestoneForm, TaskForm, CategoryBadge, AttachmentsPanel, SyncPanel, CommandPalette, Onboarding, Sidebar, Topbar, MobileNav, ErrorBoundary)

**Lib modules (23):** ai, api, appearance, binary, csv, dates, db, defaults, id, mutations, notifications, pdf, platform, progress, recurrence, repository, search, sync, useInstallPrompt, useOnlineStatus, validation, vault

### 1.3 Backend (Java Service)

**Single file:** `MomentumServer.java` (HTTP server on port 8787)

**API endpoints:**
- `GET /api/health` — health check
- `GET /api/auth/providers` — OAuth provider status
- `POST /api/auth/register` — email/password registration
- `POST /api/auth/login` — email/password login
- `POST /api/auth/logout` — session invalidation
- `GET /api/auth/me` — current user
- `GET /api/auth/oauth/{provider}` — OAuth authorization redirect
- `GET|POST /api/auth/oauth/{provider}/callback` — OAuth callback
- `DELETE /api/account` — account deletion
- `POST /api/ai/chat` — AI proxy (Groq/DeepSeek/OpenAI/Gemini/Ollama/custom)
- `GET /api/sync/envelope` — encrypted sync download
- `PUT /api/sync/envelope` — encrypted sync upload
- `DELETE /api/sync/envelope` — delete sync data
- Static file serving for web client

**Authentication:** PBKDF2 password hashing, opaque session tokens, OAuth 2.0 + PKCE (Google, GitHub, Discord), Apple removed

**AI proxy:** Provider allowlist, SSRF protection, rate limiting, request size limits, API key vault

**Sync:** Encrypted client-side (AES-256-GCM), server stores opaque blobs

### 1.4 Data Model

**Workspace** (stored in IndexedDB):
- `goals[]` — each with milestones[], tasks[], categoryId, status, kind, recurrence, priority, dates, finance, attachments
- `categories[]` — name, color, icon
- `activities[]` — activity log
- `settings` — theme, accent colors, AI config, etc.

**Accounts** (stored in `data/accounts.json` on server):
- email/password (PBKDF2), OAuth identities, sessions

**Attachments** (stored in IndexedDB as blobs)

**Sync envelope** (stored on server, encrypted)

### 1.5 Key Features

1. Goal CRUD (create, edit, archive, delete, restore, trash with 30-day purge)
2. Milestone management (nested in goals)
3. Task management (nested in goals, with parent/child hierarchy)
4. Categories with custom colors/icons
5. AI Goal Architect (interview → plan generation → import)
6. AI copilot (per-goal suggestions)
7. Encrypted cloud sync (passphrase-based, AES-256-GCM)
8. Account management (email/password + OAuth)
9. Attachments (up to 15MB, local storage)
10. PDF export
11. CSV export (goals, tasks, milestones)
12. Browser notifications for deadlines
13. PWA install prompt
14. Attachment preview (images, PDFs)
15. Theme customization (dual color pickers, dark/light)
16. Command palette (Ctrl+K)
17. Keyboard shortcuts (N for new goal)
18. Onboarding flow
19. Search and filtering
20. Activity log
21. Finance tracking per goal
22. Recurrence (daily/weekly/monthly)
23. Progress tracking with SVG ring

### 1.6 Browser Dependencies

| Feature | Browser API | JavaFX Equivalent |
|---|---|---|
| UI rendering | React DOM + CSS | JavaFX Scene Graph + FXML + CSS |
| State management | React Context + hooks | JavaFX Properties + ObservableList + bindings |
| Local storage | IndexedDB | SQLite (via JDBC) or file-based JSON |
| HTTP client | fetch API | java.net.http.HttpClient |
| Notifications | Web Notifications API | JavaFX notifications (or system tray) |
| File download | Blob + anchor click | JavaFX FileChooser + FileOutputStream |
| File upload | input[type=file] | JavaFX FileChooser + FileInputStream |
| Crypto (sync) | Web Crypto API | Java javax.crypto (AES, PBKDF2) |
| Crypto (vault) | Web Crypto (non-extractable keys) | Java KeyStore (software-backed) |
| Service worker | PWA offline | Not needed (desktop app) |
| OAuth redirect | Browser redirect | JavaFX WebView or system browser |
| PDF generation | jsPDF | Apache PDFBox or iText |
| CSV export | Blob download | File writing |
| Attachments | IndexedDB blobs | File system or SQLite BLOB |
| Drag & drop | HTML5 DnD | JavaFX drag events |
| Clipboard | Clipboard API | JavaFX Clipboard |
| Theming | CSS variables | JavaFX CSS + dynamic stylesheets |
| Animations | CSS transitions | JavaFX Timeline/Transition |
| Charts/progress | SVG | JavaFX Canvas or Shape nodes |

### 1.7 Reusable Components (Backend)

The entire Java backend is reusable:
- `MomentumServer.java` — HTTP server, routing, CORS, auth, sync, AI proxy
- `AccountStore.java` — user accounts, sessions, OAuth linking
- `OAuthService.java` — OAuth 2.0 + PKCE (Google, GitHub, Discord)
- `AiProxy.java` — AI provider proxy with SSRF protection
- `SyncStore.java` — encrypted sync storage
- `PasswordHasher.java`, `Tokens.java`, `RateLimiter.java` — security utilities
- `HttpUtil.java`, `Json.java` — HTTP/JSON utilities
- `StaticFiles.java` — static file serving (can be removed for desktop)

All backend logic, authentication, AI integration, and sync can be preserved as-is. The desktop app would embed the Java backend as a library and call it directly (in-process) or via localhost HTTP.

---

## 2. Migration Strategy

### 2.1 Approach: Hybrid Desktop Application

The recommended approach is a **hybrid** model:
- **Backend:** Reuse the existing Java backend (in-process, not HTTP)
- **Frontend:** Rewrite in JavaFX with FXML + CSS
- **Data:** Migrate from IndexedDB to SQLite (or keep JSON files)
- **Auth:** Reuse AccountStore and OAuthService (call directly, not via HTTP)
- **AI:** Reuse AiProxy (call directly)
- **Sync:** Reuse SyncStore (call directly)

This means the JavaFX app embeds the backend as a library and calls Java methods directly, eliminating the need for an HTTP server. The existing `MomentumServer` can be refactored to expose a service layer that both the HTTP server and the JavaFX app use.

### 2.2 Phase 1: Backend Refactoring (Extract Service Layer)

**Effort: 1-2 weeks**

1. Extract business logic from `MomentumServer.java` into a `MomentumService` class
2. `MomentumService` exposes methods: `register()`, `login()`, `logout()`, `chat()`, `syncUpload()`, `syncDownload()`, `deleteAccount()`, etc.
3. `MomentumServer` becomes a thin HTTP wrapper around `MomentumService`
4. JavaFX app calls `MomentumService` directly
5. This preserves all backend logic and makes it testable independently

### 2.3 Phase 2: Data Layer Migration (IndexedDB → SQLite)

**Effort: 1-2 weeks**

1. Create SQLite schema matching the Workspace data model
2. Implement `SQLiteRepository` with the same interface as the web `repository.ts`
3. Migrate attachments to file system (store as files in `data/attachments/`)
4. Migrate settings to SQLite or properties file
5. Keep JSON backup/export functionality
6. Ensure data migration from web version (import JSON backup)

### 2.4 Phase 3: JavaFX UI Framework Setup

**Effort: 1 week**

1. Set up Gradle project with JavaFX dependencies
2. Create base FXML layout (main window, sidebar, content area)
3. Implement JavaFX CSS theme (dark theme, accent colors, gradients)
4. Set up navigation (screen switching)
5. Implement toast/notification system
6. Implement modal/dialog system

### 2.5 Phase 4: Core Screens Migration

**Effort: 3-4 weeks**

Priority order (by usage frequency):
1. Dashboard (stats, recent goals, activity)
2. Goals list + Goal detail
3. Goal/Task/Milestone forms
4. Categories
5. Settings (appearance, AI, account, data)
6. Archive + Trash
7. Auth (login/register/OAuth)
8. Planner (AI goal architect)
9. Onboarding

Each screen needs:
- FXML layout
- Java controller with property bindings
- JavaFX CSS styling
- Keyboard shortcuts
- Accessibility (screen reader support)

### 2.6 Phase 5: Feature Parity

**Effort: 2-3 weeks**

1. AI integration (call AiProxy directly)
2. OAuth flows (open system browser, capture callback via local HTTP server or custom protocol)
3. Encrypted sync (reuse SyncStore)
4. Attachments (file system storage + preview)
5. PDF export (Apache PDFBox)
6. CSV export (file writing)
7. Notifications (system tray or JavaFX notifications)
8. Search and filtering
9. Command palette (Ctrl+K)
10. Theme customization (dual color pickers)

### 2.7 Phase 6: Packaging and Distribution

**Effort: 1 week**

1. Use `jpackage` to create Windows .exe installer
2. Bundle JRE (modular, ~50MB)
3. Create app icon, splash screen
4. Test on clean Windows machine
5. Create GitHub Release with the .exe

---

## 3. Compatibility Problems and Risks

### 3.1 High Risk

| Problem | Impact | Mitigation |
|---|---|---|
| IndexedDB → SQLite migration | Data loss if not careful | JSON backup import/export as migration path |
| Web Crypto → Java crypto | Sync envelope incompatibility | Re-implement PBKDF2 + AES-GCM with exact same parameters |
| CSS → JavaFX CSS | Visual identity loss | JavaFX CSS supports most properties; custom rendering for gradients |
| React state → JavaFX bindings | Complex state management | Use JavaFX ObservableList and property bindings |
| OAuth redirect capture | Need local HTTP server or custom protocol | Use `java.awt.Desktop` to open browser, capture callback via temporary HTTP server on localhost |

### 3.2 Medium Risk

| Problem | Impact | Mitigation |
|---|---|---|
| JavaFX availability | Not in JDK by default | Use Gradle to download JavaFX modules from Maven Central |
| Windows 26300 compatibility | JavaFX rendering might also be affected | JavaFX uses its own graphics pipeline (Prism), not Chromium — should work |
| Performance | JavaFX startup time | Use jlink to create minimal JRE, lazy-load screens |
| File system attachments | Different from IndexedDB blobs | Store as files in `data/attachments/`, metadata in SQLite |

### 3.3 Low Risk

| Problem | Impact | Mitigation |
|---|---|---|
| Dark theme | JavaFX CSS supports dark themes | Define CSS variables in JavaFX stylesheet |
| Animations | JavaFX has Timeline/Transition | Re-implement key animations |
| Responsive layout | JavaFX has responsive containers | Use VBox/HBox/GridPane with constraints |

### 3.4 JavaFX vs Web Tech Mapping

| Web (React) | JavaFX |
|---|---|
| `useState` | `SimpleObjectProperty<T>` / `SimpleStringProperty` |
| `useEffect` | `ChangeListener` / `InvalidationListener` |
| `useContext` | Static service locator or dependency injection |
| `useMemo` | `Bindings.create...` |
| `useCallback` | Method references |
| React Router | `ScreenManager` with screen stack |
| CSS classes | JavaFX CSS (`.class { -fx-property: value; }`) |
| Flexbox/Grid | HBox/VBox/GridPane/BorderPane |
| `onClick` | `setOnAction` / `setOnMouseClicked` |
| `onChange` | `textProperty().addListener()` |
| Modal | `Stage` with modality or custom overlay |
| Toast | Custom notification overlay |
| Drag & drop | `setOnDragOver`, `setOnDragDropped` |
| File picker | `FileChooser` |
| Local storage | SQLite or Properties file |
| IndexedDB | SQLite (JDBC) |
| Service worker | Not needed (desktop app) |
| PWA install | Not needed (native app) |

---

## 4. Estimated Effort

| Phase | Duration | Description |
|---|---|---|
| Phase 1: Backend refactoring | 1-2 weeks | Extract service layer from HTTP server |
| Phase 2: Data migration | 1-2 weeks | IndexedDB → SQLite, attachments → files |
| Phase 3: JavaFX framework | 1 week | Gradle, FXML, CSS theme, navigation, modals |
| Phase 4: Core screens | 3-4 weeks | 10 screens with full functionality |
| Phase 5: Feature parity | 2-3 weeks | AI, OAuth, sync, attachments, PDF/CSV, notifications |
| Phase 6: Packaging | 1 week | jpackage, testing, release |
| **Total** | **8-13 weeks** | Full-time equivalent |

---

## 5. What Can Be Reused

### Fully Reusable (Backend)
- All Java backend code (AccountStore, OAuthService, AiProxy, SyncStore, etc.)
- All data models (Goal, Task, Milestone, Category, etc.)
- All business logic (mutations, validation, recurrence, progress, search)
- All security (PBKDF2, rate limiting, SSRF protection, CORS)
- All API contracts (can call Java methods directly instead of HTTP)

### Partially Reusable
- CSS color tokens → JavaFX CSS variables
- SVG icons → JavaFX Shape/Path or icon fonts
- Data validation rules → Java validation methods
- AI prompts → same strings, same API calls

### Not Reusable (Must Rewrite)
- All React components (JSX → FXML)
- All CSS styling (web CSS → JavaFX CSS)
- All state management (React hooks → JavaFX properties)
- All routing (React Router → screen manager)
- All event handlers (JS events → JavaFX events)
- IndexedDB access (→ SQLite)
- Web Crypto (→ Java crypto)
- Service worker (→ not needed)
- PWA manifest (→ not needed)

---

## 6. JavaFX Availability

JavaFX is **not** included in the JDK by default (removed since Java 11). However:
- JavaFX modules are available on **Maven Central**
- Gradle can download them automatically
- JavaFX 21+ supports Java 21+ (LTS)
- JavaFX 25 supports Java 25

**Gradle dependency:**
```groovy
dependencies {
    implementation 'org.openjfx:javafx-controls:21'
    implementation 'org.openjfx:javafx-fxml:21'
    implementation 'org.openjfx:javafx-graphics:21'
    implementation 'org.openjfx:javafx-web:21'  // Only if needed for OAuth
}
```

**jpackage command:**
```bash
jpackage --input build/libs --name MOMENTUM --main-jar momentum.jar --main-class dev.momentum.desktop.Main --type exe --win-dir-chooser --win-menu --win-shortcut
```

---

## 7. Recommendation

Given the scale of this migration (8-13 weeks full-time), I recommend:

1. **Start with Phase 1** (backend refactoring) — this is the foundation and can be done without touching the UI
2. **Run the web app and JavaFX app in parallel** — the JavaFX app calls the same backend
3. **Migrate screens incrementally** — start with Dashboard, then Goals, then Settings
4. **Keep the web app as fallback** — don't delete it until JavaFX is feature-complete
5. **Use the existing test suite** to verify backend behavior doesn't change

The existing `MOMENTUM.exe` launcher (Edge app mode) remains a viable fallback for users who prefer the web UI.

---

## 8. What I Need From You

Before starting implementation:
1. **Approve the migration plan** (or request changes)
2. **Confirm priority** — should I start with Phase 1 (backend refactoring) or jump to Phase 3 (JavaFX framework)?
3. **Confirm data migration strategy** — SQLite or keep JSON files for simplicity?
4. **Confirm OAuth approach** — system browser + local callback server, or JavaFX WebView (which uses its own rendering, not Chromium)?
