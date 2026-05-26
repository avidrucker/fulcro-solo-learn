# Guardrails policy

Two decisions made 2026-05-25 after the Phase 23 audit + a session discovery that contract enforcement was partial / silent / inconsistent across dev / test / prod.

## Decision 1: Do NOT adopt `:all` mode + `:covers` proof-system sealing

### Context

`docs/infra-notes.md` had a deferred-infrastructure item: *"Guardrails `:all` mode + `:covers` proof-system sealing. The fulcro-spec-tdd skill mandates `:covers` metadata on every specification for transitive coverage and staleness detection. Defer to Phase 5I.6 or after: seal all existing specs in a batch once we have ~20+ specs."*

We're now well past that trigger: **134 tests / 147 `specification` declarations across 13 test files.**

### What `:all` mode would buy us

- **Staleness detection at spec-load time** — a spec that references a removed function fails immediately instead of at test execution.
- **Self-documenting metadata** — `^{:covers '[learn.client/foo]}` declares what each spec tests.
- **Coverage gap automation** — a function with no covering spec can be detected programmatically.

### What it would cost

- Annotating ~134 specifications with `:covers` metadata (one-time pass).
- Switching Guardrails mode (changes compile semantics; risk of unexpected interactions).
- Maintaining the annotations as code evolves.

### Why we're NOT adopting

The Phase 23.2 / 23.3 audit (and the C12 correction that followed) made the protection layer visible:

- `.github/workflows/main.yml` already runs `clojure -M:test:cljs -m test-runner` in a fresh JVM on every push to `main`. A spec that references a removed var fails CI with `No such var: …` at test execution — the same bug class `:covers` would catch at spec-load time.
- CLAUDE.md's `stale-vars-after-refactor` hard rule provides the discipline layer.
- The `learned_while_making_this.md` Phase 12.7 retrospective records the one historical case we hit; it was caught by CI.

The marginal value of `:covers` over `main.yml`'s existing fresh-JVM check is:

- **Earlier detection** (spec-load vs test-execution). Test-execution is already fast in CI; the earlier-by-seconds gain is theoretical for solo dev.
- **Self-documentation**. Spec names already encode what they test (`add-todo*`, `cancel-todo mutation`, etc.); `:covers` metadata duplicates that.
- **Automated gap detection**. We don't have a coverage-blindspot pain point pulling for this; absent a real symptom, adding the machinery would be cargo-culting.

**Verdict:** the marginal value doesn't justify the annotation cost at this scale.

### When to revisit

Promote `:all` + `:covers` adoption if any of these hits:

- A real refactor causes spec-related CI confusion that `:covers` would have prevented (i.e. a Phase 12.7-shaped incident that `main.yml` misses).
- The spec count crosses ~500 and the "which spec covers what?" question becomes hard to grep.
- A second developer joins the project and needs the self-documentation.

None of those triggers is currently active.

---

## Decision 2: Contracts log silently in dev, throw loudly in tests

### Current state (incoherent)

Pre-policy:
- **No `guardrails.edn`** in repo. No `guardrails-test.edn` either.
- **`deps.edn` `:test` alias** has `:jvm-opts ["-Dguardrails.enabled=true"]`.
- **No CLJS external-config** for Guardrails in `shadow-cljs.edn`.

What that means in practice:

| Context | Guardrails behavior |
|---|---|
| JVM dev REPL | OFF entirely (no `-D` flag) |
| JVM tests | ON but using default config — **logs violations to stderr; does not throw** |
| CLJS dev | OFF (no external-config) |
| CLJS release | OFF (DCE'd by `goog.DEBUG`, but moot since dev is off too) |

Net effect: **contracts are decoration, not enforcement.** The 34 `>defn`'s across `learn.model.*`, `learn.util.*`, `learn.rad.attributes` were silently logging violations that tests would never fail on. A violation would have to also break a `clojure.test/is` assertion to be caught.

### Target policy

| Context | Behavior | Rationale |
|---|---|---|
| JVM dev REPL | OFF | Inner-loop speed first; contracts add per-call overhead. Re-enable per-session if debugging a contract-shaped bug. |
| **JVM tests (`:test` alias)** | **THROW** | Tests are where contracts must earn their keep. Loud failures > silent logs. |
| CLJS dev | OFF (current default) | Browser-side throws would crash the page mid-render on edge cases. |
| CLJS release | OFF (DCE'd via `goog.DEBUG`) | Unchanged. |

The single hot point of the policy: **tests throw on contract violations.** Everywhere else contracts are off; documentation-only.

### Implementation

Reference: `com.fulcrologic.guardrails.config` docstring explains that an alternate config file is selected with `-Dguardrails.config=<filename>`.

Three files:

**`guardrails-test.edn`** (top of repo) — the test-time override:
```edn
{:throw?      true
 :emit-spec?  true}
```

**`deps.edn`** `:test` alias updated:
```clojure
:test {:extra-paths ["test"]
       :extra-deps  {fulcrologic/fulcro-spec {:mvn/version "3.2.8"}}
       :jvm-opts    ["-Dguardrails.enabled=true"
                     "-Dguardrails.config=guardrails-test.edn"]}
```

**`guardrails.edn`** (top of repo) — minimal default that any non-test Guardrails-enabled context would pick up. Empty since we don't enable dev contracts:
```edn
{}
```

(The file exists primarily as a placeholder for the rare future case where someone adds `-Dguardrails.enabled=true` to a dev alias.)

### Verification

Master test runner before policy change: **134 / 894 / green.**

Master test runner after policy change: **134 tests / 883 pass / 11 errors.** Eleven contract violations surfaced — all in `parse-tasks-json`'s failure-paths tests. Root cause: the `::error-type` enum in `learn.model.schema` was missing `:error/non-json` and `:error/bad-json`, both of which `parse-tasks-json` had been legitimately returning since Phase 13 (file-import error modes). The silent-log default had been masking this for nine phases.

Fix: added the two missing error types to `::error-type` in the same commit as the policy adoption. Re-run after the fix: **134 / 894 / 0 fail / 0 error.**

**This is the immediate value-add of the policy.** A real schema-vs-implementation mismatch was hidden by the silent-log default for nine phases; the throw config exposed it in the first test run after the switch. Future schema drift will be caught equally fast.

### When to revisit

- If contract overhead becomes measurable in tests (>5% test-runtime hit), per-namespace exclusions or `:guardrails/mcps` throttling (per the Guardrails README's "Throttled Checking" section).
- If we adopt CLJS-side specs and want browser-time enforcement — would mean adding `:external-config {:guardrails {...}}` to `shadow-cljs.edn`'s `:main` build.
- If we adopt a `:dev` Clojure alias with `-Dguardrails.enabled=true` — would mean `guardrails.edn` gets `{:throw? false :emit-spec? true}` instead of empty.

---

## Cross-references

- `docs/infra-notes.md` — the deferred-items list. Both entries this doc supersedes are removed there.
- `docs/learned_while_making_this.md` — Mistake C12 (incomplete-recon-led-to-false-positive) is the meta-context for re-evaluating this; doing so symmetrically required actually reading the current config (not just assuming).
- `docs/phases/23-3-recommendations.md` — Phase 23's recommendation #3 covered consistency of `>defn` *usage*; this doc covers consistency of `>defn` *enforcement*.
