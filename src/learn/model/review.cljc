(ns learn.model.review
  "Pure domain operations supporting the AutoFocus review/prioritize flow.

   Per SCHEMA.md §10, environment-agnostic (no Fulcro/Pathom/IO). The
   review statechart in `learn.review.chart` composes these as guards
   and actions."
  (:require
    [com.fulcrologic.guardrails.malli.core :refer [>defn =>]]
    [learn.model.list :as model.list]
    [learn.model.schema]))                            ; loads registry

(>defn prioritizable?
  "True when `items` has ≥1 `:status/ready`, ≥1 `:status/new`, and the
   last `:new` appears AFTER the last `:ready` in list order (SCHEMA.md §15
   list-position rule, diverging from the JS source's id-ordering rule)."
  [items]
  [:learn.model.schema/items => :boolean]
  (let [indexed     (map-indexed vector items)
        last-idx-of (fn [status]
                      (->> indexed
                        (keep (fn [[i t]]
                                (when (= status (:todo/status t)) i)))
                        last))
        last-ready  (last-idx-of :status/ready)
        last-new    (last-idx-of :status/new)]
    (boolean (and last-ready last-new (> last-new last-ready)))))

(>defn next-cursor
  "Returns the index of the first `:status/new` item at-or-after `from-index`,
   or `-1` if none. Callers wanting to advance PAST an item should pass
   `(inc current-cursor)`."
  [items from-index]
  [:learn.model.schema/items :int => :learn.model.schema/review-cursor]
  (or (->> (map-indexed vector items)
        (drop from-index)
        (some (fn [[i t]]
                (when (= :status/new (:todo/status t)) i))))
      -1))

(>defn initial-cursor
  "Returns the index of the first `:status/new` item at-or-after the last
   `:status/ready` item, or `-1` if the list has no `:ready` or no `:new`
   following it (i.e. is not prioritizable)."
  [items]
  [:learn.model.schema/items => :learn.model.schema/review-cursor]
  (if-let [last-ready (->> (map-indexed vector items)
                        (keep (fn [[i t]]
                                (when (= :status/ready (:todo/status t)) i)))
                        last)]
    (next-cursor items last-ready)
    -1))

(>defn current-question
  "Returns the review prompt comparing the cursor item to the benchmark
   (last `:status/ready`). Returns `nil` if the cursor is out of range or
   the list has no benchmark — the UI/statechart should treat that as
   'no question to ask'."
  [items cursor]
  [:learn.model.schema/items :learn.model.schema/review-cursor => [:maybe :string]]
  (when (and (nat-int? cursor) (< cursor (count items)))
    (when-let [benchmark (model.list/benchmark-item items)]
      (str "In this moment, are you more ready to '"
        (:todo/text (nth items cursor))
        "' than '"
        (:todo/text benchmark)
        "'?"))))
