(ns learn.client.lifecycle
  "Phase 12.7 — app-construction helpers extracted from `learn.client`.

   Holds the singleton SPA atom, the review-chart bootstrap, the
   body-class theme sync (CLJS-only), and the initial todos load. The
   top-level `init` fn (CLJ + CLJS) stays in `learn.client` because
   shadow-cljs's `:init-fn learn.client/init` config references the
   symbol by qualified name; everything `init` reaches for lives here.

   Each fn is public so `learn.client/init` can call them without
   reaching into a private."
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [learn.client.session :as session]
    [learn.review.chart :as chart]))

(defonce SPA
  ;; Holds the live app instance. defonce so reloading the namespace
  ;; doesn't blow away an in-progress app you've been driving from REPL.
  (atom nil))

(defn start-chart!
  "Install + register + start the review chart on `spa`. Shared between
   the JVM and CLJS init branches."
  [spa]
  (scf/install-fulcro-statecharts! spa {:event-loop? false})
  (scf/register-statechart! spa session/review-chart-key chart/chart)
  (scf/start! spa {:machine    session/review-chart-key
                   :session-id session/review-session-id})
  (scf/process-events! spa))

#?(:cljs
   (defn sync-body-theme-class!
     "Set `document.body.className` to `bg-black` in dark mode (or empty
      in light) so the browser's canvas background propagates to fill
      the area outside `<body>`'s box. Without this, the default white
      canvas leaks through past `<main>`'s background when the list
      overflows the viewport — visible in light/dark snapshots with 26
      items. Matches the JS port's runtime body-class toggle."
     [theme]
     (set! (.-className js/document.body)
       (if (= theme :theme/dark) "bg-black" ""))))

#?(:cljs
   (defn install-body-theme-sync!
     "Watch the Fulcro state-atom and keep `document.body.className` in
      sync with `[:list/id 1 :ui/theme]`. Companion to
      `storage/install-ui-prefs-persistence!` — same shape (watch +
      change-detect), separate concern."
     [fulcro-state-atom]
     (let [theme-of (fn [s] (get-in s [:list/id 1 :ui/theme] :theme/light))]
       (sync-body-theme-class! (theme-of @fulcro-state-atom))
       (add-watch fulcro-state-atom ::body-theme
         (fn [_k _ref old-state new-state]
           (let [old-theme (theme-of old-state)
                 new-theme (theme-of new-state)]
             (when (not= old-theme new-theme)
               (sync-body-theme-class! new-theme))))))))

(defn load-todos!
  "Initial load that populates `:list/todos` from the in-process Pathom
   parser. Same call shape on both platforms. Takes `todo-item-class`
   as a parameter so we don't have to require `learn.client.ui.components`
   here (and create a cycle through `learn.client`'s SPA reference)."
  [spa todo-item-class]
  (df/load! spa :all-todos todo-item-class
    {:target [:list/id 1 :list/todos]}))
