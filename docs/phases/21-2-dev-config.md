# Phase 21.2 — `learn.dev-config` namespace (flags + persistence + pure cycler)

**Status:** ✅ Complete
**Parent:** [Phase 21 — Dev-mode toggles](21-dev-mode-toggles.md)

New CLJC namespace `learn.dev-config` carries the dev-toggle infrastructure that the Settings UI (21.4) will wire to buttons. Two concerns under one roof:

## Debug-CSS flags

- **`dev-flags-defaults`** — source-of-truth map. Both keys (`:debug-css/rainbow?`, `:debug-css/depth?`) default `false` so a release build (which drops this whole namespace via call-site `^boolean goog.DEBUG` gating) never accidentally surfaces debug visuals.
- **`dev-flags`** — runtime-mutable atom. Initialized to defaults; hydrated from localStorage at startup via `install-dev-flags-persistence!`.
- **`merge-flags`** — defensive merge. For each key in `dev-flags-defaults`, uses the loaded value iff it's a boolean; otherwise falls back to the default. Unknown keys in the loaded map are dropped — a stray localStorage entry can't smuggle itself into the flags atom.
- **`install-dev-flags-persistence!`** — CLJC with internal branching, mirroring `learn.util.storage`'s install pattern. CLJS hydrates the atom + attaches a save-watch; JVM returns the atom unchanged so callers don't need conditional branches at the call site.

## Four-position list-cycler

Pure pieces (CLJC, JVM-tested):

- **`next-cycle-position`** — wrap-around: `:actual → :empty → :5 → :26 → :actual`. Unknown / nil input defaults to `:actual` so a stale-cursor reload can't get stuck.
- **`cycle-action`** — pure dispatcher returning `{:from <pos> :to <next-pos> :do <action>}`. Three actions cover the cycle:
  - `:snapshot-and-apply` — leaving `:actual` (capture SERVER-DB to the snapshot key)
  - `:apply` — fixture → fixture (snapshot already exists)
  - `:restore-and-clear` — returning to `:actual` (restore + clear snapshot)
- **`position->items`** — denormalized items vector per position. `:5` and `:26` come from `learn.dev-fixtures`; `:empty` returns `[]`; `:actual` returns `nil` (sentinel for the orchestrator: "restore from snapshot, no fixture").

CLJS-only localStorage helpers (untested here; browser-manual once 21.4 wires the UI):

- `load-flags!` / `save-flags!` — round-trip the flags slice under `autofocus.dev-flags`.
- `load-cursor!` / `save-cursor!` — round-trip the cycler position under `autofocus.dev-list-cursor`. `load-cursor!` validates against the known-position set and returns nil for anything else.
- `load-snapshot!` / `save-snapshot!` / `clear-snapshot!` — round-trip the snapshotted SERVER-DB under `autofocus.dev-list-snapshot`. Snapshot is captured on the first cycle step away from `:actual` and cleared on the wrap back.

All localStorage I/O is `try/catch`-swallowed for quota / privacy-mode resilience — one missed write/read is preferable to a runtime crash.

## What 21.2 deliberately defers

- **`cycle-list!` orchestrator** — the CLJS-only function that wires `cycle-action` to actual snapshot/restore + SERVER-DB swap. Lands in 21.4 next to the button that triggers it (avoids stranding an unreferenced public fn at this sub-phase boundary).
- **State-dump button helper** — trivially `(.log js/console ...)` + `(.dir js/console (clj->js ...))`. Sits in `learn.dev-config` too but added in 21.4 alongside the UI affordance.
- **`install-debug-css!` migration** — that's 21.3's scope.

## TDD trace

Wrote `test/learn/dev_config_test.cljc` first (5 specs covering `dev-flags-defaults` shape, `merge-flags` defensiveness, `next-cycle-position` mapping including unknown / nil defaults, `cycle-action` dispatch across all 4 starting positions + nil, and `position->items` lookup including the `:actual` nil sentinel). Stubbed the namespace with empty-/nil-returning defs, observed RED (21 of 22 assertions fail; the one passing was `position->items :actual => nil` matching the stub's nil return). Implemented to GREEN.

## Acceptance

**Master runner: 133 specs / 873 assertions, all green** (+5 specs / +22 assertions from baseline). Warm run ~3 min on this machine (cold reload of all test namespaces).
