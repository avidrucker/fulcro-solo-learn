# Phase 5 — Pathom 2

**Status:** ✅ Complete

Replaced the hand-rolled `cond`-based parser with Pathom 2 resolvers and mutations, composed via `pc/connect-plugin`. Broken into:

- **5A–5F**: Resolvers, mutations, the registry/parser, parameterized queries (`:status` filter on `:all-todos`).
- **5G**: Plugins — logging plugin (`*debug?*`-gated), error-handling plugin (`Throwable` catch), `p/post-process-parser-plugin p/elide-not-found`.

**Files:** `parser.clj`, `resolvers.clj`, `server.clj`
**Key concepts:** `pc/defresolver`, `pc/defmutation`, `::pc/sym` for wire-symbol decoupling, plugin order (outer wraps inner), `:ast :params` for reading parameterized query parameters.
