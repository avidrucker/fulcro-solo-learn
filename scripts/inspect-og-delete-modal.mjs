// One-off probe: open the og JS port's delete-confirm modal and dump
// each interactive element's outerHTML + computed styles. Used during
// the Phase 7.12 visual-comparison pass.
import { chromium } from 'playwright';

const URL = 'https://avidrucker.github.io/pwa-autofocus-app/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA==';

const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  await page.goto(URL, { waitUntil: 'networkidle' });
  await page.waitForSelector('h1', { timeout: 5000 });
  await page.getByRole('button', { name: 'Delete List', exact: true }).click();
  await page.waitForTimeout(300);

  const dump = await page.evaluate(() => {
    const yes = document.evaluate("//button[normalize-space(.)='Yes']", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
    const no  = document.evaluate("//button[normalize-space(.)='No']",  document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
    // Walk up to find the modal-shell outer section.
    let modal = yes;
    while (modal && modal.tagName !== 'SECTION') modal = modal.parentElement;
    // Now climb to the OUTER modal section (which has absolute + theme bg).
    let outer = modal;
    while (outer.parentElement && outer.parentElement.tagName === 'SECTION') outer = outer.parentElement;
    const grab = (el, deep=false) => {
      if (!el) return null;
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return {
        tag: el.tagName,
        className: el.className,
        outerHTML: deep ? el.outerHTML : el.outerHTML.slice(0, 400),
        bbox: { x: r.x, y: r.y, w: r.width, h: r.height },
        bg: cs.backgroundColor,
        color: cs.color,
        width: cs.width,
        height: cs.height,
        padding: cs.padding,
        position: cs.position
      };
    };
    return {
      outerSection: grab(outer, true),
      innerSection: grab(modal),
      yesBtn: grab(yes),
      noBtn: grab(no)
    };
  });
  console.log(JSON.stringify(dump, null, 2));
} finally {
  await browser.close();
}
