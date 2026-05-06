(ns learn.server
  "Stand-in for a real backend.

   Holds the canonical state of all todos in a single atom (`SERVER-DB`).
   In a production app this would be a database (Datomic, Postgres, etc.) —
   we use an atom so the whole 'server' fits in one process for learning.

   This namespace knows nothing about Fulcro, EQL, or remotes. It's just
   data + functions that read/write that data. The parser namespace is
   what bridges this to Fulcro's query model.")

(def initial-state
  "Seed data for the server. Two todos, one done and one not.
   UUIDs are hardcoded so we can predict and assert in tests."
  {:todo/id {#uuid "11111111-1111-1111-1111-111111111111"
             {:todo/id    #uuid "11111111-1111-1111-1111-111111111111"
              :todo/text  "Read the Fulcro book"
              :todo/done? false}
             #uuid "22222222-2222-2222-2222-222222222222"
             {:todo/id    #uuid "22222222-2222-2222-2222-222222222222"
              :todo/text  "Try out remotes"
              :todo/done? true}}})

(defonce SERVER-DB
  (atom initial-state))

(defn seed!
  "Resets the server to the seed state. Useful between REPL runs and tests."
  []
  (clojure.core/reset! SERVER-DB initial-state))

;; ----------------------------------------------------------------------
;; Read/write operations on the server.
;; These are the building blocks the parser will compose with.
;; They take and return values — the SERVER-DB swap! lives in callers.
;; ----------------------------------------------------------------------

(defn all-todos
  "Returns all todos as a vector of maps (denormalized).
   Caller passes the current server state; we just project."
  [server-state]
  (vec (vals (:todo/id server-state))))

(defn add-todo
  "Adds a todo to the server state. Returns [new-state new-todo]."
  [server-state {:todo/keys [text]}]
  (let [new-id   (random-uuid)
        new-todo {:todo/id new-id :todo/text text :todo/done? false}]
    [(assoc-in server-state [:todo/id new-id] new-todo)
     new-todo]))

(defn delete-todo
  "Removes a todo by id from the server state. Returns the new state."
  [server-state {:todo/keys [id]}]
  (update server-state :todo/id dissoc id))
