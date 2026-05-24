# Phase 5I.4 — `model.list/add-todo`

**Status:** ✅ Complete
**Parent:** [Phase 5I — AutoFocus domain operations](05i-autofocus-domain.md)

Appends a new todo with the AutoFocus add rule: `:status/ready` if no ready items exist, else `:status/new`. Validates non-blank text via the schema; returns Result-shaped map.

**Design:** Multi-arity `>defn` overload. 2-arg form `(add-todo items text)` generates a fresh UUID and delegates to 3-arg form `(add-todo items text id)`. The 3-arg form is the pure, testable core; specs exercise it for deterministic assertions. A small set of specs verifies the 2-arg form's UUID-generation behavior.

**Acceptance met:** 20 specs / ~103 assertions green. Specs cover blank text (4 cases), empty-list-becomes-ready, no-ready-list-becomes-ready (4 cases), with-ready-list-becomes-new (3 cases), and UUID generation (3 cases).
