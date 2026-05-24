# Phase 5I.2 — `model.list/benchmark-item`

**Status:** ✅ Complete
**Parent:** [Phase 5I — AutoFocus domain operations](05i-autofocus-domain.md)

Pure read function: returns the last `:status/ready` todo from a vector, or `nil`. The simplest domain function — establishes the pattern for the rest.

**Files:** `src/learn/model/list.cljc`, `test/learn/model/list_test.cljc`
**Acceptance:** Spec covers no-ready (4 sub-cases), one-ready (3 sub-cases), multiple-ready / last-wins (3 sub-cases).
`(>defn benchmark-item [items] [::schema/items => (? ::schema/todo)] ...)`.
