# Phase 7.22 — B-5 fix: empty initial list for deployed app

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Browser-first-visit bug that only surfaced after the 7.21 deploy: `learn.server/SERVER-DB` is `defonce`-initialized to `learn.server/initial-state` (the JVM-test seed — two demo todos), and CLJS `init` never overrode it. Result: every new visitor to the deployed app saw a pre-populated list.

Added `learn.server/empty-state` — same shape as `initial-state` but with `:list/todos` `[]` and `:todo/id {}`. CLJS `init` now `reset!`s SERVER-DB to that BEFORE `install-persistence!`, so:

- First visit, no localStorage, no URL → empty list (correct).
- Returning visit with localStorage → hydration overwrites the empty baseline (unchanged).
- URL with `?list=` → URL load logic overrides the baseline (unchanged).
- JVM tests → use `server/seed!`, which still resets to `initial-state`. Test suite is unchanged.

87 specs / 599 assertions, all green. CLJS: 327 files, 0 warnings. Browser-manual verification: visit `https://avidrucker.github.io/fulcro-solo-learn/` with localStorage cleared.

Closes **B-5**.
