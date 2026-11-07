import { chromium } from 'playwright-core';
import { mkdir } from 'node:fs/promises';

const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
await mkdir('docs/screenshots', { recursive: true });
const context = await chromium.launchPersistentContext('.browser-profile-e2e', {
  executablePath,
  headless: true,
  viewport: { width: 1440, height: 1000 },
});
const page = context.pages()[0] ?? (await context.newPage());

await page.goto('http://127.0.0.1:8787/settings#appearance', { waitUntil: 'networkidle' });
await page.locator('.theme-option--dark').click();
await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.waitForTimeout(5500);
await page.screenshot({ path: 'docs/screenshots/dashboard-dark.png', fullPage: false });

await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.waitForTimeout(700);
await page.screenshot({ path: 'docs/screenshots/goals.png', fullPage: false });

await page.locator('.goal-card').filter({ hasText: 'Speak Spanish' }).first().click();
await page.waitForTimeout(1000);
await page.screenshot({ path: 'docs/screenshots/goal-detail.png', fullPage: true });

await page.goto('http://127.0.0.1:8787/planner', { waitUntil: 'networkidle' });
await page.waitForTimeout(700);
await page.screenshot({ path: 'docs/screenshots/planner.png', fullPage: false });

await page.goto('http://127.0.0.1:8787/settings#appearance', { waitUntil: 'networkidle' });
await page.waitForTimeout(900);
await page.screenshot({ path: 'docs/screenshots/settings.png', fullPage: false });
await page.locator('#ai').scrollIntoViewIfNeeded();
await page.waitForTimeout(600);
await page.screenshot({ path: 'docs/screenshots/settings-ai.png', fullPage: false });
await page.locator('.theme-option--light').click();
await page.waitForTimeout(700);
await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.waitForTimeout(5500);
await page.screenshot({ path: 'docs/screenshots/dashboard-light.png', fullPage: false });

await page.goto('http://127.0.0.1:8787/settings#appearance', { waitUntil: 'networkidle' });
await page.locator('.theme-option--dark').click();
await page.goto('http://127.0.0.1:8787/auth', { waitUntil: 'networkidle' });
await page.waitForTimeout(700);
await page.screenshot({ path: 'docs/screenshots/account-sync.png', fullPage: true });

await page.setViewportSize({ width: 390, height: 844 });
await page.goto('http://127.0.0.1:8787', { waitUntil: 'networkidle' });
await page.waitForTimeout(800);
await page.screenshot({ path: 'docs/screenshots/mobile-dashboard.png', fullPage: false });
await page.goto('http://127.0.0.1:8787/goals', { waitUntil: 'networkidle' });
await page.waitForTimeout(500);
await page.screenshot({ path: 'docs/screenshots/mobile-goals.png', fullPage: false });

console.log('DOC_SCREENSHOTS_CAPTURED');
await context.close();
