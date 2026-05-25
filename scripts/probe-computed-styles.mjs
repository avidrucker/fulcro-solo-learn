#!/usr/bin/env node
// Probe getComputedStyle on key elements with rainbow ON, then depth ON
// added on top. If depth is being overridden, the box-shadow /
// background-color values will tell us which sheet is winning.

import { chromium } from 'playwright';

const URL = 'http://localhost:8000';
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  await page.goto(URL, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForSelector('h1', { timeout: 5000 });
  await page.waitForTimeout(300);

  async function readStyles (label) {
    const data = await page.evaluate(() => {
      const targets = ['body', 'main', 'header', 'section', 'h1'];
      const out = {};
      for (const sel of targets) {
        const el = document.querySelector(sel);
        if (!el) continue;
        const s = getComputedStyle(el);
        out[sel] = {
          outline: s.outline,
          boxShadow: s.boxShadow,
          backgroundColor: s.backgroundColor,
        };
      }
      // Also report which stylesheets are loaded
      const sheets = [...document.querySelectorAll('link[rel="stylesheet"]')]
        .map((l) => ({ id: l.id || '(no id)', href: l.getAttribute('href') }));
      return { computed: out, sheets };
    });
    console.log(`\n=== ${label} ===`);
    console.log('stylesheets:', JSON.stringify(data.sheets, null, 2));
    console.log('computed:', JSON.stringify(data.computed, null, 2));
  }

  await page.getByRole('button', { name: 'Settings', exact: true }).first().click();
  await page.waitForTimeout(200);
  await page.locator('#settings-debug-toggle').click();
  await page.waitForTimeout(200);

  await readStyles('baseline (nothing toggled)');

  await page.locator('#settings-debug-rainbow').click();
  await page.waitForTimeout(300);
  await readStyles('rainbow ON');

  await page.locator('#settings-debug-depth').click();
  await page.waitForTimeout(300);
  await readStyles('rainbow ON + depth ON');
} finally {
  await browser.close();
}
