import { chromium } from 'playwright-core';
import { access, rm } from 'node:fs/promises';

const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const backup = 'screenshots/momentum-backup.json';
await access(backup);
await rm('.browser-profile-import', { recursive: true, force: true });
const context = await chromium.launchPersistentContext('.browser-profile-import', {
  executablePath,
  headless: true,
  viewport: { width: 1280, height: 900 },
});
const page = context.pages()[0] ?? (await context.newPage());
const errors = [];
page.on('pageerror', (error) => errors.push(error.message));
await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
if (await page.getByRole('button', { name: 'Skip setup' }).isVisible().catch(() => false)) {
  await page.getByRole('button', { name: 'Skip setup' }).click();
}
await page.goto('http://127.0.0.1:8787/settings#data', { waitUntil: 'networkidle' });
await page.locator('input[type=file][accept*="json"]').setInputFiles(backup);
await page.getByRole('button', { name: 'Restore backup' }).click();
await page.waitForTimeout(1200);
console.log('IMPORT_TOAST', await page.locator('.toast__message').last().innerText().catch(() => 'none'));
await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.waitForTimeout(500);
const body = await page.locator('body').innerText();
console.log('IMPORTED_GOAL_VISIBLE', body.includes('Weekly review ritual'));
await page.getByText('Weekly review ritual', { exact: true }).first().click();
await page.waitForTimeout(600);
console.log('IMPORTED_ATTACHMENT_VISIBLE', await page.getByText('research-notes.txt', { exact: true }).isVisible().catch(() => false));
await page.screenshot({ path: 'screenshots/41-imported-backup.png', fullPage: false });
console.log('ERRORS', errors);
await context.close();
