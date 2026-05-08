(ns learn.parser
  "Pathom 2 parser for the loopback remote.

   The parser is a function `(fn [eql] response)`. Internally it builds
   a query plan from registered resolvers and mutations, then executes it.

   This is what `learn.client/init` hands to `lr/sync-remote`. The shape
   is identical to the previous hand-rolled handler — the loopback remote
   is none the wiser."
  (:require
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [learn.resolvers :as resolvers]))

(def ^:dynamic *debug?*
  "When true, prints the EQL each request and the response."
  false)

(def parser
  "The Pathom 2 parser. Built once at namespace load.

   Plugins:
     - connect-plugin: registers our resolvers and mutations
     - error-handler-plugin: wraps individual resolver errors so one
       broken resolver doesn't kill the whole response"
  (p/parser
    {::p/mutate  pc/mutate
     ::p/env     {::p/reader [p/map-reader
                              pc/reader2
                              pc/index-reader]}
     ::p/plugins [(pc/connect-plugin
                    {::pc/register resolvers/all-resolvers})
                  p/error-handler-plugin]}))

(defn handler
  "Top-level EQL handler called by `lr/sync-remote`.
   Calls the Pathom parser with an empty per-request env."
  [eql]
  (when *debug?*
    (println "PARSER got EQL:" (pr-str eql)))
  (let [response (parser {} eql)]
    (when *debug?*
      (println "PARSER returning:" (pr-str response)))
    response))
