# Handoff — Phase 21 + Phase 22 shipped; B-15 logged + awaiting fix

**Project:** `fulcro-solo-learn` (`C:\Users\Admin\Documents\Study\Fulcro\fulcro-solo-learn`)
**Branch:** `main`, in sync with `origin/main` (latest push: `c59258b`).
**Date handed off:** 2026-05-24.

> This session was long and busy. Phase 21 / S-dev-mode-toggles shipped
> in five sub-phases, Phase 22 / S-dev-mode-collapse-toggle shipped as
> a single follow-up sub-phase, the docs got a major reorg (phases.md
> shrunk 2109→114 lines + 39 new per-phase files for Phases 8–20), and
> the session closed by logging one new bug (B-15) and a couple of
> docs-hygiene fixes. Master runner is **134 specs / 894 assertions,
> all green**.

## Read first

1. **`CLAUDE.md`** at the repo root — refreshed early this session.
   Status line is current; new "Async waits" section explicitly bans
   `sleep N && cmd` because it kept biting agents.
2. **`docs/phases.md`** — pure status index now, ~114 lines. Phase 21
   ✅ and Phase 22 ✅. The "Queued / next" section at the bottom
   lists the open candidates.
3. **`docs/phases/`** — per-phase files. Phase 21's outline is
   `21-dev-mode-toggles.md`; sub-phases are `21-1`, `21-2`, `21-3`,
   `21-4a`, `21-4b`. Phase 22 is single-file:
   `22-debug-mode-collapse-toggle.md`. Read 22's outline before you
   touch anything dev-flag-shaped — there's an architecture lesson in
   it (next-session-relevant; see "Non-obvious context" below).
4. **`docs/bugs.md`** — B-15 is the open one. Detailed root-cause +
   suggested-fix walkthrough already in the entry.
5. **`docs/user_stories.md`** — `S-dev-mode-toggles` and
   `S-dev-mode-collapse-toggle` both ✅. `S-pwa-debug-modal` is the
   strongest 🆒 next-story candidate (sibling to S-dev-mode-toggles).

## What just shipped (commits, oldest → newest)

```
e03cc8a docs CLAUDE.md — refresh stale status line + restore Unicode
e2fcc1e docs split phases.md — migrate Phases 8-20 into docs/phases/
0d659fe docs phases.md — shrink to pure status index
e513a5d feat phase 21.1 — dev fixtures (items-5, items-26)
d440752 docs CLAUDE.md — explicit guidance on the sleep+cmd anti-pattern
e0f7e3d feat phase 21.2 — learn.dev-config (flags + persistence + pure cycler)
7f8352a refactor phase 21.3 — migrate debug-css plumbing to learn.dev-config
ef4e9fd feat phase 21.4a — cycle-step orchestrator + cycle-list! wrapper
0d0c14b feat phase 21.4b — Settings UI Debug section + closes Phase 21
333f6db docs phases/README — legitimize NN-sub-letter form for split sub-sub
7519e5d docs bugs.md — flip B-14 to closed; resolution cites the 4 fix commits
6e708ff feat phase 22 — collapsible Debug section in Settings
c59258b docs bugs.md — log B-15 (rainbow toggle also applies depth visuals)
```

## What to do next

**Top of the queue: fix B-15.** Full root-cause analysis + suggested
fix already in `docs/bugs.md` under the B-15 entry — read that first.
Summary: the upstream `pesticide@1.3.0` `pesticide.css` is a
combined outlines+depth file (794 lines, has box-shadow +
background-color rules baked in), so toggling rainbow ALONE already
applies depth visuals and the depth toggle becomes a visual no-op.
Fix: derive `pesticide-outlines.css` by stripping non-outline rules
from upstream pesticide.css, point `learn.dev-config/debug-css-links`'s
`:debug-css/rainbow?` entry at the new file, fix the docstring.

The B-15 entry recommends a small node script
(`scripts/build-pesticide-outlines.mjs`) so the derivation survives
npm bumps. Three Playwright probe scripts under `scripts/` already
characterise the bug; they should turn green after the fix:

- `probe-rainbow-toggle.mjs`
- `probe-rainbow-then-depth.mjs`
- `probe-computed-styles.mjs` — the smoking-gun probe (computed-style
  diff between "rainbow on" and "rainbow + depth on" is currently
  zero; should be non-zero after fix).

After B-15, the queue (from `phases.md`):

- `S-pwa-debug-modal` (sibling to S-dev-mode-toggles, same TDD pattern,
  builds on Phase 21 infra — best momentum play)
- `S-markdown-export` (well-scoped, mostly pure CLJC, TDD-friendly)
- Phase 19 Section-B browser-manual a11y sweep (user-driven)
- Phase 20c (deferred Lighthouse / Playwright)

## Non-obvious context the artifacts don't fully capture

- **Background nREPL is STILL RUNNING** as task `bxd8dvwfk` from this
  session, on port 7888. Discoverable via
  `clj-nrepl-eval --discover-ports`. Saves ~3 minutes of cold start on
  the first master-runner invocation. If you don't want it,
  `taskkill /F /PID <its java process>` or just leave it and ignore.
- **Shadow-cljs server is implicitly running too** — started by the
  release-build retry mid-session. Serves localhost:8000 and is
  watching CLJS sources. If you re-run `npm run release` or `npm run
  compile` it will say "0 compiled" when the incremental cache thinks
  it's up to date; check `resources/public/js/main/main.js`'s mtime
  + grep the bundle to confirm the bundle is actually fresh. This
  bites: the v1 of the phase-22-expanded snapshot LIED about the
  feature being broken because it captured a stale release bundle.
- **The Phase 22 architecture lesson** (also in
  `phases/22-debug-mode-collapse-toggle.md`): if you have UI state
  that needs to drive Fulcro re-renders, **it must live in Fulcro
  state**, not in the side-channel `dev-flags` atom.
  `app/schedule-render!` and `comp/transact!! this []` do NOT force a
  re-render when the component's queried props haven't changed —
  Fulcro's optimised renderer skips them. The fix was to add
  `:ui/debug-mode-expanded?` to TodoList's `:query` + `:initial-state`
  and use `m/toggle!!`. `dev-flags` is fine for things whose effect is
  OUT-of-Fulcro (DOM CSS injection, localStorage cursor / snapshot
  writes) — the existing watches handle those without re-render
  dependencies.
- **CLAUDE.md has a new mandatory rule** under "Async waits": never
  `sleep N && cmd`. The harness blocks it. Either wait for the
  background-task notification (the common case) or use the Monitor
  tool with an until-loop (rare, only for external state).
- **Docs/phases naming convention got an exception in this session.**
  The README (`docs/phases/README.md`) now legitimizes the
  `NN-<sub>-<letter>-<slug>.md` form for sub-sub-pieces that have
  meaningfully different verification surfaces (e.g. JVM-testable pure
  logic vs. browser-manual UI). Phase 21.4a/21.4b is the precedent;
  don't reach for it on incremental follow-ups (12.5b/c style — those
  still fold inline).
- **B-14 was actually closed earlier but bugs.md still said "Logged
  but not fixed yet"** — surfaced and fixed in commit `7519e5d` via a
  background sub-agent during this session. The B-15 entry follows
  the same `### Resolution` template.

## Verification baseline

```
Master runner (warm REPL):    134 specs / 894 assertions, 0 fail / 0 error
Fresh JVM (clojure -M:test:cljs -m test-runner):  same totals
CLJS release build (npm run release):  1.47 MB bundle, 0 warnings
Closure DCE confirmed:  "Rainbow element outlines" / "Cycle list fixture" /
                        "Dump app state" all grep to 0 in release bundle
```

The 21.4b Settings UI was verified browser-manual via Playwright
snapshots (`docs/snapshots/ef4e9fd-dirty-phase-21.4b-*.png`); Phase 22
likewise (`docs/snapshots/7519e5d-dirty-phase-22-collapsed-v2.png` +
`-expanded-v3.png`). B-15's symptoms are captured in
`docs/snapshots/6e708ff-dirty-probe-depth-only.png` and
`-probe-rainbow-plus-depth.png`.

## Suggested skills to invoke (in rough order, per fit)

- **`clojure-repl`** — MANDATORY for any Clojure execution / running
  tests. Hit the running nREPL on :7888.
- **`fulcro-spec-tdd`** — MANDATORY for Clojure(Script) work in this
  project per its own description.
- **`clj-stubs`** — for any new CLJC file that touches host env. The
  existing dev-config + modals patterns mirror `learn.util.storage`'s
  reader-conditional style — read those for reference.
- **`fulcro`** — needed for any UI-touching work (Settings modal,
  TodoList query / props, Fulcro state).
- **`tdd`** — the red-green-refactor discipline the project requires.
- **`verify`** — when you ship UI changes, the verification skill +
  Playwright snapshots via `scripts/snapshot.mjs` is the established
  path.
- **`run`** — for launching the app to drive verification.
- **`handoff`** at the end of your own session.

Skip:

- The agentic-issue-tracker / triage / story-routing skills — this
  project uses `docs/bugs.md`, `docs/user_stories.md`, `docs/ideas.md`,
  and `docs/phases.md` as the issue tracker; no GitHub Issues, no
  triage state machine.
- `grill-*`, `prototype` — no open design decisions to stress-test;
  B-15's fix is mechanical.
- `caveman` — communication style, user preference defaults are fine.

## Recommended first 30 minutes of the next session

1. Read `docs/bugs.md`'s B-15 entry top-to-bottom. Optionally run the
   three probe scripts to see the current broken state firsthand —
   they'll all complete in seconds against the running shadow-cljs
   server.
2. Pick the derivation approach. The bug entry suggests a node script;
   if that feels heavy, hand-derive the file once and check it in with
   a comment pointing at the source. Either works.
3. Strip non-outline rules from `pesticide.css` → write
   `resources/public/css/pesticide-outlines.css`. Sed/awk one-liner is
   probably enough (`box-shadow|background-color|-webkit-box-shadow`).
4. Update `learn.dev-config/debug-css-links` to point
   `:debug-css/rainbow?` at the new file. Update the docstring.
5. Re-run the three probes to confirm green. Re-snapshot rainbow-only
   + rainbow+depth to capture the visual fix in `docs/snapshots/`.
6. Bump `bugs.md` B-15's Status line to ✅ and add a Resolution
   section citing the fix commit. Master runner + commit + push.
   Should be a single small commit.

Phase number question: the B-15 fix is small enough that I'd file it
as an inline section in `bugs.md` rather than a new phase number. But
if you want phase-level visibility, "Phase 23 — B-15 fix" works too.
Ask the user at the start of the session.
