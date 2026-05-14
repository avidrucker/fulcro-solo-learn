// Quick probe: how tall is <main>, and does it have bottom padding?
// Useful while iterating on the B-6 fix.
import { chromium } from 'playwright';

const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  await page.goto('http://localhost:8000/index.html', { waitUntil: 'networkidle' });
  await page.waitForSelector('h1', { timeout: 5000 });

  // Add 26 items
  for (let i = 0; i < 26; i++) {
    const ch = String.fromCharCode(97 + i);
    await page.getByPlaceholder('Type new task here').fill(ch);
    await page.getByRole('button', { name: 'Add Item', exact: true }).click();
    await page.waitForTimeout(40);
  }
  await page.getByRole('button', { name: 'Import/Export', exact: true }).click();
  await page.waitForTimeout(400);

  const heights = await page.evaluate(() => {
    const m = document.querySelector('main');
    const cs = getComputedStyle(m);
    return {
      scrollHeight:     document.documentElement.scrollHeight,
      bodyScrollHeight: document.body.scrollHeight,
      mainOffsetHeight: m.offsetHeight,
      mainScrollHeight: m.scrollHeight,
      mainPaddingBottom: cs.paddingBottom,
      mainClassName:    m.className
    };
  });
  console.log(JSON.stringify(heights, null, 2));
} finally {
  await browser.close();
}
