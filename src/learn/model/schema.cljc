(ns learn.model.schema
  "Malli schemas for the AutoFocus domain.

   See docs/SCHEMA.md for the conceptual model and the rationale behind
   the choices below. This namespace is the executable counterpart.

   Pure data — no Fulcro, no Pathom, no IO. Loadable from both client
   and server (`.cljc`). Function contracts elsewhere (in item.cljc,
   list.cljc, etc.) reference these schemas via Guardrails' `>defn`."
  (:require
    [clojure.string :as str]
    #?(:clj  [malli.core :as m]
       :cljs [malli.core :as m])))

;; ============================================================================
;; Status enum
;; ============================================================================

(def status-values
  "The four valid todo statuses. Exposed as a set so client code can iterate
   for UI rendering (e.g., a status legend) without re-extracting from the
   schema."
  #{:status/new :status/ready :status/done :status/cancelled})

(def Status
  "Schema for a single status value."
  (into [:enum] status-values))

;; ============================================================================
;; Predicates — shared shape checks usable inside schemas and elsewhere.
;; ============================================================================

(defn non-blank-string?
  "True for non-nil, non-blank strings. Used by Todo's :todo/text constraint."
  [s]
  (and (string? s) (not (str/blank? s))))

;; ============================================================================
;; Todo
;; ============================================================================

(def Todo
  "Schema for a single todo. `:todo/was` is optional but should be present
   exactly when `:todo/status = :status/cancelled`. The stricter mutual
   constraint is enforced by the set-status* helper in client.cljc, not
   at the schema level — keeping the schema permissive prevents spurious
   validation errors during state transitions."
  [:map
   [:todo/id     :uuid]
   [:todo/text   [:fn non-blank-string?]]
   [:todo/status Status]
   [:todo/was    {:optional true} Status]])

;; ============================================================================
;; List (normalized entity)
;; ============================================================================

(def TodoIdent
  "Fulcro ident referring to an entry in the :todo/id table."
  [:tuple [:= :todo/id] :uuid])

(def TodoList
  "Schema for a normalized list entity. Order in :list/todos is meaningful;
   it determines the benchmark item (last ready) and auto-mark target
   (first new)."
  [:map
   [:list/id          :int]
   [:list/todos       [:vector TodoIdent]]
   [:ui/new-todo-text {:optional true} :string]])

;; ============================================================================
;; Review state
;; ============================================================================

(def ReviewCursor
  "An index into :list/todos, or -1 when no review is active.
   Stored as a single value rather than two separate fields to keep
   the invariant 'cursor is -1 iff review is inactive' implicit but
   recoverable from data."
  [:or [:= -1]
   [:and :int [:>= 0]]])

(def ReviewState
  "Schema for the prioritization review session. There is at most one
   active review per app instance."
  [:map
   [:review/active? :boolean]
   [:review/cursor  ReviewCursor]])

;; ============================================================================
;; Result shapes — how pure domain functions report success/failure.
;; Mutations branch on :ok? to decide how to update Fulcro state.
;; ============================================================================

(def ErrorType
  "Structured error keywords. UI maps these to human-readable strings.
   Extend this set when new failure modes are introduced — keeping all
   error types in one schema makes the UI's mapping table easy to find."
  [:enum
   :error/blank-item
   :error/item-not-found
   :error/no-actionable-items
   :error/invalid-review-decision
   :error/no-prioritizable-items])

(def Items
  "The denormalized vector of todos that pure domain functions operate on.
   Mutations are responsible for projecting from normalized Fulcro state
   into this shape and back. Keeping the domain layer on plain vectors
   makes the rules easier to read, test, and port to non-Fulcro contexts."
  [:vector Todo])

(def SuccessResult
  "Shape returned by a domain function on success."
  [:map
   [:ok?   [:= true]]
   [:items Items]])

(def ErrorResult
  "Shape returned by a domain function on failure."
  [:map
   [:ok?         [:= false]]
   [:error/type  ErrorType]])

(def Result
  "Union of success and error result shapes. The :ok? key tags the variant."
  [:or SuccessResult ErrorResult])

;; ============================================================================
;; Validation helpers (development convenience)
;; ============================================================================

(defn valid-todo?
  "Returns true if `x` conforms to the Todo schema. Useful in REPL and tests;
   production code typically uses Guardrails' `>defn` instead of inline calls."
  [x]
  (m/validate Todo x))

(defn valid-items?
  "Returns true if `x` is a vector of valid todos."
  [x]
  (m/validate Items x))

(defn explain-todo
  "Returns a human-readable explanation of why `x` fails to be a Todo,
   or `nil` if it conforms. Pair with REPL exploration."
  [x]
  (m/explain Todo x))

;; ============================================================================
;; Sample data — useful for REPL, doctests, and as living documentation.
;; ============================================================================

(def example-todo
  "A minimal valid todo, for REPL exploration."
  {:todo/id     #uuid "00000000-0000-0000-0000-000000000001"
   :todo/text   "Write tests"
   :todo/status :status/ready})

(def example-cancelled-todo
  "A cancelled todo demonstrating :todo/was capture."
  {:todo/id     #uuid "00000000-0000-0000-0000-000000000002"
   :todo/text   "Call dentist"
   :todo/status :status/cancelled
   :todo/was    :status/new})

(comment
  ;; Sanity-check from REPL:
  (valid-todo? example-todo)            ; => true
  (valid-todo? example-cancelled-todo)  ; => true
  (valid-todo? {:todo/text "broken"})   ; => false
  (explain-todo {:todo/text ""})        ; => map describing the violations
  )
