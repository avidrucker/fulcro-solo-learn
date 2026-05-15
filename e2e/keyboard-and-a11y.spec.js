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
// 19g (extension) — review modal focus management
//
// The review modal is statechart-driven (not via `:ui/open-modal`), so it
// has its own focus-sync watcher. To open it: list needs ≥1 :ready and
// ≥1 :new after the last :ready (the prioritizable? predicate). Two
// adds via the UI satisfies this — first add auto-promotes to :ready,
// second stays :new.
// ---------------------------------------------------------------------------

test.describe('19g (ext) — review modal focus', () => {
  test('Prioritize moves focus to review-question; Escape does NOT close', async ({ page }) => {
    await page.goto('');
    await page.evaluate(() => localStorage.clear());
    await page.reload();

    // Build a prioritizable list (need 2 items so the second one is :new
    // after the auto-promoted first :ready).
    const input = page.getByRole('textbox', { name: /New TODO/i });
    await input.fill('Item A');
    await page.getByRole('button', { name: /Add Item/i }).click();
    await input.fill('Item B');
    await page.getByRole('button', { name: /Add Item/i }).click();

    await page.getByRole('button', { name: /Prioritize/i }).click();

    // Review modal mounts; install-review-modal-focus-sync! (rAF poll)
    // moves focus to the question element.
    await expect(page.locator('#review-question')).toBeFocused();

    // Escape is deliberately NOT wired for the review modal — Quit is the
    // only dismissal path. Confirm Escape is a no-op.
    await page.keyboard.press('Escape');
    await expect(page.locator('#review-question')).toBeFocused();
    await expect(page.locator('section[role="dialog"][aria-labelledby="review-question"]')).toBeVisible();

    // Quit closes (via the button's click handler — `event-quit` to chart).
    await page.getByRole('button', { name: /^Quit$/i }).click();
    await expect(page.locator('section[role="dialog"][aria-labelledby="review-question"]')).toHaveCount(0);
  });
});

// ---------------------------------------------------------------------------
// 19g + 19h — non-dismissible conflict modals
//
// The two conflict modals (list-conflict, locale-conflict) are NOT in
// `dismissible-modals`. They demand a choice from the user; Escape is a
// no-op. These tests:
//   - seed the localStorage + URL preconditions
//   - confirm the modal opens with focus on its question
//   - confirm Escape does NOT close it
// ---------------------------------------------------------------------------

test.describe('19g + 19h — non-dismissible conflict modals', () => {
  test('locale-conflict modal opens when URL ?lang differs from saved locale', async ({ page }) => {
    await page.goto('');
    await page.evaluate(() => localStorage.clear());
    // Seed saved locale = :es; the install-url-locale-fallback! decision
    // path reads ui-prefs BEFORE applying URL, so this triggers the
    // :conflict branch when ?lang=en is then applied.
    await page.evaluate(() => {
      localStorage.setItem('autofocus.ui-prefs', '{:ui/locale :es}');
    });

    // URL ?lang=en differs from saved :es → conflict.
    await page.goto('?lang=en');

    await expect(page.locator('section[role="dialog"][aria-labelledby="locale-conflict-question"]')).toBeVisible();
    await expect(page.locator('#locale-conflict-question')).toBeFocused();

    // Escape is a no-op — user must pick one of the two buttons.
    await page.keyboard.press('Escape');
    await expect(page.locator('section[role="dialog"][aria-labelledby="locale-conflict-question"]')).toBeVisible();
  });

  test('list-conflict modal opens when URL ?list differs from saved list', async ({ page }) => {
    // Multi-step bootstrap — build two distinct (localStorage, URL)
    // pairs by driving the UI, then combine the localStorage from one
    // with the URL from the other to construct the conflict scenario.

    // Capture URL after adding "Item A" with a fresh localStorage.
    await page.goto('');
    await page.evaluate(() => localStorage.clear());
    await page.reload();
    await page.getByRole('textbox', { name: /New TODO/i }).fill('Item A');
    await page.getByRole('button', { name: /Add Item/i }).click();
    // install-url-sync! writes `?list=<encoded>` on every items change.
    const urlA = page.url();

    // Capture localStorage after adding "Item B" with a fresh localStorage.
    await page.goto('');
    await page.evaluate(() => localStorage.clear());
    await page.reload();
    await page.getByRole('textbox', { name: /New TODO/i }).fill('Item B');
    await page.getByRole('button', { name: /Add Item/i }).click();
    const localB = await page.evaluate(() => localStorage.getItem('autofocus.server-db'));

    // Combine: localStorage = state-after-B; URL = state-after-A.
    await page.evaluate((v) => localStorage.setItem('autofocus.server-db', v), localB);
    await page.goto(urlA);

    await expect(page.locator('section[role="dialog"][aria-labelledby="list-conflict-question"]')).toBeVisible();
    await expect(page.locator('#list-conflict-question')).toBeFocused();

    // Escape is a no-op.
    await page.keyboard.press('Escape');
    await expect(page.locator('section[role="dialog"][aria-labelledby="list-conflict-question"]')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// 19i — keyboard-only golden-path sweep
//
// Verifies the entire primary flow is reachable + activatable via Tab /
// Shift-Tab / Enter / Space — no mouse. Not exhaustive (visual focus
// indicator, keyboard trap detection, etc. still need a human eye), but
// proves the core add → prioritize → review-yes → mark-done → delete
// pipeline works for keyboard users.
// ---------------------------------------------------------------------------

test.describe('19i — keyboard-only golden path', () => {
  test('add → prioritize → review yes → mark done → delete (keyboard only)', async ({ page }) => {
    await page.goto('');
    await page.evaluate(() => localStorage.clear());
    await page.reload();

    const input = page.getByRole('textbox', { name: /New TODO/i });

    // ── 1. Tab from page entry through the header until we land on the
    //    new-todo input. Doubles as a header tab-order sanity check
    //    (the full assertion lives in 19i header-tab-order; this test
    //    only cares that the chain works at all).
    for (let i = 0; i < 6; i++) await page.keyboard.press('Tab');
    await expect(input).toBeFocused();

    // ── 2. Add 2 items via Enter-key submit (Phase 7.3).
    //
    // `.pressSequentially()` (real keystrokes) is required — `fill()`
    // sets the DOM value but doesn't fire the input event Fulcro's
    // `m/set-string!` listens for, so `:ui/new-todo-text` stays
    // empty and add-todo refuses the submit.
    //
    // The 30ms `delay` between chars gives React time to reconcile
    // each onChange before the next key fires. Without it,
    // chars-faster-than-state means some onChange events get dropped
    // and the state-stored text ends up truncated ("Firs" instead of
    // "First item").
    //
    // Between adds, wait for the new item to appear AND wait until
    // the input value clears (the add mutation resets it
    // asynchronously). Without the empty-wait, the next
    // pressSequentially can land on a still-populated input and
    // concatenate the two item texts.
    await input.pressSequentially('First item', { delay: 30 });
    await expect(input).toHaveValue('First item');
    await page.keyboard.press('Enter');
    await expect(page.getByRole('listitem').filter({ hasText: 'First item' })).toBeVisible();
    await expect(input).toHaveValue('');

    await input.pressSequentially('Second item', { delay: 30 });
    await expect(input).toHaveValue('Second item');
    await page.keyboard.press('Enter');
    await expect(page.getByRole('listitem').filter({ hasText: 'Second item' })).toBeVisible();
    await expect(input).toHaveValue('');

    // ── 3. Prioritize: focus + Enter. Not asserting the exact Tab path —
    // some review buttons carry positive tabindex (`tabIndex 0/1/2`)
    // which mixes natural and explicit ordering; testing tab paths
    // through them is brittle. The relevant a11y guarantee is that
    // every control IS reachable + activatable by keyboard, which
    // `focus()` + Enter demonstrates.
    await page.getByRole('button', { name: /Prioritize/i }).focus();
    await page.keyboard.press('Enter');

    // Review modal opens, focus moves to question via 19g extension.
    await expect(page.locator('#review-question')).toBeFocused();

    // ── 4. Answer :yes via keyboard. Focus the Yes button explicitly
    // (positive-tabindex order != DOM order — see component def in
    // `learn.client.ui.components`), then Enter.
    await page.getByRole('button', { name: /^Yes$/i }).focus();
    await page.keyboard.press('Enter');

    // Review modal auto-closes when no :new items remain at-or-after the
    // benchmark (cursor → -1, eventless transition to :inactive).
    await expect(page.locator('section[role="dialog"][aria-labelledby="review-question"]')).toHaveCount(0);

    // ── 5. Mark Done via keyboard.
    await page.getByRole('button', { name: /Mark Done/i }).focus();
    await page.keyboard.press('Enter');

    // ── 6. Delete List → confirm modal → Yes via keyboard.
    await page.getByRole('button', { name: /Delete List/i }).focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#delete-confirm-question')).toBeFocused();
    await page.getByRole('button', { name: /^Yes$/i }).focus();
    await page.keyboard.press('Enter');

    await expect(page.locator('section[role="dialog"][aria-labelledby="delete-confirm-question"]')).toHaveCount(0);
  });
});

// ---------------------------------------------------------------------------
// 19c — <html lang> reflects active locale
// ---------------------------------------------------------------------------

test.describe('locale dropdown wiring (phase 21 sanity)', () => {
  test('Settings dropdown lists all 4 supported locales', async ({ page }) => {
    await page.goto('');
    await page.getByRole('button', { name: /^Settings$/i }).click();
    const options = await page.locator('#settings-locale option').allTextContents();
    expect(options).toEqual(['English', 'Español', '日本語', 'Português']);
  });

  test('selecting Português flips html lang to pt and UI labels translate', async ({ page }) => {
    await page.goto('');
    await page.getByRole('button', { name: /^Settings$/i }).click();
    await page.locator('#settings-locale').selectOption('pt');

    // Phase 19c html-lang sync — also a regression check.
    expect(await page.locator('html').getAttribute('lang')).toBe('pt');

    // Close the modal (Escape works for Settings — phase 19h) so we can
    // see the page-level Portuguese button label.
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: /Adicionar Tarefa/i })).toBeVisible();
  });
});

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
