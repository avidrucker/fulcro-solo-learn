# Phase 20a — Long-form algorithm cross-validation scenarios

**Status:** ✅ Complete
**Parent:** [Phase 20 — testing-pyramid fill-in](20-testing-pyramid.md)

New `test/learn/model/scenarios_test.cljc`. Three stepped scenarios that walk a list through 5-10 model operations and assert on intermediate AND terminal state. Helpers mirror fp-autofocus's `af-test-utils.ts`: `items->marks` ≅ `expectMarksString`, `add-many` ≅ `populateDemoAppByList`, `simulate-yes/no` ≅ chart yes/no actions at the model layer, `simulate-answers` ≅ `SIMenterMarkAndReviewState`.

Scenarios:
  1. Simple 3-item add/review/complete — cross-port of fp-autofocus's "Simple E2E test". Documents the SCHEMA.md §7 add-rule divergence (our `add-todo` auto-promotes item 0 to `:ready`).
  2. Mark-Done auto-mark promotion — validates SCHEMA.md §6 under the complete-benchmark path.
  3. Cancel-and-auto-mark — confirms `cancel-todo` composes `auto-mark` just like `complete-benchmark`.

Joins the existing master test runner; no new deps.
127 specs / 843 assertions (+3 / +14), all green.
