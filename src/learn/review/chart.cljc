(ns learn.review.chart
  "Statechart orchestrating the AutoFocus review/prioritize flow.

   States:
     :review.state/inactive — no review in progress (initial)
     :review.state/active   — review running; chart data holds :cursor

   Events:
     :event.review/start                     — guard `prioritizable?` reads state-map
     :event.review/yes                       — mark cursor :ready, advance
     :event.review/no                        — advance cursor only
     :event.review/quit                      — back to :inactive

   The Yes/No handlers don't branch to :inactive themselves; an eventless
   transition in :active fires when `:cursor` becomes -1 and pops the chart
   back to :inactive. SCXML idiomatic.

   The chart reads items from `(:fulcro/state-map data)` at `[:list/id 1]`
   (the singleton list ident) via `learn.util.normalized/denormalize-list-items`.
   `:yes` mutates the state-map directly via a path-based `ops/assign`. The
   chart's only session-local datum is `:cursor`."
  (:require
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.convenience :refer [handle]]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :refer [state transition script]]
    [learn.model.review :as review]
    [learn.util.normalized :as norm]))

;; State IDs
(def inactive :review.state/inactive)
(def active   :review.state/active)

;; Event names
(def event-start :event.review/start)
(def event-yes   :event.review/yes)
(def event-no    :event.review/no)
(def event-quit  :event.review/quit)

;; The chart operates on the singleton list (Phase 5J/5K design).
(def list-ident [:list/id 1])

(defn- items*
  "Denormalize the chart's working list from the seeded state-map."
  [data]
  (norm/denormalize-list-items (:fulcro/state-map data) list-ident))

;; ----------------------------------------------------------------------
;; Expression functions — all pure, all take [env data].
;; ----------------------------------------------------------------------

(defn- start-guard
  "True iff the seeded state-map's list at [:list/id 1] is prioritizable."
  [_env data]
  (review/prioritizable? (items* data)))

(defn- start-action
  "Compute the initial cursor from the seeded list."
  [_env data]
  [(ops/assign :cursor (review/initial-cursor (items* data)))])

(defn- yes-action
  "Mark the cursor item :status/ready in state-map and advance the cursor."
  [_env data]
  (let [{:keys [cursor]} data
        items   (items* data)
        id      (:todo/id (nth items cursor))
        items'  (assoc-in items [cursor :todo/status] :status/ready)
        cursor' (review/next-cursor items' (inc cursor))]
    [(ops/assign [:fulcro/state-map :todo/id id :todo/status] :status/ready)
     (ops/assign :cursor cursor')]))

(defn- no-action
  "Advance the cursor without changing item status."
  [_env data]
  (let [cursor  (:cursor data)
        cursor' (review/next-cursor (items* data) (inc cursor))]
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
