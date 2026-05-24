# Phase 5K.3 — `model.review/current-question`

**Status:** ✅ Complete
**Parent:** [Phase 5K — Prioritize/review flow](05k-prioritize-review.md)

Pure formatter. Takes `[items cursor]`, returns the prompt `"In this moment, are you more ready to '{cursor-text}' than '{benchmark-text}'?"` on valid input, or `nil` when the cursor is out of range or the list has no benchmark. Delegates benchmark lookup to `learn.model.list/benchmark-item` (review namespace now requires model.list).

**Decision:** returns `nil` (not the JS error-string variants) on degenerate input. The model layer's responsibility is question-or-not; UI maps `nil` to "no question to ask". This avoids leaking presentation strings into the domain.

**CLJC note:** uses `str` instead of `format` so the function compiles unchanged for both `.clj` and `.cljs` targets.

**Acceptance:** 2 components / 7 new assertions. **Totals:** 29 specs / 268 assertions, all green.
