# Phase 5K.4 — Review statechart (`learn.review.chart`)

**Status:** ✅ Complete
**Parent:** [Phase 5K — Prioritize/review flow](05k-prioritize-review.md)

Single-file chart definition using `com.fulcrologic/statecharts 1.3.0` (added to `deps.edn`). Two states (`:review.state/inactive` / `:review.state/active`), four events (`:event.review/start|yes|no|quit`). Yes/No use `convenience/handle` (targetless event handlers); the cursor-walks-off-end exit is an eventless guarded transition in `:active` that fires when expression-fns leave `:cursor` at `-1`. The chart owns a copy of items in its data model for now — 5K.5 will swap the expression-fn bodies for Fulcro alias reads against live app state.

**Decision (locked in):** chart's eventless auto-exit pattern. Yes/No handlers don't branch — they just compute new cursor; the chart's config does the work. Matches statechart-skill `patterns.md` idiom ("Use the configuration directly", not duplicated state flags).

**Gotcha (worth bookmarking):** the testing-env mocks unmocked expressions by default — guards silently return nil and no transitions fire. Pass `:mocking-options {:run-unmocked? true}` when building the env (now baked into `new-env` helper in `chart_test.clj`). Future chart tests in this project should do the same.

**Noise control:** statecharts library emits a lot of `:debug` logs. `chart_test.clj` sets `taoensso.timbre` `:min-level` to `:warn` at namespace load so subsequent test runs stay readable.

**Acceptance:** 5 components / 19 new assertions covering start-guard rejection, happy-path :start seeding, Yes/No advancement and item promotion, eventless auto-exit on walk-off-end, and :quit. **Totals:** 34 specs / 287 assertions, all green. Warm ~3.0 s.
