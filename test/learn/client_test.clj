(ns learn.client-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.fulcro.components :as comp]
    [learn.client :as sut]))

;; ============================================================================
;; Test fixtures
;; ============================================================================

(defn fixture-state
  "Two seeded todos: id 1 (not done), id 2 (done)."
  []
  {:list/id {1 {:list/id          1
                :list/todos       [[:todo/id 1] [:todo/id 2]]
                :ui/new-todo-text "draft text"}}
   :todo/id {1 {:todo/id 1 :todo/text "First"  :todo/done? false}
             2 {:todo/id 2 :todo/text "Second" :todo/done? true}}})

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
        "starts the new todo as not done"
        (get-in after [:todo/id new-id :todo/done?]) => false
        "appends the new ident at the end of :list/todos"
        (last (get-in after [:list/id 1 :list/todos])) => new-ident
        "clears :ui/new-todo-text on the list"
        (get-in after [:list/id 1 :ui/new-todo-text]) => ""
        "affects only the new entity, the list's :list/todos, and :ui/new-todo-text"
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
        "places the new ident as the only entry in :list/todos"
        (get-in after [:list/id 1 :list/todos]) => [new-ident]
        "affects only the new entity and the list's :list/todos"
        (affects-only? before after
          [[:todo/id new-id]
           [:list/id 1 :list/todos]])
        => true)))

  (component "after a previous delete"
    (let [before     (sut/delete-todo* (fixture-state) 1)   ; only id 2 remains
          after      (sut/add-todo* before [:list/id 1] "Third")
          new-ident  (last (get-in after [:list/id 1 :list/todos]))
          [_ new-id] new-ident]
      (assertions
        "creates a new entity with the new text"
        (get-in after [:todo/id new-id :todo/text]) => "Third"
        "appends the new ident to :list/todos"
        (last (get-in after [:list/id 1 :list/todos])) => new-ident
        "affects only the new entity and the targeted list paths"
        (affects-only? before after
          [[:todo/id new-id]
           [:list/id 1 :list/todos]
           [:list/id 1 :ui/new-todo-text]])
        => true))))

(specification "delete-todo*"
  (let [before (fixture-state)
        after  (sut/delete-todo* before 1)]
    (assertions
      "removes the entity from the :todo/id table"
      (contains? (:todo/id after) 1) => false
      "removes the ident from :list/todos"
      (get-in after [:list/id 1 :list/todos]) => [[:todo/id 2]]
      "affects only :todo/id 1 and the list's :list/todos"
      (affects-only? before after
        [[:todo/id 1]
         [:list/id 1 :list/todos]])
      => true)))

(specification "edit-todo*"
  (component "for an existing todo"
    (let [before (fixture-state)
          after  (sut/edit-todo* before 1 "Updated text")]
      (assertions
        "updates :todo/text on the targeted entity"
        (get-in after [:todo/id 1 :todo/text]) => "Updated text"
        "affects only :todo/text of the targeted entity"
        (affects-only? before after [[:todo/id 1 :todo/text]]) => true)))

  (component "for a non-existent id"
    (assertions
      "is a no-op (returns equivalent state)"
      (sut/edit-todo* (fixture-state) 999 "x") => (fixture-state))))

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

(specification "mark-all-complete*"
  (component "with done? = true"
    (let [before (fixture-state)
          after  (sut/mark-all-complete* before [:list/id 1] true)]
      (assertions
        "marks the previously-not-done todo as done"
        (get-in after [:todo/id 1 :todo/done?]) => true
        "leaves the already-done todo as done"
        (get-in after [:todo/id 2 :todo/done?]) => true
        "affects only the :todo/done? fields of the targeted todos"
        (affects-only? before after
          [[:todo/id 1 :todo/done?]
           [:todo/id 2 :todo/done?]])
        => true)))

  (component "with done? = false (un-mark all)"
    (let [before (fixture-state)
          after  (sut/mark-all-complete* before [:list/id 1] false)]
      (assertions
        "un-marks the previously-done todo"
        (get-in after [:todo/id 2 :todo/done?]) => false
        "affects only the :todo/done? fields of the targeted todos"
        (affects-only? before after
          [[:todo/id 1 :todo/done?]
           [:todo/id 2 :todo/done?]])
        => true))))

;; ============================================================================
;; Integration specifications — these touch live Fulcro state and the
;; loopback remote. We don't use affects-only? here because Fulcro mutates
;; many internal keys (active-remotes, transaction queue, marker tables)
;; that we don't want to assert about.
;; ============================================================================

(specification "df/load! integration"
  (component "loads server todos into the client DB"
    (sut/reset-server!)
    (let [spa         (sut/init)
          _           (df/load! spa :all-todos sut/TodoItem
                        {:target [:list/id 1 :list/todos]})
          _           (h/render-frame! spa)
          db          (app/current-state spa)
          server-id-1 #uuid "11111111-1111-1111-1111-111111111111"
          server-id-2 #uuid "22222222-2222-2222-2222-222222222222"]
      (assertions
        "the server's todos appear in the :todo/id table"
        (contains? (:todo/id db) server-id-1) => true
        (contains? (:todo/id db) server-id-2) => true
        "the loaded idents replace :list/todos at the targeted path"
        (set (get-in db [:list/id 1 :list/todos]))
        => #{[:todo/id server-id-1] [:todo/id server-id-2]}
        "loaded entities have the expected text"
        (get-in db [:todo/id server-id-1 :todo/text]) => "Read the Fulcro book"))))

(specification "add-todo with :remote true"
  (component "client-side add reaches the server"
    (sut/reset-server!)
    (let [spa (sut/init)
          _   (h/type-into-labeled! spa "New TODO" "Pet the cat")
          _   (h/click-on-text! spa "Add")
          _   (h/render-frame! spa)]
      (assertions
        "the server's :todo/id table grew by one entry"
        (count (:todo/id @sut/SERVER-DB)) => 3
        "the new entry on the server has the typed text"
        (some #(= "Pet the cat" (:todo/text %))
          (vals (:todo/id @sut/SERVER-DB))) => true
        "the client also has a todo with that text"
        (some #(= "Pet the cat" (:todo/text %))
          (vals (:todo/id (app/current-state spa)))) => true))))
