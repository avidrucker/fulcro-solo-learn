// Quick smoke test for the Phase 7.19 PWA setup. Loads the local
// dev server in Chromium, waits for the service worker to register,
// reports its scope + which assets it cached. Skipped in CI; one-off.
import { chromium } from 'playwright';

const browser = await chromium.launch();
try {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const consoleMsgs = [];
  page.on('console', (m) => consoleMsgs.push(`[${m.type()}] ${m.text()}`));
  page.on('pageerror', (e) => consoleMsgs.push(`[pageerror] ${e.message}`));

  await page.goto('http://localhost:8000', { waitUntil: 'networkidle' });

  // Give the SW a moment to register and cache.
  await page.waitForTimeout(2000);

  const swState = await page.evaluate(async () => {
    if (!('serviceWorker' in navigator)) return { supported: false };
    const reg = await navigator.serviceWorker.getRegistration();
    const cacheNames = await caches.keys();
    const cacheContents = {};
    for (const name of cacheNames) {
      const cache = await caches.open(name);
      const keys = await cache.keys();
      cacheContents[name] = keys.map((r) => r.url);
    }
    return {
      supported: true,
      scope: reg?.scope ?? null,
      active: !!reg?.active,
      cacheNames,
      cacheContents
    };
  });

  console.log('=== Service worker state ===');
  console.log(JSON.stringify(swState, null, 2));
  console.log('\n=== Browser console ===');
  consoleMsgs.forEach((m) => console.log(m));
} finally {
  await browser.close();
}
