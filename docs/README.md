# Docs index

Where to look, and where to add. Each doc has one lens; keeping them
disjoint avoids the "is this in phases or stories or bugs?" question.

## At a glance

| Doc | Lens | "Add to this when…" |
|---|---|---|
| [`phases.md`](./phases.md) | **How** we get there — the development arc, in learning order | A phase / sub-phase lands. Closed phases get a retro block; open phases get a sketch. |
| [`user_stories.md`](./user_stories.md) | **What** the app does — current behavior, planned behavior, and acknowledged cuts | A user-visible behavior changes, or a new one is planned / promoted / cut |
| [`bugs.md`](./bugs.md) | **Where intent ≠ reality** — discrepancies between the JS port (or stated UX) and our port | A defect is reported. Stays until fixed (then links to the fix commit) |
| [`ideas.md`](./ideas.md) | **Maybe** — speculative tweaks with no clear decide-when | An idea comes up mid-conversation and we don't want to start building it yet |
| [`SCHEMA.md`](./SCHEMA.md) | **Invariants** — canonical domain reference | Domain shape, status enum, or operation contract changes |
| [`learned_while_making_this.md`](./learned_while_making_this.md) | **Retrospective** — past mistakes, by category | A mistake worth not-repeating shows up |
| [`js_source_reference.md`](./js_source_reference.md) | **JS port reference** — signatures + divergence notes for each fn in the original | A new model-layer function is being ported |
| [`js_ui_reference.md`](./js_ui_reference.md) | **JS port UI reference** — class strings, modal structure, etc. | A new UI component is being ported |
| [`browser_dev.md`](./browser_dev.md) | **Browser dev workflow** — shadow-cljs, REPL, Inspect | The browser-side dev loop changes |
| [`clj_project_stats.md`](./clj_project_stats.md) | **Project size** — LOC by namespace | When summarizing project scale; touched occasionally, not authoritative |
| [`snapshots/`](./snapshots/) | **Visual record** — PNGs of the app at landmark commits | A phase introduces visible UI change; `reference/` mirrors the same in the deployed JS port |

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
