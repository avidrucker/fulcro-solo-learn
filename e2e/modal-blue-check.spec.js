// @ts-check
// One-shot probe: snap the Save modal in TWO scenarios so we can see
// where the bg-blue close-gutter actually ends. Delete after B-14 lands.
const { test } = require('@playwright/test');

test.describe('B-14 visual probes', () => {
  test('short list (fits in viewport) — blue should cover all below header', async ({ page }) => {
    await page.setViewportSize({ width: 800, height: 600 });
    await page.goto('');
    await page.evaluate(() => localStorage.clear());
    await page.reload();

    const input = page.getByRole('textbox', { name: /New TODO/i });
    for (const t of ['Item A', 'Item B']) {
      await input.pressSequentially(t, { delay: 30 });
      await page.keyboard.press('Enter');
    }

    await page.getByRole('button', { name: /Import\/Export/i }).click();
    await page.waitForSelector('#save-modal-title');
    await page.screenshot({ path: 'modal-short-list.png', fullPage: true });
  });

  test('long list (overflows viewport) — does blue still reach page bottom?', async ({ page }) => {
    // Small viewport so 8 items overflow easily.
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

    // Full-page screenshot captures the entire scrollable document,
    // so we see whether blue reaches all the way to the bottom or not.
    await page.screenshot({ path: 'modal-long-list-fullpage.png', fullPage: true });

    // Also capture the scrolled-to-bottom viewport-only view —
    // matches the user's actual experience.
    await page.evaluate(() =>
      window.scrollTo(0, document.documentElement.scrollHeight)
    );
    await page.waitForTimeout(200);
    await page.screenshot({ path: 'modal-long-list-scrolled.png', fullPage: false });
  });
});
