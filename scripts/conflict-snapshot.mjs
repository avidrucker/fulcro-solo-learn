#!/usr/bin/env node
// scripts/conflict-snapshot.mjs
//
// One-off diagnostic for B-10 (conflict modal layout). Loads list-1
// (item "a" ready) to seed localStorage, then loads list-2 (item "a"
// done) so the conflict modal triggers. Snapshots both the OG ReactJS
// app and the Fulcro port for visual comparison. Safe to delete once
// B-10 closes.

import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const LIST_READY = 'JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA==';
const LIST_DONE  = 'JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJkb25lJTIyJTdEJTVE';

const OG_BASE      = 'https://avidrucker.github.io/pwa-autofocus-app';
const FULCRO_BASE  = 'http://localhost:8000';

const OUT_DIR = resolve('docs/snapshots/comparison');
mkdirSync(OUT_DIR, { recursive: true });

const VIEWPORT = { width: 800, height: 700 };

async function capture({ base, label }) {
  const browser = await chromium.launch();
  try {
    // Persist localStorage across the two page-loads so the conflict
    // triggers — Playwright keeps localStorage scoped to the
    // BrowserContext, so we use one context for both navigations.
    const context = await browser.newContext({ viewport: VIEWPORT });
    const page = await context.newPage();
    page.on('pageerror', (e) => console.error(`[${label} pageerror]`, e.message));

    // Step 1: load LIST_READY. This persists item "a" ready to
    // localStorage and renders normally (no conflict yet — there's
    // nothing previously in localStorage to disagree).
    await page.goto(`${base}/?list=${LIST_READY}`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForSelector('h1', { timeout: 5000 });
    await page.waitForTimeout(800);  // let persistence watch fire

    // Step 2: navigate to LIST_DONE. The URL says "a done"; localStorage
    // says "a ready". Conflict modal should open on render.
    await page.goto(`${base}/?list=${LIST_DONE}`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForSelector('h1', { timeout: 5000 });
    await page.waitForTimeout(800);

    const outPath = resolve(OUT_DIR, `${label}-conflict-modal.png`);
    await page.screenshot({ path: outPath, fullPage: true });
    console.log(`saved: ${outPath}`);
  } finally {
    await browser.close();
  }
}

await capture({ base: OG_BASE,     label: 'og' });
await capture({ base: FULCRO_BASE, label: 'fulcro' });
