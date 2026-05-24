# Phase 7.10 — Theme persists across reload (B-1 fix)

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`:ui/theme` lives in Fulcro app state, NOT in `SERVER-DB`, so the Phase 7 persistence didn't reach it — every reload reset theme to `:theme/light`. Diagnosed in `docs/bugs.md` B-1.

Added a parallel persistence path in `learn.util.storage` for a small whitelisted slice of `:list/id 1`. `ui-prefs-whitelist` is currently `#{:ui/theme}`; future keys (zoom, layout, etc.) just append to the set. Pure CLJC `extract-ui-prefs` / `apply-ui-prefs` (testable on JVM) + CLJS-only `save-ui-prefs!` / `load-ui-prefs!` / `install-ui-prefs-persistence!`. The watch fires `save-ui-prefs!` only when the extracted slice actually changes — every unrelated state edit (modal open, err-msg, input typing) doesn't trigger a write.

`learn.client/init` wires `install-ui-prefs-persistence!` *after* `mount!` because the Fulcro state-atom isn't populated until then.

4 new specs / 19 new assertions cover the round-trip, whitelist-defence-in-depth, and missing-key edge cases. **58 specs / 417 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-theme-persist**, closes **B-1**.
