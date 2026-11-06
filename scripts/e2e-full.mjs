import { chromium } from 'playwright-core';
import { mkdir, readFile, readdir, rm } from 'node:fs/promises';
import { existsSync } from 'node:fs';

const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const profileDir = '.browser-profile-e2e';
const testDataDir = '.e2e-data';
const screenshots = 'screenshots';
await mkdir(screenshots, { recursive: true });
await rm(profileDir, { recursive: true, force: true });
await mkdir(profileDir, { recursive: true });

const context = await chromium.launchPersistentContext(profileDir, {
  executablePath,
  headless: true,
  viewport: { width: 1440, height: 1000 },
  acceptDownloads: true,
});
const page = context.pages()[0] ?? (await context.newPage());
page.setDefaultTimeout(15000);
const errors = [];
page.on('console', (message) => {
  if (message.type() === 'error' && !message.text().includes('401 (Unauthorized)')) {
    errors.push(`console: ${message.text()}`);
  }
});
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));

const log = (...args) => console.log(...args);

await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.evaluate(() => window.localStorage.clear()).catch(() => {});
await page.waitForTimeout(600);

// ---------- Onboarding ----------
if (await page.getByRole('button', { name: 'Begin setup' }).isVisible().catch(() => false)) {
  await page.getByRole('button', { name: 'Begin setup' }).click();
  await page.getByLabel('What should MOMENTUM call you?').fill('Vahagn');
  await page.getByRole('button', { name: 'Ion Cyan' }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.locator('.onboarding-provider').filter({ hasText: 'Groq' }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByLabel('One goal you want to move forward').fill('Run a 10K in under 60 minutes');
  await page.getByLabel('A deadline, if one is real').fill('2027-02-14');
  await page.getByRole('button', { name: 'Create goal & enter' }).click();
  await page.waitForTimeout(800);
  log('ONBOARDING_CREATED_GOAL', page.url());
} else {
  log('ONBOARDING_ALREADY_COMPLETE');
}
await page.screenshot({ path: `${screenshots}/20-onboarding-goal.png`, fullPage: true });

// ---------- AI provider settings (Groq, server-managed key) ----------
await page.goto('http://127.0.0.1:8787/settings#ai', { waitUntil: 'networkidle' });
await page.getByLabel('Model').fill('openai/gpt-oss-120b');
await page.getByRole('button', { name: 'Save & test connection' }).click();
try {
  await page.locator('.provider-test-result').waitFor({ timeout: 120000 });
  log('AI_TEST', (await page.locator('.provider-test-result').innerText()).replace(/\s+/g, ' '));
} catch {
  log('AI_TEST_FAILED', await page.locator('.form-error-banner').innerText().catch(() => 'no error banner'));
}
await page.screenshot({ path: `${screenshots}/21-ai-settings.png`, fullPage: false });

// ---------- Goal Architect with real Groq ----------
await page.goto('http://127.0.0.1:8787/planner', { waitUntil: 'networkidle' });
await page.locator('.architect-input').fill('Learn enough Spanish to hold a 20-minute conversation on a trip to Madrid');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.architect-input').fill('I know basic greetings and about 100 words. I study inconsistently and have 25 minutes on weekday mornings.');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.architect-input').fill('A 20-minute unscripted conversation with a native speaker, plus the confidence to ask for help when stuck.');
await page.getByRole('button', { name: 'Continue' }).click();
await page.getByRole('button', { name: '5–8 hours / week' }).click();
await page.getByLabel('Target date').fill('2027-03-01');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.architect-input').fill('I travel for work twice a month and lose routine in hotels. I dislike grammar drills.');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.ai-consent .checkbox-row').click();
await page.getByRole('button', { name: 'Generate my plan' }).click();
let planGenerated = false;
try {
  await page.locator('.plan-preview').waitFor({ timeout: 300000 });
  planGenerated = true;
  log('AI_PLAN_GENERATED', (await page.locator('.plan-title-input').inputValue()));
  await page.screenshot({ path: `${screenshots}/22-ai-plan-preview.png`, fullPage: true });
  const planTitle = await page.locator('.plan-title-input').inputValue();
  await page.locator('.plan-title-input').fill(`${planTitle} — Madrid trip`);
  await page.getByRole('button', { name: 'Add to MOMENTUM' }).click();
  await page.waitForTimeout(900);
  log('AI_PLAN_IMPORTED', page.url());
} catch {
  const err = await page.locator('.form-error-banner').innerText().catch(() => 'no error visible');
  log('AI_PLAN_FAILED', err.replace(/\s+/g, ' '));
  await page.screenshot({ path: `${screenshots}/22-ai-plan-failure.png`, fullPage: true });
}

// ---------- Categories ----------
await page.goto('http://127.0.0.1:8787/categories', { waitUntil: 'networkidle' });
await page.getByRole('button', { name: 'New category' }).click();
await page.getByLabel('Name').fill('Health');
await page.getByRole('button', { name: 'health' }).click();
await page.getByRole('button', { name: 'Create category' }).click();
await page.waitForTimeout(400);
log('CATEGORY_CREATED', await page.getByText('Health', { exact: true }).first().isVisible());

// ---------- PDF export from onboarding goal ----------
await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.getByText('Run a 10K in under 60 minutes', { exact: true }).first().click();
await page.waitForTimeout(500);
let pdfPath = '';
try {
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 30000 }),
    page.getByRole('button', { name: 'PDF' }).click(),
  ]);
  pdfPath = `${screenshots}/momentum-goal.pdf`;
  await download.saveAs(pdfPath);
  const bytes = await readFile(pdfPath);
  log('PDF_EXPORTED', bytes.length, bytes.subarray(0, 5).toString());
} catch (error) {
  log('PDF_FAILED', error.message);
}
await page.screenshot({ path: `${screenshots}/23-goal-before-archive.png`, fullPage: true });

// ---------- Archive / restore ----------
await page.locator('.action-menu summary').click();
await page.getByRole('button', { name: 'Archive goal' }).click();
await page.getByRole('button', { name: 'Archive goal', exact: true }).last().click();
await page.waitForTimeout(400);
await page.goto('http://127.0.0.1:8787/archive', { waitUntil: 'networkidle' });
const archivedVisible = await page.getByText('Run a 10K in under 60 minutes', { exact: true }).first().isVisible();
log('ARCHIVED_VISIBLE', archivedVisible);
await page.getByRole('button', { name: 'Restore' }).first().click();
await page.waitForTimeout(300);
log('ARCHIVE_EMPTY_AFTER_RESTORE', (await page.locator('.archive-row').count()) === 0);

// ---------- Trash / restore ----------
await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.getByText('Run a 10K in under 60 minutes', { exact: true }).first().click();
await page.locator('.action-menu summary').click();
await page.getByRole('button', { name: 'Move to Recently Deleted' }).click();
await page.getByRole('button', { name: 'Move to Recently Deleted' }).last().click();
await page.waitForTimeout(700);
const onTrash = page.url().endsWith('/trash');
log('MOVED_TO_TRASH', onTrash, (await page.locator('.trash-row').count()) > 0);
await page.getByRole('button', { name: 'Restore' }).first().click();
await page.waitForTimeout(500);
log('TRASH_EMPTY_AFTER_RESTORE', (await page.locator('.trash-row').count()) === 0);

// ---------- Theme switch ----------
await page.goto('http://127.0.0.1:8787/settings#appearance', { waitUntil: 'networkidle' });
await page.locator('.theme-option--light').click();
await page.waitForTimeout(700);
await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.waitForTimeout(500);
await page.screenshot({ path: `${screenshots}/24-light-theme.png`, fullPage: true });
log('LIGHT_THEME', await page.locator('html').getAttribute('data-theme'));
await page.goto('http://127.0.0.1:8787/settings#appearance', { waitUntil: 'networkidle' });
await page.locator('.theme-option--dark').click();

// ---------- Authentication ----------
const email = `test-${Date.now()}@momentum.local`;
await page.goto('http://127.0.0.1:8787/auth', { waitUntil: 'networkidle' });
await page.getByRole('button', { name: 'Create account' }).click();
await page.getByLabel('Name').fill('Vahagn Test');
await page.getByLabel('Email').fill(email);
await page.getByLabel('Password').fill('momentum42');
await page.locator('.auth-form form').getByRole('button', { name: 'Create account' }).click();
try {
  await page.locator('.account-page').waitFor({ timeout: 20000 });
  log('ACCOUNT_REGISTERED', email);
} catch {
  log('ACCOUNT_REGISTRATION_FAILED', await page.locator('.form-error-banner').innerText().catch(() => 'no error'));
  await page.screenshot({ path: `${screenshots}/25-auth-failure.png`, fullPage: true });
}

// ---------- Encrypted sync ----------
if (await page.locator('#sync').isVisible().catch(() => false)) {
  await page.getByLabel('Create a sync passphrase').fill('momentum-test-passphrase-42');
  await page.locator('.sync-form .checkbox-row').click();
  await page.getByRole('button', { name: 'Enable & sync now' }).click();
  try {
    await page.locator('.settings-status--on').first().waitFor({ timeout: 60000 });
    log('SYNC_COMPLETED');
  } catch {
    log('SYNC_FAILED', await page.locator('.form-error-text').innerText().catch(() => 'no sync error'));
  }
  await page.waitForTimeout(1000);
  if (existsSync(`${testDataDir}/sync`)) {
    const files = await readdir(`${testDataDir}/sync`);
    log('SYNC_FILES', files);
    for (const file of files) {
      const raw = await readFile(`${testDataDir}/sync/${file}`, 'utf8');
      log('SYNC_CIPHERTEXT_ONLY', !raw.includes('10K') && !raw.includes('Spanish') && raw.includes('ciphertext'));
    }
  }
  await page.screenshot({ path: `${screenshots}/26-account-sync.png`, fullPage: true });
}

// ---------- Session persistence & logout/login ----------
await page.reload({ waitUntil: 'networkidle' });
await page.waitForTimeout(500);
log('SESSION_PERSISTED', await page.locator('.account-page').isVisible().catch(() => false));
await page.getByRole('button', { name: 'Sign out' }).click();
await page.waitForTimeout(700);
await page.locator('.auth-mode-switch').getByRole('button', { name: 'Sign in' }).click();
await page.getByLabel('Email').fill(email);
await page.getByLabel('Password').fill('momentum42');
await page.locator('.auth-form form').getByRole('button', { name: 'Sign in' }).click();
try {
  await page.locator('.account-page').waitFor({ timeout: 20000 });
  log('LOGIN_AGAIN_OK');
} catch {
  log('LOGIN_AGAIN_FAILED');
}

// ---------- Responsive check ----------
await page.setViewportSize({ width: 390, height: 844 });
await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.waitForTimeout(500);
await page.screenshot({ path: `${screenshots}/27-mobile-dashboard.png`, fullPage: true });
log('MOBILE_NAV_VISIBLE', await page.locator('.mobile-nav').isVisible());
await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.waitForTimeout(400);
await page.screenshot({ path: `${screenshots}/28-mobile-goals.png`, fullPage: true });

log('PLAN_GENERATED', planGenerated);
log('ERRORS', errors);
await context.close();
