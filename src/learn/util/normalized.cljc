(ns learn.util.normalized
  "Projection helpers between Fulcro's normalized state map and the
   denormalized item vector that pure domain functions in `learn.model.*`
   operate on.

   These were originally `defn-` private helpers in `learn.client`; they
   were promoted here once the review statechart became a second caller
   (5K.5). Keeping them in a dedicated namespace also keeps `client.cljc`
   focused on UI/mutation concerns."
  (:require
    [com.fulcrologic.guardrails.malli.core :refer [>defn =>]]
    [learn.model.schema]))                            ; loads registry

(>defn denormalize-list-items
  "Resolves the todo idents at `[list-ident :list/todos]` into a vector of
   full todo maps, preserving order. Used to project normalized state into
   the shape `learn.model.*` functions expect."
  [state-map list-ident]
  [:any vector? => :learn.model.schema/items]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (mapv #(get-in state-map %) todo-idents)))

(>defn sync-items
  "Writes `items` back into `state-map`: entity-level merge into `:todo/id`
   and rebuild of `[list-ident :list/todos]` from item order. Inverse of
   `denormalize-list-items` — same item set, possibly with mutations."
  [state-map list-ident items]
  [:any vector? :learn.model.schema/items => :any]
  (let [idents         (mapv (fn [t] [:todo/id (:todo/id t)]) items)
        entity-updates (into {} (map (juxt :todo/id identity)) items)]
    (-> state-map
      (update :todo/id merge entity-updates)
      (assoc-in (conj list-ident :list/todos) idents))))
