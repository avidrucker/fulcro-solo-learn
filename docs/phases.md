# Project Phases

Tracking document for the Fulcro learning project's evolution from a toy todo app to an AutoFocus implementation on a full Fulcro/Pathom/Datomic stack. Note: each phase is saved roughly as a single commit (and some phases have inbetween patch/fix commits too).

**Status legend:**
- ✅ Complete
- 🟡 In progress
- ⬜ Pending

---

## ✅ Phase 1 — Single defsc

Single Fulcro component (`Todo`). Hand-built initial state, no
normalization yet.

**Files:** `client.cljc`
**Key concept:** `defsc` macro, `:query`/`:ident`/`:initial-state`,
component factory.

---

## ✅ Phase 2 — Composition and normalization

Parent component (`TodoList`) querying for child idents. Normalized
client DB. Tools for reading from normalized state.

**Files:** `client.cljc`
**Key concept:** Idents (`[:todo/id <uuid>]`), `comp/get-query`,
`merge/merge-component`, `nsh/dissoc-in` and friends.

---

## ✅ Phase 3 — Mutations with `*`-suffix helpers

Pure state-map → state-map helpers (`add-todo*`, `delete-todo*` etc.)
wrapped by thin `defmutation` shells. Establishes the discipline that
business logic stays out of mutation bodies.

**Files:** `client.cljc`
**Key concept:** Mutation/helper separation. The mutation thread state
into the helper and back; the helper is the testable unit.

---

## ✅ Phase 4 — Fake remote via `lr/sync-remote`

Headless TDD setup. The "server" is an atom; the "remote" is a
synchronous loopback that runs against the parser. Lets tests exercise
real mutation pipelines without network or browser.

**Files:** `client.cljc`, `server.clj`, an initial parser/handler.
**Key concept:** `(lr/sync-remote parser/handler)` as the bridge from
client to server-side in-process.

---

## ✅ Phase 5 — Pathom 2

Replaced the hand-rolled `cond`-based parser with Pathom 2 resolvers
and mutations, composed via `pc/connect-plugin`. Broken into:

- **5A–5F**: Resolvers, mutations, the registry/parser, parameterized
  queries (`:status` filter on `:all-todos`).
- **5G**: Plugins — logging plugin (`*debug?*`-gated), error-handling
  plugin (`Throwable` catch), `p/post-process-parser-plugin
  p/elide-not-found`.

**Files:** `parser.clj`, `resolvers.clj`, `server.clj`
**Key concepts:** `pc/defresolver`, `pc/defmutation`, `::pc/sym` for
wire-symbol decoupling, plugin order (outer wraps inner), `:ast :params`
for reading parameterized query parameters.

---

## ✅ Phase 5H — Schema migration: `:todo/done?` → `:todo/status`

Replaced the boolean `:todo/done?` with a four-value enum
`:todo/status` (`:status/{new,ready,done,cancelled}`) plus a
`:todo/was` field for capturing prior status during cancellation.
Migration touched every layer — pure helpers, mutations, UI,
resolvers, tests.

**Files:** `client.cljc`, `server.clj`, `resolvers.clj`,
`client_test.clj`, `resolvers_test.clj`
**Key concepts:** Namespaced-keyword discipline (one rename per layer,
all `grep`-able); test fossils from removed helpers cleaned up;
`set-status*` with `:todo/was` capture; `affects-only?` test helper.

**Final state:** 16 specs / 57 assertions green via the master test
runner.

---

## ✅ Phase 5I — AutoFocus domain operations

Build out the pure AutoFocus domain model in `learn.model.list`, with
Guardrails `>defn` contracts. Replace the inline `add-todo*` rule in
`client.cljc` with delegation to the domain function.

### ✅ 5I.0 — Schema reference doc

Wrote `docs/SCHEMA.md` to capture vocabulary, entities, invariants,
the auto-mark rule, error types, validation strategy, and codebase
layout. Anchors all later phases.

### ✅ 5I.0.5 — Initial Malli schema file (deferred Guardrails)

Created `src/learn/model/schema.cljc` with Malli schemas using plain
`def`. Added Malli to deps. Guardrails added then immediately rolled
back due to a version conflict between `1.2.9` and `fulcro-spec 3.2.8`.

### ✅ 5I.1 — Add Guardrails 1.2.16 + refactor schema to `>def` registry

Upgraded Guardrails to the version mandated by the fulcro-spec-tdd
skill. Refactored `schema.cljc` to use `>def` with namespaced keywords
so schemas register in the Guardrails-extended Malli registry and can
be referenced by keyword from `>defn` specs elsewhere.

Enablement: added `-Dguardrails.enabled=true` to the `:test` alias
`:jvm-opts`. Bridge from Guardrails registry to Malli's default via
`(mr/set-default-registry! (mr/composite-registry (m/default-schemas)
(mr/mutable-registry gr.reg/schema-atom)))` so `m/validate` resolves
`>def`'d schemas.

**Files:** `deps.edn`, `src/learn/model/schema.cljc`
**Acceptance met:** Existing 16 specs pass; `(s/valid? ::s/todo
s/example-todo)` returns `true` from REPL.

### ✅ 5I.2 — `model.list/benchmark-item`

Pure read function: returns the last `:status/ready` todo from a vector,
or `nil`. The simplest domain function — establishes the pattern for
the rest.

**Files:** `src/learn/model/list.cljc`, `test/learn/model/list_test.cljc`
**Acceptance:** Spec covers no-ready (4 sub-cases), one-ready (3
sub-cases), multiple-ready / last-wins (3 sub-cases).
`(>defn benchmark-item [items] [::schema/items => (? ::schema/todo)] ...)`.

### ✅ 5I.3 — `model.list/auto-markable?` and `auto-mark`

`auto-markable?` is a predicate over items. `auto-mark*` promotes the
first new item to ready if the list is auto-markable; otherwise returns
items unchanged.

**Decision made:** JS-port discrepancy #5 — the
original `automark` reads the function reference instead of calling
it (`if (!isAutoMarkableList)` not `if (!isAutoMarkableList(tasks))`).
Fixed in the Clojure port: `auto-mark` calls `(auto-markable? items)` properly.

**Naming change:** Dropped the `*` suffix from `auto-mark` (was
`auto-mark*` in earlier roadmap). The `*` suffix is reserved for
state-map → state-map helpers (see `client.cljc`'s `add-todo*`,
`set-status*`); model-layer functions take items vectors in and out,
so the suffix doesn't apply. When Phase 5J wraps this in a Fulcro
mutation, that mutation's body might call `auto-mark*` if a state-map
helper is needed.

**`model.item` deferred.** SCHEMA.md §10 anticipates a `model.item.cljc`
for status predicates. We kept `new?` and `ready?` as `defn-` in
`model.list` because two predicates with two callers don't yet justify
the extra file. Promote to `model.item` when a third caller appears
(likely 5J's `cancel-todo` needing `done?`/`cancelled?` for the refusal
check).

**Acceptance met:** 18 specs / ~88 assertions green. Specs cover empty
list, all-ready, mixed-state, idempotence, and "no ready items in mix"
cases.

### ✅ 5I.4 — `model.list/add-todo`

Appends a new todo with the AutoFocus add rule: `:status/ready` if no
ready items exist, else `:status/new`. Validates non-blank text via
the schema; returns Result-shaped map.

**Design:** Multi-arity `>defn` overload. 2-arg form `(add-todo items
text)` generates a fresh UUID and delegates to 3-arg form `(add-todo
items text id)`. The 3-arg form is the pure, testable core; specs
exercise it for deterministic assertions. A small set of specs verifies
the 2-arg form's UUID-generation behavior.

**Acceptance met:** 20 specs / ~103 assertions green. Specs cover blank
text (4 cases), empty-list-becomes-ready, no-ready-list-becomes-ready
(4 cases), with-ready-list-becomes-new (3 cases), and UUID generation
(3 cases).

### ✅ 5I.5 — Wire Fulcro client to domain functions

Refactor `add-todo*` in `client.cljc` to project the normalized state
into a denormalized vector, call `model.list/add-todo`, and project
back. The mutation becomes pure plumbing.

**Fixture migration (prerequisite):** `client_test.clj`'s `fixture-state`
used integer todo ids; `::schema/items` requires `:uuid`. Migrated to
`fixture-id-1` / `fixture-id-2` (distinct UUIDs from `server-id-*` so
unit-test and integration-test UUID sets stay visually separable in
failure output).

**Semantic note (correctness improvement, not regression):** The
empty-list case now produces `:status/ready` (per AutoFocus rule)
instead of `:status/new` (old hardcoded). The pre-existing
"into an empty list" test didn't assert status, so this change is
invisible to it; we added a new assertion that locks in the new
behavior.

**Acceptance met:** ~20 specs / ~111 assertions green. `add-todo*` now
delegates UUID generation, status rule, and blank-text validation to
`learn.model.list/add-todo`. Blank-text input no-ops the state (the
error result is swallowed for now; UI surface deferred to a later phase).

### ✅ 5I.6 — Coverage check and master test run

Run the master test runner, confirm all specs green, capture
performance numbers. Update PHASES.md status.

**Numbers captured (post-5I.5):** 17 specs / 103 assertions, all green.
Cold run ~4s total (2.4s reload + 1.5s execution including 190ms
one-time Guardrails schema-compile warmup); warm run ~1.3s total
(985ms reload + 250ms execution).

**`:covers` proof-system sealing deferred to post-5J** — currently at
17 specs, threshold for the payoff is ~20+. 5J's specs will cross it.

---

## ✅ Phase 5J — Cancel, complete-benchmark, clone

Build the rest of the AutoFocus mutation set:
- `cancel-todo` — refuses on `:done`/`:cancelled`, captures `:todo/was`,
  fires auto-mark
- `complete-benchmark-item` — completes the last ready, fires auto-mark
- `clone-todo` — appends a new todo with the source's text

Each is a `>defn` in `model.list`, with a Fulcro mutation that
delegates to it. Server-side Pathom mutations added so `(remote [_]
true)` lights up.

### ✅ 5J.1 — `model.list/cancel-todo`

Cancels a todo by id, capturing the prior status as `:todo/was`, then composes `auto-mark` over the result. Refuses `:error/item-not-found` (missing id) or `:error/cannot-cancel` (target is `:done`/`:cancelled`).

**Decision:** double-cancel and cancel-on-done both rejected — diverges from the JS source's silent overwrites. Closed SCHEMA.md §14. See `docs/js_source_reference.md` for the JS comparison.

**Acceptance:** 8 components / ~25 assertions.

### ✅ 5J.2 — `model.list/complete-benchmark-item`

Completes the benchmark (last `:status/ready` by list order) by marking it `:status/done`, then composes `auto-mark`. Refuses `:error/no-actionable-items`.

**Decision:** no `:todo/was` capture — there is no un-complete operation, so unlike cancel there is nothing to record.

**Acceptance:** 6 components / 25 assertions.

### ✅ 5J.3 — `model.list/clone-todo`

Appends a new todo with the source's text; clone status follows `add-todo`'s rule (`:ready` when no ready exists, else `:new`), not the source's. Source is unchanged. Refuses `:error/item-not-found` on missing id.

**Decision (matches JS):** any source status is clone-eligible. Schema docs describe the typical use case (done/cancelled resurrection); model layer doesn't enforce. UI can hide the affordance on actionable items.

**Reference doc added:** `docs/js_source_reference.md` — signatures + divergences for every JS domain function.

**Acceptance:** 7 components / 28 assertions.

### ✅ 5J.4 — Wire Fulcro client mutations to model

New state-helpers in `client.cljc` (`cancel-todo*`, `complete-benchmark-item*`, `clone-todo*`) follow the `add-todo*` pattern: denormalize → call `model.list` → reproject via `sync-items` (also new, private). Refusals return state-map unchanged.

The `cancel-todo` mutation was rewired from `set-status*` to `cancel-todo*`, so it now fires auto-mark, refuses on `:done`/`:cancelled`, and refuses missing ids. Two new mutations added: `complete-benchmark-item` and `clone-todo`. List-ident hardcoded `[:list/id 1]` (singleton design — flagged inline for multi-list generalization).

**Acceptance:** 25 specs / 217 assertions, all green. Cold ~3s / warm ~1.4s.

### ✅ 5J.5 — Server-side Pathom mutations for remote sync

Server mirrors the client's normalized shape (`:list/id` + `:todo/id`), with `server/items` / `server/write-items` as the projection helpers. Each server-side Pathom mutation (`add-todo`, `cancel-todo`, `complete-benchmark-item`, `clone-todo`) is the same one-line `record-list-items` call — the server is dumb storage. All AutoFocus domain logic stays on the client.

Client mutations now flip `(remote [env] (remote-list-items env))`, which sends the post-action denormalized items vector to the server as `:list/items`. UUIDs propagate naturally (no tempid mechanism needed yet).

**Decision:** rejected an alternative where the server runs `model.list` too. Single source of domain truth (client) is simpler, makes the server replaceable (Datomic, Postgres, etc.) without porting logic, and matches the user's "frontend handles list/item processing" stance. A future phase can add a server-side validator that rejects ill-shaped lists; for now, trust.

**Acceptance:** 25 specs / 230 assertions, all green. Warm ~1.7 s.

---

## ✅ Phase 5K — Prioritize/review flow

Build `learn.model.review` plus a Fulcrologic statechart that orchestrates the binary review process. JS-port `handle-review-decision` is replaced by the chart itself (transitions express Yes/No/Quit decisions).

**Decisions locked in:**
- JS discrepancy #1 (prioritizable list): list-position rule — last `:new` must come after last `:ready` in list order. Diverges from the JS `lastNew.id > lastReady.id` (which assumed monotonic int ids; UUIDs in the port can't use ordering).
- JS discrepancy #4 (review-decision return shape): when a `handle-review-decision`-equivalent is needed, return Result-shaped — but in practice this work is absorbed by the statechart's transitions.
- Statecharts introduced in 5K.4 (skill imported from Desktop). Pure functions in 5K.1–5K.3 stay testable in isolation.

### ✅ 5K.1 — `model.review/prioritizable?`

Pure predicate. True iff the list has ≥1 `:status/ready`, ≥1 `:status/new`, AND the last `:new` index > the last `:ready` index. Created `src/learn/model/review.cljc` + `test/learn/model/review_test.cljc`.

**Acceptance:** 3 components / 14 assertions covering missing-ready/missing-new/empty, last-new-at-or-before-last-ready (false cases), and last-new-after-last-ready (true cases, including interleaved `:done`/`:cancelled`).

**Totals:** 26 specs / 244 assertions, all green.

### ✅ 5K.2 — `model.review/initial-cursor` + `next-cursor`

Two pure cursor-position helpers using the `::review-cursor` schema's `-1` sentinel:
- `next-cursor [items from-index]` — first `:status/new` index at-or-after `from-index`, else `-1`. Callers wanting "advance past current" pass `(inc cursor)`.
- `initial-cursor [items]` — first `:status/new` at-or-after the last `:status/ready`, else `-1`. Implemented as `next-cursor` composed with a last-ready lookup; returns `-1` gracefully on non-prioritizable lists.

**Acceptance:** 2 specs / 17 new assertions (next-cursor: 4 -1-cases + 5 positive; initial-cursor: 3 -1-cases + 5 positive).

**Totals:** 28 specs / 261 assertions, all green.
### ✅ 5K.3 — `model.review/current-question`

Pure formatter. Takes `[items cursor]`, returns the prompt `"In this moment, are you more ready to '{cursor-text}' than '{benchmark-text}'?"` on valid input, or `nil` when the cursor is out of range or the list has no benchmark. Delegates benchmark lookup to `learn.model.list/benchmark-item` (review namespace now requires model.list).

**Decision:** returns `nil` (not the JS error-string variants) on degenerate input. The model layer's responsibility is question-or-not; UI maps `nil` to "no question to ask". This avoids leaking presentation strings into the domain.

**CLJC note:** uses `str` instead of `format` so the function compiles unchanged for both `.clj` and `.cljs` targets.

**Acceptance:** 2 components / 7 new assertions. **Totals:** 29 specs / 268 assertions, all green.
### ✅ 5K.4 — Review statechart (`learn.review.chart`)

Single-file chart definition using `com.fulcrologic/statecharts 1.3.0` (added to `deps.edn`). Two states (`:review.state/inactive` / `:review.state/active`), four events (`:event.review/start|yes|no|quit`). Yes/No use `convenience/handle` (targetless event handlers); the cursor-walks-off-end exit is an eventless guarded transition in `:active` that fires when expression-fns leave `:cursor` at `-1`. The chart owns a copy of items in its data model for now — 5K.5 will swap the expression-fn bodies for Fulcro alias reads against live app state.

**Decision (locked in):** chart's eventless auto-exit pattern. Yes/No handlers don't branch — they just compute new cursor; the chart's config does the work. Matches statechart-skill `patterns.md` idiom ("Use the configuration directly", not duplicated state flags).

**Gotcha (worth bookmarking):** the testing-env mocks unmocked expressions by default — guards silently return nil and no transitions fire. Pass `:mocking-options {:run-unmocked? true}` when building the env (now baked into `new-env` helper in `chart_test.clj`). Future chart tests in this project should do the same.

**Noise control:** statecharts library emits a lot of `:debug` logs. `chart_test.clj` sets `taoensso.timbre` `:min-level` to `:warn` at namespace load so subsequent test runs stay readable.

**Acceptance:** 5 components / 19 new assertions covering start-guard rejection, happy-path :start seeding, Yes/No advancement and item promotion, eventless auto-exit on walk-off-end, and :quit. **Totals:** 34 specs / 287 assertions, all green. Warm ~3.0 s.
### ✅ 5K.5 — Client wiring (chart session lifecycle + UI affordances)

Stitch the review chart into the live Fulcro app. Broken into cycles so each red-green pass stays bite-sized; server sync deferred to 5K.6.

**Decisions locked in:**
- Shared denormalization helpers (previously `defn-` in `client.cljc`) promoted to `learn.util.normalized` once the chart became a second caller. Keeps `client.cljc` focused on UI/mutations.
- Chart reads items from `(:fulcro/state-map data)` at the singleton ident `[:list/id 1]` and mutates state-map via path-based `(ops/assign [:fulcro/state-map :todo/id <id> :todo/status] :status/ready)`. No local `:items` in the chart's session data — the only session-local datum is `:cursor`.
- Singleton review session at well-known id `:review-session`.
- UI will dispatch via **direct `scf/send!`** from onClicks — no thin mutation wrappers around chart events (avoids one indirection layer per affordance).
- Statecharts installed with `:event-loop? false` so headless tests pump deterministically via `scf/process-events!`. The same install will need re-evaluation when Phase 7 introduces a real browser.
- Expression-fns are variadic (`& _`) — the testing env calls them with `[env data]` (2 args) while the Fulcro install calls with `[env data event-name event-data]` (4 args); a single signature covers both. The Fulcro arity is called out explicitly in `install-fulcro-statecharts!`'s docstring as a crash-on-mismatch hazard.

### ✅ 5K.5 Cycle A — Chart reads/writes the Fulcro state-map

- **A.1** Extracted `denormalize-list-items` and `sync-items` from `client.cljc` `defn-`s into `learn.util.normalized` (new namespace). Both keep their Guardrails contracts.
- **A.2** Rewrote `learn.review.chart-test` to seed `:fulcro/state-map` via `t/goto-configuration!` (so chart-only unit tests don't need a Fulcro app). Confirmed RED on all 4 chart specs.
- **A.3** Replaced chart expression-fns' `:items` reads with `norm/denormalize-list-items (:fulcro/state-map data) [:list/id 1]`; `yes-action` now emits a path-based assign into `:fulcro/state-map`. Chart's session-local data is just `:cursor`.

**Acceptance:** 5 chart specs / 23 assertions green. Totals: 33 specs / 291 assertions.

### ✅ 5K.5 Cycle B — `init` installs/registers/starts the chart

`learn.client/init` now calls `scf/install-fulcro-statecharts!` (with `:event-loop? false`), `scf/register-statechart!` under key `::review-chart`, then `scf/start!` at session id `:review-session`. A `scf/process-events!` pump drains the initial entry actions.

Made chart expression-fns variadic (`& _`) so they work in both calling conventions.

Added a `review chart wiring via init` specification in `client_test.clj` covering: session in `:inactive` after init; `:event.review/start` enters `:active` when the loaded list is prioritizable; `:event.review/yes` promotes the cursor todo in the live Fulcro state-map; `:event.review/quit` returns to `:inactive` without mutating todos.

**Acceptance:** 35 specs / 301 assertions, all green. Warm ~8s (the slow run includes app-build + load + chart install per spec).

### ✅ 5K.5 Cycle C — UI affordances (Start/Yes/No/Quit + question display)

`TodoList` now reads chart configuration via `scf/current-configuration` and renders one of two affordance blocks:
- `:inactive` → a single `Start Review` button.
- `:active` → the `current-question` text from `model.review/current-question` plus `Yes` / `No` / `Quit` buttons.

Each onClick goes through a private `send-and-pump!` helper that wraps `scf/send!` + `scf/process-events!`. The pump is necessary because the chart is installed with `:event-loop? false`; making it part of the send keeps the click handler synchronous (and incidentally keeps tests honest — no separate pump call after every simulated click).

A small `review-cursor` helper reads the chart's single session-local datum (`:cursor`) directly from `(:com.fulcrologic.statecharts/local-data ...)` in the live app state. Skipped a Fulcro query subscription on the chart entity to keep TodoList's `:query` short; tests force a render frame after each click so the chart's most recent state is visible.

Added a `review UI affordances` specification in `client_test.clj` with 4 components covering: initial inactive state (Start visible, no other buttons), clicking Start (enters :active, question + Yes/No/Quit appear), clicking Yes (state-map mutated, chart returns to :inactive on single-:new walk-off), and clicking Quit (no mutations, chart returns to :inactive).

**Acceptance:** 36 specs / 318 assertions, all green. Warm ~6.6 s.

### ✅ 5K.6 — Server sync of review decisions

The chart's `:yes` action now persists its state-map mutation to SERVER-DB. Three pieces:

- **Server (`resolvers.clj`)** — added `sync-list-mutation` with `::pc/sym 'learn.client/sync-list`, same one-line `record-list-items` body as the other server mutations. Registered in `all-resolvers`.
- **Client (`client.cljc`)** — added a thin `defmutation sync-list` with no `(action ...)` body (the chart has already mutated the state-map via `ops/assign`) and a `(remote [env] (remote-list-items env))` that ships the post-action items vector.
- **Chart (`chart.cljc`)** — `yes-action` now returns a third op alongside the state-map assign and cursor advance: `(fop/invoke-remote `[(learn.client/sync-list ~{:list/items items'})] {})`. The Fulcro integration's data-model dispatches that op through `rc/transact!`, which goes through the loopback remote, which lands on `sync-list-mutation`.

`:no` and `:quit` don't mutate the list, so they don't sync.

A `review chart syncs Yes decisions to the server` specification in `client_test.clj` covers the happy path (post-`:yes` SERVER-DB reflects the promotion, list order preserved) and the negative cases (`:no` / `:quit` leave SERVER-DB untouched).

**Gotcha worth keeping:** the chart-only testing env (CLJ-only `chart_test.clj`) uses the working-memory data-model, which has no `:fulcro/invoke-remote` handler — it logs a harmless WARN ("Operation not understood"). The chart's state-map and cursor assertions still hold; we bumped that test ns's timbre `:min-level` to `:error` to keep test output quiet rather than try to mock the op away.

**Acceptance:** 37 specs / 324 assertions, all green. Warm ~2.0 s.

---

## ⬜ Phase 6 — shadow-cljs + browser app (no real backend)

First time the project actually runs in a browser. The "server" is the
same `learn.server` atom + Pathom 2 parser we already built — just
compiled to JS and running alongside the client. The Fulcro book calls
this pattern `(mock-remote non-conflicting-resolvers)`; our existing
`lr/sync-remote parser/handler` is the same thing, so the loopback
stays. No HTTP, no jetty, no real backend.

This is a re-scope of the original "Phase 7 (real backend)" plan. A
true server-process backend isn't part of the AutoFocus learning arc —
the front-end-only design is the target. If a real backend is ever
wanted, it slots in as a much later, optional phase.

**Sub-steps:**
- ✅ **6.1** — Add `shadow-cljs.edn`, declare a browser target, get a
  trivial "Hello" JS bundle building. (`build` commit; deps come from
  deps.edn via the new `:cljs` alias; `npm install` bootstraps
  shadow-cljs + react/react-dom.)
- ✅ **6.2** — Convert `server.clj` / `resolvers.clj` / `parser.clj`
  to `.cljc`. Only one JVM-only construct surfaced: `(catch Throwable
  e ...)` in `parser`'s error-handling plugin — fixed with a reader
  conditional `(catch #?(:clj Throwable :cljs :default) e ...)`. The
  master test runner regex was tightened to `\.cljc?$` so `.cljs`
  files (browser-only) are excluded from JVM scans. `learn.main`
  briefly requires the three namespaces as a smoke test for CLJS
  compilation; Phase 6.3 will replace that with the real entrypoint.
- ⬜ **6.3** — Create a browser entrypoint (`learn.main.cljs` or
  similar) that mounts `Root` to a DOM node and calls `init`. Replace
  the headless-DOM rendering with real `react-dom`. Reuse
  `lr/sync-remote` as-is.
- ⬜ **6.4** — Verify the master CLJ test runner stays green AND the
  browser app loads, renders, and round-trips a Yes click through the
  in-process "server".

**Out of scope here:** persistence. Page reload still resets the seed.

---

## ⬜ Phase 7 — localStorage persistence

Add a watch on `SERVER-DB` that serializes to `js/localStorage` on
every change; hydrate from localStorage in `init` before mount, falling
back to the seed when no storage entry exists or the entry is
corrupted (failed `read-string`).

Choice: localStorage over IndexedDB. Sync API matches the project's
sync-everything design, the AutoFocus list will never approach the ~5
MB limit, and `pr-str` / `clojure.edn/read-string` round-trip is fewer
moving parts than IndexedDB's request-callback dance.

**Acceptance shape:**
- Reload-survival spec: dehydrate `SERVER-DB`, mount a fresh app,
  verify list state matches.
- Corruption fallback spec: write garbage to the storage key, mount,
  verify init falls back to seed (logs a warning).

CLJ-only side stays atom-only — no localStorage shim. CLJC split for
the storage adapter: `:cljs` writes to `js/localStorage`, `:clj` is a
no-op (or, if useful for tests, an in-memory atom). Either way the
master CLJ runner stays untouched.

---

## ⬜ Phase 8 — Production Pathom patterns

Per-request env, batch resolvers (N+1 prevention), `defmutation`
return values for optimistic UI.

Still useful in the front-end-only world — batching means fewer
parser calls per render, and a per-request env teaches the pattern
even when the "request" never leaves the JS runtime.

---

## ⬜ Phase 9 — Fulcro RAD basics

Migrate from hand-written `defsc` to RAD attributes. The schema we've
built in `learn.model.schema` gets re-expressed as RAD attribute
definitions.

**New skill:** `fulcro-rad`.

---

## ⬜ Phase 10 — RAD reports and forms

Use RAD's report and form components for the AutoFocus list UI.

**New skill:** `fulcro-rad-reports`.

---

## ⬜ Phase 11 — Statecharts in depth

If we used statecharts lightly in 5K, this phase doubles down: more
complex flows (e.g., import/export, conflict resolution).

---

## ⬜ Phase 12 — i18n

Internationalize via `fulcro-i18n`. The structured error keywords from
Phase 5 already separate domain from display strings — i18n drops in
cleanly.

**New skill:** `fulcro-i18n`.

---

## Optional / out of arc

- **DataScript swap.** Replace the atom-as-database with DataScript.
  Adds datalog queries and history. Pure learning detour — the
  AutoFocus model doesn't need it. Could slot in any time after Phase
  7 if the user wants exposure.
- **Real backend (Datomic / Postgres / etc.) + tempids.** Would
  re-introduce HTTP, async coordination, and tempid rewrites.
  Excluded from the current learning arc by the front-end-only
  decision; lives here as a possible future direction.

---

## Recurring infrastructure (cross-cutting)

These aren't phases per se — they evolve continuously across phases.

- **Test runner**: Master runner (terse + verbose), per-ns runners.
- **REPL workflow**: Cursive custom commands, project-only reload to
  keep iteration under a second.
- **Specs**: Started as plain `def` in 5I.0.5, upgraded to `>def`
  registry in 5I.1, paired with `>defn` from 5I.2 onward.
- **Doc layer**: `docs/SCHEMA.md` is the canonical reference;
  `docs/phases.md` (this file) tracks progress;
  `docs/learned_while_making_this.md` is the running retrospective.

### Deferred infrastructure items (to be picked up in a later phase)

These are mandated by the `fulcro-spec-tdd` skill or otherwise
recommended, but deferred because they don't yet pay their own cost
at the current project size. Each has a planned landing phase.

- **Guardrails `:all` mode + `:covers` proof-system sealing.** The
  fulcro-spec-tdd skill mandates `:covers` metadata on every
  specification for transitive coverage and staleness detection.
  This requires Guardrails mode `:all` (currently `:runtime`), which
  populates an externs registry at compile time. Defer to **Phase
  5I.6** or **after**: seal all existing specs in a batch once we
  have ~20+ specs and the proof-system payoff (catching stale tests
  after refactors) starts to matter. Specs written in the meantime
  are structurally seal-ready (single-function focus, multi-triple
  `assertions` blocks, no `behavior` macro).

- **Per-test guardrails-test.edn config with `:throw? true`.** The
  fulcro-spec-tdd skill recommends a separate config file so test
  runs throw on contract violations instead of merely logging them.
  Defer until we have a `>defn` whose contract is meaningful enough
  that a silent log would mask a bug — likely Phase 5I.4 onward.

- **Pre-warm `dev/user.clj` for fast first-run REPL.** Identified in
  Phase 5H as a performance optimization (30s cold start →
  sub-second first run). Defer until we're restarting the REPL often
  enough that the cold-start cost matters; with a stable deps.edn,
  most days we restart 0 times.
