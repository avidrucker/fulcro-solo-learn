(ns learn.client
  "Fulcro client UI, mutations, and pure state-helpers for the TODO app.

   Layered structure:
     - TodoItem / TodoList / Root  : UI components (defsc)
     - *-suffixed fns              : pure state-map → state-map helpers
     - defmutations                : thin wrappers that swap! the helpers
                                     into the live app state atom
     - init / SPA                  : app construction + headless mount

   Server and parser live in sibling namespaces. The client sees them
   only via the loopback remote configured in `init`."
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.fulcro.headless.loopback-remotes :as lr]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
    [learn.parser :as parser]
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])))

(declare delete-todo add-todo)

;; ============================================================================
;; UI components
;; ============================================================================

(defsc TodoItem [this {:todo/keys [id text done?]}]
  {:query [:todo/id :todo/text :todo/done?]
   :ident :todo/id}
  ;; No :initial-state — TodoItems are populated by loads or by add-todo,
  ;; never seeded by their parent. Keeping initial-state off makes that
  ;; expectation explicit.
  (dom/li
    (dom/span (if done? (str "[x] " text) (str "[ ] " text)))
    (dom/button {:onClick #(m/toggle! this :todo/done?)}
      "Toggle")
    (dom/button {:onClick #(comp/transact! this [(delete-todo {:todo/id id})])}
      "Delete")))

(def ui-todo-item (comp/factory TodoItem {:keyfn :todo/id}))

(defsc TodoList [this {:list/keys [todos] :ui/keys [new-todo-text]}]
  {:query         [:list/id
                   {:list/todos (comp/get-query TodoItem)}
                   :ui/new-todo-text]
   :ident         :list/id
   :initial-state (fn [_]
                    {:list/id          1
                     :list/todos       []
                     :ui/new-todo-text ""})}
  (dom/div
    (dom/h1 "TODOs")
    (dom/ul (mapv ui-todo-item todos))
    (dom/label {:htmlFor "new-todo"} "New TODO:")
    (dom/input {:id       "new-todo"
                :value    (or new-todo-text "")
                :onChange #(m/set-string! this :ui/new-todo-text :event %)})
    (dom/button {:onClick #(comp/transact! this [(add-todo {:todo/text new-todo-text})])}
      "Add")))

(def ui-todo-list (comp/factory TodoList {:keyfn :list/id}))

(defsc Root [this {:keys [list]}]
  {:query         [{:list (comp/get-query TodoList)}]
   :initial-state (fn [_]
                    {:list (comp/get-initial-state TodoList {})})}
  (dom/div
    (when list (ui-todo-list list))))

;; ============================================================================
;; Pure state helpers — independently testable; mutations wrap them.
;; ============================================================================

(defn add-todo*
  "Returns a new state-map with a fresh todo appended to the given list
   and :ui/new-todo-text on that list cleared. The new todo's id is a
   client-generated UUID (Phase 5 will introduce tempids for client/server
   id reconciliation)."
  [state-map list-ident text]
  (let [new-id   (random-uuid)
        new-todo {:todo/id new-id :todo/text text :todo/done? false}]
    (-> state-map
      (merge/merge-component TodoItem new-todo
        :append (conj list-ident :list/todos))
      (assoc-in (conj list-ident :ui/new-todo-text) ""))))

(defn delete-todo*
  "Removes the todo with `id` from `:todo/id` and from any list referencing it."
  [state-map id]
  (nsh/remove-entity state-map [:todo/id id]))

(defn edit-todo*
  "Updates :todo/text on an existing todo. No-op if the todo doesn't exist
   (avoids `assoc-in` accidentally creating a partial entity)."
  [state-map todo-id new-text]
  (if (get-in state-map [:todo/id todo-id])
    (assoc-in state-map [:todo/id todo-id :todo/text] new-text)
    state-map))

(defn delete-all*
  "Removes every todo referenced by the given list-ident's :list/todos.
   Composes `delete-todo*` (via `nsh/remove-entity`) once per todo —
   reusing the rule rather than re-implementing it."
  [state-map list-ident]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce nsh/remove-entity state-map todo-idents)))

(defn set-complete*
  "Sets :todo/done? on a single todo. Centralizes the path so any future
   schema change happens in one place."
  [state-map todo-id done?]
  (assoc-in state-map [:todo/id todo-id :todo/done?] done?))

(defn mark-all-complete*
  "Sets :todo/done? on every todo in the given list to `done?`."
  [state-map list-ident done?]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce (fn [s [_table id]] (set-complete* s id done?))
      state-map
      todo-idents)))

;; ============================================================================
;; Mutations — thin wrappers that route helpers through swap!.
;;
;; Mutations with a (remote [_] true) section are sent to the server as
;; well as applied locally (optimistic updates). The server's response
;; can be merged back; we currently rely on initial-load to keep things
;; eventually consistent.
;; ============================================================================

(defmutation add-todo [{:todo/keys [text]}]
  (action [{:keys [state ref]}]
    (swap! state add-todo* ref text))
  (remote [_] true))

(defmutation delete-todo [{:todo/keys [id]}]
  (action [{:keys [state]}]
    (swap! state delete-todo* id))
  (remote [_] true))

(defmutation edit-todo [{:todo/keys [id]
                         new-text   :todo/text}]
  (action [{:keys [state]}]
    (swap! state edit-todo* id new-text)))

(defmutation delete-all [_]
  (action [{:keys [state ref]}]
    (swap! state delete-all* ref)))

(defmutation mark-all-complete [{:list/keys [done?]}]
  (action [{:keys [state ref]}]
    (swap! state mark-all-complete* ref done?)))

;; ============================================================================
;; App construction
;; ============================================================================

(defonce SPA
  ;; Holds the live app instance. defonce so reloading the namespace
  ;; doesn't blow away an in-progress app you've been driving from REPL.
  (atom nil))

(defn init
  "Build, mount, and load the app. Returns the spa.

   Side effects:
     - Resets `SPA` to the new app instance.
     - Issues an immediate `df/load!` to populate :list/todos from the server.

   The remote is a `sync-remote` wrapping `parser/handler` — meaning loads
   and remote mutations resolve synchronously in-process. This is what
   makes headless TDD feasible."
  []
  (let [spa (h/build-test-app
              {:root-class Root
               :remotes    {:remote (lr/sync-remote parser/handler)}})]
    (reset! SPA spa)
    (app/mount! spa Root :app)
    (df/load! spa :all-todos TodoItem
      {:target [:list/id 1 :list/todos]})
    (h/render-frame! spa)
    spa))

(defn snapshot
  "Returns the current normalized state. Useful from REPL to inspect
   what loaded, what mutated, and where things landed in the graph."
  []
  {:state (app/current-state @SPA)})

;; ============================================================================
;; REPL playground — one canonical scratchpad. Edit and re-eval forms here
;; rather than maintaining many comment blocks.
;; ============================================================================

(comment
  ;; Fresh start: server seed + new app + load. snapshot to inspect.
  (do
    (require 'learn.server)
    (learn.server/reset!)
    (init)
    (snapshot))

  ;; Trigger a UI interaction via simulated clicks/typing:
  (do
    (h/type-into-labeled! @SPA "New TODO" "Pet the cat")
    (h/click-on-text! @SPA "Add")
    (h/render-frame! @SPA)
    (snapshot))

  ;; Toggle the first todo's done state:
  (do
    (h/click-on-text! @SPA "Toggle" 0)
    (h/render-frame! @SPA)
    (snapshot))

  ;; Delete the first todo (now also persists to the server):
  (do
    (h/click-on-text! @SPA "Delete" 0)
    (h/render-frame! @SPA)
    (snapshot))

  ;; Compare both worlds:
  @learn.server/SERVER-DB
  (:todo/id (app/current-state @SPA)))
