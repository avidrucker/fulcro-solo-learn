(ns learn.resolvers-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [com.wsscode.pathom.connect :as pc]
    [learn.resolvers :as sut]
    [learn.parser :as parser]
    [learn.server :as server]))

;; ----------------------------------------------------------------------
;; Helpers — pull a resolver's function out of its registered map.
;;
;; Pathom 2 resolvers are maps with ::pc/resolve (for resolvers) or
;; ::pc/mutate (for mutations). Calling those functions directly skips
;; the parser, so we can unit-test the logic in isolation.
;; ----------------------------------------------------------------------

(defn run-resolver
  "Invoke a Pathom resolver's function. Defaults to an empty env;
   pass one (typically built with `test-env`) to simulate parameterized
   queries or other parser-supplied context."
  ([resolver input] (run-resolver resolver {} input))
  ([resolver env input] ((::pc/resolve resolver) env input)))

(defn run-mutation
  "Invoke a Pathom mutation's function with a fresh empty env."
  [mutation params]
  ((::pc/mutate mutation) {} params))

(defn test-env
  "Build a Pathom-shaped env for resolver/mutation testing.

   Options map:
     :params - query parameters, e.g. .... Placed where the
               real Pathom parser would put them when EQL contains a
               parameterized call like (:all-todos ...).

   Returns an env map. Empty when called with no options.

   Why this exists: Pathom's env shape is an implementation detail of
   the framework. By constructing the env in one place, every spec
   declares its intent (here are the params) without depending on
   Pathom's current internal representation. If the contract changes,
   we update this function rather than every spec that constructs
   an env by hand."
  ([] (test-env {}))
  ([{:keys [params]}]
   (cond-> {}
     params (assoc-in [:ast :params] params))))

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

(specification "all-todos-resolver with :status parameter"
  (component "no parameter — returns every todo"
    (server/seed!)
    (let [result (run-resolver sut/all-todos-resolver {})]
      (assertions
        "returns both seeded todos"
        (count (:all-todos result)) => 2)))

  (component "{:status :status/ready} — returns only ready todos"
    (server/seed!)
    (let [env    (test-env {:params {:status :status/ready}})
          result (run-resolver sut/all-todos-resolver env {})]
      (assertions
        "returns only the ready todo (seed-id-1)"
        (count (:all-todos result)) => 1
        (set (map :todo/id (:all-todos result))) => #{seed-id-1})))

  (component "{:status :status/new} — returns only new todos"
    (server/seed!)
    (let [env    (test-env {:params {:status :status/new}})
          result (run-resolver sut/all-todos-resolver env {})]
      (assertions
        "returns only the new todo (seed-id-2)"
        (count (:all-todos result)) => 1
        (set (map :todo/id (:all-todos result))) => #{seed-id-2}))))

(specification "todo-resolver"
  (component "given a known todo id, returns text and status"
    (server/seed!)
    (let [result (run-resolver sut/todo-resolver {:todo/id seed-id-1})]
      (assertions
        "returns the todo's text"
        (:todo/text result) => "Read the Fulcro book"
        "returns the todo's status"
        (:todo/status result) => :status/ready
        "returns only those two keys"
        (set (keys result)) => #{:todo/text :todo/status}
        )))

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

(specification "list-recording mutations (add/cancel/complete/clone)"
  ;; All four mutations share the same `record-list-items` body and differ
  ;; only by ::pc/sym. One representative is exercised end-to-end; the rest
  ;; are checked via registration coverage.
  (component "writes :list/items verbatim to SERVER-DB, returns the list ident"
    (server/seed!)
    (let [new-id    (random-uuid)
          new-todo  {:todo/id new-id :todo/text "Brand new" :todo/status :status/new}
          new-items (conj (server/all-todos @server/SERVER-DB) new-todo)
          result    (run-mutation sut/add-todo-mutation {:list/items new-items})]
      (assertions
        "returns the list ident"
        result => {:list/id server/list-id}
        ":todo/id table grew by one entry"
        (count (:todo/id @server/SERVER-DB)) => 3
        "new entity has the expected text"
        (get-in @server/SERVER-DB [:todo/id new-id :todo/text]) => "Brand new"
        "new id appended to :list/todos"
        (last (get-in @server/SERVER-DB [:list/id server/list-id :list/todos]))
        => new-id)))

  (component "list-recording mutations are registered"
    (let [symbols (set (map ::pc/sym sut/all-resolvers))]
      (assertions
        "add-todo wired"
        (contains? symbols 'learn.client/add-todo) => true
        "cancel-todo wired"
        (contains? symbols 'learn.client/cancel-todo) => true
        "complete-benchmark-item wired"
        (contains? symbols 'learn.client/complete-benchmark-item) => true
        "clone-todo wired"
        (contains? symbols 'learn.client/clone-todo) => true
        "import-from-text wired (Phase 7.12)"
        (contains? symbols 'learn.client/import-from-text) => true
        "import-from-json wired (Phase 13)"
        (contains? symbols 'learn.client/import-from-json) => true))))

;; ============================================================================
;; Error handling specifications
;; ============================================================================

(specification "parser error handling"
  (component "missing keys are elided rather than appearing as not-found markers"
    (server/seed!)
    (let [response (parser/handler [:nonexistent/key])]
      (assertions
        ":nonexistent/key is elided from response"
        (contains? response :nonexistent/key) => false))))
