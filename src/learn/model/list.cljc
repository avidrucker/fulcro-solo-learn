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
    [clojure.string :as str]
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

;; ============================================================================
;; auto-markable? and auto-mark
;;
;; The auto-mark rule (SCHEMA.md §6): when a list has new items but no ready
;; items, the first new item (in list order) is automatically promoted to
;; ready. This fires as a *consequence* of complete-benchmark and cancel-todo
;; — it doesn't fire on add-todo or set-status.
;; ============================================================================

(defn- new?
  "True for todos with :status/new."
  [todo]
  (= :status/new (:todo/status todo)))

(defn- ready?
  "True for todos with :status/ready."
  [todo]
  (= :status/ready (:todo/status todo)))

(>defn auto-markable?
  "True when `items` is eligible for auto-marking: at least one
   :status/new item is present, and zero :status/ready items exist.

   Returns false on an empty list — no new items means nothing to promote."
  [items]
  [:learn.model.schema/items => :boolean]
  (boolean
    (and (some new? items)
      (not-any? ready? items))))

(>defn auto-mark
  "If `items` is auto-markable, returns a copy with the first :status/new
   item promoted to :status/ready. Otherwise returns `items` unchanged.

   Idempotent: applying twice yields the same result as applying once,
   because after the first call there is a ready item and the list is
   no longer auto-markable."
  [items]
  [:learn.model.schema/items => :learn.model.schema/items]
  (if-not (auto-markable? items)
    items
    (let [first-new-idx (->> items
                          (map-indexed vector)
                          (some (fn [[i t]] (when (new? t) i))))]
      (assoc-in items [first-new-idx :todo/status] :status/ready))))

;; ============================================================================
;; add-todo
;;
;; Appends a fresh todo to the list, with status determined by the AutoFocus
;; add rule (SCHEMA.md §7):
;;   - If items has zero :status/ready items → new todo is :status/ready
;;   - Otherwise (at least one ready exists)  → new todo is :status/new
;;
;; Blank text returns an :error/blank-item result without modifying items.
;;
;; The 2-arity form generates a fresh UUID; the 3-arity form takes an explicit
;; UUID for deterministic testing. Production callers (Fulcro mutations) use
;; the 2-arity; specs use the 3-arity.
;; ============================================================================

(>defn add-todo
  "Appends a fresh todo to `items`. Returns a Result map.

   Status rule (SCHEMA.md §7):
     - If `items` has zero :status/ready items → new todo gets :status/ready.
     - Otherwise (at least one ready exists)   → new todo gets :status/new.

   Blank text (empty, whitespace-only, or nil-by-trim) returns
   {:ok? false :error/type :error/blank-item} with `items` unmodified.

   The 2-arity form generates a fresh random UUID. The 3-arity form takes an
   explicit UUID — used by specs for deterministic assertions."
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
