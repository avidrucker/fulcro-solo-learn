# Phase 21.1 — Dev fixtures (`items-5`, `items-26`)

**Status:** ✅ Complete
**Parent:** [Phase 21 — Dev-mode toggles](21-dev-mode-toggles.md)

Pure-data fixtures for the dev list-cycler. New namespace `learn.dev-fixtures` (CLJC), pure data only, no Fulcro / no Pathom / no IO dependencies — loadable on JVM and CLJS alike. Consumed by the cycler logic that lands in 21.2.

Two fixtures:

- **`items-5`** — five items covering all four statuses. List order is `[cancelled (was :ready), cancelled (was :new), :done, :ready, :new]`. Active subsequence (non-cancelled, non-done) is `[:ready :new]`, satisfying SCHEMA.md §5 (all `:ready` precede all `:new`). Stable UUIDs (`51111111-…` through `55555555-…`) for readable failure output.
- **`items-26`** — 26 items lettered `a`..`z`. First item `:status/ready`, remaining 25 `:status/new`. Computed via `mapv` over `(range 26)` with deterministic UUIDs (`26000000-0000-0000-0000-<index>`). Stress-test for long-list overflow / scrolling behaviour. SCHEMA.md §5 trivially holds.

The `:empty` and `:actual` cycle positions aren't fixture data — they live in `learn.dev-config` (21.2): `:empty` re-uses `learn.server/empty-state`, and `:actual` is a marker keyword that triggers a localStorage-snapshot restore.

**TDD trace:** Wrote `test/learn/dev_fixtures_test.cljc` first (2 specs / 11 assertions covering count, status sequence, `:todo/was` capture on the two cancelled items, schema validity via `::learn.model.schema/items`, and an explicit `ready-before-new?` invariant assertion). Stubbed `learn.dev-fixtures` with empty vectors, observed RED (5 failures, 2 `IndexOutOfBoundsException` errors on `nth` of empty), then implemented to GREEN.

**Acceptance:** 128 specs / 851 assertions, all green via the master runner (was 126 specs / 840 assertions before 21.1, so +2 specs / +11 assertions). 0 warnings.
