(ns learn.resolvers
  "Pathom 2 resolvers and mutations for the TODO app.

   The mutations are intentionally dumb: each receives `:list/items` (the
   post-mutation denormalized vector computed client-side) and writes it
   straight into SERVER-DB via `server/write-items`. The AutoFocus domain
   logic (auto-mark, status rules, refusals) lives entirely on the client;
   the server records the result.

   Pathom 2's pc/defmutation does NOT accept docstrings between the name
   and the arglist (unlike pc/defresolver), so this file uses `;;` comments
   above each form instead."
  (:require
    [com.wsscode.pathom.connect :as pc]
    [learn.server :as server]))

;; ----------------------------------------------------------------------
;; Resolvers — read-only
;; ----------------------------------------------------------------------

;; Entry point for loading every todo. Returns a vector of *idents*,
;; preserving `:list/todos` order; Pathom chains to `todo-resolver` for
;; the remaining fields.
;;
;; Supports an optional `:status` query parameter that filters by status.
(pc/defresolver all-todos-resolver [env _input]
  {::pc/output [{:all-todos [:todo/id]}]}
  (let [params         (-> env :ast :params)
        status-filter? (contains? params :status)
        target-status  (:status params)
        all-todos      (server/all-todos @server/SERVER-DB)
        filtered       (cond->> all-todos
                         status-filter? (filter #(= target-status (:todo/status %))))]
    {:all-todos (mapv (fn [t] {:todo/id (:todo/id t)}) filtered)}))

;; Given a `:todo/id`, produces the rest of the todo's fields.
(pc/defresolver todo-resolver [_env {:todo/keys [id]}]
  {::pc/input  #{:todo/id}
   ::pc/output [:todo/text :todo/status :todo/was]}
  (let [todo (get-in @server/SERVER-DB [:todo/id id])]
    (select-keys todo [:todo/text :todo/status :todo/was])))

;; ----------------------------------------------------------------------
;; Mutations — write `:list/items` to the server.
;;
;; Each mutation is the same one-line write because the client has already
;; done the domain work. They're registered under distinct `::pc/sym`s so
;; the client's `(remote [_] true)` lights up the right symbol per call.
;; ----------------------------------------------------------------------

(defn- record-list-items
  "Writes `items` as the new state of the list, returning the list ident."
  [items]
  (swap! server/SERVER-DB server/write-items server/list-id items)
  {:list/id server/list-id})

(pc/defmutation add-todo-mutation [_env {:list/keys [items]}]
  {::pc/sym    'learn.client/add-todo
   ::pc/output [:list/id]}
  (record-list-items items))

(pc/defmutation cancel-todo-mutation [_env {:list/keys [items]}]
  {::pc/sym    'learn.client/cancel-todo
   ::pc/output [:list/id]}
  (record-list-items items))

(pc/defmutation complete-benchmark-item-mutation [_env {:list/keys [items]}]
  {::pc/sym    'learn.client/complete-benchmark-item
   ::pc/output [:list/id]}
  (record-list-items items))

(pc/defmutation clone-todo-mutation [_env {:list/keys [items]}]
  {::pc/sym    'learn.client/clone-todo
   ::pc/output [:list/id]}
  (record-list-items items))

;; ----------------------------------------------------------------------
;; Registry
;; ----------------------------------------------------------------------

(def all-resolvers
  [all-todos-resolver
   todo-resolver
   add-todo-mutation
   cancel-todo-mutation
   complete-benchmark-item-mutation
   clone-todo-mutation])

;; REPL: to trace EQL traffic, enable parser debug:
;;   (require 'learn.parser :reload)
;;   (alter-var-root #'learn.parser/*debug?* (constantly true))
