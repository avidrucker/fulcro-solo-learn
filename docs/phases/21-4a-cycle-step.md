# Phase 21.4a — Pure `cycle-step` orchestrator + CLJS `cycle-list!` wrapper

**Status:** ✅ Complete
**Parent:** [Phase 21 — Dev-mode toggles](21-dev-mode-toggles.md)

The cycler's actual data transformation, extracted as a pure CLJC function so the heavy lifting is JVM-tested; with a thin CLJS-only wrapper that handles the actual side effects. Splitting along this seam keeps the unavoidable browser-manual surface (localStorage + `SERVER-DB` reset) tiny — ~15 lines of plumbing — and puts the rest under red-green TDD.

## `cycle-step` (pure CLJC)

```clojure
(cycle-step current-cursor server-db snapshot)
  => {:cursor'     <next position>
      :server-db'  <next SERVER-DB value>
      :snapshot'   <new snapshot value, or nil>
      :snapshot-op :save | :keep | :clear}
```

Three actions, mapped from `cycle-action`'s output:

| `cycle-action` `:do` | `:snapshot'` | `:snapshot-op` | `:server-db'` |
|---|---|---|---|
| `:snapshot-and-apply` (leaving `:actual`) | input server-db (captured) | `:save` | `empty-state` + new fixture's items |
| `:apply` (fixture → fixture) | input snapshot (preserved) | `:keep` | `empty-state` + new fixture's items |
| `:restore-and-clear` (returning to `:actual`) | `nil` | `:clear` | input snapshot, or `empty-state` if snapshot is nil (defensive) |

The 3-way `:snapshot-op` exists so the CLJS wrapper can avoid a redundant localStorage write on the `:keep` path (the common cycler step between fixtures).

`:server-db'` is computed via `server/write-items` over `server/empty-state` as the structural template — this gives each fixture a clean `:list/id` + empty `:todo/id` table that's then populated with the fixture's items. The `:restore-and-clear` path bypasses `write-items` entirely and returns the snapshot map verbatim.

## `cycle-list!` (CLJC, JVM no-op)

Tiny glue:

```
load cursor + load snapshot
→ cycle-step
→ reset! SERVER-DB :server-db'
→ save-cursor! :cursor'
→ dispatch on :snapshot-op (:save / :keep / :clear)
→ return :cursor'  (caller can trigger df/load! to refresh UI)
```

CLJC body uses `#?(:cljs ... :clj nil)` so the JVM branch is a no-op — callers don't need conditional branches at their call sites.

## TDD trace

Wrote 6 components in `dev_config_test.cljc`:

1. **Leaving `:actual` (snapshot-and-apply)** — input non-empty SERVER-DB, nil snapshot. Verify cursor advances to `:empty`, snapshot captures the input verbatim, `:snapshot-op :save`, server-db is the empty fixture.
2. **Fixture → fixture (apply, snapshot exists)** — verify cursor advances, snapshot preserved unchanged, `:snapshot-op :keep`, server-db has items-5 loaded.
3. **`:5 → :26`** — same shape as #2, sanity check with items-26.
4. **Returning to `:actual` (restore-and-clear)** — verify cursor wraps to `:actual`, server-db is the restored snapshot verbatim, snapshot' is nil, `:snapshot-op :clear`.
5. **Defensive: `:26 → :actual` with nil snapshot** — falls back to `empty-state`; `:snapshot-op` still `:clear` (clearing an absent key is a no-op).
6. **Defensive: nil cursor** — treated as `:actual`, snapshot-and-apply path.

Stubbed `cycle-step` returning nil. RED: 19 of 21 cycle-step assertions failed (2 incidental passes — `(server/items nil ...) => []` matches the empty-fixture expectation by accident). Implemented to GREEN.

## Acceptance

**Master runner: 134 specs / 894 assertions, all green** (+1 spec / +21 assertions from the 21.3 baseline of 133 / 873). Warm REPL only — `cycle-step` is a new public var, not a removed/renamed one, so the CLAUDE.md "verify with fresh JVM" rule doesn't apply.

The CLJS `cycle-list!` wrapper is not exercised here — its localStorage reads + SERVER-DB reset run in the browser only, and verification lands in 21.4b once the Settings button calls it.
