(ns learn.main
  "Browser entry point for the AutoFocus front-end (Phase 6).

   Phase 6.1 is intentionally minimal: just enough to prove the
   shadow-cljs build pipeline produces a working bundle. Real wiring
   (Fulcro app mount, server seed, render frame) lands in Phase 6.3
   once the server / resolvers / parser have been migrated to CLJC.")

(defn ^:export init
  "Called from main.js once the bundle loads (entrypoint declared in
   shadow-cljs.edn). Phase 6.1 just prints to the JS console so we can
   confirm the bundle runs."
  []
  (js/console.log "AutoFocus front-end starting (Phase 6.1)"))
