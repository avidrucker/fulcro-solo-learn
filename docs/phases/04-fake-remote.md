# Phase 4 — Fake remote via `lr/sync-remote`

**Status:** ✅ Complete

Headless TDD setup. The "server" is an atom; the "remote" is a synchronous loopback that runs against the parser. Lets tests exercise real mutation pipelines without network or browser.

**Files:** `client.cljc`, `server.clj`, an initial parser/handler.
**Key concept:** `(lr/sync-remote parser/handler)` as the bridge from client to server-side in-process.
