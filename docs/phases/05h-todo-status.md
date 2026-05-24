# Phase 5H — Schema migration: `:todo/done?` → `:todo/status`

**Status:** ✅ Complete

Replaced the boolean `:todo/done?` with a four-value enum `:todo/status` (`:status/{new,ready,done,cancelled}`) plus a `:todo/was` field for capturing prior status during cancellation. Migration touched every layer — pure helpers, mutations, UI, resolvers, tests.

**Files:** `client.cljc`, `server.clj`, `resolvers.clj`, `client_test.clj`, `resolvers_test.clj`
**Key concepts:** Namespaced-keyword discipline (one rename per layer, all `grep`-able); test fossils from removed helpers cleaned up; `set-status*` with `:todo/was` capture; `affects-only?` test helper.

**Final state:** 16 specs / 57 assertions green via the master test runner.
