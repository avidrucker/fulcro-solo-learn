// @ts-check
// B-14 (open) — Modal close-gutter button doesn't reach page bottom
// when content overflows the viewport.
//
// Reported symptom (from `docs/bugs.md`): user has enough items that
// the page scrolls past the viewport. They open a dismissible modal,
// scroll down, then click in the lower portion of the page expecting
// the click-anywhere-to-close affordance to fire. Click does NOT
// close the modal — even though the visual overlay appears to cover
// that area.
//
// Mechanism (per diagnose-skill investigation): `modal-shell`'s
// outer <section> is `position: absolute top-0 bottom-0 left-0
// right-0` anchored to `.app-container`. `app-container` is a
// `flex-1` child of `<main class="min-vh-100 flex flex-column">`;
// flex-basis math caps its rendered height at roughly viewport
// height even when items overflow it. The overlay (and the close-
// gutter button inside it) inherit that cap and never reach the
// page bottom in document coordinates. Items render past the
// overlay's bottom edge because `overflow: visible` is the default.
//
// This spec asserts the user-facing BEHAVIOR (clicking near the
// bottom of a scrolled viewport closes the modal), not a specific
// positioning strategy — so it validates any fix that restores the
// "click anywhere visible to close" affordance.
//
// First pass: ONE modal (Save / Import-Export). Once the fix lands,
// the matrix expands to Info, Settings, and Delete-Confirm (the
// other three dismissible modals — review, list-conflict, and
// locale-conflict aren't dismissible via the gutter).
//
// Prereq: `npx shadow-cljs watch app` running on :8000.

const { test, expect } = require('@playwright/test');

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Small viewport forces page overflow with a manageable number of
 * items. Header + new-todo input + actions ≈ 160px of chrome; the
 * remaining ~140px holds ~3 todo rows. 8 items overflows comfortably
 * across both themes and all four locales' button label widths.
 */
async function setOverflowViewport(page) {
  await page.setViewportSize({ width: 800, height: 300 });
}

/**
 * Seed the list with `n` items via the real UI flow so localStorage,
 * url-sync, and Fulcro state all stay coherent. `pressSequentially`
 * with a short per-char delay is required — `fill()` skips the
 * React onChange that Fulcro's `m/set-string!` listens for.
 */
async function seedItems(page, n) {
  const input = page.getByRole('textbox', { name: /New TODO/i });
  for (let i = 1; i <= n; i++) {
    const text = `Item ${i}`;
    await input.pressSequentially(text, { delay: 30 });
    await expect(input).toHaveValue(text);
    await page.keyboard.press('Enter');
    await expect(
      page.getByRole('listitem').filter({ hasText: text })
    ).toBeVisible();
    await expect(input).toHaveValue('');
  }
}

// ---------------------------------------------------------------------------
// B-14 — Save (Import/Export) modal
// ---------------------------------------------------------------------------

test.describe('B-14 — modal close-gutter is reachable across full overflow', () => {
  test('Save modal: scroll past viewport, click near bottom → modal closes', async ({ page }) => {
    await setOverflowViewport(page);
    await page.goto('');
    await page.evaluate(() => localStorage.clear());
    await page.reload();

    await seedItems(page, 8);

    // Precondition: page content overflows the viewport. Guard against
    // a future viewport / item-rendering change that would trivially
    // green this test for the wrong reason.
    const before = await page.evaluate(() => ({
      scrollHeight: document.documentElement.scrollHeight,
      viewportHeight: window.innerHeight,
    }));
    expect(before.scrollHeight).toBeGreaterThan(before.viewportHeight);

    // Open Save (Import/Export) modal.
    await page.getByRole('button', { name: /Import\/Export/i }).click();
    const dialog = page.locator(
      'section[role="dialog"][aria-labelledby="save-modal-title"]'
    );
    await expect(dialog).toBeVisible();
    await expect(page.locator('#save-modal-title')).toBeFocused();

    // Scroll the page to its bottom. After this, the visible viewport
    // shows the LOWER portion of the document — the region where the
    // user reports clicks falling through.
    await page.evaluate(() =>
      window.scrollTo(0, document.documentElement.scrollHeight)
    );
    // Settle scroll + any layout reflow.
    await page.waitForFunction(() => {
      const docBottomReached =
        window.scrollY + window.innerHeight >=
        document.documentElement.scrollHeight - 1;
      return docBottomReached;
    });

    // Click near the bottom of the visible viewport, in the LEFT
    // margin (x=80) — outside the centered `measure-narrow` inner
    // content section so the click lands on the close-gutter button
    // (z-0), not the inner modal content (z-1). This is exactly the
    // gesture B-14 describes failing: clicking in the lower-outer
    // portion of the page expecting the click-anywhere-to-close
    // affordance to fire. With the bug present the close button
    // clips above this point and the click falls through to
    // underlying page content; with the fix it hits the button
    // and dismisses the modal.
    const viewportHeight = await page.evaluate(() => window.innerHeight);
    await page.mouse.click(80, viewportHeight - 10);

    // Modal should be gone.
    await expect(dialog).toHaveCount(0);
  });
});
