# Phase 7.13 — Visual parity sweep + B-2 fix

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Three visible diffs vs the deployed JS port were identified through side-by-side snapshot comparison and corrected. The work isn't a single feature so it's logged together here:

1. **Dark-mode visual parity** (commit `47d2cad`). Three fixes: `bg-near-black` → `bg-black` for the dark page bg (closes the #111 vs #000 gap below the modal); new `delete-confirm-btn-class` (w4 — JS UI reference line 99) replacing the reused review-btn-class (w3) on the No/Yes buttons; ported `-webkit-font-smoothing: antialiased` + `-moz-osx-font-smoothing: grayscale` + `display: flex; flex-direction: column` + `min-height: 100dvh` from the og's `index.css` into our `app.css` on `html, body, #app`.

2. **Long-list visual parity** (commit `4c91db8`). Two more fixes that only surface when the list overflows the viewport (26 items): modal text was squeezed because `modal-shell`'s inner section had `pa3` that the og's `measure-narrow` lacked — dropped `pa3`, kept `relative z-1` for click-stacking with our transparent close button (which the og doesn't have). White canvas leaked past `<main>`'s box on long lists because we weren't syncing the body's bg-class with theme — added `install-body-theme-sync!` (companion to `install-ui-prefs-persistence!`, same state-atom watch shape) so `document.body.className` follows `:ui/theme`. The og hides the same overflow via body bg-class propagation onto the browser's canvas.

3. **Textarea theme** (commit `2d35154`). The save-modal textarea was using `theme-text-class` (text color only) — in dark mode that was white text on browser-default white bg, effectively invisible. Switched to `theme-input-class` (text + bg + hover + active), matching both our top-level new-todo input and the JS UI reference line 110 (`textarea ... + theme suffix`).

**B-2 fix** (commit `0ffe0b4`). Reported by user: batch-import Submit was also closing the save modal. Cause: leftover `(close-current-modal! this)` from the initial impl that copied the Add-Item "act + close + refocus" pattern — Add Item doesn't have its own modal so the pattern didn't apply. Dropped that call; mutation, textarea-clear, and err-clear all still fire. Whether auto-close is the right default (or a settings-modal preference) is logged in `docs/ideas.md#modal-auto-close`.

**B-3 (open)** logged in `docs/bugs.md`: header menu icons (Save / About / Help) stay clickable during the review and delete-confirm modals; the og disables them in those states while keeping the theme toggle always enabled. Fix sketch in the bug entry; deferred because B-3 is a UX polish item and the current behaviour isn't functionally broken.

Five `/scripts/inspect-*.mjs` probes (`inspect-og-css`, `inspect-local-css`, `inspect-og-delete-modal`, `inspect-og-save-modal-long`, `inspect-og-long-list`) plus `snapshot-long-list.mjs` (drives our port to N items via Add Item clicks) shipped during this work. Kept in tree for future visual-parity passes.

Master runner: **69 specs / 498 assertions, all green. CLJS: 327 files, 0 warnings.** No new specs (visual + a single test assertion swap for B-2).
