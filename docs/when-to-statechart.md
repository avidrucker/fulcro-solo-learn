# When to (and when NOT to) use a statechart

Statecharts are a powerful pattern, but they aren't free — they add a
new vocabulary (states, transitions, events, guards, actions),
indirection (events flow through a queue, actions read/write a data
model), and a separate testing surface. Reaching for them when a
keyword flag would do is over-engineering.

This document captures **decision criteria** specific to this
project. It's a complement to the local `statechart` skill, which
documents *how* to use charts well but doesn't cover *whether* you
should reach for one. Phase 8 was originally going to be a "refactor
the conflict modal into a chart" exercise — the analysis below is
why we decided **not** to.

---

## Criteria — when a chart pays off

Reach for a statechart when **at least two** of these are true:

1. **The flow has ≥3 distinct states** with non-trivial transitions
   between them. Two-state flags ("modal open / closed", "loading /
   loaded") are a flag, not a state machine.
2. **Guards or actions live on transitions** and you want them
   declarative. Hand-rolling a transition guard inside a `case` or
   `cond` works but rots into spaghetti as the flow grows.
3. **Multiple events can occur in the same state**, and your
   alternative is a giant `cond` that dispatches on event-name +
   current-state pairs.
4. **The flow needs history, parallelism, or hierarchy**.
   - *History*: re-entering a parent state should restore which
     substate was last active.
   - *Parallel*: two regions run concurrently and need to coordinate.
   - *Hierarchy*: shared transitions across substates that bubble up
     to a parent (internal transitions).
5. **You want one place to read "what state am I in?"** from
   multiple UI components — the chart's configuration becomes the
   single subscribable source.

Examples that meet ≥2 criteria → **chart it**:
- The AutoFocus review flow (our `learn.review.chart`):
  - States: `inactive` / `active` with `reviewing` substate.
  - Guard: `prioritizable?` on the `:event.review/start` transition.
  - Action: `mark cursor :ready` + advance + sync to server on
    `:event.review/yes`.
  - Eventless transition: cursor walks off the end (`= -1`) → pops
    back to `inactive`.
  - That's 3+ events, a guard, declarative actions, and an
    eventless transition. Charts pay off here.

---

## Anti-criteria — when a chart shoehorns

Don't reach for a chart when **all** of these are true:

1. **Two or fewer states**, and the second state is "the modal is
   open" or "we're loading."
2. **One event closes/exits** the second state.
3. **No history or parallelism** — leaving and re-entering is just
   re-entering.
4. **A plain keyword flag** in your app state already does the job.
5. **The flow is unlikely to grow** beyond its current shape.

Examples that meet all five → **don't chart it**:

### Conflict-resolution modal (in this project)

- 2 states (`inactive` / `showing`), 2 events (`keep-link` /
  `keep-local`), 1 effectively-implicit guard.
- The state IS `:ui/open-modal = :conflict` — a flag with a
  payload (`:ui/conflict-url-items`).
- Replacement chart would be ~30 lines of chart + ~20 lines of
  wiring + ~20 lines of UI reading chart state, vs the current ~50
  lines of `decide-initial-list` + `init` case + mutations + UI.
- No measurable readability, performance, or maintainability win.
- Flow is unlikely to grow — the JS port has the same shape and
  hasn't changed.

→ **We considered this for Phase 8 and chose not to refactor.**

### Modal mutex (`:ui/open-modal`)

- Five values (`:none`, `:about`, `:help`, `:save`,
  `:delete-confirm`, `:conflict`).
- "Transitions" are just `assoc-in :ui/open-modal <new-keyword>`.
- No guards, no actions on transition, no history.
- A keyword is literally the right data type for a mutex value.

→ **Already a flag. Don't chart it.**

---

## When you're on the fence

Ask yourself:

- *Can I describe the flow as a sticky-note diagram in 30 seconds?*
  If yes and there are <3 states, it's a flag.
- *Would a junior engineer reading the code understand the state
  machine immediately?* If a chart helps clarity, count it. If the
  chart adds indirection without insight, skip it.
- *Will the flow likely grow?* Charts pay forward — adding a fourth
  state to an existing chart is cheap; adding a fourth case to a
  flat `case` is cheap too, until it isn't.
- *Is there a real guard or transition action?* If the guard is
  just "the user clicked X" — that's not a guard, that's an event.

---

## Checklist before reaching for a chart

1. ☐ Three or more states (not "open / closed" with payload).
2. ☐ At least one transition has a guard or an action worth
   describing declaratively.
3. ☐ I'd be writing a per-event-per-state `case`/`cond` if I
   didn't use a chart.
4. ☐ Or: the flow has history, parallelism, or hierarchy
   semantics.
5. ☐ The state will be read from multiple places in the UI.

If you check 2+, a chart is justified. If you check 0–1, use a
flag + mutations.

---

## Related docs

- [`diagrams/statecharts.md`](./diagrams/statecharts.md) —
  visual diagram of the review chart (the existing chart in this
  project).
- [`SCHEMA.md`](./SCHEMA.md) §13 — review-session
  invariants the chart enforces.
- [`bugs.md`](./bugs.md) B-3 — note about why the menu-disabled
  predicate is computed in Root rather than chart-driven (we
  don't have a `:menu-disabled` chart; a predicate is enough).
- Local skill: `~/.claude/skills/statechart/` —
  `resources/patterns.md` for usage patterns and intra-chart
  anti-patterns (the skill doesn't currently document
  "when not to chart at all" — this doc fills that gap).

---

## A note for the skill author

The local `statechart` skill is excellent on *how* to use charts
well but light on *whether* to reach for one. The five anti-
patterns in `resources/patterns.md` are all *intra-chart* anti-
patterns (using aliases for state, business logic in mutations,
duplicated transitions, missing error handling, log-as-return).
None warn against the broader "this is a 2-state flag, don't
chart it" mistake.

Adding a "When to use a statechart" section near the top of
`resources/patterns.md` (or as its own `resources/when-to-use.md`)
with criteria like the ones in this doc would help future projects
avoid shoe-horning charts onto trivial flag-with-payload state.
