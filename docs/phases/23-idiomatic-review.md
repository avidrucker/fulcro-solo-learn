# Phase 23 — Idiomatic-Fulcro audit

**Status:** 🟡 In progress (23.1 underway)

A code-quality audit comparing the AutoFocus Fulcro port against Tony Kay's onboarding-rad-project curriculum (`~/Documents/Work/onboarding-rad-project/` branch `10-report-row-actions`, plus the companion `curriculum-onboarding-rad-project/` lecture path). Goal: surface where this project is or isn't idiomatically the best it could be, and where Pathom 2 / RAD / statecharts could be leveraged more (or less, if they're overkill).

**Audit voice — Tony-as-reviewer plus counterargument.** Each finding gets Tony Kay's framework-author perspective ("this is / isn't the Fulcro Way") AND a "but in our context…" rebuttal that engages with the project's prior decisions. Reverse-recommendations against existing doc-artifact decisions require explicit evidence from the reference repos; the doc-artifact rejections stand otherwise.

## Sub-phases

- 🟡 [23.1 — Reference-repo reconnaissance](23-1-reference-recon.md) — three parallel Explore agents catalog Pathom 2 / RAD / statechart / Fulcro UI / testing / Guardrails idioms from branch 10 + the curriculum lecture path. Output: structured idioms catalog with file:line references back to the reference repos.
- ⬜ [23.2 — Comparative analysis](23-2-comparative-analysis.md) — for each cataloged idiom, classify against fulcro-solo-learn as ✓ adopted / 🔄 could adopt / ❌-by-design (cite the doc) / ⊘ not applicable at this scale. Each 🔄 carries explicit cost/benefit + counterargument.
- ⬜ [23.3 — Prioritized recommendations](23-3-recommendations.md) — the deliverable. Gap list bucketed by effort × value, with the low-hanging-fruit shortlist called out. Each item is a candidate Phase 24+ story.

After 23.3 lands, individual recommendations become candidate Phase 24+ implementations (each gated on its own TDD red-green plan per CLAUDE.md).

## Scope guardrails

- **Honor prior decisions unless evidence overturns them.** Three doc artifacts already encode "we considered this and decided no":
  - Phase 8 — [`when-to-statechart.md`](../when-to-statechart.md) (the conflict modal was not chart-worthy; the review flow was).
  - Phase 10 — [`when-to-use-RAD-forms-and-reports.md`](../when-to-use-RAD-forms-and-reports.md) (`defsc-form` / `defsc-report` would be net-negative refactors at this scale).
  - Phase 11 — [`when-to-use-pathom-prod-patterns.md`](../when-to-use-pathom-prod-patterns.md) (per-request env, batch resolvers, mutation return values don't pay off at single-user / atom-DB / in-process scale).

  These stand unless 23.1 surfaces a concrete branch-10 pattern that the existing rationale doesn't account for.

- **No Datomic.** SERVER-DB is an atom. Datomic-specific patterns get cataloged as "would apply if SERVER-DB → Datomic" rather than recommended for migration.

- **Honor scale.** Single-user, ~30-item lists, in-process JVM with browser client. Each recommendation must justify itself at *this* scale, not at "enterprise RAD app" scale.

- **No code edits in 23.1–23.3.** Pure analysis phase. The deliverables are three markdown docs. Implementation lands in Phase 24+.

## Skills inventory

In rough order of relevance:

- `fulcro-rad` — RAD attribute / form / report patterns
- `pathom` — Pathom 2 resolver / env / plugin idioms
- `statechart` — chart-worthy vs flag-worthy decision criteria, CLJ vs Fulcro-integration patterns
- `fulcro` — UI / query / ident / mutation / lifecycle patterns
- `eql-processing` — EQL-AST opportunities
- `fulcro-spec-tdd` — test architecture
- `guardrails` — `>defn` contract coverage + idiomatic gspec shapes
- `clojure` — general Clojure idioms
- `improve-codebase-architecture` — explicit "find deepening opportunities" pass; `docs/SCHEMA.md` substitutes for CONTEXT.md, `docs/learned_while_making_this.md` for ADRs per [`docs/agents/domain.md`](../agents/domain.md).

Skipping: `datomic` / `seed-data` (NA — atom-as-server); `fulcro-uism` (project uses statecharts, not UISM); `code-review` (designed for pending-diff review, not whole-codebase historical pass).
