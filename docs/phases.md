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

## 🟡 Phase 5I — AutoFocus domain operations

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

### 🟡 5I.2 — `model.list/benchmark-item`

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

## 🟡 Phase 5J — Cancel, complete-benchmark, clone

Build the rest of the AutoFocus mutation set:
- `cancel-todo` — refuses on `:done`/`:cancelled`, captures `:todo/was`,
  fires auto-mark
- `complete-benchmark-item` — completes the last ready, fires auto-mark
- `clone-todo` — appends a new todo with the source's text

Each is a `>defn` in `model.list`, with a Fulcro mutation that
delegates to it. Server-side Pathom mutations added so `(remote [_]
true)` lights up.

### ✅ 5J.1 — `model.list/cancel-todo`

Refuses `:done`/`:cancelled` targets with `:error/cannot-cancel`. Refuses missing ids with `:error/item-not-found`. Captures `:todo/was` on success. Composes `auto-mark` over the post-cancellation list.

**Decisions locked in:** JS-port discrepancy #2 resolved (double-cancel is an explicit error, not silent idempotence). SCHEMA.md §14 question "cancelling done items?" closed (also an error). Both decisions documented in SCHEMA.md §13.

**Acceptance met:** 8 components / ~25 assertions covering validation (not-found, cannot-cancel × 2), basic cancellation (:new, :ready), and auto-mark integration (fires on sole-ready cancel, no-fires when other ready remains, no-fires when no new to promote).

### ✅ 5J.2 — `model.list/complete-benchmark-item`

Pure function over items: completes the benchmark (last `:status/ready` by list order) by setting its status to `:status/done`, then composes `auto-mark` over the result. Refuses with `:error/no-actionable-items` when no ready item exists.

**Design notes:**
- No `:todo/was` capture on completion. `:was` is the cancellation-specific affordance ("what was this before I cancelled it"); completion has no analogous un-complete operation, so no need to record the prior status. An explicit assertion locks this in: a completed todo does *not* gain a `:todo/was` key.
- "Benchmark" is *last :ready in list order*, not *last item in list*. Spec covers the case where a `:done` item follows the last `:ready` to prove the function isn't accidentally indexing from the tail of `items`.
- Auto-mark suppression when other readies remain is exercised in two distinct list shapes (consecutive readies, and readies separated by other statuses), to prove the `auto-markable?` check sees the post-completion state correctly.

**Acceptance met:** 6 components / 25 assertions covering refusal (5 sub-cases of "no actionable items"), basic completion (sole-ready, multi-ready-last-wins, last-ready-not-last-in-list), and auto-mark integration (fires on sole-ready completion with news remaining; no-fires when other ready remains; no-fires when no news to promote).

### ⬜ 5J.3 — `model.list/clone-todo`
### ⬜ 5J.4 — Wire Fulcro client mutations to model
### ⬜ 5J.5 — Server-side Pathom mutations for remote sync

---

## ⬜ Phase 5K — Prioritize/review flow

Build `learn.model.review` for the binary review process:
- `prioritizable?` (with the JS rule: last new must be after last ready)
- `initial-cursor`
- `next-cursor`
- `current-question`
- `handle-review-decision`

**Decisions required at this phase:**
- JS-port discrepancy #1 (prioritizable list): use the JS rule
  exactly — "at least one ready, at least one new, *and last new is
  after last ready in list order*." Already in SCHEMA.md §15 as a
  revision item.
- JS-port discrepancy #4 (review-decision return shape): return Result-shaped
  `{:ok? true :items ... :review/cursor ... :review/active? ...}`
  rather than the JS `{tasks, cursor, endReview}`. Open question §14
  in SCHEMA.md asks the same; resolving it here.

This phase likely introduces Tony Kay's **statecharts** library — the
review flow's `:active?`/`:cursor` state and three-decision response
is a textbook statechart.

**New skill:** `statechart` (already in available skills).

---

## ⬜ Phase 6 — Datomic backend

Replace `SERVER-DB` (the atom) with a real Datomic database. Update
resolvers to query Datomic. The transition is the point at which
"the server" becomes capable of multi-process / multi-client
operation.

**New skill:** `datomic`.

---

## ⬜ Phase 7 — shadow-cljs + real http-kit/jetty + browser

First time the project actually runs in a browser. Switch from
loopback remote to `http-remote`. Add Fulcro Inspect (browser
devtool).

---

## ⬜ Phase 8 — Tempids

Real client-server ID exchange: client mints a tempid, server returns
the canonical id, Fulcro auto-rewrites references everywhere.

---

## ⬜ Phase 9 — Production Pathom patterns

Per-request env, batch resolvers (N+1 prevention), `defmutation`
return values for optimistic UI.

---

## ⬜ Phase 10 — Fulcro RAD basics

Migrate from hand-written `defsc` to RAD attributes. The schema we've
built in `learn.model.schema` gets re-expressed as RAD attribute
definitions.

**New skill:** `fulcro-rad`.

---

## ⬜ Phase 11 — RAD reports and forms

Use RAD's report and form components for the AutoFocus list UI.

**New skill:** `fulcro-rad-reports`.

---

## ⬜ Phase 12 — Statecharts in depth

If we used statecharts lightly in 5K, this phase doubles down: more
complex flows (e.g., import/export, conflict resolution).

---

## ⬜ Phase 13 — i18n

Internationalize via `fulcro-i18n`. The structured error keywords from
Phase 5 already separate domain from display strings — i18n drops in
cleanly.

**New skill:** `fulcro-i18n`.

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
