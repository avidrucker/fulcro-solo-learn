(ns learn.resolvers-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [com.wsscode.pathom.connect :as pc]
    [com.fulcrologic.guardrails.malli.fulcro-spec-helpers :refer [when-mocking! provided!]]
    [learn.resolvers :as sut]
    [learn.server :as server]))

;; ----------------------------------------------------------------------
;; Helpers — pull a resolver's function out of its registered map.
;;
;; Pathom 2 resolvers are maps with ::pc/resolve (for resolvers) or
;; ::pc/mutate (for mutations). Calling those functions directly skips
;; the parser, so we can unit-test the logic in isolation.
;; ----------------------------------------------------------------------

(defn run-resolver
  "Invoke a Pathom resolver's function with a fresh empty env."
  [resolver input]
  ((::pc/resolve resolver) {} input))

(defn run-mutation
  "Invoke a Pathom mutation's function with a fresh empty env."
  [mutation params]
  ((::pc/mutate mutation) {} params))

;; Server seed UUIDs — same as in client-test, repeated here for clarity.
(def seed-id-1 #uuid "11111111-1111-1111-1111-111111111111")
(def seed-id-2 #uuid "22222222-2222-2222-2222-222222222222")

;; ============================================================================
;; Resolver specifications
;; ============================================================================

(specification "all-todos-resolver"
  (component "returns every todo in the SERVER-DB as a vector of idents"
    (server/seed!)
    (let [result (run-resolver sut/all-todos-resolver {})]
      (assertions
        "produces a single :all-todos key"
        (keys result) => [:all-todos]
        "returns one entry per todo in the server"
        (count (:all-todos result)) => 2
        "each entry is a map containing only :todo/id"
        (every? #(= #{:todo/id} (set (keys %))) (:all-todos result)) => true
        "the ids match what's in the server"
        (set (map :todo/id (:all-todos result)))
        => #{seed-id-1 seed-id-2}))))

(specification "todo-resolver"
  (component "given a known todo id, returns text and done?"
    (server/seed!)
    (let [result (run-resolver sut/todo-resolver {:todo/id seed-id-1})]
      (assertions
        "returns the todo's text"
        (:todo/text result) => "Read the Fulcro book"
        "returns the todo's done state"
        (:todo/done? result) => false
        "returns only those two keys"
        (set (keys result)) => #{:todo/text :todo/done?})))

  (component "given an unknown id, returns an empty result"
    ;; The resolver currently just returns (select-keys nil [...]) → {}.
    ;; In production, this would typically be wrapped in a security check
    ;; that returns nil/throws — but for our learning version, an empty
    ;; map is a graceful degradation.
    (server/seed!)
    (let [result (run-resolver sut/todo-resolver {:todo/id (random-uuid)})]
      (assertions
        "returns an empty map for missing entities"
        result => {}))))

;; ============================================================================
;; Mutation specifications
;; ============================================================================

(specification "add-todo-mutation"
  (component "creates a new todo on the server and returns it"
    (server/seed!)
    (let [before-count (count (:todo/id @server/SERVER-DB))
          result       (run-mutation sut/add-todo-mutation
                         {:todo/text "From a test"})]
      (assertions
        "the server gained one entry"
        (count (:todo/id @server/SERVER-DB)) => (inc before-count)
        "the returned map has the expected text"
        (:todo/text result) => "From a test"
        "the returned map starts not-done"
        (:todo/done? result) => false
        "the returned id matches an entry in the server"
        (contains? (:todo/id @server/SERVER-DB) (:todo/id result)) => true))))

(specification "delete-todo-mutation"
  (component "removes the todo from the server"
    (server/seed!)
    (let [result (run-mutation sut/delete-todo-mutation {:todo/id seed-id-1})]
      (assertions
        "the targeted todo is gone"
        (contains? (:todo/id @server/SERVER-DB) seed-id-1) => false
        "other todos remain"
        (contains? (:todo/id @server/SERVER-DB) seed-id-2) => true
        "returns the id that was deleted"
        result => {:todo/id seed-id-1}))))
