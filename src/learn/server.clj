(ns learn.server
  "Stand-in for a real backend.

   Holds the canonical state of all todos in a single atom (`SERVER-DB`).
   Production would be a database (Datomic, Postgres, etc.); we use an
   atom so the whole 'server' fits in one process for learning.

   The state shape mirrors the client's normalized form: a `:list/id` table
   carrying ordered idents, plus a `:todo/id` entity table. Order in
   `:list/todos` is meaningful — see SCHEMA.md §5.")

(def list-id
  "Singleton list id. The current app has one list; multi-list support is
   a later phase (mirrors the client-side hardcoded `[:list/id 1]`)."
  1)

(def initial-state
  "Seed data: two todos in a known order, exercising :ready + :new."
  (let [id-1 #uuid "11111111-1111-1111-1111-111111111111"
        id-2 #uuid "22222222-2222-2222-2222-222222222222"]
    {:list/id {list-id {:list/id    list-id
                        :list/todos [id-1 id-2]}}
     :todo/id {id-1 {:todo/id     id-1
                     :todo/text   "Read the Fulcro book"
                     :todo/status :status/ready}
               id-2 {:todo/id     id-2
                     :todo/text   "Try out remotes"
                     :todo/status :status/new}}}))

(defonce SERVER-DB
  (atom initial-state))

(defn seed!
  "Resets the server to the seed state."
  []
  (reset! SERVER-DB initial-state))

;; ----------------------------------------------------------------------
;; Projection helpers: SERVER-DB ↔ denormalized items vector.
;; ----------------------------------------------------------------------

(defn items
  "Returns the denormalized items vector for `list-id`, preserving order."
  [server-state list-id]
  (let [order (get-in server-state [:list/id list-id :list/todos])]
    (mapv #(get-in server-state [:todo/id %]) order)))

(defn write-items
  "Replaces the list's ordered idents and merges entity updates from `items`."
  [server-state list-id items]
  (let [ids      (mapv :todo/id items)
        entities (into {} (map (juxt :todo/id identity)) items)]
    (-> server-state
      (assoc-in [:list/id list-id :list/todos] ids)
      (update :todo/id merge entities))))

(defn all-todos
  "All todos as a vector of maps, in list order."
  [server-state]
  (items server-state list-id))
