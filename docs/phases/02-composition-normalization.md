# Phase 2 — Composition and normalization

**Status:** ✅ Complete

Parent component (`TodoList`) querying for child idents. Normalized client DB. Tools for reading from normalized state.

**Files:** `client.cljc`
**Key concept:** Idents (`[:todo/id <uuid>]`), `comp/get-query`, `merge/merge-component`, `nsh/dissoc-in` and friends.
