(ns learn.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
    [com.fulcrologic.fulcro.headless.loopback-remotes :as lr]
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])))

(declare delete-todo add-todo)

(defsc TodoItem [this {:todo/keys [id text done?]}]
  {:query [:todo/id :todo/text :todo/done?]
   :ident :todo/id
   :initial-state
   (fn [{:keys [id text done?]}]
     {:todo/id id
      :todo/text text
      :todo/done? (boolean done?)})}
  (dom/li
    (dom/span (if done? (str "[x] " text) (str "[ ] " text)))
    (dom/button {:onClick #(m/toggle! this :todo/done?)}
      "Toggle")
    (dom/button {:onClick #(comp/transact! this [(delete-todo {:todo/id id})])}
      "Delete")))

(def ui-todo-item (comp/factory TodoItem {:keyfn :todo/id}))

(defsc TodoList [this {:list/keys [todos] :ui/keys [new-todo-text]}]
  {:query [:list/id {:list/todos (comp/get-query TodoItem)}
           :ui/new-todo-text]
   :ident :list/id
   :initial-state
   (fn [_]
     {:list/id 1
      :ui/new-todo-text ""
      :list/todos [(comp/get-initial-state TodoItem
                     {:id 1 :text "Learn Fulcro" :done? false})
                   (comp/get-initial-state TodoItem
                     {:id 2 :text "Build a TODO app" :done? false})]})}
  (dom/div
    (dom/h1 "TODOs")
    (dom/ul (mapv ui-todo-item todos))
    (dom/label {:htmlFor "new-todo"} "New TODO:")
    (dom/input {:id       "new-todo"
                :value    (or new-todo-text "")
                :onChange #(m/set-string! this :ui/new-todo-text :event %)})
    (dom/button {:onClick #(comp/transact! this [(add-todo {:text new-todo-text})])}
      "Add")))

(def ui-todo-list (comp/factory TodoList {:keyfn :list/id}))

(defn add-todo* [state-map list-ident text]
  ;; idiomatic Clojure trick: using apply with a sentinel default is how
  ;; one safely handles empty-collection cases without an explicit if
  (let [new-id   (random-uuid)
        new-todo {:todo/id new-id :todo/text text :todo/done? false}]
    (-> state-map
      (merge/merge-component TodoItem new-todo
        :append (conj list-ident :list/todos))
      (assoc-in (conj list-ident :ui/new-todo-text) ""))))

(defn delete-todo* [state-map id]
  (nsh/remove-entity state-map [:todo/id id]))

(defmutation add-todo [{:todo/keys [text]}]
  (action [{:keys [state ref]}]
    (swap! state add-todo* ref text)))

(defmutation delete-todo [{:todo/keys [id]}]
  (action [{:keys [state]}]
    (swap! state delete-todo* id)))

(defn edit-todo* [state-map todo-id new-text]
  (if (get-in state-map [:todo/id todo-id])
    (assoc-in state-map [:todo/id todo-id :todo/text] new-text)
    state-map))

(defmutation edit-todo [{:todo/keys [id]
                         new-text :todo/text}]
  (action [{:keys [state]}]
    (swap! state edit-todo* id new-text)))

(defn delete-all* [state-map list-ident]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce nsh/remove-entity state-map todo-idents)))

(defmutation delete-all [_]
  (action [{:keys [state ref]}]
    (swap! state delete-all* ref)))

(defn set-complete* [state-map todo-id done?]
  (assoc-in state-map [:todo/id todo-id :todo/done?] done?))

(defn mark-all-complete* [state-map list-ident done?]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce (fn [s [_table id]] (set-complete* s id done?))
      state-map
      todo-idents)))

(defmutation mark-all-complete [{:list/keys [done?]}]
  (action [{:keys [state ref]}]
    (swap! state mark-all-complete* ref done?)))

(defsc Root [this {:keys [list]}]
  {:query [{:list (comp/get-query TodoList)}]
   :initial-state
   (fn [_]
     {:list (comp/get-initial-state TodoList {})})}
  (dom/div
    (when list (ui-todo-list list))))

(defonce SPA (atom nil))

;; -------------------------------------------------------------------
;; "Server" — a plain atom holding the canonical state.
;; In a real app this would be a database. For us, it's an atom
;; that lives in the same process but is conceptually separate
;; from the client's normalized DB.
;; -------------------------------------------------------------------

(def init-state
  {:todo/id {#uuid "11111111-1111-1111-1111-111111111111"
             {:todo/id    #uuid "11111111-1111-1111-1111-111111111111"
              :todo/text  "Read the Fulcro book"
              :todo/done? false}
             #uuid "22222222-2222-2222-2222-222222222222"
             {:todo/id    #uuid "22222222-2222-2222-2222-222222222222"
              :todo/text  "Try out remotes"
              :todo/done? true}}})

(defonce SERVER-DB
  (atom init-state))

(defn reset-server!
  "Resets the server to a known seed state. Useful between REPL runs."
  []
  (reset! SERVER-DB init-state))

;; -------------------------------------------------------------------
;; "Server" handler — receives EQL, returns a tree response.
;; This is a hand-rolled minimal parser. In real apps you'd use
;; Pathom for this; we're staying primitive to make the mechanics clear.
;; -------------------------------------------------------------------

(defn server-handler
  "Receives EQL from the client and returns a tree response.

   Supports:
   - The query [:all-todos] — returns all todos as a vector under :all-todos
   - The mutation `add-todo with {:todo/text \"...\"} — creates and returns the new todo
   - The mutation `delete-todo with {:todo/id ...}    — removes from server DB

   Other EQL is ignored (returns {})."
  [eql]
  (reduce
    (fn [response query-element]
      (cond
        ;; Query: a keyword or join-map asking for data
        (= query-element :all-todos)
        (assoc response :all-todos (vec (vals (:todo/id @SERVER-DB))))

        ;; Mutation: a list with a symbol head, e.g. (add-todo {:todo/text "..."})
        (and (list? query-element)
          (= 'learn.client/add-todo (first query-element)))
        (let [{:todo/keys [text]} (second query-element)
              new-id              (random-uuid)
              new-todo            {:todo/id new-id :todo/text text :todo/done? false}]
          (swap! SERVER-DB assoc-in [:todo/id new-id] new-todo)
          (assoc response 'learn.client/add-todo new-todo))

        (and (list? query-element)
          (= 'learn.client/delete-todo (first query-element)))
        (let [{:todo/keys [id]} (second query-element)]
          (swap! SERVER-DB update :todo/id dissoc id)
          (assoc response 'learn.client/delete-todo {}))

        :else response))
    {}
    eql))

(defn init []
  (let [spa
        ; headless equivalent of app/fulcro-app
        (h/build-test-app {:root-class Root
                           :remotes    {:remote (lr/sync-remote server-handler)}})]
    (reset! SPA spa)
    ; :app == CLJ "mount target" (like DOM element id '#app')
    (app/mount! spa Root :app)
    ; force render & capture as hiccup to read it back
    (h/render-frame! spa)
    spa))

(defn snapshot []
  {:state
   (app/current-state @SPA) ; app/current-state cannot be resolved
   #_#_:hiccup
   (h/hiccup-frame @SPA)})

(comment
  ;; 1) Fresh start. You should see two TODOs in :todo/id and in :todos.
  (do (init) (snapshot))

  ;; 2) Toggle "Learn Fulcro" via the first Toggle button.
  ;;    :done? on todo 1 should flip from false to true,
  ;;    and the hiccup should now show "[x] Learn Fulcro".
  (do (h/click-on-text! @SPA "Toggle" 0)
      (h/render-frame! @SPA)
      (snapshot))

  ;; 3) Type into the labeled input. Watch :ui/new-todo-text fill in
  ;;    in :state, and the input's :value attribute update in :hiccup.
  (do (h/type-into-labeled! @SPA "New TODO" "Try out mutations")
      (h/render-frame! @SPA)
      (snapshot))

  ;; 4) Click Add. A new entity appears in :todo/id keyed by 3,
  ;;    its ident appears at the end of :todos, and :ui/new-todo-text
  ;;    is cleared (because add-todo's action does that).
  (do (h/click-on-text! @SPA "Add")
      (h/render-frame! @SPA)
      (snapshot))

  ;; 5) Delete the first TODO. Both the entity AND the ident reference
  ;;    are gone from the DB.
  (do (h/click-on-text! @SPA "Delete" 0)
      (h/render-frame! @SPA)
      (snapshot))
  )

(comment
  ;; Build a tiny fake state and run the helper directly:
  (def fake-state {:list/id  {1 {:list/id 1
                                 :list/todos []
                                 :ui/new-todo-text "draft"}}
                   :todo/id {}})

  ;; one-off, note that fake-state actually doesn't change,
  ;; this mutation returns a new database
  (add-todo* fake-state [:list/id 1] "Try the helper")
  ;; => the new state, ready to inspect

  fake-state ;; note that this is still empty

  (-> fake-state
    (add-todo* [:list/id 1] "First")
    (add-todo* [:list/id 1] "Second")
    #_(delete-todo* 1)
    )
  ;; => composable
  )
