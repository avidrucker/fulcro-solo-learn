# Phase 7.17 — Read `?list=` on page load (S-url-load-on-init)

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Companion to 7.16's url-sync. When the page opens with `?list=<encoded>`, decode it into items and overwrite SERVER-DB's list. The seed and any localStorage-hydrated state get overridden when the URL alone wins. (Move 2e refined this for the conflict case.)

`parse-list-param` (pure CLJC) extracts `?list=<value>` from a query string. `items-from-query-string` chains it with `url-segment->items` and returns nil if no list param OR decode fails. `items-from-current-url` (CLJS-only) reads `window.location.search`. 2 new specs / 15 new assertions.

Implements **S-url-load-on-init** (Planned ⬜ → ✅).
