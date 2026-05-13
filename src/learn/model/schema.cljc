(ns learn.model.schema
  "Malli schemas for the AutoFocus domain, registered via Guardrails `>def`.

   See docs/SCHEMA.md for the conceptual model.

   Pure data — no Fulcro, no Pathom, no IO. Loadable from both client
   and server (`.cljc`). Function contracts elsewhere reference these
   schemas by their qualified keyword names (e.g. ::status, ::todo).

   All schemas registered with `>def` go into the Guardrails Malli
   registry, where Guardrails' `>defn` looks them up. This means a
   schema change here propagates automatically to every function that
   declares a parameter or return type by keyword.

   NOTE: `>def` is a two-arg macro: (>def ::keyword schema). It does
   NOT accept a docstring slot like `defn`. Documentation for each
   schema lives in the `;;` comment immediately above it."
  (:require
    [clojure.string :as str]
    [com.fulcrologic.guardrails.malli.core :refer [>def]]
    [malli.registry :as mr]
    [com.fulcrologic.guardrails.malli.registry :as gr.reg]
    #?(:clj  [malli.core :as m]
       :cljs [malli.core :as m])))

(mr/set-default-registry!
  (mr/composite-registry
    (m/default-schemas)
    (mr/mutable-registry gr.reg/schema-atom)))

;; ============================================================================
;; Predicates — shared shape checks usable inside schemas.
;; ============================================================================

(defn non-blank-string?
  "True for non-nil, non-blank strings. Used by ::todo's :todo/text constraint."
  [s]
  (and (string? s) (not (str/blank? s))))

;; ============================================================================
;; Status enum
;; ============================================================================

(def status-values
  "The four valid todo statuses as a set. Exposed for client code that
   iterates (e.g., a status legend) without re-extracting from the schema."
  #{:status/new :status/ready :status/done :status/cancelled})

;; Schema for a single status value.
(>def ::status (into [:enum] status-values))

;; ============================================================================
;; Todo entity
;; ============================================================================

;; Schema for a single todo entity. `:todo/was` is optional but should
;; only be present when `:todo/status = :status/cancelled`. The stricter
;; mutual constraint is enforced by `set-status*` in client.cljc rather
;; than at the schema level — schema stays permissive so transient states
;; during transitions don't raise spurious validation errors.
(>def ::todo
  [:map
   [:todo/id     :uuid]
   [:todo/text   [:fn non-blank-string?]]
   [:todo/status ::status]
   [:todo/was    {:optional true} ::status]])

;; ============================================================================
;; Item collection (denormalized; what pure domain functions operate on)
;; ============================================================================

;; The denormalized vector of todos that pure domain functions take as
;; input. Order is meaningful — it determines the benchmark (last
;; ready) and auto-mark target (first new). Fulcro mutations are
;; responsible for projecting from normalized state into this shape
;; and back.
(>def ::items [:vector ::todo])

;; ============================================================================
;; Fulcro normalized-state shapes
;; ============================================================================

;; Fulcro ident referring to an entry in the :todo/id table.
(>def ::todo-ident [:tuple [:= :todo/id] :uuid])

;; Schema for a normalized list entity. Order in :list/todos defines
;; the benchmark item and auto-mark target.
(>def ::todo-list
  [:map
   [:list/id          :int]
   [:list/todos       [:vector ::todo-ident]]
   [:ui/new-todo-text {:optional true} :string]])

;; ============================================================================
;; Review state
;; ============================================================================

;; An index into :list/todos, or -1 when no review is active. Stored as
;; a single value so the invariant 'cursor is -1 iff review is inactive'
;; is recoverable from the data without a separate flag.
(>def ::review-cursor
  [:or [:= -1]
   [:and :int [:>= 0]]])

;; Schema for the prioritization review session. There is at most one
;; active review per app instance.
(>def ::review-state
  [:map
   [:review/active? :boolean]
   [:review/cursor  ::review-cursor]])

;; ============================================================================
;; Error types and Result shapes
;; ============================================================================

;; Structured error keywords. UI maps these to human-readable strings.
;; Extend when new failure modes are introduced — keeping all error
;; types here makes the UI's mapping table easy to find.
(>def ::error-type
  [:enum
   :error/blank-item
   :error/item-not-found
   :error/cannot-cancel
   :error/no-actionable-items
   :error/not-prioritizable-list
   :error/invalid-review-decision
   ;; Phase 7.12: batch-import textarea is empty or all-whitespace lines.
   :error/empty-import])

;; Shape returned by a domain function on success.
(>def ::success-result
  [:map
   [:ok?   [:= true]]
   [:items ::items]])

;; Shape returned by a domain function on failure.
(>def ::error-result
  [:map
   [:ok?        [:= false]]
   [:error/type ::error-type]])

;; Union of success and error result shapes. The :ok? key tags the variant.
(>def ::result [:or ::success-result ::error-result])

;; ============================================================================
;; Validation helpers (development convenience)
;;
;; Use these from REPL or in tests. Production code typically uses
;; Guardrails' `>defn` validation rather than calling these inline.
;; ============================================================================

(defn valid?
  "Returns true if `x` conforms to the named schema (e.g. ::todo, ::items)."
  [schema-name x]
  (m/validate schema-name x))

(defn explain
  "Returns a human-readable explanation of why `x` fails to conform to
   `schema-name`, or `nil` if it conforms."
  [schema-name x]
  (m/explain schema-name x))

;; ============================================================================
;; Sample data — useful for REPL, tests, and living documentation.
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
  ;; Sanity-check from REPL after the master test runner has loaded
  ;; this namespace (the Guardrails registry needs to be populated):
  (valid? ::todo example-todo)              ; => true
  (valid? ::todo example-cancelled-todo)    ; => true
  (valid? ::todo {:todo/text "broken"})     ; => false
  (explain ::todo {:todo/text ""})          ; => map describing violations
  (valid? ::items [example-todo])           ; => true
  )
