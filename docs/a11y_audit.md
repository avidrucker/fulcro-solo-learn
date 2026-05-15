# Accessibility / Section 508 audit

Living record of accessibility work for the AutoFocus port. Phase 19
landed the first programmatic pass; remaining items are split into:

- **Section A** — things the agent can fix from the codebase
  (ARIA, semantics, i18n of accessible names, focus management).
- **Section B** — things the human must run (Lighthouse / axe / NVDA
  / keyboard sweeps) because they need a real browser, real screen
  reader, or human judgment.

References used in this audit:
- WCAG 2.1 AA (Section 508 cites WCAG 2.0 AA, but AA-2.1 is the de
  facto modern baseline).
- ARIA Authoring Practices Guide (APG) for modal-dialog and
  toolbar/button patterns.

---

## Phase 19 — completed in-codebase work

### 19a — Tooltip / aria-label / close-label i18n migration

All button accessible names that were still hardcoded English have
been moved into `learn.i18n.core` and pulled via `i18n/tr`:

- Modal close-buttons: `:close-modal/info`, `:close-modal/settings`,
  `:close-modal/save`, `:close-modal/delete-confirm` — formerly
  hardcoded English `aria-label`s. Both `title` and `aria-label`
  pull the same localized string.
- Header-button tooltips: `:tooltip/export-json`,
  `:tooltip/copy-list-url`.
- Four primary action-button tooltips: add-item, delete-list,
  prioritize, mark-done — both `title` and `aria-label` localized.
- Six row-action tooltip variants: `:tooltip/cancel-delete`,
  `:tooltip/confirm-delete`, `:tooltip/quit-review`,
  `:tooltip/review-no`, `:tooltip/review-yes`.

**Commit**: `7d37ea6`.

### 19b — Modal dialog semantics (`role` / `aria-modal` / `aria-labelledby`)

Every modal opened via `modal-shell` now declares:
- `role="dialog"` — screen reader announces "dialog".
- `aria-modal="true"` — signals that the rest of the page is inert
  while the dialog is open.
- `aria-labelledby="<id-of-title-element>"` (optional, per call
  site) — the dialog's accessible name comes from the existing
  visible heading instead of being duplicated as a separate label.

Per-modal opt-in via stable element IDs:
- Info modal — `info-modal-title`
- Settings modal — `settings-modal-title`
- Save / Import-Export modal — `save-modal-title`
- Delete-confirm — `delete-confirm-question` (no h2 — the question
  `<p>` doubles as the name)
- Locale-conflict — `locale-conflict-question`
- List-conflict — `list-conflict-question`
- Review modal — `review-question`

**Commit**: `bb0e44b`.

### 19c — `<html lang>` sync with `:ui/locale`

`resources/public/index.html` ships with `<html lang="en">` as the
baseline. Phase 19c adds `learn.client.lifecycle/install-html-lang-sync!`
which watches the Fulcro state-atom and rewrites the `lang` attribute
when `[:list/id 1 :ui/locale]` changes. Screen readers use this to
pick the right voice — without it a Japanese user reading Spanish UI
would get the Japanese TTS voice trying to pronounce Spanish words.

Maps: `:en` → `en`, `:es` → `es`, `:ja` → `ja` (IETF tags happen to
match our locale-keyword names 1:1 for the supported set).

**Commit**: `e445453`.

### 19d — Decorative SVG icons (`aria-hidden` + `focusable=false`)

`learn.ui.icons/svg-attrs` (the shared root attrs every icon merges)
now sets `aria-hidden="true"` and `focusable="false"`. All eleven
icons (dot-circle, empty-circle, filled-circle, info-circle,
lightbulb-solid, lightbulb-regular, save-disk, question-circle,
gear, cancel-x, repeat-arrow) flow through these attrs.

**Why aria-hidden:** every icon ships inside a parent (button or
labeled span) that already carries its own accessible name via
`aria-label` / `title`. Without `aria-hidden`, screen readers would
announce the SVG as a separate "graphic" alongside the button label —
duplicate noise (e.g. "Save and import — graphic"). With it, only
the parent's accessible name is announced.

**Why focusable=false:** legacy IE/Edge made inline SVGs part of
the keyboard tab order by default. Benign in modern browsers, but
the explicit `false` removes the risk for older AT/browser combos.

**Commit**: (this commit).

---

## Section A — remaining in-codebase a11y work (planned)

These are queued as Phase 19 follow-ups. Order is rough; nothing here
blocks anything else.

### 19e — Localized tooltips on currently-bare interactive controls

Surfaced by the user 2026-05-15. Controls in the import/export modal
and the settings modal lack `title` / `aria-label` entirely. Each
needs a localized string in all three locales:

1. **"Include language in link" checkbox** in the save modal.
   Current label is the visible checkbox text only; users who
   tab-focus the checkbox first hear no explanation of what the
   flag does. **Locked en string** (user confirmed 2026-05-15):
   > When checked, the share link will open in this app's current
   > language for whoever clicks it.
2. **JSON import button** (file picker trigger). Working draft:
   > Click here to import a JSON file of to-do items.
3. **Text-list import submit button** (under the textarea). Working
   draft:
   > Click here to import a text list of to-do items.
4. **Language dropdown in the settings modal**. Working draft:
   > Select a language from this list to change this app's
   > language.

Each tooltip lives as `:tooltip/<key>` in `learn.i18n.core` with
en/es/ja strings, and is wired as both `:title` (mouse hover) and
`:aria-label` (screen reader) on the control.

### 19f — Status-icon accessible names (icon-only status indicator)

`TodoItem` shows status via the SVG icon (dot-circle / empty-circle
/ filled-circle / cancel-x glyph). The icon is now `aria-hidden`
(19d), and the wrapping `<span>` carries only a `:title (name status)`
that screen readers don't announce. Net: screen reader users get
**no** status announcement for any row.

Plan: replace the unlocalized `:title (name status)` with a localized
status name on the span (both `:title` and `:aria-label`, since spans
need an explicit ARIA role/label to be announced — leaning toward
making the status icon a `role="img"` element with an `aria-label`
holding the localized status name, e.g. "ready", "new", "done",
"cancelled — was ready"). Cancelled rows should announce both the
cancelled state and what the row was before cancellation.

### 19g — Focus management on modal open / close

Currently nothing moves focus into a modal when it opens; the focused
control before opening (often a header button) stays focused, which
means:
- Tab order from that control still walks the now-inert background
  page.
- Closing the modal via background-click leaves focus on a
  now-dismissed element.

Standard ARIA modal pattern:
- On open: move focus to the first focusable element inside the
  modal (or the modal's heading made `tabindex=-1`).
- Trap Tab/Shift+Tab inside the modal while it's open.
- On close: restore focus to the element that had it before open.

Lowest-cost first pass: focus-the-heading on open, restore on close.
Real focus-trap can come later (or live as a small util).

### 19h — Escape-to-close on dismissible modals

Info / Settings / Save / Delete-confirm modals all close via
background-click or an explicit close button, but not via the
`Escape` key. APG modal-dialog pattern recommends Escape. Excluded:
the list-conflict and locale-conflict modals are explicitly
non-dismissible (the user **must** pick) — Escape stays disabled
for those.

### 19i — Keyboard-only navigation sweep

Walk every interactive flow with the keyboard alone (Tab / Shift-Tab
/ Enter / Space / Escape) — no mouse — and log any place where:
- A button can't be reached.
- Tab order jumps unexpectedly.
- Enter/Space on a button doesn't activate it.
- A modal's visible buttons are tab-skipped.

This is partly Section A (fixes once gaps are found) and partly
Section B (the sweep itself is human work).

### 19j — Color-contrast pass on the dark theme

Hand-verify foreground/background contrast on each colored element
in dark mode against WCAG AA (4.5:1 for normal text, 3:1 for large
text and UI components). Tachyons `moon-gray` on `near-black` is
the suspect pair. May require swapping a class or two; small fix
once measured.

---

## Section B — what the user needs to run

These need a real browser, real assistive tech, or human judgment;
the agent can't do them from the CLI.

### B-1 — Lighthouse audit in Chrome / Edge

DevTools → Lighthouse → "Accessibility" only → mobile + desktop.
Goal: 95+ on both. Common findings:
- Missing `<label>` for form controls.
- Insufficient color contrast.
- Buttons without discernible text (most fixed in 19a, but the
  text-list textarea submit and JSON-import buttons still need 19e).
- Image elements without `alt` (none in this app — icons are inline
  SVG, see 19d).

### B-2 — axe DevTools (Chrome / Firefox extension)

Deque's free extension. Catches things Lighthouse misses, especially
ARIA misuse. Run on every modal open + the main list view.

### B-3 — WAVE (Chrome / Firefox extension or [wave.webaim.org](https://wave.webaim.org/))

Visual overlay that flags errors and warnings inline on the page.
Good complement to axe — different rule set, different blind spots.

### B-4 — NVDA (free Windows) or VoiceOver (built-in macOS)

Listen to every modal open, every primary action, every form
submission. Note any "graphic", "button", or "unlabeled" announcement
that should have been more specific. Test in all three locales —
hard to catch broken `<html lang>` sync any other way.

### B-5 — Keyboard-only navigation pass

No mouse. Walk the golden path (add item → prioritize → review →
mark done → delete list). Log per **B-1 / B-2** findings.

### B-6 — Zoom test (200% via browser zoom)

WCAG 1.4.4 requires the app to remain usable at 200% zoom without
horizontal scrolling on viewport widths down to 320 CSS px.
Tachyons-based layout should already pass; verify before claiming.

### B-7 — `prefers-reduced-motion` test

DevTools → Rendering → Emulate CSS media feature
`prefers-reduced-motion: reduce`. Confirm no animations play when
this is on. (Current app has minimal animation — likely passes by
default.)

### B-8 — Color-contrast measurement

Either DevTools' built-in contrast picker (Inspect → color swatch)
or a standalone tool (e.g. Stark, WebAIM contrast checker). Pair
with 19j: agent picks the swaps, human re-measures.

---

## Suggested run order for the user (Section B)

1. **B-1 (Lighthouse)** — fastest, broadest first pass. Five
   minutes per locale.
2. **B-2 (axe)** — runs on the same browser session as B-1; takes
   another five minutes and surfaces things Lighthouse misses.
3. **B-5 (keyboard-only sweep)** — ten minutes for the golden path
   in one locale; do once per browser.
4. **B-4 (screen reader)** — biggest time cost (~30 min) but the
   only way to know if 19a / 19b / 19c actually paid off. Do once
   per locale.
5. **B-6 / B-7 / B-8** — quick spot-checks once everything above is
   green.
