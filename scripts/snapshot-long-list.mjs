// One-off: drive our local port into a long-list state (the alphabet,
// a–z, 26 items), then optionally open a modal and snapshot. Use this
// for the Phase 7.12 visual-parity follow-up where we compare our
// save/about/help/delete modals against the og JS port with a list
// that overflows the viewport.
//
// Usage:
//   node scripts/snapshot-long-list.mjs <label> [--n N] [--dark]
//                                       [--open <button name>]
//
// Examples:
//   node scripts/snapshot-long-list.mjs phase-7.12-26-light --n 26
//   node scripts/snapshot-long-list.mjs phase-7.12-9-dark-save \
//        --n 9 --dark --open Import/Export
//
// The output filename follows the same convention as `snapshot.mjs`
// (git short hash + dirty marker + label).
import { chromium } from 'playwright';
import { execSync } from 'node:child_process';
import { resolve } from 'node:path';
import { mkdirSync } from 'node:fs';

const URL = 'http://localhost:8000';
const VIEWPORT = { width: 1280, height: 800 };

function shortHash() {
  try { return execSync('git rev-parse --short HEAD', { encoding: 'utf-8' }).trim(); }
  catch { return 'no-git'; }
}
function isDirty() {
  try { return execSync('git status --porcelain', { encoding: 'utf-8' }).trim() !== ''; }
  catch { return false; }
}

const args = process.argv.slice(2);
let label = 'long-list';
let n = 9;
let dark = false;
let openModal = null;
for (let i = 0; i < args.length; i++) {
  const a = args[i];
  if (a === '--n') n = parseInt(args[++i], 10);
  else if (a === '--dark') dark = true;
  else if (a === '--open') openModal = args[++i];
  else if (!a.startsWith('--')) label = a;
}

const hash = shortHash();
const dirty = isDirty() ? '-dirty' : '';
const outPath = resolve(`docs/snapshots/${hash}${dirty}-${label}.png`);
mkdirSync('docs/snapshots', { recursive: true });

const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ viewport: VIEWPORT })).newPage();
  page.on('pageerror', (err) => console.error('[pageerror]', err.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('[console.error]', m.text()); });

  await page.goto(URL, { waitUntil: 'networkidle' });
  await page.waitForSelector('h1', { timeout: 5000 });

  // Clear whatever default list we boot with — Delete List then Yes.
  await page.getByRole('button', { name: 'Delete List', exact: true }).click();
  await page.waitForTimeout(150);
  // Yes button in the new confirm modal — only present if list was non-empty.
  const yes = page.getByRole('button', { name: 'Yes', exact: true });
  if (await yes.count() > 0) { await yes.first().click(); await page.waitForTimeout(150); }

  // Add n items: 'a', 'b', 'c', ...
  for (let i = 0; i < n; i++) {
    const ch = String.fromCharCode(97 + i); // 97 = 'a'
    await page.getByPlaceholder('Type new task here').fill(ch);
    await page.getByRole('button', { name: 'Add Item', exact: true }).click();
    await page.waitForTimeout(50);
  }

  if (dark) {
    await page.getByRole('button', { name: 'Toggle Theme', exact: true }).click();
    await page.waitForTimeout(200);
  }

  if (openModal) {
    await page.getByRole('button', { name: openModal, exact: true }).click();
    await page.waitForTimeout(300);
  }

  await page.screenshot({ path: outPath, fullPage: true });
  console.log(`saved: ${outPath}`);
} finally {
  await browser.close();
}
