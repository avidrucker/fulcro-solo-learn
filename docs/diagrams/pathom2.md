# Pathom 2 — EQL parser with a resolver graph

Pathom is "GraphQL for Clojure, but the query language is EQL and
the schema is implicit." You don't declare a schema up-front; you
register **resolvers** (input → output mappings) and **mutations**
(named effects). At query time, Pathom finds a path through the
resolver graph from "what we have" to "what was asked for" and
executes it.

```mermaid
flowchart TB
    Client["Client EQL<br/>[{:list/id 1}<br/> {:list/todos [:todo/text :todo/status]}]"]

    subgraph Parser["Pathom parser"]
        AST["EQL → AST"]
        Plan["Planner<br/>(walk AST,<br/> consult index)"]
        Run["Runner<br/>(thread inputs<br/> through resolvers)"]
    end

    subgraph Registry["Registry (built once at startup)"]
        ResolverDefs["pc/defresolver entries<br/>{:input #{:list/id}<br/> :output [{:list/todos [...]}]<br/> :resolve (fn [env input] ...)}"]
        MutationDefs["pc/defmutation entries<br/>{::pc/sym 'app/add-todo<br/> :params [...]<br/> :body (fn [env params] ...)}"]
        Indexes[("Index<br/>output-attr → resolver(s)<br/>input-attr → resolver(s)")]
        ResolverDefs --> Indexes
        MutationDefs --> Indexes
    end

    Client --> AST
    AST --> Plan
    Plan -->|"need :list/todos<br/>given :list/id?"| Indexes
    Indexes -->|"resolver R1 satisfies"| Plan
    Plan --> Run
    Run -->|env, input| ResolverDefs

    MutationCall["Client mutation<br/>(app/add-todo {:todo/text \"x\"})"]
    MutationCall --> AST
    AST --> MutationDefs

    Run --> DataTree["Output tree<br/>{:list/id 1<br/> :list/todos [{:todo/text \"a\"<br/>               :todo/status :status/new} ...]}"]
    MutationDefs --> MutResult["Mutation return<br/>(map merged back<br/> into client state)"]
```

## What makes Pathom different

1. **No hand-written schema.** The registry IS the schema, derived from `defresolver` / `defmutation` declarations. Add a resolver → new attributes are queryable. Add a mutation → new effect is callable.
2. **The planner chains resolvers.** If you ask for `:user/full-name` and you have `:user/id`, and `:user/id → :user/first-name`, `:user/id → :user/last-name`, and `[:user/first-name :user/last-name] → :user/full-name` are all registered, the planner figures out to call all three in the right order. You never wrote `:user/full-name` as a query handler — it composed.
3. **EQL is the query language.** Same syntax used in Fulcro `defsc` `:query`s. The client query AST is consumed verbatim by the parser — no translation layer.
4. **The `env` carries everything else.** Database connections, session/auth context, the request itself. Resolvers receive `(fn [env input] …)` — `env` is whatever you wired at parser construction.

## In this project

- **No HTTP.** Pathom runs in-process. The "remote" function in Fulcro (`learn.util.remote/sync-remote`) calls the parser directly with the EQL AST. Response shape is identical to what an HTTP+Transit setup would return.
- **Two resolvers** (`src/learn/resolvers.cljc`): `all-todos-resolver` returns the list of todo idents; `todo-resolver` returns one todo's attributes given its id.
- **Seven defmutations** all share one body: write the client-computed item vector into `SERVER-DB`. The work was already done client-side (in the `*`-suffixed helpers); the server just records the result. See `learn.resolvers/record-list-items` for the one-line shared write.
- **One wire symbol per client mutation.** `(pc/defmutation … {::pc/sym 'learn.client/delete-all …})` ties the client's `(defmutation delete-all …)` to the right server handler. The client's `(remote [env] true)` triggers the lookup.

## Pathom 2 vs Pathom 3

This project uses **Pathom 2.4.0**. Pathom 3 is a newer version with a different planner architecture (smart-map-based, async-first) and incompatible APIs. Phase 8 mentions "production Pathom patterns" — that work would stay on Pathom 2 unless a port is in scope.
