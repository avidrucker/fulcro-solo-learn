# Phase 6.2 — `.clj` → `.cljc` for server/resolvers/parser

**Status:** ✅ Complete
**Parent:** [Phase 6 — shadow-cljs + browser app (no real backend)](06-shadow-cljs.md)

Convert `server.clj` / `resolvers.clj` / `parser.clj` to `.cljc`. Only one JVM-only construct surfaced: `(catch Throwable e ...)` in `parser`'s error-handling plugin — fixed with a reader conditional `(catch #?(:clj Throwable :cljs :default) e ...)`. The master test runner regex was tightened to `\.cljc?$` so `.cljs` files (browser-only) are excluded from JVM scans. `learn.main` briefly requires the three namespaces as a smoke test for CLJS compilation; Phase 6.3 will replace that with the real entrypoint.
