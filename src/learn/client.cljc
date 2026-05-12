(ns learn.client
  "Fulcro client UI, mutations, and pure state-helpers for the AutoFocus app.

   Status enum (per AutoFocus spec):
     :status/new       — added but not yet reviewed
     :status/ready     — actionable
     :status/done      — completed
     :status/cancelled — explicitly cancelled (preserves prior status in :todo/was)

   Layered structure:
     - TodoItem / TodoList / Root  : UI components (defsc)
     - *-suffixed fns              : pure state-map → state-map helpers
     - defmutations                : thin wrappers that swap! the helpers
                                     into the live app state atom
     - init / SPA                  : app construction + headless mount"
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.fulcro.headless.loopback-remotes :as lr]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [learn.parser :as parser]
    [learn.model.list :as model.list]
    [learn.model.review :as review]
    [learn.review.chart :as chart]
    [learn.util.normalized :as norm]
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
  {:query [:todo/id :todo/text :todo/status :todo/was]
   :ident :todo/id}
  ;; No :initial-state — TodoItems are populated by loads or by add-todo,
  ;; never seeded by their parent. Keeping initial-state off makes that
  ;; expectation explicit.
  (dom/li
    (dom/span (str (status-symbol status) " " text))
    (dom/button {:onClick #(comp/transact! this [(cancel-todo {:todo/id id})])}
      "Cancel")))

(def ui-todo-item (comp/factory TodoItem {:keyfn :todo/id}))

(defn- send-and-pump!
  "Dispatch a chart event and immediately drain the statechart event queue.
   Required because `init` installs with `:event-loop? false` — without the
   pump, `scf/send!` only enqueues and tests would have to drive the loop
   themselves. Pumping here makes onClicks behave synchronously, which also
   matches the project's broader headless / sync-remote stance."
  [app-ish event]
  (scf/send! app-ish review-session-id event)
  (scf/process-events! app-ish))

(defn- review-cursor
  "Reads the chart session's local `:cursor` from the Fulcro state-map. The
   chart stores its only session-local datum (the cursor) here; the UI uses
   it to render the current-question prompt."
  [app-ish]
  (get-in (app/current-state app-ish)
    [:com.fulcrologic.statecharts/local-data review-session-id :cursor]))

(defsc TodoList [this {:list/keys [todos] :ui/keys [new-todo-text]}]
  {:query         [:list/id
                   {:list/todos (comp/get-query TodoItem)}
                   :ui/new-todo-text]
   :ident         :list/id
   :initial-state (fn [_]
                    {:list/id          1
                     :list/todos       []
                     :ui/new-todo-text ""})}
  (let [config   (scf/current-configuration this review-session-id)
        active?  (contains? config chart/active)
        cursor   (when active? (review-cursor this))
        question (when (and active? cursor)
                   (review/current-question todos cursor))]
    (dom/div
      (dom/h1 "AutoFocus WIP in Fulcro")
      (dom/ul (mapv ui-todo-item todos))
      (if active?
        (dom/div
          (when question (dom/p question))
          (dom/button {:onClick #(send-and-pump! this chart/event-yes)} "Yes")
          (dom/button {:onClick #(send-and-pump! this chart/event-no)} "No")
          (dom/button {:onClick #(send-and-pump! this chart/event-quit)} "Quit"))
        (dom/button {:onClick #(send-and-pump! this chart/event-start)} "Start Review"))
      (dom/label {:htmlFor "new-todo"} "New TODO:")
      (dom/input {:id       "new-todo"
                  :value    (or new-todo-text "")
                  :onChange #(m/set-string! this :ui/new-todo-text :event %)})
      (dom/button {:onClick #(comp/transact! this [(add-todo {:todo/text new-todo-text})])}
        "Add"))))

(def ui-todo-list (comp/factory TodoList {:keyfn :list/id}))

(defsc Root [this {:keys [list]}]
  {:query         [{:list (comp/get-query TodoList)}]
   :initial-state (fn [_]
                    {:list (comp/get-initial-state TodoList {})})}
  (dom/div
    (when list (ui-todo-list list))))

;; ============================================================================
;; Pure state helpers — independently testable; mutations wrap them.
;;
;; Note: The AutoFocus model intentionally keeps the API surface minimal.
;; There is no edit-todo, no individual delete - the user is prevented from
;; micromanaging. Cancel + clone serve those needs from a different angle.
;; ============================================================================

(defn add-todo*
  "Append a fresh todo to the given list and clear :ui/new-todo-text.

   Status determination delegates to learn.model.list/add-todo, which
   applies the AutoFocus add rule (SCHEMA.md §7):
     - If the list has zero :status/ready items → new todo gets :status/ready.
     - Otherwise (at least one ready exists)    → new todo gets :status/new.

   Blank text returns state unchanged. The model returns
   {:ok? false :error/type :error/blank-item}; we no-op here for now.
   A future phase can surface the error via UI feedback."
  [state-map list-ident text]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/add-todo items text)]
    (if (:ok? result)
      (let [new-todo (last (:items result))]
        (-> state-map
          (merge/merge-component TodoItem new-todo
            :append (conj list-ident :list/todos))
          (assoc-in (conj list-ident :ui/new-todo-text) "")))
      state-map)))

(defn delete-all*
  "Removes every todo referenced by the given list-ident's :list/todos.
   Used by the 'Delete List' operation in the AutoFocus model."
  [state-map list-ident]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce nsh/remove-entity state-map todo-idents)))

;; ============================================================================
;; cancel-todo* / complete-benchmark-item* / clone-todo*
;; Each helper denormalizes state → calls the corresponding model.list fn →
;; on :ok? reprojects via norm/sync-items, on refusal returns state unchanged.
;; ============================================================================

(defn cancel-todo*
  "State-helper for the cancel-todo mutation. Refusal is a no-op."
  [state-map list-ident todo-id]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/cancel-todo items todo-id)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

(defn complete-benchmark-item*
  "State-helper for the complete-benchmark-item mutation. Refusal is a no-op."
  [state-map list-ident]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/complete-benchmark-item items)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

(defn clone-todo*
  "State-helper for the clone-todo mutation. Refusal is a no-op."
  [state-map list-ident todo-id]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/clone-todo items todo-id)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

;; TODO: add status change enforcement mechanics - perhaps this
;; could/should be a state chart?
(defn set-status*
  "Sets :todo/status on a single todo. Centralizes the path so any future
   schema change happens in one place. If todo is set to cancelled, then
   :todo/was will also be set to the previous status for rendering purposes."
  [state-map todo-id status]

  (let [path [:todo/id todo-id :todo/status]
        prev-status (get-in state-map path)]
    (cond
      ;; when transitioning into cancelled, we store the previous status
      ;; and then update
      (and (= status :status/cancelled)
        (not= prev-status :status/cancelled))
      (-> state-map
        (assoc-in [:todo/id todo-id :todo/was] prev-status)
        (assoc-in path :status/cancelled))
      ;; any other status: just set it
      :else
      (assoc-in state-map path status))))

;; ============================================================================
;; Mutations — thin wrappers that route helpers through swap!.
;; Mutations that hit the server send the post-action items vector via
;; `remote-list-items`; the server records it verbatim (no domain logic
;; on the backend).
;; ============================================================================

(defn- remote-list-items
  "Builds a remote AST whose params carry the current denormalized list
   at [:list/id 1] as `:list/items`. Server mutations write this vector
   straight to SERVER-DB."
  [env]
  (let [items (norm/denormalize-list-items @(:state env) [:list/id 1])]
    (m/with-params env {:list/items items})))

(defmutation add-todo [{:todo/keys [text]}]
  (action [{:keys [state ref]}]
    (swap! state add-todo* ref text))
  (remote [env] (remote-list-items env)))

(defmutation delete-all [_]
  (action [{:keys [state ref]}]
    (swap! state delete-all* ref))
  #_(remote [_] true)               ; no server handler
  )

(defmutation set-status [{:todo/keys [id status]}]
  (action [{:keys [state]}]
    (swap! state set-status* id status))
  #_(remote [_] true)               ; no server handler (admin/REPL-only)
  )

;; List-ident is hardcoded `[:list/id 1]` for the current singleton-list
;; design; revisit when multi-list support arrives.
(defmutation cancel-todo [{:todo/keys [id]}]
  (action [{:keys [state]}]
    (swap! state cancel-todo* [:list/id 1] id))
  (remote [env] (remote-list-items env)))

(defmutation complete-benchmark-item [_]
  (action [{:keys [state]}]
    (swap! state complete-benchmark-item* [:list/id 1]))
  (remote [env] (remote-list-items env)))

(defmutation clone-todo [{:todo/keys [id]}]
  (action [{:keys [state]}]
    (swap! state clone-todo* [:list/id 1] id))
  (remote [env] (remote-list-items env)))

;; Remote-only mutation fired from the review chart's :yes action. The
;; chart has already mutated the client state-map via `ops/assign`; this
;; defmutation has no `(action ...)` body because there's no client work
;; left to do. Its `(remote ...)` ships the post-action items vector to
;; the server's `sync-list` mutation.
(defmutation sync-list [_]
  (remote [env] (remote-list-items env)))

;; ============================================================================
;; App construction
;; ============================================================================

(defonce SPA
  ;; Holds the live app instance. defonce so reloading the namespace
  ;; doesn't blow away an in-progress app you've been driving from REPL.
  (atom nil))

;; Well-known singleton session id for the review chart. The chart runs at
;; most one session at a time per app (SCHEMA.md §13 "One per app instance"),
;; so a keyword id is sufficient; no need to mint random UUIDs.
(def review-session-id :review-session)

;; Registry key for the review chart definition on the Fulcro app.
(def review-chart-key ::review-chart)

(defn init
  "Build, mount, and load the app. Returns the spa.

   Side effects:
     - Resets `SPA` to the new app instance.
     - Installs statecharts on the app with `:event-loop? false` (so tests
       can drain the queue deterministically via `scf/process-events!`).
     - Registers and starts the review chart at `review-session-id`.
     - Issues an immediate `df/load!` to populate :list/todos from the server.

   The remote is a `sync-remote` wrapping `parser/handler` — meaning loads
   and remote mutations resolve synchronously in-process. This is what
   makes headless TDD feasible."
  []
  (let [spa (h/build-test-app
              {:root-class Root
               :remotes    {:remote (lr/sync-remote parser/handler)}})]
    (reset! SPA spa)
    (scf/install-fulcro-statecharts! spa {:event-loop? false})
    (scf/register-statechart! spa review-chart-key chart/chart)
    (scf/start! spa {:machine    review-chart-key
                     :session-id review-session-id})
    (scf/process-events! spa)
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
    (require '[learn.server :as server])
    (server/seed!)
    (init)
    (snapshot))

  ;; Trigger a UI interaction via simulated clicks/typing:
  (do
    (h/type-into-labeled! @SPA "New TODO" "Pet the cat")
    (h/click-on-text! @SPA "Add")
    (h/render-frame! @SPA)
    (snapshot))

  ;; Cancel a loaded todo by id (use a real one from snapshot first):
  (let [first-id (-> @SPA app/current-state :todo/id keys first)]
    (comp/transact! @SPA [(cancel-todo {:todo/id first-id})])
    (h/render-frame! @SPA)
    (snapshot))

  ;; Compare both worlds:
  @learn.server/SERVER-DB

  (:todo/id (app/current-state @SPA))

  )
