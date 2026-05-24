# Phase 6 — shadow-cljs + browser app (no real backend)

**Status:** ✅ Complete

First time the project actually runs in a browser. The "server" is the same `learn.server` atom + Pathom 2 parser we already built — just compiled to JS and running alongside the client. The Fulcro book calls this pattern `(mock-remote non-conflicting-resolvers)`; our existing `lr/sync-remote parser/handler` is the same thing, so the loopback stays. No HTTP, no jetty, no real backend.

This is a re-scope of the original "Phase 7 (real backend)" plan. A true server-process backend isn't part of the AutoFocus learning arc — the front-end-only design is the target. If a real backend is ever wanted, it slots in as a much later, optional phase.

## Sub-phases

- ✅ [6.1 — shadow-cljs.edn + browser target](06-1-shadow-cljs-edn.md)
- ✅ [6.2 — `.clj` → `.cljc` for server/resolvers/parser](06-2-cljc-conversion.md)
- ✅ [6.3 — Split `init` via reader conditionals](06-3-split-init.md)
- ✅ [6.4 — Browser app loads and round-trips (3 bugfixes)](06-4-browser-roundtrip.md)
- ⬜ [6.5 — Strings + Tachyons port to match the original JS UI](06-5-strings-tachyons.md) (parent marker stale in source; all 6.5.x sub-steps ✅)
