import { chromium } from 'playwright-core';
import { mkdir, readFile, rm } from 'node:fs/promises';

const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
await mkdir('screenshots', { recursive: true });
await rm('.browser-profile-advanced', { recursive: true, force: true });
const context = await chromium.launchPersistentContext('.browser-profile-advanced', {
  executablePath,
  headless: true,
  viewport: { width: 1440, height: 1000 },
  acceptDownloads: true,
});
const page = context.pages()[0] ?? (await context.newPage());
const errors = [];
page.on('console', (message) => {
  if (message.type() === 'error' && !message.text().includes('401 (Unauthorized)')) errors.push(message.text());
});
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));

await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
if (await page.getByRole('button', { name: 'Skip setup' }).isVisible().catch(() => false)) {
  await page.getByRole('button', { name: 'Skip setup' }).click();
}

// ---------- Recurring goal ----------
await page.getByRole('button', { name: 'New goal' }).first().click();
await page.getByTestId('goal-title').fill('Weekly review ritual');
await page.getByLabel('Why this matters / description').fill('A recurring checkpoint that keeps the week intentional.');
await page.getByRole('button', { name: 'Recurring goal' }).click();
await page.getByLabel('Deadline').fill('2026-11-10');
await page.getByRole('button', { name: 'Create goal' }).click();
await page.waitForTimeout(400);
await page.getByRole('button', { name: /add first task/i }).click();
await page.getByLabel('Task', { exact: true }).fill('Review the week and choose three moves');
await page.getByRole('button', { name: 'Add task', exact: true }).click();
await page.waitForTimeout(300);
await page.getByRole('button', { name: 'Complete goal' }).click();
await page.getByRole('button', { name: 'Complete goal' }).last().click();
await page.waitForTimeout(700);
const completedText = await page.locator('body').innerText();
console.log('RECURRING_NEXT_CREATED', completedText.includes('next occurrence') || completedText.includes('Open next'));
await page.getByRole('link', { name: /Open next/i }).click();
await page.waitForTimeout(500);
const nextBody = await page.locator('body').innerText();
console.log('NEXT_OCCURRENCE_ACTIVE', nextBody.includes('Occurrence 2'), nextBody.includes('Nov 17, 2026'));
await page.screenshot({ path: 'screenshots/32-recurring-next.png', fullPage: false });

// ---------- Attachment upload + search + backup ----------
await page.locator('.attachment-drop input[type=file]').setInputFiles({
  name: 'research-notes.txt',
  mimeType: 'text/plain',
  buffer: Buffer.from('Important research notes for MOMENTUM testing.'),
});
await page.waitForTimeout(700);
console.log('ATTACHMENT_UPLOADED', await page.getByText('research-notes.txt', { exact: true }).isVisible());
await page.keyboard.press('Control+k');
await page.locator('.palette__input').waitFor();
await page.locator('.palette__input').fill('research-notes');
await page.waitForTimeout(500);
const paletteKinds = await page.locator('.palette__item-kind').allInnerTexts();
const paletteQuery = await page.locator('.palette__input').inputValue().catch(() => '');
console.log('ATTACHMENT_SEARCHABLE', paletteKinds.map((text) => text.toLowerCase()).includes('attachment'), paletteKinds, paletteQuery);
await page.keyboard.press('Escape');
await page.waitForTimeout(300);

await page.goto('http://127.0.0.1:8787/settings#data', { waitUntil: 'networkidle' });
let backupJson = null;
try {
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 20000 }),
    page.getByRole('button', { name: 'Export JSON' }).click(),
  ]);
  const savePath = 'screenshots/momentum-backup.json';
  await download.saveAs(savePath);
  backupJson = JSON.parse(await readFile(savePath, 'utf8'));
  console.log('BACKUP_ATTACHMENTS', backupJson.attachments?.length, backupJson.attachments?.[0]?.meta?.name);
  console.log('BACKUP_NO_KEY', backupJson.settings?.ai?.apiKeyCipher === null);
} catch (error) {
  console.log('BACKUP_FAILED', error.message);
}

// ---------- Trash + 30 day purge ----------
await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.getByText('Weekly review ritual', { exact: true }).first().click();
await page.locator('.action-menu summary').click();
await page.getByRole('button', { name: 'Move to Recently Deleted' }).click();
await page.getByRole('button', { name: 'Move to Recently Deleted' }).last().click();
await page.waitForTimeout(900);
console.log('TRASHED_FOR_PURGE', (await page.locator('.trash-row').count()) > 0);
await page.evaluate(async () => {
  const database = await new Promise((resolve, reject) => {
    const request = indexedDB.open('momentum-local');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  await new Promise((resolve, reject) => {
    const transaction = database.transaction('documents', 'readwrite');
    const store = transaction.objectStore('documents');
    const get = store.get('workspace');
    get.onsuccess = () => {
      const record = get.result;
      const old = new Date();
      old.setDate(old.getDate() - 31);
      record.value.goals = record.value.goals.map((goal) =>
        goal.title === 'Weekly review ritual' ? { ...goal, deletedAt: old.toISOString() } : goal,
      );
      store.put(record);
    };
    transaction.oncomplete = () => resolve(undefined);
    transaction.onerror = () => reject(transaction.error);
  });
  database.close();
});
await page.reload({ waitUntil: 'networkidle' });
await page.waitForTimeout(1400);
const purgeBody = await page.locator('body').innerText();
console.log('PURGED_AFTER_30_DAYS', !purgeBody.includes('Weekly review ritual'));
await page.screenshot({ path: 'screenshots/33-after-purge.png', fullPage: false });

// ---------- Mobile viewport checks ----------
await page.setViewportSize({ width: 390, height: 844 });
await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.waitForTimeout(700);
await page.screenshot({ path: 'screenshots/34-mobile-viewport.png', fullPage: false });
console.log('MOBILE_OVERFLOW', await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1));
await page.getByRole('button', { name: 'Create a new goal' }).click();
await page.waitForTimeout(300);
await page.screenshot({ path: 'screenshots/35-mobile-goal-modal.png', fullPage: false });
await page.keyboard.press('Escape');

await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.waitForTimeout(300);
await page.screenshot({ path: 'screenshots/36-mobile-goals-viewport.png', fullPage: false });

console.log('ERRORS', errors);
await context.close();
