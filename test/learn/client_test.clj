(ns learn.client-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.client :as sut]))

;; ============================================================================
;; Test fixtures — small, realistic, hand-built normalized state maps.
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

;; ============================================================================
;; Existing helpers — these should pass with your current implementation.
;; ============================================================================

(specification "add-todo*"
  (component "into a populated list"
    (let [list-ident [:list/id 1]
          result     (sut/add-todo* (fixture-state) list-ident "Third")]
      (assertions
        "adds a new entity to the :todo/id table"
        (count (:todo/id result)) => 3
        "stores the new text on the new entity"
        (get-in result [:todo/id 3 :todo/text]) => "Third"
        "starts the new todo as not done"
        (get-in result [:todo/id 3 :todo/done?]) => false
        "appends the new ident to :list/todos"
        (last (get-in result [:list/id 1 :list/todos])) => [:todo/id 3]
        "preserves existing todos in :list/todos"
        (vec (take 2 (get-in result [:list/id 1 :list/todos])))
        => [[:todo/id 1] [:todo/id 2]]
        "clears :ui/new-todo-text on the list"
        (get-in result [:list/id 1 :ui/new-todo-text]) => "")))

  (component "into an empty list"
    (let [list-ident [:list/id 1]
          result     (sut/add-todo* (empty-fixture-state) list-ident "First!")]
      (assertions
        "creates the first todo with the new text"
        (get-in result [:todo/id 1 :todo/text]) => "First!"
        "places its ident as the only entry in :list/todos"
        (get-in result [:list/id 1 :list/todos]) => [[:todo/id 1]])))

  (component "after a previous delete"
    ;; This component exercises a bug — see the note in my message.
    (let [list-ident [:list/id 1]
          state      (sut/delete-todo* (fixture-state) 1) ; only id 2 remains
          result     (sut/add-todo* state list-ident "Third")]
      (assertions
        "preserves the surviving todo's text"
        (get-in result [:todo/id 2 :todo/text]) => "Second"
        "table contains exactly the surviving todo plus the new one"
        (count (:todo/id result)) => 2
        "the new todo's text is reachable somewhere in the table"
        (some #(= "Third" (:todo/text %)) (vals (:todo/id result))) => true))))

(specification "delete-todo*"
  (let [result (sut/delete-todo* (fixture-state) 1)]
    (assertions
      "removes the entity from the :todo/id table"
      (contains? (:todo/id result) 1) => false
      "leaves other todos in the table"
      (contains? (:todo/id result) 2) => true
      "removes the ident from :list/todos"
      (get-in result [:list/id 1 :list/todos]) => [[:todo/id 2]]
      "preserves :ui/new-todo-text"
      (get-in result [:list/id 1 :ui/new-todo-text]) => "draft text")))

;; ============================================================================
;; New helpers — failing tests drive these implementations.
;; Add `edit-todo*`, `delete-all*`, and `mark-all-complete*` to client.cljc
;; until these specifications all pass.
;; ============================================================================

(specification "edit-todo*"
  (component "for an existing todo"
    (let [result (sut/edit-todo* (fixture-state) 1 "Updated text")]
      (assertions
        "updates :todo/text on the targeted entity"
        (get-in result [:todo/id 1 :todo/text]) => "Updated text"
        "leaves :todo/done? unchanged on the targeted entity"
        (get-in result [:todo/id 1 :todo/done?]) => false
        "leaves other todos unchanged"
        (get-in result [:todo/id 2 :todo/text]) => "Second"
        "leaves :list/todos unchanged"
        (get-in result [:list/id 1 :list/todos]) => [[:todo/id 1] [:todo/id 2]])))

  (component "for a non-existent id"
    (assertions
      "is a no-op (returns equivalent state)"
      (sut/edit-todo* (fixture-state) 999 "x") => (fixture-state))))

(specification "delete-all*"
  (let [list-ident [:list/id 1]
        result     (sut/delete-all* (fixture-state) list-ident)]
    (assertions
      "empties :list/todos at the given list"
      (get-in result [:list/id 1 :list/todos]) => []
      "removes every entity from the :todo/id table"
      (:todo/id result) => nil ;; was {}, now nil
      "preserves the list entity itself"
      (contains? (:list/id result) 1) => true
      "preserves :ui/new-todo-text on the list"
      (get-in result [:list/id 1 :ui/new-todo-text]) => "draft text")))

(specification "mark-all-complete*"
  (component "with done? = true"
    (let [list-ident [:list/id 1]
          result     (sut/mark-all-complete* (fixture-state) list-ident true)]
      (assertions
        "marks the previously-not-done todo as done"
        (get-in result [:todo/id 1 :todo/done?]) => true
        "leaves the already-done todo as done"
        (get-in result [:todo/id 2 :todo/done?]) => true
        "leaves :todo/text unchanged"
        (get-in result [:todo/id 1 :todo/text]) => "First")))

  (component "with done? = false (un-mark all)"
    (let [list-ident [:list/id 1]
          result     (sut/mark-all-complete* (fixture-state) list-ident false)]
      (assertions
        "un-marks the previously-done todo"
        (get-in result [:todo/id 2 :todo/done?]) => false
        "leaves the not-done todo unchanged"
        (get-in result [:todo/id 1 :todo/done?]) => false))))
