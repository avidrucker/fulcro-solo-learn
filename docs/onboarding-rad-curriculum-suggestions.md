# Branch 11 (and 12?) suggestion — onboarding-rad-project curriculum

**Audience:** Tony Kay
**From:** Avi Drucker (via fulcro-solo-learn, an AutoFocus Fulcro port)
**Date:** 2026-05-25
**Status:** Talking points / draft

## Why this doc exists

I built a small Fulcro learning project ([`fulcro-solo-learn`](https://github.com/avidrucker/fulcro-solo-learn)) — a TDD-disciplined port of the AutoFocus productivity model to Fulcro/Pathom 2. While doing a code-quality audit comparing it against your onboarding-rad-project branch 10, I noticed five things my project leans on heavily that branch 10 (and the curriculum more broadly) doesn't teach or use:

1. **Fulcrologic Statecharts** (for the binary-question review flow)
2. **Guardrails `>defn`** contracts (model layer, heavily)
3. **Hand-rolled i18n** (en / es / ja / pt; rejected `fulcro-i18n` as overkill)
4. **CSS / theme system** (Tachyons + light/dark toggle)
5. **Browser-driven tests** (Playwright + axe-core for a11y)

Of the five, items 3-5 are pretty clearly "feature requirements that the onboarding example app doesn't have" — not curriculum gaps. But **items 1 and 2 feel like genuine knowledge gaps** a curriculum graduate would want covered when they start a real Fulcro project. Hence this doc.

## The proposal

Two new branches, not one. **Branch 11 = Guardrails. Branch 12 = Statecharts.** Reasoning:

| | Guardrails | Statecharts |
|---|---|---|
| **Teaching surface area** | ~30-60 min: syntax (`>defn`, `>defn-`, gspecs), registry pattern, dev-vs-test config, when to use vs `clojure.spec` | 2-4+ hours: state/event/guard/action model, library API, when to chart vs flag, Fulcro integration |
| **Conceptual lift** | Mechanical, drop-in. Each `defn` → `>defn` is local. | Big — students need a new mental model |
| **Where it fits in curriculum** | Could go *anywhere* in the curriculum, even retroactively to earlier branches | Naturally late — after basic Fulcro patterns are solid |
| **Co-dependence on the other** | None | None |

Bundling them as one branch would either rush statecharts or pad Guardrails. The curriculum's "one concept per branch" pattern is too valuable to compromise.

---

## Branch 11 — Guardrails `>defn` contracts

### Why it deserves a branch

`guardrails.edn` and `guardrails-test.edn` are already in branch 10. The `:external-config {:guardrails {...}}` shadow-cljs wiring is there. But **no `src/main` file actually uses `>defn`** — the library is plumbed but never demonstrated. A curriculum graduate sees `[com.fulcrologic/guardrails …]` in `deps.edn`, doesn't know what it does, and either:

- ignores it (loses the safety net),
- or learns from a separate source (you missed the chance to teach the *idiomatic* way to wire it into a Fulcro+RAD stack).

### Suggested scope

- **What `>defn` adds over `defn`**: gspec syntax `[arg-schema => return-schema | additional-conditions]`, dev-vs-test split (silent vs throw), per-fn opt-in.
- **gspecs that match RAD attributes**: how to keep `:employee/first-name` validations co-located with the attribute *and* available as a gspec in a `>defn` that receives one. This is a juicy integration point — your existing curriculum already declares `ao/validator` on attributes; `>defn` is the "now use that validator in pure model functions" companion.
- **When to use vs `clojure.spec`**: `>defn` is friendlier (Malli-based, better error messages, Expound by default).
- **The dev-vs-test config split**: `{:throw? false}` for silent dev, `{:throw? true}` for tests-fail-loudly. Important because students will hit a fresh-process surprise where local REPL tests pass but CI fails on contract violations — this is exactly the kind of footgun the curriculum should defuse.

### Why now

In my own project I caught at least two bugs that `>defn` flagged at the layer boundary (model function receiving the wrong-shape map from a mutation). Those bugs would have been one-Pathom-call away from production in a real RAD app. A curriculum that *teaches* this safety net is more honest than one that just declares the dep.

---

## Branch 12 — Fulcrologic Statecharts

### Why it deserves a branch

The `com.fulcrologic/fulcro` umbrella includes UISM (`com.fulcrologic.fulcro.ui-state-machines`), and the standalone `com.fulcrologic/statecharts` library exists — but **neither shows up in branch 10**. The curriculum doesn't currently distinguish flag-worthy state from chart-worthy state, so students reach for ad-hoc flags + booleans every time, and then accumulate state-management debt when flows grow past 4-5 states.

I wrote a [decision-criteria doc](./when-to-statechart.md) for my project after closing one phase as "this looked chart-worthy but is really a flag" and another as "this looked flag-worthy but really wants a chart" — that doc has *worked examples on both sides* (a 2-state conflict-modal that stayed a flag; a 4-state review flow with per-decision guards + history that became a chart).

### Suggested scope

- **Conceptual model**: states, events, guards, actions, entry/exit, history pseudostates. Don't slip into HSM jargon; keep it concrete.
- **Library landscape**: `fulcro/ui-state-machines` (UISM, deprecated-ish but widely used) vs `com.fulcrologic/statecharts` (newer, SCXML-flavored). When to pick which.
- **The decision criteria**: my notes are that 2 states + 1 implicit guard isn't worth the chart machinery; 4+ states with per-event guards is. Counts of states / events / guards as a rule of thumb.
- **Fulcro integration**: where does the chart live (Fulcro state? side atom?), how do components subscribe, how do transitions get triggered from `defmutation`, how is server-side history captured.
- **Worked example**: the "two-mode form" from `employee.cljs:96-114` (create vs edit) is already a tiny state machine implicit in a `cond`. Promoting it to a chart for pedagogy could be the branch's central exercise.

### Why now

A real Fulcro app accumulates flows that *want* to be charts but are hidden in nested `cond` blocks across multiple mutations. Once a curriculum graduate has shipped 2-3 apps without ever hearing about statecharts, they bake the wrong instinct into their architecture. Teaching the decision criteria *while the app is still small* is much cheaper than retrofitting charts later.

---

## The other three items (out of scope for branches, IMO)

For completeness — three more things my project does that branch 10 doesn't, that I do *not* think need branches:

| Item | Why it's not branch-worthy |
|---|---|
| **i18n (en/es/ja/pt)** | A real feature requirement of my product, not a Fulcro pattern. The curriculum's example app is English-only by design. If someone wanted to teach i18n, the natural vehicle is *fulcro-i18n*, but I deliberately rejected it as overkill for ~30 keys × 4 locales. A branch teaching *when to use `fulcro-i18n` vs hand-roll* would be a doc artifact, not a branch. |
| **CSS / theme system (Tachyons + dark mode)** | Pure product decision. Semantic UI is your default for good pedagogical reasons (everything looks ok with zero CSS effort). |
| **Browser-driven (Playwright) tests** | I needed these for a11y (axe-core, focus management, keyboard nav). They cover a surface fulcro-spec doesn't reach. But "do you need browser e2e?" is highly project-specific; a branch teaching it would risk implying it's always-on. |

If you wanted to add *any* of these, I'd suggest a "Branch 13 — disciplines for real-world Fulcro apps" wrap-up branch that surveys them at low resolution, points students at the right libraries / resources, and leaves implementation to projects that need it.

---

## What I'd love your feedback on

1. Does the **two-branch split** make sense to you, or do you see them as one?
2. **Placement** — would Guardrails fit earlier in the curriculum (before RAD complexity grows), or is "end-of-curriculum disciplines" the right slot?
3. Statecharts feels like the heavier add. Would you want it to **build on the existing app** (promoting some flow that's currently a flag), or **introduce a new feature whose shape demands a chart**?
4. Anything I've missed from my project that *should* be in the curriculum, or anything I'm using that you'd actually recommend *against*?

Happy to share the comparative analysis doc that drove this (`docs/phases/23-2-comparative-analysis.md` in [fulcro-solo-learn](https://github.com/avidrucker/fulcro-solo-learn)) if you want the full receipts.
