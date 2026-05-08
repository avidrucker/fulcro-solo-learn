(ns learn.resolvers
  "Pathom 2 resolvers and mutations for the TODO app.

   This namespace is the production-shaped replacement for the hand-rolled
   `cond`-based parser. Each resolver/mutation is a small, declarative piece:
     - Resolvers declare their inputs and outputs; Pathom composes them.
     - Mutations declare a wire symbol (::pc/sym) that the client sends.

   At Dataico, this is roughly the shape of every server-side namespace,
   though real production code adds security checks, request-scoped env,
   and connects to a real database (Datomic) instead of an atom."
  (:require
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [learn.server :as server]))

;; ----------------------------------------------------------------------
;; Resolvers — read-only operations that produce data.
;; ----------------------------------------------------------------------

(pc/defresolver all-todos-resolver
  "Global resolver: entry point for loading every todo.
   Returns a vector of *idents* — Pathom will call the entity resolver
   below to fill in :todo/text and :todo/done? for each one."
  [_env _input]
  {::pc/output [{:all-todos [:todo/id]}]}
  {:all-todos (mapv (fn [id] {:todo/id id})
                (keys (:todo/id @server/SERVER-DB)))})

(pc/defresolver todo-resolver
  "Entity resolver: given a :todo/id, produces the rest of a todo's fields.
   This is the resolver Pathom chains to from the all-todos result."
  [_env {:todo/keys [id]}]
  {::pc/input  #{:todo/id}
   ::pc/output [:todo/text :todo/done?]}
  (let [todo (get-in @server/SERVER-DB [:todo/id id])]
    (select-keys todo [:todo/text :todo/done?])))

;; ----------------------------------------------------------------------
;; Mutations — writes that change the server's state.
;;
;; The `::pc/sym` key tells Pathom what symbol the client sends.
;; This decouples the mutation's local function name from the wire
;; protocol, addressing the namespace-coupling concern from Phase 4.
;; ----------------------------------------------------------------------

(pc/defmutation add-todo-mutation
  "Server-side handler for the client's add-todo mutation.
   Adds a todo to SERVER-DB and returns it so the client can merge."
  [_env {:todo/keys [text]}]
  {::pc/sym    'learn.client/add-todo
   ::pc/output [:todo/id :todo/text :todo/done?]}
  (let [[new-state new-todo] (server/add-todo @server/SERVER-DB
                               {:todo/text text})]
    (reset! server/SERVER-DB new-state)
    new-todo))

(pc/defmutation delete-todo-mutation
  "Server-side handler for the client's delete-todo mutation."
  [_env {:todo/keys [id]}]
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
