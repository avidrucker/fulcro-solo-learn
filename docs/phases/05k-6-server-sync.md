# Phase 5K.6 — Server sync of review decisions

**Status:** ✅ Complete
**Parent:** [Phase 5K — Prioritize/review flow](05k-prioritize-review.md)

The chart's `:yes` action now persists its state-map mutation to SERVER-DB. Three pieces:

- **Server (`resolvers.clj`)** — added `sync-list-mutation` with `::pc/sym 'learn.client/sync-list`, same one-line `record-list-items` body as the other server mutations. Registered in `all-resolvers`.
- **Client (`client.cljc`)** — added a thin `defmutation sync-list` with no `(action ...)` body (the chart has already mutated the state-map via `ops/assign`) and a `(remote [env] (remote-list-items env))` that ships the post-action items vector.
- **Chart (`chart.cljc`)** — `yes-action` now returns a third op alongside the state-map assign and cursor advance: `(fop/invoke-remote `[(learn.client/sync-list ~{:list/items items'})] {})`. The Fulcro integration's data-model dispatches that op through `rc/transact!`, which goes through the loopback remote, which lands on `sync-list-mutation`.

`:no` and `:quit` don't mutate the list, so they don't sync.

A `review chart syncs Yes decisions to the server` specification in `client_test.clj` covers the happy path (post-`:yes` SERVER-DB reflects the promotion, list order preserved) and the negative cases (`:no` / `:quit` leave SERVER-DB untouched).

**Gotcha worth keeping:** the chart-only testing env (CLJ-only `chart_test.clj`) uses the working-memory data-model, which has no `:fulcro/invoke-remote` handler — it logs a harmless WARN ("Operation not understood"). The chart's state-map and cursor assertions still hold; we bumped that test ns's timbre `:min-level` to `:error` to keep test output quiet rather than try to mock the op away.

**Acceptance:** 37 specs / 324 assertions, all green. Warm ~2.0 s.
