# Phase 12.7 — `learn.client.cljc` namespace refactor

**Status:** ✅ Complete
**Parent:** [Phase 12 — i18n + visual polish + facade refactor](12-i18n-and-refactor.md)

`learn.client.cljc` namespace refactor. The original ~1450-line file split into seven small focused namespaces behind a thin `learn.client` facade preserving every public wire symbol:

- `learn.client.session` — cross-namespace constants
- `learn.client.state` — pure state-map helpers
- `learn.client.mutations` — Fulcro defmutations (each defmutation uses an explicit fully-qualified target symbol so the multimethod registers under `'learn.client/<name>`; preserves server `::pc/sym` dispatch unchanged)
- `learn.client.ui.theme` — Tachyons class strings + theme helpers
- `learn.client.ui.modals` — modal-shell + body fns + header icon button + Mutation-record aliases via `m/declare-mutation` (avoids a cycle through `learn.client`)
- `learn.client.ui.components` — TodoItem / TodoList / Root
- `learn.client.lifecycle` — SPA atom, chart bootstrap, body-class theme sync, load-todos!

`learn.client` itself shrank to ~280 lines: requires + re-exports preserving `learn.client/<state-helper*>`, `learn.client/<mutation>`, `learn.client/Root`, `learn.client/TodoItem`, plus the `init` fn (still here because `shadow-cljs.edn`'s `:init-fn` references it by qualified symbol) and `snapshot`.
