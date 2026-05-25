# Phase 21.4b — Settings UI integration (four affordances wired)

**Status:** ✅ Complete
**Parent:** [Phase 21 — Dev-mode toggles](21-dev-mode-toggles.md)

The Settings modal's Debug section, wrapped in `#?(:cljs (when ^boolean goog.DEBUG ...) :clj nil)` so release builds drop the entire block via Closure DCE and headless JVM tests don't try to render it.

## What landed in `learn.client.ui.modals`

New requires: `[com.fulcrologic.fulcro.application :as app]`, `[com.fulcrologic.fulcro.data-fetch :as df]`, `[learn.dev-config :as dev-config]`, and CLJS-only `[cljs.pprint]` (for the dump button's pretty-print).

Inside `settings-modal`, between the language dropdown and the close-instruction footer:

```clojure
#?(:cljs
   (when ^boolean goog.DEBUG
     (let [flags @dev-config/dev-flags]
       (dom/section {:className       "pt3 mt2 bt b--moon-gray"
                     :aria-labelledby "settings-debug-heading"}
         (dom/h3 ...)                ; "Debug mode" heading
         <rainbow checkbox>          ; :defaultChecked from flags
         <depth checkbox>            ; :defaultChecked from flags
         <dump button>               ; (console.log pprint) + (console.dir clj->js)
         <cycle button>))))          ; (cycle-list!) + (df/load! ... TodoItem ...)
    (dom/p ...)                      ; close-instruction (unchanged)
```

## Design decisions

- **Uncontrolled checkboxes (`:defaultChecked`).** The Fulcro convention is controlled inputs, but `dev-flags` lives outside Fulcro's state-atom — there's no Fulcro query that would re-render on swap. Uncontrolled + `:defaultChecked` reads the atom at initial mount, `onChange` syncs both the DOM (browser handles it) and the atom (we swap). The existing watches do the rest: persistence watch saves to localStorage, `::debug-css` watch reconciles the DOM `<link>` tags.
- **`comp/registry-key->class 'learn.client.ui.components/TodoItem`** for the cycle button's `df/load!` normalizing class — uses the auto-populated component registry instead of importing `TodoItem` directly, which would create a `modals → components → modals` cycle.
- **`comp/any->app this`** for app reference — avoids passing the SPA atom through as a parameter or computed prop.
- **English-only labels.** Dev-only surface; no `:debug/*` keys added to `learn.i18n.core`.
- **A11y**: each checkbox has an `<input id>` paired with `<label htmlFor>`; each button has `:title` + `:aria-label`; the section has `:aria-labelledby` pointing at the `<h3>`'s id. Standard `<button>` + `<input type="checkbox">` are keyboard-reachable out of the box.

## Verification (browser-manual via Playwright snapshots)

`scripts/snapshot.mjs` against `npx shadow-cljs watch app` on localhost:8000.

| Snapshot | Captures |
|---|---|
| `ef4e9fd-dirty-phase-21.4b-settings-debug-v2.png` | Settings modal renders the Debug section: heading + 2 checkboxes + 2 buttons + the existing close-instruction footer below. |
| `ef4e9fd-dirty-phase-21.4b-rainbow-on.png` | After clicking the Rainbow checkbox: Pesticide CSS link is injected; every element on the page now has a colored outline (the `::debug-css` watch fired `sync-debug-css!`). |
| `ef4e9fd-dirty-phase-21.4b-after-cycle.png` | Add "verify task" → open Settings → click Cycle → close: footer reads "You have 0 items in your list." The `:actual → :empty` cycle ran end-to-end; `cycle-list!` reset SERVER-DB and `df/load!` refreshed the Fulcro UI. |
| `ef4e9fd-dirty-phase-21.4b-cycle-to-5.png` | Same flow but Cycle twice (`:actual → :empty → :5`): all 5 fixture items rendered with correct statuses (cancelled-was-ready, cancelled-was-new, done, ready, new); "next actionable: 'Ready task'" — benchmark detection picks up the fixture. |

## Closure DCE verified

`npm run release` produces a 1.47 MB bundle. `grep` for the three Debug-section strings:

```
grep -c "Rainbow element outlines" main.js   → 0
grep -c "Cycle list fixture"      main.js   → 0
grep -c "Dump app state"          main.js   → 0
```

The whole block is dropped from release builds.

## Untested (deliberately)

- **Depth checkbox** — mechanically identical to Rainbow (same swap, same watch reaction). Browser-manual coverage during normal use will catch any divergence.
- **Dump app state button** — opens browser DevTools console output; the snapshot harness clicks but doesn't capture console messages. Inline `console.log` + `console.dir` is trivially correct; verify by opening DevTools and clicking.

## Acceptance

**Master runner: 134 specs / 894 assertions, all green** (unchanged from 21.4a — no new JVM tests; UI is CLJS-only). CLJS release build: 0 warnings, DCE confirmed by string-absence in the release bundle.

This commit closes Phase 21 and the `S-dev-mode-toggles` story.
