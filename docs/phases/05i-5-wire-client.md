# Phase 5I.5 — Wire Fulcro client to domain functions

**Status:** ✅ Complete
**Parent:** [Phase 5I — AutoFocus domain operations](05i-autofocus-domain.md)

Refactor `add-todo*` in `client.cljc` to project the normalized state into a denormalized vector, call `model.list/add-todo`, and project back. The mutation becomes pure plumbing.

**Fixture migration (prerequisite):** `client_test.clj`'s `fixture-state` used integer todo ids; `::schema/items` requires `:uuid`. Migrated to `fixture-id-1` / `fixture-id-2` (distinct UUIDs from `server-id-*` so unit-test and integration-test UUID sets stay visually separable in failure output).

**Semantic note (correctness improvement, not regression):** The empty-list case now produces `:status/ready` (per AutoFocus rule) instead of `:status/new` (old hardcoded). The pre-existing "into an empty list" test didn't assert status, so this change is invisible to it; we added a new assertion that locks in the new behavior.

**Acceptance met:** ~20 specs / ~111 assertions green. `add-todo*` now delegates UUID generation, status rule, and blank-text validation to `learn.model.list/add-todo`. Blank-text input no-ops the state (the error result is swallowed for now; UI surface deferred to a later phase).
