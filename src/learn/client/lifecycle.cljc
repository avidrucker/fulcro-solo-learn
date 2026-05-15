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
    [learn.review.chart :as chart]
    [learn.util.storage :as storage]
    [learn.util.url-encoding :as url-encoding]))

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

#?(:cljs
   (defn sync-html-lang!
     "Phase 19 a11y — set `<html lang=\"...\">` to reflect the active
      `:ui/locale`. Screen readers use this attribute to pick the
      right pronunciation / voice for content. Without it the
      browser guesses (usually defaults to the OS locale), which
      means a Japanese user reading our Spanish UI would get the
      Japanese voice attempting to read Spanish text.

      Maps :en/:es/:ja to the matching IETF tag (which happens to
      equal the locale keyword name for our supported set)."
     [locale]
     (.setAttribute (.-documentElement js/document) "lang" (name locale))))

#?(:cljs
   (defn install-html-lang-sync!
     "Phase 19 a11y — watch the Fulcro state-atom and keep
      `<html lang>` in sync with `:ui/locale`. Same pattern as
      `install-body-theme-sync!`."
     [fulcro-state-atom]
     (let [locale-of (fn [s] (get-in s [:list/id 1 :ui/locale] :en))]
       (sync-html-lang! (locale-of @fulcro-state-atom))
       (add-watch fulcro-state-atom ::html-lang
         (fn [_k _ref old-state new-state]
           (let [old-locale (locale-of old-state)
                 new-locale (locale-of new-state)]
             (when (not= old-locale new-locale)
               (sync-html-lang! new-locale))))))))

(defn load-todos!
  "Initial load that populates `:list/todos` from the in-process Pathom
   parser. Same call shape on both platforms. Takes `todo-item-class`
   as a parameter so we don't have to require `learn.client.ui.components`
   here (and create a cycle through `learn.client`'s SPA reference)."
  [spa todo-item-class]
  (df/load! spa :all-todos todo-item-class
    {:target [:list/id 1 :list/todos]}))

(defn install-url-locale-fallback!
  "Phase 14 + Phase 18 — resolve the relationship between localStorage
   `:ui/locale` (saved preference) and URL `?lang=<code>` (URL hint
   for sharing).

   Decision logic in `url-encoding/locale-decision`:
     :apply    — first-time visitor + URL hint; swap state to URL value
     :conflict — saved present + URL differs; open the locale-conflict
                 modal so the user picks (Phase 18 / S-language-conflict-modal)
     :no-op    — no URL hint, or saved matches URL

   The persistence watch (installed earlier in `init` via
   `storage/install-ui-prefs-persistence!`) handles writing the
   user's chosen locale back to localStorage in both the :apply
   and :conflict-resolution paths.

   JVM: no-op (the headless test suite doesn't exercise URL-based
   locale selection)."
  [fulcro-state-atom]
  #?(:cljs
     (let [saved-locale (some-> (storage/load-ui-prefs!) :ui/locale)
           url-locale   (url-encoding/locale-from-current-url)
           decision     (url-encoding/locale-decision saved-locale url-locale)]
       (case (:action decision)
         :apply
         (swap! fulcro-state-atom
           assoc-in [:list/id 1 :ui/locale] (:locale decision))

         :conflict
         (swap! fulcro-state-atom
           (fn [s]
             (-> s
               (assoc-in [:list/id 1 :ui/locale-conflict-pair]
                 (select-keys decision [:saved :url]))
               (assoc-in [:list/id 1 :ui/open-modal] :locale-conflict))))

         ;; :no-op — nothing to do
         nil))))
