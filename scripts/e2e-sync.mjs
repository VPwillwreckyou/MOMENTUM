import { chromium } from 'playwright-core';
import { rm } from 'node:fs/promises';

const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
await rm('.browser-profile-sync-a', { recursive: true, force: true });
await rm('.browser-profile-sync-b', { recursive: true, force: true });
const email = `sync-${Date.now()}@momentum.local`;
const password = 'momentum42';
const passphrase = 'shared-sync-passphrase-42';

async function newDevice(profile) {
  const context = await chromium.launchPersistentContext(profile, {
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
  return { context, page, errors };
}

async function signIn(page) {
  await page.goto('http://127.0.0.1:8787/auth', { waitUntil: 'networkidle' });
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.locator('.auth-form form').getByRole('button', { name: 'Sign in' }).click();
  await page.locator('.account-page').waitFor({ timeout: 20000 });
}

async function sync(page, firstTime) {
  await page.locator('#sync').scrollIntoViewIfNeeded();
  await page.getByLabel(firstTime ? 'Create a sync passphrase' : 'Sync passphrase').fill(passphrase);
  if (firstTime) {
    await page.locator('.sync-form .checkbox-row').click();
    await page.getByRole('button', { name: 'Enable & sync now' }).click();
  } else {
    await page.getByRole('button', { name: 'Sync now' }).click();
  }
  await page.waitForTimeout(3000);
  const error = await page.locator('.form-error-text').innerText().catch(() => null);
  console.log('SYNC_ERROR', firstTime ? 'first' : 'later', error);
  if (error) throw new Error(error);
}

const deviceA = await newDevice('.browser-profile-sync-a');
await deviceA.page.getByRole('button', { name: 'New goal' }).first().click();
await deviceA.page.getByTestId('goal-title').fill('Shared across devices');
await deviceA.page.getByLabel('Why this matters / description').fill('Created on device A and synchronized as ciphertext.');
await deviceA.page.getByRole('button', { name: 'Create goal' }).click();
await deviceA.page.waitForTimeout(400);
await deviceA.page.getByRole('button', { name: /add first task/i }).click();
await deviceA.page.getByLabel('Task', { exact: true }).fill('Created on device A');
await deviceA.page.getByRole('button', { name: 'Add task', exact: true }).click();
await deviceA.page.waitForTimeout(300);

await deviceA.page.goto('http://127.0.0.1:8787/auth', { waitUntil: 'networkidle' });
await deviceA.page.locator('.auth-mode-switch').getByRole('button', { name: 'Create account' }).click();
await deviceA.page.getByLabel('Name').fill('Sync Test');
await deviceA.page.getByLabel('Email').fill(email);
await deviceA.page.getByLabel('Password').fill(password);
await deviceA.page.locator('.auth-form form').getByRole('button', { name: 'Create account' }).click();
await deviceA.page.locator('.account-page').waitFor({ timeout: 20000 });
console.log('DEVICE_A_REGISTERED', email);
await sync(deviceA.page, true);
console.log('DEVICE_A_SYNCED');

const deviceB = await newDevice('.browser-profile-sync-b');
const emptyBefore = await deviceB.page.getByText('Shared across devices', { exact: true }).count();
console.log('DEVICE_B_EMPTY_BEFORE', emptyBefore === 0);
await signIn(deviceB.page);
await sync(deviceB.page, true);
await deviceB.page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await deviceB.page.waitForTimeout(500);
const visibleOnB = await deviceB.page.getByText('Shared across devices', { exact: true }).first().isVisible();
console.log('DEVICE_B_RECEIVED_GOAL', visibleOnB);
await deviceB.page.getByText('Shared across devices', { exact: true }).first().click();
await deviceB.page.waitForTimeout(500);
await deviceB.page.getByRole('button', { name: 'Task', exact: true }).click();
await deviceB.page.getByLabel('Task', { exact: true }).fill('Created on device B');
await deviceB.page.getByRole('button', { name: 'Add task', exact: true }).click();
await deviceB.page.waitForTimeout(500);
await deviceB.page.goto('http://127.0.0.1:8787/auth', { waitUntil: 'networkidle' });
await sync(deviceB.page, false);
console.log('DEVICE_B_SYNCED');

await deviceA.page.goto('http://127.0.0.1:8787/auth', { waitUntil: 'networkidle' });
await sync(deviceA.page, false);
await deviceA.page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await deviceA.page.waitForTimeout(400);
await deviceA.page.getByText('Shared across devices', { exact: true }).first().click();
await deviceA.page.waitForTimeout(600);
const merged = await deviceA.page.getByText('Created on device B', { exact: true }).isVisible();
console.log('DEVICE_A_MERGED_REMOTE_TASK', merged);
console.log('A_ERRORS', deviceA.errors);
console.log('B_ERRORS', deviceB.errors);

await Promise.all([deviceA.context.close(), deviceB.context.close()]);
