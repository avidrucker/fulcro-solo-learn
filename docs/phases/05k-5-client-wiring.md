# Phase 5K.5 — Client wiring (chart session lifecycle + UI affordances)

**Status:** ✅ Complete
**Parent:** [Phase 5K — Prioritize/review flow](05k-prioritize-review.md)

Stitch the review chart into the live Fulcro app. Broken into cycles so each red-green pass stays bite-sized; server sync deferred to 5K.6.

**Decisions locked in:**
- Shared denormalization helpers (previously `defn-` in `client.cljc`) promoted to `learn.util.normalized` once the chart became a second caller. Keeps `client.cljc` focused on UI/mutations.
- Chart reads items from `(:fulcro/state-map data)` at the singleton ident `[:list/id 1]` and mutates state-map via path-based `(ops/assign [:fulcro/state-map :todo/id <id> :todo/status] :status/ready)`. No local `:items` in the chart's session data — the only session-local datum is `:cursor`.
- Singleton review session at well-known id `:review-session`.
- UI will dispatch via **direct `scf/send!`** from onClicks — no thin mutation wrappers around chart events (avoids one indirection layer per affordance).
- Statecharts installed with `:event-loop? false` so headless tests pump deterministically via `scf/process-events!`. The same install will need re-evaluation when Phase 7 introduces a real browser.
- Expression-fns are variadic (`& _`) — the testing env calls them with `[env data]` (2 args) while the Fulcro install calls with `[env data event-name event-data]` (4 args); a single signature covers both. The Fulcro arity is called out explicitly in `install-fulcro-statecharts!`'s docstring as a crash-on-mismatch hazard.

## Cycle A — Chart reads/writes the Fulcro state-map

- **A.1** Extracted `denormalize-list-items` and `sync-items` from `client.cljc` `defn-`s into `learn.util.normalized` (new namespace). Both keep their Guardrails contracts.
- **A.2** Rewrote `learn.review.chart-test` to seed `:fulcro/state-map` via `t/goto-configuration!` (so chart-only unit tests don't need a Fulcro app). Confirmed RED on all 4 chart specs.
- **A.3** Replaced chart expression-fns' `:items` reads with `norm/denormalize-list-items (:fulcro/state-map data) [:list/id 1]`; `yes-action` now emits a path-based assign into `:fulcro/state-map`. Chart's session-local data is just `:cursor`.

**Acceptance:** 5 chart specs / 23 assertions green. Totals: 33 specs / 291 assertions.

## Cycle B — `init` installs/registers/starts the chart

`learn.client/init` now calls `scf/install-fulcro-statecharts!` (with `:event-loop? false`), `scf/register-statechart!` under key `::review-chart`, then `scf/start!` at session id `:review-session`. A `scf/process-events!` pump drains the initial entry actions.

Made chart expression-fns variadic (`& _`) so they work in both calling conventions.

Added a `review chart wiring via init` specification in `client_test.clj` covering: session in `:inactive` after init; `:event.review/start` enters `:active` when the loaded list is prioritizable; `:event.review/yes` promotes the cursor todo in the live Fulcro state-map; `:event.review/quit` returns to `:inactive` without mutating todos.

**Acceptance:** 35 specs / 301 assertions, all green. Warm ~8s (the slow run includes app-build + load + chart install per spec).

## Cycle C — UI affordances (Start/Yes/No/Quit + question display)

`TodoList` now reads chart configuration via `scf/current-configuration` and renders one of two affordance blocks:
- `:inactive` → a single `Start Review` button.
- `:active` → the `current-question` text from `model.review/current-question` plus `Yes` / `No` / `Quit` buttons.

Each onClick goes through a private `send-and-pump!` helper that wraps `scf/send!` + `scf/process-events!`. The pump is necessary because the chart is installed with `:event-loop? false`; making it part of the send keeps the click handler synchronous (and incidentally keeps tests honest — no separate pump call after every simulated click).

A small `review-cursor` helper reads the chart's single session-local datum (`:cursor`) directly from `(:com.fulcrologic.statecharts/local-data ...)` in the live app state. Skipped a Fulcro query subscription on the chart entity to keep TodoList's `:query` short; tests force a render frame after each click so the chart's most recent state is visible.

Added a `review UI affordances` specification in `client_test.clj` with 4 components covering: initial inactive state (Start visible, no other buttons), clicking Start (enters :active, question + Yes/No/Quit appear), clicking Yes (state-map mutated, chart returns to :inactive on single-:new walk-off), and clicking Quit (no mutations, chart returns to :inactive).

**Acceptance:** 36 specs / 318 assertions, all green. Warm ~6.6 s.
