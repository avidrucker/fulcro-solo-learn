# Phase 5J — Cancel, complete-benchmark, clone

**Status:** ✅ Complete

Build the rest of the AutoFocus mutation set:
- `cancel-todo` — refuses on `:done`/`:cancelled`, captures `:todo/was`, fires auto-mark
- `complete-benchmark-item` — completes the last ready, fires auto-mark
- `clone-todo` — appends a new todo with the source's text

Each is a `>defn` in `model.list`, with a Fulcro mutation that delegates to it. Server-side Pathom mutations added so `(remote [_] true)` lights up.

## Sub-phases

- ✅ [5J.1 — `model.list/cancel-todo`](05j-1-cancel-todo.md)
- ✅ [5J.2 — `model.list/complete-benchmark-item`](05j-2-complete-benchmark.md)
- ✅ [5J.3 — `model.list/clone-todo`](05j-3-clone-todo.md)
- ✅ [5J.4 — Wire Fulcro client mutations to model](05j-4-wire-client.md)
- ✅ [5J.5 — Server-side Pathom mutations for remote sync](05j-5-server-sync.md)
