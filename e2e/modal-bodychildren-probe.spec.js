// @ts-check
// One-shot probe: enumerate every direct child of <body> and measure
// each. Goal: find what's contributing to scrollHeight beyond html.
const { test } = require('@playwright/test');

test('list every direct child of body with its geometry', async ({ page }) => {
  await page.setViewportSize({ width: 800, height: 300 });
  await page.goto('');
  await page.evaluate(() => localStorage.clear());
  await page.reload();

  const input = page.getByRole('textbox', { name: /New TODO/i });
  for (let i = 1; i <= 8; i++) {
    await input.pressSequentially(`Item ${i}`, { delay: 30 });
    await page.keyboard.press('Enter');
  }

  await page.getByRole('button', { name: /Import\/Export/i }).click();
  await page.waitForSelector('#save-modal-title');

  const data = await page.evaluate(() => {
    const out = [];
    out.push({
      _: 'doc',
      scrollHeight: document.documentElement.scrollHeight,
      htmlHeight: document.documentElement.getBoundingClientRect().height,
      bodyHeight: document.body.getBoundingClientRect().height,
      bodyScrollHeight: document.body.scrollHeight,
    });
    document.body.childNodes.forEach((node) => {
      if (node.nodeType !== 1) return; // skip text/comment
      const r = node.getBoundingClientRect();
      const cs = getComputedStyle(node);
      out.push({
        tag: node.tagName,
        id: node.id || null,
        classes: node.className || null,
        position: cs.position,
        height: r.height,
        top: r.top + window.scrollY,
        bottom: r.bottom + window.scrollY,
      });
    });
    return out;
  });
  console.log(JSON.stringify(data, null, 2));
});
