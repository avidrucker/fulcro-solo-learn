(ns learn.review.chart-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [com.fulcrologic.statecharts.testing :as t]
    [taoensso.timbre :as log]
    [learn.review.chart :as sut]))

;; Silence the statecharts library's verbose DEBUG logging during tests —
;; the chart implementation already exercises it via run-events!.
(log/merge-config! {:min-level :warn})

;; ============================================================================
;; Test fixtures
;; ============================================================================

(def id-1 #uuid "11111111-1111-1111-1111-111111111111")
(def id-2 #uuid "22222222-2222-2222-2222-222222222222")
(def id-3 #uuid "33333333-3333-3333-3333-333333333333")

(defn todo
  ([id text]        (todo id text :status/new))
  ([id text status] {:todo/id id :todo/text text :todo/status status}))

(def prioritizable-2
  "Smallest prioritizable list: one :ready followed by one :new."
  [(todo id-1 "Read book" :status/ready)
   (todo id-2 "Walk dog"  :status/new)])

(def prioritizable-3
  "Prioritizable list with two :new candidates after the benchmark."
  [(todo id-1 "Read book"  :status/ready)
   (todo id-2 "Walk dog"   :status/new)
   (todo id-3 "Call Alice" :status/new)])

(def non-prioritizable
  "Last :new is before last :ready — fails the SCHEMA.md §15 rule."
  [(todo id-1 "Stale new" :status/new)
   (todo id-2 "Anchor"    :status/ready)])

(defn new-env
  "Builds a testing env that actually runs the chart's guards and actions.
   Without `:run-unmocked? true`, expression fns default to mocked no-ops
   so guards always return nil/false and transitions don't fire."
  []
  (t/new-testing-env {:statechart      sut/chart
                      :mocking-options {:run-unmocked? true}}
    {}))

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
  (component "non-prioritizable items — guard rejects, stays inactive"
    (let [env (new-env)]
      (t/start! env)
      (t/run-events! env {:name sut/event-start
                          :data {:items non-prioritizable}})
      (assertions
        "still in :inactive"
        (t/in? env sut/inactive) => true
        "no items stored in chart data"
        (:items (t/data env)) => nil)))

  (component "prioritizable items — transitions to :active and seeds data"
    (let [env (new-env)]
      (t/start! env)
      (t/run-events! env {:name sut/event-start
                          :data {:items prioritizable-2}})
      (assertions
        "in :active"
        (t/in? env sut/active) => true
        "chart data carries the items"
        (:items (t/data env)) => prioritizable-2
        "cursor is initial-cursor (1 for [:ready :new])"
        (:cursor (t/data env)) => 1))))

(specification "review chart — :yes"
  (component "marks cursor item :status/ready and advances cursor"
    (let [env (new-env)]
      (t/start! env)
      (t/run-events! env {:name sut/event-start
                          :data {:items prioritizable-3}})
      ;; cursor is now 1 (first :new after the :ready)
      (t/run-events! env sut/event-yes)
      (let [data (t/data env)]
        (assertions
          "cursor item (id-2) was promoted to :ready"
          (get-in (:items data) [1 :todo/status]) => :status/ready
          "cursor advanced to the next :new (id-3 at idx 2)"
          (:cursor data) => 2
          "still in :active (more news remain)"
          (t/in? env sut/active) => true))))

  (component ":yes on the final :new walks off the end and auto-returns to :inactive"
    (let [env (new-env)]
      (t/start! env)
      (t/run-events! env {:name sut/event-start
                          :data {:items prioritizable-2}})
      ;; cursor 1, only one :new — Yes promotes it; next-cursor returns -1
      (t/run-events! env sut/event-yes)
      (assertions
        "eventless transition fired — back in :inactive"
        (t/in? env sut/inactive) => true
        "cursor is -1 (review ended)"
        (:cursor (t/data env)) => -1))))

(specification "review chart — :no"
  (component "advances cursor without changing item status"
    (let [env (new-env)]
      (t/start! env)
      (t/run-events! env {:name sut/event-start
                          :data {:items prioritizable-3}})
      ;; cursor is 1 — Walk dog stays :new
      (t/run-events! env sut/event-no)
      (let [data (t/data env)]
        (assertions
          "cursor item still :new (id-2)"
          (get-in (:items data) [1 :todo/status]) => :status/new
          "cursor advanced to id-3"
          (:cursor data) => 2
          "still in :active"
          (t/in? env sut/active) => true))))

  (component ":no on the final :new walks off the end and auto-returns to :inactive"
    (let [env (new-env)]
      (t/start! env)
      (t/run-events! env {:name sut/event-start
                          :data {:items prioritizable-2}})
      (t/run-events! env sut/event-no)
      (assertions
        "back in :inactive"
        (t/in? env sut/inactive) => true
        "cursor -1"
        (:cursor (t/data env)) => -1))))

(specification "review chart — :quit"
  (component "returns to :inactive with cursor -1 regardless of remaining news"
    (let [env (new-env)]
      (t/start! env)
      (t/run-events! env {:name sut/event-start
                          :data {:items prioritizable-3}})
      ;; cursor 1, two :news remain
      (t/run-events! env sut/event-quit)
      (assertions
        "back in :inactive"
        (t/in? env sut/inactive) => true
        "cursor -1"
        (:cursor (t/data env)) => -1))))
