# Phase 7.2 — Wire into init

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`learn.client/init`'s CLJS branch calls `install-persistence!` between the Inspect setup and `start-chart!`, so the order is:

1. build app
2. register Inspect
3. **hydrate `SERVER-DB` + attach persistence watch**
4. start review chart
5. mount Root
6. `df/load!` (now reads the hydrated server)

JVM init is unchanged in behavior because `install-persistence!` is a no-op there; the call is omitted from the `:clj` branch to keep the test-driven path byte-for-byte identical.

`scripts/snapshot.mjs` grew `--type <text>` and `--reload` flags (executed in argv order) so a single command captures the type-then-add-then-reload-then-snap demo. Each `chromium.launch()` is a fresh browser instance, so localStorage is empty at the start of each snapshot run — runs are isolated automatically.
