# Phase 21 — Dev-mode toggles (S-dev-mode-toggles)

**Status:** ✅ Complete

A runtime-toggleable "Debug mode" section in the Settings modal that gives developers control over four dev-only affordances: rainbow-outlines toggle, depth-backgrounds toggle, app-state dump button, and a list-fixture cycler (actual → empty → 5-item → 26-item → actual). The whole section is gated on `^boolean goog.DEBUG` so release builds drop it via Closure dead-code elimination.

Full design — architecture, dep graph, fixture invariants, test plan, coexistence of source-defaults with the runtime UI — lives in [`docs/ideas.md`](../ideas.md) under tag `dev-mode-toggles`.

## Sub-phases

- ✅ [21.1 — Dev fixtures (`items-5`, `items-26`)](21-1-dev-fixtures.md)
- ✅ [21.2 — `learn.dev-config` namespace (flags + persistence + pure cycler)](21-2-dev-config.md)
- ✅ [21.3 — Migrate debug-css plumbing + runtime-watch model](21-3-debug-css-migration.md)
- ✅ [21.4a — Pure `cycle-step` orchestrator + CLJS `cycle-list!` wrapper](21-4a-cycle-step.md)
- ✅ [21.4b — Settings UI integration (four affordances wired)](21-4b-settings-ui.md)
