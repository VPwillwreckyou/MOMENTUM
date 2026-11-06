'use strict';

const path = require('node:path');
const fs = require('node:fs');
const { startLocalBackend, healthCheck } = require('../apps/desktop/backend.cjs');

const mode = process.argv[2] === 'packaged' ? 'packaged' : 'dev';
const repoRoot = path.resolve(__dirname, '..');
const userDataPath = path.resolve(repoRoot, '.desktop-test-data');
const resourcesPath = path.join(repoRoot, 'apps', 'desktop', 'release', 'win-unpacked', 'resources');
const port = mode === 'packaged' ? 8800 : 8799;

fs.rmSync(userDataPath, { recursive: true, force: true });

(async () => {
  let backend;
  try {
    backend = await startLocalBackend({
      isPackaged: mode === 'packaged',
      resourcesPath,
      repoRoot,
      userDataPath,
      port,
    });
    console.log('BACKEND_STARTED', backend.started, backend.baseUrl, backend.logPath);
    console.log('HEALTH', await healthCheck(backend.baseUrl));

    const response = await fetch(`${backend.baseUrl}/api/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'groq',
        model: 'openai/gpt-oss-120b',
        messages: [{ role: 'user', content: 'Reply with exactly: DESKTOP GROQ READY' }],
      }),
    });
    const payload = await response.json();
    console.log('AI_STATUS', response.status, payload.provider, payload.model);
    console.log('AI_TEXT_EXPECTED', String(payload.text || '').trim() === 'DESKTOP GROQ READY');

    const lockedResponse = await fetch(`${backend.baseUrl}/api/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider: 'openai', messages: [{ role: 'user', content: 'hi' }] }),
    });
    console.log('PROVIDER_LOCK_STATUS', lockedResponse.status);
  } finally {
    if (backend) await backend.stop();
  }
})().catch((error) => {
  console.error('DESKTOP_BACKEND_TEST_FAILED', error.message);
  process.exit(1);
});
