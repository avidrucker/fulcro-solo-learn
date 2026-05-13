// One-off probe used during the Phase 7.12 visual-comparison work to
// dump the og JS port's CSS requests and computed styles for the root
// stack (html/body/#root/main). Lives in /scripts so the project's
// node_modules is on path. Not part of the regular build.
import { chromium } from 'playwright';

const URL = 'https://avidrucker.github.io/pwa-autofocus-app/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA==';

const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  const cssReqs = [];
  page.on('response', (r) => {
    const u = r.url();
    const ct = r.headers()['content-type'] || '';
    if (/\.css(\?|$)/i.test(u) || ct.includes('text/css')) {
      cssReqs.push({ url: u, status: r.status() });
    }
  });
  await page.goto(URL, { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);

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
      root: grab(document.getElementById('root')),
      main: grab(document.querySelector('main')),
    };
  });
  console.log('CSS REQUESTS:');
  console.log(JSON.stringify(cssReqs, null, 2));
  console.log('\nCOMPUTED STYLES:');
  console.log(JSON.stringify(computed, null, 2));
} finally {
  await browser.close();
}
