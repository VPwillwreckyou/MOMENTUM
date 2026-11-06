'use strict';

const fs = require('node:fs');
const path = require('node:path');

const source = path.resolve(__dirname, '..', '..', 'web', 'dist');
const destination = path.resolve(__dirname, '..', 'web', 'dist');

if (!fs.existsSync(source)) {
  console.error('Web build not found. Run `npm run build` in the repository root first.');
  process.exit(1);
}

fs.rmSync(destination, { recursive: true, force: true });
fs.mkdirSync(path.dirname(destination), { recursive: true });
fs.cpSync(source, destination, { recursive: true });
console.log(`Copied web build to ${destination}`);
