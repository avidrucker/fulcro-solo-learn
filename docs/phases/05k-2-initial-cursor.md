# Phase 5K.2 — `model.review/initial-cursor` + `next-cursor`

**Status:** ✅ Complete
**Parent:** [Phase 5K — Prioritize/review flow](05k-prioritize-review.md)

Two pure cursor-position helpers using the `::review-cursor` schema's `-1` sentinel:
- `next-cursor [items from-index]` — first `:status/new` index at-or-after `from-index`, else `-1`. Callers wanting "advance past current" pass `(inc cursor)`.
- `initial-cursor [items]` — first `:status/new` at-or-after the last `:status/ready`, else `-1`. Implemented as `next-cursor` composed with a last-ready lookup; returns `-1` gracefully on non-prioritizable lists.

**Acceptance:** 2 specs / 17 new assertions (next-cursor: 4 -1-cases + 5 positive; initial-cursor: 3 -1-cases + 5 positive).

**Totals:** 28 specs / 261 assertions, all green.
