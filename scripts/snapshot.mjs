#!/usr/bin/env node
// scripts/snapshot.mjs
//
// Capture a visual snapshot of the running AutoFocus app and save it to
// `docs/snapshots/<short-hash>[-<label>].png`. Forward-only — requires
// `npx shadow-cljs watch app` (or any static server) running on
// localhost:8000 before invocation.
//
// Usage:
//   npm run snapshot                                # default view
//   npm run snapshot -- phase-6.5.2                 # add label suffix
//   npm run snapshot -- phase-6.5.4-review-modal --click "Prioritize"
//   npm run snapshot -- phase-7-persistence \
//     --type "Persisted item" --click "Add Item" --reload
//                                                   # flags execute in argv order:
//                                                   # type into the new-todo input,
//                                                   # click the Add Item button,
//                                                   # reload the page (localStorage
//                                                   # survives), then snapshot.
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

async function snapshot({ url = URL_DEFAULT, label, actions = [] } = {}) {
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

    // Sequenced actions — preserve argv order so callers can compose
    // multi-step demos like "type X, click Add Item, reload, snap".
    for (const action of actions) {
      if (action.kind === 'click') {
        // Prefer accessible-name lookup (button :title becomes the
        // accessible name) — this avoids clicking through a Tachyons
        // `clip`-hidden screen-reader span on icon buttons. Falls back
        // to visible-text matching for buttons that are text-labeled.
        const byRole = page.getByRole('button', { name: action.text, exact: true });
        if (await byRole.count() > 0) {
          await byRole.first().click();
        } else {
          await page.getByText(action.text, { exact: true }).first().click();
        }
        await page.waitForTimeout(200);
      } else if (action.kind === 'type') {
        // Hardcoded to the new-todo input — only one input in the app
        // currently. If we add a second, take a selector explicitly.
        await page.getByPlaceholder('Type new task here').fill(action.text);
        await page.waitForTimeout(100);
      } else if (action.kind === 'reload') {
        await page.reload({ waitUntil: 'networkidle' });
        await page.waitForSelector(APP_SELECTOR, { timeout: 5000 });
        await page.waitForTimeout(250);
      }
    }

    await page.screenshot({ path: outPath, fullPage: true });

    console.log(`saved: ${outPath}`);
  } finally {
    await browser.close();
  }
}

// Argv parsing: first non-flag positional is the label. Flags run in
// the order given so multi-step demos compose naturally.
//   --click <text> — click an element with the given visible text
//   --type  <text> — type into the new-todo input (placeholder match)
//   --reload       — reload the page (no value)
const args = process.argv.slice(2);
let label;
const actions = [];
for (let i = 0; i < args.length; i++) {
  const a = args[i];
  if (a === '--click') {
    actions.push({ kind: 'click', text: args[++i] });
  } else if (a === '--type') {
    actions.push({ kind: 'type', text: args[++i] });
  } else if (a === '--reload') {
    actions.push({ kind: 'reload' });
  } else if (!label) {
    label = a;
  }
}
await snapshot({ label, actions });
