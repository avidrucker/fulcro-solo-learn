# Phase 6.4 — Browser app loads and round-trips (3 bugfixes)

**Status:** ✅ Complete
**Parent:** [Phase 6 — shadow-cljs + browser app (no real backend)](06-shadow-cljs.md)

Browser app loads and round-trips. Surfaced three bugs along the way, all fixed in one `fix ... phase 6 bugfix` commit:

1. **Review state subscription** — `TodoList` read chart state via `scf/current-configuration` (a side-channel Fulcro can't see), so the optimized renderer skipped re-rendering after Yes/No/Quit. Headless tests masked this by calling `h/render-frame!` after every click. Fix: ident-joins in `:query` against `[::sc/session-id :review-session]` and `[::sc/local-data :review-session]` so Fulcro knows the component depends on those paths.

2. **Fulcro Inspect 1.x wiring** — the deprecated `com.fulcrologic.fulcro.inspect.preload` was logging "Inspect NOT installed" because Inspect 1.x requires both `com.fulcrologic.devtools.chrome-preload` *and* an explicit `(fulcro.inspect.tool/add-fulcro-inspect! spa)` call. Both wired in.

3. **`goog.reflect.cache is not a function`** at runtime — the **shaded** `closure-compiler` jar pulled in by ClojureScript 1.12.42 bundles `lib/{base,goog,reflect}.js`, which shadow-cljs mis-classifies as JS sources, producing a duplicate provide for `goog.reflect` (stripped vs full). Fix: top-level `:exclusions [com.google.javascript/closure-compiler]` on `org.clojure/clojurescript` in deps.edn. shadow-cljs supplies `closure-compiler-unshaded` itself, so nothing else broke.

**Out of scope here:** persistence (page reload resets seed) and styling (Phase 6.5).
