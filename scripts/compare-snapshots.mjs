#!/usr/bin/env node
// scripts/compare-snapshots.mjs
//
// One-off diagnostic: snapshot both the OG ReactJS app and the local
// Fulcro port at 200% zoom with a 10-item list and the import/export
// modal open, in light AND dark mode. Output lands in
// docs/snapshots/comparison/. Run with:
//
//   node scripts/compare-snapshots.mjs
//
// Used for the 12.5c modal-overlay + textarea-bg diagnosis. Safe to
// delete once those bugs are filed and fixed.

import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const OG_URL = 'https://avidrucker.github.io/pwa-autofocus-app/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMSUyQyUyMnRleHQlMjIlM0ElMjJiJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0EyJTJDJTIydGV4dCUyMiUzQSUyMmMlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTMlMkMlMjJ0ZXh0JTIyJTNBJTIyZCUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNCUyQyUyMnRleHQlMjIlM0ElMjJlJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E1JTJDJTIydGV4dCUyMiUzQSUyMmYlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTYlMkMlMjJ0ZXh0JTIyJTNBJTIyZyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNyUyQyUyMnRleHQlMjIlM0ElMjJoJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E4JTJDJTIydGV4dCUyMiUzQSUyMmklMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTklMkMlMjJ0ZXh0JTIyJTNBJTIyaiUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCU1RA==';

const FULCRO_URL = 'http://localhost:8000/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJkb25lJTIyJTdEJTJDJTdCJTIyaWQlMjIlM0ExJTJDJTIydGV4dCUyMiUzQSUyMmIlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJjYW5jZWxsZWQlMjIlMkMlMjJ3YXMlMjIlM0ElMjJyZWFkeSUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMiUyQyUyMnRleHQlMjIlM0ElMjJjJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIyY2FuY2VsbGVkJTIyJTJDJTIyd2FzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0EzJTJDJTIydGV4dCUyMiUzQSUyMmQlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNCUyQyUyMnRleHQlMjIlM0ElMjJlJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E1JTJDJTIydGV4dCUyMiUzQSUyMmYlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTYlMkMlMjJ0ZXh0JTIyJTNBJTIyZyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNyUyQyUyMnRleHQlMjIlM0ElMjJoJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E4JTJDJTIydGV4dCUyMiUzQSUyMmklMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTklMkMlMjJ0ZXh0JTIyJTNBJTIyaiUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCU1RA==';

const OUT_DIR = resolve('docs/snapshots/comparison');
mkdirSync(OUT_DIR, { recursive: true });

// 200% zoom simulated by halving the viewport width while keeping the
// CSS-pixel layout intact. Playwright's `deviceScaleFactor` doesn't
// affect layout; setting `document.body.style.zoom` does.
const VIEWPORT = { width: 640, height: 800 };

async function capture({ url, label, dark }) {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: VIEWPORT });
    page.on('pageerror', (e) => console.error('[pageerror]', e.message));

    await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForSelector('h1', { timeout: 5000 });
    await page.waitForTimeout(500);

    if (dark) {
      // Both ports use a lightbulb icon to toggle theme — accessible
      // name 'Toggle Theme'.
      const toggle = page.getByRole('button', { name: /toggle theme/i });
      if (await toggle.count() > 0) {
        await toggle.first().click();
        await page.waitForTimeout(300);
      }
    }

    // Set zoom to 200%. `document.body.style.zoom` is a Chromium-only
    // CSS extension but works for our visual-diagnosis purpose.
    await page.evaluate(() => { document.body.style.zoom = '2.0'; });
    await page.waitForTimeout(300);

    // Open the Import/Export modal.
    const importBtn = page.getByRole('button', { name: /import\/?export/i });
    if (await importBtn.count() > 0) {
      await importBtn.first().click();
      await page.waitForTimeout(400);
    } else {
      console.warn(`[${label}] no Import/Export button found`);
    }

    const outPath = resolve(OUT_DIR, `${label}.png`);
    await page.screenshot({ path: outPath, fullPage: true });
    console.log(`saved: ${outPath}`);
  } finally {
    await browser.close();
  }
}

for (const dark of [false, true]) {
  const suffix = dark ? 'dark' : 'light';
  await capture({ url: OG_URL,     label: `og-${suffix}-save-modal-200pct`,     dark });
  await capture({ url: FULCRO_URL, label: `fulcro-${suffix}-save-modal-200pct`, dark });
}
