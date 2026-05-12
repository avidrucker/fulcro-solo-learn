#!/usr/bin/env node
// scripts/snapshot.mjs
//
// Capture a visual snapshot of the running AutoFocus app and save it to
// `docs/snapshots/<short-hash>[-<label>].png`. Forward-only — requires
// `npx shadow-cljs watch app` (or any static server) running on
// localhost:8000 before invocation.
//
// Usage:
//   npm run snapshot              # bare commit-hash filename
//   npm run snapshot -- phase-6.5.2   # appends "-phase-6.5.2"
//
// Snapshots are full-page PNGs at 1280x800 viewport. Saved to
// docs/snapshots/ which is committed (PNGs of a simple UI stay small;
// retroactive viewing of "how it looked at commit X" is easier when the
// images live alongside the code).

import { chromium } from 'playwright';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const URL_DEFAULT = 'http://localhost:8000';
const VIEWPORT = { width: 1280, height: 800 };
const APP_SELECTOR = 'h1'; // the AutoFocus heading — first thing to render

function shortHash() {
  try {
    return execSync('git rev-parse --short HEAD', { encoding: 'utf-8' }).trim();
  } catch {
    return 'no-git';
  }
}

function isDirty() {
  try {
    return execSync('git status --porcelain', { encoding: 'utf-8' }).trim() !== '';
  } catch {
    return false;
  }
}

async function snapshot({ url = URL_DEFAULT, label } = {}) {
  const hash = shortHash();
  const dirty = isDirty() ? '-dirty' : '';
  const suffix = label ? `-${label}` : '';
  const outPath = resolve(`docs/snapshots/${hash}${dirty}${suffix}.png`);

  mkdirSync(dirname(outPath), { recursive: true });

  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: VIEWPORT });
    page.on('pageerror', (err) => console.error('[browser pageerror]', err.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') console.error('[browser console.error]', msg.text());
    });

    await page.goto(url, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForSelector(APP_SELECTOR, { timeout: 5000 });
    // Belt-and-suspenders: give Fulcro one more tick to settle layout.
    await page.waitForTimeout(250);
    await page.screenshot({ path: outPath, fullPage: true });

    console.log(`saved: ${outPath}`);
  } finally {
    await browser.close();
  }
}

const label = process.argv[2]; // optional: e.g. "phase-6.5.2"
await snapshot({ label });
