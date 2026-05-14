# When to (and when NOT to) use RAD's forms and reports

Companion to [`when-to-statechart.md`](./when-to-statechart.md) and
[`benefits-of-RAD-in-this-project.md`](./benefits-of-RAD-in-this-project.md).

Phase 9 introduced RAD attribute definitions plus a lightweight
attribute-driven input (`learn.rad.input/text-input`). Phase 10 was
originally going to add `defsc-form` and `defsc-report` — RAD's full
auto-generated UI components. On honest analysis we chose **not to
build them** for this project, and instead recorded the decision
criteria here.

The TL;DR: attribute definitions (Phase 9) pay off at any scale.
`defsc-form` and `defsc-report` need a *minimum project size* to
earn their plumbing.

---

## What `defsc-form` and `defsc-report` actually are

- **`defsc-form`** — a Fulcro `defsc` that knows about an attribute
  set. Renders inputs automatically based on attribute types,
  manages a form-state machine (dirty / clean / saving / saved /
  failed), submits a save mutation with a *delta* of changed
  fields, and integrates with Fulcro's routing.
- **`defsc-report`** — a Fulcro `defsc` that renders a list of
  entities as a table or row layout. Columns derive from attribute
  metadata, sort/filter/pagination come stock, row actions
  (edit / delete) wire to mutations.

Both require a **render plugin** registered with the Fulcro app —
none of the stock plugins (Semantic UI, etc.) emits Tachyons-styled
markup, so a Tachyons-styled app needs a custom plugin.

---

## Criteria — when defsc-form pays off

Reach for `defsc-form` when **at least three** of these are true:

1. **The form has 4+ fields.** RAD's auto-layout earns its keep
   when you'd otherwise be writing one `<input>` block per field
   by hand.
2. **You want a stock form lifecycle** — dirty tracking, save
   indicators, validation messages per-field, cancel-with-confirm
   on dirty.
3. **Saving submits a delta** — only the changed fields go to the
   server. RAD's delta machinery is the right shape; rolling
   your own delta diff is real work.
4. **The entity has a routable identity** — you create/edit it
   from a URL like `/person/edit/123`. RAD forms expect this.
5. **You have a real DB** that RAD's storage adapter integrates
   with (SQL via Asami, Datomic, async-storage). Stock plugins do
   the schema → query → mutation wiring.
6. **You're styling with a stock RAD theme** (Semantic UI is the
   most-shipped) OR you have a render-plugin budget — ~150 lines
   minimum to support one input type with custom CSS.

Examples that meet ≥3 → **use defsc-form**:
- Admin app with Person / Organization / Project forms (3-10 fields each).
- E-commerce product editor with stock validations + dirty state.
- Multi-step wizard where save-on-every-step is the UX pattern.

---

## Anti-criteria — when defsc-form shoehorns

Don't reach for `defsc-form` when **all** of these are true:

1. **Single input** or trivial 1-2 field form.
2. **No routing** — the form lives inline in another view.
3. **Custom save logic** that doesn't fit a delta pattern (e.g.
   server generates the id, or there's domain-rule application
   on save).
4. **Custom visual styling** that doesn't match any stock render
   plugin.
5. **Existing save path already works** and is tested.

Example that meets all five → **don't use defsc-form**:

### Add Item input (in this project)

- One field (`:todo/text`), one save action.
- Inline in `TodoList`'s render. No routing.
- Custom save: our `add-todo` mutation generates a UUID
  server-side AND applies the AutoFocus add-rule
  (`:status/ready` if no ready exists, else `:status/new`).
  RAD's delta pattern doesn't fit — we'd have to override.
- Tachyons styling. No stock render plugin matches.
- Save flow is exercised by 39+ tests across `client-test` and
  `model.list-test`.

Plumbing cost for `defsc-form` here would be 200+ lines of render
plugin + state-machine wiring + tempid-handling-vs-our-UUID-flow
override. Net negative against Phase 9's `rad-input/text-input`
helper (~60 lines, identical behaviour).

→ **We considered this for Phase 10 and chose not to refactor.**

---

## Criteria — when defsc-report pays off

Reach for `defsc-report` when **at least three** of these are true:

1. **Tabular data** — rows are mostly homogeneous, columns are
   the attributes.
2. **Sort / filter / pagination** are real requirements.
3. **Multiple report views** of the same entity (e.g. "active",
   "archived", "search results") — RAD's report config makes
   variants cheap.
4. **Per-row actions** that fit the edit-row / delete-row pattern.
5. **You want stock theme styling** (Semantic UI tables, etc.) OR
   have a custom render plugin already.

---

## Anti-criteria — when defsc-report shoehorns

Don't reach for `defsc-report` when:

1. **The list isn't really a table.** Custom per-row visuals
   (icons, dim-when-cancelled, strikethrough, conditional buttons)
   dominate the rendering.
2. **No sort/filter/pagination needed.**
3. **Custom layout around the list** (form header, footer with
   counts, modal overlays as siblings) — `defsc-report` doesn't
   wrap with these.
4. **Existing list renderer is well-tested** and the behavior is
   custom-tailored.

Example that meets all four → **don't use defsc-report**:

### TodoList rendering (in this project)

Our list has:
| Feature | RAD report default? |
|---|---|
| SVG status icons per row | No — need cell renderer |
| `o-50` opacity for done/cancelled | No — need row-class fn |
| `strike` text for cancelled | No — need cell renderer |
| Hover-to-show cancel/clone buttons | No — custom row actions |
| Benchmark item in bold | No — need row-class fn |
| No column headers | RAD shows them by default — need to hide |
| List-count + next-actionable footer | No — need custom wrapper |
| Modal overlays as siblings | No — outside RAD's tree |

Almost every visual feature requires an override. The "report"
identity is gone before we're done; we'd write more code than the
current `TodoList + TodoItem` (~80 lines combined).

→ **We considered this for Phase 10 and chose not to refactor.**

---

## A useful middle ground

Phase 9's `learn.rad.input/text-input` is the *attribute-driven
input* without the form-state-machine ceremony. The lesson there
generalizes: you can use RAD attributes to drive your own
rendering without using `defsc-form` / `defsc-report`. Specifically:

- Read `:field/label`, `:field/maxlength`, `ao/required?` etc.
  from the attribute map in your custom `defsc` component.
- Use `ao/enumerated-values` / `ao/enumerated-labels` to drive
  a dropdown's options.
- Use the attribute as documentation — anyone reading the
  component can follow the call to `rad-attrs/text` and see the
  metadata.

This is "RAD-lite" — you get the attribute pattern without
buying into the form/report machinery. For projects with <5
entities and custom UI, this is often the right shape.

---

## Checklist before reaching for defsc-form

1. ☐ Four or more fields in the form.
2. ☐ Stock save-delta flow works for the entity (no custom domain
   rules to apply on save).
3. ☐ Form is routable (or you accept the headless form pattern).
4. ☐ Stock render plugin matches your styling, OR you have a
   render-plugin budget.
5. ☐ You want dirty-tracking / save-state / cancel-with-confirm
   for free.

If you check 3+, `defsc-form` is justified. Otherwise, an
attribute-driven custom input is the right shape.

## Checklist before reaching for defsc-report

1. ☐ The list is genuinely tabular.
2. ☐ Sort / filter / pagination are real requirements.
3. ☐ Standard row styling (no per-row icon / opacity / strike
   patterns that fight default cell rendering).
4. ☐ Edit / delete row actions are the only per-row interactions.
5. ☐ Stock render plugin matches, or render-plugin budget exists.

If you check 3+, `defsc-report` is justified. Otherwise, a
hand-rolled list renderer (possibly attribute-driven) is the
right shape.

---

## Related docs

- [`when-to-statechart.md`](./when-to-statechart.md) — same shape
  of decision doc for statecharts.
- [`benefits-of-RAD-in-this-project.md`](./benefits-of-RAD-in-this-project.md)
  — Phase 9 write-up of what RAD attributes added at our scale.
- `learn.rad.attributes` — the attribute definitions we DID build.
- `learn.rad.input` — the attribute-driven input we DID build (the
  "RAD-lite" pattern).
