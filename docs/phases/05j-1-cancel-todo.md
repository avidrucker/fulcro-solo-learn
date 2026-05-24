# Phase 5J.1 — `model.list/cancel-todo`

**Status:** ✅ Complete
**Parent:** [Phase 5J — Cancel, complete-benchmark, clone](05j-cancel-complete-clone.md)

Cancels a todo by id, capturing the prior status as `:todo/was`, then composes `auto-mark` over the result. Refuses `:error/item-not-found` (missing id) or `:error/cannot-cancel` (target is `:done`/`:cancelled`).

**Decision:** double-cancel and cancel-on-done both rejected — diverges from the JS source's silent overwrites. Closed SCHEMA.md §14. See `docs/js_source_reference.md` for the JS comparison.

**Acceptance:** 8 components / ~25 assertions.
