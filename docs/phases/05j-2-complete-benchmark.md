# Phase 5J.2 — `model.list/complete-benchmark-item`

**Status:** ✅ Complete
**Parent:** [Phase 5J — Cancel, complete-benchmark, clone](05j-cancel-complete-clone.md)

Completes the benchmark (last `:status/ready` by list order) by marking it `:status/done`, then composes `auto-mark`. Refuses `:error/no-actionable-items`.

**Decision:** no `:todo/was` capture — there is no un-complete operation, so unlike cancel there is nothing to record.

**Acceptance:** 6 components / 25 assertions.
