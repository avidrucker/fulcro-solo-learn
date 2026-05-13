// One-off probe that mirrors `inspect-og-css.mjs` but against our
// localhost dev server. Used during the Phase 7.12 visual-comparison
// work. Not part of the regular build.
import { chromium } from 'playwright';

const URL = 'http://localhost:8000';

const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  await page.goto(URL, { waitUntil: 'networkidle' });
  await page.waitForSelector('h1', { timeout: 5000 });
  // Toggle to dark to match og's default.
  await page.getByRole('button', { name: 'Toggle Theme', exact: true }).click();
  await page.waitForTimeout(300);

  const computed = await page.evaluate(() => {
    const grab = (el) => {
      if (!el) return null;
      const cs = getComputedStyle(el);
      return {
        tag: el.tagName,
        id: el.id || null,
        className: el.className || null,
        bgColor: cs.backgroundColor,
        color: cs.color,
        height: cs.height,
        minHeight: cs.minHeight,
        display: cs.display,
        flexDirection: cs.flexDirection,
        margin: cs.margin,
        padding: cs.padding,
        boxSizing: cs.boxSizing,
        fontFamily: cs.fontFamily,
        boundingHeight: el.getBoundingClientRect().height,
        boundingWidth: el.getBoundingClientRect().width
      };
    };
    return {
      html: grab(document.documentElement),
      body: grab(document.body),
      app: grab(document.getElementById('app')),
      main: grab(document.querySelector('main')),
    };
  });
  console.log('COMPUTED STYLES (local, dark mode):');
  console.log(JSON.stringify(computed, null, 2));
} finally {
  await browser.close();
}
