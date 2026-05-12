(ns learn.client-test
  (:require
    [clojure.set]
    [fulcro-spec.core :refer [specification component assertions =>]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [learn.client :as sut]
    [learn.review.chart :as chart]
    [learn.server :as server]))

;; ============================================================================
;; Test fixtures
;; ============================================================================

;; UUIDs for unit-test fixtures. Distinct from server-id-* (integration
;; tests) to keep the two layers visually separable in failure output.
(def fixture-id-1 #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1")
(def fixture-id-2 #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2")

(defn fixture-state
  "Two seeded todos: fixture-id-1 (ready), fixture-id-2 (new)."
  []
  {:list/id {1 {:list/id          1
                :list/todos       [[:todo/id fixture-id-1] [:todo/id fixture-id-2]]
                :ui/new-todo-text "draft text"}}
   :todo/id {fixture-id-1 {:todo/id fixture-id-1 :todo/text "First"  :todo/status :status/ready}
             fixture-id-2 {:todo/id fixture-id-2 :todo/text "Second" :todo/status :status/new}}})

(defn empty-fixture-state
  "Empty list, no todos."
  []
  {:list/id {1 {:list/id 1 :list/todos [] :ui/new-todo-text ""}}
   :todo/id {}})

(defn affects-only?
  "True when `after` differs from `before` only at the given paths."
  [before after paths]
  (let [strip (fn [m] (reduce nsh/dissoc-in m paths))]
    (= (strip before) (strip after))))

(def server-id-1 #uuid "11111111-1111-1111-1111-111111111111")
(def server-id-2 #uuid "22222222-2222-2222-2222-222222222222")

;; ============================================================================
;; Pure helper specifications
;; ============================================================================

(specification "add-todo*"
  (component "into a populated list"
    (let [before     (fixture-state)
          after      (sut/add-todo* before [:list/id 1] "Third")
          new-ident  (last (get-in after [:list/id 1 :list/todos]))
          [_ new-id] new-ident]
      (assertions
        "stores the new text on the new entity"
        (get-in after [:todo/id new-id :todo/text]) => "Third"
        "starts the new todo at :status/new"
        (get-in after [:todo/id new-id :todo/status]) => :status/new
        "appends the new ident at the end of :list/todos"
        (last (get-in after [:list/id 1 :list/todos])) => new-ident
        "clears :ui/new-todo-text on the list"
        (get-in after [:list/id 1 :ui/new-todo-text]) => ""
        "affects only the new entity and the targeted list paths"
        (affects-only? before after
          [[:todo/id new-id]
           [:list/id 1 :list/todos]
           [:list/id 1 :ui/new-todo-text]])
        => true)))

  (component "into an empty list"
    (let [before     (empty-fixture-state)
          after      (sut/add-todo* before [:list/id 1] "First!")
          new-ident  (last (get-in after [:list/id 1 :list/todos]))
          [_ new-id] new-ident]
      (assertions
        "creates the first todo with the new text"
        (get-in after [:todo/id new-id :todo/text]) => "First!"
        "new todo gets :status/ready (empty list — no existing ready items)"
        (get-in after [:todo/id new-id :todo/status]) => :status/ready
        "places the new ident as the only entry in :list/todos"
        (get-in after [:list/id 1 :list/todos]) => [new-ident]
        "affects only the new entity and the list's :list/todos"
        (affects-only? before after
          [[:todo/id new-id]
           [:list/id 1 :list/todos]])
        => true))))

(specification "set-status*"
  (component "setting a non-cancelled status"
    (let [before (fixture-state)
          after  (sut/set-status* before fixture-id-1 :status/done)]
      (assertions
        "updates :todo/status on the targeted entity"
        (get-in after [:todo/id fixture-id-1 :todo/status]) => :status/done
        "does not set :todo/was for non-cancelled transitions"
        (contains? (get-in after [:todo/id fixture-id-1]) :todo/was) => false
        "affects only :todo/status of the targeted entity"
        (affects-only? before after
          [[:todo/id fixture-id-1 :todo/status]])
        => true)))

  (component "cancelling a :status/new todo"
    (let [before (fixture-state)
          after  (sut/set-status* before fixture-id-2 :status/cancelled)]
      (assertions
        ":todo/status becomes :status/cancelled"
        (get-in after [:todo/id fixture-id-2 :todo/status]) => :status/cancelled
        ":todo/was captures the previous :status/new"
        (get-in after [:todo/id fixture-id-2 :todo/was]) => :status/new)))

  (component "cancelling a :status/ready todo"
    (let [before (fixture-state)
          after  (sut/set-status* before fixture-id-1 :status/cancelled)]
      (assertions
        ":todo/status becomes :status/cancelled"
        (get-in after [:todo/id fixture-id-1 :todo/status]) => :status/cancelled
        ":todo/was captures the previous :status/ready"
        (get-in after [:todo/id fixture-id-1 :todo/was]) => :status/ready)))

  (component "double-cancel is idempotent — :todo/was is preserved"
    (let [once  (sut/set-status* (fixture-state) fixture-id-1 :status/cancelled)
          twice (sut/set-status* once fixture-id-1 :status/cancelled)]
      (assertions
        ":todo/was retains the original prior status, not :cancelled"
        (get-in twice [:todo/id fixture-id-1 :todo/was]) => :status/ready))))

(specification "delete-all*"
  (let [before (fixture-state)
        after  (sut/delete-all* before [:list/id 1])]
    (assertions
      "empties :list/todos at the given list"
      (get-in after [:list/id 1 :list/todos]) => []
      "removes every entity from the :todo/id table"
      (:todo/id after) => nil
      "affects only the :todo/id table and the list's :list/todos"
      (affects-only? before after
        [[:todo/id]
         [:list/id 1 :list/todos]])
      => true)))

;; ============================================================================
;; cancel-todo* / complete-benchmark-item* / clone-todo* — state-helpers that
;; delegate to learn.model.list for domain semantics. These specs verify the
;; *wiring* (denormalize → model → reproject), not the domain rules
;; (those are tested exhaustively in learn.model.list-test).
;; ============================================================================

(def missing-id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa9")

(specification "cancel-todo*"
  (component "refuses missing id — state unchanged"
    (let [before (fixture-state)
          after  (sut/cancel-todo* before [:list/id 1] missing-id)]
      (assertions
        "state map is unchanged on refusal"
        after => before)))

  (component "refuses :done item — state unchanged"
    (let [before (-> (fixture-state)
                   (assoc-in [:todo/id fixture-id-1 :todo/status] :status/done))
          after  (sut/cancel-todo* before [:list/id 1] fixture-id-1)]
      (assertions
        "state map is unchanged on refusal"
        after => before)))

  (component "cancels a :status/new todo (no auto-mark — :ready remains)"
    (let [before (fixture-state)
          after  (sut/cancel-todo* before [:list/id 1] fixture-id-2)]
      (assertions
        ":todo/status becomes :status/cancelled"
        (get-in after [:todo/id fixture-id-2 :todo/status]) => :status/cancelled
        ":todo/was captures the previous :status/new"
        (get-in after [:todo/id fixture-id-2 :todo/was]) => :status/new
        "the :ready item is unchanged"
        (get-in after [:todo/id fixture-id-1 :todo/status]) => :status/ready
        "affects only the cancelled entity"
        (affects-only? before after
          [[:todo/id fixture-id-2]
           [:list/id 1 :list/todos]])
        => true)))

  (component "cancels the sole :status/ready and auto-mark fires"
    ;; Fixture is [(id-1 :ready) (id-2 :new)]. Cancelling id-1 leaves no
    ;; :ready, so auto-mark promotes id-2 to :ready.
    (let [before (fixture-state)
          after  (sut/cancel-todo* before [:list/id 1] fixture-id-1)]
      (assertions
        "cancelled item :todo/status :cancelled"
        (get-in after [:todo/id fixture-id-1 :todo/status]) => :status/cancelled
        "cancelled item :todo/was captures the previous :ready"
        (get-in after [:todo/id fixture-id-1 :todo/was]) => :status/ready
        "auto-mark promotes the :new to :ready"
        (get-in after [:todo/id fixture-id-2 :todo/status]) => :status/ready
        ":ui/new-todo-text on the list is untouched"
        (get-in after [:list/id 1 :ui/new-todo-text]) => "draft text"))))

(specification "complete-benchmark-item*"
  (component "no actionable items — state unchanged"
    (let [before (-> (fixture-state)
                   (assoc-in [:todo/id fixture-id-1 :todo/status] :status/new))
          after  (sut/complete-benchmark-item* before [:list/id 1])]
      (assertions
        "state map is unchanged when no :ready exists"
        after => before)))

  (component "completes the sole :ready and auto-mark fires"
    ;; Fixture: [(id-1 :ready) (id-2 :new)]. Complete id-1 → :done; auto-mark
    ;; promotes id-2 → :ready.
    (let [before (fixture-state)
          after  (sut/complete-benchmark-item* before [:list/id 1])]
      (assertions
        "benchmark becomes :status/done"
        (get-in after [:todo/id fixture-id-1 :todo/status]) => :status/done
        "no :todo/was added on completion"
        (contains? (get-in after [:todo/id fixture-id-1]) :todo/was) => false
        "auto-mark promotes the :new to :ready"
        (get-in after [:todo/id fixture-id-2 :todo/status]) => :status/ready
        ":list/todos vector is unchanged in shape and order"
        (get-in after [:list/id 1 :list/todos])
        => [[:todo/id fixture-id-1] [:todo/id fixture-id-2]]))))

(specification "clone-todo*"
  (component "refuses missing id — state unchanged"
    (let [before (fixture-state)
          after  (sut/clone-todo* before [:list/id 1] missing-id)]
      (assertions
        "state map is unchanged on refusal"
        after => before)))

  (component "clones a :ready source — new entity inserted, source unchanged"
    (let [before     (fixture-state)
          after      (sut/clone-todo* before [:list/id 1] fixture-id-1)
          todo-ids   (set (keys (:todo/id after)))
          new-id     (first (clojure.set/difference todo-ids
                              #{fixture-id-1 fixture-id-2}))
          list-todos (get-in after [:list/id 1 :list/todos])]
      (assertions
        "the source todo is unchanged"
        (get-in after [:todo/id fixture-id-1])
        => (get-in before [:todo/id fixture-id-1])
        "a new todo with the source's text was added to the :todo/id table"
        (get-in after [:todo/id new-id :todo/text]) => "First"
        "clone has :status/new (a :ready already exists in the list)"
        (get-in after [:todo/id new-id :todo/status]) => :status/new
        "clone's ident is appended to :list/todos"
        (last list-todos) => [:todo/id new-id]
        ":list/todos grew by exactly one ident"
        (count list-todos) => 3)))

  (component "clones a :cancelled source — source's :todo/was preserved, clone has no :was"
    (let [before     (-> (fixture-state)
                       (update-in [:todo/id fixture-id-1] assoc
                         :todo/status :status/cancelled
                         :todo/was    :status/ready))
          after      (sut/clone-todo* before [:list/id 1] fixture-id-1)
          todo-ids   (set (keys (:todo/id after)))
          new-id     (first (clojure.set/difference todo-ids
                              #{fixture-id-1 fixture-id-2}))]
      (assertions
        "a new entity was added to :todo/id (anchor — fails under a no-op)"
        (some? new-id) => true
        "clone has the source's text"
        (get-in after [:todo/id new-id :todo/text]) => "First"
        "source :cancelled status preserved"
        (get-in after [:todo/id fixture-id-1 :todo/status]) => :status/cancelled
        "source :todo/was preserved"
        (get-in after [:todo/id fixture-id-1 :todo/was]) => :status/ready
        "clone has no :todo/was (fresh todo, never cancelled)"
        (contains? (get-in after [:todo/id new-id]) :todo/was) => false))))

;; ============================================================================
;; Integration specifications
;; ============================================================================

(specification "df/load! integration (via init)"
  (component "init triggers an initial load that populates :list/todos"
    (server/seed!)
    (let [spa (sut/init)
          db  (app/current-state spa)]
      (assertions
        "the server's todos appear in the :todo/id table"
        (contains? (:todo/id db) server-id-1) => true
        (contains? (:todo/id db) server-id-2) => true
        "the loaded idents replace :list/todos at the targeted path"
        (set (get-in db [:list/id 1 :list/todos]))
        => #{[:todo/id server-id-1] [:todo/id server-id-2]}
        "loaded entities have the expected text"
        (get-in db [:todo/id server-id-1 :todo/text]) => "Read the Fulcro book"
        "loaded entities have :todo/status from the server"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/ready
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/new))))

(specification "add-todo mutation (with :remote true)"
  (component "client-side add reaches the server and the client"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/type-into-labeled! spa "New TODO" "Pet the cat")
          _   (h/click-on-text! spa "Add")
          _   (h/render-frame! spa)]
      (assertions
        "the server's :todo/id table grew by one entry"
        (count (:todo/id @server/SERVER-DB)) => 3
        "the new entry on the server has the typed text"
        (some #(= "Pet the cat" (:todo/text %))
          (vals (:todo/id @server/SERVER-DB))) => true
        "the new entry on the server starts at :status/new"
        (some #(and (= "Pet the cat" (:todo/text %))
                 (= :status/new (:todo/status %)))
          (vals (:todo/id @server/SERVER-DB))) => true
        "the client also has a todo with that text"
        (some #(= "Pet the cat" (:todo/text %))
          (vals (:todo/id (app/current-state spa)))) => true))))

(specification "set-status mutation"
  (component "transitions a todo to the given status"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa
                [(sut/set-status {:todo/id server-id-2 :todo/status :status/done})])
          db  (app/current-state spa)]
      (assertions
        "the targeted todo now has the new status"
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/done
        "the other todo is unchanged"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/ready
        "no :todo/was was set (not a cancel transition)"
        (contains? (get-in db [:todo/id server-id-2]) :todo/was) => false))))

(specification "cancel-todo mutation"
  (component "cancels a :status/ready todo, capturing :todo/was"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa
                [(sut/cancel-todo {:todo/id server-id-1})])
          db  (app/current-state spa)]
      (assertions
        ":todo/status becomes :status/cancelled"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/cancelled
        ":todo/was captures the previous :status/ready"
        (get-in db [:todo/id server-id-1 :todo/was]) => :status/ready)))

  (component "cancels a :status/new todo, capturing :todo/was"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa
                [(sut/cancel-todo {:todo/id server-id-2})])
          db  (app/current-state spa)]
      (assertions
        ":todo/status becomes :status/cancelled"
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/cancelled
        ":todo/was captures the previous :status/new"
        (get-in db [:todo/id server-id-2 :todo/was]) => :status/new)))

  (component "auto-mark fires after cancelling the sole :ready"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa
                [(sut/cancel-todo {:todo/id server-id-1})])
          db  (app/current-state spa)]
      (assertions
        "server-id-1 (the sole :ready) becomes :cancelled"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/cancelled
        "server-id-2 (was :new) is auto-marked to :ready"
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/ready)))

  (component "the mutation persists through to SERVER-DB"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa
                [(sut/cancel-todo {:todo/id server-id-1})])]
      (assertions
        "server reflects the cancelled status"
        (get-in @server/SERVER-DB [:todo/id server-id-1 :todo/status])
        => :status/cancelled
        "server captures :todo/was"
        (get-in @server/SERVER-DB [:todo/id server-id-1 :todo/was])
        => :status/ready
        "server reflects the auto-mark on the other item"
        (get-in @server/SERVER-DB [:todo/id server-id-2 :todo/status])
        => :status/ready))))

(specification "complete-benchmark-item mutation"
  (component "completes the sole :ready and auto-marks the :new"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa [(sut/complete-benchmark-item {})])
          db  (app/current-state spa)]
      (assertions
        "server-id-1 (the benchmark) becomes :done"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/done
        "auto-mark promotes server-id-2 to :ready"
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/ready
        "no :todo/was set on the completed item"
        (contains? (get-in db [:todo/id server-id-1]) :todo/was) => false)))

  (component "no actionable items — state unchanged"
    (server/seed!)
    (let [spa     (sut/init)
          _       (comp/transact! spa
                    [(sut/set-status
                       {:todo/id server-id-1 :todo/status :status/new})])
          before  (app/current-state spa)
          _       (comp/transact! spa [(sut/complete-benchmark-item {})])
          after   (app/current-state spa)]
      (assertions
        "state is unchanged on refusal"
        after => before)))

  (component "the mutation persists through to SERVER-DB"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa [(sut/complete-benchmark-item {})])]
      (assertions
        "server reflects the completion"
        (get-in @server/SERVER-DB [:todo/id server-id-1 :todo/status])
        => :status/done
        "server reflects the auto-mark"
        (get-in @server/SERVER-DB [:todo/id server-id-2 :todo/status])
        => :status/ready))))

(specification "clone-todo mutation"
  (component "clones a todo, appending a new entity to :todo/id and :list/todos"
    (server/seed!)
    (let [spa            (sut/init)
          before-todo-ids (set (keys (:todo/id (app/current-state spa))))
          _              (comp/transact! spa
                           [(sut/clone-todo {:todo/id server-id-1})])
          db             (app/current-state spa)
          after-todo-ids (set (keys (:todo/id db)))
          new-id         (first (clojure.set/difference
                                  after-todo-ids before-todo-ids))]
      (assertions
        "exactly one new entity in :todo/id table"
        (count after-todo-ids) => 3
        "clone has the source's text"
        (get-in db [:todo/id new-id :todo/text])
        => (get-in db [:todo/id server-id-1 :todo/text])
        "clone's ident appended to :list/todos"
        (last (get-in db [:list/id 1 :list/todos])) => [:todo/id new-id]
        "source is unchanged"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/ready
        "clone has :status/new (a :ready exists in the list)"
        (get-in db [:todo/id new-id :todo/status]) => :status/new)))

  (component "the mutation persists through to SERVER-DB (same UUID on both sides)"
    (server/seed!)
    (let [spa             (sut/init)
          before-server   (set (keys (:todo/id @server/SERVER-DB)))
          _               (comp/transact! spa
                            [(sut/clone-todo {:todo/id server-id-1})])
          client-ids      (set (keys (:todo/id (app/current-state spa))))
          server-ids      (set (keys (:todo/id @server/SERVER-DB)))
          server-clone-id (first (clojure.set/difference server-ids before-server))]
      (assertions
        "server's :todo/id table grew by exactly one entry"
        (count server-ids) => 3
        "the clone UUID is the same on client and server"
        client-ids => server-ids
        "server has the clone's text"
        (get-in @server/SERVER-DB [:todo/id server-clone-id :todo/text])
        => "Read the Fulcro book"
        "server's :list/todos has the clone id at the end"
        (last (get-in @server/SERVER-DB [:list/id 1 :list/todos]))
        => server-clone-id))))

(specification "delete-all mutation"
  (component "removes every todo referenced by the list"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa [(sut/delete-all)] {:ref [:list/id 1]})
          db  (app/current-state spa)]
      (assertions
        ":list/todos is empty"
        (get-in db [:list/id 1 :list/todos]) => []
        "no loaded entities remain in the :todo/id table"
        (contains? (:todo/id db) server-id-1) => false
        (contains? (:todo/id db) server-id-2) => false))))

;; ============================================================================
;; Review chart wiring — init installs the statechart support, registers the
;; chart, and starts a singleton session at `:review-session`. The chart's
;; expression-fns read items from `(:fulcro/state-map data)` at [:list/id 1]
;; and mutate it directly via path-assigns, so this spec proves the round-trip
;; through Fulcro's normalized state works the same as the chart-only unit
;; tests in `learn.review.chart-test`.
;;
;; We install with `:event-loop? false` and pump via `scf/process-events!` to
;; keep these tests deterministic (same pattern the scf install! docstring
;; recommends).
;; ============================================================================

(defn- pump! [spa]
  (scf/process-events! spa))

(specification "review chart wiring via init"
  (component "init starts a session at :review-session in :inactive"
    (server/seed!)
    (let [spa    (sut/init)
          config (scf/current-configuration spa sut/review-session-id)]
      (assertions
        "chart session exists and contains :review.state/inactive"
        (contains? config chart/inactive) => true
        ":active is not yet entered"
        (contains? config chart/active) => false)))

  (component ":event.review/start enters :active when the loaded list is prioritizable"
    (server/seed!)
    (let [spa (sut/init)]
      (scf/send! spa sut/review-session-id chart/event-start)
      (pump! spa)
      (let [config (scf/current-configuration spa sut/review-session-id)]
        (assertions
          "chart is now in :review.state/active"
          (contains? config chart/active) => true
          ":inactive is no longer in the configuration"
          (contains? config chart/inactive) => false))))

  (component ":event.review/yes promotes the cursor todo to :status/ready in the Fulcro state-map"
    (server/seed!)
    (let [spa (sut/init)]
      (scf/send! spa sut/review-session-id chart/event-start)
      (pump! spa)
      (scf/send! spa sut/review-session-id chart/event-yes)
      (pump! spa)
      (let [db (app/current-state spa)]
        (assertions
          "server-id-2 (sole :new) is now :ready in client state"
          (get-in db [:todo/id server-id-2 :todo/status]) => :status/ready
          "server-id-1 (:ready) is unchanged"
          (get-in db [:todo/id server-id-1 :todo/status]) => :status/ready
          "single-:new walk-off-end returns the chart to :inactive"
          (contains? (scf/current-configuration spa sut/review-session-id) chart/inactive)
          => true))))

  (component ":event.review/quit returns to :inactive without mutating todos"
    (server/seed!)
    (let [spa (sut/init)]
      (scf/send! spa sut/review-session-id chart/event-start)
      (pump! spa)
      (scf/send! spa sut/review-session-id chart/event-quit)
      (pump! spa)
      (let [db (app/current-state spa)]
        (assertions
          "chart is back in :inactive"
          (contains? (scf/current-configuration spa sut/review-session-id) chart/inactive)
          => true
          "todos in state-map are unchanged"
          (get-in db [:todo/id server-id-1 :todo/status]) => :status/ready
          (get-in db [:todo/id server-id-2 :todo/status]) => :status/new)))))

;; ============================================================================
;; Review UI affordances — Start/Yes/No/Quit buttons + current question.
;; The buttons dispatch chart events directly via `scf/send!` (no thin mutation
;; wrappers). The current question is rendered as a sibling of the buttons
;; while the chart is in :active.
;; ============================================================================

(specification "review UI affordances"
  (component "while :inactive: Start Review button is visible; Yes/No/Quit are not"
    (server/seed!)
    (let [spa (sut/init)]
      (assertions
        "the 'Start Review' button is visible"
        (h/text-exists? spa "Start Review") => true
        "no review-question prompt is rendered"
        (h/text-exists? spa "are you more ready to") => false
        "no Yes button while :inactive"
        (h/text-exists? spa "Yes") => false
        "no Quit button while :inactive"
        (h/text-exists? spa "Quit") => false)))

  (component "clicking 'Start Review' enters :active and renders the question + Yes/No/Quit"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Start Review")
          _   (h/render-frame! spa)]
      (assertions
        "chart is in :active"
        (contains? (scf/current-configuration spa sut/review-session-id) chart/active)
        => true
        "current-question text is now rendered"
        (h/text-exists? spa "are you more ready to") => true
        "Yes button is visible"
        (h/text-exists? spa "Yes") => true
        "No button is visible"
        (h/text-exists? spa "No") => true
        "Quit button is visible"
        (h/text-exists? spa "Quit") => true
        "'Start Review' is no longer visible"
        (h/text-exists? spa "Start Review") => false)))

  (component "clicking 'Yes' promotes the cursor todo to :ready in the state-map"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Start Review")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Yes")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        "the sole :new (server-id-2) is now :status/ready in the client state"
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/ready
        "after walking off the end, chart returns to :inactive"
        (contains? (scf/current-configuration spa sut/review-session-id) chart/inactive)
        => true
        "'Start Review' is visible again after the chart returns to :inactive"
        (h/text-exists? spa "Start Review") => true)))

  (component "clicking 'Quit' returns to :inactive without mutating todos"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Start Review")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Quit")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        "chart is :inactive"
        (contains? (scf/current-configuration spa sut/review-session-id) chart/inactive)
        => true
        "server-id-1 is unchanged (:ready)"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/ready
        "server-id-2 is unchanged (:new)"
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/new
        "'Start Review' button is visible again"
        (h/text-exists? spa "Start Review") => true))))

;; ============================================================================
;; 5K.6 — Review chart state-map mutations are synced to SERVER-DB.
;;
;; The chart's :yes action mutates Fulcro state-map via path-assign; on its
;; own, that change wouldn't reach the server. Phase 5K.6 fires a remote
;; `sync-list` mutation from the chart so the server records the post-:yes
;; items vector. :no and :quit don't mutate the list, so they don't sync.
;; ============================================================================

(specification "review chart syncs Yes decisions to the server"
  (component ":yes persists the cursor promotion to SERVER-DB"
    (server/seed!)
    (let [spa (sut/init)]
      (scf/send! spa sut/review-session-id chart/event-start)
      (pump! spa)
      (scf/send! spa sut/review-session-id chart/event-yes)
      (pump! spa)
      (assertions
        "client shows server-id-2 promoted to :ready (already covered by 5K.5)"
        (get-in (app/current-state spa) [:todo/id server-id-2 :todo/status])
        => :status/ready
        "SERVER-DB also shows server-id-2 as :ready (the 5K.6 sync)"
        (get-in @server/SERVER-DB [:todo/id server-id-2 :todo/status])
        => :status/ready
        "SERVER-DB's list order is preserved"
        (get-in @server/SERVER-DB [:list/id 1 :list/todos])
        => [server-id-1 server-id-2])))

  (component ":no does not sync (no state-map mutation to persist)"
    (server/seed!)
    (let [spa (sut/init)]
      (scf/send! spa sut/review-session-id chart/event-start)
      (pump! spa)
      (scf/send! spa sut/review-session-id chart/event-no)
      (pump! spa)
      (assertions
        "SERVER-DB is unchanged — server-id-2 stays :new"
        (get-in @server/SERVER-DB [:todo/id server-id-2 :todo/status])
        => :status/new)))

  (component ":quit does not sync"
    (server/seed!)
    (let [spa (sut/init)]
      (scf/send! spa sut/review-session-id chart/event-start)
      (pump! spa)
      (scf/send! spa sut/review-session-id chart/event-quit)
      (pump! spa)
      (assertions
        "SERVER-DB is unchanged"
        (get-in @server/SERVER-DB [:todo/id server-id-1 :todo/status]) => :status/ready
        (get-in @server/SERVER-DB [:todo/id server-id-2 :todo/status]) => :status/new))))
