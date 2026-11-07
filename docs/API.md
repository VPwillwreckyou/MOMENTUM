# MOMENTUM HTTP API

Base URL in development: `http://127.0.0.1:8787`.

Responses are JSON. Errors use:

```json
{ "error": "A human-readable message." }
```

All responses are `Cache-Control: no-store` unless served from the static client.

## 1. Health

`GET /api/health`

```json
{
  "status": "ok",
  "version": "1.0.0",
  "time": "2026-11-04T20:00:00Z",
  "aiProviders": ["deepseek", "openai", "gemini", "groq", "ollama", "custom"]
}
```

## 2. Authentication

Cookies are used; clients must send `credentials: 'include'` (the MOMENTUM web client does).

### `GET /api/auth/providers`

```json
{
  "providers": [
    { "id": "google", "label": "Google", "configured": false },
    { "id": "apple", "label": "Apple", "configured": false },
    { "id": "github", "label": "GitHub", "configured": false },
    { "id": "discord", "label": "Discord", "configured": false }
  ]
}
```

### `POST /api/auth/register`

```json
{ "name": "Ada", "email": "ada@example.com", "password": "momentum42" }
```

- `201` with `{ "user": {...} }` and `Set-Cookie`.
- Validation errors return `400`.
- Duplicate email returns `400`.
- Rate-limited per IP.

### `POST /api/auth/login`

```json
{ "email": "ada@example.com", "password": "momentum42" }
```

- `200` with `{ "user": {...} }` and a session cookie.
- Bad credentials return `401`.

### `POST /api/auth/logout`

Clears the session cookie. `{ "ok": true }`.

### `GET /api/auth/me`

- `200` `{ "user": {...} }` when authenticated.
- `401` otherwise.

User view fields: `id`, `email`, `name`, `provider`, `providers[]`, `hasPassword`, `createdAt`. Password hashes are never returned.

### OAuth

- `GET /api/auth/oauth/{provider}` → `302` to the provider (or `503` when unconfigured).
- `GET|POST /api/auth/oauth/{provider}/callback` → validates state + PKCE, exchanges code, sets session cookie, redirects to the configured app URL with `?auth=success` or `?auth=error&message=...`.

Apple uses `response_mode=form_post`, so its callback is a POST. Google, GitHub and Discord use GET callbacks.

### Account deletion

`DELETE /api/account` (also accepted as `POST /api/account/delete`)

```json
{ "password": "optional-only-for-password-accounts" }
```

Requires an authenticated session. Deletes the account and encrypted sync envelope. Local goals are not server data and are unaffected. Returns `{ "ok": true }` and clears the cookie.

## 3. AI proxy

`POST /api/ai/chat` (no authentication required; rate-limited)

```json
{
  "provider": "groq",
  "apiKey": "optional-user-override",
  "model": "openai/gpt-oss-120b",
  "baseUrl": "https://api.groq.com/openai/v1",
  "json": true,
  "messages": [
    { "role": "system", "content": "You are..." },
    { "role": "user", "content": "..." }
  ]
}
```

Response:

```json
{
  "text": "model output",
  "provider": "groq",
  "model": "openai/gpt-oss-120b",
  "usage": "optional provider usage JSON"
}
```

Rules:

- `provider` is one of `deepseek`, `openai`, `gemini`, `groq`, `ollama`, `custom`.
- The delivered build sets `MOMENTUM_AI_PROVIDERS=groq`; other providers return `400`.
- Max 24 messages, 60,000 total characters; provider responses capped at 2 MB.
- Provider base URLs are validated against allowlists.
- User-supplied API keys are never logged or returned.
- Server environment keys are used when the request has a valid session. When `MOMENTUM_ALLOW_LOCAL_AI_KEY=true`, loopback requests may also use the server key without a session; use this only for a local desktop deployment.
- Errors: `400` invalid configuration/provider not allowed, `401` rejected key, `502` provider failure, `504` provider timeout, `429` rate limit.

## 4. Encrypted sync

All sync endpoints require an authenticated session.

### `GET /api/sync/envelope`

- `200` with `{ "envelope": "...", "revision": 1, "updatedAt": "ISO" }`.
- `200` with `{ "envelope": null, "revision": 0, "updatedAt": null }` if no backup exists.

### `PUT /api/sync/envelope`

```json
{
  "envelope": "{\"version\":1,\"salt\":\"...\",\"iv\":\"...\",\"ciphertext\":\"...\"}",
  "baseRevision": 0
}
```

- `200` with new `{ "revision": 2, "updatedAt": "ISO" }`.
- `409` when `baseRevision` is stale; body includes the current envelope and revision.
- Envelope payload max 4 MB; valid JSON with `version`, `salt`, `iv`, `ciphertext` is required.
- The server does not decrypt or inspect goal content.

### `DELETE /api/sync/envelope`

Deletes the user's encrypted backup. Local goals are unaffected.

## 5. Static client

Everything else under `/` serves the production web build, with SPA fallback for extensionless routes. API responses are never served from the service worker cache.

Security headers include CSP, nosniff, no-referrer, frame denial and a restrictive permissions policy. Compressible static assets are gzip-encoded when the client advertises gzip.

## 6. CORS and cookies

Default allowed origins:

- `http://127.0.0.1:5173`
- `http://localhost:5173`
- the configured public origin
- `https://localhost` — the Capacitor Android WebView origin
- `capacitor://localhost` — the Capacitor iOS WebView origin

Add extra native/test origins with `MOMENTUM_ALLOWED_ORIGINS` (comma-separated exact origins). Mutating requests with an `Origin` header outside the allowlist are rejected with `403`. Cookies are `HttpOnly; SameSite=Lax`, and `Secure` when the server is configured for HTTPS.

## 7. Rate limits

| Bucket | Limit |
| --- | --- |
| Auth/OAuth | 40 per 15 minutes per IP |
| AI | 24 per minute per IP |
| Sync | 40 per minute per IP |

These are in-memory and per-process. Use an edge rate limiter in production.
