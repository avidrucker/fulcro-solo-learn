#!/usr/bin/env node
// Probe the user-reported scenario: rainbow first (it turns ON), THEN
// click depth. Expected: both flags true, both link tags present.
// User reports: depth click "does nothing".

import { chromium } from 'playwright';

const URL = 'http://localhost:8000';
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  page.on('pageerror', (err) => console.error('[pageerror]', err.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error') console.error('[console.error]', msg.text());
  });

  await page.goto(URL, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForSelector('h1', { timeout: 5000 });
  await page.waitForTimeout(300);

  async function snap (label) {
    const linkTags = await page.locator('link[id^="debug-css-"]').all();
    const links = [];
    for (const l of linkTags) {
      links.push({ id: await l.getAttribute('id'), href: await l.getAttribute('href') });
    }
    const rainbow = (await page.locator('#settings-debug-rainbow').count()) > 0
      ? await page.locator('#settings-debug-rainbow').first().isChecked()
      : null;
    const depth = (await page.locator('#settings-debug-depth').count()) > 0
      ? await page.locator('#settings-debug-depth').first().isChecked()
      : null;
    console.log(`\n=== ${label} ===`);
    console.log(`  rainbow:${rainbow}  depth:${depth}  links:${JSON.stringify(links)}`);
  }

  await page.getByRole('button', { name: 'Settings', exact: true }).first().click();
  await page.waitForTimeout(200);
  await page.locator('#settings-debug-toggle').click();
  await page.waitForTimeout(200);
  await snap('expanded, both off');

  await page.locator('#settings-debug-rainbow').click();
  await page.waitForTimeout(300);
  await snap('rainbow clicked ON');

  // User's scenario: depth click WITH rainbow already on
  await page.locator('#settings-debug-depth').click();
  await page.waitForTimeout(300);
  await snap('THEN depth clicked');

  // And reverse: depth off again
  await page.locator('#settings-debug-depth').click();
  await page.waitForTimeout(300);
  await snap('depth unchecked');
} finally {
  await browser.close();
}
