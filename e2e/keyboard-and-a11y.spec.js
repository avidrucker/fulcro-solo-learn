// @ts-check
// Phase 20b — minimal Playwright + axe-core spec for the AutoFocus
// Fulcro port. Covers keyboard navigation + automated a11y rule
// checks for things only a real browser can verify. Companion to
// the JVM-side fulcro-spec suite (which already covers the data
// plane). See docs/e2e_test_research.md for the strategy.
//
// Prereq: `npx shadow-cljs watch app` running on :8000 in another
// terminal. See README.md.
//
// Each test block is independent. The page reloads fresh for each
// (Playwright default) so localStorage / state doesn't bleed.

const { test, expect } = require('@playwright/test');
const AxeBuilder = require('@axe-core/playwright').default;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Return the computed `top` CSS value of the skip link. Used to assert the
 * link is off-screen by default (`-100px`) and slid into view on focus
 * (`8px` = `0.5rem`).
 */
async function skipLinkComputedTop(page) {
  return page.evaluate(() => {
    const el = document.querySelector('.skip-link');
    return el ? getComputedStyle(el).top : null;
  });
}

// ---------------------------------------------------------------------------
// 19o — skip link
// ---------------------------------------------------------------------------

test.describe('19o — skip link', () => {
  test('skip link is off-screen by default, slides in on focus', async ({ page }) => {
    await page.goto('');
    // Off-screen at rest (top: -100px from app.css).
    expect(await skipLinkComputedTop(page)).toBe('-100px');

    // One Tab focuses the skip link (it's the first focusable element).
    await page.keyboard.press('Tab');
    const skipLink = page.getByRole('link', { name: /Skip to main content/i });
    await expect(skipLink).toBeFocused();

    // The `:focus` rule moves it to `top: 0.5rem` = `8px`.
    expect(await skipLinkComputedTop(page)).toBe('8px');
  });

  test('pressing Enter on the skip link moves focus to main content', async ({ page }) => {
    await page.goto('');
    await page.keyboard.press('Tab'); // focus skip link
    await page.keyboard.press('Enter');

    // Activating the link sets the URL fragment to #main-content; the
    // target <section id="main-content" tabindex="-1"> receives focus.
    const mainContent = page.locator('#main-content');
    await expect(mainContent).toBeFocused();
  });
});

// ---------------------------------------------------------------------------
// 19i — keyboard navigation through the header
// ---------------------------------------------------------------------------

test.describe('19i — header tab order', () => {
  test('Tab cycles skip link → header buttons in visual order', async ({ page }) => {
    await page.goto('');

    // 1. Skip link
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: /Skip to main content/i })).toBeFocused();

    // 2. Save / Import-Export
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: /Import\/Export/i })).toBeFocused();

    // 3. Info
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: /^Info$/i })).toBeFocused();

    // 4. Settings
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: /^Settings$/i })).toBeFocused();

    // 5. Theme toggle — direction-aware name from Phase 19m. Light mode
    // is the default initial-state value, so the toggle offers "Switch
    // to dark mode".
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: /Switch to dark mode/i })).toBeFocused();
  });
});

// ---------------------------------------------------------------------------
// 19g + 19h — modal focus management + Escape-to-close
//
// Four dismissible modals: info, settings, save, delete-confirm.
// Each test opens the modal, asserts focus moved inside, presses Escape,
// asserts modal closed AND focus restored.
// ---------------------------------------------------------------------------

test.describe('19g + 19h — dismissible modal focus + Escape', () => {
  /**
   * Shared pattern: click the trigger button → focus moves to the modal
   * heading → press Escape → modal closes → focus restores to the
   * trigger.
   */
  async function modalOpenCloseCycle(page, {
    triggerName, headingId, modalRoot,
  }) {
    const trigger = page.getByRole('button', { name: triggerName });
    await trigger.click();

    // Focus moves to the heading on next tick (setTimeout 0 in
    // install-modal-focus-sync!). Playwright auto-waits for the assertion.
    await expect(page.locator('#' + headingId)).toBeFocused();
    await expect(modalRoot).toBeVisible();

    await page.keyboard.press('Escape');

    // Modal unmounts; trigger regains focus from install-escape-to-close!
    // → set-open-modal :none → install-modal-focus-sync! restore branch.
    await expect(modalRoot).toHaveCount(0);
    await expect(trigger).toBeFocused();
  }

  test('Info modal opens, focuses heading, Escape closes, focus restores', async ({ page }) => {
    await page.goto('');
    await modalOpenCloseCycle(page, {
      triggerName: /^Info$/i,
      headingId:   'info-modal-title',
      modalRoot:   page.locator('section[role="dialog"][aria-labelledby="info-modal-title"]'),
    });
  });

  test('Settings modal opens, focuses heading, Escape closes, focus restores', async ({ page }) => {
    await page.goto('');
    await modalOpenCloseCycle(page, {
      triggerName: /^Settings$/i,
      headingId:   'settings-modal-title',
      modalRoot:   page.locator('section[role="dialog"][aria-labelledby="settings-modal-title"]'),
    });
  });

  test('Save modal opens, focuses heading, Escape closes, focus restores', async ({ page }) => {
    await page.goto('');
    await modalOpenCloseCycle(page, {
      triggerName: /Import\/Export/i,
      headingId:   'save-modal-title',
      modalRoot:   page.locator('section[role="dialog"][aria-labelledby="save-modal-title"]'),
    });
  });

  test('Delete-confirm opens, focuses question, Escape closes, focus restores', async ({ page }) => {
    await page.goto('');
    // Server SERVER-DB isn't seeded automatically on a fresh app
    // load (seed! is a REPL-only convenience). Add an item via the
    // UI so the list is non-empty when Delete List is clicked —
    // otherwise it surfaces the "nothing to delete" error and never
    // opens the modal.
    await page.getByRole('textbox', { name: /New TODO/i }).fill('placeholder');
    await page.getByRole('button', { name: /Add Item/i }).click();

    await modalOpenCloseCycle(page, {
      triggerName: /Delete List/i,
      headingId:   'delete-confirm-question',
      modalRoot:   page.locator('section[role="dialog"][aria-labelledby="delete-confirm-question"]'),
    });
  });
});

// ---------------------------------------------------------------------------
// axe-core — automated WCAG rule checks
//
// One scan per page state. The default ruleset is WCAG 2.1 AA. The
// suite asserts ZERO violations at each state. (Phase 19j fixed the
// dim-button contrast that the first run surfaced — see commit notes.)
// ---------------------------------------------------------------------------

async function axeViolations(page) {
  const results = await new AxeBuilder({ page }).analyze();
  return results.violations;
}

test.describe('axe-core scans', () => {
  test('initial page has zero a11y violations', async ({ page }) => {
    await page.goto('');
    expect(await axeViolations(page)).toEqual([]);
  });

  test('Info modal open: zero violations', async ({ page }) => {
    await page.goto('');
    await page.getByRole('button', { name: /^Info$/i }).click();
    await expect(page.locator('#info-modal-title')).toBeFocused();
    expect(await axeViolations(page)).toEqual([]);
  });

  test('Settings modal open: zero violations', async ({ page }) => {
    await page.goto('');
    await page.getByRole('button', { name: /^Settings$/i }).click();
    await expect(page.locator('#settings-modal-title')).toBeFocused();
    expect(await axeViolations(page)).toEqual([]);
  });

  test('Save modal open: zero violations', async ({ page }) => {
    await page.goto('');
    await page.getByRole('button', { name: /Import\/Export/i }).click();
    await expect(page.locator('#save-modal-title')).toBeFocused();
    expect(await axeViolations(page)).toEqual([]);
  });

  test('Delete-confirm open: zero violations', async ({ page }) => {
    await page.goto('');
    // Non-empty list precondition (see 19g+19h block).
    await page.getByRole('textbox', { name: /New TODO/i }).fill('placeholder');
    await page.getByRole('button', { name: /Add Item/i }).click();

    await page.getByRole('button', { name: /Delete List/i }).click();
    await expect(page.locator('#delete-confirm-question')).toBeFocused();
    expect(await axeViolations(page)).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// 19c — <html lang> reflects active locale
// ---------------------------------------------------------------------------

test.describe('19c — html lang sync', () => {
  test('<html lang> starts at "en" and flips when locale changes', async ({ page }) => {
    await page.goto('');

    // Baseline (index.html ships lang="en"; install-html-lang-sync!
    // confirms on app-init).
    expect(await page.locator('html').getAttribute('lang')).toBe('en');

    // Open Settings, change language to Spanish via the <select>.
    await page.getByRole('button', { name: /^Settings$/i }).click();
    await page.locator('#settings-locale').selectOption('es');
    expect(await page.locator('html').getAttribute('lang')).toBe('es');

    // Same dropdown, Japanese.
    await page.locator('#settings-locale').selectOption('ja');
    expect(await page.locator('html').getAttribute('lang')).toBe('ja');
  });
});
