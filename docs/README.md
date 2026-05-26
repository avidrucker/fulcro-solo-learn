# Docs index

Where to look, and where to add. Each doc has one lens; keeping them
disjoint avoids the "is this in phases or stories or bugs?" question.

## At a glance

| Doc | Lens | "Add to this when…" |
|---|---|---|
| [`phases.md`](./phases.md) | **How** we get there — the development arc, in learning order (chronological status index) | A phase / sub-phase lands. Closed phases get a retro block; open phases get a sketch. |
| [`phases/`](./phases/) | **Per-phase detail** — outline file per phase + sub-phase files for anything with non-trivial scope | A phase has enough surface area to warrant its own file (vs. an inline retro block in `phases.md`) |
| [`infra-notes.md`](./infra-notes.md) | **Cross-cutting** — dev/test machinery that evolves continuously, plus deliberately-deferred / out-of-arc directions | An infrastructure decision spans multiple phases, OR a direction is deliberately not pursued and should be recorded |
| [`user_stories.md`](./user_stories.md) | **What** the app does — current behavior, planned behavior, and acknowledged cuts | A user-visible behavior changes, or a new one is planned / promoted / cut |
| [`bugs.md`](./bugs.md) | **Where intent ≠ reality** — discrepancies between the JS port (or stated UX) and our port | A defect is reported. Stays until fixed (then links to the fix commit) |
| [`ideas.md`](./ideas.md) | **Maybe** — speculative tweaks with no clear decide-when | An idea comes up mid-conversation and we don't want to start building it yet |
| [`task_suggestions.md`](./task_suggestions.md) | **Open proposals catalog** — exhaustive index of work that could be done but isn't committed to a phase; bucketed by leverage | An audit / session surfaces a concrete proposal that doesn't belong in `ideas.md` (speculative) or `phases.md` (phase-committed) |
| [`changes.md`](./changes.md) | **Diverges from the og JS port** — intentional differences, not bug fixes | We deliberately do something the og doesn't (or vice versa) |
| [`when-to-statechart.md`](./when-to-statechart.md) | **Decision criteria** — chart vs flag-with-payload | About to add new state and considering whether to reach for a chart |
| [`benefits-of-RAD-in-this-project.md`](./benefits-of-RAD-in-this-project.md) | **What RAD added here** — honest tradeoff write-up | Considering RAD adoption (or removal) in a similarly-sized project |
| [`benefits-of-i18n-in-this-project.md`](./benefits-of-i18n-in-this-project.md) | **What hand-rolled i18n added here** — why we did NOT use `fulcro-i18n` (Phase 12 followup) | Considering an i18n approach in a similarly-sized project, or revisiting the hand-roll decision |
| [`when-to-use-RAD-forms-and-reports.md`](./when-to-use-RAD-forms-and-reports.md) | **Decision criteria** — defsc-form / defsc-report | About to build a form or list view; deciding whether RAD components or hand-rolled |
| [`when-to-use-pathom-prod-patterns.md`](./when-to-use-pathom-prod-patterns.md) | **Decision criteria** — per-request env / batch resolvers / mutation return values | Setting up a Pathom parser and deciding which production-shape patterns to wire in |
| [`guardrails_policy.md`](./guardrails_policy.md) | **Decision record** — Guardrails mode + throw/log behavior across dev / test / prod | Revisiting contract enforcement (per the doc's "When to revisit" triggers) |
| [`SCHEMA.md`](./SCHEMA.md) | **Invariants** — canonical domain reference | Domain shape, status enum, or operation contract changes |
| [`a11y_audit.md`](./a11y_audit.md) | **Living a11y checklist** — Phase 19 audit; Section A (agent-fixable) vs Section B (human-must-run) | An a11y check is observed, fixed, or flagged for the user-driven Section B sweep |
| [`manual_tests.md`](./manual_tests.md) | **Manual verification checklist** — browser-only behavior the JVM runner can't TDD (focus, keyboard, contrast, persistence, etc.) | A behavior can only be verified in a real browser; record the procedure + expected result |
| [`learned_while_making_this.md`](./learned_while_making_this.md) | **Retrospective** — past mistakes, by category | A mistake worth not-repeating shows up |
| [`matt_skills_test_001_results.md`](./matt_skills_test_001_results.md) | **Skill-evaluation log** — results of testing 3rd-party agent skills (e.g. Matt Pocock's) against real material | Another formal skill evaluation is conducted |
| [`agents/`](./agents/) | **Agent-mapping** — how to direct skills that expect CONTEXT.md / `docs/adr/` onto this project's actual doc layout | A skill expects a doc convention this project doesn't follow; add a redirect entry |
| [`js_source_reference.md`](./js_source_reference.md) | **JS port reference** — signatures + divergence notes for each fn in the original | A new model-layer function is being ported |
| [`js_ui_reference.md`](./js_ui_reference.md) | **JS port UI reference** — class strings, modal structure, etc. | A new UI component is being ported |
| [`browser_dev.md`](./browser_dev.md) | **Browser dev workflow** — shadow-cljs, REPL, Inspect | The browser-side dev loop changes |
| [`dev_scripts.md`](./dev_scripts.md) | **REPL cheat sheet** — "I want to verify/tweak X, what do I run?" recipes | A useful dev-time snippet emerges that isn't covered by CLAUDE.md / `browser_dev.md` |
| [`e2e_test_research.md`](./e2e_test_research.md) | **Decision record** — why Playwright, our e2e architecture, lessons from `fp-autofocus` + `pwa-autofocus-app` | Revisiting e2e tooling or test architecture |
| [`e2e_tool_research.md`](./e2e_tool_research.md) | **Tool survey** — generic Playwright / Puppeteer / Selenium comparison (Phase 20 prep) | The browser-automation tool landscape shifts meaningfully (rare) |
| [`clj_project_stats.md`](./clj_project_stats.md) | **Project size** — LOC by namespace | When summarizing project scale; touched occasionally, not authoritative |
| [`snapshots/`](./snapshots/) | **Visual record** — PNGs of the app at landmark commits | A phase introduces visible UI change; `reference/` mirrors the same in the deployed JS port |
| [`diagrams/`](./diagrams/) | **High-level visuals** — Mermaid diagrams for AutoFocus + each tech in the stack | A new technology lands, or an existing layer changes shape |

## Status conventions (used by `user_stories.md` and `bugs.md`)

User stories use these markers:

| Mark | Meaning |
|---|---|
| ✅ | Functional **and** tested |
| 🟢 | Functional, **not** tested in the spec suite (browser-manual or pure-UX) |
| 🟡 | Stubbed — UI present but action is a no-op |
| ⬜ | Planned — will build, not started yet |
| 🆒 | Nice-to-have — no urgency, no phase commitment |
| ❌ | Won't implement — acknowledged scope cut |

Bugs use:

| Mark | Meaning |
|---|---|
| 🐛 | Open — reproducible, not yet diagnosed |
| 🔍 | Triaged — root cause identified, fix planned |
| 🛠️ | In progress |
| ✅ | Fixed — links to fix commit |

## When in doubt

- New user-facing thing → `user_stories.md` (add as ⬜ or 🆒)
- New defect → `bugs.md` (next B-N)
- New phase-completing work → `phases.md` (retro block)
- New idea with no urgency → `ideas.md`
- Changes to the type / status / contract → `SCHEMA.md`

If an item could plausibly live in two places, pick the one whose
**lens** matches the change you're making. A bug that turned into a
feature gets a `bugs.md` entry (the fix) AND a `user_stories.md`
update (the new behaviour). Cross-link with parenthetical refs.
