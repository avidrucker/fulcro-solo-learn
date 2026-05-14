# When to (and when NOT to) use production Pathom 2 patterns

Companion to [`when-to-statechart.md`](./when-to-statechart.md) and
[`when-to-use-RAD-forms-and-reports.md`](./when-to-use-RAD-forms-and-reports.md).

Phase 11 was originally going to teach "production Pathom patterns":
per-request env, batch resolvers, mutation return values for
optimistic UI. On honest analysis, our **in-process / front-end-only
/ single-user / atom-as-DB** setup gives every pattern zero
load-bearing payoff. None of these patterns are *wrong* — they're
genuinely useful in real Fulcro/Pathom deployments — but our project
shape doesn't exercise them. This doc captures what each pattern
does and when you should reach for it, so the learning is
preserved even though we didn't build the demos.

The recurring lesson, by now familiar from Phases 8 and 10:
**powerful patterns have a scale below which they add ceremony without
payoff.**

---

## Pattern 1 — Per-request env

### What it is

Pathom's parser is a function `(parser env eql)`. The first argument
is the *environment* — a map carrying everything resolvers and
mutations need access to that isn't part of the EQL query itself.

In a "hello-world" parser, `env` is `{}` and resolvers get nothing.
In production, `env` typically carries:

- **DB connection** (Datomic conn, JDBC pool, Asami connection,
  atom reference)
- **Auth context** — session, user id, role/scope claims
- **Request metadata** — trace id, request id, locale,
  feature flags
- **Per-call services** — clock fn, id-generator, sender refs
- **The original Ring request** itself (so resolvers can read
  cookies, headers, etc.)

A **per-request env** means: every HTTP request constructs a fresh
env map (typically in Ring middleware), and that env is the first
arg to `parser`. Each resolver call sees the same env.

### Criteria — when it pays off

Reach for per-request env when **at least one** of these is true:

1. **You have a real DB connection** that resolvers need access to.
2. **You have auth** — resolvers must know "who is asking" to
   filter results or refuse access.
3. **You're in a multi-user system** where one request's context
   must not leak into another.
4. **You have request-scoped services** (clocks, id-generators,
   tracing) that are easier to inject than to top-level-bind.

### Anti-criteria — when it shoehorns

Don't bother with per-request env when **all** of these are true:

1. **No DB connection** — your "server" is a top-level atom that
   every resolver can `(deref server-db)` directly.
2. **No auth** — there's exactly one user (you), and there's no
   need to restrict what they see.
3. **No multi-tenancy** — one process, one user, one in-flight
   request at a time.
4. **No request-scoped services** — your resolvers don't need
   anything beyond the EQL query itself.

### In this project

Our `learn.parser` parser passes `{}` as env. Every resolver reads
from a top-level atom (`learn.server/SERVER-DB`). The "request" is
a function call in the browser's JS event loop — there's no HTTP,
no concurrency, no users.

We could refactor to pass `{:server-db SERVER-DB}` in env and have
resolvers read from `(:server-db env)` instead of the global. That'd
be a stylistic preference, not a fix to a bug. **Net change: zero
behaviour, +ceremony.**

→ **Skip.** Useful pattern in real deployments; nothing to teach in
our setup.

---

## Pattern 2 — Batch resolvers (N+1 prevention)

### What it is

Pathom 2 dispatches resolvers per attribute per entity. The naive
case: if you have 100 entities and a resolver returns `:person/name`
given `:person/id`, Pathom calls that resolver 100 times — one per
person. If the resolver does a per-call DB query, you've just made
100 round trips.

A **batch resolver** advertises that it can answer for many entities
at once. Pathom collects the inputs and makes a single call. You
write the resolver to take a vector of inputs and return a vector
of outputs in the same order.

```clojure
;; Non-batch: 100 calls
(pc/defresolver person-name [env {:person/keys [id]}]
  {::pc/input  #{:person/id}
   ::pc/output [:person/name]}
  {:person/name (db/lookup-name id)})

;; Batch: 1 call
(pc/defresolver person-name [env inputs]
  {::pc/input   #{:person/id}
   ::pc/output  [:person/name]
   ::pc/batch?  true}
  (let [ids (map :person/id inputs)
        all-names (db/lookup-names-bulk ids)]
    (mapv (fn [id] {:person/name (get all-names id)}) ids)))
```

### Criteria — when it pays off

Reach for batch resolvers when **at least one** of these is true:

1. **Resolvers do real I/O** (DB query, HTTP call, file read).
2. **Lists are non-trivial** — 10+ entities resolved at a time.
3. **The data layer supports bulk fetch** — your DB can answer
   "give me all of these in one call" cheaper than N round trips.
4. **Profiling shows N+1 is a real bottleneck.**

### Anti-criteria — when it shoehorns

1. **No I/O** — resolvers are pure functions over in-memory data.
2. **Lists are tiny** — 1-2 items at a time.
3. **Data is already pre-fetched** — a single top-level resolver
   returns the entire list shape, and per-id resolvers never
   fire.

### In this project

Our `learn.resolvers/all-todos-resolver` returns the entire
denormalized list in one call:

```clojure
(pc/defresolver all-todos-resolver [_ _]
  {::pc/output [{:all-todos [:todo/id :todo/text :todo/status :todo/was]}]}
  {:all-todos (server/all-todos @server/SERVER-DB)})
```

The per-id `todo-resolver` exists but is rarely called because
`:all-todos` denormalizes everything up front. Even if it were
called, the "DB" is `(get-in @server/SERVER-DB [:todo/id <uuid>])`
— a constant-time map lookup. No I/O, no round trips, no
opportunity for N+1.

→ **Skip.** Useful pattern with a real backend; structurally a
no-op for our atom-as-DB.

---

## Pattern 3 — Mutation return values for optimistic UI

### What it is

`defmutation` (both Fulcro and Pathom) can return data from the
server side of a mutation. The client merges that data into its
normalized state.

Most common uses:

1. **Tempid remap.** Client allocates a tempid, server allocates
   the real id, returns `{:tempids {tempid real-id}}`. Fulcro
   rewrites every reference automatically.
2. **Server-generated fields.** Server sets `:item/created-at` or
   `:item/computed-score`; returning the new entity lets the
   client see those values.
3. **Cascading writes.** Server creates related entities (e.g.
   creating a comment also creates an event-log entry); returns
   the new entities for the client to merge.
4. **Optimistic UI correction.** Client guesses a value
   optimistically; server returns the real one to correct any
   drift.

### Criteria — when it pays off

Reach for mutation return values when **at least one** of these is
true:

1. **Server assigns ids** that the client doesn't know up front.
2. **Server computes fields** (timestamps, derived values,
   trigger-applied transformations) that the UI needs to display.
3. **Server creates related entities** as a side effect of the
   mutation.
4. **Optimistic UI may diverge** from server truth and needs
   reconciliation.

### Anti-criteria — when it shoehorns

1. **Client allocates ids** (UUIDs) so no remap is needed.
2. **No server-generated fields** — what the client sent IS
   what's stored.
3. **No cascading writes.**
4. **Optimistic UI is exact** — no drift possible.

### In this project

Our client generates UUIDs (`random-uuid` in `learn.model.list/
add-todo`). The server stores those UUIDs verbatim. No remap
needed.

Our server mutations return `{:list/id 1}` — the list ident —
which is constant. We never modify it on save. The return value is
load-bearing only in that Fulcro requires *something*; we could
return `nil` and the app would work identically.

Server-generated fields: none. The AutoFocus add-rule (`:status/
ready` vs `:status/new`) is computed on the **client** in
`learn.model.list/add-todo`, then sent to the server. The server
just records.

Cascading writes: none. One mutation, one write.

→ **Skip.** Useful pattern when client/server share id allocation
or when servers derive fields; our flow has neither.

---

## A useful middle ground (already in place)

Our parser is built with **plugins**:

- `error-handling-plugin` — catches `Throwable` from any inner
  plugin or resolver, returns a structured `:server/error`
  response. *Real production pattern; we ship it.*
- `logging-plugin` — opt-in EQL trace via `*debug?*` dynamic var.
  *Real production pattern; we ship it.*
- `connect-plugin` — registers resolvers + mutations from
  `learn.resolvers/all-resolvers`. *Standard Pathom 2 wiring.*
- `error-handler-plugin` — built-in resolver-level error
  handling.
- `elide-not-found` — strips `:not-found` markers from response.

This is "Pathom production patterns" in the parts where we
genuinely benefit. Per-request env / batch resolvers / mutation
return values are the remaining three — and our app shape doesn't
exercise them.

---

## Checklist before reaching for these patterns

| Pattern | Check if YES → consider it |
|---|---|
| Per-request env | ☐ DB connection? ☐ Auth context? ☐ Multi-user? ☐ Request-scoped services? |
| Batch resolvers | ☐ Real I/O in resolvers? ☐ Lists ≥10 items? ☐ DB supports bulk fetch? ☐ N+1 measured? |
| Mutation return values | ☐ Server allocates ids? ☐ Server-generated fields? ☐ Cascading writes? ☐ Optimistic UI drift? |

If you check 0–1 in a row, that pattern is over-tooling for the
current project shape. Revisit when the project grows into it.

---

## Related docs

- [`when-to-statechart.md`](./when-to-statechart.md) — same shape
  for statecharts.
- [`when-to-use-RAD-forms-and-reports.md`](./when-to-use-RAD-forms-and-reports.md)
  — same shape for RAD form/report components.
- [`benefits-of-RAD-in-this-project.md`](./benefits-of-RAD-in-this-project.md)
  — Phase 9 write-up of what RAD attributes added.
- [`diagrams/pathom2.md`](./diagrams/pathom2.md) — high-level
  diagram of how Pathom 2 works.
- `learn.parser` — our parser construction (where the env, plugins,
  and resolver registry are wired).
- `learn.resolvers` — the resolvers + Pathom mutations we register.
