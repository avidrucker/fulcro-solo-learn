(ns learn.util.remote
  "A minimal, CLJC, in-process Fulcro remote.

   The Fulcro headless library (`com.fulcrologic.fulcro.headless.loopback-remotes`)
   ships `sync-remote` for the same purpose, but that namespace is `.clj` —
   JVM-only — so the browser build can't use it. The function below is a
   stripped-down equivalent that runs in both runtimes.

   Used by `learn.client/init` in the CLJS branch to wire the in-process
   Pathom 2 parser as a 'remote'. The JVM branch keeps using the headless
   library's version so its richer test instrumentation (capture, latency,
   etc.) stays available.

   Phase 6 design note: AutoFocus runs entirely in the browser — there is
   no server process — so 'remote' here is purely a logical boundary that
   separates UI from domain/storage."
  (:require
    [edn-query-language.core :as eql]))

(defn sync-remote
  "Build a Fulcro remote that synchronously calls `handler-fn` with the
   EQL extracted from the outgoing transaction's AST.

   `handler-fn` — a function `[eql] => response-body`, typically a Pathom
   parser handler.

   Returns a map satisfying Fulcro's tx-processing remote shape. No
   options (no latency, no error transforms, no abort) — production
   error handling lives in the parser's `error-handling-plugin`."
  [handler-fn]
  {:transmit!
   (fn [_this
        {:com.fulcrologic.fulcro.algorithms.tx-processing/keys [ast result-handler]}]
     (let [eql      (eql/ast->query ast)
           response (handler-fn eql)]
       (result-handler {:status-code 200 :body response})))

   :abort!
   (fn [_ _] nil)})
