# Phase 21.3 — Migrate debug-css plumbing + runtime-watch model

**Status:** ✅ Complete
**Parent:** [Phase 21 — Dev-mode toggles](21-dev-mode-toggles.md)

Mechanical move of the pesticide debug-CSS infrastructure from `learn.client.cljc` into `learn.dev-config.cljc`, plus a semantic upgrade: instead of reading a static `debug-css-options` def at init time, the new install function syncs the DOM to `dev-flags` and attaches a watch so flag flips at runtime (from the 21.4 Settings UI) take effect immediately — no reload required.

## Moved

From `learn.client.cljc`:
- `debug-css-options` def (the static `{:rainbow false :depth false}` map) — **deleted.** Replaced by `dev-flags` (21.2), keyed `:debug-css/rainbow?` / `:debug-css/depth?`.
- `ensure-debug-link!` private CLJS helper → `learn.dev-config/ensure-debug-link!`.
- `install-debug-css!` public CLJS fn → replaced by `install-debug-css-from-runtime!` (new shape; see below).
- The SAFETY comment block explaining `^boolean goog.DEBUG` gating → carried over to `dev-config` near the DOM-touching helpers.

## Added in `learn.dev-config`

- `debug-css-links` (CLJS-only private) — `{flag-key → {:marker-id ..., :href ...}}` map. Single place to declare the flag-to-stylesheet mapping; both ensure and remove paths read from it.
- `ensure-debug-link!` (CLJS-only private) — idempotent `<link>` injector (carried over verbatim from `learn.client`).
- `remove-debug-link!` (CLJS-only private) — new. Removes a `<link>` by marker id. Needed because flags can now flip false-to-true AND true-to-false at runtime (the old `install-debug-css!` only added).
- `sync-debug-css!` (CLJS-only private) — reconciles the DOM with a given flags map. Iterates `debug-css-links`; ensures present iff the flag is truthy, otherwise removes. Gated on `^boolean goog.DEBUG` so release builds drop the body.
- `install-debug-css-from-runtime!` (CLJC) — applies the current `@dev-flags` to the DOM, then attaches a watch (`::debug-css`) that re-syncs on every change. JVM no-op.
- `install-dev-config!` (CLJC) — one-call orchestrator: `(install-dev-flags-persistence!)` then `(install-debug-css-from-runtime!)`. Init calls this single fn.

## `learn.client.cljc` changes

- Removed the entire dev-config section (def + SAFETY comment + two CLJS-only fn defs).
- Added `[learn.dev-config :as dev-config]` to the require list.
- The CLJS `init` now calls `(dev-config/install-dev-config!)` at the top in place of the old `(install-debug-css!)`. The new call also handles 21.2's flag persistence, so flags survive reloads.

## Why the watch model

The original `install-debug-css!` read a source-edited def at page-load time. The only way to toggle visuals was: edit `debug-css-options`, hot-reload, refresh the browser. That's fine for a developer who already has the file open, but the whole point of S-dev-mode-toggles is to expose those switches in the Settings UI for any session. With the watch in place, 21.4's checkbox can call `(swap! dev-flags assoc :debug-css/rainbow? true)` and the DOM updates on the next tick — no refresh.

## Acceptance

Master runner — warm REPL **AND** a fresh JVM (per CLAUDE.md "verify with a fresh JVM after removing/renaming public vars" rule, since `debug-css-options` and `install-debug-css!` were both removed): **133 specs / 873 assertions, all green** — same as the 21.2 baseline. No new tests; 21.3 is a structural refactor with browser-manual verification deferred to 21.4 when the UI gives a way to actually flip the flags.
