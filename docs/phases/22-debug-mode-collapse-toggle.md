# Phase 22 — Collapsible Debug section in Settings (S-dev-mode-collapse-toggle)

**Status:** ✅ Complete

UX refinement of Phase 21's Debug section. Previously the four affordances (rainbow / depth checkboxes + Dump / Cycle buttons) were always visible when the Settings modal was open. Now they're hidden behind a disclosure-toggle button that defaults to collapsed.

Layout, before: `<h3>Debug mode</h3>` heading + four affordances always rendered.
Layout, after: one `<button>` labelled `debug mode (OFF)` / `debug mode (ON)` at the top; the four affordances render only when expanded.

## The architecture decision worth remembering

**First attempt put `:debug-ui/expanded?` in the `dev-flags` atom (alongside `:debug-css/rainbow?` / `:debug-css/depth?`).** The toggle click did `swap! dev-config/dev-flags update :debug-ui/expanded? not` and called `app/schedule-render!` to nudge Fulcro. **It didn't work** — Fulcro's optimised renderer compares props, and TodoList's query doesn't reference `dev-flags`, so it sees no change and skips the re-render. A Playwright probe (`scripts/probe-debug-toggle.mjs`, since deleted) confirmed: `aria-expanded` stayed `"false"` after the click. `comp/transact!! this []` (empty tx) didn't help for the same reason.

**Fix: move `:debug-ui/expanded?` into Fulcro state at `[:list/id 1 :ui/debug-mode-expanded?]`.** Added to `TodoList`'s `:query` + `:initial-state`; destructured into props; passed to `settings-modal` as a 4th arg. The disclosure-toggle now uses `m/toggle!!` instead of `swap! + transact!!`. The synchronous toggle changes a key TodoList queries, so Fulcro's prop-diff sees the change and re-renders naturally. `aria-expanded` flips, the conditional panel appears.

**Lesson:** if it's UI state that needs to drive renders, it belongs in Fulcro state. `dev-flags` stays for things whose effect is OUT-of-Fulcro (DOM CSS injection, localStorage cursor / snapshot writes) — the existing watches handle those side effects without re-render dependencies.

## Persistence

Deliberately NOT in `ui-prefs-whitelist` — every fresh page load starts collapsed. Matches the UX intent: the dev affordances are opt-in *per session*, not sticky preferences.

## A11y

Disclosure widget pattern:
- Button carries `aria-expanded` (`"true"`/`"false"` string) reflecting current state.
- Button carries `aria-controls="settings-debug-panel"` pointing at the panel's id.
- Section carries `aria-labelledby="settings-debug-toggle"` pointing back at the button.
- Standard `<button type="button">` so keyboard activation works (Enter / Space).

## TDD trace

Pure-data changes (`:ui/debug-mode-expanded?` in TodoList's initial-state) have no specs of their own — the existing `client_test.clj` tests for the Settings modal continue to pass because the JVM branch of the `#?(:cljs ...)` block renders nothing. Browser-manual verification was the source of truth: `docs/snapshots/7519e5d-dirty-phase-22-collapsed-v2.png` shows the OFF state (no affordances), `docs/snapshots/7519e5d-dirty-phase-22-expanded-v3.png` shows the ON state with all four affordances revealed and the button label flipped.

## Acceptance

Master runner: **134 specs / 894 assertions, all green** (unchanged from 21.4b — no new JVM tests). CLJS dev build compiles cleanly. Release build still DCEs the entire Debug section per the `^boolean goog.DEBUG` gate.
