# Contributing

Thanks for helping make MOMENTUM better. This is a compact, solo-ward project — keep submissions focused and verified.

## Getting started

```bash
git clone https://github.com/VPwillwreckyou/MOMENTUM.git
cd MOMENTUM
npm install --workspaces --include-workspace-root
cp .env.example .env   # fill GROQ_API_KEY to enable AI features (optional)
npm start
```

## What to know before you code

- **Architecture**: see `docs/ARCHITECTURE.md` and `docs/DATA_MODEL.md`. The web client (React + Vite) is the product surface for every platform; the local Java service (`services/api/src`) provides auth, sync and the AI proxy.
- **Local-first rule**: the IndexedDB workspace is the source of truth; every feature must keep working with the service offline.
- **Platform shells** (`apps/desktop`, `apps/mobile`) wrap the same web build — never fork platform-specific behavior into them.

## Verify before you push

```bash
npm run test:web        # 26 web unit tests
npm run test:api        # 8 Java API tests
npm run test:colors     # 10 e2e checks (colors + OAuth guidance)
npm run doctor          # 14-point system health check
```

CI runs the web and API jobs on every push — keep it green.

## Conventions

- Commit messages: short imperative summary + optional body ("Add X", "Fix Y" — like the existing log).
- New settings keys belong in `.env.example` (placeholders, never real values) and `docs/SECURITY.md` if they alter the trust boundary.
- Never commit secrets or generated build outputs; the `.gitignore` list is the source of truth.
- Screenshots for docs come from `node scripts/capture-docs.mjs` (needs Chrome and the service running).

## Reporting problems

Open an issue with: platform + OS version, launcher used, `npm run doctor` output, and the service logs (`%TEMP%\momentum\` on Windows, `${TMPDIR:-/tmp}/momentum/` on Linux/macOS). Security issues: see `docs/SECURITY.md` — do not open public issues for those.
