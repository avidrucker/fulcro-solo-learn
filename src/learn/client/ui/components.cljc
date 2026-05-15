(ns learn.client.ui.components
  "Phase 12.7 — Fulcro UI components extracted from `learn.client`.

   Exposes:
     `TodoItem` / `ui-todo-item`   — one row in the list
     `TodoList` / `ui-todo-list`   — form, buttons, list, footer, modals slot
     `Root`                        — `<main>` shell with header icon buttons

   Plus the small chart-helper fns that the components depend on
   (`send-and-pump!`, `review-cursor`) and the focus helper for the
   new-todo input.

   Mutation aliases (`m/declare-mutation`) re-create Mutation records
   resolving to the `learn.client/<name>` wire syms, same trick used
   in `learn.client.ui.modals`. The actual `defmethod mutate`
   registrations live in `learn.client.mutations`."
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [learn.client.session :refer [review-session-id]]
    [learn.client.ui.modals :as modals]
    [learn.client.ui.theme :as theme]
    [learn.i18n.core :as i18n]
    [learn.model.list :as model.list]
    [learn.model.review :as review]
    [learn.rad.attributes :as rad-attrs]
    [learn.rad.input :as rad-input]
    [learn.review.chart :as chart]
    [learn.ui.icons :as icons]
    [learn.ui.strings :as s]
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])))

;; ============================================================================
;; Mutation aliases — Mutation records resolving to wire syms
;; `learn.client/<name>`. See learn.client.mutations docstring.
;; ============================================================================

(m/declare-mutation add-todo                learn.client/add-todo)
(m/declare-mutation cancel-todo             learn.client/cancel-todo)
(m/declare-mutation clone-todo              learn.client/clone-todo)
(m/declare-mutation complete-benchmark-item learn.client/complete-benchmark-item)
(m/declare-mutation delete-all              learn.client/delete-all)
(m/declare-mutation import-from-text        learn.client/import-from-text)
(m/declare-mutation set-err-msg             learn.client/set-err-msg)
(m/declare-mutation set-open-modal          learn.client/set-open-modal)
(m/declare-mutation toggle-theme            learn.client/toggle-theme)

;; ============================================================================
;; New-todo input focus helper (Phase 7.3)
;; ============================================================================

(def new-todo-input-id
  "DOM id of the new-todo input — also used by `focus-new-todo-input!`
   to refocus the input after Add Item / Delete List actions (Phase 7.3,
   user stories S-input-enter-submit / S-input-refocus-after-delete)."
  "new-todo")

(defn- focus-new-todo-input!
  "Refocus the new-todo input. CLJS-only (DOM only exists in the
   browser); no-op on JVM so the headless spec suite isn't disturbed.
   Headless mode also doesn't track focus the way a real browser does;
   the keep-typing UX is verified browser-manually via the Phase 7.3
   snapshot."
  []
  #?(:cljs (some-> (.getElementById js/document new-todo-input-id) .focus)
     :clj nil))

;; ============================================================================
;; TodoItem — one row in the list
;; ============================================================================

(defsc TodoItem [this {:todo/keys [id text status was]} {:keys [benchmark? theme]}]
  {:query [:todo/id :todo/text :todo/status :todo/was]
   :ident :todo/id}
  ;; No :initial-state — TodoItems are populated by loads or by add-todo,
  ;; never seeded by their parent. Keeping initial-state off makes that
  ;; expectation explicit.
  ;;
  ;; The status icon for `:status/cancelled` rows falls back to `:todo/was`
  ;; (the pre-cancel status) so the user retains a visual cue of what the
  ;; row WAS. Matches the JS port's `statusToSymbol` null-fallback path.
  (let [effective-status (if (and (= status :status/cancelled) was) was status)
        dim?             (#{:status/done :status/cancelled} status)
        actionable?      (#{:status/new :status/ready} status)
        li-class         (str "flex lh-135 align-start mb1-butlast "
                              (if benchmark? "fw6 " "fw4 ")
                              (when dim? "o-50"))
        text-class       (str "break-word"
                              (when (= status :status/cancelled) " strike"))]
    (dom/li {:className li-class}
      (dom/span {:className "mr1 dib h-15"
                 :title     (name status)}
        (icons/status-icon effective-status))
      (dom/span {:className text-class} text)
      (dom/div {:className "relative ml1 h-15 w3"}
        (if actionable?
          (dom/button {:className (theme/btn-icon-class theme)
                       :title     s/title-cancel-task
                       :aria-label s/title-cancel-task
                       :onClick   #(comp/transact! this [(cancel-todo {:todo/id id})])}
            icons/cancel-x)
          (dom/button {:className (theme/btn-icon-class theme)
                       :title     s/title-clone-task
                       :aria-label s/title-clone-task
                       :onClick   #(comp/transact! this [(clone-todo {:todo/id id})])}
            icons/repeat-arrow))))))

(def ui-todo-item (comp/factory TodoItem {:keyfn :todo/id}))

;; ============================================================================
;; Statechart helpers used inside TodoList
;; ============================================================================

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

;; ============================================================================
;; TodoList — form, action buttons, list, footer, modals slot
;; ============================================================================

(defsc TodoList [this {:list/keys [todos]
                       :ui/keys   [new-todo-text textarea-import-text
                                   open-modal theme locale err-msg
                                   conflict-url-items]
                       :or        {theme :theme/light locale :en}}]
  {:query         [:list/id
                   {:list/todos (comp/get-query TodoItem)}
                   :ui/new-todo-text
                   ;; Phase 7.12: textarea content for batch import in
                   ;; the save modal. Controlled component pattern —
                   ;; `m/set-string!` on every keystroke; cleared after
                   ;; a successful Submit.
                   :ui/textarea-import-text
                   ;; Phase 7.18: transient stash for the conflict
                   ;; modal — the URL-derived items the user can choose
                   ;; to keep over the localStorage list. Cleared by
                   ;; either Keep button. The local items live in the
                   ;; normalized list at `:list/todos`.
                   :ui/conflict-url-items
                   ;; Phase 7.4: which (if any) menu modal is currently
                   ;; open. Default `:none`. The query lives here on
                   ;; TodoList because all modals are page-level — there
                   ;; isn't a Modal component with its own ident.
                   :ui/open-modal
                   ;; Phase 7.7: `:theme/light` (default) or `:theme/dark`.
                   :ui/theme
                   ;; Phase 12.4: i18n locale. One of
                   ;; `learn.i18n.core/supported-locales`. Persisted via
                   ;; `learn.util.storage/ui-prefs-whitelist`.
                   :ui/locale
                   ;; Phase 7.9: page-level error message string, or nil.
                   :ui/err-msg
                   ;; Subscribe to the review chart's state. Without these
                   ;; ident-joins, the render reads from app state via
                   ;; `scf/current-configuration` (a side-channel Fulcro
                   ;; can't see), so the optimized renderer skips
                   ;; re-rendering TodoList when chart state changes. The
                   ;; headless test suite masks this by calling
                   ;; `h/render-frame!` after every click; the browser
                   ;; doesn't, so the joins are load-bearing for the UI.
                   ;; The body still reads via `scf/current-configuration`
                   ;; and `review-cursor` — the joins exist purely so
                   ;; Fulcro knows TodoList depends on these paths.
                   {[:com.fulcrologic.statecharts/session-id :review-session]
                    [:com.fulcrologic.statecharts/configuration]}
                   {[:com.fulcrologic.statecharts/local-data :review-session]
                    [:cursor]}]
   :ident         :list/id
   :initial-state (fn [_]
                    {:list/id                 1
                     :list/todos              []
                     :ui/new-todo-text        ""
                     :ui/textarea-import-text ""
                     :ui/conflict-url-items   nil
                     :ui/open-modal           :none
                     :ui/theme                :theme/light
                     :ui/locale               :en
                     :ui/err-msg              nil})}
  (let [config         (scf/current-configuration this review-session-id)
        active?        (contains? config chart/active)
        cursor         (when active? (review-cursor this))
        question       (when (and active? cursor)
                         (review/current-question todos cursor))
        benchmark      (model.list/benchmark-item todos)
        benchmark-id   (some-> benchmark :todo/id)
        actionable?    (some? benchmark)             ; benchmark exists iff at least one :ready
        no-todos?      (empty? todos)
        prioritizable? (review/prioritizable? todos)
        blank-input?   (str/blank? (or new-todo-text ""))
        ;; Phase 7.9 / 7.8 revised: dim the buttons whose actions
        ;; would refuse, but keep them clickable — the click surfaces
        ;; an error message via `:ui/err-msg`. The JS port has a
        ;; matching error string for every primary action now,
        ;; including Prioritize (`s/not-prioritizable-err`).
        add-dim?            blank-input?
        delete-dim?         no-todos?
        prioritize-dim?     (not prioritizable?)
        mark-done-dim?      (not actionable?)
        btn-cls            (fn [dim-or-disabled?]
                             (if dim-or-disabled?
                               (theme/btn-primary-dim-class theme)
                               (theme/btn-primary-class theme)))
        clear-err!         #(comp/transact! this [(set-err-msg {:ui/err-msg nil})])
        set-err!           (fn [msg]
                             (comp/transact! this [(set-err-msg {:ui/err-msg msg})]))
        ;; Phase 7.9: each handler first checks the refusal condition
        ;; locally and either surfaces the relevant error or runs the
        ;; mutation and clears any prior error. The "successful action
        ;; clears the prior error" path matches the JS port's behaviour.
        submit-add!        (fn []
                             (if blank-input?
                               (set-err! (i18n/tr locale :err/empty-input))
                               (do (comp/transact! this
                                     [(add-todo {:todo/text new-todo-text})])
                                   (clear-err!)
                                   (focus-new-todo-input!))))
        ;; Phase 7.12: Delete List no longer empties the list directly
        ;; on a non-empty list — it opens the confirm modal. The empty
        ;; path still surfaces the existing `nothing-to-delete-err`
        ;; (matching the JS port: skip the modal when there's nothing
        ;; to confirm).
        submit-delete!     (fn []
                             (if no-todos?
                               (set-err! (i18n/tr locale :err/nothing-to-delete))
                               (comp/transact! this
                                 [(set-open-modal {:ui/open-modal :delete-confirm})])))
        confirm-delete!    (fn []
                             (comp/transact! this [(delete-all)])
                             (modals/close-current-modal! this)
                             (clear-err!)
                             (focus-new-todo-input!))
        cancel-delete!     (fn [] (modals/close-current-modal! this))
        submit-mark-done!  (fn []
                             (if (not actionable?)
                               (set-err! (i18n/tr locale :err/cannot-take-action))
                               (do (comp/transact! this [(complete-benchmark-item)])
                                   (clear-err!))))
        submit-prioritize! (fn []
                             (if (not prioritizable?)
                               (set-err! (i18n/tr locale :err/not-prioritizable))
                               (do (send-and-pump! this chart/event-start)
                                   (clear-err!))))
        ;; Phase 7.12 batch import. The textarea content `textarea-import-text`
        ;; is the controlled value. On Submit:
        ;;   - blank → surface `:err/empty-textarea`.
        ;;   - non-blank → run the import-from-text mutation, clear the
        ;;     textarea, clear the prior error. The modal stays open
        ;;     (B-2 fix) so the user can verify the import or paste a
        ;;     second batch without re-opening the modal. Auto-close
        ;;     and a settings-modal preference are tracked as future
        ;;     ideas in `docs/ideas.md`.
        textarea-blank?    (str/blank? (or textarea-import-text ""))
        submit-import!     (fn []
                             (if textarea-blank?
                               (set-err! (i18n/tr locale :err/empty-textarea))
                               (do (comp/transact! this
                                     [(import-from-text
                                        {:ui/textarea-import-text textarea-import-text})])
                                   (m/set-string! this :ui/textarea-import-text
                                     :value "")
                                   (clear-err!))))]
    (comp/fragment
      ;; Form wraps the input so the browser's default form-submit
      ;; (Enter key) routes through `submit-add!`. The action buttons
      ;; live in a separate <section> below, so they don't accidentally
      ;; submit the form on click.
      (dom/form {:className "ph3"
                 :onSubmit  (fn [e]
                              (.preventDefault e)
                              (submit-add!))}
        (dom/div {:className "measure-narrow ml-auto mr-auto"}
          ;; Phase 9.2 (RAD basics): input is now attribute-driven —
          ;; placeholder text + maxlength come from `rad-attrs/text`
          ;; instead of being hard-coded here. The visible behaviour
          ;; is identical; the *source of truth* moved.
          ;; The hidden label is still emitted (with "New TODO:" via
          ;; the attribute's `:field/label`) so
          ;; `h/type-into-labeled!` still finds the input by label.
          (rad-input/text-input rad-attrs/text
            {:this        this
             :state-key   :ui/new-todo-text
             :value       new-todo-text
             :class-name  (theme/input-class theme)
             :input-id    new-todo-input-id
             :label-text  "New TODO:"})
          ;; Phase 7.9: page-level error message. Only rendered when
          ;; `:ui/err-msg` is truthy; the JS port uses red copy for
          ;; immediate visual cue.
          (when err-msg
            (dom/p {:className "lh-135 red ml-auto mr-auto measure-narrow ma0 pt2"}
              err-msg))))
      (dom/section {:className "pt2 pb2 flex justify-center flex-wrap measure-wide ml-auto mr-auto"}
        ;; Group 1: list-mutation actions (Add Item, Delete List).
        ;; Per 7.9: dim-when-invalid is purely visual; the click still
        ;; fires `submit-*!` which sets `:ui/err-msg` instead of running
        ;; the mutation. Hard `:disabled` only when reviewing.
        (dom/div {:className "dib"}
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls (or active? add-dim?))
                         :title     s/tooltip-add-item
                         :disabled  active?
                         :onClick   #(submit-add!)}
              (i18n/tr locale :btn/add-item)))
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls (or active? delete-dim?))
                         :title     s/tooltip-delete-list
                         :disabled  active?
                         :onClick   #(submit-delete!)}
              (i18n/tr locale :btn/delete-list))))
        ;; Group 2: review-flow actions (Prioritize, Mark Done). Both
        ;; follow the click-surfaces-error pattern now; only the
        ;; active-review case still hard-disables them.
        (dom/div {:className "dib"}
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls (or active? prioritize-dim?))
                         :title     s/tooltip-prioritize
                         :disabled  active?
                         :onClick   #(submit-prioritize!)}
              (i18n/tr locale :btn/prioritize)))
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls (or active? mark-done-dim?))
                         :title     s/tooltip-mark-done
                         :disabled  active?
                         :onClick   #(submit-mark-done!)}
              (i18n/tr locale :btn/mark-done)))))
      ;; Task list
      (when (seq todos)
        (dom/section {:className "task-list"}
          (dom/div {:className "ph3"}
            (dom/ul {:className "ph0 todo-list list ma0 tl measure-narrow ml-auto mr-auto"}
              (mapv (fn [item]
                      (ui-todo-item
                        (comp/computed item
                          {:benchmark? (= (:todo/id item) benchmark-id)
                           :theme      theme})))
                todos)))))
      ;; List footer — count + next-actionable preview
      (dom/div {:className "ph3 pt2 pb3"}
        (dom/p {:className "ma0 o-70 measure-narrow ml-auto mr-auto lh-135"}
          (i18n/tr-list-count locale (count todos)))
        (when benchmark
          (dom/p {:className "ma0 o-70 measure-narrow ml-auto mr-auto lh-135 line-clamp-3 overflow-hidden"}
            (i18n/tr-next-actionable locale (:todo/text benchmark)))))
      ;; Review modal — `on-close` is intentionally absent: the JS port
      ;; (and our chart) requires Quit to dismiss, no background click.
      (when active?
        (modals/modal-shell {:theme theme}
          (when question
            (dom/p {:className "ma0 pb3 lh-135 tc"} question))
          (dom/div {:className "tc"}
            (dom/button {:className (theme/review-btn-class theme)
                         :title     s/tooltip-quit-review
                         :tabIndex  0
                         :onClick   #(send-and-pump! this chart/event-quit)}
              (i18n/tr locale :btn/quit))
            (dom/button {:className (theme/review-btn-class theme)
                         :title     s/tooltip-review-no
                         :tabIndex  1
                         :onClick   #(send-and-pump! this chart/event-no)}
              (i18n/tr locale :btn/no))
            (dom/button {:className (theme/review-btn-class theme)
                         :title     s/tooltip-review-yes
                         :tabIndex  2
                         :onClick   #(send-and-pump! this chart/event-yes)}
              (i18n/tr locale :btn/yes)))))
      ;; Menu modals — driven by `:ui/open-modal`. Mutex by construction
      ;; (single keyword), so at most one is visible at a time.
      (case open-modal
        :info           (modals/info-modal this theme locale)
        :settings       (modals/settings-modal this theme locale)
        :save           (modals/save-modal this theme locale todos
                          textarea-import-text submit-import!)
        :delete-confirm (modals/delete-confirm-modal this theme locale
                          confirm-delete! cancel-delete!)
        :conflict       (modals/conflict-modal this theme locale todos conflict-url-items)
        nil))))

(def ui-todo-list (comp/factory TodoList {:keyfn :list/id}))

;; ============================================================================
;; Root — <main> shell with header icon buttons
;; ============================================================================

(defsc Root [this {:keys [list]}]
  {:query         [{:list (comp/get-query TodoList)}]
   :initial-state (fn [_]
                    {:list (comp/get-initial-state TodoList {})})}
  ;; Root markup mirrors the JS App.js shell: `<main>` > `<header>` (with
  ;; the AutoFocus h1 + icon buttons) + `<section>` containing the
  ;; form/list/footer. Theme (Phase 7.7) lives on TodoList's props;
  ;; Root reads it from `(:ui/theme list)` and applies the text-color
  ;; class. Other theme tokens cascade through TodoList's children.
  (let [theme            (or (:ui/theme list) :theme/light)
        locale           (or (:ui/locale list) :en)
        ;; Phase 7.14 / B-3 fix: header menu icons (Save / About /
        ;; Help) are hard-disabled while a review session is active
        ;; OR a hard-choice modal is up (`:delete-confirm`,
        ;; `:conflict`). Toggle Theme is rendered separately below
        ;; and stays enabled. Matches the JS port's
        ;; `isPrioritizing || showingDeleteModal || showingConflictModal`
        ;; predicate (`docs/js_ui_reference.md` line 149).
        config           (scf/current-configuration this review-session-id)
        review-active?   (contains? config chart/active)
        open-modal       (:ui/open-modal list)
        menu-disabled?   (or review-active?
                           (contains? #{:delete-confirm :conflict} open-modal))
        toggle-theme-lbl (i18n/tr locale :tooltip/toggle-theme)]
    (dom/main {:className (str "app min-vh-100 flex flex-column f5 montserrat "
                               ;; Phase 12.1 (B-6 fix): bottom padding so the
                               ;; user can tell they've scrolled to the end of
                               ;; the page when content overflows. Goes on
                               ;; <main> so the theme bg extends through the
                               ;; padding zone.
                               "pb4 "
                               (theme/theme-text-class theme)
                               " "
                               (theme/theme-page-bg-class theme))}
      (dom/header {:className "app-header pa3 pb2 flex justify-center items-center"}
        (dom/h1 {:className "ma0 f2-ns f3 fw8 tracked-custom dib gray"}
          s/app-name)
        (modals/header-icon-button this {:icon      icons/save-disk
                                         :label     (i18n/tr locale :tooltip/import-export)
                                         :modal-id  :save
                                         :first?    true
                                         :disabled? menu-disabled?})
        ;; Phase 12.3: About + Help merged into one Info modal. The
        ;; `?` icon is gone from the header; clicking the `i` icon
        ;; shows both About and Instructions content under one modal.
        (modals/header-icon-button this {:icon      icons/info-circle
                                         :label     (i18n/tr locale :tooltip/info)
                                         :modal-id  :info
                                         :disabled? menu-disabled?})
        (modals/header-icon-button this {:icon      icons/gear
                                         :label     (i18n/tr locale :tooltip/settings)
                                         :modal-id  :settings
                                         :disabled? menu-disabled?})
        ;; Theme toggle — lightbulb-solid when in light mode (clicking
        ;; flips to dark), lightbulb-regular when in dark mode. Same
        ;; wrapper-div pattern as the modal toggles; explicit
        ;; `type="button"` per the JS port (and defensive against any
        ;; future enclosing <form>).
        (dom/div {:className (theme/header-icon-wrapper-class {})}
          (dom/button {:type      "button"
                       :className theme/header-icon-btn-class
                       :title     toggle-theme-lbl
                       :onClick   #(comp/transact! this [(toggle-theme)])}
            (if (theme/dark? theme) icons/lightbulb-regular icons/lightbulb-solid)
            (dom/span {:className "clip"} toggle-theme-lbl))))
      (dom/section {:className "app-container relative flex flex-column flex-1"}
        (when list (ui-todo-list list))))))
