'use strict';

const fs = require('node:fs');
const path = require('node:path');

const desktopRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(desktopRoot, '..', '..');
const envPath = path.join(repoRoot, '.env');
const outputPath = path.join(desktopRoot, 'runtime.env');
const jarPath = path.join(repoRoot, 'services', 'api', 'build', 'momentum-api.jar');
const webIndex = path.join(repoRoot, 'apps', 'web', 'dist', 'index.html');

function parseEnv(contents) {
  const values = {};
  for (const line of contents.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const separator = trimmed.indexOf('=');
    if (separator <= 0) continue;
    const key = trimmed.slice(0, separator).trim();
    let value = trimmed.slice(separator + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    values[key] = value;
  }
  return values;
}

if (!fs.existsSync(envPath)) {
  console.error('Missing .env. Create it from .env.example and add GROQ_API_KEY before packaging.');
  process.exit(1);
}
if (!fs.existsSync(jarPath)) {
  console.error('Missing services/api/build/momentum-api.jar. Run npm run api:build first.');
  process.exit(1);
}
if (!fs.existsSync(webIndex)) {
  console.error('Missing apps/web/dist. Run npm run build first.');
  process.exit(1);
}

const env = parseEnv(fs.readFileSync(envPath, 'utf8'));
if (!env.GROQ_API_KEY) {
  console.error('GROQ_API_KEY is not set in .env.');
  process.exit(1);
}

const runtimeLines = [
  '# Generated for the packaged local service. Do not commit or share this file.',
  'MOMENTUM_AI_PROVIDERS=groq',
  'MOMENTUM_ALLOW_LOCAL_AI_KEY=true',
  `GROQ_API_KEY=${env.GROQ_API_KEY}`,
];
fs.writeFileSync(outputPath, `${runtimeLines.join('\n')}\n`, { encoding: 'utf8', mode: 0o600 });
console.log('Prepared packaged local-service configuration (Groq key not printed).');
