# Phase 7.1 — `learn.util.storage` ns

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

CLJC split:
- Pure `->edn` / `<-edn` (testable on JVM). `<-edn` returns `nil` on blank input, malformed EDN, reader-eval forms (`#=`), and **any non-map result** — the last guard catches `clojure.edn/read-string` succeeding on garbage like `"not edn"` by returning the symbol `'not`. Map-only contract is just strong enough that callers can always feed `(or (<-edn s) seed)` and trust the type.
- CLJS-only `save!` / `load!` / `clear!` wrap `js/localStorage` with try-catch swallows (quota-exceeded, privacy-mode disabled, etc. shouldn't crash a render).
- `install-persistence!` is CLJC: on CLJS it hydrates + attaches a watch on `SERVER-DB`; on JVM it's a no-op so callers can use a single signature.
- Storage key is `"autofocus.server-db"` — namespaced enough that an unrelated site key collision is implausible.

40 specs / 337 assertions, all green (added 3 specs / 13 assertions for the round-trip + corruption + key-name properties).
