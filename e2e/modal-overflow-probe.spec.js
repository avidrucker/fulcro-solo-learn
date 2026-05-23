// @ts-check
// Temporary probe — measures the layout chain to identify which
// ancestor of modal-shell's overlay is clamping its height. Delete
// after the B-14 fix lands.
const { test } = require('@playwright/test');

test('B-14 probe — measure ancestor chain heights', async ({ page }) => {
  await page.setViewportSize({ width: 800, height: 300 });
  await page.goto('');
  await page.evaluate(() => localStorage.clear());
  await page.reload();

  const input = page.getByRole('textbox', { name: /New TODO/i });
  for (let i = 1; i <= 8; i++) {
    await input.pressSequentially(`Item ${i}`, { delay: 30 });
    await page.keyboard.press('Enter');
    await input.evaluate((el) => el.value === '' ? null : null);
  }

  await page.getByRole('button', { name: /Import\/Export/i }).click();
  await page.waitForSelector('#save-modal-title');

  const measurements = await page.evaluate(() => {
    const m = (sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      return {
        sel,
        height: r.height,
        top: r.top + window.scrollY,
        bottom: r.bottom + window.scrollY,
        position: cs.position,
        minHeight: cs.minHeight,
        flex: `${cs.flexGrow} ${cs.flexShrink} ${cs.flexBasis}`,
      };
    };
    return {
      doc: {
        scrollHeight: document.documentElement.scrollHeight,
        viewport: window.innerHeight,
        scrollY: window.scrollY,
      },
      html: m('html'),
      body: m('body'),
      main: m('main.app'),
      header: m('header.app-header'),
      appContainer: m('section.app-container'),
      todoList: m('section.app-container > *'),
      modalDialog: m('section[role="dialog"][aria-labelledby="save-modal-title"]'),
      closeBtn: m('section[role="dialog"][aria-labelledby="save-modal-title"] > button'),
    };
  });

  console.log(JSON.stringify(measurements, null, 2));
});
