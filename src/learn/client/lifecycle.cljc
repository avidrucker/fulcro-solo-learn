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
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [learn.client.session :as session]
    [learn.review.chart :as chart]
    [learn.util.storage :as storage]
    [learn.util.url-encoding :as url-encoding]))

;; Phase 19g/19h a11y — declare the modal-close mutation by qualified
;; name so we can `transact!` from the lifecycle layer without pulling
;; in `learn.client` (which would create a cycle). The mutation itself
;; is defined in `learn.client` and resolved at dispatch time, after
;; both namespaces have loaded.
(m/declare-mutation set-open-modal learn.client/set-open-modal)

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

;; ============================================================================
;; Phase 19g/19h a11y — modal focus management + Escape-to-close
;; ============================================================================

(def ^:private modal-id->heading-id
  "Phase 19g — maps a `:ui/open-modal` value to the DOM `id` of the
   heading/question element inside that modal (set in Phase 19b for
   `aria-labelledby`). Focus management uses this id to focus the
   correct heading when the modal mounts."
  {:info            "info-modal-title"
   :settings        "settings-modal-title"
   :save            "save-modal-title"
   :delete-confirm  "delete-confirm-question"
   :conflict        "list-conflict-question"
   :locale-conflict "locale-conflict-question"})

(def ^:private dismissible-modals
  "Phase 19h — modals that close on Escape. The two conflict modals
   are excluded by design: the user MUST resolve the conflict, no
   silent dismissal."
  #{:info :settings :save :delete-confirm})

#?(:cljs
   (defonce ^:private prev-focus-element
     ;; Snapshot of `document.activeElement` at the moment a modal
     ;; opens, so we can restore focus on close. defonce so a
     ;; hot-reload mid-modal doesn't lose the snapshot.
     (atom nil)))

#?(:cljs
   (defn- focus-modal-heading!
     "Locate the modal's heading element by id and focus it. Polls
      via `requestAnimationFrame` until the element exists, with a
      ~10-frame ceiling (~160ms at 60fps) — Fulcro's render lands
      on a later frame than the state-atom watcher's tick, so a
      naive `setTimeout 0` runs BEFORE the modal mounts.

      Adds `tabindex=-1` only as a defensive fallback; heading
      elements ship with `:tabIndex \"-1\"` declaratively so React
      preserves it across re-renders."
     [modal-id]
     (when-let [heading-id (get modal-id->heading-id modal-id)]
       (letfn [(try-focus! [attempts]
                 (if-let [el (.getElementById js/document heading-id)]
                   (do
                     (when-not (.hasAttribute el "tabindex")
                       (.setAttribute el "tabindex" "-1"))
                     (.focus el))
                   (when (pos? attempts)
                     (js/requestAnimationFrame
                       #(try-focus! (dec attempts))))))]
         (js/requestAnimationFrame #(try-focus! 10))))))

#?(:cljs
   (defn- restore-previous-focus!
     "Re-focus whatever element had focus before the modal opened.
      No-op if the previous element is gone (e.g. unmounted)."
     []
     (when-let [prev @prev-focus-element]
       (try
         (.focus prev)
         (catch :default _e nil))
       (reset! prev-focus-element nil))))

#?(:cljs
   (defn install-modal-focus-sync!
     "Phase 19g a11y — focus management on modal open/close.

      On open (transition :none → modal-id):
        - Snapshot `document.activeElement`.
        - On next tick (so React mounts the modal first), focus the
          modal's heading element by id. Set `tabindex=-1` if missing
          so the heading is programmatically focusable.
      On close (transition modal-id → :none):
        - Restore focus to the previously-active element.
      On modal-to-modal transition (rare):
        - Focus the new modal's heading; previous snapshot survives.

      Covers the six `:ui/open-modal`-driven modals. The review modal
      is statechart-driven and is NOT covered — future work."
     [fulcro-state-atom]
     (let [open-of (fn [s] (get-in s [:list/id 1 :ui/open-modal] :none))]
       (add-watch fulcro-state-atom ::modal-focus
         (fn [_k _ref old-state new-state]
           (let [old-modal (open-of old-state)
                 new-modal (open-of new-state)]
             (when (not= old-modal new-modal)
               (cond
                 (and (= old-modal :none) (not= new-modal :none))
                 (do
                   (reset! prev-focus-element (.-activeElement js/document))
                   (focus-modal-heading! new-modal))

                 (and (not= old-modal :none) (= new-modal :none))
                 (restore-previous-focus!)

                 :else
                 (focus-modal-heading! new-modal)))))))))

#?(:cljs
   (defn install-review-modal-focus-sync!
     "Phase 19g a11y (extension) — focus management for the review
      modal, which is statechart-driven and not represented in
      `:ui/open-modal`. Watches the statechart session configuration
      at `[:com.fulcrologic.statecharts/session-id :review-session
      :com.fulcrologic.statecharts/configuration]`; when the
      `:review.state/active` keyword enters the set, the modal is
      visible — snapshot focus and move it to the `review-question`
      element. When the keyword leaves, restore focus.

      Shares `prev-focus-element` with `install-modal-focus-sync!`
      — safe because the review modal and the :ui/open-modal menu
      modals are mutually exclusive by construction (the
      Prioritize button is disabled while a menu modal is up, and
      menu-disabling logic blocks menu modals while review is
      active).

      Caveat: the heading id is hardcoded here. If the review
      modal's `<p id=\"review-question\">` is ever renamed, this
      reference must update. The id is asserted in
      `manual_tests.md` §19b.2 to slow that drift."
     [fulcro-state-atom]
     (let [config-path [:com.fulcrologic.statecharts/session-id
                        :review-session
                        :com.fulcrologic.statecharts/configuration]
           active-state :review.state/active
           active-of    (fn [s]
                          (boolean
                            (contains? (get-in s config-path #{})
                              active-state)))]
       (add-watch fulcro-state-atom ::review-modal-focus
         (fn [_k _ref old-state new-state]
           (let [was-active? (active-of old-state)
                 is-active?  (active-of new-state)]
             (cond
               (and (not was-active?) is-active?)
               (do
                 (reset! prev-focus-element (.-activeElement js/document))
                 ;; rAF poll mirrors `focus-modal-heading!` — naive
                 ;; setTimeout 0 fires before Fulcro renders the modal,
                 ;; so the heading element doesn't yet exist.
                 (letfn [(try-focus! [attempts]
                           (if-let [el (.getElementById js/document "review-question")]
                             (do
                               (when-not (.hasAttribute el "tabindex")
                                 (.setAttribute el "tabindex" "-1"))
                               (.focus el))
                             (when (pos? attempts)
                               (js/requestAnimationFrame
                                 #(try-focus! (dec attempts))))))]
                   (js/requestAnimationFrame #(try-focus! 10))))

               (and was-active? (not is-active?))
               (restore-previous-focus!))))))))

#?(:cljs
   (defn install-escape-to-close!
     "Phase 19h a11y — window-level keydown listener that closes the
      current `:ui/open-modal` on Escape, but only for dismissible
      modals (`dismissible-modals` set). The two conflict modals stay
      non-dismissible by design.

      Dispatched via the Fulcro mutation pipeline (`set-open-modal`)
      so any future side effects of closing — persistence, statechart
      events — stay consistent with the background-click and
      close-button paths."
     [spa]
     (let [state-atom (:com.fulcrologic.fulcro.application/state-atom spa)]
       (.addEventListener js/window "keydown"
         (fn [e]
           (when (= "Escape" (.-key e))
             (let [open-modal (get-in @state-atom [:list/id 1 :ui/open-modal] :none)]
               (when (contains? dismissible-modals open-modal)
                 (.preventDefault e)
                 (comp/transact! spa
                   [(set-open-modal {:ui/open-modal :none})])))))))))

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
