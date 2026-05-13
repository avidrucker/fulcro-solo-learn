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

(specification "import-from-text*"
  ;; Wiring spec for the Phase 7.12 batch-import state-helper:
  ;; denormalize → model.list/import-from-string → sync-items back.
  ;; Domain rules are covered exhaustively in `model.list-test:import-from-string`.
  (component "blank text — state unchanged"
    (let [before (fixture-state)]
      (assertions
        "empty string is a no-op"
        (sut/import-from-text* before [:list/id 1] "") => before
        "whitespace-only string is a no-op"
        (sut/import-from-text* before [:list/id 1] "  \n\t ") => before)))

  (component "multi-line input — appends every non-blank line"
    (let [before (fixture-state)
          after  (sut/import-from-text* before [:list/id 1] "alpha\nbeta\ngamma")
          new-idents (vec (drop 2 (get-in after [:list/id 1 :list/todos])))]
      (assertions
        ":list/todos length grew by 3 (two seed + three new)"
        (count (get-in after [:list/id 1 :list/todos])) => 5
        "first two idents preserved at the head (existing items unchanged)"
        (vec (take 2 (get-in after [:list/id 1 :list/todos])))
        => [[:todo/id fixture-id-1] [:todo/id fixture-id-2]]
        "new entities are reachable via the appended idents"
        (mapv #(get-in after [:todo/id (second %) :todo/text]) new-idents)
        => ["alpha" "beta" "gamma"]
        "all new todos are :status/new (fixture has a :ready already)"
        (every? #{:status/new}
          (mapv #(get-in after [:todo/id (second %) :todo/status]) new-idents))
        => true)))

  (component "blank lines mixed with content are skipped"
    (let [before (empty-fixture-state)
          after  (sut/import-from-text* before [:list/id 1] "a\n\nb\n   \nc")
          idents (get-in after [:list/id 1 :list/todos])]
      (assertions
        "only the 3 non-blank lines became todos"
        (count idents) => 3
        "texts preserved in order"
        (mapv #(get-in after [:todo/id (second %) :todo/text]) idents)
        => ["a" "b" "c"]
        "first into empty list gets :status/ready; rest :status/new"
        (mapv #(get-in after [:todo/id (second %) :todo/status]) idents)
        => [:status/ready :status/new :status/new]))))

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
          _   (h/click-on-text! spa "Add Item")
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

(specification "Save modal — batch import textarea flow"
  ;; UI wiring spec for the Phase 7.12 save-modal textarea. The mutation
  ;; spec below covers the data path; this one proves the click-to-mutation
  ;; chain works through the open-modal state + the (clip-hidden) label
  ;; wiring used by `h/type-into-labeled!`.
  (component "type into textarea + click Submit imports the lines"
    (server/seed!)
    (let [spa (sut/init)
          ;; Open the Save modal via the disk icon's accessible name.
          _   (h/click-on-text! spa "Import/Export")
          _   (h/render-frame! spa)
          ;; `h/type-into-labeled!` only finds <input> tags — use the
          ;; id-based `type-into!` for the textarea.
          _   (h/type-into! spa sut/textarea-import-id "foo\nbar\nbaz")
          ;; `s/save-info-2` contains the word "Submit" in its body, so the
          ;; button is the SECOND match — pass index 1.
          _   (h/click-on-text! spa "Submit" 1)
          _   (h/render-frame! spa)
          db  (app/current-state spa)
          new-ids (mapv second (drop 2 (get-in db [:list/id 1 :list/todos])))]
      (assertions
        "list grew by 3 (2 seeded + 3 imported)"
        (count (get-in db [:list/id 1 :list/todos])) => 5
        "imported texts in line order"
        (mapv #(get-in db [:todo/id % :todo/text]) new-ids)
        => ["foo" "bar" "baz"]
        ":ui/textarea-import-text cleared after successful Submit"
        (get-in db [:list/id 1 :ui/textarea-import-text]) => ""
        ;; B-2 fix: modal stays open after a successful import so the
        ;; user can verify the new items or paste a second batch. Auto-
        ;; close is tracked as a future idea in `docs/ideas.md`.
        "modal STAYS OPEN after successful Submit"
        (get-in db [:list/id 1 :ui/open-modal]) => :save
        ":ui/err-msg cleared after successful Submit"
        (get-in db [:list/id 1 :ui/err-msg]) => nil)))

  (component "Submit on blank textarea surfaces empty-textarea-err"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Import/Export")
          _   (h/render-frame! spa)
          ;; Don't type anything — Submit on the blank default.
          ;; `s/save-info-2` contains the word "Submit" in its body, so the
          ;; button is the SECOND match — pass index 1.
          _   (h/click-on-text! spa "Submit" 1)
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        "list unchanged at 2 seeded items"
        (count (get-in db [:list/id 1 :list/todos])) => 2
        ":ui/err-msg surfaces the empty-textarea error string"
        (get-in db [:list/id 1 :ui/err-msg])
        => "New items cannot be empty or whitespace only."
        "modal stays open so the user can correct"
        (get-in db [:list/id 1 :ui/open-modal]) => :save))))

(specification "import-from-text mutation"
  ;; End-to-end: client transact runs the helper AND syncs to SERVER-DB
  ;; via the remote. Two seeded items + three imported lines = 5 total.
  (component "appends batch items on client AND SERVER-DB"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa
                [(sut/import-from-text {:ui/textarea-import-text "x\ny\nz"})]
                {:ref [:list/id 1]})
          db  (app/current-state spa)
          client-idents (get-in db [:list/id 1 :list/todos])
          new-client-ids (mapv second (drop 2 client-idents))]
      (assertions
        "client :list/todos length is 5 (2 seeded + 3 imported)"
        (count client-idents) => 5
        "client texts for the new idents in import order"
        (mapv #(get-in db [:todo/id % :todo/text]) new-client-ids)
        => ["x" "y" "z"]
        "SERVER-DB :list/todos length is 5"
        (count (get-in @server/SERVER-DB [:list/id 1 :list/todos])) => 5
        "SERVER-DB and client share the same UUIDs for the new items"
        (set (map second client-idents))
        => (set (get-in @server/SERVER-DB [:list/id 1 :list/todos])))))

  (component "blank input is a no-op (model refuses :error/empty-import)"
    (server/seed!)
    (let [spa (sut/init)
          _   (comp/transact! spa
                [(sut/import-from-text {:ui/textarea-import-text "\n  \t\n"})]
                {:ref [:list/id 1]})
          db  (app/current-state spa)]
      (assertions
        "client :list/todos unchanged at 2 seeded items"
        (count (get-in db [:list/id 1 :list/todos])) => 2))))

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
        (h/text-exists? spa "Prioritize") => true
        "no review-question prompt is rendered"
        (h/text-exists? spa "are you more ready to") => false
        "no Yes button while :inactive"
        (h/text-exists? spa "Yes") => false
        "no Quit button while :inactive"
        (h/text-exists? spa "Quit") => false)))

  (component "clicking 'Start Review' enters :active and renders the question + Yes/No/Quit"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Prioritize")
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
        ;; 6.5.3 behavior change: the Prioritize button stays in the
        ;; layout while reviewing (dimmed/disabled, matching the JS
        ;; port). Earlier code swapped it out; the new code mirrors the
        ;; JS app's always-show-but-disable pattern.
        "Prioritize button stays rendered (disabled, dimmed) during review"
        (h/text-exists? spa "Prioritize") => true)))

  (component "clicking 'Yes' promotes the cursor todo to :ready in the state-map"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Prioritize")
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
        (h/text-exists? spa "Prioritize") => true)))

  (component "clicking 'Quit' returns to :inactive without mutating todos"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Prioritize")
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
        (h/text-exists? spa "Prioritize") => true))))

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

;; ============================================================================
;; Phase 7.3 — Delete List + Mark Done button affordances.
;;
;; Verifies the click-through path for the two new primary buttons added in
;; 7.3. Refocus-after-delete and Enter-to-submit are browser-manual (headless
;; lacks DOM-focus tracking and key-press simulation in this library) — they
;; are covered by `docs/snapshots/<phase-7.3>*.png` and the user-story doc.
;; ============================================================================

(specification "Delete List button"
  (component "renders at default state with todos present"
    (server/seed!)
    (let [spa (sut/init)]
      (assertions
        "'Delete List' button text is visible"
        (h/text-exists? spa "Delete List") => true)))

  ;; Phase 7.12: Delete List on a non-empty list no longer empties
  ;; immediately — it opens a confirm modal. The "click → Yes" path is
  ;; what now matches the prior 7.3 behavior.
  (component "clicking 'Delete List' then 'Yes' empties the list on client AND server"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Yes")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        "client :list/todos is empty after the Yes click"
        (get-in db [:list/id 1 :list/todos]) => []
        "SERVER-DB :list/todos is empty too (delete-all has a remote in 7.3)"
        (get-in @server/SERVER-DB [:list/id 1 :list/todos]) => []
        "delete-confirm modal closed after Yes"
        (get-in db [:list/id 1 :ui/open-modal]) => :none
        "list-count footer pluralizes correctly: 'You have 0 items in your list.'"
        (h/text-exists? spa "You have 0 items in your list.") => true))))

;; ============================================================================
;; Phase 7.12 — Delete-list confirmation modal.
;;
;; Matches the JS port's UX: clicking Delete List on a non-empty list
;; opens a confirm modal instead of acting immediately. Yes empties +
;; closes; No just closes. Empty-list clicks still go straight to the
;; "nothing to delete" error and do NOT open the modal (per the JS
;; port — the modal would be a confusing no-op).
;; ============================================================================

(specification "Delete-confirm modal — opens via Delete List click"
  (component "clicking 'Delete List' on a non-empty list opens :delete-confirm modal"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        ":ui/open-modal flipped to :delete-confirm"
        (get-in db [:list/id 1 :ui/open-modal]) => :delete-confirm
        "list is NOT yet emptied — Yes is the commit step"
        (count (get-in db [:list/id 1 :list/todos])) => 2
        "SERVER-DB is also untouched until Yes"
        (count (get-in @server/SERVER-DB [:list/id 1 :list/todos])) => 2
        "modal body text is visible"
        (h/text-exists? spa
          "Are you sure you want to delete your list? This action cannot be undone.")
        => true
        "Yes and No buttons are visible"
        (h/text-exists? spa "Yes") => true
        (h/text-exists? spa "No")  => true)))

  (component "clicking 'Delete List' on an EMPTY list does NOT open the confirm modal"
    (server/seed!)
    (let [spa (sut/init)
          ;; First Delete List click opens the modal; click Yes to actually empty.
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Yes")
          _   (h/render-frame! spa)
          ;; Now empty — click Delete List again.
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        ":ui/open-modal stays at :none on empty list"
        (get-in db [:list/id 1 :ui/open-modal]) => :none
        ":ui/err-msg surfaces the nothing-to-delete error (existing 7.9 path)"
        (get-in db [:list/id 1 :ui/err-msg])
        => "There is nothing to delete."))))

(specification "Delete-confirm modal — Yes commits, No cancels"
  (component "clicking 'Yes' empties the list and closes the modal"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          ;; Pin the intermediate (post-Delete-List, pre-Yes) state so the
          ;; assertions can't pass via the old "Delete List empties
          ;; immediately" path.
          mid (app/current-state spa)
          _   (h/click-on-text! spa "Yes")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        "intermediate: modal is open after first click, list still populated"
        (get-in mid [:list/id 1 :ui/open-modal]) => :delete-confirm
        (count (get-in mid [:list/id 1 :list/todos])) => 2
        "post-Yes: list emptied on client"
        (get-in db [:list/id 1 :list/todos]) => []
        "post-Yes: list emptied on server"
        (get-in @server/SERVER-DB [:list/id 1 :list/todos]) => []
        "post-Yes: modal closed"
        (get-in db [:list/id 1 :ui/open-modal]) => :none
        "post-Yes: prior error message cleared"
        (get-in db [:list/id 1 :ui/err-msg]) => nil)))

  (component "clicking 'No' leaves the list untouched and closes the modal"
    (server/seed!)
    (let [spa (sut/init)
          ;; Capture todos BEFORE Delete List click so the assertion can't
          ;; coincidentally pass if Delete List were to still empty the
          ;; list immediately. Client side carries idents
          ;; (`[[:todo/id uuid] ...]`); SERVER-DB carries bare UUIDs.
          before-client-todos (get-in (app/current-state spa) [:list/id 1 :list/todos])
          before-server-todos (get-in @server/SERVER-DB [:list/id 1 :list/todos])
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "No")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        "list was populated before any click (sanity: 2 items)"
        (count before-client-todos) => 2
        "modal closed by No"
        (get-in db [:list/id 1 :ui/open-modal]) => :none
        "client list is unchanged (still has the original 2 items)"
        (get-in db [:list/id 1 :list/todos]) => before-client-todos
        "SERVER-DB is unchanged (No had no remote — delete-all was never run)"
        (get-in @server/SERVER-DB [:list/id 1 :list/todos]) => before-server-todos))))

(specification "Mark Done button"
  (component "renders at default state with an actionable list"
    (server/seed!)
    (let [spa (sut/init)]
      (assertions
        "'Mark Done' button text is visible"
        (h/text-exists? spa "Mark Done") => true)))

  (component "clicking 'Mark Done' completes the benchmark and auto-marks the next :new"
    ;; Fixture: server-id-1 is :ready (benchmark), server-id-2 is :new.
    ;; Mark Done flips id-1 to :done; auto-mark promotes id-2 to :ready.
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Mark Done")
          _   (h/render-frame! spa)
          db  (app/current-state spa)]
      (assertions
        "server-id-1 (former benchmark) is now :status/done"
        (get-in db [:todo/id server-id-1 :todo/status]) => :status/done
        "server-id-2 was auto-marked from :new to :ready"
        (get-in db [:todo/id server-id-2 :todo/status]) => :status/ready
        "SERVER-DB matches (mutation has a remote)"
        (get-in @server/SERVER-DB [:todo/id server-id-1 :todo/status]) => :status/done
        (get-in @server/SERVER-DB [:todo/id server-id-2 :todo/status]) => :status/ready))))

;; ============================================================================
;; Phase 7.4 — Modal state foundation.
;;
;; Verifies the pure state-helpers + mutex behavior. UI wiring (icon buttons,
;; modal-shell `:on-close`) is exercised by the per-modal specs in 7.5/7.6.
;; ============================================================================

(specification "set-open-modal*"
  (component "sets :ui/open-modal at the given list-ident"
    (let [after (sut/set-open-modal* (fixture-state) [:list/id 1] :about)]
      (assertions
        "stored at [:list/id 1 :ui/open-modal]"
        (get-in after [:list/id 1 :ui/open-modal]) => :about
        "no other keys mutated"
        (affects-only? (fixture-state) after
          [[:list/id 1 :ui/open-modal]]) => true)))

  (component "is mutex by construction (single-value overwrite)"
    (let [after (-> (fixture-state)
                  (sut/set-open-modal* [:list/id 1] :about)
                  (sut/set-open-modal* [:list/id 1] :help))]
      (assertions
        "second call replaces the first"
        (get-in after [:list/id 1 :ui/open-modal]) => :help)))

  (component "closes via :none"
    (let [after (-> (fixture-state)
                  (sut/set-open-modal* [:list/id 1] :about)
                  (sut/set-open-modal* [:list/id 1] :none))]
      (assertions
        "set to :none clears whatever was open"
        (get-in after [:list/id 1 :ui/open-modal]) => :none))))

(specification "toggle-open-modal*"
  (component "opens the modal when closed"
    (let [after (-> (fixture-state)
                  (sut/set-open-modal* [:list/id 1] :none)
                  (sut/toggle-open-modal* [:list/id 1] :about))]
      (assertions
        "transition :none → :about"
        (get-in after [:list/id 1 :ui/open-modal]) => :about)))

  (component "closes the same modal when it's open"
    (let [after (-> (fixture-state)
                  (sut/set-open-modal* [:list/id 1] :about)
                  (sut/toggle-open-modal* [:list/id 1] :about))]
      (assertions
        "transition :about → :none"
        (get-in after [:list/id 1 :ui/open-modal]) => :none)))

  (component "opens a different modal when one is already open (mutex)"
    (let [after (-> (fixture-state)
                  (sut/set-open-modal* [:list/id 1] :about)
                  (sut/toggle-open-modal* [:list/id 1] :help))]
      (assertions
        ":about → :help (replaces, doesn't stack)"
        (get-in after [:list/id 1 :ui/open-modal]) => :help))))

;; ============================================================================
;; Phase 7.5 — About + Help modals.
;;
;; The icon buttons in the header carry a screen-reader `<span class="clip">`
;; with the tooltip text, so h/click-on-text! finds them by that text. The
;; modals themselves render their content as DOM text, which h/text-exists?
;; picks up. Background-click close is verified by clicking the transparent
;; close button's hidden label text.
;; ============================================================================

(specification "About modal"
  (component "closed by default"
    (server/seed!)
    (let [spa (sut/init)]
      (assertions
        "About body text not present at startup"
        (h/text-exists? spa "The AutoFocus algorithm was designed") => false)))

  (component "clicking the 'About' header icon opens the modal with expected content"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "About")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/open-modal flipped to :about"
        (get-in (app/current-state spa) [:list/id 1 :ui/open-modal]) => :about
        "About heading visible"
        (h/text-exists? spa "About AutoFocus") => true
        "info-string-1 paragraph visible"
        (h/text-exists? spa "The AutoFocus algorithm was designed") => true
        "version line visible"
        (h/text-exists? spa "Version 0.1.4") => true
        "close-instruction footer visible"
        (h/text-exists? spa "Click on the 'i' icon above to close this window.") => true)))

  (component "clicking the background close-overlay dismisses the modal"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "About")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Close Info Modal")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/open-modal back to :none"
        (get-in (app/current-state spa) [:list/id 1 :ui/open-modal]) => :none
        "About content no longer visible"
        (h/text-exists? spa "The AutoFocus algorithm was designed") => false))))

(specification "Help modal"
  (component "clicking the 'Help' header icon opens the modal"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Help")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/open-modal = :help"
        (get-in (app/current-state spa) [:list/id 1 :ui/open-modal]) => :help
        "Help heading visible"
        (h/text-exists? spa "Instructions & Help") => true
        "instructions paragraph visible"
        (h/text-exists? spa "Add new items to your list by typing") => true
        "issues link text visible"
        (h/text-exists? spa "AutoFocus Issues") => true)))

  (component "mutex — clicking About then Help replaces (single modal at a time)"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "About")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Help")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/open-modal = :help (was :about)"
        (get-in (app/current-state spa) [:list/id 1 :ui/open-modal]) => :help
        "About content gone"
        (h/text-exists? spa "The AutoFocus algorithm was designed") => false
        "Help content present"
        (h/text-exists? spa "Add new items to your list by typing") => true))))

;; ============================================================================
;; Phase 7.6 — Import/Export modal (stubbed actions).
;;
;; The four buttons (Copy URL / Import / Export / Submit) currently
;; `console.log` only; real behaviour lands in a later phase. These specs
;; verify the markup is correctly wired to `:ui/open-modal :save` and that
;; bg-close dismisses, but don't (yet) exercise the click→action path.
;; ============================================================================

(specification "Import/Export modal"
  (component "clicking the 'Import/Export' header icon opens the modal"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Import/Export")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/open-modal = :save"
        (get-in (app/current-state spa) [:list/id 1 :ui/open-modal]) => :save
        "Import/Export heading visible"
        (h/text-exists? spa "Import/Export") => true
        "save-info-1 paragraph visible"
        (h/text-exists? spa "You can import and export JSON lists") => true
        "Copy List URL button visible"
        (h/text-exists? spa "Copy List URL") => true
        "Import button visible"
        (h/text-exists? spa "Import") => true
        "Export button visible"
        (h/text-exists? spa "Export") => true
        "Submit button visible"
        (h/text-exists? spa "Submit") => true
        "close-instruction footer visible"
        (h/text-exists? spa "Click on the 'disk' icon above to close this window.") => true)))

  (component "bg-close dismisses the Import/Export modal"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Import/Export")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Close Save Modal")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/open-modal back to :none"
        (get-in (app/current-state spa) [:list/id 1 :ui/open-modal]) => :none
        "save heading gone"
        (h/text-exists? spa "You can import and export JSON lists") => false))))

;; ============================================================================
;; Phase 7.7 — Theme toggle (light / dark).
;;
;; State-helper round-trip + a click-through that verifies the
;; `toggle-theme` mutation flips `:ui/theme` on `[:list/id 1]`. Visual
;; class swap is browser-manual (snapshot pair).
;; ============================================================================

(specification "toggle-theme*"
  (component "missing :ui/theme treated as :theme/light, first toggle → :dark"
    (let [after (sut/toggle-theme* (fixture-state) [:list/id 1])]
      (assertions
        ":ui/theme = :theme/dark after first toggle"
        (get-in after [:list/id 1 :ui/theme]) => :theme/dark)))

  (component "second toggle returns to :theme/light"
    (let [after (-> (fixture-state)
                  (sut/toggle-theme* [:list/id 1])
                  (sut/toggle-theme* [:list/id 1]))]
      (assertions
        ":ui/theme = :theme/light after two toggles"
        (get-in after [:list/id 1 :ui/theme]) => :theme/light))))

(specification "Toggle Theme button"
  (component "clicking 'Toggle Theme' flips :ui/theme"
    (server/seed!)
    (let [spa (sut/init)
          start-theme (get-in (app/current-state spa) [:list/id 1 :ui/theme])
          _   (h/click-on-text! spa "Toggle Theme")
          _   (h/render-frame! spa)
          mid-theme   (get-in (app/current-state spa) [:list/id 1 :ui/theme])
          _   (h/click-on-text! spa "Toggle Theme")
          _   (h/render-frame! spa)
          end-theme   (get-in (app/current-state spa) [:list/id 1 :ui/theme])]
      (assertions
        "initial theme is :theme/light (default from initial-state)"
        start-theme => :theme/light
        "first click → :theme/dark"
        mid-theme => :theme/dark
        "second click → back to :theme/light"
        end-theme => :theme/light))))

;; ============================================================================
;; Phase 7.9 — Error surfacing.
;;
;; Clicking Add Item with blank text, Delete List on an empty list, or
;; Mark Done with no actionable items now sets `:ui/err-msg` to the
;; relevant string from `learn.ui.strings`. Successful actions clear
;; the prior error.
;; ============================================================================

(specification "set-err-msg*"
  (component "sets the message at the given list-ident"
    (let [after (sut/set-err-msg* (fixture-state) [:list/id 1] "oh no")]
      (assertions
        ":ui/err-msg now 'oh no'"
        (get-in after [:list/id 1 :ui/err-msg]) => "oh no")))

  (component "nil clears the message"
    (let [after (-> (fixture-state)
                  (sut/set-err-msg* [:list/id 1] "oh no")
                  (sut/set-err-msg* [:list/id 1] nil))]
      (assertions
        ":ui/err-msg back to nil"
        (get-in after [:list/id 1 :ui/err-msg]) => nil))))

(specification "Error surfacing — Add Item with blank text"
  (component "clicking 'Add Item' with empty input shows the empty-input error"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Add Item")  ; default :ui/new-todo-text is ""
          _   (h/render-frame! spa)]
      (assertions
        ":ui/err-msg = empty-input-err"
        (get-in (app/current-state spa) [:list/id 1 :ui/err-msg])
        => "New items cannot be empty or only whitespace."
        "error text visible in the DOM"
        (h/text-exists? spa "New items cannot be empty or only whitespace.") => true
        "no new todo was added"
        (count (get-in (app/current-state spa) [:list/id 1 :list/todos])) => 2)))

  (component "typing text and clicking Add Item clears any prior error"
    (server/seed!)
    (let [spa (sut/init)
          _   (h/click-on-text! spa "Add Item")          ; set err
          _   (h/render-frame! spa)
          _   (h/type-into-labeled! spa "New TODO" "valid text")
          _   (h/click-on-text! spa "Add Item")          ; clears err + adds
          _   (h/render-frame! spa)]
      (assertions
        ":ui/err-msg cleared after successful add"
        (get-in (app/current-state spa) [:list/id 1 :ui/err-msg]) => nil
        "list grew by one"
        (count (get-in (app/current-state spa) [:list/id 1 :list/todos])) => 3))))

(specification "Error surfacing — Delete List on empty list"
  (component "clicking 'Delete List' on an empty list shows nothing-to-delete-err"
    (server/seed!)
    (let [spa (sut/init)
          ;; Phase 7.12: emptying the list now takes Delete List → Yes;
          ;; only after that do subsequent Delete List clicks hit the
          ;; "already empty" error path.
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Yes")
          _   (h/render-frame! spa)
          ;; Sanity: list is empty after Delete List → Yes and err is clear.
          first-err (get-in (app/current-state spa) [:list/id 1 :ui/err-msg])
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)]
      (assertions
        "after the first (valid) delete via the modal, err is nil"
        first-err => nil
        "second click (list already empty) sets the nothing-to-delete error"
        (get-in (app/current-state spa) [:list/id 1 :ui/err-msg])
        => "There is nothing to delete."
        "error text visible in the DOM"
        (h/text-exists? spa "There is nothing to delete.") => true))))

(specification "Error surfacing — Mark Done with no actionable items"
  (component "clicking 'Mark Done' with no :ready items shows cannot-take-action-err"
    (server/seed!)
    (let [spa (sut/init)
          ;; First Mark Done flips id-1 :ready → :done and auto-marks
          ;; id-2 :new → :ready. Second Mark Done makes id-2 :done. After
          ;; that, no :ready items left.
          _   (h/click-on-text! spa "Mark Done")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Mark Done")
          _   (h/render-frame! spa)
          ;; Third click — no actionable items remaining.
          _   (h/click-on-text! spa "Mark Done")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/err-msg = cannot-take-action-err"
        (get-in (app/current-state spa) [:list/id 1 :ui/err-msg])
        => "There are no actionable tasks in your list."
        "error text visible in the DOM"
        (h/text-exists? spa "There are no actionable tasks in your list.") => true))))

(specification "Error surfacing — Prioritize on non-prioritizable list"
  (component "clicking 'Prioritize' with an empty list shows not-prioritizable-err"
    (server/seed!)
    (let [spa (sut/init)
          ;; Empty the list so the prioritizable predicate is false.
          ;; Phase 7.12: delete now goes through the confirm modal.
          _   (h/click-on-text! spa "Delete List")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Yes")
          _   (h/render-frame! spa)
          _   (h/click-on-text! spa "Prioritize")
          _   (h/render-frame! spa)]
      (assertions
        ":ui/err-msg = not-prioritizable-err"
        (get-in (app/current-state spa) [:list/id 1 :ui/err-msg])
        => "The list isn't prioritizable right now."
        "error text visible in the DOM"
        (h/text-exists? spa "The list isn't prioritizable right now.") => true
        "review chart did not transition to :active"
        (contains? (scf/current-configuration spa sut/review-session-id) chart/active)
        => false))))
