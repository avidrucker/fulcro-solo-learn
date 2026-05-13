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
    ;; The Fulcro headless library is JVM-only — used by the spec suite
    ;; via `init`. The browser build doesn't need it; see `learn.util.remote`
    ;; for the CLJC `sync-remote` shim used in the CLJS init branch.
    #?(:clj [com.fulcrologic.fulcro.headless :as h])
    #?(:clj [com.fulcrologic.fulcro.headless.loopback-remotes :as lr])
    ;; Fulcro Inspect 1.x requires explicit registration via
    ;; `add-fulcro-inspect!` at app-build time. CLJS-only because the
    ;; Chrome extension is browser-side only.
    #?(:cljs [fulcro.inspect.tool :as inspect-tool])
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [learn.parser :as parser]
    [learn.model.list :as model.list]
    [learn.model.review :as review]
    [learn.review.chart :as chart]
    [learn.server :as server]
    [learn.ui.icons :as icons]
    [learn.ui.strings :as s]
    [learn.util.normalized :as norm]
    [learn.util.remote :as remote]
    [learn.util.storage :as storage]
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])))

(declare cancel-todo add-todo clone-todo delete-all complete-benchmark-item
         set-open-modal toggle-open-modal toggle-theme)

;; ============================================================================
;; Constants — kept at the top because ClojureScript flags forward
;; references at compile time (CLJ resolves at runtime and tolerates it).
;; These two are referenced by TodoList's render, which appears below.
;; ============================================================================

;; Well-known singleton session id for the review chart. The chart runs at
;; most one session at a time per app (SCHEMA.md §13 "One per app instance"),
;; so a keyword id is sufficient; no need to mint random UUIDs.
(def review-session-id :review-session)

;; Registry key for the review chart definition on the Fulcro app.
(def review-chart-key ::review-chart)

;; ============================================================================
;; UI components
;; ============================================================================

;; ----------------------------------------------------------------------
;; Tachyons class strings + theme-aware helpers (Phase 6.5.3 / 7.7).
;; Sourced verbatim from `docs/js_ui_reference.md` §B. The
;; `theme-*-class` helpers return the JS port's light/dark suffix
;; pair for each themed element. `:theme/light` is the default and
;; what callers see if `:ui/theme` is missing.
;; ----------------------------------------------------------------------

(defn- dark? [theme] (= theme :theme/dark))

(defn- theme-text-class
  "Foreground text color class for the page root."
  [theme] (if (dark? theme) "white" "black"))

(defn- theme-page-bg-class
  "Page background class for `<main>` — `bg-near-black` in dark mode so
   the white-text content is readable. Light mode is the document's
   default (no class needed). The JS port's CSS handles this at the
   body level; we apply it on <main> here since that's our root."
  [theme] (if (dark? theme) "bg-near-black" ""))

(defn- theme-modal-bg-class
  "Modal overlay tint."
  [theme] (if (dark? theme) "bg-black-90" "bg-white-90"))

(defn- theme-input-class
  "Theme-suffix for the new-todo input."
  [theme]
  (if (dark? theme)
    "white bg-black hover-bg-dark-gray active-bg-black"
    "black hover-bg-light-gray active-bg-white"))

(defn- theme-primary-btn-suffix
  "Theme-suffix for primary `<button>` text + bg (Add Item, Delete
   List, Prioritize, Mark Done, modal action buttons)."
  [theme]
  (if (dark? theme) "bg-dark-gray white" "bg-moon-gray black"))

(defn- theme-icon-btn-color
  "Theme-suffix for per-row Cancel/Clone icon buttons."
  [theme]
  (if (dark? theme) "mid-gray" "moon-gray"))

(defn- btn-icon-class
  "Cancel / clone icon buttons on each todo row. `hover-button` (custom
   class in `app.css`) hides the button until the row is hovered on
   pointer-capable devices and stays visible on touch."
  [theme]
  (str "button-reset pa1 hover-button w2 h-15 pointer bg-transparent bn "
       (theme-icon-btn-color theme)))

(def ^:private new-todo-input-id
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
          (dom/button {:className (btn-icon-class theme)
                       :title     s/title-cancel-task
                       :aria-label s/title-cancel-task
                       :onClick   #(comp/transact! this [(cancel-todo {:todo/id id})])}
            icons/cancel-x)
          (dom/button {:className (btn-icon-class theme)
                       :title     s/title-clone-task
                       :aria-label s/title-clone-task
                       :onClick   #(comp/transact! this [(clone-todo {:todo/id id})])}
            icons/repeat-arrow))))))

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

;; ----------------------------------------------------------------------
;; Tachyons class strings for the main list / form / button row.
;; Theme-aware via the `theme-*` helpers above (Phase 7.7).
;; ----------------------------------------------------------------------

(defn- btn-primary-class
  "Theme-aware primary button class string (Add Item, Delete List,
   Prioritize, Mark Done)."
  [theme]
  (str "br3 w4 fw6 ba bw1 b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 ph1 pointer grow"))

(defn- btn-primary-dim-class
  "Disabled/dimmed variant — same theme suffix, no `pointer grow`,
   `o-50` opacity for the visual."
  [theme]
  (str "br3 w4 fw6 ba bw1 b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 ph1 o-50"))

(defn- input-class
  "Theme-aware new-todo input class string."
  [theme]
  (str "todo-input pa2 w-100 input-reset br3 ba bw1 b--gray "
       (theme-input-class theme)))

(defn- review-btn-class
  "Theme-aware review modal action button class (Quit/No/Yes)."
  [theme]
  (str "br3 w3 fw6 ba bw1 b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 pointer grow ma1 dib"))

(defn- modal-shell
  "Tachyons-styled overlay used by all modals in the JS port (Phase 6.5.4).

   The outer `<section>` is absolutely positioned over the app container
   (which is itself `position: relative` on `Root`); the inner `<section>`
   centers its content in a `measure-narrow` column.

   Options map:
     :on-close   — fn invoked when the transparent full-area close button
                   is clicked. When nil, no close button is rendered —
                   used by the review modal (must use Quit to dismiss).
     :close-label — a11y text for that close button (e.g. \"Close Save Modal\").
     :theme       — :theme/light (default) or :theme/dark; drives the
                    `bg-white-90` / `bg-black-90` overlay tint.

   `children` are positional DOM nodes for the modal body."
  [{:keys [on-close close-label theme] :or {theme :theme/light}} & children]
  (dom/section {:className (str "absolute f5 top-0 w-100 h-100 "
                                (theme-modal-bg-class theme))}
    (apply dom/section
      {:className "measure-narrow ml-auto mr-auto relative z-1 pa3"}
      children)
    (when on-close
      (dom/button {:className "absolute z-0 top-0 left-0 w-100 o-0 min-h-100"
                   :onClick   on-close}
        close-label))))

(def ^:private header-icon-btn-class
  "Tachyons class string for header icon buttons. `clip` is applied to
   the inner `<span>` so the screen-reader-only text label is invisible
   visually but still in the DOM (a11y + h/click-on-text! findable)."
  "button-reset pa1 w2 h2 pointer f5 fw6 grow bg-transparent bn gray pl2 inline-flex items-center")

(defn- header-icon-button
  "A header icon button that toggles `modal-id` via
   `toggle-open-modal`. The label text is kept in the DOM via Tachyons'
   `clip` (visually hidden, screen-reader visible, h/click-on-text!
   findable for tests)."
  [this {:keys [icon label modal-id]}]
  (dom/button {:className header-icon-btn-class
               :title     label
               :onClick   #(comp/transact! this
                             [(toggle-open-modal {:ui/open-modal modal-id})])}
    icon
    (dom/span {:className "clip"} label)))

(defn- close-current-modal!
  "Dispatch `set-open-modal :none`. Used by `modal-shell`'s :on-close."
  [this]
  (comp/transact! this [(set-open-modal {:ui/open-modal :none})]))

(defn- about-modal
  [this theme]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label s/close-info-modal
                :theme       theme}
    (dom/h2 {:className "pb2 ma0"} s/heading-about)
    (dom/p {:className "pb3 ma0 lh-135"} s/info-string-1)
    (dom/p {:className "pb3 ma0 lh-135"} s/info-string-2)
    (dom/div {:className "pb3"}
      (dom/h3 {:className "f5 fw6 ma0 mb2"} (s/version-line)))
    (dom/p {:className "pt2 ma0 lh-135"} s/click-i-circle-to-close)))

(defn- help-modal
  [this theme]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label s/close-help-modal
                :theme       theme}
    (dom/h2 {:className "pb2 ma0"} s/heading-help)
    (dom/p {:className "pb2 ma0 lh-135"} s/instructions)
    (dom/p {:className "pb2 ma0 lh-135"} s/instructions-2)
    (dom/p {:className "pb3 ma0 lh-135"}
      s/how-to-report-issues
      (dom/a {:href   s/link-issues-href
              :target "_blank"
              :rel    "noopener noreferrer"
              :className "link underline blue hover-orange"}
        s/link-issues-text))
    (dom/p {:className "pt2 ma0 lh-135"} s/click-question-circle-to-close)))

(defn- stub-onclick
  "Returns a click handler that logs to the JS console and otherwise
   no-ops. Used for the Phase 7.6 Import/Export modal buttons whose
   real behaviour (URL serialization, JSON parse, etc.) lands in a
   later phase."
  [label]
  (fn [& _]
    #?(:cljs (js/console.log "[stub]" label)
       :clj  nil)))

(defn- save-modal-btn-class
  "Theme-aware Import/Export modal action-button class string."
  [theme]
  (str "br3 w4 f5 fw6 ba dib bw1 grow b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 pointer ma1"))

(defn- save-modal-wide-btn-class
  "Full-width variant — Copy URL + Submit."
  [theme]
  (str "br3 w-100 f5 fw6 ba dib bw1 grow b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 pointer"))

(defn- save-modal
  [this theme]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label s/close-save-modal
                :theme       theme}
    (dom/h2 {:className "pb2 ph3 ma0"} s/heading-import-export)
    (dom/div {:className "ph3 pb2"}
      (dom/button {:className (save-modal-wide-btn-class theme)
                   :title     s/tooltip-copy-list-url
                   :onClick   (stub-onclick "copy-list-url")}
        s/btn-copy-list-url))
    (dom/p {:className "ph3 ma0 lh-135"} s/save-info-1)
    (dom/div {:className "ph3 pt2 tc"}
      ;; File-upload "button" is a styled <label> wrapping a hidden
      ;; <input type="file"> — same pattern the JS port uses.
      (dom/label {:className (str "br3 grow dib button-reset border-box w4 f5 fw6 "
                                  "ba bw1 b--gray "
                                  (theme-primary-btn-suffix theme)
                                  " pa2 pointer ma1")
                  :htmlFor   "save-modal-file-upload"}
        s/btn-import)
      (dom/input {:id        "save-modal-file-upload"
                  :type      "file"
                  :accept    ".json"
                  :className "dn input-reset"
                  :onChange  (stub-onclick "import-json-file")})
      (dom/button {:className (save-modal-btn-class theme)
                   :title     s/tooltip-export-json
                   :onClick   (stub-onclick "export-json")}
        s/btn-export))
    (dom/p {:className "ph3 pt2 ma0 lh-135"} s/save-info-2)
    (dom/div {:className "ph3 pt1"}
      (dom/textarea {:className   (str "db input-reset pa2 w-100 resize-none lh-135 "
                                       "br3 ba bw1 b--gray "
                                       (theme-text-class theme))
                     :placeholder s/textarea-placeholder
                     :rows        2
                     :onChange    (stub-onclick "textarea-change")})
      (dom/button {:className (save-modal-wide-btn-class theme)
                   :onClick   (stub-onclick "submit-textarea-import")}
        s/btn-submit))
    (dom/p {:className "pt2 ph3 pb3 ma0 lh-135"} s/click-disk-to-close)))

(defsc TodoList [this {:list/keys [todos]
                       :ui/keys   [new-todo-text open-modal theme]
                       :or        {theme :theme/light}}]
  {:query         [:list/id
                   {:list/todos (comp/get-query TodoItem)}
                   :ui/new-todo-text
                   ;; Phase 7.4: which (if any) menu modal is currently
                   ;; open. Default `:none`. The query lives here on
                   ;; TodoList because all modals are page-level — there
                   ;; isn't a Modal component with its own ident.
                   :ui/open-modal
                   ;; Phase 7.7: `:theme/light` (default) or `:theme/dark`.
                   :ui/theme
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
                    {:list/id          1
                     :list/todos       []
                     :ui/new-todo-text ""
                     :ui/open-modal    :none
                     :ui/theme         :theme/light})}
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
        ;; Disabled / dim conditions per JS port:
        ;;   Add Item — dim/disabled while reviewing
        ;;   Delete List — dim/disabled when the list is empty or reviewing
        ;;   Prioritize — dim/disabled when not prioritizable or reviewing
        ;;   Mark Done — dim/disabled when no actionable items or reviewing
        add-disabled?       active?
        delete-disabled?    (or active? no-todos?)
        prioritize-disabled? (or active? (not prioritizable?))
        mark-done-disabled?  (or active? (not actionable?))
        btn-cls            (fn [disabled?]
                             (if disabled?
                               (btn-primary-dim-class theme)
                               (btn-primary-class theme)))
        ;; One canonical handler for "submit the Add Item action" so both
        ;; the form's onSubmit (Enter key) and the button's onClick
        ;; converge on the same code path. Trusts the model to refuse
        ;; blank text; refocuses regardless so the user can keep typing.
        submit-add!        (fn []
                             (comp/transact! this [(add-todo {:todo/text new-todo-text})])
                             (focus-new-todo-input!))
        submit-delete!     (fn []
                             (comp/transact! this [(delete-all)])
                             (focus-new-todo-input!))
        submit-mark-done!  (fn []
                             (comp/transact! this [(complete-benchmark-item)]))]
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
          ;; Hidden label preserves the headless-test affordance
          ;; (`h/type-into-labeled! ... "New TODO"`) while staying out of
          ;; the visible UI. Tachyons class `clip` is the screen-reader-only
          ;; hide pattern.
          (dom/label {:htmlFor new-todo-input-id :className "clip"} "New TODO:")
          (dom/input {:id          new-todo-input-id
                      :className   (input-class theme)
                      :placeholder s/input-placeholder
                      :value       (or new-todo-text "")
                      :onChange    #(m/set-string! this :ui/new-todo-text :event %)})))
      (dom/section {:className "pt2 pb2 flex justify-center flex-wrap measure-wide ml-auto mr-auto"}
        ;; Group 1: list-mutation actions (Add Item, Delete List).
        (dom/div {:className "dib"}
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls add-disabled?)
                         :title     s/tooltip-add-item
                         :disabled  add-disabled?
                         :onClick   #(submit-add!)}
              s/btn-add-item))
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls delete-disabled?)
                         :title     s/tooltip-delete-list
                         :disabled  delete-disabled?
                         :onClick   #(submit-delete!)}
              s/btn-delete-list)))
        ;; Group 2: review-flow actions (Prioritize, Mark Done).
        (dom/div {:className "dib"}
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls prioritize-disabled?)
                         :title     s/tooltip-prioritize
                         :disabled  prioritize-disabled?
                         :onClick   #(send-and-pump! this chart/event-start)}
              s/btn-prioritize))
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls mark-done-disabled?)
                         :title     s/tooltip-mark-done
                         :disabled  mark-done-disabled?
                         :onClick   #(submit-mark-done!)}
              s/btn-mark-done))))
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
          (s/list-count-line (count todos)))
        (when benchmark
          (dom/p {:className "ma0 o-70 measure-narrow ml-auto mr-auto lh-135 line-clamp-3 overflow-hidden"}
            (s/next-actionable-line (:todo/text benchmark)))))
      ;; Review modal — `on-close` is intentionally absent: the JS port
      ;; (and our chart) requires Quit to dismiss, no background click.
      (when active?
        (modal-shell {:theme theme}
          (when question
            (dom/p {:className "ma0 pb3 lh-135 tc"} question))
          (dom/div {:className "tc"}
            (dom/button {:className (review-btn-class theme)
                         :title     s/tooltip-quit-review
                         :tabIndex  0
                         :onClick   #(send-and-pump! this chart/event-quit)} s/btn-quit)
            (dom/button {:className (review-btn-class theme)
                         :title     s/tooltip-review-no
                         :tabIndex  1
                         :onClick   #(send-and-pump! this chart/event-no)}  s/btn-no)
            (dom/button {:className (review-btn-class theme)
                         :title     s/tooltip-review-yes
                         :tabIndex  2
                         :onClick   #(send-and-pump! this chart/event-yes)} s/btn-yes))))
      ;; Menu modals — driven by `:ui/open-modal`. Mutex by construction
      ;; (single keyword), so at most one is visible at a time.
      (case open-modal
        :about (about-modal this theme)
        :help  (help-modal this theme)
        :save  (save-modal this theme)
        nil))))

(def ui-todo-list (comp/factory TodoList {:keyfn :list/id}))

(defsc Root [this {:keys [list]}]
  {:query         [{:list (comp/get-query TodoList)}]
   :initial-state (fn [_]
                    {:list (comp/get-initial-state TodoList {})})}
  ;; Root markup mirrors the JS App.js shell: `<main>` > `<header>` (with
  ;; the AutoFocus h1 + icon buttons) + `<section>` containing the
  ;; form/list/footer. Theme (Phase 7.7) lives on TodoList's props;
  ;; Root reads it from `(:ui/theme list)` and applies the text-color
  ;; class. Other theme tokens cascade through TodoList's children.
  (let [theme (or (:ui/theme list) :theme/light)]
    (dom/main {:className (str "app min-vh-100 flex flex-column f5 montserrat "
                               (theme-text-class theme)
                               " "
                               (theme-page-bg-class theme))}
      (dom/header {:className "app-header pa3 pb2 flex justify-center items-center"}
        (dom/h1 {:className "ma0 f2-ns f3 fw8 tracked-custom dib gray"}
          s/app-name)
        (header-icon-button this {:icon     icons/save-disk
                                  :label    s/tooltip-import-export
                                  :modal-id :save})
        (header-icon-button this {:icon     icons/info-circle
                                  :label    s/tooltip-about
                                  :modal-id :about})
        (header-icon-button this {:icon     icons/question-circle
                                  :label    s/tooltip-help
                                  :modal-id :help})
        ;; Theme toggle — lightbulb-solid when in light mode (clicking
        ;; flips to dark), lightbulb-regular when in dark mode.
        (dom/button {:className header-icon-btn-class
                     :title     s/tooltip-toggle-theme
                     :onClick   #(comp/transact! this [(toggle-theme)])}
          (if (dark? theme) icons/lightbulb-regular icons/lightbulb-solid)
          (dom/span {:className "clip"} s/tooltip-toggle-theme)))
      (dom/section {:className "app-container relative flex flex-column h-100"}
        (when list (ui-todo-list list))))))

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
;; Modal state foundation (Phase 7.4)
;;
;; `[:list/id 1 :ui/open-modal]` carries one of:
;;   :none   — no modal open (default)
;;   :about  — About modal
;;   :help   — Help modal
;;   :save   — Import/Export modal
;;
;; `set-open-modal*` is mutex-by-construction (single value), so opening
;; any modal closes whatever else was open. `toggle-open-modal*` lets the
;; header icon buttons toggle: click while closed → open; click again
;; while open → close.
;; ============================================================================

(defn set-open-modal*
  "Mutex setter — overwrites whatever modal is currently open."
  [state-map list-ident modal-id]
  (assoc-in state-map (conj list-ident :ui/open-modal) modal-id))

(defn toggle-open-modal*
  "If `modal-id` is currently open at `list-ident`, close it (set to
   :none); otherwise open it. Used by the header icon buttons so the
   same click both opens and dismisses."
  [state-map list-ident modal-id]
  (let [current (get-in state-map (conj list-ident :ui/open-modal))]
    (set-open-modal* state-map list-ident
      (if (= current modal-id) :none modal-id))))

;; ============================================================================
;; Theme toggle (Phase 7.7)
;;
;; `:ui/theme` at `[:list/id 1]` is one of `:theme/light` (default) or
;; `:theme/dark`. The classes diff per theme matches the JS port's
;; suffix-swap pattern documented in `docs/js_ui_reference.md` §B.
;; ============================================================================

(defn toggle-theme*
  "Flip between :theme/light and :theme/dark. Defaults missing/unknown
   values to :theme/light → :theme/dark on first toggle."
  [state-map list-ident]
  (update-in state-map (conj list-ident :ui/theme)
    (fn [t] (if (= t :theme/dark) :theme/light :theme/dark))))

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
  ;; Phase 7.3: enable server sync so localStorage persistence reflects
  ;; the empty list after the user clicks Delete List. Server has a
  ;; matching `learn.client/delete-all` Pathom mutation.
  (remote [env] (remote-list-items env)))

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

;; Phase 7.4 — modal state mutations. Local-only (no server sync) since
;; modal open/close is pure UI state. Hardcoded list-ident `[:list/id 1]`
;; matches the singleton pattern used by the rest of the file.
(defmutation set-open-modal [{:ui/keys [open-modal]}]
  (action [{:keys [state]}]
    (swap! state set-open-modal* [:list/id 1] open-modal)))

(defmutation toggle-open-modal [{:ui/keys [open-modal]}]
  (action [{:keys [state]}]
    (swap! state toggle-open-modal* [:list/id 1] open-modal)))

(defmutation toggle-theme [_]
  (action [{:keys [state]}]
    (swap! state toggle-theme* [:list/id 1])))

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

(defn- start-chart!
  "Install + register + start the review chart on `spa`. Shared between
   the JVM and CLJS init branches."
  [spa]
  (scf/install-fulcro-statecharts! spa {:event-loop? false})
  (scf/register-statechart! spa review-chart-key chart/chart)
  (scf/start! spa {:machine    review-chart-key
                   :session-id review-session-id})
  (scf/process-events! spa))

(defn- load-todos!
  "Initial load that populates `:list/todos` from the in-process Pathom
   parser. Same call shape on both platforms."
  [spa]
  (df/load! spa :all-todos TodoItem
    {:target [:list/id 1 :list/todos]}))

#?(:clj
   (defn init
     "Headless JVM build — what the spec suite drives. Uses
      `h/build-test-app` (render tracking, network capture, etc.) and
      the headless library's `lr/sync-remote` so existing test helpers
      keep working unchanged. Returns the spa.

      Side effects:
        - Resets `SPA` to the new app instance.
        - Installs statecharts on the app with `:event-loop? false` (so
          tests can drain the queue deterministically via
          `scf/process-events!`).
        - Registers and starts the review chart at `review-session-id`.
        - Issues an immediate `df/load!` to populate :list/todos from the
          in-process Pathom parser.
        - Forces a render frame so the DOM stub reflects post-load state."
     []
     (let [spa (h/build-test-app
                 {:root-class Root
                  :remotes    {:remote (lr/sync-remote parser/handler)}})]
       (reset! SPA spa)
       (start-chart! spa)
       (app/mount! spa Root :app)
       (load-todos! spa)
       (h/render-frame! spa)
       spa)))

#?(:cljs
   (defn ^:export init
     "Browser build — mounts to the DOM node id 'app' (see
      resources/public/index.html) and lets Fulcro's normal render loop
      drive the UI. The 'server' is the same Pathom parser the JVM tests
      use, exposed through the CLJC `remote/sync-remote` shim because the
      headless `lr/sync-remote` is JVM-only.

      `install-persistence!` runs BEFORE `load-todos!` so that
      `df/load!` sees any state hydrated from localStorage. The watch
      it attaches then re-saves on every subsequent change to
      `SERVER-DB` (Phase 7).

      Returns the spa. Exported so shadow-cljs can call it as the
      module's `:init-fn`."
     []
     (let [spa (app/fulcro-app
                 {:remotes {:remote (remote/sync-remote parser/handler)}})]
       (reset! SPA spa)
       ;; Register the app with Fulcro Inspect 1.x. Paired with the
       ;; `com.fulcrologic.devtools.chrome-preload` in shadow-cljs.edn.
       ;; Noop if Inspect is disabled by compiler flags (release builds).
       (inspect-tool/add-fulcro-inspect! spa)
       ;; Hydrate from localStorage and attach the persistence watch
       ;; BEFORE the initial load so any saved state is what we render.
       (storage/install-persistence! server/SERVER-DB)
       (start-chart! spa)
       (app/mount! spa Root "app")
       (load-todos! spa)
       spa)))

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
