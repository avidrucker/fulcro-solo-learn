(ns learn.review.chart-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [com.fulcrologic.statecharts.testing :as t]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [taoensso.timbre :as log]
    [learn.review.chart :as sut]))

;; Silence the statecharts library's verbose DEBUG logging during tests.
;; Using :error rather than :warn: the chart's `:yes` action emits a
;; `fop/invoke-remote` op (5K.6) that the chart-only testing env's
;; working-memory data-model can't process — it logs a harmless WARN
;; ("Operation not understood :fulcro/invoke-remote"). State-map and
;; cursor assertions still hold; we just want quiet test output.
(log/merge-config! {:min-level :error})

;; ============================================================================
;; Test fixtures
;;
;; The chart reads items from `(:fulcro/state-map data)` and denormalizes via
;; `learn.util.normalized/denormalize-list-items`. In CLJ-only tests, we seed
;; `:fulcro/state-map` into the chart's session data via `goto-configuration!`
;; before sending events. Production (5K.5b) uses the Fulcro integration to
;; merge the live app state in automatically; the chart's contract is the same.
;; ============================================================================

(def id-1 #uuid "11111111-1111-1111-1111-111111111111")
(def id-2 #uuid "22222222-2222-2222-2222-222222222222")
(def id-3 #uuid "33333333-3333-3333-3333-333333333333")

(defn todo
  ([id text]        (todo id text :status/new))
  ([id text status] {:todo/id id :todo/text text :todo/status status}))

(defn fulcro-state
  "Wraps an items vector as a Fulcro-normalized state-map at `[:list/id 1]`."
  [items]
  {:list/id {1 {:list/id 1
                :list/todos (mapv (fn [t] [:todo/id (:todo/id t)]) items)}}
   :todo/id (into {} (map (juxt :todo/id identity)) items)})

(def state-prioritizable-2
  "Smallest prioritizable state: one :ready followed by one :new."
  (fulcro-state [(todo id-1 "Read book" :status/ready)
                 (todo id-2 "Walk dog"  :status/new)]))

(def state-prioritizable-3
  "Prioritizable state with two :new candidates after the benchmark."
  (fulcro-state [(todo id-1 "Read book"  :status/ready)
                 (todo id-2 "Walk dog"   :status/new)
                 (todo id-3 "Call Alice" :status/new)]))

(def state-non-prioritizable
  "Last :new is before last :ready — fails the SCHEMA.md §15 rule."
  (fulcro-state [(todo id-1 "Stale new" :status/new)
                 (todo id-2 "Anchor"    :status/ready)]))

(defn new-env
  "Builds a testing env that actually runs the chart's guards and actions.
   Without `:run-unmocked? true`, expression fns default to mocked no-ops
   so guards always return nil/false and transitions don't fire."
  []
  (t/new-testing-env {:statechart      sut/chart
                      :mocking-options {:run-unmocked? true}}
    {}))

(defn seed-state!
  "Seeds the chart session's local data with `:fulcro/state-map state-map`
   while keeping the chart in `:inactive`. Mirrors what `scf/start!` would
   do in a real Fulcro context."
  [env state-map]
  (t/goto-configuration! env
    [(ops/assign [:fulcro/state-map] state-map)]
    #{sut/inactive}))

(defn cursor-todo-status
  "Reads the cursor todo's :todo/status from the chart's seeded state-map."
  [env id]
  (get-in (t/data env) [:fulcro/state-map :todo/id id :todo/status]))

;; ============================================================================
;; Specifications
;; ============================================================================

(specification "review chart — initial state"
  (component "starts in :inactive"
    (let [env (new-env)]
      (t/start! env)
      (assertions
        "configuration is #{:inactive}"
        (t/in? env sut/inactive) => true
        ":active is NOT in the configuration"
        (t/in? env sut/active) => false))))

(specification "review chart — :start"
  (component "non-prioritizable state-map — guard rejects, stays inactive"
    (let [env (new-env)]
      (t/start! env)
      (seed-state! env state-non-prioritizable)
      (t/run-events! env sut/event-start)
      (assertions
        "still in :inactive"
        (t/in? env sut/inactive) => true
        "cursor not assigned (chart didn't enter :active)"
        (:cursor (t/data env)) => nil)))

  (component "prioritizable state-map — transitions to :active and seeds cursor"
    (let [env (new-env)]
      (t/start! env)
      (seed-state! env state-prioritizable-2)
      (t/run-events! env sut/event-start)
      (assertions
        "in :active"
        (t/in? env sut/active) => true
        "cursor is initial-cursor (1 for [:ready :new])"
        (:cursor (t/data env)) => 1))))

(specification "review chart — :yes"
  (component "marks cursor item :status/ready in state-map and advances cursor"
    (let [env (new-env)]
      (t/start! env)
      (seed-state! env state-prioritizable-3)
      (t/run-events! env sut/event-start)
      ;; cursor is now 1 (first :new after the :ready, id-2)
      (t/run-events! env sut/event-yes)
      (assertions
        "state-map shows id-2 promoted to :ready"
        (cursor-todo-status env id-2) => :status/ready
        "id-3 (next :new) stays :new"
        (cursor-todo-status env id-3) => :status/new
        "cursor advanced to 2 (id-3 position)"
        (:cursor (t/data env)) => 2
        "still in :active (more news remain)"
        (t/in? env sut/active) => true)))

  (component ":yes on the final :new walks off the end and auto-returns to :inactive"
    (let [env (new-env)]
      (t/start! env)
      (seed-state! env state-prioritizable-2)
      (t/run-events! env sut/event-start)
      ;; cursor 1, only one :new — Yes promotes it; next-cursor returns -1
      (t/run-events! env sut/event-yes)
      (assertions
        "id-2 promoted in state-map"
        (cursor-todo-status env id-2) => :status/ready
        "eventless transition fired — back in :inactive"
        (t/in? env sut/inactive) => true
        "cursor is -1 (review ended)"
        (:cursor (t/data env)) => -1))))

(specification "review chart — :no"
  (component "advances cursor without changing item status in state-map"
    (let [env (new-env)]
      (t/start! env)
      (seed-state! env state-prioritizable-3)
      (t/run-events! env sut/event-start)
      ;; cursor is 1 — Walk dog stays :new
      (t/run-events! env sut/event-no)
      (assertions
        "id-2 still :new in state-map"
        (cursor-todo-status env id-2) => :status/new
        "cursor advanced to id-3 position"
        (:cursor (t/data env)) => 2
        "still in :active"
        (t/in? env sut/active) => true)))

  (component ":no on the final :new walks off the end and auto-returns to :inactive"
    (let [env (new-env)]
      (t/start! env)
      (seed-state! env state-prioritizable-2)
      (t/run-events! env sut/event-start)
      (t/run-events! env sut/event-no)
      (assertions
        "id-2 stays :new in state-map (No doesn't promote)"
        (cursor-todo-status env id-2) => :status/new
        "back in :inactive"
        (t/in? env sut/inactive) => true
        "cursor -1"
        (:cursor (t/data env)) => -1))))

(specification "review chart — :quit"
  (component "returns to :inactive with cursor -1 regardless of remaining news"
    (let [env (new-env)]
      (t/start! env)
      (seed-state! env state-prioritizable-3)
      (t/run-events! env sut/event-start)
      ;; cursor 1, two :news remain
      (t/run-events! env sut/event-quit)
      (assertions
        "back in :inactive"
        (t/in? env sut/inactive) => true
        "cursor -1"
        (:cursor (t/data env)) => -1
        "state-map items untouched by :quit"
        (cursor-todo-status env id-2) => :status/new
        (cursor-todo-status env id-3) => :status/new))))
