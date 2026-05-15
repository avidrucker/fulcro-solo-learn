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

## ✅ Phase 6 — shadow-cljs + browser app (no real backend)

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
- ✅ **6.3** — Split `learn.client/init` via reader conditionals. The
  JVM branch is byte-for-byte the existing behaviour (`h/build-test-app`
  + headless `lr/sync-remote`, used by the spec suite). The CLJS branch
  uses `app/fulcro-app` + a tiny new CLJC `learn.util.remote/sync-remote`
  shim (the headless library's `lr/sync-remote` is JVM-only). `init` is
  `^:export`ed in CLJS so shadow-cljs can call it as the module's
  `:init-fn`. `learn.main.cljs` (the 6.2 smoke test) was removed —
  shadow-cljs now points directly at `learn.client/init`. One forward
  reference (`review-session-id` used in `TodoList`'s render before it
  was `def`'d below) had to move up: CLJ tolerates the forward ref at
  runtime, CLJS rejects it at compile time.
- ✅ **6.4** — Browser app loads and round-trips. Surfaced three bugs
  along the way, all fixed in one `fix ... phase 6 bugfix` commit:
  1. **Review state subscription** — `TodoList` read chart state via
     `scf/current-configuration` (a side-channel Fulcro can't see), so
     the optimized renderer skipped re-rendering after Yes/No/Quit.
     Headless tests masked this by calling `h/render-frame!` after
     every click. Fix: ident-joins in `:query` against
     `[::sc/session-id :review-session]` and `[::sc/local-data :review-session]`
     so Fulcro knows the component depends on those paths.
  2. **Fulcro Inspect 1.x wiring** — the deprecated
     `com.fulcrologic.fulcro.inspect.preload` was logging "Inspect
     NOT installed" because Inspect 1.x requires both
     `com.fulcrologic.devtools.chrome-preload` *and* an explicit
     `(fulcro.inspect.tool/add-fulcro-inspect! spa)` call. Both wired
     in.
  3. **`goog.reflect.cache is not a function`** at runtime — the
     **shaded** `closure-compiler` jar pulled in by ClojureScript
     1.12.42 bundles `lib/{base,goog,reflect}.js`, which shadow-cljs
     mis-classifies as JS sources, producing a duplicate provide for
     `goog.reflect` (stripped vs full). Fix: top-level
     `:exclusions [com.google.javascript/closure-compiler]` on
     `org.clojure/clojurescript` in deps.edn. shadow-cljs supplies
     `closure-compiler-unshaded` itself, so nothing else broke.

**Out of scope here:** persistence (page reload resets seed) and
styling (Phase 6.5).

### ⬜ 6.5 — Strings + Tachyons port to match the original JS UI

Reference: `docs/js_ui_reference.md` — captured 2026-05-12 from the
upstream JS app (`pwa-autofocus-app`). Three concerns, addressed in
sub-steps:

- ✅ **6.5.1** — `learn.ui.strings.cljc` holds the 24 named constants
  verbatim from App.js plus the inline labels (button text,
  placeholders), tooltips, and templated lines (`list-count-line`,
  `next-actionable-line`, `version-line`). `learn.client.cljc` now
  references it for `app-name`, button labels, the cancel-task title,
  the input placeholder, and review-button tooltips. Renamed visible
  labels to match the JS source: `Add` → `Add Item`, `Start Review` →
  `Prioritize`. Tests updated accordingly. `learn.model.review/current-question`
  keeps its prompt template inline (function's reason for existing);
  flagged in the strings doc for the Phase 12 i18n pass.
- ✅ **6.5.2** — Tachyons 4.12.0 added via a single `<link>` in
  `resources/public/index.html` (unpkg CDN). No shadow-cljs config
  changes, no npm step. Visible effects after hard-reload: normalize
  baseline, system font stack. Actual class application lands in 6.5.3.
- ✅ **6.5.x (snapshot infra)** — Playwright as a npm devDependency,
  `scripts/snapshot.mjs` writes a full-page PNG of the running app to
  `docs/snapshots/<short-hash>[-<label>].png`. `-dirty` suffix marks
  snapshots taken with uncommitted changes. Forward-only; retroactive
  replay over old commits would need a bash loop and a per-commit
  `shadow-cljs compile` (deferred). Baseline saved at `e60306d`
  showing the post-6.5.2 state (Tachyons loaded, no classes yet
  applied).
- ✅ **6.5.3** — Tachyons class strings (light theme) applied per
  `docs/js_ui_reference.md` §B. Restructured `Root` to render `<main>`
  with a `<header>` (h1) and `<section>` shell, matching the JS App.js
  hierarchy; `TodoList` now renders only the form + button row + list
  + footer. `TodoItem` got conditional Cancel/Clone (Cancel on
  `:new`/`:ready`, Clone on `:done`/`:cancelled` — same flip as the
  JS port), a status-icon fallback to `:todo/was` for cancelled rows,
  and the `fw6` benchmark-bold weighting via a `:benchmark?` computed
  prop from the parent. The Prioritize button now stays in the
  layout during a review session (dimmed + disabled) instead of being
  swapped out — also matching the JS port; one test assertion flipped
  from "no longer visible" to "stays rendered, disabled during review".
  The hidden `New TODO` `<label>` keeps the `h/type-into-labeled!` test
  affordance working (uses Tachyons' `clip` SR-only class).
- ✅ **6.5.4** — Private `modal-shell` helper in `learn.client.cljc`
  takes `:on-close` + `:close-label` opts and renders the JS port's
  modal pattern: absolutely-positioned outer `<section>` with
  `bg-white-90` tint over the app-container (`position: relative` on
  Root's section anchors it), an inner `measure-narrow` content
  column, and an optional transparent full-area close button behind
  the content. Review affordances now render inside `modal-shell {}`
  with no `:on-close` (review modal must use Quit, matching the JS
  spec). Inlined rather than extracted to its own ns until a second
  modal needs it. Also extended `scripts/snapshot.mjs` with a
  `--click <text>` flag so the modal state can be captured.
- ✅ **6.5.5** — Inline SVG icons + custom CSS + Montserrat font.
  Original plan called for `react-icons`/font-awesome; revised after
  noting the JS port ships **inline SVGs** at `src/core/icons.js`. New
  ns `learn.ui.icons` holds the 5 icons `TodoItem` consumes
  (`dot-circle`/`empty-circle`/`filled-circle` for status,
  `cancel-x`/`repeat-arrow` for action buttons), each a `def`'d
  `dom/svg` element with `fill="currentColor"` so surrounding color
  utilities work. `status-icon` returns `nil` for `:status/cancelled`
  so the existing `was`-fallback path keeps working. Custom CSS
  (20-ish rules: `.h-15`, `.lh-135`, `.tracked-custom`,
  `.hover-button` + `@media (hover: hover)`, `.line-clamp-3`,
  `.mb1-butlast`, `.break-word`, plus light/dark hover transition
  helpers) ported verbatim to `resources/public/css/app.css` and
  linked from `index.html` AFTER tachyons.min.css so cascade ties go
  to the local file. Montserrat 400/600/800 loaded via Google Fonts
  preconnect + stylesheet links. The remaining 6 icons from the JS
  source (info circle, question circle, save disk, lightbulb
  solid/regular, wrench) stay un-ported — their consuming UI
  (theme toggle, save modal, etc.) doesn't exist yet.

**Out of scope here:** features we lack the data layer for — delete-list
mutation, import/export, theme toggle, conflict resolution, debug
modal. Those land in 6.6+ as separate phases.

---

## ✅ Phase 7 — localStorage persistence + UI feature parity

The user's list survives page reloads. `learn.util.storage` watches
`SERVER-DB` and dumps it to `js/localStorage` on every change; the CLJS
init branch hydrates the atom from storage before `df/load!` runs so
the first render shows the persisted state.

Choice (locked in): localStorage over IndexedDB. Sync API matches the
project's sync-everything design, the AutoFocus list will never
approach the ~5 MB limit, and `pr-str` / `clojure.edn/read-string`
round-trip is fewer moving parts than IndexedDB's request-callback
dance.

### ✅ 7.1 — `learn.util.storage` ns

CLJC split:
- Pure `->edn` / `<-edn` (testable on JVM). `<-edn` returns `nil` on
  blank input, malformed EDN, reader-eval forms (`#=`), and **any
  non-map result** — the last guard catches `clojure.edn/read-string`
  succeeding on garbage like `"not edn"` by returning the symbol
  `'not`. Map-only contract is just strong enough that callers can
  always feed `(or (<-edn s) seed)` and trust the type.
- CLJS-only `save!` / `load!` / `clear!` wrap `js/localStorage` with
  try-catch swallows (quota-exceeded, privacy-mode disabled, etc.
  shouldn't crash a render).
- `install-persistence!` is CLJC: on CLJS it hydrates + attaches a
  watch on `SERVER-DB`; on JVM it's a no-op so callers can use a
  single signature.
- Storage key is `"autofocus.server-db"` — namespaced enough that an
  unrelated site key collision is implausible.

40 specs / 337 assertions, all green (added 3 specs / 13 assertions
for the round-trip + corruption + key-name properties).

### ✅ 7.2 — Wire into init

`learn.client/init`'s CLJS branch calls `install-persistence!` between
the Inspect setup and `start-chart!`, so the order is:

1. build app
2. register Inspect
3. **hydrate `SERVER-DB` + attach persistence watch**
4. start review chart
5. mount Root
6. `df/load!` (now reads the hydrated server)

JVM init is unchanged in behavior because `install-persistence!` is a
no-op there; the call is omitted from the `:clj` branch to keep the
test-driven path byte-for-byte identical.

`scripts/snapshot.mjs` grew `--type <text>` and `--reload` flags
(executed in argv order) so a single command captures the
type-then-add-then-reload-then-snap demo. Each `chromium.launch()` is
a fresh browser instance, so localStorage is empty at the start of
each snapshot run — runs are isolated automatically.

### ✅ 7.3 — Delete List + Mark Done + Enter-to-submit + refocus

Two remaining JS-port primary buttons added; the button row is now
two `dib` groups matching the JS source (Add/Delete on the left,
Prioritize/Mark Done on the right). `submit-add!` / `submit-delete!`
/ `submit-mark-done!` are inline `let`-bound handlers that wrap
`comp/transact!`, with the add/delete variants also calling
`focus-new-todo-input!` (a CLJC fn whose `:cljs` branch hits
`document.getElementById(...).focus()`; `:clj` no-op).

The form's `onSubmit` routes Enter through `submit-add!`. Action
buttons are `type="button"` so clicking them doesn't accidentally
submit the form.

The client-side `delete-all` defmutation now has a `(remote [env]
(remote-list-items env))` so persistence covers list-clear; a matching
`learn.client/delete-all` Pathom mutation was added to
`resolvers.cljc` (one-line `record-list-items` like the others).

Two new specs (`Delete List button`, `Mark Done button`) exercise the
click-through path on both client and SERVER-DB. Refocus and
Enter-to-submit are **browser-manual** — `h/` headless doesn't track
focus the way a real browser does, and lacks key-press simulation.

Implements **S-complete-benchmark** (UI), **S-delete-list** (UI +
persistence), **S-input-enter-submit** (browser-manual),
**S-input-refocus-after-delete** (browser-manual).

**Bonus fix in CLAUDE.md** — the master test runner now does an extra
`(require 'learn.parser :reload)` after the main reload loop, because
the alphabetical order makes `learn.parser` reload BEFORE
`learn.resolvers` — capturing a stale `all-resolvers` snapshot that
omits any newly-added Pathom mutation. The second reload fixes this.

**42 specs / 346 assertions, all green. CLJS: 326 files, 0 warnings.**

### ✅ 7.4 — Modal state foundation

`:ui/open-modal` lives on `[:list/id 1]`. Default `:none`. Other
values: `:about`, `:help`, `:save` (added in 7.5/7.6).

`set-open-modal*` is single-value, so it's mutex-by-construction —
opening any modal overwrites whatever was open. `toggle-open-modal*`
wraps it: if the requested modal is currently open, set to `:none`;
otherwise open. Defmutations `set-open-modal` and `toggle-open-modal`
expose both to transact!.

The existing `modal-shell` already supports `:on-close` — the
per-modal phases (7.5/7.6) wire it to `(toggle-open-modal :none)`
shorthand: `(comp/transact! this [(set-open-modal {:ui/open-modal :none})])`.

7 new specs cover open/replace/close/toggle/mutex. 44 specs / 353
assertions, all green. CLJS: 326 files, 0 warnings.

Implements **S-modal-mutex**, **S-modal-bg-close** (via existing
`modal-shell` `:on-close`), **S-modal-toggle-via-button** (via
`toggle-open-modal*`).

### ✅ 7.5 — About + Help modals

`info-circle` and `question-circle` SVGs added to `learn.ui.icons`.
Two header icon buttons rendered in Root via the new
`header-icon-button` helper, which puts the tooltip label in a
visually-hidden `<span class="clip">` so `h/click-on-text!` can find
the button by its label text in headless mode. About + Help modals
defined as small private fns (`about-modal`, `help-modal`) returning
`modal-shell` with the appropriate strings from `learn.ui.strings`.

The modals render inside TodoList's fragment via a `case` on
`:ui/open-modal` (the value driven by 7.4's mutations). `:on-close`
on each modal calls `(set-open-modal {:ui/open-modal :none})` —
clicking the transparent background button dismisses.

5 new specs cover: About content visible after click, About content
gone after bg-close, Help content visible, About→Help mutex (only
one open at a time).

**Bonus runner fix:** the master test runner now uses `:reload-all`
on each test namespace, which transitively reloads src namespaces in
dependency order. This is the general fix for the
client-references-icons / parser-references-resolvers cross-file
ref-capture issue. There's some `BUG: Internal error validating ...`
malli-registry noise during reload-all that doesn't affect test
correctness.

**46 specs / 368 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-about**, **S-help**, and exercises **S-modal-mutex**
/ **S-modal-bg-close** end-to-end.

### ✅ 7.6 — Import/Export modal (stubbed)

`save-disk` SVG added; third header icon button rendered before the
About/Help pair. `save-modal` renders the full JS-port markup:
Copy List URL button, Import (styled `<label>` wrapping a
`type="file" accept=".json"` hidden input), Export button, textarea
+ Submit. All four interactive elements use the new `stub-onclick`
helper — `(js/console.log "[stub]" label)` in CLJS, no-op on JVM.
Real behaviour lands in a later phase.

2 new specs cover: open via header icon → expected markup visible;
bg-close dismisses.

**47 specs / 378 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-import-export** (stubbed status, markup verified).

### ✅ 7.7 — Theme toggle (light/dark)

`lightbulb-solid` + `lightbulb-regular` SVGs added. `:ui/theme` lives
on `[:list/id 1]` (default `:theme/light`). Toggled by a 4th header
icon — `lightbulb-solid` while in light mode, `lightbulb-regular` in
dark mode (matches the JS port's "the icon shows what theme is
currently active").

Six private theme helpers (`dark?`, `theme-text-class`,
`theme-modal-bg-class`, `theme-input-class`,
`theme-primary-btn-suffix`, `theme-icon-btn-color`) return the
appropriate class suffix for the current theme. The existing
`btn-primary-class`, `btn-primary-dim-class`, `input-class`,
`review-btn-class`, `btn-icon-class`, and the save-modal helpers all
became 1-arg fns of theme. `modal-shell` accepts `:theme` in its
options map. Theme is threaded explicitly to all callers (no React
context / dynamic var).

Theme propagation:
- `Root` reads theme from `(:ui/theme list)` → applies
  `theme-text-class` to `<main>` → renders the lightbulb toggle.
- `TodoList` destructures `:ui/theme` from its props with
  `:or {theme :theme/light}` defaulting, passes to TodoItem via
  `comp/computed`, passes to modal helpers as an arg.
- `TodoItem` destructures `:theme` from computed; `btn-icon-class
  theme` picks `moon-gray` vs `mid-gray` for the per-row buttons.

3 new specs cover the state-helper flips and the toggle-via-click
behaviour. 49 specs / 383 assertions, all green. CLJS: 326 files, 0
warnings.

Implements **S-theme-toggle**.

### ✅ 7.8 — Visual comparison vs the deployed reference

`scripts/snapshot.mjs` grew a `--url <url>` flag — passing an external
URL bypasses the localhost dev server and saves the snapshot under
`docs/snapshots/reference/<label>.png` (no git-hash prefix; the
deployed app's UI state isn't tied to our commits). Captured the
deployed JS port at `?list=JTVCJTVE` (base64 of `[]` — empty list)
for an apples-to-apples eyeball comparison.

`docs/snapshots/reference/README.md` documents the workflow and is
the running diff log between our local and the deployed reference.
Eyeball pass against `6f992c0-phase-7.8-local-empty-dark.png` turned
up one mechanical fix: the JS port pads the FIRST header icon with
`pl3` and the rest with `pl2`. Wired via a `:first?` flag on
`header-icon-button`.

Future ratchet: a `pixelmatch`-based diff script would automate the
side-by-side diff. Not implemented; out-of-scope unless we want
visual-regression gating.

Implements **S-deployed-reference-comparison** (new story).

### ✅ 7.22 — B-5 fix: empty initial list for deployed app

Browser-first-visit bug that only surfaced after the 7.21 deploy:
`learn.server/SERVER-DB` is `defonce`-initialized to
`learn.server/initial-state` (the JVM-test seed — two demo todos),
and CLJS `init` never overrode it. Result: every new visitor to the
deployed app saw a pre-populated list.

Added `learn.server/empty-state` — same shape as `initial-state`
but with `:list/todos` `[]` and `:todo/id {}`. CLJS `init` now
`reset!`s SERVER-DB to that BEFORE `install-persistence!`, so:

- First visit, no localStorage, no URL → empty list (correct).
- Returning visit with localStorage → hydration overwrites the
  empty baseline (unchanged).
- URL with `?list=` → URL load logic overrides the baseline
  (unchanged).
- JVM tests → use `server/seed!`, which still resets to
  `initial-state`. Test suite is unchanged.

87 specs / 599 assertions, all green. CLJS: 327 files, 0 warnings.
Browser-manual verification: visit
`https://avidrucker.github.io/fulcro-solo-learn/` with localStorage
cleared.

Closes **B-5**.

### ✅ 7.21 — Deploy pipeline + content polish

User-driven housekeeping pass before moving on to Phase 8:

- **GitHub Actions workflow** (`.github/workflows/main.yml`):
  - Push to `main` builds + tests + deploys to GitHub Pages.
  - Setup: Java 21 + Clojure CLI + Node 20, cached.
  - `clojure -M:test:cljs -m test-runner` for tests (the
    `:cljs` alias is needed on the JVM classpath because
    fulcro-spec's macros pull in `cljs/test.cljc`, which
    references closure-compiler classes — we exclude the
    shaded closure-compiler jar in deps.edn for an unrelated
    shadow-cljs reflect.js bug, so the unshaded jar from
    shadow-cljs is the working dependency).
  - `npx shadow-cljs release app` for the release build.
  - `actions/upload-pages-artifact@v3` + `actions/deploy-
    pages@v4` publish `resources/public/`.
  - `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` so `npm install`
    doesn't pull a 250MB headless Chromium for the snapshot
    scripts that CI doesn't run.
- **`test/test_runner.clj`** — mirror of the master runner with a
  proper `(System/exit code)` so CI can fail the job on test
  failure. Discovers every `*-test` namespace under `test/`.
- **Relative asset paths in `index.html`**: `/css/app.css` →
  `css/app.css`, `/js/main/main.js` → `js/main/main.js`. Works
  both at the dev-server root and at the GH Pages subpath
  (`https://avidrucker.github.io/fulcro-solo-learn/`).
- **OG feature audit** (`/tmp/og-App.js` cross-referenced
  against our impl): five gaps remain — Import JSON file,
  Export JSON file, URL-length safeguard,
  review-state-persistence, online-event listener. First three
  promoted to ⬜ Planned stories; remaining two demoted to 🆒
  Nice-to-have. `S-keyboard-shortcuts` moved to 🆒 (the og
  never shipped keyboard shortcuts beyond Enter either).
- **`docs/changes.md`** (new): catalogues intentional
  divergences from the JS port (Add-Item dim-when-blank, header
  icons hard-disable, batch-text Submit keeps modal open,
  UUIDs vs integer ids, conflict-detection ignores UUIDs,
  statechart for the review flow, dual-platform `<body>`+`<main>`
  theming, shadow-cljs/clj-nrepl toolchain, deterministic
  statechart tests). Cross-linked from `docs/README.md`.
- **About-modal tech-stack copy**: ReactJS-flavored
  `info-string-2` swapped for Fulcro 3.9 + Pathom 2 +
  statecharts + shadow-cljs + Font Awesome + Tachyons.
- **Help-modal GitHub link**: now points at
  `github.com/avidrucker/fulcro-solo-learn/issues`.

**Required repo settings** (user action, can't be automated by
the workflow):
1. Settings → Pages → Build and deployment → Source =
   **"GitHub Actions"**.
2. Settings → Actions → General → Workflow permissions =
   **"Read and write permissions"** (or accept the per-job
   `pages: write` permission already declared in the workflow).
3. Push to `main` triggers deploy automatically;
   `workflow_dispatch` enables manual runs from the Actions tab.

Master runner: 87 specs / 599 assertions, all green. CLJS:
327 files, 0 warnings.

### ✅ 7.19 — PWA service worker + manifest (S-pwa-offline)

App is now installable as a PWA and runs offline once the shell has
been cached. Adapted from the og JS port's `serviceWorker.js`
(avidrucker/pwa-autofocus-app/public/serviceWorker.js), simplified
for our single-bundle shadow-cljs output.

Files added under `resources/public/`:
- **`sw.js`** — service worker. Version-bumped cache key
  (`autofocus-cache-v7.19`) cleared on activate. Pre-caches the
  shell on install (index.html, offline.html, manifest, CSS, JS,
  icon, Tachyons CDN, Google Fonts CDN). Network-first for
  navigations (fall back to cached index → offline.html);
  cache-first for static assets.
- **`manifest.webmanifest`** — basic PWA manifest with `standalone`
  display, scope-relative `start_url ./?source=pwa`, SVG icon.
- **`offline.html`** — minimal fallback page.
- **`icon.svg`** — AF-monogram placeholder.
- `scripts/verify-sw.mjs` — Playwright probe that dumps SW
  registration state + cache contents for browser-manual review.

Index.html got manifest link, theme-color meta, and an inline SW-
registration block. Browser-manual verification only — no JVM
tests.

Implements **S-pwa-offline** (Planned ⬜ → ✅).

### ✅ 7.18 — Conflict-resolution modal (S-conflict-modal)

When the page loads with `?list=<encoded>` AND localStorage has
saved state AND the two differ, a modal opens auto-magically with
both lists side-by-side and four buttons (Copy Link URL, Copy Local
URL, Keep Link, Keep Local). User must explicitly choose — no
background-click cancel (matches the JS port's "must choose"
contract).

Pure decision in `learn.util.url-encoding/decide-initial-list`
returns one of `{:source #{:seed :url :local :conflict} …}`. The
conflict branch defers SERVER-DB updates and writes a transient
stash + opens the modal post-mount.

`storage/install-persistence!` grew a `{:hydrated? bool}` return so
init can distinguish "localStorage was present" from "fell back to
seed". `:conflict` was already in the menu-disabled predicate from
Phase 7.14 (B-3 fix).

UI: `conflict-list-preview` helper renders read-only items with
status icons; cancelled rows fall back to `:todo/was`'s icon
matching the JS port's `statusToSymbol` recursion. Two mutations:
`keep-link-list` (syncs URL items → SERVER-DB via remote) and
`keep-local-list` (close modal + force URL bar refresh since the
items vector didn't change so `install-url-sync!` wouldn't fire).

4 new specs / 11 new assertions for the state-helpers + 1 spec /
7 assertions for `decide-initial-list`. Master runner: 87 / 596,
all green.

Implements **S-conflict-modal** (Planned ⬜ → ✅).

### ✅ 7.17 — Read `?list=` on page load (S-url-load-on-init)

Companion to 7.16's url-sync. When the page opens with
`?list=<encoded>`, decode it into items and overwrite SERVER-DB's
list. The seed and any localStorage-hydrated state get overridden
when the URL alone wins. (Move 2e refined this for the conflict
case.)

`parse-list-param` (pure CLJC) extracts `?list=<value>` from a
query string. `items-from-query-string` chains it with
`url-segment->items` and returns nil if no list param OR decode
fails. `items-from-current-url` (CLJS-only) reads
`window.location.search`. 2 new specs / 15 new assertions.

Implements **S-url-load-on-init** (Planned ⬜ → ✅).

### ✅ 7.16 — URL sync watch (S-url-sync-current-list)

Address bar now reflects the current list — the user can copy any
URL straight from the browser (without going through the Copy List
URL modal). Pattern mirrors `install-ui-prefs-persistence!`:
state-atom watch that change-detects on the denormalized items
vector at `[:list/id 1]` and calls a `url-setter` fn when items
differ.

1-arity production defaults to `replace-url-with-items!` (CLJS-
only — builds URL from `window.location` + encoded segment, calls
`history.replaceState`). 2-arity (tests) injects a recording
setter. 3 new specs / 7 new assertions. Wired in `init` after the
other install-* helpers.

Implements **S-url-sync-current-list** (Planned ⬜ → ✅).

### ✅ 7.15 — URL encoder OG-compat shape + decoder

Phase 7.11's encoder dumped our Fulcro shape verbatim — URLs we
produced wouldn't decode in the JS port. This phase makes the
output cross-compatible:

- `status->og-string` / `og-string->status` — status keyword ↔
  lowercase string.
- `items->og-shape` / `og-shape->items` — vector translation.
  Integer ids derived from list position; UUIDs assigned fresh on
  decode. `:was` preserved for cancelled items.
- `items->json` now translates to OG shape first. Single-:ready-
  item fixture pinned to the og's deployed URL fragment
  `JTVCJTdCJTIyaWQlMjI…JTdEJTVE`.

Decoder added: `base64-decode`, `js-url-decode`, `parse-json-array`
(JVM hand-rolled JSON, CLJS `js/JSON.parse`), `url-segment->items`
(full round-trip). Corrupt input returns nil at every layer —
caller treats as "fall back to seed".

14 specs / 69 assertions (was 5/20). All TDD-built.

### ✅ 7.14 — B-3 fix: header menu icons disable during review / delete-confirm

Per the JS port (`docs/js_ui_reference.md` line 149), Save / About /
Help disable when `isPrioritizing || showingDeleteModal ||
showingConflictModal`; Toggle Theme always enabled.

`header-icon-button` grew `:disabled?`. Root computes the predicate
`(or review-active? (contains? #{:delete-confirm :conflict} open-modal))`.
`:conflict` included pre-emptively for Phase 7.18.

Belt-and-suspenders: both the HTML `:disabled` attribute AND a nil
`onClick` are set when disabled. The attribute covers real browsers
(default click semantics); the nil handler covers the headless test
framework whose `click!` invokes onClick without checking
`:disabled`.

6 new specs / 11 new assertions. Closes **B-3**.

### ✅ 7.13 — Visual parity sweep + B-2 fix

Three visible diffs vs the deployed JS port were identified through
side-by-side snapshot comparison and corrected. The work isn't a
single feature so it's logged together here:

1. **Dark-mode visual parity** (commit `47d2cad`). Three fixes:
   `bg-near-black` → `bg-black` for the dark page bg (closes the
   #111 vs #000 gap below the modal); new `delete-confirm-btn-class`
   (w4 — JS UI reference line 99) replacing the reused review-btn-class
   (w3) on the No/Yes buttons; ported `-webkit-font-smoothing:
   antialiased` + `-moz-osx-font-smoothing: grayscale` + `display:
   flex; flex-direction: column` + `min-height: 100dvh` from the og's
   `index.css` into our `app.css` on `html, body, #app`.

2. **Long-list visual parity** (commit `4c91db8`). Two more fixes
   that only surface when the list overflows the viewport (26
   items): modal text was squeezed because `modal-shell`'s inner
   section had `pa3` that the og's `measure-narrow` lacked — dropped
   `pa3`, kept `relative z-1` for click-stacking with our
   transparent close button (which the og doesn't have). White
   canvas leaked past `<main>`'s box on long lists because we
   weren't syncing the body's bg-class with theme — added
   `install-body-theme-sync!` (companion to
   `install-ui-prefs-persistence!`, same state-atom watch shape)
   so `document.body.className` follows `:ui/theme`. The og hides
   the same overflow via body bg-class propagation onto the
   browser's canvas.

3. **Textarea theme** (commit `2d35154`). The save-modal textarea
   was using `theme-text-class` (text color only) — in dark mode
   that was white text on browser-default white bg, effectively
   invisible. Switched to `theme-input-class` (text + bg + hover +
   active), matching both our top-level new-todo input and the
   JS UI reference line 110 (`textarea ... + theme suffix`).

**B-2 fix** (commit `0ffe0b4`). Reported by user: batch-import
Submit was also closing the save modal. Cause: leftover
`(close-current-modal! this)` from the initial impl that copied the
Add-Item "act + close + refocus" pattern — Add Item doesn't have
its own modal so the pattern didn't apply. Dropped that call;
mutation, textarea-clear, and err-clear all still fire. Whether
auto-close is the right default (or a settings-modal preference)
is logged in `docs/ideas.md#modal-auto-close`.

**B-3 (open)** logged in `docs/bugs.md`: header menu icons (Save /
About / Help) stay clickable during the review and delete-confirm
modals; the og disables them in those states while keeping the
theme toggle always enabled. Fix sketch in the bug entry; deferred
because B-3 is a UX polish item and the current behaviour isn't
functionally broken.

Five `/scripts/inspect-*.mjs` probes (`inspect-og-css`,
`inspect-local-css`, `inspect-og-delete-modal`,
`inspect-og-save-modal-long`, `inspect-og-long-list`) plus
`snapshot-long-list.mjs` (drives our port to N items via Add Item
clicks) shipped during this work. Kept in tree for future
visual-parity passes.

Master runner: **69 specs / 498 assertions, all green. CLJS: 327
files, 0 warnings.** No new specs (visual + a single test
assertion swap for B-2).

### ✅ 7.12 (followup) — Batch import via the save modal textarea

Closes the last stubbed action in the save modal alongside Copy List
URL (Phase 7.11). The Submit button now wires through a full
TDD-built stack:

- `learn.model.list/import-from-string` — pure CLJC. Mirrors the JS
  port's `importTasksFromString` (`tasksIO.js`): split on `\n`, drop
  blank lines, reduce `add-todo` over the rest. Refuses with
  `:error/empty-import` on all-blank input. Each new todo follows
  `add-todo`'s status rule fresh per iteration — first non-blank line
  into an empty list becomes `:ready`, subsequent lines become `:new`
  (because :ready now exists).
- `learn.client/import-from-text*` — state-helper. Denormalize →
  model → `norm/sync-items` back. No-op on refusal.
- `learn.client/import-from-text` defmutation with remote (server
  side `learn.resolvers/import-from-text-mutation`, registered under
  `'learn.client/import-from-text`).
- UI wiring in `save-modal`: textarea is a controlled input bound to
  `:ui/textarea-import-text` via `m/set-string!`; Submit handler
  splits two ways (blank → `set-err! empty-textarea-err`; non-blank
  → run the mutation, clear textarea, clear err. **Modal stays
  open** per B-2 fix — see Phase 7.13).
- `:error/empty-import` added to the model schema enum so the
  `>defn` contract on `import-from-string` accepts the new error
  shape.

**8 new specs / 36 new assertions** (Layer 1: 8 specs / 20 assertions
in `model.list-test`; Layer 2-4: 3 specs / 16 assertions in
`client_test`). **69 specs / 498 assertions, all green. CLJS: 327
files, 0 warnings.**

Implements **S-import-batch-text** (new story); partially closes
**S-import-export**.

### ✅ 7.12 — Delete-list confirmation modal

Phase 7.3 had Delete List empty the list immediately, with a footnote
in `user_stories.md` that the JS port shows a confirm modal first.
This phase closes that gap.

`:ui/open-modal` grew a fourth value `:delete-confirm`, joining the
existing mutex (`:about`, `:help`, `:save`). `submit-delete!` now
splits two ways:
- Empty list → still surfaces `nothing-to-delete-err` (no modal for a
  no-op, matching the JS port).
- Non-empty list → opens `:delete-confirm` via `set-open-modal`. The
  actual `delete-all` mutation only fires when the user clicks Yes.

New `delete-confirm-modal` helper reuses `modal-shell` (transparent
background close = cancel, matching the other modals) and the
`review-btn-class` styling for its No/Yes buttons. Body text is
`s/confirm-list-delete` (already in strings.cljc since Phase 6.5).
Yes calls `delete-all`, closes the modal, clears any prior error,
and refocuses the input; No just closes.

3 new specs / 11 new assertions (opens-modal path, empty-list-bypass,
Yes commits, No cancels). Two existing specs (`Delete List button`
and `Error surfacing — Delete List on empty list`, plus
`Prioritize on non-prioritizable list`) were updated for the new
two-click flow. **65 specs / 456 assertions, all green. CLJS: 327
files, 0 warnings.**

Implements **S-delete-list-confirm** (new story); updates
**S-delete-list** for the two-step flow.

### ✅ 7.11 — Wire Copy List URL action

The Phase 7.6 Import/Export modal shipped with all four interactive
buttons hitting `stub-onclick`. This phase makes Copy List URL real:
clicking it writes the share URL to the user's clipboard.

New `learn.util.url-encoding` namespace implements the JS port's
three-step recipe (`btoa(encodeURIComponent(JSON.stringify(items)))`).
The empty-list case is pinned to the deployed reference fixture
(`[] → "JTVCJTVE"` — same value used in `?list=JTVCJTVE` on the
deployed JS port). Pure-CLJC: `js-url-encode` matches JS
`encodeURIComponent` (unreserved set `-_.~!'()*` + alpha/digit pass
through, space → `%20`, everything else UTF-8 %-encoded);
`base64-encode` uses `java.util.Base64` on JVM and `js/btoa` in CLJS;
`items->json` uses `js/JSON.stringify` in CLJS and a tiny
hand-rolled JSON encoder in CLJ (covers our items shape — vectors,
maps, keywords, strings, UUIDs).

`learn.client/copy-list-url!` (CLJS-only) reads `window.location` and
calls `navigator.clipboard.writeText` on the constructed URL.
Best-effort: silently no-ops on non-https/old-browser contexts where
the Clipboard API isn't present. `save-modal` grew a `todos` arg and
the Copy URL button's `onClick` now invokes `copy-list-url!` with the
current snapshot.

5 new specs / 20 new assertions cover each step (base64, URL-encode,
JSON), the full chain at the empty-list fixture, and the URL
composition. **63 specs / 437 assertions, all green. CLJS: 327 files,
0 warnings.**

Implements **S-copy-list-url** (new story); partially closes
**S-import-export** stub.

### ✅ 7.10 — Theme persists across reload (B-1 fix)

`:ui/theme` lives in Fulcro app state, NOT in `SERVER-DB`, so the
Phase 7 persistence didn't reach it — every reload reset theme to
`:theme/light`. Diagnosed in `docs/bugs.md` B-1.

Added a parallel persistence path in `learn.util.storage` for a small
whitelisted slice of `:list/id 1`. `ui-prefs-whitelist` is currently
`#{:ui/theme}`; future keys (zoom, layout, etc.) just append to the
set. Pure CLJC `extract-ui-prefs` / `apply-ui-prefs` (testable on JVM)
+ CLJS-only `save-ui-prefs!` / `load-ui-prefs!` /
`install-ui-prefs-persistence!`. The watch fires `save-ui-prefs!`
only when the extracted slice actually changes — every unrelated
state edit (modal open, err-msg, input typing) doesn't trigger a
write.

`learn.client/init` wires `install-ui-prefs-persistence!` *after*
`mount!` because the Fulcro state-atom isn't populated until then.

4 new specs / 19 new assertions cover the round-trip,
whitelist-defence-in-depth, and missing-key edge cases. **58 specs /
417 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-theme-persist**, closes **B-1**.

### ✅ 7.9 — Error message surfacing

`:ui/err-msg` (string or nil) lives on `[:list/id 1]`. The `set-err-msg`
mutation + `set-err-msg*` state-helper set or clear it. The error
renders inside the form, just below the input, with the JS port's
classes: `lh-135 red ml-auto mr-auto measure-narrow ma0 pt2`.

Switched the click semantics for Add Item, Delete List, and Mark Done
from "disabled when the action can't fire" to the JS port's pattern:
the button stays clickable but dimmed; clicking sets the relevant
error string. Strings already lived in `learn.ui.strings`:
- `s/empty-input-err` for blank Add Item
- `s/nothing-to-delete-err` for Delete List on an empty list
- `s/cannot-take-action-err` for Mark Done with no `:ready` items

Successful actions clear the prior error. Prioritize keeps its hard
`:disabled` when not prioritizable — the JS port has no matching
error string for that case.

6 new specs cover state-helper round-trip, the 3 invalid-action error
paths, and the clear-on-success path.

**53 specs / 395 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-error-add-blank**, **S-error-delete-empty**,
**S-error-mark-done-no-actionable** (new stories).

---

## ✅ Phase 8 — Statecharts in depth (closed as a doc artifact)

Originally scoped as "refactor the conflict modal into a chart" to
demonstrate replacing ad-hoc state with a statechart. On honest
analysis (readability vs ceremony, performance, testability,
maintainability), the refactor would have been shoe-horning — the
conflict flow is 2 states + 2 events + 1 implicit guard, which is
a keyword flag with a payload, not a state machine.

Closed instead as a **doc artifact**:
[`docs/when-to-statechart.md`](./when-to-statechart.md) captures
the decision criteria for *when* to reach for a chart, with our
existing review chart (yes — 3+ states, guard, eventless
transition) and the conflict modal (no — 2 states, flag with
payload) as the worked examples.

Filed a skill-gap note: the local `statechart` skill's
`resources/patterns.md` covers *intra-chart* anti-patterns well
but doesn't cover the "don't chart a flag" decision. The doc
links the gap.

No code changes. No new specs. Master runner: 87 specs / 599
assertions, unchanged.

---

## ✅ Phase 9 — Fulcro RAD basics

Capped scope: did 9.1 (attribute definitions) + 9.2 (replace the
Add Item input with attribute-driven rendering) + 9.4 (analysis
doc). Skipped 9.3 (RAD report) — would have fought our custom
per-row rendering for no learning win.

- **9.1**: Added `com.fulcrologic/fulcro-rad 1.6.23` to deps.edn.
  New `learn.rad.attributes` ns with `defattr` declarations for
  `:todo/id`, `:todo/text`, `:todo/status`, `:todo/was`. Each
  carries data type, cardinality, required-flag, schema; text adds
  `:field/label` (sourced from `learn.ui.strings/input-placeholder`)
  and `:field/maxlength`; status + was enumerate the four valid
  values. 1 new spec / 15 new assertions verifying the registry
  shape.
- **9.2**: New `learn.rad.input/text-input` helper reads `:field/*`
  metadata from an attribute and renders the Tachyons-styled input
  + `clip`-hidden label. The Add Item input swaps from a 5-key
  inline `dom/input` to a 7-key call to the helper. Visual output
  identical; source of truth for placeholder + maxlength moved
  from hard-coded literals to attribute metadata. Browser-manual
  verification confirmed type + Add Item + list-update flow
  unchanged.
- **9.4**: New `docs/benefits-of-RAD-in-this-project.md` —
  honest write-up of what RAD added at our scale (attribute-as-
  source-of-truth pattern, self-documenting schema, architectural
  readiness for future entities), what it didn't (no auto-rendered
  forms / reports / save flow / schema generation — we declined
  all of these), and when its full machinery WOULD pay off (3+
  entities, form-heavy UIs, real DB backend, multi-developer
  consistency needs).
- Dropped duplicate side-by-side debug-view idea into
  `docs/ideas.md` (`rad-debug-side-by-side` tag) as a nice-to-have.

**Numbers**: 88 specs / 614 assertions, all green (+1 spec / +15
assertions). CLJS: 334 files, 0 warnings (was 327 — RAD pulls in
7 transitive files).

**Implements**: 9.1 and 9.2 sub-phases; no new user stories
(RAD is a refactor, not a feature).

---

## ✅ Phase 10 — RAD reports and forms (closed as a doc artifact)

Scoped to "use `defsc-form` and `defsc-report` for the AutoFocus
UI". On analysis, both would be net-negative refactors for this
project:

- **`defsc-form`** for our Add Item input: ~200+ lines of render-
  plugin + state-machine wiring + tempid-handling-vs-our-UUID-
  flow override, replacing the ~60-line `rad-input/text-input`
  from Phase 9. Our custom save (UUID server-side + AutoFocus
  add-rule for status) doesn't fit RAD's delta-based save flow.
- **`defsc-report`** for our list: almost every existing visual
  feature (status SVG icons, dim-when-cancelled, strike-when-
  cancelled, hover-to-show buttons, benchmark bold, no column
  headers, footer count line, modal siblings) requires an
  override of `defsc-report`'s defaults. The result would be more
  code than the current `TodoList + TodoItem`.

Closed as a doc artifact:
[`docs/when-to-use-RAD-forms-and-reports.md`](./when-to-use-RAD-forms-and-reports.md)
captures the criteria for when each pays off, with our app's
shape as the worked counter-example. Companion to
`when-to-statechart.md` and `benefits-of-RAD-in-this-project.md`.

Notes the "RAD-lite" middle ground (attribute-driven custom
rendering without `defsc-form`/`defsc-report` machinery) as the
right shape for our scale — which is what
`learn.rad.input/text-input` from Phase 9 already is.

No code changes. No new specs. Master runner: 88 specs / 614
assertions, unchanged.

---

## ✅ Phase 11 — Production Pathom patterns (closed as a doc artifact)

Scoped to "introduce per-request env, batch resolvers, and
mutation return values for optimistic UI". Honest analysis: our
in-process / no-HTTP / single-user / atom-as-DB Pathom setup has
zero load-bearing use for any of these patterns.

- **Per-request env**: no DB connection to inject, no auth, no
  multi-tenancy. Adding `{:server-db ...}` to env would be a
  stylistic choice that doesn't change behaviour.
- **Batch resolvers**: `all-todos-resolver` returns the full
  denormalized list in one constant-time map lookup. No I/O, no
  N+1 surface.
- **Mutation return values**: client allocates UUIDs, server
  stores them verbatim, no remap needed; server doesn't compute
  any fields the client needs back; no cascading writes.

Closed as a doc artifact:
[`docs/when-to-use-pathom-prod-patterns.md`](./when-to-use-pathom-prod-patterns.md)
explains each pattern, when it pays off, and why our app shape
doesn't exercise them. Notes that we DO ship two "production-
shape" plugins (`error-handling-plugin`, `logging-plugin` with
opt-in `*debug?*`) — those are the parts of "production Pathom"
where we genuinely benefit.

Closes out the original Phase-8-then-swapped-to-11 slot. No code
changes, no new specs.

---

## ✅ Phase 12 — i18n + visual polish + facade refactor

Originally scoped as "internationalize via `fulcro-i18n`". On
analysis the third-party lib was overkill for three locales and a
~30-key surface — see [`benefits-of-i18n-in-this-project.md`](./benefits-of-i18n-in-this-project.md)
for the decision. Phase grew to include the visual-polish work
(`i` + gear icon header restructure, modal padding fixes, dark-mode
dropdown rendering, modal overlay extent) and a long-overdue
`learn.client.cljc` namespace refactor as the work surfaced cross-
namespace touchpoints.

- **12.1**: B-6 modal-bottom-padding fix. `pb4` on `<main>` so the
  dark/light theme background extends past the last content line
  on tall lists.
- **12.2**: Gear icon SVG added (Font Awesome 7.x solid/gear, 640
  viewBox) in `learn.ui.icons`. Sets up the Settings modal trigger.
- **12.3**: Modal restructure. About + Help merged into one Info
  modal under the existing `i` icon (the `?`-Help button dropped
  from the header). New Settings modal under a new gear icon, body
  intentionally empty in 12.3 — populated in 12.5. `:ui/open-modal`
  enum gains `:info` and `:settings`, loses `:about` and `:help`.
- **12.4**: Hand-rolled i18n integration. `learn.i18n.core` ships
  the canonical `:en`/`:es`/`:ja` translation map, `tr` lookup with
  fallback chain (requested → :en → key-as-string), and two
  parameterised fns for the pluralised footer lines
  (`tr-list-count`, `tr-next-actionable`). TodoList gains
  `:ui/locale`, Root threads it to the modal bodies, components
  swap curated `s/*` references for `(i18n/tr locale :…)`. Locale
  persists via `storage/ui-prefs-whitelist` (joined `:ui/theme`).
- **12.5**: Language dropdown in Settings. New `set-locale*` pure
  state-helper + `learn.client/set-locale` mutation (client-only;
  no remote). `<select>` populated from `i18n/supported-locales`
  with `i18n/locale-label` driving option text in each language's
  own script (English / Español / 日本語). onChange fires
  set-locale; modal heading + language label re-render in the new
  locale on the next frame.
- **12.5b**: Extended translation coverage + dark-mode dropdown
  fix. Info / Settings / Save modal body copy all go through
  `i18n/tr` now (about copy, instructions, version label,
  close-instruction footers, save button labels, textarea
  placeholder). Dark-mode `<select>` options panel was rendering
  white-on-white on Chromium/Windows where `color-scheme: dark`
  alone isn't enough — fixed by inline `background-color` + `color`
  on each `<option>` when the theme is dark.
- **12.5c**: Three visual fixes:
    1. **Modal textarea/select hover/focus**: introduced
       `theme/theme-modal-input-class` — gray-at-rest matching the
       primary-button bg, snap to solid black/white on hover/focus.
       `theme-input-class` keeps the page-level new-todo input
       verbatim with the JS port (transparent fade).
    2. **Modal overlay extent**: dropped `height: 100%` from the
       html/body/#app root chain in `app.css` (kept `min-height:
       100% / 100dvh`) so the root grows with overflow content;
       changed `.app-container` from `h-100` to `flex-1` so it
       fills available space AND grows with content; changed the
       overlay from `top-0 w-100 h-100` to `top-0 bottom-0 left-0
       right-0` so it tracks `.app-container`'s full height.
       Header stays visible (it's outside `.app-container`), so
       icons remain reachable; Fulcro port now behaves better than
       the OG on this specific case (the OG's overlay still stops
       short at viewport height).
    3. **Info + Settings modal bottom padding**: `pb3` on the
       close-instruction paragraph so the bottom text has breathing
       room. Save modal already had this; carried the same pattern.
- **12.6**: Documentation sweep. This Phase 12 entry, the
  `i18n` decision write-up, and `changes.md` updates.
- **12.7**: `learn.client.cljc` namespace refactor. The original
  ~1450-line file split into seven small focused namespaces
  behind a thin `learn.client` facade preserving every public
  wire symbol:
    - `learn.client.session` — cross-namespace constants
    - `learn.client.state` — pure state-map helpers
    - `learn.client.mutations` — Fulcro defmutations (each
      defmutation uses an explicit fully-qualified target symbol
      so the multimethod registers under `'learn.client/<name>`;
      preserves server `::pc/sym` dispatch unchanged)
    - `learn.client.ui.theme` — Tachyons class strings + theme
      helpers
    - `learn.client.ui.modals` — modal-shell + body fns + header
      icon button + Mutation-record aliases via
      `m/declare-mutation` (avoids a cycle through
      `learn.client`)
    - `learn.client.ui.components` — TodoItem / TodoList / Root
    - `learn.client.lifecycle` — SPA atom, chart bootstrap,
      body-class theme sync, load-todos!
  `learn.client` itself shrank to ~280 lines: requires + re-exports
  preserving `learn.client/<state-helper*>`, `learn.client/<mutation>`,
  `learn.client/Root`, `learn.client/TodoItem`, plus the `init` fn
  (still here because `shadow-cljs.edn`'s `:init-fn` references it
  by qualified symbol) and `snapshot`.

**Numbers**: 99 specs / 675 assertions, all green (88 → 99 specs,
614 → 675 assertions). New tests added: i18n core unit specs
(4 specs / 21 assertions), TodoList locale propagation, set-locale
helper + mutation, Settings dropdown rendering, modal body copy
translations.

**Infrastructure notes**:
- `learn.util.storage/ui-prefs-whitelist` extended from
  `#{:ui/theme}` to `#{:ui/theme :ui/locale}`.
- New `scripts/compare-snapshots.mjs` (one-off diagnostic for the
  OG-vs-Fulcro visual comparison at small viewport) and
  `scripts/inspect-heights.mjs` (DOM-height probe used during the
  overlay-extent debugging).
- `resources/public/sw.js` localhost bypass for `/js/main/*`
  added separately (paired with the SW diagnosis surfaced during
  12.4); committed as a discrete dev-experience fix, documented in
  [`dev_scripts.md`](./dev_scripts.md).
- New [`dev_scripts.md`](./dev_scripts.md) cheat sheet for the two
  REPLs (JVM :7888 / shadow-cljs CLJS) and common state-poke
  recipes — most useful for locale switching before 12.5 landed
  but reusable for any dev-time inspection.

**Implements**: i18n architecture (decline `fulcro-i18n` in favor
of the hand-rolled lookup), language switching UX, the namespace
refactor that paid off the technical debt accumulated through
Phase 11, and three drive-by visual polish items.

---

## ✅ Phase 13 — Close S-import-export (JSON file import + export)

The last 🟡 stale-partial story in the tracker. Phase 7.6 stubbed
the save modal's Import + Export buttons (the Phase 7.11
Copy-URL and Phase 7.12 batch-text-paste paths were real). Phase
13 closes both file-IO halves.

- **`learn.util.tasks-io`** (new) — `parse-tasks-json` returns
  one of three shapes:
    - `{:ok? true :items <vector>}` on success
    - `{:ok? false :error/type :error/non-json}` when
      `JSON.parse` throws (UI surfaces "Please select a valid
      JSON file")
    - `{:ok? false :error/type :error/bad-json}` when JSON
      parsed but structure was wrong (UI surfaces "Failed to
      import tasks. Ensure the JSON file has the correct
      format")
  Reuses `learn.util.url-encoding/og-shape->items` for the
  shape translation (UUIDs fresh-generated; statuses preserved
  verbatim; legacy items without `:was` default to
  `:status/new` to keep the schema invariant). JVM-side parser
  adds a strict top-level JSON-type check so EDN's relaxed
  reader doesn't swallow non-JSON input as a symbol.
- **`learn.client.state/import-from-json*`** — state-helper.
  Appends parsed items to the existing list; empty / nil
  input is a no-op. No domain-rule application (imported
  items keep their statuses, matching the OG's `addAll`).
- **`learn.client/import-from-json` defmutation** with remote
  (server has a matching `record-list-items` handler under
  `'learn.client/import-from-json`).
- **`learn.client.ui.modals` CLJS-only helpers**:
    - `import-json-file!` — reads the selected file via
      `FileReader.readAsText`, runs `tasks-io/parse-tasks-json`,
      dispatches the mutation on success or sets `:ui/err-msg`
      with the right error string on failure. Clears the
      `<input>`'s value after each pick so the user can re-select
      the same file after fixing an error.
    - `export-items-json!` — `items` → `items->json` → `Blob` →
      `URL.createObjectURL` → synthetic anchor click downloading
      `tasks.json`. Filename matches the OG ReactJS port so
      cross-app round-trips work.

OG reference: `pwa-autofocus-app/src/utils/tasksIO.js`
(`importTasksFromJSON`, `exportTasksToJSON`) + `App.js`
(`handleImportTasks`, `handleExportTasks`).

**Numbers**: 103 specs / 722 assertions (99 → 103 / 675 → 722).
New specs: 2 in `tasks-io-test` (parse happy + failure paths),
2 in `client-test` (state-helper + mutation round-trip);
resolvers-test got 2 more registry assertions for
import-from-text + import-from-json wire-up.

**Closes**: `S-import-export` (was 🟡 since 7.6 / 7.12),
`S-import-json-file` (was ⬜), `S-export-json-file` (was ⬜).

---

## ✅ Phase 14 — `?lang=<code>` URL-level locale hint

Closes `S-i18n-url-locale`. Adds a URL query-param entrypoint so
publishers can write locale-specific links (e.g.
`https://avidrucker.github.io/fulcro-solo-learn/?lang=ja` for "the
app, in Japanese") without breaking the list-share flow.

Path-based routing (`/jp/`, `/es/`) was considered and rejected:
GitHub Pages doesn't natively SPA-route, so the path approach
would have needed a `404.html` redirect trick, hash-routing, or
per-locale duplicated `index.html`. Query param is one-line URL
parsing, coexists cleanly with `?list=`, no hosting changes.
See `docs/changes.md` for the user-facing summary and
`docs/user_stories.md` `S-i18n-url-locale` for the precedence rule
write-up.

Precedence: `localStorage :ui/locale > URL ?lang= > :en`.
Saved preferences always win over URL hints, so list-share links
(`?list=…`) never override the recipient's chosen language.
First-time visitors following `/?lang=es` get Spanish, and that
becomes their saved preference for the next visit.

- **Pure parser** in `learn.util.url-encoding`:
  `parse-lang-param` (private) extracts the raw value;
  `locale-from-url-search` validates against
  `i18n/supported-locales` and returns a keyword or nil. Case-
  insensitive code normalisation (`?lang=ES` → `:es`).
- **CLJS-only wrapper** `locale-from-current-url` reads
  `window.location.search`.
- **Lifecycle integration**
  `learn.client.lifecycle/install-url-locale-fallback!` runs in
  the CLJS `init` AFTER `storage/install-ui-prefs-persistence!`.
  It reads the raw ui-prefs slice from localStorage; if
  `:ui/locale` isn't there (first-time visitor) AND the URL has
  a valid `?lang=`, swap it into state. The storage save-watch
  is already attached, so the URL-derived locale persists on
  the next state change. JVM branch: no-op.
- **Copy List URL** unchanged — the share-URL helper
  (`learn.client.ui.modals/copy-list-url!`) still only writes
  `?list=…`. Shared lists stay locale-neutral.

**Numbers**: 104 → 105 specs / 728 → 741 assertions, all green.
1 new spec / 13 new assertions in `url-encoding-test:locale-from-url-search`
covering happy paths (`:en` / `:es` / `:ja`, case insensitivity,
coexistence with `?list=`) and failure paths (unsupported /
empty / malformed input).

**Implements**: `S-i18n-url-locale`. No new bugs surfaced; no
JS-port equivalent (the OG ReactJS app is English-only).

---

## ✅ Phase 15 — URL-length safeguard (S-max-url-length)

Closes the last ⬜ on the tracker. When the encoded `?list=`
segment would exceed `MAX_URL_LENGTH` (8000, matching the OG),
the URL-sync watch freezes the URL at its last fitting value
and surfaces `:err/url-too-long`. localStorage continues
normally — the user's list keeps growing locally; only URL
sharing is paused until the encoded length comes back under
limit (e.g. by deleting items, marking done, etc.).

**Divergence from OG** (logged in `docs/changes.md`): the JS
port lets the URL grow unbounded and produces unsharable links.
We freeze instead — predictable, no broken URLs, error message
points the user to manual recovery (Export JSON, paste text
elsewhere).

- **`learn.util.url-encoding/MAX_URL_LENGTH`** — 8000-char
  constant.
- **`learn.util.url-encoding/items-encode-fits?`** — pure
  predicate. Encodes via the existing
  `items->base64-url-segment` chain, returns boolean.
- **`learn.util.url-encoding/install-url-sync!`** — extended to
  a 3-arity. 1-arity (production) injects the default
  `replace-url-with-items!` setter PLUS an `on-over-limit`
  callback that swaps the i18n `:err/url-too-long` string into
  `[:list/id 1 :ui/err-msg]`. 2-arity remains for legacy tests
  that don't exercise the over-limit branch; 3-arity for tests
  that do.
- **`:err/url-too-long`** i18n key — added to all three
  locales (`:en` / `:es` / `:ja`). First fully-localized
  error string; rest are still English-only (logged as
  `bugs.md` B-8 for future cleanup).

**Numbers**: 105 → 108 specs / 741 → 749 assertions, all green
via fresh JVM (`clojure -M:test:cljs -m test-runner`). New
specs: `MAX_URL_LENGTH` constant, `items-encode-fits?` (empty /
single-item / 200-item-overflow cases), `install-url-sync!`
over-limit branch (url-setter NOT called when over,
on-over-limit IS called).

**Implements**: `S-max-url-length` (was the last ⬜).
**Surfaces**: `B-8` for tracking — other error messages remain
English-only and would benefit from the same i18n migration.

---

## ✅ Phase 16 — Close B-8: error messages translate with `:ui/locale`

Mechanical follow-up to Phase 12.4 — the i18n infrastructure was
in place, errors were the last user-visible English-only strings.
Closes B-8.

Seven error keys added to `learn.i18n.core` in all three locales
(`:en` / `:es` / `:ja`). English values are verbatim copies of the
existing `learn.ui.strings/<name>-err` constants so the dozen-plus
test assertions that compare against exact English text continue to
pass. New :es and :ja translations cover idiomatic equivalents.

Keys added:
- `:err/empty-input` — blank-text Add Item
- `:err/nothing-to-delete` — Delete List on empty list
- `:err/cannot-take-action` — Mark Done with no actionable items
- `:err/not-prioritizable` — Prioritize on non-prioritizable list
- `:err/empty-textarea` — blank Submit on import textarea
- `:err/bad-json-import` — JSON file structure invalid
- `:err/non-json-import` — file isn't JSON

Call sites updated:
- `learn.client.ui.components/TodoList` — 5 `set-err!` sites
  switch from `s/<name>-err` to `(i18n/tr locale :err/<name>)`.
- `learn.client.ui.modals/import-json-file!` — both error branches
  + the FileReader onerror handler. Function signature gained a
  `locale` parameter (passed from save-modal's existing locale
  binding).

**Numbers**: 108 → 109 specs / 749 → 752 assertions, all green
via fresh JVM. New spec: `TodoList errors — translate with
:ui/locale` covering Spanish empty-input + Japanese
nothing-to-delete (3 assertions).

**Reserved strings** (`max-list-length-err`,
`invalid-query-params-err`, `export-fail-err`) stay in
`learn.ui.strings` for now — they aren't surfaced anywhere, no
translation needed yet. Will revisit when / if they get wired into
a real surfacing flow.

**Implements**: closes B-8. Every user-visible string the app
shows the user is now localized for all three supported locales.

---

## ✅ Phase 17 — Include-language checkbox for Copy List URL

Closes `S-i18n-share-with-locale` (a new user story added this
phase). Completes the Phase 14 round-trip — Phase 14 added URL
parsing for `?lang=<code>` on incoming links; Phase 17 gives the
sharing user a way to ACTUALLY produce such links from the UI.

**Why a checkbox** (opt-in default-off): forcing your locale on
recipients overrides their preference if they haven't saved one
yet, and most sharing flows don't actually want that. Opt-in
respects the recipient. See `docs/changes.md` for the divergence
note (the OG has no i18n, so this entire flow is Fulcro-port-only).

- **`learn.util.url-encoding/list-share-url`** — 4-arity overload.
  Accepts an optional locale; appends `&lang=<code>` only when
  non-nil. 3-arity remains for callers that don't stamp.
  Round-trips with `locale-from-url-search` (Phase 14).
- **`learn.client.state/set-share-with-locale*`** + matching
  `learn.client/set-share-with-locale` defmutation. Client-only;
  the value is `:ui/share-with-locale?` on `[:list/id 1]`.
- **Persistence** — `:ui/share-with-locale?` joins
  `:ui/theme` / `:ui/locale` in
  `learn.util.storage/ui-prefs-whitelist`. Once toggled on, it
  stays on across reloads.
- **i18n** — `:save/include-lang` key added to all three
  locales (`:en` "Include language in URL" / `:es` "Incluir
  idioma en la URL" / `:ja` 「URLに言語を含める」).
- **UI** — `<input type="checkbox">` in the save modal
  (`learn.client.ui.modals/save-modal`), positioned ABOVE the
  Copy List URL button. Reads `share-with-locale?` from
  TodoList's props; `onChange` fires the mutation. The Copy URL
  button reads the same flag and passes locale (or nil) into
  `copy-list-url!`.

**Numbers**: 109 → 113 specs / 752 → 765 assertions, all green via
fresh JVM. New specs: `list-share-url — with optional locale`
(URL builder + Phase-14 round-trip), `set-share-with-locale*`
(pure helper + affects-only), `set-share-with-locale mutation`
(state update + no-remote confirmation), `Save modal —
Include-language checkbox` (label renders in both `:en` and
`:es`).

**Implements**: `S-i18n-share-with-locale`. Completes the
language-share UX loop introduced in Phase 14.

---

## ✅ Phase 18 — Locale-conflict modal (S-language-conflict-modal)

Closes a UX gap surfaced after Phases 14 + 17 landed: when a
user has a saved locale (e.g. English) and someone sends them
a list link with `?lang=es`, Phase 14's silent-apply rule
(saved wins, URL silently ignored) means the sender's intent
never reaches the recipient. Phase 18 adds an explicit
non-cancellable resolution modal whenever saved and URL
disagree.

The modal asks "Which language do you want to use? /
¿Qué idioma quieres usar?" (bilingual, so either reader can
answer) and offers two buttons labelled in their own scripts
(`English` / `Español` / `日本語`). After the user picks,
`:ui/locale` is set, the modal closes, and the address bar's
`?lang=` is rewritten to match — so a reload doesn't re-trigger
the modal.

**Decision matrix** (`locale-decision`):

| saved | url | result |
|---|---|---|
| nil | nil | `:no-op` |
| nil | :es | `:apply` (silent — Phase 14 behaviour) |
| :en | nil | `:no-op` |
| :en | :en | `:no-op` (no conflict) |
| :en | :es | `:conflict` (modal opens) |

The `:apply` path stays from Phase 14 — first-time visitors
following `/?lang=ja` still get Japanese without a prompt.

**Pieces**:
- `learn.util.url-encoding/locale-decision` — pure dispatcher,
  JVM-testable. Returns `{:action :apply :locale ...}` /
  `{:action :conflict :saved ... :url ...}` / `{:action :no-op}`.
- `learn.util.url-encoding/replace-lang-param` — pure URL-query
  rewriter (overwrites/removes `lang=`); JVM-testable.
- `learn.util.url-encoding/update-current-url-lang!` — CLJS-only
  side-effect wrapper around `history.replaceState`.
- `learn.client.state/set-locale-conflict-pair*` +
  `keep-locale*` — pure state helpers.
- `learn.client/keep-locale` defmutation — client-only; state
  swap via `keep-locale*` + CLJS-only `replaceState`.
- `learn.client.ui.modals/locale-conflict-modal` body —
  bilingual question + two locale buttons. No `:on-close`, no
  full-area dismiss (same UX shape as the list-conflict modal).
- `learn.client.lifecycle/install-url-locale-fallback!` —
  extended to dispatch on the three-way `locale-decision`
  result instead of just the binary "saved present?" check.
- `:locale-conflict/question` i18n key — added in all three
  locales.
- `:locale-conflict` joins `:delete-confirm` and `:conflict` in
  the `menu-disabled?` set in Root, so header icons hard-disable
  while the modal is up.

**Numbers**: 113 → 119 specs / 769 → 794 assertions, all green
via fresh JVM. New specs: `replace-lang-param` (URL builder),
`locale-decision` (4-case decision), `set-locale-conflict-pair*`
(state helper), `keep-locale*` (state helper), `keep-locale
mutation` (state round-trip + client-only check), `Locale-
conflict modal renders both locale labels` (UI rendering check).

**Implements**: `S-language-conflict-modal`. Completes the
Phase 14 / Phase 17 / Phase 18 i18n-URL round-trip:
- 14: incoming URL → silent apply for new visitors
- 17: outgoing Copy URL → opt-in language stamp
- 18: incoming URL with conflict → user resolves explicitly

**Surfaces**: `B-10` (existing conflict modal button layout) and
`B-11` (empty-vs-non-empty conflict-modal trigger) — both
logged this phase but not fixed; next-up after Phase 18 ships.

---

## 🟡 Phase 19 — a11y / Section 508 audit pass

Programmatic accessibility pass. Living artifact: `docs/a11y_audit.md`,
which holds the full Section-A (in-codebase) / Section-B (user must
run) split and the per-sub-phase notes. Below is the phases-doc
tracking summary.

**✅ 19a — Tooltip / aria-label / close-label i18n migration.** All
button accessible names that were still hardcoded English (modal
close-buttons, header-button tooltips, the four primary action
buttons, six row-action variants) routed through `learn.i18n.core`
and pulled via `i18n/tr`. Each button now has localized `:title` and
`:aria-label` pulling the same key. Commit `7d37ea6`.

**✅ 19b — Modal dialog semantics.** `modal-shell` now emits
`role="dialog"` + `aria-modal="true"` on every modal; opt-in
`aria-labelledby="<id-of-title>"` extension wired through to every
caller, with stable IDs on each modal's heading/question element
(info-modal-title, settings-modal-title, save-modal-title,
delete-confirm-question, locale-conflict-question,
list-conflict-question, review-question). Commit `bb0e44b`.

**✅ 19c — `<html lang>` sync.** New
`learn.client.lifecycle/sync-html-lang!` /
`install-html-lang-sync!` pair (CLJS-only, parallel to the body-theme
watch) keeps `<html lang>` aligned with `[:list/id 1 :ui/locale]` so
screen readers pick the right voice. Mapping is locale-keyword
`name` → IETF tag (1:1 for :en/:es/:ja). Commit `e445453`.

**✅ 19d — Decorative SVG icons.** `learn.ui.icons/svg-attrs` now
sets `aria-hidden="true"` + `focusable="false"`, applied via merge
to all eleven icons (status icons, header icons, lightbulbs, gear,
cancel-x, repeat-arrow). Prevents screen readers from double-reading
button labels alongside a generic "graphic" announcement.

**✅ 19e — Localized tooltips on bare interactive controls.** Four
controls that previously had no accessible name beyond their visible
label now carry both `:title` (hover) and `:aria-label` (screen
reader) sourced from `learn.i18n.core`:

- `:tooltip/include-lang` — "Include language in link" checkbox.
  Locked en wording: "When checked, the share link will open in
  this app's current language for whoever clicks it."
- `:tooltip/import-json` — JSON import button (styled label that
  triggers the hidden file input).
- `:tooltip/submit-text-import` — text-list import submit button
  under the textarea.
- `:tooltip/language-dropdown` — language `<select>` in the
  settings modal.

Added a guard spec (`tr — Phase 19e tooltip keys`) that asserts
each key resolves to a real translation (not the keyword-as-string
fallback) in :en/:es/:ja, plus an exact-match assertion on the
locked include-lang en string.

**⬜ 19f — Status-icon accessible names** (queued). Now that the
status SVG itself is `aria-hidden`, the wrapping `<span>` has no
announceable text, so screen reader users get no status info per
row. Move to a `role="img"` + localized `aria-label` for the
status indicator span. Cancelled rows announce both state and
prior-state.

**⬜ 19g — Focus management on modal open / close** (queued). On
open, move focus inside the modal; on close, restore. Lowest-cost
first pass: focus-the-heading + restore.

**⬜ 19h — Escape-to-close on dismissible modals** (queued).
Info / Settings / Save / Delete-confirm. List-conflict and
locale-conflict remain non-dismissible by design.

**⬜ 19i — Keyboard-only navigation sweep** (queued — partly user
work, see Section B-5).

**⬜ 19j — Color-contrast pass on dark theme** (queued — measure
first, then fix; see Section B-8).

The Section-B handoff list (Lighthouse, axe, WAVE, NVDA/VoiceOver,
keyboard, zoom, reduced-motion, contrast measurement) lives in
`docs/a11y_audit.md` and tracks as `S-ux-a11y-review-pass` in
`docs/user_stories.md`.

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
