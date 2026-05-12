(ns learn.main
  "Browser entry point for the AutoFocus front-end (Phase 6).

   Phase 6.1 is intentionally minimal: just enough to prove the
   shadow-cljs build pipeline produces a working bundle. Real wiring
   (Fulcro app mount, server seed, render frame) lands in Phase 6.3
   once the server / resolvers / parser have been migrated to CLJC."
  (:require
    ;; Phase 6.2 smoke test: requiring the three CLJC namespaces forces
    ;; shadow-cljs to compile them as ClojureScript. If any of them used
    ;; JVM-only constructs, this would fail at compile time. We don't
    ;; use these refs yet — Phase 6.3 will pull in `learn.client` which
    ;; transitively requires them — but binding them silences "unused
    ;; require" warnings during the migration step.
    [learn.server :as server]
    [learn.parser :as parser]
    [learn.resolvers :as resolvers]))

(defn ^:export init
  "Called from main.js once the bundle loads (entrypoint declared in
   shadow-cljs.edn). Phase 6.1 prints to the JS console; Phase 6.2 also
   logs the number of registered resolvers to prove the CLJC migration
   compiled successfully under the CLJS target."
  []
  (js/console.log
    (str "AutoFocus front-end starting (Phase 6.2). "
      "Registered resolvers: " (count resolvers/all-resolvers) ". "
      "Server list-id: " server/list-id ". "
      "Parser handler: " (boolean parser/handler) ".")))
