#!/usr/bin/env node
/**
 * MOMENTUM system doctor.
 * One command that verifies the whole stack: Java, local service, web build,
 * Android APK sync, launchers, config and the AI provider.
 *
 * Usage:  npm run doctor
 */

import { execFile } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
let failures = 0;
let warnings = 0;

function bucket(result) {
  if (result === 'fail') failures++;
  if (result === 'warn') warnings++;
}

function check(name, result, detail = '') {
  bucket(result);
  const mark = result === 'pass' ? '✓ ' : result === 'warn' ? '! ' : '✗ ';
  console.log(`${mark}${name}${detail ? ` — ${detail}` : ''}`);
}

function run(cmd, args) {
  return new Promise((resolve) => {
    execFile(cmd, args, { timeout: 15000, windowsHide: true }, (error, stdout, stderr) => {
      resolve({ ok: !error, stdout, stderr });
    });
  });
}

async function fetchOk(url, timeoutMs = 6000) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) });
    return response.ok ? response : null;
  } catch {
    return null;
  }
}

function sha256(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex');
}

// ---------------------------------------------------------------- checks ---

// 1. Java runtime
{
  const java = await run('java', ['-version']);
  if (java.ok) {
    const line = (java.stderr || java.stdout || '').split(/\r?\n/).find(Boolean) ?? 'java found';
    check('Java runtime', 'pass', line.trim());
  } else {
    check('Java runtime', 'fail', 'java not found in PATH — the local service cannot start');
  }
}

// 2. Web build
const dist = path.join(repo, 'apps', 'web', 'dist');
if (existsSync(path.join(dist, 'index.html'))) {
  check('Web build (apps/web/dist)', 'pass');
  console.log(`    service worker: ${existsSync(path.join(dist, 'sw.js')) ? 'present' : 'MISSING (offline mode would break)'}`);
  console.log(`    manifest: ${existsSync(path.join(dist, 'manifest.webmanifest')) ? 'present' : 'MISSING'}`);
} else {
  check('Web build (apps/web/dist)', 'fail', 'run: npm run build');
}

// 3. Java service jar
const jar = path.join(repo, 'services', 'api', 'build', 'momentum-api.jar');
check('Service jar (momentum-api.jar)', existsSync(jar) ? 'pass' : 'fail', existsSync(jar) ? '' : 'run: npm run api:build');

// 4. Local service health
const port = process.env.MOMENTUM_PORT || '8787';
const base = `http://127.0.0.1:${port}`;
const health = await fetchOk(`${base}/api/health`);
if (health) {
  const body = await health.json().catch(() => null);
  check('Local service (health endpoint)', 'pass', `${base} — status ${body?.status ?? 'ok'}`);
} else {
  check('Local service (health endpoint)', 'fail', `not responding — start it with Launch MOMENTUM.bat or npm start`);
}

// 5. AI provider (server-side Groq key)
if (health) {
  const aiResponse = await fetch(`${base}/api/ai/chat`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      provider: 'groq',
      json: false,
      model: 'openai/gpt-oss-120b',
      baseUrl: 'https://api.groq.com/openai/v1',
      messages: [{ role: 'user', content: 'Reply with exactly: OK' }],
    }),
    signal: AbortSignal.timeout(30000),
  }).catch(() => null);
  if (aiResponse && aiResponse.ok) {
    const body = await aiResponse.json().catch(() => null);
    check('Groq AI through local service', 'pass', `model replied: ${String(body?.text ?? '').slice(0, 20)}`);
  } else if (aiResponse) {
    check('Groq AI through local service', 'warn', `service answered ${aiResponse.status} — internet or key issue`);
  } else {
    check('Groq AI through local service', 'warn', 'no answer (offline?) — manual everything still works without internet');
  }
} else {
  check('Groq AI through local service', 'warn', 'skipped — service is not running');
}

// 6. Config essentials
const envPath = path.join(repo, '.env');
if (existsSync(envPath)) {
  const env = {};
  for (const line of readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const trimmedLine = line.trim();
    if (!trimmedLine || trimmedLine.startsWith('#')) continue;
    const index = trimmedLine.indexOf('=');
    if (index > 0) env[trimmedLine.slice(0, index).trim()] = trimmedLine.slice(index + 1).trim();
  }
  check('.env present', 'pass');
  check('Groq key configured in .env', env.GROQ_API_KEY ? 'pass' : 'fail');
  console.log(`    AI providers allowed: ${env.MOMENTUM_AI_PROVIDERS || '(all)'}`);
  const oauthReady = ['GOOGLE_CLIENT_ID', 'GITHUB_CLIENT_ID', 'DISCORD_CLIENT_ID'].filter((k) => env[k]);
  if (oauthReady.length) console.log(`    OAuth providers with IDs: ${oauthReady.join(', ')}`);
} else {
  check('.env present', 'fail', 'missing — copy .env.example and configure');
}

// 7. Windows launchers
const launchers = [
  ['Launch MOMENTUM.bat', path.join(repo, 'Launch MOMENTUM.bat')],
  ['MOMENTUM Website.bat', path.join(repo, 'MOMENTUM Website.bat')],
  ['MOMENTUM.exe (compiled launcher)', path.join(repo, 'MOMENTUM.exe')],
  ['Desktop shortcut', path.join(homedir(), 'Desktop', 'MOMENTUM.lnk')],
];
for (const [name, file] of launchers) {
  check(`Launcher: ${name}`, existsSync(file) ? 'pass' : 'warn', existsSync(file) ? '' : 'not found');
}

// 8. Android APK embedded web build sync
const apk = path.join(repo, 'MOMENTUM-android.apk');
if (existsSync(apk)) {
  check('Android APK (MOMENTUM-android.apk)', 'pass', `${(statSync(apk).size / 1048576).toFixed(1)} MB`);
  const embedded = path.join(repo, 'apps', 'mobile', 'android', 'app', 'src', 'main', 'assets', 'public', 'index.html');
  if (existsSync(embedded) && existsSync(path.join(dist, 'index.html'))) {
    const same = sha256(embedded) === sha256(path.join(dist, 'index.html'));
    check('APK embeds the current web build', same ? 'pass' : 'warn', same ? 'hashes match' : 'web build changed — run: npm --prefix apps/mobile run copy, then rebuild the APK');
  }
} else {
  check('Android APK (MOMENTUM-android.apk)', 'warn', 'not built yet — see TUTORIAL.md (Android section)');
}

// 9. Service crash logs worth reading
const logDir = path.join(tmpdir(), 'momentum');
for (const log of ['service-out.log', 'service-err.log']) {
  const logPath = path.join(logDir, log);
  if (existsSync(logPath)) console.log(`    service log: ${logPath} (${statSync(logPath).size} bytes)`);
}

// ------------------------------------------------------------- summary ---
console.log('');
if (failures === 0) {
  console.log(`Doctor: HEALTHY (${warnings} warning${warnings === 1 ? '' : 's'})`);
} else {
  console.log(`Doctor: ${failures} problem${failures === 1 ? '' : 's'} found (${warnings} warning${warnings === 1 ? '' : 's'})`);
}
process.exitCode = failures === 0 ? 0 : 1;
// Let libuv release the child-process handles before teardown (Node-on-Windows quirk).
await new Promise((resolve) => setTimeout(resolve, 150));
