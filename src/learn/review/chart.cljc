(ns learn.review.chart
  "Statechart orchestrating the AutoFocus review/prioritize flow.

   States:
     :review.state/inactive — no review in progress (initial)
     :review.state/active   — review running; chart data holds :items + :cursor

   Events:
     :event.review/start  (data {:items …})  — guard `prioritizable?`
     :event.review/yes                       — mark cursor :ready, advance
     :event.review/no                        — advance cursor only
     :event.review/quit                      — back to :inactive

   The Yes/No handlers don't branch to :inactive themselves; an eventless
   transition in :active fires when `:cursor` becomes -1 and pops the chart
   back to :inactive. SCXML idiomatic.

   CLJ-only for now: chart owns its copy of items during a session. Phase
   5K.5 will replace expression-fn bodies with Fulcro alias reads so the
   chart drives the live app state instead."
  (:require
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.convenience :refer [handle]]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :refer [state transition script]]
    [learn.model.review :as review]))

;; State IDs
(def inactive :review.state/inactive)
(def active   :review.state/active)

;; Event names
(def event-start :event.review/start)
(def event-yes   :event.review/yes)
(def event-no    :event.review/no)
(def event-quit  :event.review/quit)

;; ----------------------------------------------------------------------
;; Expression functions — all pure, all take [env data].
;; ----------------------------------------------------------------------

(defn- start-guard
  "True iff the :start event carries a prioritizable items vector."
  [_env data]
  (review/prioritizable? (-> data :_event :data :items)))

(defn- start-action
  "Seed chart data from the :start event: copy items in and compute the
   initial cursor via `model.review/initial-cursor`."
  [_env data]
  (let [items  (-> data :_event :data :items)
        cursor (review/initial-cursor items)]
    [(ops/assign :items items)
     (ops/assign :cursor cursor)]))

(defn- yes-action
  "Mark the cursor item :status/ready and advance the cursor."
  [_env data]
  (let [{:keys [items cursor]} data
        items'  (assoc-in items [cursor :todo/status] :status/ready)
        cursor' (review/next-cursor items' (inc cursor))]
    [(ops/assign :items items')
     (ops/assign :cursor cursor')]))

(defn- no-action
  "Advance the cursor without changing items."
  [_env data]
  (let [{:keys [items cursor]} data
        cursor' (review/next-cursor items (inc cursor))]
    [(ops/assign :cursor cursor')]))

(defn- quit-action
  "Reset the cursor to -1 to mark the review inactive."
  [_env _data]
  [(ops/assign :cursor -1)])

(defn- cursor-invalid?
  "Guard for the eventless auto-exit transition in :active."
  [_env data]
  (= -1 (:cursor data)))

;; ----------------------------------------------------------------------
;; Chart definition
;; ----------------------------------------------------------------------

(def chart
  (statechart {:initial inactive}
    (state {:id inactive}
      (transition {:event  event-start
                   :cond   start-guard
                   :target active}
        (script {:expr start-action})))

    (state {:id active}
      ;; Eventless transition: when Yes/No advances cursor past the last
      ;; :new (next-cursor returns -1), pop the chart back to :inactive.
      (transition {:cond cursor-invalid? :target inactive})

      (handle event-yes yes-action)
      (handle event-no  no-action)

      (transition {:event event-quit :target inactive}
        (script {:expr quit-action})))))
