# Diagrams

High-level visuals of the technologies powering AutoFocus, plus how
they fit together in this project. Each file is a single Mermaid
diagram + a short legend. View them on GitHub (renders natively) or
any Markdown editor with Mermaid support.

## Files

| Diagram | What it shows |
|---|---|
| [`autofocus-overview.md`](./autofocus-overview.md) | How all the tech layers compose in *this* project: user → DOM → Fulcro → Pathom → SERVER-DB → localStorage, plus the statechart and URL-encoder side paths. |
| [`fulcro.md`](./fulcro.md) | Plain Fulcro — `defsc` components, normalized state atom, mutations with optimistic `action` + remote, the render loop (state → query → denormalize → render). |
| [`fulcro-rad.md`](./fulcro-rad.md) | What RAD adds on top of Fulcro — attribute-driven schema, auto-derived forms / reports, built-in CRUD, DB drivers. Reference only; *we don't use RAD in AutoFocus*. |
| [`pathom2.md`](./pathom2.md) | Pathom 2 — EQL parser, resolver index (input → output), defmutations, the resolution loop that turns a query AST into a data tree. |
| [`statecharts.md`](./statecharts.md) | SCXML-inspired statecharts — a `stateDiagram-v2` of *our* review chart (`learn.review.chart`), since the concrete example is more illuminating than a framework dataflow. |

## How to read these

- **Solid arrows** carry data in the normal flow direction (call, update, response).
- **Dashed arrows** carry "in reverse" or "out of band" — re-renders triggered by state change, hydration on init, etc.
- **Cylinders** `[(…)]` are persistent stores (atoms, localStorage, DBs).
- **Subgraphs** group related pieces and label the layer.
- Labels on arrows describe *what* flows, not *when*.

## When to update

- A new technology lands in the project → add a diagram, link from this index, and from [`../README.md`](../README.md)'s "At a glance" table.
- An existing layer changes shape (e.g. we adopt RAD, swap the in-process Pathom for a real HTTP remote, replace localStorage with IndexedDB) → re-render the affected diagram.
- The diagrams are documentation, not specification — when in doubt, defer to the code or [`../SCHEMA.md`](../SCHEMA.md).
