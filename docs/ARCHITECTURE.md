# MOMENTUM Architecture

## 1. Design principles

1. **Local-first, not cloud-first with a cache.** The browser's IndexedDB workspace is the source of truth. The network is optional for every core goal workflow.
2. **Java owns the server-side core.** Authentication, OAuth, AI provider access, encrypted sync storage and static hosting are implemented in a Java service. The client never holds OAuth client secrets or server API keys.
3. **One product, platform-specific shells.** A shared React/TypeScript client is served as a PWA, wrapped by Electron on Windows, and wrapped by Capacitor on Android/iOS. The Java API can be shared by all of them. This avoids three separate UI implementations while keeping platform packaging honest.
4. **Explicit network boundaries.** Signing in does not upload goals. AI interview data leaves the device only when the user presses Generate and confirms consent. Sync uploads only after the user presses Sync/Enable.
5. **No framework cargo cult.** The Java service uses the JDK's built-in HTTP server and JSON utilities rather than adding a framework that this environment could not build reliably. It has no external Java dependencies.

## 2. Runtime topology

```text
┌─────────────────────────────────────────────────────────────┐
│ Client (browser / PWA / Electron / Capacitor WebView)       │
│                                                             │
│  React 19 + TypeScript                                       │
│  ├─ UI pages, design system, accessible dialogs               │
│  ├─ Domain logic: progress, recurrence, filtering, search     │
│  ├─ IndexedDB repository (workspace, attachments, vault)      │
│  ├─ Web Crypto: API-key vault, sync envelope                  │
│  └─ API client ───────────────┐                               │
└────────────────────────────────┼─────────────────────────────┘
                                 │ optional, explicit
                     ┌───────────▼────────────────────────────┐
                     │ Java 21 service (MomentumServer)        │
                     │                                        │
                     │ /api/auth/*     accounts, sessions, OAuth│
                     │ /api/ai/chat    provider proxy          │
                     │ /api/sync/*     opaque encrypted blobs  │
                     │ /*              built web client        │
                     │                                        │
                     │ data/accounts.json                      │
                     │ data/sync/<userId>.json                 │
                     └────────────┬───────────────────────────┘
                                  │ outgoing only
          ┌───────────────────────┼─────────────────────────┐
          │                       │                         │
   OpenAI-compatible       Google Gemini                 Ollama
   (DeepSeek/OpenAI/       generateContent              localhost:11434
    Groq/custom)                                         or configured URL
```

## 3. Technology choices

### Web client

| Concern | Choice | Reason |
| --- | --- | --- |
| UI | React 19 + TypeScript | Type safety, component reuse, strong ecosystem |
| Build | Vite 7 | Fast production builds and code splitting |
| Routing | React Router 7 | Nested app shell and route-level pages |
| Icons | Lucide | Consistent, tree-shaken icon language |
| Typography | Fontsource Manrope + Space Grotesk | Self-hosted, offline-safe, premium typography |
| PDF | jsPDF, lazy-loaded | Client-side export without sending private data |
| Persistence | IndexedDB via a small typed adapter | Large, transactional, durable local storage |
| Crypto | Web Crypto API | PBKDF2, AES-GCM, non-extractable device keys |
| Styling | Handcrafted CSS token system | Full control over a distinctive design, no utility-class bloat |

No state-management library is needed. The application has a single workspace provider whose actions mutate a versioned document and persist it through a serialized IndexedDB write queue.

### Java service

- Java 21 language/runtime target, compiled with JDK 25 in this environment (`--release 21`).
- `com.sun.net.httpserver.HttpServer` with a fixed thread pool.
- Dependency-free JSON parser/serializer (`Json`), HTTP helper layer, PBKDF2 password hashing, secure random tokens.
- File-backed stores with temp-file + atomic-move writes.
- `HttpClient` for OAuth token exchange and AI provider requests.
- No JPA, Spring, database driver, or external runtime dependency required.

The service is intentionally small and auditable: around 3,000 lines covering auth, OAuth, AI adapters, sync storage, static serving and tests. It can be deployed behind TLS/reverse proxy or run locally alongside the desktop app.

### Why no SQL database?

Goal data is device-local and accessed as a complete workspace graph for search, progress and AI planning. IndexedDB document storage avoids a server dependency and keeps the offline experience fast. The server stores only account/session records and opaque encrypted sync envelopes, so a SQL database would add operational weight without improving this product's core privacy model. The store interfaces are isolated and can be replaced with SQLite/PostgreSQL if multi-user collaboration or server-side goal data is added later.

## 4. Client architecture

```text
apps/web/src/
  main.tsx                 App bootstrap + service worker registration
  App.tsx                  Providers, routing, onboarding gate
  state/
    types.ts               Domain contracts
    MomentumContext.tsx    Workspace/state actions and persistence orchestration
    AuthContext.tsx        Optional account session state
  lib/
    db.ts                  IndexedDB adapter (documents, attachments, vault)
    repository.ts          Load/save/normalize/import/erase boundaries
    mutations.ts           Pure goal/task/milestone/recurrence transformations
    progress.ts            Derived progress and dashboard signals
    search.ts              Ranked global search + filters/sorts
    dates.ts               Local-date utilities (no UTC drift)
    validation.ts          Form and untrusted AI output validation
    ai.ts                  Provider-neutral prompt + browser AI client
    sync.ts                Encrypted sync envelope and per-item merge
    pdf.ts                 Lazy-loaded professional PDF export
    binary.ts              Base64/Blob/Crypto buffer helpers
  components/              Reusable UI + feature components
  pages/                   Route pages
  styles/                  Design tokens, components, layout, pages, responsive polish
  test/                    Vitest unit suites
```

### Persistence behavior

- A single `workspace` document holds goals, categories and activities.
- Attachments live in a separate object store to avoid rewriting large blobs on every task change.
- The non-extractable AES-GCM device key lives in a `vault` object store.
- Saves are queued and written through IndexedDB transactions. A failed save surfaces a visible storage error rather than pretending success.
- Settings are a separate document. This lets the app save appearance/AI settings independently and keeps authentication out of the goal document.
- On startup, the store normalizes old/incomplete records, purges goals deleted more than 30 days ago, and removes their attachments.

### Domain derivation

- Progress = weighted tasks/milestones if both exist; otherwise whichever exists. Completed goals show 100%.
- Recurrence creates a **new active occurrence** when the current occurrence is completed, preserving series history. Task/milestone IDs are remapped and completion state reset. The next due date is advanced past today.
- Momentum score combines average active-goal progress and recent completion velocity. It is deterministic and computed locally.
- Search is in-memory over the normalized workspace, so it works offline and updates instantly.

## 5. Server architecture

```text
services/api/src/main/java/dev/momentum/
  MomentumServer.java      HTTP routing, security headers, CORS, rate limits
  core/
    Json.java              Dependency-free parser/serializer
    HttpUtil.java          Request limits, cookies, JSON responses
    PasswordHasher.java    PBKDF2-HMAC-SHA256
    RateLimiter.java       In-memory fixed-window throttling
    Tokens.java            Secure session/state tokens and hashing
  auth/
    AccountStore.java      Users, identities, sessions, atomic JSON persistence
    OAuthService.java      OAuth 2.0 + PKCE, profile exchange, Apple JWT verification
  ai/
    AiProxy.java           Provider adapters, SSRF-resistant URL validation
  sync/
    SyncStore.java         Opaque encrypted envelope storage with revisions
  web/
    StaticFiles.java       SPA host, CSP, gzip, immutable assets
```

### Request lifecycle

1. Every API request receives security headers and an Origin policy check.
2. Mutating requests with a disallowed `Origin` are rejected before routing.
3. Sensitive endpoints enforce IP rate limits.
4. Auth uses an opaque session token cookie; only its SHA-256 hash is stored server-side.
5. AI keys are looked up from the request (user-supplied) or—only for authenticated users—from server environment variables.
6. Sync endpoints require a session and store only ciphertext.
7. Errors are returned as uniform JSON and never echo secrets or request bodies.

### Data files

- `data/accounts.json`: users (PBKDF2 hashes, identities) and SHA-256 session hashes.
- `data/sync/<userId>.json`: one opaque encrypted envelope + revision + timestamp per user.
- No goal titles, tasks or notes exist in server storage in plaintext.

## 6. AI provider abstraction

The delivered build is **locked to Groq** at two levels:

- Frontend build flag `VITE_AI_PROVIDER_LOCK=groq` (in `apps/web/.env.production`) hides other providers and defaults to Groq `openai/gpt-oss-120b`.
- Server environment `MOMENTUM_AI_PROVIDERS=groq` rejects any other provider with HTTP 400.

The full multi-provider architecture remains implemented and can be re-enabled by changing those configuration values.

The browser always calls `POST /api/ai/chat`. The Java proxy dispatches by provider:

| Provider family | Wire format |
| --- | --- |
| DeepSeek, OpenAI, Groq, custom | OpenAI-compatible `/chat/completions` |
| Gemini | `models/{model}:generateContent` with `contents` / `systemInstruction` |
| Ollama | `/api/chat` with native Ollama JSON |

Security controls:

- Provider base URLs are allowlisted by provider host.
- Ollama defaults to loopback port 11434; an operator can allow one explicit configured endpoint.
- Custom OpenAI-compatible providers require an explicit environment opt-in and host allowlist, preventing SSRF through a user-supplied URL.
- Request bodies and keys are never logged. API keys are never returned to the browser by the server.
- Server-side env keys are used for authenticated users. In the local desktop configuration only, `MOMENTUM_ALLOW_LOCAL_AI_KEY=true` also allows loopback requests to use the key, so the app works without signing in.
- User-entered keys are supplied for that request only.

### Embedded Windows runtime

The packaged Electron app includes:

- `resources/web` — the built React application
- `resources/api/momentum-api.jar` — the same Java service
- `resources/runtime.env` — generated local configuration containing the Groq key and provider allowlist

At startup, Electron launches Java on `127.0.0.1:8799` with data in `%APPDATA%\MOMENTUM\data`, waits for `/api/health`, then loads the app from that local server. This makes the UI and API same-origin, preserving cookies and sync behavior. The key is never inserted into frontend JavaScript, but it is extractable from the local package; see [Security](SECURITY.md#packaged-windows-key-boundary).

The UI enforces a consent checkbox before sending interview answers and stores the consent timestamp in local settings. It never sends the user's existing workspace to the provider.

## 7. Encrypted sync design

This is a **backup/merge** design, not live collaboration.

- Envelope: `{version, algorithm:'AES-GCM', kdf:'PBKDF2-SHA-256', iterations, salt, iv, ciphertext, updatedAt, deviceId}`.
- The payload is `{format:'momentum-workspace', version:1, workspace}`.
- Server stores the envelope string and a monotonic revision. It has no decryption key.
- On sync, the client fetches the remote envelope, decrypts it with the user's passphrase, merges goals/categories by `id` and `updatedAt`, merges activity records, then encrypts and pushes with optimistic concurrency (`baseRevision`).
- A 409 conflict returns the newer envelope; the client retries once after decrypting and merging again.
- Deletions are retained as tombstones (`deletedAt`) for 30 days, so a sync can propagate deletion before the purge.

**Trade-off:** users must remember their passphrase. That is deliberate: recovering the cloud data without it would mean the server could read it.

## 8. Platform strategy

| Platform | Runtime | Status |
| --- | --- | --- |
| Web | Browser + Vite build served by Java or any static host | Working/tested |
| Windows | Installable PWA / packaged Electron app with embedded Java service | PWA path tested; packaged backend and Groq tested; Electron window launch blocked by headless environment |
| Android | Capacitor 8 WebView + native project | Native project generated; not compiled (no Android SDK) |
| iOS | Capacitor 8 WKWebView + native project | Native project generated; not compiled (needs macOS/Xcode) |

The same offline application core runs in all shells. Native builds use the built `apps/web/dist` assets. For native shells that need authentication/AI/sync, set `VITE_API_URL` at build time to the deployed Java API origin and configure CORS/cookie settings. See [PLATFORMS.md](PLATFORMS.md).

## 9. Deployment notes

Recommended production setup:

```text
Internet → TLS reverse proxy (Caddy/Nginx) → Java service on 127.0.0.1:8787
                                             ├─ data volume mounted at /data
                                             └─ built web assets at /app/web/dist
```

Set at least:

- `MOMENTUM_COOKIE_SECURE=true`
- `MOMENTUM_PUBLIC_URL=https://your-domain`
- `MOMENTUM_ALLOWED_ORIGINS=https://your-domain` if a distinct app origin is used
- `MOMENTUM_DATA_DIR=/data`
- `MOMENTUM_WEB_DIST=/app/web/dist`

Back up `data/accounts.json` and `data/sync/` with normal server backup tooling. They contain credentials/session hashes and encrypted envelopes respectively. Rotate/delete them according to your privacy policy.

## 10. Known architectural limitations

- No realtime multi-device collaboration or conflict-free replicated data types. Sync is explicit and snapshot-based.
- No server-side encryption-key escrow (by design).
- No background sync while the app is closed. The service worker caches the app, not user data upload.
- OAuth state is process-local; a service restart during a login requires retry.
- No password-reset/email-verification flow yet.
- Native shells need platform build environments and were not compiled here.
