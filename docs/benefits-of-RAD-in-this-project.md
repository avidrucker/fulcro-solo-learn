# Fulcro RAD in this project — what we gained, what we didn't

Phase 9 added Fulcro RAD (1.6.23) to the project. This doc is the
honest assessment of what that bought us, where the value was real,
where it was learning-only, and where RAD's full machinery genuinely
doesn't fit.

The companion doc [`when-to-statechart.md`](./when-to-statechart.md)
is the same exercise for statecharts — read both together for the
recurring lesson: powerful tools have a scale below which they
add ceremony without payoff.

---

## What we built

- **`learn.rad.attributes`** — `defattr` declarations for `:todo/id`,
  `:todo/text`, `:todo/status`, `:todo/was`. Each carries:
  - The data type (`:uuid`, `:string`, `:keyword`)
  - Cardinality, required-flag, schema
  - For `:todo/text`: `:field/label` (used as the input placeholder)
    and `:field/maxlength`. The `:field/label` value is sourced
    from `learn.ui.strings/input-placeholder` so the placeholder
    text has exactly one canonical home.
  - For `:todo/status` / `:todo/was`: `ao/enumerated-values` and
    `ao/enumerated-labels` matching our schema.

- **`learn.rad.input/text-input`** — a small helper that takes a RAD
  attribute and renders our Tachyons-styled `<input>` + the
  `clip`-hidden `<label>` for headless-test access. Placeholder and
  maxlength come from the attribute, NOT from inline code. The
  controlled-value plumbing (Fulcro `m/set-string!` to a state
  key) and the visible class string are still parameters at the
  call site — they're concerns of the surrounding component, not
  the attribute.

- **Add Item input now uses `rad-input/text-input`** — the call site
  shrank from a 5-key `dom/input` map to a 7-key options map on the
  helper. Visual output is identical to the previous version; the
  *source of truth* for "what does this input look like" moved from
  the call site into the attribute.

That's it. We did NOT:

- Build a `defsc-form` (the full RAD form component with state
  machine, save mutation, etc.).
- Register a RAD render plugin.
- Wire RAD's routing.
- Add a storage adapter.

---

## What we gained (real value, even at our scale)

1. **One source of truth for input metadata.** Changing the
   placeholder, maxlength, or label requires editing one attribute.
   The previous version had `:placeholder` hard-coded on the
   `<input>` and `:input-placeholder` in `strings.cljc` — two
   places. Now there's one, with the strings constant referenced
   from the attribute.

2. **Self-documenting attribute set.** A reader of
   `learn.rad.attributes` sees the entire Todo schema in 40 lines:
   four attributes, their types, their required-ness, their valid
   enum values. That's the same information `learn.model.schema`
   carries but in a format that's UI-aware (it knows about labels
   and maxlengths, which Malli schemas don't natively).

3. **Architectural readiness.** If a future phase adds a second
   entity (say `:list/title` or `:user/email`), the `defattr`
   pattern is already in place; we add a new namespace, register
   with `all-attributes`, and the same input helper renders it.
   The opposite direction — going from "no attributes" to "RAD
   attributes" — is the migration we just did. The next entity
   doesn't have to do that migration.

4. **Vocabulary exposure.** The project now has a real example of
   `defattr`, attribute metadata, the `attributes-options`
   namespace, and the option keys that map onto form rendering.
   That's the conceptual leap RAD asks you to make; we've made it
   for one entity, which is enough to understand the pattern.

---

## What we did NOT gain (and were honest about up front)

1. **No auto-rendered forms.** `defsc-form` + a render plugin would
   let RAD draw the entire input from attribute metadata with zero
   call-site code. We didn't go there — our single input doesn't
   justify the render-plugin complexity (and the only stock RAD
   plugin, Semantic UI, would visually clash with Tachyons).

2. **No auto-derived save flow.** RAD's `form/save-form!`
   constructs a Pathom mutation from form deltas. We kept our
   existing `add-todo` mutation — saving still goes through
   hand-rolled code.

3. **No backend schema generation.** RAD can derive SQL DDL or
   Datomic schema from attributes. We don't have a real backend
   (SERVER-DB is an atom), so this benefit is moot here.

4. **No auto-derived reports.** A `defsc-report` would generate
   the list view from attributes. Our list view has too much
   custom behavior (status icons, dim cancelled, conditional
   cancel/clone buttons, benchmark bolding) for RAD's report
   defaults to handle without a render plugin. The cost-benefit
   was wrong.

---

## When RAD's full machinery WOULD pay off

The five conditions where RAD becomes worth the cost:

1. **Three or more entities** that share CRUD shape (think users,
   organizations, projects, tasks — admin-app territory).
2. **Forms outnumber custom UI.** If 70%+ of your screens are
   form-shaped (edit-this, search-that), RAD's defaults amortize
   well.
3. **You want one schema source** that serves UI, validation,
   and DB schema. Single-source-of-truth saves real work when
   the schema has 30+ attributes.
4. **A real DB backend** that needs schema migrations. RAD's
   schema generation is more useful than rolling your own.
5. **Teams of 3+ developers** where consistency of form/report
   UX matters more than tailored per-screen design.

We have **one** of these (single source of truth — marginally),
which is why we capped our usage at attribute definitions + a
hand-rolled input helper.

---

## Cost paid

- **Dependency**: `com.fulcrologic/fulcro-rad 1.6.23` plus its
  transitive deps (potemkin, encore, truss). Shadow-cljs build
  grew from 327 files to 334 files.
- **Build time**: Initial cold compile +50s when RAD jars were
  first pulled and indexed. Incremental dev builds are unchanged.
- **Test runtime**: +0s (RAD doesn't impose any test-time cost
  because our tests don't load it).
- **Code volume**: +43 lines (`learn.rad.attributes`) +
  60 lines (`learn.rad.input`) + small require additions in
  `learn.client`. Removed ~5 lines of hard-coded `<input>` props.
  Net +~100 lines of code for a single-input refactor.

---

## Verdict

For *this* project, RAD adds **modest** value:
- The attribute-as-source-of-truth pattern is real and worth seeing.
- A new contributor can find the Todo schema in one place
  (`learn.rad.attributes`) instead of inferring it from scattered
  `defsc` queries and inline DOM props.
- We're set up for the day a second entity arrives (if ever).

The cost is also modest: one dep, a small helper, a small attribute
namespace.

For a *real-world* project the value scales with entity count and
form-heaviness. The example AutoFocus is — at this scale — the wrong
shape for RAD to shine. We did Phase 9 anyway because it's a
learning project. If we were building AutoFocus for shipping, RAD
would be over-tooling. If we were building a 20-entity admin app,
RAD would be under-tooling not to use.

## Related reading

- [`changes.md`](./changes.md) — other intentional divergences
  from the og JS port (this isn't one of them — the og doesn't use
  RAD either; we're showing what RAD looks like for learning).
- [`when-to-statechart.md`](./when-to-statechart.md) — the
  companion "when to reach for this tool" doc for statecharts.
- [`SCHEMA.md`](./SCHEMA.md) — Malli schemas and the function-
  contract layer that RAD attributes coexist with.
- `learn.rad.attributes` — the attribute definitions.
- `learn.rad.input` — the rendering helper.
