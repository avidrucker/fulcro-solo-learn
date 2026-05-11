(ns learn.model.list
  "Pure domain operations on a list of todos.

   Per docs/SCHEMA.md §10, the `learn.model.*` namespaces are environment-
   agnostic — no Fulcro, no Pathom, no IO. They operate on the *denormalized*
   shape: a vector of todos `[{:todo/id ... :todo/text ... :todo/status ...}
   ...]`. The Fulcro mutation layer projects from normalized state into
   this shape and back.

   Operation contracts and behavior are defined in SCHEMA.md §7."
  (:require
    [clojure.string :as str]
    [com.fulcrologic.guardrails.malli.core :refer [>defn => ?]]
    [learn.model.schema]))                            ; loads registry; no alias needed

;; ============================================================================
;; Status predicates and index lookup (private)
;; ============================================================================

(defn- new?   [todo] (= :status/new   (:todo/status todo)))
(defn- ready? [todo] (= :status/ready (:todo/status todo)))

(defn- indices-of
  "Lazy seq of indices in `items` where `pred` returns truthy."
  [pred items]
  (->> items
    (map-indexed vector)
    (keep (fn [[i t]] (when (pred t) i)))))

;; ============================================================================
;; benchmark-item
;; ============================================================================

(>defn benchmark-item
  "Returns the last `:status/ready` todo from `items`, or `nil` if none exist.
   Among multiple ready items, the *last* by list position is the benchmark."
  [items]
  [:learn.model.schema/items => (? :learn.model.schema/todo)]
  (last (filter ready? items)))

;; ============================================================================
;; auto-markable? and auto-mark
;; ============================================================================

(>defn auto-markable?
  "True when `items` has at least one `:status/new` and zero `:status/ready`.
   See SCHEMA.md §6 for the auto-mark rule."
  [items]
  [:learn.model.schema/items => :boolean]
  (boolean (and (some new? items) (not-any? ready? items))))

(>defn auto-mark
  "If `items` is auto-markable, promotes the first `:status/new` to
   `:status/ready`; otherwise returns `items` unchanged. Idempotent."
  [items]
  [:learn.model.schema/items => :learn.model.schema/items]
  (if-not (auto-markable? items)
    items
    (let [idx (first (indices-of new? items))]
      (assoc-in items [idx :todo/status] :status/ready))))

;; ============================================================================
;; add-todo
;; ============================================================================

(>defn add-todo
  "Appends a fresh todo to `items` and returns a Result map.

   Status rule (SCHEMA.md §7): `:status/ready` when `items` has no `:ready`,
   else `:status/new`. Blank text returns
   `{:ok? false :error/type :error/blank-item}`.

   2-arity generates a fresh UUID; 3-arity takes an explicit id."
  ([items text]
   [:learn.model.schema/items :string => :learn.model.schema/result]
   (add-todo items text (random-uuid)))
  ([items text id]
   [:learn.model.schema/items :string :uuid => :learn.model.schema/result]
   (if (str/blank? text)
     {:ok? false :error/type :error/blank-item}
     (let [status   (if (some ready? items) :status/new :status/ready)
           new-todo {:todo/id     id
                     :todo/text   text
                     :todo/status status}]
       {:ok? true :items (conj items new-todo)}))))

;; ============================================================================
;; cancel-todo
;; ============================================================================

(>defn cancel-todo
  "Cancels the todo with `id`, capturing the prior status as `:todo/was`,
   then composes `auto-mark` over the result.

   Refusals: `:error/item-not-found` (missing id),
   `:error/cannot-cancel` (target is `:done` or `:cancelled`)."
  [items id]
  [:learn.model.schema/items :uuid => :learn.model.schema/result]
  (let [idx (first (indices-of #(= id (:todo/id %)) items))]
    (cond
      (nil? idx)
      {:ok? false :error/type :error/item-not-found}

      (contains? #{:status/done :status/cancelled}
        (:todo/status (nth items idx)))
      {:ok? false :error/type :error/cannot-cancel}

      :else
      (let [prev      (:todo/status (nth items idx))
            cancelled (-> (nth items idx)
                        (assoc :todo/status :status/cancelled
                               :todo/was    prev))
            updated   (assoc items idx cancelled)]
        {:ok? true :items (auto-mark updated)}))))

;; ============================================================================
;; complete-benchmark-item
;; ============================================================================

(>defn complete-benchmark-item
  "Completes the benchmark (last `:status/ready` by list order) by setting
   its status to `:status/done`, then composes `auto-mark` over the result.

   Refuses with `:error/no-actionable-items` when no `:status/ready` exists."
  [items]
  [:learn.model.schema/items => :learn.model.schema/result]
  (if-let [idx (last (indices-of ready? items))]
    (let [completed (assoc (nth items idx) :todo/status :status/done)
          updated   (assoc items idx completed)]
      {:ok? true :items (auto-mark updated)})
    {:ok? false :error/type :error/no-actionable-items}))

;; ============================================================================
;; clone-todo
;; ============================================================================

(>defn clone-todo
  "Appends a new todo carrying the source's text. The clone's status follows
   `add-todo`'s rule (not the source's). Source is unchanged.

   Refuses `:error/item-not-found` on missing id.

   2-arity generates a fresh UUID; 3-arity takes an explicit clone-id."
  ([items id]
   [:learn.model.schema/items :uuid => :learn.model.schema/result]
   (clone-todo items id (random-uuid)))
  ([items id clone-id]
   [:learn.model.schema/items :uuid :uuid => :learn.model.schema/result]
   (if-let [source (some #(when (= id (:todo/id %)) %) items)]
     (add-todo items (:todo/text source) clone-id)
     {:ok? false :error/type :error/item-not-found})))
