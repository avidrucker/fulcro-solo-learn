# Phase 6.3 — Split `init` via reader conditionals

**Status:** ✅ Complete
**Parent:** [Phase 6 — shadow-cljs + browser app (no real backend)](06-shadow-cljs.md)

Split `learn.client/init` via reader conditionals. The JVM branch is byte-for-byte the existing behaviour (`h/build-test-app` + headless `lr/sync-remote`, used by the spec suite). The CLJS branch uses `app/fulcro-app` + a tiny new CLJC `learn.util.remote/sync-remote` shim (the headless library's `lr/sync-remote` is JVM-only). `init` is `^:export`ed in CLJS so shadow-cljs can call it as the module's `:init-fn`. `learn.main.cljs` (the 6.2 smoke test) was removed — shadow-cljs now points directly at `learn.client/init`. One forward reference (`review-session-id` used in `TodoList`'s render before it was `def`'d below) had to move up: CLJ tolerates the forward ref at runtime, CLJS rejects it at compile time.
