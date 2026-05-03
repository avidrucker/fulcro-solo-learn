(ns learn.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.headless :as h]
    ; reader conditional CLJC pattern
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj [com.fulcrologic.fulcro.dom-server :as dom])))

(defsc Greeting [this {:greeting/keys [id text]}]
  {:query [:greeting/id :greeting/text]
   :ident :greeting/id
   :initial-state (fn [{:keys [id text]}]
                    {:greeting/id id :greeting/text text})}
  (dom/h1 text))

(def ui-greeting (comp/factory Greeting {:keyfn :greeting/id}))

(defsc Root [this {:keys [greeting]}]
  {:query [{:greeting (comp/get-query Greeting)}]
   :initial-state
   (fn [_]
     {:greeting (comp/get-initial-state Greeting
                  {:id 1 :text "Hello Fulcro!"})})}
  (dom/div
    (when greeting
      (ui-greeting greeting))
    (dom/p "Below the heading, in plain Root.")
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
  )
