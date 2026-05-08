(ns learn.parser
  "Pathom 2 parser for the loopback remote.

   Plugins (ordered outer → inner):
     - error-handling-plugin : catches Throwables from any inner plugin
     - logging-plugin        : optional EQL/response trace via *debug?*
     - connect-plugin        : registers our resolvers and mutations
     - p/error-handler-plugin: built-in resolver-level error handling
     - elide-not-found       : strips :not-found markers from response"
  (:require
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [taoensso.timbre :as log]
    [learn.resolvers :as resolvers]))

(def ^:dynamic *debug?*
  "When true, the logging plugin prints every EQL request and response.
   Bind via: (binding [parser/*debug? true] (df/load! ...))"
  false)

;; ----------------------------------------------------------------------
;; Custom plugins — cross-cutting concerns wrapping every parser call.
;; ----------------------------------------------------------------------

;; ::p/wrap-parser is the most general plugin shape: it sees and may
;; modify the entire request/response pair. We use it for opt-in tracing.
(def logging-plugin
  {::p/wrap-parser
   (fn [parser]
     (fn [env tx]
       (when *debug?*
         (log/debug "PARSER got EQL:" (pr-str tx)))
       (let [response (parser env tx)]
         (when *debug?*
           (log/debug "PARSER returning:" (pr-str response)))
         response)))})

;; Catches Throwable thrown by inner plugins / resolvers and converts
;; to a structured server error. Without this, a single resolver crash
;; kills the whole response. In production this is also where you'd
;; hook into observability (Sentry, structured logs, etc.).
(def error-handling-plugin
  {::p/wrap-parser
   (fn [parser]
     (fn [env tx]
       (try
         (parser env tx)
         (catch Throwable e
           (log/error e "Parser error processing:" (pr-str tx))
           {:server/error      (ex-message e)
            :server/error-data (ex-data e)}))))})

;; ----------------------------------------------------------------------
;; Parser construction.
;;
;; Plugin order matters: wrappers compose outermost-first. So
;; error-handling wraps everything (including logging-plugin), and
;; logging wraps the resolver dispatch chain. The error handler thus
;; catches errors from resolvers AND from the logging plugin itself.
;; ----------------------------------------------------------------------

(def parser
  (p/parser
    {::p/mutate  pc/mutate
     ::p/env     {::p/reader [p/map-reader
                              pc/reader2
                              pc/index-reader]}
     ::p/plugins [error-handling-plugin
                  logging-plugin
                  (pc/connect-plugin {::pc/register resolvers/all-resolvers})
                  p/error-handler-plugin
                  (p/post-process-parser-plugin p/elide-not-found)]}))

(defn handler
  "Top-level EQL handler called by `lr/sync-remote`. Logging and error
   handling now live in the plugin chain; this stays a one-liner."
  [eql]
  (parser {} eql))
