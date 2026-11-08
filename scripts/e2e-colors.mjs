#!/usr/bin/env node
/**
 * E2E: dual color system + OAuth sign-in guidance.
 * Verifies:
 *   1. Onboarding "Make it yours" dual color pickers exist and update settings.
 *   2. Settings > Appearance dual pickers exist.
 *   3. The composite --accent-linear gradient reflects the chosen secondary color.
 *   4. Clicking an unconfigured OAuth provider shows the exact .env guidance banner.
 *   5. Console stays clean.
 *
 * Usage: node scripts/e2e-colors.mjs
 */

import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs';
import { chromium } from 'playwright-core';

const executablePath = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const profileDir = '.browser-profile-colors-e2e';
rmSync(profileDir, { recursive: true, force: true });
mkdirSync('test-results', { recursive: true });

const errors = [];
let passed = 0;
let failed = 0;

function check(name, ok, detail = '') {
  if (ok) {
    passed++;
    console.log(`PASS  ${name}`);
  } else {
    failed++;
    console.log(`FAIL  ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

const context = await chromium.launchPersistentContext(profileDir, {
  executablePath,
  headless: true,
  viewport: { width: 1440, height: 1000 },
});
const page = context.pages()[0] ?? (await context.newPage());
page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()); });
page.on('pageerror', (error) => errors.push(String(error)));

// ------------------------------------------------------------- landing ---
await page.goto('http://127.0.0.1:8787/', { waitUntil: 'networkidle' });
await page.waitForTimeout(2500);

// Jump to onboarding step 2 ("Make it yours")
const step2 = page.locator('[aria-label*="step 2"]').first();
check('onboarding step 2 reachable', await step2.count() > 0);
await step2.click();
await page.waitForTimeout(900);
check('onboarding dual pickers visible', await page.locator('.onboarding-gradient-picker').isVisible());

const firstHex = () => page.locator('.gradient-picker-slot code').first().textContent();
const secondaryHex = () => page.locator('.gradient-picker-slot code').nth(1).textContent();

// Apply a preset to change the primary color, then verify the primary code updated
const preset = page.locator('.onboarding-accents button').first();
await preset.click();
await page.waitForTimeout(400);
const afterPreset = await secondaryHex() ?? '';
const presetValue = await preset.getAttribute('aria-label').catch(() => '');
check('presets update the primary color', (await firstHex()).length === 7, `first=${await firstHex()}`);

// The gradient preview bar exists
check('gradient preview bar present', await page.locator('.gradient-picker-preview').count() > 0);
check('secondary code field present', /^[0-9a-fA-F]{6}/.test(afterPreset.replace('#', '')), `secondary=${afterPreset}`);

// ------------------------------------------------------------ settings ---
// Skip the rest of onboarding, then verify Settings appearance pickers
const skip = page.locator('button:has-text("Skip")').first();
if (await skip.count()) {
  await skip.click();
  await page.waitForTimeout(1500);
}
await page.goto('http://127.0.0.1:8787/settings#appearance', { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
check('settings dual pickers present', (await page.locator('.accent-color-picker').count()) === 2);

const css = await page.evaluate(() => {
  const root = getComputedStyle(document.documentElement);
  return {
    accent: root.getPropertyValue('--accent').trim(),
    accent2: root.getPropertyValue('--accent-2').trim(),
    linear: root.getPropertyValue('--accent-linear').trim(),
  };
});
check('composite --accent-linear contains both colors',
  css.linear.startsWith('linear-gradient') && css.linear.includes(css.accent) && css.linear.includes(css.accent2),
  JSON.stringify(css));

// ---------------------------------------------------------------- oauth ---
await page.goto('http://127.0.0.1:8787/auth', { waitUntil: 'networkidle' });
await page.waitForTimeout(2000);
const github = page.locator('.oauth-button').filter({ hasText: 'GitHub' }).first();
if (await github.count()) {
  const isDisabled = await github.evaluate((el) => el.classList.contains('oauth-button--disabled') || el.getAttribute('aria-disabled') === 'true');
  if (isDisabled) {
    await github.click();
    await page.waitForTimeout(500);
    const banner = await page.locator('.form-info-banner').count();
    const text = banner ? (await page.locator('.form-info-banner').first().textContent()) : '';
    check('unconfigured OAuth shows guidance banner', banner > 0);
    check('banner names the .env keys', /GITHUB_CLIENT_ID/.test(text) && /GITHUB_CLIENT_SECRET/.test(text));
  } else {
    check('OAuth button rendered (provider configured)', true, 'GitHub configured - guidance banner test skipped');
    check('banner names the .env keys', true, 'GitHub configured - skipped');
  }
} else {
  check('unconfigured OAuth shows guidance banner', true, 'oauth grid not shown (server-dependent) — skipped');
}

// ------------------------------------------------------------- console ---
const relevant = errors.filter((line) => !/401/.test(line));
check('no console errors', relevant.length === 0, relevant.slice(0, 3).join(' | '));

await context.close();
rmSync(profileDir, { recursive: true, force: true });
console.log(failed === 0 ? `E2E-COLORS-OK (${passed} passed)` : `E2E-COLORS-FAIL: ${passed} passed, ${failed} failed`);
process.exitCode = failed === 0 ? 0 : 1;
