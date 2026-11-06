import { chromium } from 'playwright-core';
import { mkdir, rm } from 'node:fs/promises';

const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
await mkdir('screenshots', { recursive: true });
await rm('.browser-profile-offline', { recursive: true, force: true });
const context = await chromium.launchPersistentContext('.browser-profile-offline', {
  executablePath,
  headless: true,
  viewport: { width: 1440, height: 1000 },
});
const page = context.pages()[0] ?? (await context.newPage());
const errors = [];
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));

await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
if (await page.getByRole('button', { name: 'Skip setup' }).isVisible().catch(() => false)) {
  await page.getByRole('button', { name: 'Skip setup' }).click();
}
await page.waitForTimeout(500);
const sw = await page.evaluate(async () => {
  if (!('serviceWorker' in navigator)) return { supported: false };
  const registration = await navigator.serviceWorker.ready;
  if (!navigator.serviceWorker.controller) {
    await new Promise((resolve) => {
      const timer = setTimeout(resolve, 5000);
      navigator.serviceWorker.addEventListener('controllerchange', () => {
        clearTimeout(timer);
        resolve(undefined);
      }, { once: true });
    });
  }
  return {
    supported: true,
    scope: registration.scope,
    controlled: Boolean(navigator.serviceWorker.controller),
  };
});
console.log('SERVICE_WORKER', sw);

await context.setOffline(true);
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1200);
const offlineLoaded = await page.getByText('Your command center is ready. Give it a direction.').isVisible().catch(() => false)
  || await page.locator('.mobile-nav').isVisible().catch(() => false)
  || await page.locator('.sidebar').isVisible().catch(() => false);
console.log('OFFLINE_SHELL_LOADED', offlineLoaded);
console.log('OFFLINE_BODY', (await page.locator('body').innerText()).slice(0, 200).replace(/\s+/g, ' '));
await page.screenshot({ path: 'screenshots/30-offline-dashboard.png', fullPage: false });

await page.getByRole('button', { name: 'New goal' }).first().click();
await page.getByTestId('goal-title').fill('Offline-first goal survives a restart');
await page.getByLabel('Why this matters / description').fill('Created while the network was disabled to verify IndexedDB persistence.');
await page.getByRole('button', { name: 'Create goal' }).click();
await page.waitForTimeout(600);
const createdOffline = await page.getByText('Offline-first goal survives a restart').first().isVisible();
console.log('GOAL_CREATED_OFFLINE', createdOffline, page.url());

await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1000);
const persistedOffline = await page.getByText('Offline-first goal survives a restart').first().isVisible();
console.log('GOAL_PERSISTED_OFFLINE_RELOAD', persistedOffline);
await page.screenshot({ path: 'screenshots/31-offline-goal.png', fullPage: true });

await context.setOffline(false);
await page.waitForTimeout(300);
const manifest = await page.evaluate(async () => {
  const response = await fetch('/manifest.webmanifest');
  return { status: response.status, type: response.headers.get('content-type') };
});
console.log('MANIFEST', manifest);
console.log('ERRORS', errors);
await context.close();
