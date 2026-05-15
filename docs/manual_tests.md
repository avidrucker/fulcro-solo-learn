# Manual test plan

Things that can't be red-green TDD'd from the JVM test runner: browser
DOM behavior, focus management, keyboard handling, screen reader
announcements, persistence across reloads, color contrast, animation
timing, native-control rendering. This doc is the checklist for those.

Run the dev build (`npx shadow-cljs watch app`, then load
`http://localhost:9630` — actual port may differ; the build server
prints it) and walk each section. Tick the checkbox once verified.

When a test fails, log it as a bug in `docs/bugs.md` and reference
the section here (e.g. "manual_tests.md §19g.1").

Tip: open DevTools Console before testing. The app intentionally
keeps console clean in normal operation; any warning/error during a
manual flow is a regression.

---

## Phase 19a — localized tooltips & ARIA labels on buttons

### 19a.1 Hover tooltips switch with locale
- [ ] Set language to **English**. Hover the four header icon buttons
      (save disk, info-circle, gear, lightbulb). Tooltip text is in
      English (e.g. "Import/Export", "Info", "Settings", "Toggle
      Theme").
- [ ] Switch to **Spanish**. Repeat — tooltips are in Spanish
      ("Importar/Exportar", "Información", "Ajustes", "Cambiar Tema").
- [ ] Switch to **Japanese**. Repeat — tooltips are in Japanese.

### 19a.2 Action-button tooltips localized
- [ ] In each of en/es/ja, hover the four primary buttons under the
      list (Add Item, Delete List, Prioritize, Mark Done). Tooltip
      strings are localized.

### 19a.3 Row-action tooltips localized
- [ ] Add a couple items. Hover the per-row cancel (X) icon → tooltip
      "Cancel Task" / "Cancelar Tarea" / "タスクをキャンセル". Click cancel
      then hover the now-shown repeat-arrow → tooltip "Clone Task" /
      "Clonar Tarea" / "タスクを複製".

### 19a.4 Modal close-button accessible names localized
With each modal open in turn (info / settings / save / delete-confirm),
inspect the transparent full-area close button via DevTools. Its
`aria-label` and `title` should match the locale:
- [ ] info: "Close Info Modal" / "Cerrar Ventana de Información" / "情報モーダルを閉じる".
- [ ] settings: "Close Settings Modal" / etc.
- [ ] save: "Close Save Modal" / etc.
- [ ] delete-confirm: "Close Delete Modal" / etc.

---

## Phase 19b — modal dialog semantics

### 19b.1 Every modal has role="dialog" + aria-modal="true"
Inspect the outer `<section>` of each modal via DevTools:
- [ ] Info modal — has `role="dialog"` and `aria-modal="true"`.
- [ ] Settings modal — same.
- [ ] Save / Import-Export modal — same.
- [ ] Delete-confirm modal — same.
- [ ] List-conflict modal — same. (Load two conflicting list URLs to
      trigger it — see e.g. the URLs in PR-X / the B-10 fix commit
      message for shortcuts.)
- [ ] Locale-conflict modal — same. (Save a locale to localStorage,
      then visit `?lang=<different-locale>` to trigger.)
- [ ] Review modal — same. (Add 2+ items, click Prioritize.)

### 19b.2 aria-labelledby points to the visible heading/question
Inspect each modal's outer `<section>` — its `aria-labelledby`
should match the `id` of the visible heading/question element:
- [ ] info → `info-modal-title`.
- [ ] settings → `settings-modal-title`.
- [ ] save → `save-modal-title`.
- [ ] delete-confirm → `delete-confirm-question`.
- [ ] list-conflict → `list-conflict-question`.
- [ ] locale-conflict → `locale-conflict-question`.
- [ ] review → `review-question`.

---

## Phase 19c — `<html lang>` sync

### 19c.1 `<html lang>` reflects active locale
Inspect the `<html>` element in DevTools (Elements panel, scroll all
the way up):
- [ ] On first load with no saved locale, `lang="en"`.
- [ ] Switch language to **Spanish** in Settings. `<html lang>` flips
      to `"es"` immediately, no reload required.
- [ ] Switch to **Japanese**. `<html lang>` flips to `"ja"`.
- [ ] Reload page. `<html lang>` matches the saved locale (the bar
      starts on `"en"` because that's the HTML baseline, then flips
      on app-init in the same tick — a screenshot at frame 0 might
      catch "en" but the user-visible value is the saved locale).

### 19c.2 Screen reader picks up the language
Open NVDA (Windows) or VoiceOver (macOS), navigate to the page in
each of the three locales, and verify:
- [ ] English text reads with the English voice.
- [ ] Spanish text reads with the Spanish voice (NVDA may need the
      Spanish language pack installed; VoiceOver ships all locales).
- [ ] Japanese text reads with the Japanese voice.

---

## Phase 19d — decorative icons hidden from AT

### 19d.1 SVGs have aria-hidden="true"
Inspect any icon button (e.g. the header gear, the cancel X on a
row). The inline `<svg>` should have `aria-hidden="true"` and
`focusable="false"`:
- [ ] Header save-disk SVG.
- [ ] Header info-circle SVG.
- [ ] Header gear SVG.
- [ ] Header lightbulb-solid / lightbulb-regular SVG.
- [ ] Per-row cancel-x SVG.
- [ ] Per-row repeat-arrow SVG.
- [ ] Per-row status icons (dot-circle / empty-circle / filled-circle).

### 19d.2 No "graphic" announcement alongside button labels
With NVDA / VoiceOver running, tab through the header buttons:
- [ ] Each button announces only its accessible name
      (e.g. "Import/Export, button") — NOT
      "Import/Export, button, graphic".
- [ ] Same for the per-row action buttons.

---

## Phase 19e — tooltips on bare controls

### 19e.1 Include-language checkbox tooltip
- [ ] Open the save / import-export modal. Hover the
      "Include language in link" checkbox. Tooltip appears with the
      locked en string: "When checked, the share link will open in
      this app's current language for whoever clicks it."
- [ ] Switch to Spanish — tooltip is the Spanish translation.
- [ ] Switch to Japanese — tooltip is the Japanese translation.

### 19e.2 JSON import button tooltip
- [ ] In the save modal, hover the "Import" button (styled label).
      Tooltip: "Click here to import a JSON file of to-do items." /
      Spanish / Japanese.

### 19e.3 Text-list submit button tooltip
- [ ] In the save modal, hover the Submit button under the textarea.
      Tooltip: "Click here to import a text list of to-do items." /
      es / ja.

### 19e.4 Language dropdown tooltip
- [ ] In the settings modal, hover the language `<select>`. Tooltip:
      "Select a language from this list to change this app's
      language." / es / ja.

### 19e.5 Screen reader announces the tooltip text on focus
With NVDA / VoiceOver running, tab to each of those four controls and
verify the screen reader announces the tooltip wording (the
`aria-label`).
- [ ] Include-language checkbox.
- [ ] Import (file) button.
- [ ] Text-list submit button.
- [ ] Language dropdown.

---

## Phase 19f — status-icon accessible names

### 19f.1 Per-row status announced by screen reader
Add three items, prioritize / cancel a few so you have rows with
mixed statuses. With NVDA / VoiceOver running, walk the list:
- [ ] A `:status/new` row announces "new" / "nuevo" / "新規".
- [ ] A `:status/ready` row announces "ready" / "listo" / "準備完了".
- [ ] A `:status/done` row announces "done" / "hecho" / "完了".
- [ ] A `:status/cancelled` row that was previously `:status/ready`
      announces "cancelled (was ready)" / "cancelado (antes: listo)" /
      "キャンセル済み（元：準備完了）".

### 19f.2 List-conflict modal previews also announce status
With the list-conflict modal open (need two diverging lists — load
one URL, then load another):
- [ ] Each list-preview row announces its status the same way as
      the main list.

---

## Phase 19g — modal focus management

### 19g.1 Focus moves into the modal on open
Keyboard only (no mouse):
- [ ] Tab to the header gear icon → press Enter to open Settings
      modal → focus is now inside the modal (typically on the
      "Settings" heading). Pressing Tab next moves to the language
      dropdown, not back to the header buttons.
- [ ] Repeat with the info-circle button → Info modal.
- [ ] Repeat with the save-disk button → Save modal.
- [ ] Click "Delete List" → Delete-confirm modal opens with focus
      inside.
- [ ] Trigger the list-conflict modal (load conflicting URLs) → focus
      moves to the conflict question.
- [ ] Trigger the locale-conflict modal (save a locale, visit
      `?lang=<other>`) → focus moves to the conflict question.
- [ ] **Review modal** (Phase 19g extension): with 2+ items in the
      list, tab to "Prioritize" → Enter → focus moves to the review
      question.

### 19g.2 Focus restores on close
- [ ] Tab to the gear icon, open Settings, then close (via Escape or
      the transparent close button). Focus returns to the gear icon
      — not to `<body>` or the first focusable element on the page.
- [ ] Same test from the info-circle button.
- [ ] Same test from the save-disk button.

### 19g.3 Modal-to-modal transition does not lose the original anchor
Rare path: open Settings via gear → open Info via info-circle while
Settings is up (header buttons are disabled while modals are open, so
this needs to be triggered programmatically or via a future
non-disabled pathway). Skip this case if not currently reachable;
flag for retest when modal-to-modal is allowed.

---

## Phase 19h — Escape closes dismissible modals

### 19h.1 Escape closes the four dismissible modals
- [ ] Info modal open → press Escape → modal closes, focus returns
      to the info-circle button.
- [ ] Settings modal open → Escape closes it.
- [ ] Save modal open → Escape closes it.
- [ ] Delete-confirm modal open → Escape closes it (cancels the
      delete).

### 19h.2 Escape does NOT close the two non-dismissible modals
- [ ] List-conflict modal open → Escape does nothing; modal stays
      up. User must click one of the four buttons.
- [ ] Locale-conflict modal open → Escape does nothing.

### 19h.3 Escape with no modal open is a no-op
- [ ] No modal open → pressing Escape does not throw a console
      error and does not unexpectedly change app state.

### 19h.4 Review modal
The review modal is statechart-driven, separate from `:ui/open-modal`,
and is NOT wired for Escape-to-close (Quit is the only dismissal
path; the review chart has explicit state transitions that we don't
want bypassed). Confirm:
- [ ] Review modal open → Escape does not close it (must use Quit).
      If this changes in a future phase, update this test.

---

## Phase 19k — error banner is an ARIA live region

`:ui/err-msg` flips from nil to a string when a refused action
needs to surface (e.g. adding a blank item, deleting an empty
list). The `<p>` rendering the error now has `role="alert"`, so
screen readers announce the new error immediately on render.

### 19k.1 Screen reader announces a new error
With NVDA / VoiceOver running:
- [ ] Tab to the new-todo input. Without typing anything, tab to
      "Add Item" and press Enter. The screen reader should
      announce something like "Alert: New items cannot be empty
      or only whitespace." (NVDA's "alert" preamble is verbose;
      VoiceOver may just read the text.)
- [ ] Repeat with each error source: Delete List on empty list,
      Mark Done with no actionable items, Prioritize on
      non-prioritizable list. All four announce automatically.
- [ ] Repeat in Spanish and Japanese — the announcement reads in
      the active locale.

### 19k.2 Error inspectable in DevTools
- [ ] When an error is showing, inspect the red `<p>`. It should
      have `role="alert"`.
- [ ] When no error is showing (after dismissing or completing a
      valid action), the element is gone entirely (we render
      `(when err-msg ...)`), which is correct: the alert region
      shouldn't be empty-and-present.

---

## Phase 19l — localized new-todo input

The page-level new-todo input's placeholder + clip-hidden label
now flip with `:ui/locale`.

### 19l.1 Placeholder switches with locale
- [ ] English: empty input shows "Type new task here".
- [ ] Spanish: empty input shows "Escribe una nueva tarea aquí".
- [ ] Japanese: empty input shows "ここに新しいタスクを入力".

### 19l.2 Screen reader announces the localized label
With NVDA / VoiceOver:
- [ ] Tab to the new-todo input in each locale. The announced
      name should be "New TODO:" / "Nueva tarea:" / "新しいToDo:"
      respectively.

---

## Phase 19m — theme-toggle direction labeling + aria-pressed

### 19m.1 Tooltip switches with state
- [ ] In **light** mode, hover the lightbulb icon. Tooltip is
      "Switch to dark mode" (or es/ja equivalent).
- [ ] Click → mode flips to dark; the lightbulb icon also flips
      (solid → outline). Hover again. Tooltip is now "Switch to
      light mode".
- [ ] Repeat in Spanish: "Cambiar a modo oscuro" / "Cambiar a
      modo claro".
- [ ] Repeat in Japanese: "ダークモードに切り替える" /
      "ライトモードに切り替える".

### 19m.2 aria-pressed announces the toggle state
Inspect the toggle button via DevTools:
- [ ] In light mode, `aria-pressed="false"`.
- [ ] In dark mode, `aria-pressed="true"`.

### 19m.3 Screen reader announces direction + state
With NVDA / VoiceOver:
- [ ] In light mode, tab to the toggle. Announce should include
      the localized "Switch to dark mode" plus "toggle button,
      not pressed" (NVDA wording may vary).
- [ ] Click to flip; the announce updates accordingly on next
      focus / re-announce.

---

## Phase 19n — per-element `lang` attrs for cross-locale text

Three spots display text whose language differs from `<html lang>`.
Each carries its own `lang` attribute so AT picks the right voice.

### 19n.1 Settings dropdown options
Open Settings, inspect the `<select>` element via DevTools:
- [ ] The "English" `<option>` has `lang="en"`.
- [ ] The "Español" `<option>` has `lang="es"`.
- [ ] The "日本語" `<option>` has `lang="ja"`.

### 19n.2 Locale-conflict modal buttons + question
Trigger the locale-conflict modal (save a locale, then visit
`?lang=<different-locale>`). Inspect the modal:
- [ ] The bilingual question splits into two `<span lang>`
      elements (one per locale's translation), not a single
      mixed-script string.
- [ ] Each of the two choice buttons has a `lang` attribute
      matching the locale it represents.

### 19n.3 Screen reader pronounces each segment correctly
With NVDA / VoiceOver running and the page in English:
- [ ] Tab to the Settings dropdown, navigate through options.
      "English" reads with the English voice; "Español" reads
      with the Spanish voice; "日本語" reads with the Japanese
      voice — not the English voice trying to pronounce them.
- [ ] Open the locale-conflict modal. The bilingual question
      reads each half in its own voice. Each button announces
      with the right voice.

---

## Phase 19o — skip link

### 19o.1 Skip link is the first focusable element + becomes visible on focus
- [ ] Open the app, click somewhere neutral (or reload), then
      press Tab. The "Skip to main content" link slides into
      view at the top-left of the viewport.
- [ ] Press Tab again. Focus moves to the first header button
      (the link is no longer visible).
- [ ] In Spanish: link reads "Saltar al contenido principal".
- [ ] In Japanese: link reads "メインコンテンツへスキップ".

### 19o.2 Pressing Enter jumps to main content
- [ ] Reload, press Tab to focus the skip link, press Enter.
      Focus lands on the `#main-content` `<section>`. Pressing
      Tab next moves to the new-todo input (or whichever
      element is first inside the main content).

### 19o.3 Skip link not visible to mouse users
- [ ] Reload, do NOT press Tab. The skip link should NOT be
      visible anywhere on the page.

### 19o.4 Dark theme variant
- [ ] Switch to dark mode, then Tab to focus the skip link.
      Background is black, text and border are white (high
      contrast against the dark page).

---

## Phase 19p — respect prefers-reduced-motion

WCAG 2.3.3 (Animation from Interactions). The 0.2s button-bg
transitions suppress when the user prefers reduced motion.

### 19p.1 DevTools emulation
- [ ] DevTools → Rendering panel → Emulate CSS media feature
      `prefers-reduced-motion: reduce`.
- [ ] Hover any header icon button (light theme): the bg fade
      is gone — the new color appears immediately, no
      `.2s` ease.
- [ ] Same in dark theme.

### 19p.2 OS-level preference (optional)
For higher-confidence verification:
- Windows: Settings → Ease of Access → Display → "Show
  animations in Windows" OFF.
- macOS: System Preferences → Accessibility → Display →
  "Reduce motion" ON.
- Linux: varies by DE.

Reload the page. Hover behavior should match §19p.1 without
DevTools emulation.

---

## Phase 19i — keyboard-only navigation sweep

Walk the golden path with **keyboard only** (no mouse). Each
sub-step should be reachable + activatable via Tab / Shift-Tab /
Enter / Space / arrow keys / Escape.

### 19i.1 Golden path
- [ ] Tab to the new-todo input → type "Pet the cat" → Tab to "Add
      Item" → Enter. Item appears.
- [ ] Repeat for "Pet the dog". Two items in the list.
- [ ] Tab to "Prioritize" → Enter. Review modal opens.
- [ ] Tab through the Quit / No / Yes buttons. Press Enter on Yes.
- [ ] After review ends, Tab to "Mark Done" → Enter.
- [ ] Tab to "Delete List" → Enter. Delete-confirm modal opens.
- [ ] Tab to "Confirm" → Enter. List clears.

### 19i.2 No keyboard trap
- [ ] No interaction sequence reaches a state where Tab / Shift-Tab
      cycles infinitely or jumps to an unexpected place.

### 19i.3 Visible focus indicator
- [ ] Every focusable element has a visible focus ring (browser
      default or styled). If any control is focusable but invisible
      when focused, log as a bug.

### 19i.4 Header icon buttons are reachable
- [ ] Tab order reaches the four header icons (save-disk, info,
      gear, lightbulb). They can be activated with Enter or Space.

---

## Phase 19j — dark-theme color contrast

Run in dark mode. Use DevTools' inspect → color picker (it shows the
WCAG contrast ratio for any text element) OR the WebAIM contrast
checker (https://webaim.org/resources/contrastchecker/).

WCAG AA targets:
- 4.5:1 for normal text.
- 3:1 for large text (18pt / 14pt bold) and UI components / icons.

### 19j.1 Main list text contrast
- [ ] List item text against the dark background — measure
      contrast. Should be ≥ 4.5:1.
- [ ] Dimmed (`o-50`) text on cancelled/done items — measure
      contrast. Should still be ≥ 4.5:1; if it dips below, flag for
      a class change.

### 19j.2 Button text contrast
- [ ] Header icon button SVG color vs. button background — ≥ 3:1.
- [ ] Primary button (Add Item etc.) text vs. background — ≥ 4.5:1.
- [ ] Secondary action button (Cancel / Clone) icon vs. button bg
      — ≥ 3:1.

### 19j.3 Modal text contrast
- [ ] Modal heading + body text vs. modal background — ≥ 4.5:1.
- [ ] Modal action buttons — same targets as above.

### 19j.4 Error message contrast
- [ ] Trigger an error (e.g. submit an empty new-todo). The error
      message vs. background — ≥ 4.5:1.

---

## Section B summary — externals to run

Tooling that needs to run against the live app (not the codebase):

- **B-1 Lighthouse** (Chrome / Edge DevTools): aim for 95+ on the
  Accessibility category in each locale.
- **B-2 axe DevTools** (Deque, free extension): zero violations on
  the main list view + each modal open.
- **B-3 WAVE** (https://wave.webaim.org/ or extension): visual
  overlay check.
- **B-4 NVDA / VoiceOver**: covered by the §19a-§19f screen-reader
  steps above, in all three locales.
- **B-5 Keyboard sweep**: §19i above.
- **B-6 Zoom 200%**: page remains usable down to 320 CSS px viewport
  width with no horizontal scroll.
- **B-7 `prefers-reduced-motion: reduce`**: no animations play.
  Phase 19p added the CSS guard; §19p above is the test.
- **B-8 Contrast measurement**: §19j above.

---

## Run log

Append a one-line note per pass run. Date + git short SHA + summary.

- 2026-05-15 — phase 19 in-codebase work done through 19h. Awaiting
  user manual pass.
