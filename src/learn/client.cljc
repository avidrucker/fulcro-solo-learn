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
    [com.fulcrologic.devtools.common.resolvers :refer [remote-mutations]]
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

(declare cancel-todo add-todo)

;; ============================================================================
;; UI components
;; ============================================================================

(defn status-symbol
  "Renders a four-status enum to a small text symbol."
  [status]
  (case status
    :status/new       "[ ]"
    :status/done      "[x]"
    :status/ready     "[o]"
    :status/cancelled "[~]"
    "[?]"))             ; default — covers any unexpected value

(defsc TodoItem [this {:todo/keys [id text status]}]
  {:query [:todo/id :todo/text :todo/status]
   :ident :todo/id}
  ;; No :initial-state — TodoItems are populated by loads or by add-todo,
  ;; never seeded by their parent. Keeping initial-state off makes that
  ;; expectation explicit.
  (dom/li
    (dom/span (str (status-symbol status) " " text))
    (dom/button {:onClick #(comp/transact! this [(cancel-todo {:todo/id id})])}
      "Cancel")))

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
;; Note: The AutoFocus model intentionally keeps the API surface minimal.
;; There is no edit-todo, no delete-todo, etc.. The user is prevented from
;; micromanaging their to-do list in these ways.
;; ============================================================================

(defn add-todo*
  "Returns a new state-map with a fresh todo appended to the given list
   and :ui/new-todo-text on that list cleared. New todos start :status/new."
  [state-map list-ident text]
  (let [new-id   (random-uuid)
        new-todo {:todo/id new-id
                  :todo/text text
                  :todo/status :status/new}]
    (-> state-map
      (merge/merge-component TodoItem new-todo
        :append (conj list-ident :list/todos))
      (assoc-in (conj list-ident :ui/new-todo-text) ""))))

(defn delete-all*
  "Removes every todo referenced by the given list-ident's :list/todos.
   Composes `delete-todo*` (via `nsh/remove-entity`) once per todo —
   reusing the rule rather than re-implementing it."
  [state-map list-ident]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce nsh/remove-entity state-map todo-idents)))

;; TODO: add status change enforcement mechanics - perhaps this
;; could/should be a state chart?
(defn set-status*
  "Sets :todo/status on a single todo. Centralizes the path so any future
   schema change happens in one place. If todo is set to cancelled, then
   :todo/was will also be set to the previous status for rendering purposes."
  [state-map todo-id status]
  (if (= status :status/cancelled)
    (let [previous-status (get state-map [:todo-id todo-id :todo/status])]
      (assoc-in state-map [:todo/id todo-id :todo/status] :status/cancelled)
      (assoc-in state-map [:todo/id todo-id :todo/was] previous-status))
    (assoc-in state-map [:todo/id todo-id :todo/status] status)
    )
  )

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

(defmutation delete-all [_]
  (action [{:keys [state ref]}]
    (swap! state delete-all* ref))
  (remote [_] true)
  )

(defmutation set-status [{:todo/keys [status]}]
  (action [{:keys [state ref]}]
    (swap! state set-status* ref status))
  (remote [_] true)
  )

(defmutation cancel-todo [{:todo/keys [id]}]
  (action [{:keys [state ref]}]
    (swap! state set-status* ref :status/cancelled))
  (remote [_] true)
  )

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

  ;; Compare both worlds:
  @learn.server/SERVER-DB
  (:todo/id (app/current-state @SPA)))
