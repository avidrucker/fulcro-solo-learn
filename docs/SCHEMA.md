# AutoFocus Schema Reference

This is the canonical reference for the AutoFocus domain model as implemented
in this Fulcro project. It defines vocabulary, entity shapes, status semantics,
domain invariants, operation contracts, and error types — and explains where
each piece lives in the codebase.

This document doubles as a context anchor when bringing collaborators (or AI
assistants) up to speed on the project.

---

## 1. Tooling decisions

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Data schemas | **Malli** | Schemas-as-data; composable; what Fulcro RAD uses |
| Function contracts | **Guardrails (Malli flavor)** via `>defn` | Inline, dev-only, near-zero production cost |
| Validation site | Inline at function boundaries | Catches violations where they're cheapest to debug |
| Client/server share | `.cljc` for schemas | Same definitions enforced on both sides |

`clojure.spec` is not used in this project. It remains in alpha after many
years, and the community has converged on Malli. Fulcro RAD uses Malli
natively for its attribute system, so adopting Malli now eliminates a future
migration pass.

---

## 2. Naming conventions

### Entity namespace: `:todo`

Despite the AutoFocus spec recommending `:item`, this project uses `:todo`
throughout for continuity with the existing codebase. The two are
interchangeable in meaning. A rename pass is cheap (mechanical
search-and-replace) and is deferred until the domain stabilizes.

### Status namespace: `:status`

Status values use the `:status/` keyword namespace rather than
`:todo.status/`. Short namespaces read better at call sites and the enum is
unlikely to collide with another status type. If it ever does, disambiguation
is a one-pass rename.

### ID type: UUID

Todos use UUIDs (`#uuid "..."`) rather than the AutoFocus spec's integer IDs.
UUIDs play well with Fulcro's normalization (idents are `[:todo/id <uuid>]`)
and avoid the integer-allocation question. Order is captured by `:list/todos`
(a vector of idents), not by ID magnitude.

### Keyword namespacing for derived/transient state

- `:ui/...` — UI-local state (input text, focus state, modal flags)
- `:review/...` — review session state (active flag, cursor)
- `:error/...` — structured error data (type, message)
- `:list/...` — list-entity attributes
- `:todo/...` — todo-entity attributes

---

## 3. Status enum

Four values, all in the `:status/` namespace:

```clojure
:status/new        ; added but not yet reviewed
:status/ready      ; actionable
:status/done       ; completed
:status/cancelled  ; explicitly cancelled; preserves prior status in :todo/was
```

### Status transitions

```
:status/new  ─── prioritize "Yes" ──▶ :status/ready
:status/new  ─── cancel        ────▶ :status/cancelled  (was = :new)
:status/new  ─── auto-mark     ────▶ :status/ready

:status/ready ── complete benchmark ▶ :status/done
:status/ready ── cancel             ▶ :status/cancelled  (was = :ready)

:status/done       ── clone ──▶  new todo (text only)
:status/cancelled  ── clone ──▶  new todo (text only)
```

There is no `:done → :new`, no `:cancelled → :ready` "uncancel" transition.
Cloning is the AutoFocus way of bringing a cancelled or done item back.

---

## 4. Entity schemas

### Todo

```clojure
(def Todo
  [:map
   [:todo/id     :uuid]
   [:todo/text   [:and :string [:fn (complement clojure.string/blank?)]]]
   [:todo/status Status]
   [:todo/was    {:optional true} Status]])
```

`:todo/was` is required when (and only when) `:todo/status = :status/cancelled`.
A stricter schema variant could enforce this via `[:multi]`; for now we rely on
function-level invariants enforced in `set-status*`.

### List

A list is a normalized entity holding a vector of todo idents:

```clojure
(def List
  [:map
   [:list/id          :int]
   [:list/todos       [:vector [:tuple [:= :todo/id] :uuid]]]
   [:ui/new-todo-text :string]])
```

The vector's order is meaningful — it defines the benchmark item (last ready)
and the auto-mark target (first new). Idents within `:list/todos` always
reference entries in the normalized `:todo/id` table.

### Review state

```clojure
(def ReviewState
  [:map
   [:review/active? :boolean]
   [:review/cursor  [:or
                     [:= -1]              ; sentinel: review not in progress
                     [:and :int [:>= 0]]]]])
```

The cursor is an index into the *order* of `:list/todos`, not a todo id.
When inactive, it's `-1` by convention.

---

## 5. Domain invariants

### Todo invariants

1. `:todo/id` is a UUID (never reassigned).
2. `:todo/text` is non-empty after trim.
3. `:todo/status` is one of the four enum values.
4. `:todo/was` is present iff `:todo/status = :status/cancelled`.

### List invariants

1. Todo ids in `:list/todos` are unique within the vector.
2. List order is meaningful and preserved across operations.
3. Add appends to the end.
4. Complete and cancel do **not** remove items (only `delete-all` removes).
5. Clone appends a new todo with the source's text.
6. The benchmark item is always `(last (filter ready? items))`, or `nil`.
7. After any operation that leaves the list with new items but no ready
   items, the auto-mark rule fires (see §6).

---

## 6. The auto-mark rule

A list is **auto-markable** when:

```clojure
(and (has-new? items)
     (not (has-ready? items)))
```

When auto-markable, the **first** new item (in list order) is promoted to
`:status/ready`.

This rule fires automatically as part of these operations:

- `complete-benchmark` — after marking the benchmark done
- `cancel-todo` — after marking the target cancelled

It does **not** fire on these operations:

- `add-todo` — handled by add's own rule (see §7)
- `set-status` — explicit status changes don't auto-cascade

### Clarification: when does auto-mark actually apply?

Auto-mark applies **only when the list has no ready items remaining**, after
the triggering operation. Specifically:

- **Completing the benchmark when other ready items exist** → benchmark goes
  done; the new last-ready item becomes the next benchmark; **no auto-mark**.
- **Completing the benchmark when it was the only ready item** → benchmark
  goes done; list has no ready items; **if new items exist, the first new
  becomes ready**; otherwise, the list is now inactionable.
- **Cancelling a ready item when other ready items exist** → that item goes
  cancelled; the new last-ready becomes the benchmark; **no auto-mark**.
- **Cancelling the only ready item with new items present** → that item goes
  cancelled; the first new item is promoted to ready.
- **Cancelling a new item** → that item goes cancelled; `:has-ready?` is
  unchanged; **no auto-mark** (other readys exist, or none existed before).

The phrasing "auto-promote when the final ready item is completed/cancelled"
is correct only when *final* means *last remaining*, not *last in list order*.

---

## 7. Core operations and contracts

All operations are pure functions over the *denormalized* todo vector. They
produce result maps shaped:

```clojure
{:ok? true  :items updated-items}
;; or
{:ok? false :error/type :error/...}
```

This convention lets Fulcro mutations branch cleanly on success without
relying on exceptions.

### Function contracts (Guardrails `>defn`)

```clojure
(>defn next-id          [items]            [Items => :int])
(>defn benchmark-item   [items]            [Items => [:maybe Todo]])
(>defn add-todo         [items text]       [Items :string => Result])
(>defn complete-benchmark-item [items]     [Items => Result])
(>defn cancel-todo      [items id]         [Items :uuid => Result])
(>defn clone-todo       [items id]         [Items :uuid => Result])
(>defn empty-list       []                 [=> [:vector :any]])
(>defn add-all          [existing imports] [Items Items => Result])
```

Where `Items` is `[:vector Todo]` and `Result` is the success-or-error map.

### Behavior summary

| Op | Input | Output | Auto-mark?                     |
|----|-------|--------|--------------------------------|
| `next-id` | items | next available int (UUIDs in practice) | —                              |
| `benchmark-item` | items | last ready, or `nil` | —                              |
| `add-todo` | items, text | items with new todo at end | No (handled by add's own rule) |
| `complete-benchmark` | items | benchmark → done | Yes                            |
| `cancel-todo` | items, id | item → cancelled | Yes                            |
| `clone-todo` | items, id | items with cloned copy at end | same as `add-todo`             |
| `empty-list` | none | `[]` | —                              |
| `add-all` | existing, imports | concatenated, re-IDed | No                             |

### `add-todo` status rule

- If the list has at least one ready item → new todo is `:status/new`
- If the list has no ready items → new todo is `:status/ready`

This is *not* the same as auto-mark. Auto-mark promotes an existing new item;
`add-todo`'s rule sets the *initial* status of a fresh item.

---

## 8. Error types

Structured errors keyed by `:error/type`:

```clojure
:error/blank-item            ; text was empty or whitespace
:error/item-not-found        ; cancel/clone target id doesn't exist
:error/no-actionable-items   ; complete-benchmark with no ready items
:error/invalid-review-decision ; review decision wasn't :yes/:no/:quit
:error/no-prioritizable-items ; start-review with no new items
```

(Additional types for IO/URL state belong in `learn.model.io` and
`learn.model.url-state` when those layers exist.)

UI maps these to human strings. Pure logic returns the keyword.

---

## 9. Validation strategy

Three places where validation can fire. Each has a clear role.

### Layer 1: Pure domain functions (Guardrails `>defn`)

Every domain function declares its contract with `>defn`. Guardrails validates
inputs and outputs at function boundaries *during development*. In production,
contracts erase to zero overhead. This is where the bulk of validation lives.

### Layer 2: Mutations

Mutations are thin wrappers. They typically don't re-validate — they trust the
domain function to validate. The mutation's job is to thread state through the
function and translate result maps into Fulcro state changes.

### Layer 3: Resolvers (server-side)

Resolvers can validate their parameter inputs via `>defn`. For loads from
untrusted clients (eventually), this is the perimeter check.

### When Guardrails fails

Guardrails violations are thrown as exceptions during development. Because
parser plugins catch `Throwable` in this project, a Guardrails violation
becomes a structured `:server/error` in the response. That makes contract
violations debuggable from the client without crashing the whole request.

---

## 10. Codebase layout

The layout below reflects the *target* state. Phase 5I builds out the
`model/` namespaces; phases beyond extend to RAD attributes.

```
src/learn/
  client.cljc              ; UI components, Fulcro mutations (UI layer)
  parser.clj               ; Pathom parser + plugins
  resolvers.clj            ; Pathom resolvers & mutations (wire layer)
  server.clj               ; SERVER-DB atom + helper operations

  model/                   ; Pure domain — no Fulcro/Pathom dependencies
    schema.cljc            ; Malli schemas: Todo, List, ReviewState, errors
    item.cljc              ; Item predicates (new?, ready?, done?, etc.)
    list.cljc              ; List operations (add-todo, complete-benchmark, ...)
    review.cljc            ; Review session (cursor, decisions)

test/learn/
  client_test.clj          ; UI/mutation specs
  resolvers_test.clj       ; Pathom layer specs
  model/
    schema_test.cljc       ; Schema-conformance specs
    item_test.cljc         ; Item predicate specs
    list_test.cljc         ; List operation specs (the bulk of TDD coverage)
    review_test.cljc       ; Review flow specs

docs/
  SCHEMA.md                ; this file
```

### Dependency direction

```
ui (client.cljc)
  ↓
fulcro mutations
  ↓
pure domain (model/*)        ← schema.cljc, item.cljc, list.cljc, review.cljc
  ↓
schema (Malli)
```

The arrows go one way. Mutations call domain functions; domain functions know
nothing about Fulcro, Pathom, DOM, or persistence. Schema knows nothing about
anything else.

This is why the `model/` namespaces are `.cljc` — they're the part of the app
that's environment-agnostic. They could be lifted into a separate library if
the project ever forks a CLI or mobile UI.

---

## 11. Future migration to Fulcro RAD

When this project moves to RAD (Phase 10+), each schema entry becomes a RAD
**attribute** — a data structure that defines the type, cardinality, storage,
and UI rendering of a field.

```clojure
;; Today (Malli)
(def Todo
  [:map
   [:todo/id     :uuid]
   [:todo/text   :string]
   [:todo/status Status]])

;; Tomorrow (RAD attribute)
(defattr todo-id :todo/id :uuid
  {ao/identity?          true
   ao/schema             :production})

(defattr todo-text :todo/text :string
  {ao/required?          true
   ao/validator          (complement str/blank?)})

(defattr todo-status :todo/status :enum
  {ao/enumerated-values  #{:status/new :status/ready :status/done :status/cancelled}})
```

RAD attributes use Malli underneath for type definitions, so the schema
contents migrate; only the wrapping changes. This is why investing in Malli
now is "free" relative to the RAD future — same types, different packaging.

---

## 12. Quick reference — vocabulary

| Term | Definition |
|------|-----------|
| Todo / item | A unit in the AutoFocus list |
| List | An ordered collection of todos |
| Status | One of `:status/{new,ready,done,cancelled}` |
| Benchmark item | The last ready item; the "next actionable" |
| Actionable list | A list with at least one ready item |
| Auto-markable list | A list with new items but no ready items |
| Auto-mark | Promote first new → ready when list is auto-markable |
| Prioritize / review | Walk through new items asking yes/no/quit |
| Cursor | Position within an active review session |
| Clone | Create a new todo from a cancelled/done one's text |
| `:todo/was` | Status held before cancellation, for UI display |

---

## 13. Closed questions

These are settled items, and are generally not up for being changed.

- **Should `:status/done` items be cloneable?** Answer: Yes.
- **Should the benchmark be visually emphasized in lists?** Answer: Yes. Per UI rules, the benchmark item will be rendered bold. For REPL and CLI testing, the benchmark item could be (OPTIONALLY) indicated with a left facing arrow to the right of it to indicate it is the current benchmark item.
- **How many review cursors at once?** Answer: One per app instance at a time.

---

## 14. Open questions

These are deliberately *not* settled in this document. They get resolved as
the project evolves.

- **Server validation of mutations?** Resolvers don't currently re-validate
  the schema on incoming mutation params. Note: Eventually, list state validation may be beneficial to implement. For now, YAGNI.
