# Matt Pocock skills — test session 001 results

Test of Matt Pocock's `diagnose` and `handoff` skills against
real material in this project. Conducted 2026-05-22.

## Setup

- **Installation:** 16 skills from `mattpocock/skills` installed via
  `npx skills@latest add mattpocock/skills` (lives at
  `~/.agents/skills/`), bridged into Claude Code via directory
  junctions at `~/.claude/skills/`. See `~/.claude/link-agent-skills.md`
  for the bridge.
- **Matt's recommended invocation:** slash commands in the main
  agent (`/diagnose`, `/handoff`, etc.). His README does not address
  subagent-isolation patterns.
- **What we did instead:** spawned two general-purpose subagents in
  parallel, each given a self-contained task and a single skill
  to evaluate. Each ran the skill across 2-3 framings.

**Caveat that shapes the results.** Subagent isolation is a fine fit
for `diagnose` (operates on a concrete artifact — a bug). It's an
artificial fit for `handoff` (the skill is designed to compact the
*current conversation*, but a subagent's "current conversation" is
just its task prompt). We worked around that by feeding the handoff
agent a fabricated session context, but the limitation showed up in
the results and is called out below.

## `diagnose` — applied to B-14

Target: real open bug in `docs/bugs.md` — modal close-gutter button
doesn't reach the page bottom when content overflows the viewport.

### What the skill produced (Round 1: full 6-phase pass)

- **Phase 1 feedback-loop ranking** correctly demoted the cheap-and-
  wrong CLJC class-string assertion in favor of a Playwright DOM-
  geometry assertion against the existing `e2e/` harness. Specifically
  proposed `expect(overlay.boundingClientRect().bottom).toBeGreaterThanOrEqual(scrollHeight - 1)`
  with ~30 seeded todos. Concrete and runnable.
- **Phase 3 hypotheses (5, ranked, falsifiable)** mapped tightly to
  the suspected layout chain in `learn.client.ui.modals/modal-shell`
  and `learn.client.ui.components/Root`. The top hypothesis
  (`app-container`'s computed height = viewport because `<main>`'s
  `min-vh-100 flex flex-column` resolves there) was the same one
  bugs.md flagged, but the skill also generated three falsifiable
  alternatives (`fixed`-position swap probe, transform-containing-
  block ancestor, `elementFromPoint` hit-testing) that the bug doc
  did not enumerate.
- **Phase 5 seam observation** produced a load-bearing finding:
  **the bug has no correct seam at the CLJC unit-test layer.** The
  failure mode is the *interaction* of `min-vh-100` + `position:
  relative` + `absolute top-0 bottom-0` + actual content overflow —
  none observable through `:className`-string assertions. The skill
  promotes this from "test layer can't catch it (frustration)" to
  "needs Playwright-layer regression coverage (deliverable)."

### Where the skill fell flat

- **CSS / layout bugs barely addressed.** The skill's mental model is
  "code path → observable output." `position: absolute` resolving
  against the wrong containing block has no code path. Phase 4's
  debugger/REPL/targeted-log hierarchy is largely useless here;
  browser devtools' computed-styles + containing-block overlay is the
  actual instrument, and the skill never mentions devtools.
- **Hypothesis generation guidance is generic.** "3-5 ranked,
  falsifiable" is good but says nothing about *how* to generate.
  A per-domain checklist (containing block? stacking context?
  overflow ancestor? new formatting context? viewport unit?) would
  have helped.
- **`[DEBUG-xxxx]` log tagging** (Phase 4 specific advice) is
  irrelevant when the probe is a devtools click.
- **"Minimise the repro" is implicit but never named** as a phase
  or sub-step.

### Round 2 — hypothetical flake (resize-order-dependent)

The skill earned more keep here than on B-14. The non-deterministic-
bugs subsection (raise reproduction rate before debugging) applied
directly. Hypotheses shifted toward stateful causes (ResizeObserver
race, debounced layout, Tachyons class memoisation). Discipline
unchanged, loop becomes a small state-machine fuzzer.

### Round 3 — what to keep from the skill

Three things the agent flagged as worth adopting independently:

1. **The feedback-loop ladder ranking** (test → curl → CLI → headless
   browser → replay → harness → fuzz → bisect → diff → HITL).
   Rejecting the unit test for the right reason was directly
   attributable to seeing the ladder.
2. **"If no correct seam exists, that itself is the finding"** as a
   re-framing tool.
3. **"Show ranked hypotheses to user before testing"** as a cheap
   anchoring guard.

### Verdict on `diagnose`

**Keep — but selectively.** Best fit: logic bugs, async/timing bugs,
performance regressions, bugs reachable through a deterministic test
seam. Weakest fit: pure CSS/layout bugs (it doesn't mislead, but
~60% of its phase-specific advice doesn't apply).

For this project specifically: `docs/bugs.md` already has good
discipline. The skill's marginal value is the Phase 1 ladder and the
Phase 3 multi-hypothesis discipline. Useful when picking up a B-N
that's been logged but not yet diagnosed.

## `handoff` — three session-end framings

Generated three handoff docs in `%TEMP%`:
- `handoff-fix-b14.md` (argument: "fix B-14")
- `handoff-phase-20c.md` (argument: "tackle Phase 20c")
- `handoff-open.md` (no argument — open-ended)

### What varied with the argument

- **B-14 framing:** narrow opening to `docs/bugs.md` §B-14, named
  suspect component, diagnostic steps. Suggested skills: `diagnose`,
  `verify`.
- **20c framing:** opened with phase definition pointer, surfaced
  three open scope decisions, flagged the untracked
  `e2e/package-lock.json` as relevant to reproducibility. Suggested
  skill: `fulcro-headless`.
- **Open framing:** became a *decision menu* — four candidate
  options ranked, each pointing to its source-of-truth doc.
  Suggested skills bifurcated per branch.

### What stayed constant

Project path, branch state, untracked-files note, Phase 21 commit
refs, pointer to `CLAUDE.md` for hard rules, master-test-runner
mention, "what I am NOT carrying over" section.

### Honest gaps

What the skill should have prescribed but didn't:

- A "committed vs uncommitted state" snapshot.
- A "tests green / red" line.
- The branch name. (Trivial; real omission.)
- An instruction to grep for stale mid-session statements the next
  session shouldn't trust.

What the skill said to do that felt redundant given this project's
docs:

- The "don't duplicate" rule is correct but the skill doesn't tell
  you *how to verify* you didn't — had to manually grep `bugs.md` and
  `phases.md`.
- The "suggested skills" requirement is the skill's most opinionated
  ask and arguably least valuable here — `CLAUDE.md` already
  prescribes the workflow, and a fresh agent has its own skill list.

### Anti-duplication check

Worked **because** this project's durable docs are unusually
thorough. B-14 has 60 lines of bug doc; the handoff was ~50 lines
mostly pointers. Phase 20c is fully scoped in `phases.md`; the
handoff added *only* the "now unblocked because pt shipped" framing
and surfaced three pending scope decisions — actual new signal.

Risk on the other side: the open-ended round is dangerously close to
"just read `phases.md`." Useful in that it sequences options by
priority, but a reader could reasonably ask "why didn't you just
say that?"

### Subagent caveat — where the fabrication showed

- No real reproduction-attempt evidence for B-14. A real session
  would carry "I tried X, observed Y, ruled out Z."
- No "things the user changed their mind about."
- No live REPL state — a Fulcro/Clojure session leaves a hot nREPL
  with named defs and inspector state worth flagging.
- No tool-call history — fabricated test-runner results were
  honest about being fabricated.

These gaps would close in a real session. The skill is fundamentally
designed for that context, and our experimental pattern handicapped
it.

### Verdict on `handoff`

**Borderline-redundant in this project, but earns its place with one
adjustment.** Against `phases.md` + `bugs.md` +
`learned_while_making_this.md` + `CLAUDE.md` + `SCHEMA.md` +
`js_source_reference.md`, a handoff doc has to justify itself as
more than a re-index. It does, but barely. Estimated value-add: ~5
minutes of context-reconstruction per session-boundary, not 30.

**Worth keeping** for cross-day or cross-week resumes. **Overkill**
within a single coding day.

**Concrete improvement that would tip it from "nice to have" to
"obviously worth running":** explicitly capture **REPL state +
uncommitted-edit summary + last-test-run result** — the three things
that exist *only* in conversation context and cannot be reconstructed
from the repo.

## Recommendations for this project

| Skill | When | Why |
|---|---|---|
| `/diagnose` | Picking up a logged but undiagnosed B-N. Async/timing/perf bugs. **Skip for pure CSS/layout bugs** — bug-investigation prose in `bugs.md` is already as good as what the skill would add. | Phase 1 feedback-loop ladder and Phase 3 multi-hypothesis discipline are the load-bearing parts. The rest is checklist scaffolding. |
| `/handoff` | End of a multi-session phase, or before a break of more than ~1 day. **Skip** within a single coding session. | Pointer-based handoff saves real time when resuming cold. Less useful when context is still fresh. |
| Both | Treat as slash-command invocations in the main agent (Matt's recommended pattern), not subagent-isolated. Our subagent experiment was a useful evaluation pattern but is not how to use these day-to-day. | `handoff` specifically NEEDS to operate on the live conversation to be worth running. |

## Things we did NOT test (deferred)

- `/zoom-out` — six-line skill, low risk, can be tried opportunistically.
- `/grill-me` — would need a real planning decision to test against.
- `/prototype` — would need a real design question; could fit when a
  Phase 5K-style UI variant decision comes up.
- The CONTEXT.md+ADR-dependent skills (`grill-with-docs`,
  `improve-codebase-architecture`) — would need a doc-architecture
  decision first. See `~/.claude/matt-pocock-skills-overview.md` and
  the project memory for the full picture.

## Files referenced by this test

Read-only during the experiment:

- `~/.agents/skills/diagnose/SKILL.md`
- `~/.agents/skills/handoff/SKILL.md`
- `docs/bugs.md` (B-14 specifically)
- `docs/phases.md` (Phase 20c, Phase 21 state)
- `src/learn/client/ui/modals.cljc` (modal-shell)
- `src/learn/client/ui/components.cljc` (Root, app-container)
- `CLAUDE.md`

Generated artifacts:

- `%TEMP%\handoff-fix-b14.md`
- `%TEMP%\handoff-phase-20c.md`
- `%TEMP%\handoff-open.md`

No project source files were modified during this test.
