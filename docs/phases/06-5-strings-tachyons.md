# Phase 6.5 — Strings + Tachyons port to match the original JS UI

**Status:** ⬜ (stale marker in source; all sub-steps below are ✅)
**Parent:** [Phase 6 — shadow-cljs + browser app (no real backend)](06-shadow-cljs.md)

Reference: `docs/js_ui_reference.md` — captured 2026-05-12 from the upstream JS app (`pwa-autofocus-app`). Three concerns, addressed in sub-steps:

## 6.5.1 — Strings extracted into `learn.ui.strings`

**Status:** ✅

`learn.ui.strings.cljc` holds the 24 named constants verbatim from App.js plus the inline labels (button text, placeholders), tooltips, and templated lines (`list-count-line`, `next-actionable-line`, `version-line`). `learn.client.cljc` now references it for `app-name`, button labels, the cancel-task title, the input placeholder, and review-button tooltips. Renamed visible labels to match the JS source: `Add` → `Add Item`, `Start Review` → `Prioritize`. Tests updated accordingly. `learn.model.review/current-question` keeps its prompt template inline (function's reason for existing); flagged in the strings doc for the Phase 12 i18n pass.

## 6.5.2 — Tachyons 4.12.0 loaded via CDN

**Status:** ✅

Tachyons 4.12.0 added via a single `<link>` in `resources/public/index.html` (unpkg CDN). No shadow-cljs config changes, no npm step. Visible effects after hard-reload: normalize baseline, system font stack. Actual class application lands in 6.5.3.

## 6.5.x — Snapshot infra (Playwright)

**Status:** ✅

Playwright as a npm devDependency, `scripts/snapshot.mjs` writes a full-page PNG of the running app to `docs/snapshots/<short-hash>[-<label>].png`. `-dirty` suffix marks snapshots taken with uncommitted changes. Forward-only; retroactive replay over old commits would need a bash loop and a per-commit `shadow-cljs compile` (deferred). Baseline saved at `e60306d` showing the post-6.5.2 state (Tachyons loaded, no classes yet applied).

## 6.5.3 — Tachyons class strings applied (light theme)

**Status:** ✅

Tachyons class strings (light theme) applied per `docs/js_ui_reference.md` §B. Restructured `Root` to render `<main>` with a `<header>` (h1) and `<section>` shell, matching the JS App.js hierarchy; `TodoList` now renders only the form + button row + list + footer. `TodoItem` got conditional Cancel/Clone (Cancel on `:new`/`:ready`, Clone on `:done`/`:cancelled` — same flip as the JS port), a status-icon fallback to `:todo/was` for cancelled rows, and the `fw6` benchmark-bold weighting via a `:benchmark?` computed prop from the parent. The Prioritize button now stays in the layout during a review session (dimmed + disabled) instead of being swapped out — also matching the JS port; one test assertion flipped from "no longer visible" to "stays rendered, disabled during review". The hidden `New TODO` `<label>` keeps the `h/type-into-labeled!` test affordance working (uses Tachyons' `clip` SR-only class).

## 6.5.4 — `modal-shell` helper

**Status:** ✅

Private `modal-shell` helper in `learn.client.cljc` takes `:on-close` + `:close-label` opts and renders the JS port's modal pattern: absolutely-positioned outer `<section>` with `bg-white-90` tint over the app-container (`position: relative` on Root's section anchors it), an inner `measure-narrow` content column, and an optional transparent full-area close button behind the content. Review affordances now render inside `modal-shell {}` with no `:on-close` (review modal must use Quit, matching the JS spec). Inlined rather than extracted to its own ns until a second modal needs it. Also extended `scripts/snapshot.mjs` with a `--click <text>` flag so the modal state can be captured.

## 6.5.5 — Inline SVG icons + custom CSS + Montserrat font

**Status:** ✅

Inline SVG icons + custom CSS + Montserrat font. Original plan called for `react-icons`/font-awesome; revised after noting the JS port ships **inline SVGs** at `src/core/icons.js`. New ns `learn.ui.icons` holds the 5 icons `TodoItem` consumes (`dot-circle`/`empty-circle`/`filled-circle` for status, `cancel-x`/`repeat-arrow` for action buttons), each a `def`'d `dom/svg` element with `fill="currentColor"` so surrounding color utilities work. `status-icon` returns `nil` for `:status/cancelled` so the existing `was`-fallback path keeps working. Custom CSS (20-ish rules: `.h-15`, `.lh-135`, `.tracked-custom`, `.hover-button` + `@media (hover: hover)`, `.line-clamp-3`, `.mb1-butlast`, `.break-word`, plus light/dark hover transition helpers) ported verbatim to `resources/public/css/app.css` and linked from `index.html` AFTER tachyons.min.css so cascade ties go to the local file. Montserrat 400/600/800 loaded via Google Fonts preconnect + stylesheet links. The remaining 6 icons from the JS source (info circle, question circle, save disk, lightbulb solid/regular, wrench) stay un-ported — their consuming UI (theme toggle, save modal, etc.) doesn't exist yet.

**Out of scope here:** features we lack the data layer for — delete-list mutation, import/export, theme toggle, conflict resolution, debug modal. Those land in 6.6+ as separate phases.
