#!/usr/bin/env node
// scripts/inspect-heights.mjs
//
// One-off diagnostic: open the Fulcro port at 200% zoom with a 10-item
// list and the import/export modal open, then dump computed heights
// for the elements that govern overlay extent. Used while debugging
// the 12.5c modal-overlay-doesn't-reach-bottom issue.

import { chromium } from 'playwright';

const URL = 'http://localhost:8000/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJkb25lJTIyJTdEJTJDJTdCJTIyaWQlMjIlM0ExJTJDJTIydGV4dCUyMiUzQSUyMmIlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJjYW5jZWxsZWQlMjIlMkMlMjJ3YXMlMjIlM0ElMjJyZWFkeSUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMiUyQyUyMnRleHQlMjIlM0ElMjJjJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIyY2FuY2VsbGVkJTIyJTJDJTIyd2FzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0EzJTJDJTIydGV4dCUyMiUzQSUyMmQlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNCUyQyUyMnRleHQlMjIlM0ElMjJlJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E1JTJDJTIydGV4dCUyMiUzQSUyMmYlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTYlMkMlMjJ0ZXh0JTIyJTNBJTIyZyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNyUyQyUyMnRleHQlMjIlM0ElMjJoJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E4JTJDJTIydGV4dCUyMiUzQSUyMmklMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTklMkMlMjJ0ZXh0JTIyJTNBJTIyaiUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCU1RA==';

const browser = await chromium.launch();
// Simulate browser-level 200% zoom by SHRINKING the layout viewport —
// real Ctrl-+ in a browser tells the page it has a smaller layout
// viewport (e.g. 640×400), which is what triggers content overflow.
// `document.body.style.zoom` only scales the visual output and doesn't
// reflow content, so it doesn't reproduce the overflow case.
const page = await browser.newPage({ viewport: { width: 640, height: 400 } });
await page.goto(URL, { waitUntil: 'networkidle' });
await page.waitForSelector('h1');
await page.waitForTimeout(400);
await page.getByRole('button', { name: /import\/?export/i }).first().click();
await page.waitForTimeout(400);

const dims = await page.evaluate(() => {
  const fmt = (el) => {
    if (!el) return null;
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    return {
      tag: el.tagName.toLowerCase(),
      class: el.className,
      bbox_height: Math.round(r.height),
      offsetHeight: el.offsetHeight,
      scrollHeight: el.scrollHeight,
      computed_height: cs.height,
      computed_min_height: cs.minHeight,
      position: cs.position,
    };
  };
  return {
    html: fmt(document.documentElement),
    body: fmt(document.body),
    app: fmt(document.querySelector('#app')),
    main: fmt(document.querySelector('main.app')),
    appContainer: fmt(document.querySelector('.app-container')),
    overlay: fmt(document.querySelector('.app-container > section.absolute')),
    viewport_height: window.innerHeight,
    document_height: document.documentElement.scrollHeight,
  };
});
console.log(JSON.stringify(dims, null, 2));
await browser.close();
