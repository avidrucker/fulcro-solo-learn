(ns learn.resolvers
  "Pathom 2 resolvers and mutations for the TODO app.

   This namespace is the production-shaped replacement for the hand-rolled
   `cond`-based parser. Each resolver/mutation is a small, declarative piece:
     - Resolvers declare their inputs and outputs; Pathom composes them.
     - Mutations declare a wire symbol (::pc/sym) that the client sends.

   Pathom 2's pc/defmutation does NOT accept docstrings between the
   name and the arglist (unlike pc/defresolver). For consistency, all
   forms in this file use `;;` comments above the definition instead."
  (:require
    [com.wsscode.pathom.connect :as pc]
    [learn.server :as server]))

;; ----------------------------------------------------------------------
;; Resolvers — read-only operations that produce data.
;; ----------------------------------------------------------------------

;; Global resolver: entry point for loading every todo.
;; Returns a vector of *idents* — Pathom will call the entity resolver
;; below to fill in :todo/text and :todo/status for each one.
;;
;; Supports an optional :status query parameter:
;;   nil           - returns all todos
;;   :status/new   - returns only new todos
;;   :status/ready - returns only ready todos
;;   etc.
(pc/defresolver all-todos-resolver [env _input]
  {::pc/output [{:all-todos [:todo/id]}]}
  (let [params       (-> env :ast :params)
        status-filter? (contains? params :status)
        target-status (:status params)
        all-todos    (vals (:todo/id @server/SERVER-DB))
        filtered     (cond->> all-todos
                       status-filter? (filter #(= target-status (:todo/status %))))]
    {:all-todos (mapv (fn [t] {:todo/id (:todo/id t)}) filtered)}))

;; Entity resolver: given a :todo/id, produces the rest of a todo's fields.
;; This is the resolver Pathom chains to from the all-todos result.
(pc/defresolver todo-resolver [_env {:todo/keys [id]}]
  {::pc/input  #{:todo/id}
   ::pc/output [:todo/text :todo/status]}
  (let [todo (get-in @server/SERVER-DB [:todo/id id])]
    (select-keys todo [:todo/text :todo/status])))

;; ----------------------------------------------------------------------
;; Mutations — writes that change the server's state.
;;
;; The `::pc/sym` key tells Pathom what symbol the client sends.
;; This decouples the mutation's local function name from the wire
;; protocol, addressing the namespace-coupling concern from Phase 4.
;; ----------------------------------------------------------------------

;; Server-side handler for the client's add-todo mutation.
;; Adds a todo to SERVER-DB and returns it so the client can merge.
(pc/defmutation add-todo-mutation [_env {:todo/keys [text]}]
  {::pc/sym    'learn.client/add-todo
   ::pc/output [:todo/id :todo/text :todo/status]}
  (let [[new-state new-todo] (server/add-todo @server/SERVER-DB
                               {:todo/text text})]
    (reset! server/SERVER-DB new-state)
    new-todo))

;; Server-side handler for the client's delete-todo mutation.
(pc/defmutation delete-todo-mutation [_env {:todo/keys [id]}]
  {::pc/sym    'learn.client/delete-todo
   ::pc/output [:todo/id]}
  (swap! server/SERVER-DB server/delete-todo {:todo/id id})
  {:todo/id id})

;; ----------------------------------------------------------------------
;; Registry — every resolver/mutation in this namespace, listed once.
;; The parser uses this to know which functions exist.
;;
;; In production code, this is often automated via a custom defresolver
;; macro that registers into a global atom. For learning, an explicit
;; vector is clearer.
;; ----------------------------------------------------------------------

(def all-resolvers
  [all-todos-resolver
   todo-resolver
   add-todo-mutation
   delete-todo-mutation])

;; Note: To see Pathom in action, enable debug from the REPL:
;; (require 'learn.parser :reload)
;; (in-ns 'learn.parser)
;; (alter-var-root #'*debug?* (constantly true))
