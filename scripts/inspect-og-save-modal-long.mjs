// Probe the og JS port's save (import/export) modal with a 9-item list
// so the content extends past the viewport. Captures the modal outer
// section's computed height + the page's full scroll height so we can
// see whether the overlay covers the full document or only the
// viewport. Used during the Phase 7.12 visual-parity follow-up.
import { chromium } from 'playwright';

const URL = 'https://avidrucker.github.io/pwa-autofocus-app/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMSUyQyUyMnRleHQlMjIlM0ElMjJiJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0EyJTJDJTIydGV4dCUyMiUzQSUyMmMlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTMlMkMlMjJ0ZXh0JTIyJTNBJTIyZCUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNCUyQyUyMnRleHQlMjIlM0ElMjJlJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E1JTJDJTIydGV4dCUyMiUzQSUyMmYlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTYlMkMlMjJ0ZXh0JTIyJTNBJTIyZyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNyUyQyUyMnRleHQlMjIlM0ElMjJoJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E4JTJDJTIydGV4dCUyMiUzQSUyMmklMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlNUQ=';

const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  await page.goto(URL, { waitUntil: 'networkidle' });
  await page.waitForSelector('h1', { timeout: 5000 });
  // Open save modal (the disk icon button).
  await page.getByRole('button', { name: 'Import/Export', exact: true }).click();
  await page.waitForTimeout(300);

  const dump = await page.evaluate(() => {
    const grab = (el) => {
      if (!el) return null;
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return {
        tag: el.tagName,
        className: el.className,
        bbox: { x: r.x, y: r.y, w: r.width, h: r.height },
        bg: cs.backgroundColor,
        height: cs.height,
        minHeight: cs.minHeight,
        position: cs.position,
        top: cs.top
      };
    };
    // Find the save modal — it's the section with `bg-white-90` or
    // `bg-black-90` (depending on theme).
    const overlays = Array.from(document.querySelectorAll('section'))
      .filter(s => /bg-(white|black)-90/.test(s.className));
    const modalOuter = overlays[overlays.length - 1]; // assume save modal is last
    return {
      pageHeight: document.documentElement.scrollHeight,
      viewportHeight: window.innerHeight,
      bodyHeight: document.body.scrollHeight,
      mainHeight: document.querySelector('main').scrollHeight,
      modalOuter: grab(modalOuter),
      html: grab(document.documentElement),
      body: grab(document.body),
      root: grab(document.getElementById('root')),
      main: grab(document.querySelector('main')),
    };
  });
  console.log(JSON.stringify(dump, null, 2));
} finally {
  await browser.close();
}
