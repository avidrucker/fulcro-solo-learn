# Phase 23.3 — Prioritized recommendations

**Status:** ✅ Complete (2026-05-25)

Final deliverable of Phase 23. Takes the 🔄 candidates from [23.2's](23-2-comparative-analysis.md) verdict table and prioritizes them by effort × value, with each item written up as a candidate Phase 24+ story. The low-hanging-fruit shortlist for "do these next if you want quick wins" is called out at the bottom.

## Effort × value matrix

|  | Low effort (≤ 1 day) | Medium effort (1-3 days) | High effort (3+ days) |
|---|---|---|---|
| **High value** | **#1 — CI workflow** | — | — |
| **Medium value** | **#2 — `m/returning` cleanup** | — | — |
| **Low value** | #3 — Guardrails audit | #4 — Namespace split | — |

Everything in the audit landed in the **low-effort column**. That's actually informative: when a reference project at different scale produces only low-effort 🔄 candidates, the project under audit is well-positioned — the alternative (medium-or-high-effort 🔄s) would have meant structural debt. We don't have structural debt; we have one tooling gap and a few cosmetic cleanups.

---

## #1 — Suggested Phase 24: CI workflow for master JVM test runner

**Verdict:** 🟢 **Strongly recommend promoting.** Highest value-to-effort ratio in the entire audit.

### Problem statement

CLAUDE.md's hard rule "after any refactor that removes/renames a public var, verify with a fresh JVM" exists because of a real incident (Phase 12.7 facade refactor — `learn.client/textarea-import-id` re-export was dropped, long-running REPL kept the cached var alive, local tests passed, would have failed CI). The rule defends correctness with **discipline**, not **automation**. A GitHub Action that runs the master test runner on every PR would convert that discipline into automation — catching the same class of regression without depending on the human remembering to do a fresh-JVM run.

The project already has one workflow (`.github/workflows/...` — `gitleaks` secret scan, commit `973f46b`). Adding a second is mechanical.

### Proposed implementation outline

- Add `.github/workflows/test.yml`
- Trigger: `pull_request` + `push` to `main`
- Job: install Clojure CLI + Node (for shadow-cljs if needed) → cache `~/.m2` → `clojure -M:test:cljs -m test-runner`
- Optional: cache `node_modules` (the project has `pesticide` + `playwright` + `shadow-cljs` + `tachyons` as dev deps, but the JVM test runner doesn't need them)
- Optional: separate job that runs `e2e/keyboard-and-a11y.spec.js` headless. Defer to Phase 24.x if the JVM job lands cleanly first.

### Effort estimate

**S (~1-3 hours).** Mostly YAML + first-run debugging. Test runner already produces a clean `TOTALS:` line with `:fail 0 :error 0` — exit code 0 vs non-zero is straightforward to plumb.

### Risk / dependencies

- **No code changes**, just a workflow file. Reversible by deleting the file.
- **First-run flakiness** is the usual GitHub Actions cost (cache key tuning, JDK version pin, Clojure CLI install path). The risk is "wastes a few iterations getting the YAML right," not "breaks anything."
- **No dependency on other phases.**

### TDD acceptance criteria

- A PR that deliberately removes a public var without updating its callers triggers a red workflow run (proves the workflow catches the Phase 12.7 incident class).
- A clean PR triggers a green workflow run with the same `TOTALS:` printed as a local fresh-JVM run.
- Workflow run time < 5 minutes (the master test runner finishes in ~30s locally; GitHub Actions adds overhead but should stay well under 5 min with `~/.m2` cached).

### Notes

- The Playwright e2e suite is a separate concern — `e2e/keyboard-and-a11y.spec.js` has one pre-existing failure on the Portuguese label test (`/Adicionar Tarefa/i` regex; actual text is `Adicionar`). That failure should be fixed independently before adding e2e to CI, or the e2e job gated behind the JVM job's success.

---

## #2 — Suggested Phase 25 (optional): `m/returning` for `import-from-text`

**Verdict:** 🟡 **Worth doing, but not urgent.** Small cleanup; eliminates duplicated model logic.

### Problem statement

The `import-from-text` mutation has parallel client + server implementations of the same logic (split lines → filter blanks → assign statuses per AutoFocus add rule). The client implementation lives in `learn.client.state/import-from-text*`; the server implementation in `learn.resolvers/import-from-text`. Both call into `learn.model.list/import-from-string`, but the client also runs the resulting list through `learn.client.normalize` to reshape for Fulcro.

A `(remote [env] (m/returning env Row))` pattern would let the server compute the post-import list and return it as the canonical result. The client would replace its `state/import-from-text*` body with `m/returning`'s merge logic.

### Proposed implementation outline

- Add `:com.wsscode.pathom.connect/output` to the server resolver so Pathom knows what `m/returning` is allowed to ask for
- Annotate the `defmutation` client side with `(remote [env] (m/returning env Row))` instead of `(remote [_] true)`
- Drop or simplify `state/import-from-text*`'s body (it becomes just the error-handling / err-msg-clearing parts; the merge is handled by `m/returning`)
- Adjust the spec assertions to assert the returned shape instead of the post-state directly

### Effort estimate

**S (~2-4 hours).** TDD red-green cycle: assertion-shape changes first, then the mutation annotation, then strip the duplicated client logic.

### Risk / dependencies

- **One mutation, well-scoped.** Doesn't touch any other mutation's semantics.
- **The other mutations** (`add-todo`, `cancel-todo`, `clone-todo`, `complete-benchmark-item`, etc.) don't benefit from `m/returning` — their post-state is trivially derivable from the input + state-helper, so no client/server duplication exists to eliminate. This is a one-mutation refactor, not a project-wide pattern change.

### TDD acceptance criteria

- `import-from-text` spec asserts the mutation returns a normalized list shape (new test).
- After the refactor, `state/import-from-text*` no longer duplicates the line-splitting/status-assignment logic (proves the duplication is gone).
- Existing import flow tests continue to pass (proves the refactor is behavior-preserving).
- The CLJS-only `import-from-text!` browser entry point continues to work via Playwright snapshot.

### Notes

- This is the *only* mutation in the project where the client/server logic duplication is non-trivial. Doing it once isn't establishing a pattern — it's eliminating a one-off.

---

## #3 — Optional: Guardrails consistency audit

**Verdict:** ⚪ **Discretionary.** Half-hour scan; likely finds nothing meaningful.

### Problem statement

`>defn` is heavy in `learn.model.list`, `learn.model.review`, `learn.util.url-encoding`. Lighter in `learn.util.storage`, `learn.util.tasks-io`. Almost absent in `learn.client.*` and `learn.client.ui.*`. The asymmetry isn't accidental — the model layer has the strongest gspec-able shape (per-keyword schemas via the `:learn.model.schema/*` registry), and UI namespaces are mostly Fulcro-DOM hiccup which doesn't benefit from contracts.

But are there model-adjacent helpers in `util/*` or `client/*` that *would* benefit from `>defn` and don't have it yet?

### Proposed implementation outline

- One pass through `learn.util.*` and `learn.client.lifecycle.*` looking for functions that:
  - Take a domain map (`:todo/*`, `:list/*`, `:ui/*`) as input
  - Return a transformed domain map
  - Are not already `>defn`'d
- For each candidate, add a gspec referencing the existing `:learn.model.schema/*` keywords
- Run the master test runner to confirm no contract violations are flagged in the existing test suite

### Effort estimate

**XS (~30-60 min).** Pure scan + a few small edits.

### Risk / dependencies

- **None.** Adding `>defn` is purely additive — if a gspec is wrong, the contract fires loudly in tests and you fix it; if there's no model-adjacent helper to upgrade, the scan yields zero edits and you've spent half an hour.

### TDD acceptance criteria

- For any function upgraded `defn → >defn`, the existing tests continue to pass (proves the gspec is at-least-as-loose as the implementation behavior).
- For each upgraded function, at least one assertion in the project's spec suite exercises the gspec's tightness (otherwise the gspec is dead code).

### Notes

- If the scan yields zero upgrades, that's a valid outcome — the project's `>defn` coverage is already where it should be.
- Don't blanket-upgrade `defn → >defn` in UI namespaces. The cost of gspec'ing Fulcro DOM-producing functions (which return hiccup) exceeds the benefit.

---

## #4 — Defer: `learn.model_rad.todo` / `learn.model.todo` namespace split

**Verdict:** 🔴 **Defer.** Cost-to-benefit doesn't pencil out at this scale.

### Problem statement

The reference splits per-entity files into `model_rad/<entity>.cljc` (RAD attribute declarations only) and `model/<entity>.cljc` (Pathom resolvers + mutations + business logic). Today our equivalent file (`learn.model.list`) holds business logic; RAD attribute decls are in `learn.rad.attributes`. The split exists at the *namespace level* but not at the *per-entity level* the reference uses.

### Why defer

- The reference's pattern pays off at **N entities**: each entity gets two files, making the per-entity surface area easy to scan. At N=1 (we have only one entity — `:todo`), the split adds a directory layer without making anything clearer.
- The Phase 9.4 doc artifact already capped RAD's role to "attributes + input rendering, no forms/reports." If we later expand RAD coverage, **then** the split earns its keep. Until then, premature.
- The rename + re-require sweep would touch every model-layer test and every client mutation. M-level cost for L-level zero immediate benefit.

### Revisit condition

Promote if/when a second entity lands. The current schema has one entity (`:todo`); a hypothetical second (e.g. `:project` or `:tag`) would surface the per-entity split as actually useful, and that's the natural moment to refactor.

---

## Low-hanging fruit shortlist

If you want quick wins from this audit, do **these two and stop**:

1. **#1 — CI workflow** (Phase 24 candidate). Highest ROI; converts an existing CLAUDE.md hard rule from discipline-based to automation-based. ~1-3 hours.
2. **#3 — Guardrails consistency audit** (no phase needed; can be done inline during any future model-touching session). ~30-60 min.

Skip #2 (`m/returning`) unless you're doing other work in `import-from-text` anyway — the duplication is annoying but not painful, and the refactor's value is narrow.

Skip #4 (namespace split) outright — defer to "if/when we add a second entity."

---

## Process reflections

A few observations about the audit itself, worth keeping for any future similar exercise:

- **Reference-at-different-scale produces mostly ⊘ verdicts.** 6 out of 8 Bucket A items came back ⊘ (not-applicable-at-this-scale). That's a feature, not a bug — it confirms the project's prior scope decisions (Phase 8, 10, 11 doc artifacts) held up against direct comparison. If you ever audit this project against another reference and get *more* than ~25% non-⊘ verdicts, that's evidence of structural debt to investigate.
- **Bucket B is more useful for outward-facing artifacts** than inward refactor candidates. Five of five Bucket B items came back ✓ by-design for our project; the same data turned into a curriculum-feedback doc for someone else's project. Audits like this one have asymmetric value — *small* for the auditor, *potentially significant* for the audited reference.
- **The recon-then-comparative split paid off.** Phase 23.1's "catalog only, no recommendations" rule kept the agents on-task. Phase 23.2's "Tony-voice + counterargument" forced engagement with the existing doc artifacts rather than reflexive "this is the Fulcro Way" deference. The split is reusable for future audits.
- **Doc-artifact decisions held up.** None of the Phase 8 / 10 / 11 doc artifacts (`when-to-statechart.md`, `when-to-use-RAD-forms-and-reports.md`, `when-to-use-pathom-prod-patterns.md`) needed revision based on the audit. They engaged with the reference's choices *in advance* and the engagement held.

## Closing

Phase 23 deliverables shipped:

- `23-1-reference-recon.md` — what the reference does
- `23-2-comparative-analysis.md` — what we do vs the reference, with verdicts
- `23-3-recommendations.md` — what to do next (this doc)
- `branch_proposals.md` (in `curriculum-onboarding-rad-project`) — outward-facing byproduct

**Recommended next step:** promote #1 (CI workflow) to Phase 24. The other three items don't need phase scaffolding — #3 can be done inline, #2 is a deferred mutation cleanup, #4 is a wait-and-see.
