# Infrastructure notes

Cross-cutting items that aren't phases per se: pieces of the dev/test machinery that evolve continuously, out-of-arc directions deliberately not pursued, and deferred items waiting for a payoff phase.

For per-phase work see [`docs/phases.md`](./phases.md) (chronological status index) and [`docs/phases/`](./phases/) (per-phase outlines + sub-phase details).

---

## Optional / out of arc

- **DataScript swap.** Replace the atom-as-database with DataScript. Adds datalog queries and history. Pure learning detour — the AutoFocus model doesn't need it. Could slot in any time after Phase 7 if the user wants exposure.
- **Real backend (Datomic / Postgres / etc.) + tempids.** Would re-introduce HTTP, async coordination, and tempid rewrites. Excluded from the current learning arc by the front-end-only decision; lives here as a possible future direction.

---

## Recurring infrastructure (cross-cutting)

These aren't phases per se — they evolve continuously across phases.

- **Test runner**: Master runner (terse + verbose), per-ns runners. Source-of-truth snippet lives in `CLAUDE.md`.
- **REPL workflow**: Cursive custom commands, project-only reload to keep iteration under a second. See `CLAUDE.md` for the `clj-nrepl-eval` usage pattern.
- **Specs**: Started as plain `def` in 5I.0.5, upgraded to `>def` registry in 5I.1, paired with `>defn` from 5I.2 onward.
- **Doc layer**: `docs/SCHEMA.md` is the canonical domain reference; `docs/phases.md` is the chronological status index linking into `docs/phases/`; `docs/learned_while_making_this.md` is the running retrospective; `docs/bugs.md` tracks open and resolved bugs; `docs/changes.md` catalogues intentional divergences from the JS port.

---

## Deferred infrastructure items

These are mandated by the `fulcro-spec-tdd` skill or otherwise recommended, but deferred because they don't yet pay their own cost at the current project size. Each has a planned landing phase.

- ~~**Guardrails `:all` mode + `:covers` proof-system sealing.**~~ **Decided 2026-05-25 (do not adopt).** Marginal value over `main.yml`'s existing fresh-JVM CI run doesn't justify the annotation cost at this scale. Rationale: [`guardrails_policy.md`](./guardrails_policy.md) Decision 1.

- ~~**Per-test guardrails-test.edn config with `:throw? true`.**~~ **Adopted 2026-05-25.** `guardrails-test.edn` + `guardrails.edn` added at repo root; `:test` alias updated. Tests now throw on contract violations. Rationale: [`guardrails_policy.md`](./guardrails_policy.md) Decision 2.

- **Pre-warm `dev/user.clj` for fast first-run REPL.** Identified in Phase 5H as a performance optimization (30s cold start → sub-second first run). Defer until we're restarting the REPL often enough that the cold-start cost matters; with a stable deps.edn, most days we restart 0 times.
