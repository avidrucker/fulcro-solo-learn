(ns learn.model.list
  "Pure domain operations on a list of todos.

   Per docs/SCHEMA.md §10, the `learn.model.*` namespaces are environment-
   agnostic — no Fulcro, no Pathom, no IO. They operate on the *denormalized*
   shape: a vector of todos `[{:todo/id ... :todo/text ... :todo/status ...}
   ...]`. The Fulcro mutation layer's job is to project from normalized
   state into this shape and back.

   Operation contracts and behavior are defined in SCHEMA.md §7. This
   namespace implements them one at a time, each with a Guardrails `>defn`
   referencing schemas from `learn.model.schema`."
  (:require
    [com.fulcrologic.guardrails.malli.core :refer [>defn => ?]]
    [learn.model.schema]))                            ; loads registry; no alias needed

;; ============================================================================
;; benchmark-item
;;
;; The benchmark item is the "next actionable" todo — by AutoFocus convention,
;; the LAST :status/ready item in list order. It's what the user is meant to
;; be working on right now; completing it advances the list forward.
;;
;; Returns nil when the list has no ready items (the list is then
;; "inactionable" — see SCHEMA.md §5).
;; ============================================================================

(>defn benchmark-item
  "Returns the last `:status/ready` todo from `items`, or `nil` if none exist.

   Order in `items` is significant: among multiple ready items, the *last*
   one (by list position) is the benchmark. This matches the AutoFocus
   rule that newer ready items take precedence as the focal task."
  [items]
  [:learn.model.schema/items => (? :learn.model.schema/todo)]
  (->> items
    (filter #(= :status/ready (:todo/status %)))
    last))
