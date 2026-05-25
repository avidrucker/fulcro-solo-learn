#!/usr/bin/env node
// One-shot diagnostic: open Settings, expand Debug section, click the
// rainbow checkbox, then report what link tags + flag values exist
// after each step. Used to validate whether the rainbow toggle is
// actually independent of depth (user-reported potential bug).

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

  async function snapshotState (label) {
    const linkTags = await page.locator('link[id^="debug-css-"]').all();
    const links = [];
    for (const l of linkTags) {
      links.push({
        id:   await l.getAttribute('id'),
        href: await l.getAttribute('href'),
      });
    }
    const rainbow = await page.locator('#settings-debug-rainbow').count() > 0
      ? await page.locator('#settings-debug-rainbow').first().isChecked()
      : null;
    const depth = await page.locator('#settings-debug-depth').count() > 0
      ? await page.locator('#settings-debug-depth').first().isChecked()
      : null;
    console.log(`\n=== ${label} ===`);
    console.log(`  rainbow checkbox: ${rainbow}`);
    console.log(`  depth checkbox:   ${depth}`);
    console.log(`  debug-css link tags: ${JSON.stringify(links, null, 2)}`);
  }

  await snapshotState('initial');

  await page.getByRole('button', { name: 'Settings', exact: true }).first().click();
  await page.waitForTimeout(200);
  await snapshotState('after opening Settings');

  await page.locator('#settings-debug-toggle').click();
  await page.waitForTimeout(200);
  await snapshotState('after expanding Debug section');

  await page.locator('#settings-debug-rainbow').click();
  await page.waitForTimeout(300);
  await snapshotState('after clicking RAINBOW checkbox');

  await page.locator('#settings-debug-rainbow').click();
  await page.waitForTimeout(300);
  await snapshotState('after UNCHECKING rainbow');

  await page.locator('#settings-debug-depth').click();
  await page.waitForTimeout(300);
  await snapshotState('after clicking DEPTH checkbox');
} finally {
  await browser.close();
}
