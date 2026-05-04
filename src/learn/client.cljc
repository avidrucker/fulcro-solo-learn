(ns learn.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
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
    (dom/button {:onClick #(comp/transact! this [(delete-todo {:todo-id id})])}
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
  (let [next-id  (inc (apply max 0 (keys (:todo/id state-map))))
        new-todo {:todo/id next-id :todo/text text :todo/done? false}]
    (-> state-map
      (merge/merge-component TodoItem new-todo
        :append (conj list-ident :list/todos))
      (assoc-in (conj list-ident :ui/new-todo-text) ""))))

(defn delete-todo* [state-map todo-id]
  (nsh/remove-entity state-map [:todo/id todo-id]))

(defmutation add-todo [{:keys [text]}]
  (action [{:keys [state ref]}]
    (swap! state add-todo* ref text)))

(defmutation delete-todo [{:keys [todo-id]}]
  (action [{:keys [state]}]
    (swap! state delete-todo* todo-id)))

(defn edit-todo* [state-map todo-id new-text]
  (if (get-in state-map [:todo/id todo-id])
    (assoc-in state-map [:todo/id todo-id :todo/text] new-text)
    state-map))

(defn delete-all* [state-map list-ident]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce (fn [s [_table id]] (delete-todo* s id))
      state-map
      todo-idents)))

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

(defmutation mark-all-complete [{:keys [done?]}]
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

(defn init []
  (let [spa
        ; headless equivalent of app/fulcro-app
        (h/build-test-app {:root-class Root})]
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
