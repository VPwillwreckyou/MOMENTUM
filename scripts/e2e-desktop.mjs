import { _electron as electron } from 'playwright-core';
import { mkdir } from 'node:fs/promises';

await mkdir('screenshots', { recursive: true });
const electronApp = await electron.launch({
  executablePath: 'apps/desktop/node_modules/electron/dist/electron.exe',
  args: ['apps/desktop'],
  env: { ...process.env, NODE_ENV: 'production' },
});

const page = await electronApp.firstWindow();
await page.waitForLoadState('domcontentloaded');
await page.waitForTimeout(1200);
const errors = [];
page.on('console', (message) => {
  if (message.type() === 'error' && !message.text().includes('401 (Unauthorized)')) errors.push(message.text());
});
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));

console.log('ELECTRON_TITLE', await page.title());
console.log('ELECTRON_URL', page.url());
console.log('PROTOCOL', await page.evaluate(() => location.protocol));
console.log('INDEXED_DB', await page.evaluate(() => typeof indexedDB !== 'undefined'));
console.log('BODY', (await page.locator('body').innerText()).slice(0, 240).replace(/\s+/g, ' '));

if (await page.getByRole('button', { name: 'Skip setup' }).isVisible().catch(() => false)) {
  await page.getByRole('button', { name: 'Skip setup' }).click();
}
await page.waitForTimeout(500);
if (await page.getByRole('button', { name: 'New goal' }).first().isVisible().catch(() => false)) {
  await page.getByRole('button', { name: 'New goal' }).first().click();
  await page.getByTestId('goal-title').fill('Desktop persistence check');
  await page.getByRole('button', { name: 'Create goal' }).click();
  await page.waitForTimeout(600);
}
await page.screenshot({ path: 'screenshots/40-electron-goal.png', fullPage: false });
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(900);
const persisted = await page.getByText('Desktop persistence check', { exact: true }).first().isVisible().catch(() => false);
console.log('DESKTOP_PERSISTED_AFTER_RELOAD', persisted);
console.log('ELECTRON_ERRORS', errors);
await electronApp.close();
