# Phase 5I.3 — `model.list/auto-markable?` and `auto-mark`

**Status:** ✅ Complete
**Parent:** [Phase 5I — AutoFocus domain operations](05i-autofocus-domain.md)

`auto-markable?` is a predicate over items. `auto-mark*` promotes the first new item to ready if the list is auto-markable; otherwise returns items unchanged.

**Decision made:** JS-port discrepancy #5 — the original `automark` reads the function reference instead of calling it (`if (!isAutoMarkableList)` not `if (!isAutoMarkableList(tasks))`). Fixed in the Clojure port: `auto-mark` calls `(auto-markable? items)` properly.

**Naming change:** Dropped the `*` suffix from `auto-mark` (was `auto-mark*` in earlier roadmap). The `*` suffix is reserved for state-map → state-map helpers (see `client.cljc`'s `add-todo*`, `set-status*`); model-layer functions take items vectors in and out, so the suffix doesn't apply. When Phase 5J wraps this in a Fulcro mutation, that mutation's body might call `auto-mark*` if a state-map helper is needed.

**`model.item` deferred.** SCHEMA.md §10 anticipates a `model.item.cljc` for status predicates. We kept `new?` and `ready?` as `defn-` in `model.list` because two predicates with two callers don't yet justify the extra file. Promote to `model.item` when a third caller appears (likely 5J's `cancel-todo` needing `done?`/`cancelled?` for the refusal check).

**Acceptance met:** 18 specs / ~88 assertions green. Specs cover empty list, all-ready, mixed-state, idempotence, and "no ready items in mix" cases.
