import { chromium } from 'playwright-core';
import { mkdir, rm } from 'node:fs/promises';

const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const profile = '.browser-profile-groq';
await rm(profile, { recursive: true, force: true });
await mkdir('screenshots', { recursive: true });
const context = await chromium.launchPersistentContext(profile, {
  executablePath,
  headless: true,
  viewport: { width: 1440, height: 1000 },
});
const page = context.pages()[0] ?? (await context.newPage());
const errors = [];
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`);
});

await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.waitForTimeout(500);
if (await page.getByRole('button', { name: 'Skip setup' }).isVisible().catch(() => false)) {
  await page.getByRole('button', { name: 'Skip setup' }).click();
}

await page.goto('http://127.0.0.1:8787/planner', { waitUntil: 'networkidle' });
await page.waitForTimeout(400);
const settingsText = await page.locator('.architect-provider').innerText();
console.log('PROVIDER_PANEL', settingsText.replace(/\s+/g, ' '));

await page.locator('.architect-input').fill('Get my first three freelance clients within 90 days');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.architect-input').fill('I have a portfolio, 8 years of product design experience, and about six hours per week. I have not done outbound sales before.');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.architect-input').fill('Three signed retainer agreements with a repeatable outreach and proposal process.');
await page.getByRole('button', { name: 'Continue' }).click();
await page.getByRole('button', { name: '5–8 hours / week' }).click();
await page.getByLabel('Target date').fill('2027-02-01');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.architect-input').fill('I have limited evenings, a full-time job, and I get discouraged by cold outreach.');
await page.getByRole('button', { name: 'Continue' }).click();
await page.locator('.ai-consent .checkbox-row').click();
await page.getByRole('button', { name: 'Generate my plan' }).click();

try {
  await page.locator('.plan-preview').waitFor({ timeout: 120000 });
  const planTitle = await page.locator('.plan-title-input').inputValue();
  const milestones = await page.locator('.plan-milestone-editor').count();
  console.log('GROQ_PLAN_OK', JSON.stringify({ planTitle, milestones }));
  await page.screenshot({ path: 'screenshots/50-groq-plan.png', fullPage: true });
  await page.getByRole('button', { name: 'Add to MOMENTUM' }).click();
  await page.waitForTimeout(700);
  console.log('GROQ_PLAN_IMPORTED', page.url());
} catch (error) {
  const detail = await page.locator('.form-error-banner').innerText().catch(() => '');
  console.log('GROQ_PLAN_FAILED', detail.replace(/\s+/g, ' '));
  await page.screenshot({ path: 'screenshots/50-groq-plan-failure.png', fullPage: true });
}
console.log('ERRORS', errors);
await context.close();
