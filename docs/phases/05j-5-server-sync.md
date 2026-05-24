# Phase 5J.5 — Server-side Pathom mutations for remote sync

**Status:** ✅ Complete
**Parent:** [Phase 5J — Cancel, complete-benchmark, clone](05j-cancel-complete-clone.md)

Server mirrors the client's normalized shape (`:list/id` + `:todo/id`), with `server/items` / `server/write-items` as the projection helpers. Each server-side Pathom mutation (`add-todo`, `cancel-todo`, `complete-benchmark-item`, `clone-todo`) is the same one-line `record-list-items` call — the server is dumb storage. All AutoFocus domain logic stays on the client.

Client mutations now flip `(remote [env] (remote-list-items env))`, which sends the post-action denormalized items vector to the server as `:list/items`. UUIDs propagate naturally (no tempid mechanism needed yet).

**Decision:** rejected an alternative where the server runs `model.list` too. Single source of domain truth (client) is simpler, makes the server replaceable (Datomic, Postgres, etc.) without porting logic, and matches the user's "frontend handles list/item processing" stance. A future phase can add a server-side validator that rejects ill-shaped lists; for now, trust.

**Acceptance:** 25 specs / 230 assertions, all green. Warm ~1.7 s.
