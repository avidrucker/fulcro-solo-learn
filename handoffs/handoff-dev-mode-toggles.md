# Handoff — implement S-dev-mode-toggles (debug mode in Settings)

**Project:** `fulcro-solo-learn` (`C:\Users\Admin\Documents\Study\Fulcro\fulcro-solo-learn`)
**Branch:** `main`, in sync with `origin/main` after the push at end of session.
**Date handed off:** 2026-05-23.

> **Addendum 2026-05-24:** Doc verified fresh. Core task untouched — no
> `learn.dev-fixtures` / `learn.dev-config` ns yet; `debug-css-options` and
> `install-debug-css!` still live in `learn.client.cljc`. All 5 commits on
> `main` since handoff are docs-only. **One stale reference to fix mentally:**
> `docs/phases.md` was split into per-file `docs/phases/NN-*.md` (commits
> `c3f3d34`, `7ec575d`, `a68489d`). So the "update `docs/phases.md` with a new
> Phase 22 entry" step below now means "add `docs/phases/22-dev-mode-toggles.md`
> and link it from `docs/phases/README.md`." Same intent, new layout. Also: the
> background `shadow-cljs watch` task id `bf10czokk` referenced below is dead
> from the previous session — start your own.

The previous session closed bug **B-14** (modal close-gutter not reaching page bottom on overflow) and designed — but did NOT implement — a new dev-mode feature, **`S-dev-mode-toggles`**. The next session implements it.

## What you are picking up

**Read first, in this order:**

1. `CLAUDE.md` at the repo root — project hard rules. The TDD-first / stub-then-implement / one-sub-step-at-a-time rules are load-bearing for this project; respect them.
2. `docs/agents/domain.md` — points to `SCHEMA.md`, `learned_while_making_this.md`, etc. — the domain glossary this project uses *instead of* a CONTEXT.md.
3. **`docs/ideas.md`, section "Debug-mode controls in Settings" (tag `dev-mode-toggles`).** This is your spec. Architecture, dep graph, fixture shapes, snapshot/restore, test plan, gating model — all there.
4. `docs/user_stories.md`, `S-dev-mode-toggles ⬜` — short story stub pointing back at ideas.md.

Do not re-design. The design questions were closed in conversation; the answers are in ideas.md. If you find a design choice unclear, surface the question — don't silently choose.

## What was just shipped (commits on `main` since this session opened)

```
27b572a docs S-dev-mode-toggles — design + toggle defaults back to false
256e40d wip dev — debug-css-options map + goog.DEBUG-gated installer
2feab35 fix B-14 — finalise: o-0 on close-gutter, project debug-css toggle
9adc520 merge B-14 fix (H1) — DOM restructure + modal-shell min-h-100
58667a9 experiment B-14 H1 — fragment-root DOM: header + main as siblings
809721d wip B-14 — dev probe infrastructure for modal-overflow investigation
```

## Repo state at handoff

- **Working tree:** clean except for untracked artifacts you can ignore or clean (`docs/e2e_tool_research.md`, `e2e/package-lock.json`, several `e2e/*.png` screenshots from probe runs).
- **Tests:**
  - `e2e/modal-overflow.spec.js` — **green** (B-14 closed).
  - `e2e/keyboard-and-a11y.spec.js` — 1 **pre-existing** failure on the Portuguese label test (`/Adicionar Tarefa/i` regex; actual button text is just `Adicionar` after the Phase 21 label-shortening commit `617ea2b`). Confirmed broken on `main` BEFORE B-14 work. **Don't chase.**
  - JVM master test runner (per CLAUDE.md) was NOT run this session — no JVM tests touch the components/modals DOM, so no regression risk from B-14 changes, but if you touch model layer code, run it.
- **Probe specs in `e2e/`** that were one-shot dev tools and can be deleted by you when convenient: `modal-overflow-probe.spec.js`, `modal-blue-check.spec.js`, `modal-bodychildren-probe.spec.js`. Their job is done; B-14 is closed; the screenshots they generated (PNGs in `e2e/`) are also disposable.

## Non-obvious context the artifacts don't capture

- **`shadow-cljs watch app` is running in the background** as task id `bf10czokk` from this session's process tree, serving on `localhost:8000`. The user also has a separate shadow-cljs + JVM running for an unrelated `fulcro-book` project on different ports — **do not touch those**. You can either keep the existing background process or stop and restart it cleanly.
- **`learn.client/debug-css-options` and `install-debug-css!`** currently live in `learn.client.cljc` as a static def + helper. Per the design, your first move is to **move (not copy)** them into the new `learn.dev-config` ns. The runtime API will grow around them.
- **The user rejected `position: fixed`** during the B-14 investigation as a fix path. The final B-14 fix kept `absolute` via DOM restructure + `min-h-100` on modal-shell. If your dev-fixture cycling somehow surfaces a layout regression that tempts you toward `position: fixed`, **don't** — it's already been discussed and ruled out for this codebase. The user wants `absolute` semantics with normal document flow throughout.
- **`learn.dev-fixtures` must stay dependency-clean** — depends only on `learn.model.schema` (or whatever the schema namespace is). No `learn.client`, no `learn.server`. The list-cycler logic that MUTATES `SERVER-DB` lives in `learn.dev-config`, not in `dev-fixtures`. This keeps the dep graph acyclic.
- **`debug-css-options` is currently `{:rainbow false :depth false}`** at HEAD. Earlier in the session it was flipped `true` for the user's manual browser inspection of the B-14 fix; the final state on `main` is back to `false`. The user accepts either value as prod-safe via the `goog.DEBUG` dead-code-elimination gate.
- **i18n is explicitly OUT of scope** for the debug section — dev only, English labels only. Don't add `:debug/*` keys to the i18n translations maps.
- **A11y is IN scope** — buttons must be keyboard-driveable, ARIA-labeled, in tab order. See Phase 19 commitments in `phases.md` (skip-link, focus management, escape-to-close) for the existing a11y bar.
- **The user invokes this session via `/handoff`** specifically because the design discussion is durable in docs, but ephemeral session state (e.g., the rejection of `position: fixed`, the choice of H1 over H2 and why) needed a one-shot carry-over. **Read this doc; don't re-derive.**

## Recommended first steps for the next session

In order:

1. **Confirm the spec is unambiguous.** Re-read `docs/ideas.md` `dev-mode-toggles` section + `docs/user_stories.md` `S-dev-mode-toggles`. If anything looks under-specified, surface to user — don't choose silently.
2. **Find a phase slot.** This is a new feature; per CLAUDE.md, work is phase-tracked. Ask the user whether to create a new phase (e.g. "Phase 22 — dev-mode toggles") or attach to an existing one.
3. **Write the tracer-bullet test first**, per the project's TDD-first rule and the `tdd` skill. Start with the smallest seam — likely fixture-data validity tests in `test/learn/dev_fixtures_test.cljc`:
   - "26-item fixture has 26 items, first :ready, rest :new" — falsifiable shape assertion.
   - "5-item fixture is schema-valid per §5 active-status ordering."
4. Stub `learn.dev-fixtures` with the minimum needed to make the failing test fail in the right way (not crash), then implement to green. Repeat for cycler logic, then CSS install/uninstall, then UI.
5. **Move** (don't copy) `debug-css-options` + `install-debug-css!` from `learn.client.cljc` to the new `learn.dev-config.cljc`. The `learn.client/init` call site changes from `(install-debug-css!)` to `(dev-config/install-debug-css-from-runtime!)`.
6. **Settings-UI integration last.** The cycler/dump/toggle UI in `learn.client.ui.modals/settings-modal` is the integration step. Wrap the whole debug section in a CLJS `(when ^boolean goog.DEBUG ...)` at render time so release builds drop it.

## Suggested skills to invoke (in order, per fit)

- **`tdd`** — load this first; the project's hard rule is no code without a failing test. The `tdd` skill's vertical-slice / tracer-bullet discipline matches CLAUDE.md's "stub-then-implement" rule.
- **`fulcro-spec-tdd`** — MANDATORY per its own description when writing Clojure(Script) on a project with fulcro-spec as a dependency. This project does. Use for the `dev-fixtures` tests.
- **`clojure-repl`** — MANDATORY for any Clojure execution / running tests. The project has a JVM REPL convention + a CLJS REPL pattern documented in `docs/dev_scripts.md`.
- **`guardrails`** — `>defn` + `:learn.model.schema/*` is the project's contract pattern. Any new model-layer fns must use it. Fixture defs are pure data so may not need contracts, but the cycler logic does.
- **`fulcro`** — for the settings-modal integration step. State management + transactions + the `[:list/id 1]` ident pattern this project uses.
- **`zoom-out`** — cheap, useful when re-entering an unfamiliar part of the modals/components tree. Free of project-specific assumptions.
- **`handoff`** — at the end of this implementation session, before the next break, run /handoff again so the *next* next-session has continuity.

Skills to SKIP for this work:
- `to-issues`, `to-prd`, `triage`, `setup-matt-pocock-skills` — solo project, no issue tracker, design already in `docs/ideas.md`.
- `grill-with-docs`, `improve-codebase-architecture` — these assume a CONTEXT.md+ADR layout the project deliberately doesn't have (see `docs/agents/domain.md`).
- `caveman` — communication style, user preference; defaults are fine.

## Definition of done for the next session

- `learn.dev-fixtures.cljc` created with the 4 fixtures (`:empty` re-uses `learn.server/empty-state`; `:5` and `:26` are new; `:actual` is a marker, not a static value). All schema-valid. fulcro-spec tested.
- `learn.dev-config.cljc` created with: `dev-flags-defaults`, `dev-flags` atom, install/uninstall CSS helpers, cycler logic (snapshot/restore via `autofocus.dev-list-snapshot` localStorage key). Pure parts tested.
- `learn.client.cljc` updated: old `debug-css-options` / `install-debug-css!` removed; init calls into `dev-config`.
- `learn.client.ui.modals/settings-modal` updated: new "Debug mode" section, all controls wrapped in `(when ^boolean goog.DEBUG ...)`, ARIA-labeled.
- `docs/phases.md` updated with the new phase entry (e.g. Phase 22) flagged ✅.
- `docs/user_stories.md` — `S-dev-mode-toggles` flipped from ⬜ to ✅ (or 🟢 if browser-manual coverage rather than spec-suite).
- Master JVM test runner green; e2e B-14 test still green; pt label test still failing (unchanged, not your problem).
- Commit message references the phase + the user story tag.
