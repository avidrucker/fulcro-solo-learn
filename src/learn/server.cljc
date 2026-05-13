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
  "Dev / JVM-test seed: two todos in a known order, exercising
   `:ready` + `:new`. The spec suite refers to these UUIDs/texts
   directly (search for `server-id-1` / `server-id-2` in
   `client_test`), so this shape is load-bearing for tests. The
   *deployed* CLJS app overrides this with `empty-state` (B-5)
   before any user interaction."
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

(def empty-state
  "Production initial state — an empty list. Used by CLJS `init`
   (B-5 fix) so first-time visitors to the deployed app see an
   empty list rather than the JVM-test dev seed. Shape matches
   `initial-state`: a list entity with `:list/todos` `[]` and the
   `:todo/id` table empty."
  {:list/id {list-id {:list/id list-id :list/todos []}}
   :todo/id {}})

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
