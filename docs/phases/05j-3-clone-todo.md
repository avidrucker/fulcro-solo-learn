# Phase 5J.3 — `model.list/clone-todo`

**Status:** ✅ Complete
**Parent:** [Phase 5J — Cancel, complete-benchmark, clone](05j-cancel-complete-clone.md)

Appends a new todo with the source's text; clone status follows `add-todo`'s rule (`:ready` when no ready exists, else `:new`), not the source's. Source is unchanged. Refuses `:error/item-not-found` on missing id.

**Decision (matches JS):** any source status is clone-eligible. Schema docs describe the typical use case (done/cancelled resurrection); model layer doesn't enforce. UI can hide the affordance on actionable items.

**Reference doc added:** `docs/js_source_reference.md` — signatures + divergences for every JS domain function.

**Acceptance:** 7 components / 28 assertions.
