(ns learn.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.headless :as h]
    ; reader conditional CLJC pattern
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj [com.fulcrologic.fulcro.dom-server :as dom])))

(defsc Person [this {:person/keys [id name age]}]
  {:query [:person/id :person/name :person/age]
   :ident :person/id
   :initial-state (fn [{:keys [id name age]}]
                    {:person/id id
                     :person/name name
                     :person/age age})}
  (dom/li (str name " (age " age ")")))

(def ui-person (comp/factory Person {:keyfn :person/id}))

(defsc Greeting [this {:greeting/keys [id text]}]
  {:query [:greeting/id :greeting/text]
   :ident :greeting/id
   :initial-state (fn [{:keys [id text]}]
                    {:greeting/id id :greeting/text text})}
  (dom/h1 text))

(def ui-greeting (comp/factory Greeting {:keyfn :greeting/id}))

(defsc Root [this {:keys [greeting people]}]
  {:query [{:greeting (comp/get-query Greeting)}
           {:people (comp/get-query Person)}]
   :initial-state
   (fn [_]
     {:greeting (comp/get-initial-state Greeting
                  {:id 1 :text "Hello Fulcro!"})
      :people [(comp/get-initial-state Person
                 {:id 1 :name "Alice" :age 30})
               (comp/get-initial-state Person
                 {:id 2 :name "Bob" :age 25})]})}
  (dom/div
    (when greeting
      (ui-greeting greeting))
    (dom/h2 "People:")
    (dom/ul (mapv ui-person people))
    ))

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
   :hiccup
   (h/hiccup-frame @SPA)})

(comment
  (do
    (init)
    (snapshot))

  ;; What does Root see as its props? Useful for
  ;; understanding denormalization. This shows us
  ;; the denormalized tree that Root's props
  ;; argument actually receives.
  (do (init)
      (com.fulcrologic.fulcro.algorithms.denormalize/db->tree
        (comp/get-query Root)
        (app/current-state @SPA)
        (app/current-state @SPA)))

  ;; Just the people table:
  (do (init) (get (app/current-state @SPA) :person/id))

  ;; Getting a specific entity by ident:
  (do (init)
      (get-in
        (app/current-state @SPA)
        [:person/id 2]))
  )
