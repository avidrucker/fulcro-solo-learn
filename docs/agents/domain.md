# Domain docs — fulcro-solo-learn

This project does NOT use the standard CONTEXT.md + docs/adr/ layout.
Durable domain knowledge is split across the existing docs as follows:

| Topic | File |
|---|---|
| Domain glossary (entities, statuses, invariants, operations) | `docs/SCHEMA.md` |
| Architectural decisions, past mistakes, divergences from JS source | `docs/learned_while_making_this.md` |
| Phase-by-phase implementation plan (rolling tracker + scope decisions) | `docs/phases.md` |
| Function-by-function divergence from the original JS port | `docs/js_source_reference.md` |
| Open + resolved bugs (B-N numbered) | `docs/bugs.md` |

## Consumer rules

When a skill (e.g. `/diagnose`, `/improve-codebase-architecture`)
instructs you to "read the domain glossary," start with
`docs/SCHEMA.md`. For architectural background or past trade-offs,
start with `docs/learned_while_making_this.md` and consult the
relevant phase in `docs/phases.md` for scoping decisions.

Do NOT create `CONTEXT.md` or `docs/adr/` in this project. The above
files are the source of truth.
