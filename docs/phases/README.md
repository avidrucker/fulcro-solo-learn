# Phases — per-phase docs

Each top-level phase has an **outline file** (e.g. `05k-prioritize-review.md`), and each numbered sub-phase has its own **detail file** (e.g. `05k-1-prioritizable.md`).

The chronological status index lives at the repo root: [`docs/phases.md`](../phases.md). It's the canonical "where are we?" doc and links to every outline here.

## Naming convention

`<phase-number-or-letter>-<slug>.md`, all lowercase, hyphens between words.

- Top-level phase outline → `<NN><letter?>-<slug>.md`
  e.g. `05k-prioritize-review.md`, `07-persistence-and-features.md`
- Sub-phase detail → `<NN><letter?>-<sub>-<slug>.md`
  e.g. `05k-1-prioritizable.md`, `07-10-theme-persists.md`, `19a-tooltip-i18n.md`

Phase 5 uses letter suffixes (5H, 5I, 5J, 5K) because those are how the source phases are named. Phase numbers stay zero-padded to two digits so file-tree sort matches chronological order.

## What stays inline (not split)

Sub-sub-pieces — e.g. 5K.5 Cycles A/B/C, Phase 6.5.1–6.5.5, Phase 7.21 sub-bullets — stay as `##` headers inside their parent sub-phase file. The 2-level depth (phase / sub-phase) is the splitting boundary.

Closed-as-doc-artifact phases (8, 10, 11) don't have files here at all — they're one-line entries in the master index pointing at their standalone `docs/when-to-*.md` docs.

## Adding a new phase

1. Add the entry to `docs/phases.md` first (status emoji + 1-line summary + link).
2. Create the outline file here with sub-phase placeholders.
3. Create sub-phase detail files as work lands.

See [`docs/infra-notes.md`](../infra-notes.md) for cross-cutting items that aren't phases per se (test runner, REPL workflow, deferred infra mandates).
