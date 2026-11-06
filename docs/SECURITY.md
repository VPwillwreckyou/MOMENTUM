# MOMENTUM Security & Privacy

## 1. Security model

MOMENTUM assumes:

- The browser/device is trusted and may be shared only at the user's discretion.
- The Java server is operated by the app owner or trusted party.
- TLS terminates at a reverse proxy in production.
- AI providers are third parties selected by the user.
- No server-side goal database exists; server sync data is encrypted client-side.

The design prioritizes **data minimization**. The server stores accounts, sessions and encrypted blobs; it does not store goals.

## 2. Authentication

### Email/password

- Passwords are hashed with PBKDF2-HMAC-SHA256, 210,000 iterations, 16-byte random salts.
- Hashes are compared with constant-time equality.
- Minimum 8 characters with at least one letter and one number; maximum 200.
- Login failures return a generic message to reduce account enumeration. A dummy hash is evaluated for unknown accounts to reduce timing differences.
- Rate limiting applies to register/login/OAuth callback endpoints (40 requests / 15 minutes per IP).

### Sessions

- 32-byte cryptographically random opaque tokens.
- Cookie `HttpOnly; SameSite=Lax; Path=/`; `Secure` when HTTPS/`MOMENTUM_COOKIE_SECURE=true`.
- Only SHA-256 token hashes are persisted. A stolen accounts file does not directly yield valid session cookies.
- 30-day session lifetime; expired sessions are cleaned up.
- Logout deletes the session hash server-side and clears the cookie.
- Deleting a user removes their sessions and encrypted sync envelope.

### OAuth

Supported: Google, Apple, GitHub, Discord.

- Authorization-code flow with PKCE S256.
- Random state token stored server-side for 10 minutes and validated on callback.
- Provider credentials are read only from environment variables; no OAuth secret is bundled in frontend code.
- Provider profile data is used to create/link an account:
  - Existing provider identity → same account.
  - Verified email matching an existing account → link identity.
  - Unverified email matching an existing account → refused, avoiding email-based takeover.
- GitHub account linking always checks `/user/emails` for `verified`, not just the public profile email.
- Apple `id_token`:
  - RS256 signature verified against Apple's JWKS.
  - `iss`, `aud`, `exp` validated.
  - No unsigned/decoded-only acceptance.
- OAuth errors redirect to the configured app URL with an error message; no tokens are placed in URLs.

**Operational requirement for Apple:** `APPLE_CLIENT_SECRET` is a JWT generated from the Apple team/key/service configuration. MOMENTUM consumes it; it does not generate long-lived Apple secrets. See Apple's "Creating a client secret" documentation. Rotate before expiry.

**Untested path:** OAuth token exchange and callback flows require real provider credentials. They were implemented but not exercised end-to-end in this environment.

## 3. Browser security

- Strict Content Security Policy on static responses:
  - `default-src 'self'`
  - scripts only from self (no inline scripts)
  - styles from self plus inline style attributes used for dynamic theme colors
  - images self/data/blob
  - fonts self/data
  - connections self only (AI/auth/sync go through the same-origin Java proxy)
  - `object-src 'none'`, `frame-ancestors 'none'`.
- `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, restrictive `Permissions-Policy`.
- SPA fallback never serves arbitrary paths; static path traversal is normalized and confined to the web root.
- React escapes user text; no `dangerouslySetInnerHTML` is used.
- Attachments are downloaded as blobs, not rendered as active same-origin HTML.
- Service worker never caches `/api/` responses, so goals, sync envelopes, AI responses and credentials are not stored in the HTTP cache.
- No third-party analytics, advertising scripts or remote fonts are loaded.

## 4. AI key handling

- User API keys are encrypted in the browser with AES-256-GCM using a **non-extractable** device key stored in IndexedDB.
- Ciphertext is stored in `AppSettings`; plaintext exists only in memory during a request.
- The key is sent only to the local/owned Java service, never directly to third parties or any cloud logging service.
- The AI proxy:
  - never logs request bodies or API keys;
  - never echoes keys in responses;
  - caps request size, conversation length and provider response size;
  - applies per-IP rate limits;
  - enforces a server-side provider allowlist;
  - sanitizes provider error messages before returning them.
- Server environment keys are used for authenticated users. In the local desktop configuration, `MOMENTUM_ALLOW_LOCAL_AI_KEY=true` also permits loopback (127.0.0.1/::1) requests to use the server key without an account, so the bundled Windows app works turnkey. Never enable that flag on a shared or reverse-proxied deployment, because local callers could consume the key.
- This build is locked to Groq with `MOMENTUM_AI_PROVIDERS=groq`; the UI hides other providers and the server rejects them. The multi-provider architecture remains available behind configuration.
- On HTTPS deployment, the request path from browser to Java is TLS-protected. Running on plain HTTP is suitable only for localhost development.

### Packaged Windows key boundary

The supplied Groq key is present in:
- the ignored `.env` used by the local Java service, and
- `apps/desktop/runtime.env` copied into the packaged app's resources.

It is **not** in frontend JavaScript, web assets, docs or source control. But a turnkey local desktop build necessarily contains a credential that the local user can obtain. Do not distribute this build as-is. For distribution, rotate the key and remove `runtime.env` embedding, forcing each user to supply their own Groq key.

The packaged app's local service binds to `127.0.0.1:8799`, allowing only local connections. It uses the same CSP and security headers as the web server. The renderer has Node integration disabled, context isolation enabled, sandboxing enabled and permissions denied by default.

## 5. AI data consent

- The Goal Architect displays the exact interview brief and requires a checkbox before generating.
- The app records `consentAcceptedAt`, and each new plan request still requires an explicit consent action.
- Existing goals, tasks, notes, categories and account data are **not** included in AI requests.
- No automatic AI calls run in the background.
- Revoking consent clears the local timestamp; the planner will require consent again.
- AI unavailability has a manual fallback; the app does not send data silently or fabricate AI output.

## 6. SSRF and provider URL validation

AI provider URLs are a classic SSRF risk. MOMENTUM therefore:

- Forces known provider hosts: `api.deepseek.com`, `api.openai.com`, `api.groq.com`, `generativelanguage.googleapis.com`.
- Allows Ollama only on loopback port 11434 by default.
- Allows a different Ollama URL only when an operator sets `MOMENTUM_OLLAMA_URL` to that exact base URL.
- Disables custom OpenAI-compatible providers unless `MOMENTUM_ALLOW_CUSTOM_AI=true`.
- Requires every custom host to appear in `MOMENTUM_CUSTOM_AI_HOSTS` and to use HTTPS.
- Rejects userinfo, query strings and fragments in base URLs.

Note: DNS rebinding remains theoretically possible for an operator-allowlisted custom host. Operators should only allow hosts they control or trust.

## 7. Encrypted sync

- Client-side AES-256-GCM with a passphrase-derived key (PBKDF2-SHA-256, 310,000 iterations, random salt, random IV, AAD).
- The server stores only opaque ciphertext and a revision number.
- Transport is expected to use TLS in production.
- Optimistic concurrency prevents silent overwrite: stale revisions receive 409, and the client merges after decrypting.
- User must acknowledge that a lost passphrase is unrecoverable.
- Sync is opt-in per device and never runs automatically on login.

**Threat boundary:** someone with access to the server files and unlimited passphrase guesses could attack the ciphertext offline. A strong, unique passphrase is essential. The app enforces only a 10-character minimum; a longer passphrase is strongly recommended.

## 8. Data retention and deletion

- Goals in Recently Deleted are kept for 30 days, purged at startup/hourly.
- Permanent goal deletion removes tasks, notes, activity and attachment blobs.
- Local erase requires typing `ERASE`; separate controls exist for cloud backup and account deletion.
- Account deletion requires the password for password-based accounts and deletes the server account and encrypted sync envelope. It does **not** delete local goals; the UI states this explicitly.
- JSON backup never contains API keys or the sync passphrase.
- Local backups include attachment data and should be treated as sensitive files.

## 9. Transport & deployment checklist

Before public deployment:

- Put the Java service behind HTTPS (Caddy, Nginx, Traefik, cloud LB).
- Set `MOMENTUM_COOKIE_SECURE=true` and `MOMENTUM_PUBLIC_URL=https://…`.
- Restrict `MOMENTUM_HOST` to loopback when a reverse proxy is used.
- Set `MOMENTUM_ALLOWED_ORIGINS` precisely (no wildcard support is implemented).
- Protect `data/` with filesystem permissions; back it up securely.
- Configure rate limiting at the edge in addition to the in-process limits.
- Do not expose Ollama directly to the internet.
- Never commit `.env`, `data/`, API keys or OAuth secrets.
- Rotate provider and OAuth credentials if a workstation is compromised.
- Keep the JDK and Node build dependencies patched.

## 10. Known security limitations

- No MFA, password reset, email verification or login notification.
- In-memory OAuth state is lost on service restart; users simply retry.
- CSRF relies on SameSite=Lax, an explicit Origin check for mutating requests, and same-origin API calls. No double-submit CSRF token.
- Session invalidation across devices is per-session; deleting the account removes all sessions, but there is no "sign out all devices" UI.
- API rate limiting is in-memory per process; a multi-instance deployment needs a shared edge limiter.
- No third-party security audit has been performed.
- Native WebView cookie/CORS behavior for Capacitor needs platform-specific validation before production mobile release.
