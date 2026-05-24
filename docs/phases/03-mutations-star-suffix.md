# Phase 3 — Mutations with `*`-suffix helpers

**Status:** ✅ Complete

Pure state-map → state-map helpers (`add-todo*`, `delete-todo*` etc.) wrapped by thin `defmutation` shells. Establishes the discipline that business logic stays out of mutation bodies.

**Files:** `client.cljc`
**Key concept:** Mutation/helper separation. The mutation thread state into the helper and back; the helper is the testable unit.
