# Phase 7.16 — URL sync watch (S-url-sync-current-list)

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Address bar now reflects the current list — the user can copy any URL straight from the browser (without going through the Copy List URL modal). Pattern mirrors `install-ui-prefs-persistence!`: state-atom watch that change-detects on the denormalized items vector at `[:list/id 1]` and calls a `url-setter` fn when items differ.

1-arity production defaults to `replace-url-with-items!` (CLJS-only — builds URL from `window.location` + encoded segment, calls `history.replaceState`). 2-arity (tests) injects a recording setter. 3 new specs / 7 new assertions. Wired in `init` after the other install-* helpers.

Implements **S-url-sync-current-list** (Planned ⬜ → ✅).
