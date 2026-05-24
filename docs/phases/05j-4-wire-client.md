# Phase 5J.4 — Wire Fulcro client mutations to model

**Status:** ✅ Complete
**Parent:** [Phase 5J — Cancel, complete-benchmark, clone](05j-cancel-complete-clone.md)

New state-helpers in `client.cljc` (`cancel-todo*`, `complete-benchmark-item*`, `clone-todo*`) follow the `add-todo*` pattern: denormalize → call `model.list` → reproject via `sync-items` (also new, private). Refusals return state-map unchanged.

The `cancel-todo` mutation was rewired from `set-status*` to `cancel-todo*`, so it now fires auto-mark, refuses on `:done`/`:cancelled`, and refuses missing ids. Two new mutations added: `complete-benchmark-item` and `clone-todo`. List-ident hardcoded `[:list/id 1]` (singleton design — flagged inline for multi-list generalization).

**Acceptance:** 25 specs / 217 assertions, all green. Cold ~3s / warm ~1.4s.
