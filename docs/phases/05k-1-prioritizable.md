# Phase 5K.1 — `model.review/prioritizable?`

**Status:** ✅ Complete
**Parent:** [Phase 5K — Prioritize/review flow](05k-prioritize-review.md)

Pure predicate. True iff the list has ≥1 `:status/ready`, ≥1 `:status/new`, AND the last `:new` index > the last `:ready` index. Created `src/learn/model/review.cljc` + `test/learn/model/review_test.cljc`.

**Acceptance:** 3 components / 14 assertions covering missing-ready/missing-new/empty, last-new-at-or-before-last-ready (false cases), and last-new-after-last-ready (true cases, including interleaved `:done`/`:cancelled`).

**Totals:** 26 specs / 244 assertions, all green.
