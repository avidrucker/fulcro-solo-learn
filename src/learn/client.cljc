(ns learn.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.headless :as h]
    ; reader conditional CLJC pattern
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj [com.fulcrologic.fulcro.dom-server :as dom])))

(defsc Root [this {:keys [greeting subtitle]}]
  {:query [:greeting :subtitle]
   :initial-state {:greeting "Hello Fulcro!"
                   :subtitle "Now my props come from the database."}}
  (dom/div
    (dom/h1 greeting)
    (dom/p subtitle)))

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
  )
