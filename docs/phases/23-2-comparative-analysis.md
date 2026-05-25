# Phase 23.2 — Comparative analysis

**Status:** ✅ Complete (2026-05-25)

For each major idiom catalogued in [23.1](./23-1-reference-recon.md), this doc presents Tony Kay's framework-author voice (extrapolated from his curriculum prose and his choices in branch `10-report-row-actions`) and a fulcro-solo-learn counterargument. Each item gets a verdict; 🔄 items feed Phase 23.3's prioritized recommendations.

## Verdict legend

| Mark | Meaning |
|---|---|
| ✓ | Already adopted. Pattern matches the reference. |
| 🔄 | Could adopt. Worth a Phase 24+ candidate story. |
| ❌ | By-design rejection. Pre-existing doc artifact engages with the trade-off; reference doesn't surface new evidence to overturn it. |
| ⊘ | Not applicable at this scale. Pattern presupposes infrastructure / scope we don't have. |

---

## Bucket A — patterns the reference uses, this project doesn't

### A.1 — Wrapper macros (`pm/defresolver`, `pm/defmutation`)

> **Tony voice:** Pathom's `pc/defresolver` and `pc/defmutation` are minimal. They don't register the resolver with anything, don't catch exceptions, don't maintain the per-request db atom, don't normalize the result shape, don't have an authorization hook. The project wants all five behaviors uniformly. The wrapper macros bundle them. *(curriculum quote, `curr:3-pathom/tutorial.md:275-285`)*

**Counterargument.** Of the five concerns the macro bundles, we already handle three differently:

- **Registration**: we use an explicit vector in `learn.resolvers` instead of a `defonce` atom registry. With ~12 resolvers, the explicit vector is more lexically traceable; the atom registry's value lights up at ~50+.
- **Error isolation**: we have `error-handling-plugin` on the parser. A thrown resolver doesn't kill the parse for us either.
- **Result normalization**: our resolvers return well-typed data (Pathom Connect's input/output specs are enforced at registration time); we haven't hit the `nil → {}` or `seq?-with-nils` cases the wrapper macro guards against.

The other two (per-request db atom + authorization hook) presuppose Datomic + multi-user. ⊘ for us.

That said, **there's a small refactor opportunity**: extract the three concerns we DO handle into one obvious place. Right now the registration vector is in `learn.resolvers`, the error plugin is in `learn.parser`, the contract spec is implicit. A tiny `defresolver` macro that does `(comp/defresolver ... [reg-vec! sym])` could consolidate. But the gain is small enough that I'd file it as a "nice-to-have."

**Verdict:** ⊘ (with a 🔄 footnote — possible micro-refactor at low priority).

### A.2 — `m/returning` for mutation row-refresh

> **Tony voice:** The mutation declares its return shape via `(remote [env] (m/returning env Row))`. The auto-generated `<Report>-Row` class is exposed deliberately so the mutation knows what to merge back. Hand-rolling `merge/merge-component` after a mutation is fighting the framework. *(extrapolated from `obrp:src/main/com/example/model/employee.cljc:91-103` + curriculum exercise pattern)*

**Counterargument.** Our mutations don't usually need server-driven refresh because the optimistic state-map update is the canonical truth — `add-todo*` updates the client state, then `(remote [_] true)` lets the server know. The server returns nothing actionable (atom-as-server has no auto-generated IDs to remap; client UUIDs are canonical).

**But:** `import-from-text` is an exception. It adds multiple items in one shot, and the resulting list state is non-trivial (positions, statuses follow the AutoFocus add rule). Using `m/returning` would let the server return the canonical post-import list, and the client wouldn't need to duplicate the model logic in `import-from-text*`. We currently keep them in sync by both calling `learn.model.list/import-from-string` — duplication that `m/returning` would eliminate.

**Verdict:** 🔄 — narrow scope (mutations where the post-mutation state isn't trivially derivable client-side). Worth filing as a Phase 24 candidate, but minor.

### A.3 — Optimistic `action` + `ok-action` pattern

> **Tony voice:** `action` runs the optimistic local update; `ok-action` finalizes pristine snapshot via `fs/entity->pristine*` after the server confirms. The user never waits on the network. *(extrapolated from `obrp:src/main/com/example/model/employee.cljc:91-103`)*

**Counterargument.** We don't have form-state, so there's no "pristine snapshot" to advance. And our remote is `lr/sync-remote` — synchronous in-process. There's no network to wait for. The optimistic vs server-confirmed distinction has no behavioral meaning here.

**Verdict:** ⊘. Would re-emerge if we ever moved to a real HTTP remote and added editable forms.

### A.4 — Tempid lifecycle (client tempid → server returns real id)

> **Tony voice:** `(tempid/tempid)` at create; `tempid/tempid?` predicates gate field visibility during the pending state; server's `:tempids` map round-trips real IDs back. This is how Fulcro coordinates client and server identity. *(extrapolated from `obrp:src/main/com/example/ui/employee.cljs:99-100`)*

**Counterargument.** We mint client-side UUIDs and treat them as the canonical ID. There's no separate server-assigned ID space (Datomic would assign `:db/id` numerics; we don't have that). Our UUIDs go into the atom and stay there.

**Verdict:** ⊘. Would matter if we adopted Datomic, but at atom-as-server, tempid lifecycle adds zero value.

### A.5 — Per-request db atom (read-your-writes within one parse)

> **Tony voice:** Datomic's `d/db` returns a database value as of a moment. If two resolvers in the same request each called `(d/db conn)` directly, they'd see two different snapshots when a write landed in between. The fix: each request grabs one db value at the start, stores it in an atom, every resolver derefs that atom. The end result is read-your-writes within a single request. *(curriculum quote, `curr:3-pathom/tutorial.md:200-234`)*

**Counterargument.** Single-threaded JS client + single-process JVM + atom-as-server means we don't have intra-request races. The atom IS our "snapshot" — `swap!` is atomic; consecutive reads see consistent state because nothing's running between them.

**Verdict:** ⊘ — would resurface if we adopted Datomic. The lesson is worth keeping in the back pocket; in the meantime it's invisible infrastructure we don't need.

### A.6 — RAD `defsc-form` / `defsc-report`

> **Tony voice:** Branch 6 walked you through the long way — manual `defsc` for row + list + form, hand-rolled `:will-enter`, deferred-routing. Branches 7-10 show the RAD-macro endpoint. The macros buy you brevity at the cost of visibility — that's a fair trade for typical CRUD; for genuinely unusual flows, manual style remains the right tool. *(curriculum quote, `curr:7-RAD-equipment/tutorial.md:48`)*

**Counterargument.** [`when-to-use-RAD-forms-and-reports.md`](../when-to-use-RAD-forms-and-reports.md) (Phase 10 doc artifact) engages with this directly. Our "form" is one text input; `defsc-form` assumes 3+ fields with form-state, validation, save/cancel — the macros' floor is higher than our ceiling. Our "list" is a single-column custom-rendered todo list with per-row dynamic affordances (Cancel/Clone/Mark Done buttons that appear conditionally based on status); `defsc-report` assumes a table with cell-clicks routing to a form.

Branch 10 doesn't surface new evidence. Tony's own framing — *"for genuinely unusual flows, manual style remains the right tool"* — actually supports the rejection. Our per-row affordances are exactly the "genuinely unusual" case.

**Verdict:** ❌ — by-design. Phase 10's doc artifact stands.

### A.7 — Attribute-centric model file split (`model_rad/X.cljc` vs `model/X.cljc`)

> **Tony voice:** Every namespace exports a `(def attributes [...])` aggregated into top-level `all-attributes`. RAD attribute declarations live in `model_rad/<entity>.cljc`; Pathom resolvers and mutations live in `model/<entity>.cljc`. The split keeps declaration separate from behavior. *(extrapolated from the reference's directory layout)*

**Counterargument.** Phase 9 (`defs in `learn.rad.attributes`) already adopted attributes for the Add Item input. The Phase 9.4 doc artifact closed RAD-deeper with: "deliberate scope cap — attributes + input rendering only, no forms/reports because our domain is too small."

But the **file split itself** is interesting independent of forms/reports. Today our attribute declarations sit alongside model functions in `learn.rad.attributes` + `learn.model.list`. Splitting along the reference's lines would give us `learn.model_rad.todo` (attribute decls) + `learn.model.todo` (resolvers + business logic).

The cost is moderate (rename + re-require sweep) and the benefit is small (clearer separation of "what" vs "how"). At ~5 attributes and 2 resolver namespaces, the convention overhead is probably not worth the gain. But this is the closest Bucket A item to a real refactor candidate.

**Verdict:** ❌ — adjacent to Phase 9.4's by-design rejection. If 23.3 promotes it, scope to "attribute-only namespace split" without re-litigating the broader RAD-forms/reports decision.

### A.8 — Mount-managed component lifecycle

> **Tony voice:** Six `defstate`s under `com.example.components.*`, started in require-graph order: `config → automatic-resolvers → datomic-connections → parser → middleware → server`. `(restart)` calls `(stop) → (reload/reload) → (start)`. Hand-rolled init is fragile — Mount makes the dependency order explicit. *(extrapolated from `obrp:src/dev/development.clj` + `curr:2-server-composition/tutorial.md:42-58`)*

**Counterargument.** Our `learn.client/init` is ~40 lines of hand-rolled startup that does: hydrate localStorage → install URL-sync watch → install locale-fallback → mount app → restore route. It's readable, debuggable, and lives in one file. Mount earns its keep with 5+ singletons across 5+ namespaces; we have one client app + one Pathom parser. The cost of adopting Mount (new dep, refactor of `init`, every component a `defstate`) outweighs the benefit at this scale.

**Verdict:** ⊘ at this scale. If the project ever grew a real HTTP server (vs `lr/sync-remote`), the calculus would change.

---

## Bucket B — patterns this project uses, the reference doesn't teach in branch 10

### B.1 — Fulcrologic Statecharts (review flow)

> **Tony voice (hypothetical, since the curriculum doesn't address this).** If a flow has 5+ states or branching guards, reach for a chart. Otherwise flags are fine. The library is there when you need it — the curriculum doesn't teach it because nothing in the example app needs it.

**Counterargument.** [`when-to-statechart.md`](../when-to-statechart.md) (Phase 8 doc artifact) engages with this directly using two worked examples FROM THIS PROJECT:

- The conflict modal (2 states: open vs closed; 1 implicit guard) — **not chart-worthy**. Closed as a keyword flag with payload.
- The review flow (4 states: idle / asking / confirmed / done; per-decision guards on `:yes` / `:no` / `:quit`; history needed for "what was just asked") — **genuinely chart-worthy**. Implemented in `learn.review.chart` as a Fulcrologic Statechart in Phase 5K.4.

The reference's curriculum doesn't surface new evidence — branch 10 has no flow comparable to our review chart in complexity, so there's nothing to learn from in that direction.

This *is* a curriculum gap on Tony's side (covered in [`onboarding-rad-curriculum-suggestions.md`](../onboarding-rad-curriculum-suggestions.md) as suggested Branch 12), but for our project it's a settled good decision.

**Verdict:** ✓ — by-design adoption, Phase 8 stands.

### B.2 — Guardrails `>defn` (heavy in model layer)

> **Tony voice (hypothetical).** `>defn` is plumbed for when you need it. The curriculum doesn't demonstrate it because the example app's domain functions are thin enough that contracts add noise without catching anything. Guardrails earns its place at the layer boundary between pure functions and impure machinery.

**Counterargument.** We use `>defn` heavily in `learn.model.list`, `learn.model.review`, and `learn.util.url-encoding`. The benefits at our scale are measurable:

- Two model-layer bugs caught by Guardrails during development (one in `add-todo` shape, one in `cancel-todo` status precondition).
- Self-documenting function signatures — readers see the gspec without having to chase callers.
- The `:learn.model.schema/*` registry pattern (single source of truth for keyword specs) plays well with Malli's existing usage.

The cost is ~2 extra LOC per function (a `[?args => ?return]` line) and ~5% runtime overhead in dev (Closure DCE drops it in release builds).

**Verdict:** ✓ — by-design adoption, justified by ROI at this scale. But: **consistency could improve** — `>defn` is heavy in model layer, lighter in `util.*`, almost absent in client/UI namespaces. If there's a 🔄 nearby, it's "audit util/client for missing contracts on model-adjacent helpers." Probably low priority.

### B.3 — i18n / theme / Playwright (grouped: feature-requirement items)

> **Tony voice (hypothetical).** Branch 10 demonstrates Fulcro, RAD, Pathom, Datomic. It doesn't demonstrate i18n, theming, or browser-driven testing because the example app doesn't need them. Those are real-world disciplines, not curriculum gaps.

**Counterargument.** All three are feature requirements of THIS project:

- **i18n (4 locales)**: real user requirement (Portuguese audience surfaced today via Android DuckDuckGo). Hand-rolled ~30 keys × 4 locales; `fulcro-i18n` was considered and rejected as overkill ([`benefits-of-i18n-in-this-project.md`](../benefits-of-i18n-in-this-project.md)).
- **Tachyons + theme toggle**: feature-level UX commitment; persisted in localStorage; AA-contrast verified in Phase 19j with axe.
- **Playwright e2e + axe-core**: covers a11y assertions fulcro-spec can't reach (focus management, keyboard navigation, color contrast, ARIA live regions).

Branch 10 doesn't have these because the example app doesn't have them. No evidence to overturn.

**Verdict:** ✓ — all three by-design adoptions justified by product/feature needs that don't exist in the reference's example app.

### B.4 — CI / formatter / pre-commit / GitHub Actions

> **Tony voice (hypothetical, extrapolated from reference's `.clj-kondo/config.edn` one-liner).** Tooling enforcement is doc-driven, not pipeline-driven. The curriculum recommends `clj-kondo --lint src/main/...` ad-hoc but doesn't wire it into the build. Build pipelines are real-project concerns.

**Counterargument.** We have **one** GitHub workflow (`gitleaks` secret scanning, commit `973f46b`). We don't have a workflow that runs the master JVM test runner on every PR. We don't have a formatter check. We don't have pre-commit hooks.

There's a concrete pain point that argues for a test-runner workflow: the `stale-vars-after-refactor` bug (Phase 12.7 retrospective, captured in `learned_while_making_this.md` Part 1) was a "local tests pass, CI fails" pattern — except CI didn't exist to catch it; we caught it manually in a fresh JVM. A workflow that runs `clojure -M:test:cljs -m test-runner` on every PR would have caught it automatically.

Cost: ~30 LOC of YAML in `.github/workflows/`. Benefit: every PR runs the test runner in a fresh JVM, catching exactly the class of regression the master-runner-in-fresh-process hard-rule exists to defend against.

**Verdict:** 🔄 — concrete, well-scoped, low-effort. **The strongest "low-hanging fruit" candidate from this analysis.**

---

## C — Patterns where we already match the reference

For completeness, things we already do that the reference also does:

| Pattern | Where we adopt it |
|---|---|
| **fulcro-spec shape** (`specification` + nested `behavior` + `assertions`) | `test/learn/**` |
| **`>defn` (Guardrails)** — albeit reference doesn't use it; we are early-adopting what their EDN config implies | `learn.model.*` |
| **REPL-driven TDD loop** | `clj-nrepl-eval` + master test runner in CLAUDE.md |
| **`:reload-all` for fresh-namespace-state in tests** | master test runner |
| **Pathom 2 with `error-handling-plugin` + `logging-plugin`** | `learn.parser` (Phase 11 doc artifact engages why we don't need batch resolvers / per-request env) |
| **Atom-as-server for tests** (theirs is in-memory Datomic via `testdb.clj`; ours is a plain atom) | `learn.parser/SERVER-DB` |

---

## Verdict summary

| # | Pattern | Verdict | Note |
|---|---|---|---|
| A.1 | Wrapper macros (`pm/defresolver`) | ⊘ | Micro-refactor footnote possible |
| A.2 | `m/returning` for row-refresh | 🔄 | Scoped to `import-from-text` mutation |
| A.3 | Optimistic `action` + `ok-action` | ⊘ | No form-state; sync remote |
| A.4 | Tempid lifecycle | ⊘ | No Datomic; UUIDs are canonical |
| A.5 | Per-request db atom | ⊘ | Single-thread; no Datomic |
| A.6 | RAD `defsc-form` / `defsc-report` | ❌ | Phase 10 stands |
| A.7 | `model_rad/X.cljc` / `model/X.cljc` split | ❌ adj | Minor — file reorg only if 23.3 promotes |
| A.8 | Mount-managed lifecycle | ⊘ | Not enough singletons |
| B.1 | Statecharts (review flow) | ✓ | Phase 8 |
| B.2 | Guardrails `>defn` | ✓ | Audit consistency in util/UI as possible 🔄 |
| B.3 | i18n / theme / Playwright | ✓ | Feature requirements |
| B.4 | GitHub Action for test runner | 🔄 | **Low-hanging fruit** |

## What this means for 23.3

Two real 🔄 candidates surface from the analysis:

1. **GitHub Action running master JVM test runner on every PR** (B.4) — concrete, ~30 LOC YAML, defends the existing `stale-vars-after-refactor` hard rule with automation. Highest value-to-effort ratio of any item in this audit.
2. **`m/returning` for `import-from-text`** (A.2) — minor cleanup that eliminates duplicated model logic between client and server. Low effort, modest value.

Two adjacent micro-items worth surfacing in 23.3 as discretionary:

3. **Guardrails consistency audit** in util/client layer (B.2 footnote) — likely no real bugs to catch but worth a half-hour scan.
4. **`learn.model_rad.todo` / `learn.model.todo` namespace split** (A.7) — moderate cost, small benefit; defer unless extending RAD coverage elsewhere.

Everything else is ⊘ (not applicable at scale) or ❌ (already-considered-and-rejected via existing doc artifacts that branch 10 doesn't overturn).

**Headline: this project is mostly idiomatic for its scale.** The reference doesn't surface major refactor opportunities — it surfaces one concrete tooling gap (CI workflow) and one minor mutation cleanup. The bigger story is Bucket B → curriculum feedback, captured separately in [`onboarding-rad-curriculum-suggestions.md`](../onboarding-rad-curriculum-suggestions.md).
