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
    [clojure.string :as str]
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
    [learn.rad.attributes :as rad-attrs]
    [learn.rad.input :as rad-input]
    [learn.server :as server]
    [learn.ui.icons :as icons]
    [learn.ui.strings :as s]
    [learn.util.normalized :as norm]
    [learn.util.remote :as remote]
    [learn.util.storage :as storage]
    [learn.util.url-encoding :as url-encoding]
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])))

(declare cancel-todo add-todo clone-todo delete-all complete-benchmark-item
         import-from-text keep-link-list keep-local-list
         set-open-modal toggle-open-modal toggle-theme
         set-err-msg)

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
  "Page background class for `<main>` — `bg-black` in dark mode so
   the white-text content is readable AND the shade matches the JS
   port's `<body class=\"bg-black\">` exactly (#000 rather than #111).
   Light mode is the document's default (no class needed). The JS port
   sets the class on `<body>`; we apply it on `<main>` because that's
   the highest level our React component owns — paired with the
   html/body/#app flex-column reset in app.css so `<main>` fills the
   viewport."
  [theme] (if (dark? theme) "bg-black" ""))

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

(defn- delete-confirm-btn-class
  "Theme-aware Yes/No button class for the delete-confirm modal. Same
   recipe as `review-btn-class` but `w4` instead of `w3` — the JS port
   uses a wider button here (`docs/js_ui_reference.md` line 99)."
  [theme]
  (str "br3 w4 fw6 ba bw1 b--gray button-reset "
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
    ;; Inner section mirrors the JS port: `measure-narrow ml-auto mr-auto`
    ;; ONLY — no `pa3` (would squeeze the text into a narrower column).
    ;; `relative z-1` is kept so the inner section sits above the
    ;; transparent close button below; without it, clicks would land on
    ;; the close button instead of the modal content. The og has neither
    ;; (no close button to stack against).
    (apply dom/section
      {:className "measure-narrow ml-auto mr-auto relative z-1"}
      children)
    (when on-close
      (dom/button {:className "absolute z-0 top-0 left-0 w-100 o-0 min-h-100"
                   :onClick   on-close}
        close-label))))

;; Phase 7.8 (revised) — matching the JS port's structure exactly:
;;   <div class="pl3 inline-flex items-center">       ; or pl2
;;     <button class="button-reset pa1 w2 h2 ... gray">
;;       <svg ...>
;;       <span class="clip">label</span>               ; our addition for tests
;;     </button>
;;   </div>
;; The pl3/pl2 lives on the WRAPPER div (not the button), and the button
;; stays a fixed 2rem × 2rem. SVGs without explicit width/height (info,
;; question) fill the button's content area uniformly.

(def ^:private header-icon-btn-class
  "button-reset pa1 w2 h2 pointer f5 fw6 grow bg-transparent bn gray")

(defn- header-icon-wrapper-class
  "Outer-div padding class — `pl3` for the leftmost icon, `pl2` for
   the rest (matches the JS port's spacing)."
  [{:keys [first?]}]
  (str (if first? "pl3" "pl2") " inline-flex items-center"))

(defn- header-icon-button
  "A header icon button that toggles `modal-id` via
   `toggle-open-modal`. The label text is kept in the DOM via Tachyons'
   `clip` (visually hidden, screen-reader visible, h/click-on-text!
   findable for tests — the JS port has no such span; this is our
   testability tweak). Pass `:first? true` for the leftmost icon to
   match the JS port's `pl3`/`pl2` spacing.

   Pass `:disabled? true` to hard-disable the button — used by Phase
   7.14 (B-3 fix) to suppress menu opens while a review session is
   active or the delete-confirm modal is up. The theme-toggle button
   is rendered separately below and never receives this flag (always
   enabled, matching the JS port).

   `:type \"button\"` is explicit (matches the JS port) so the button
   stays a no-op activation even if it ever ends up inside a `<form>`
   — HTML's default for a form-internal `<button>` is `type=\"submit\"`."
  [this {:keys [icon label modal-id first? disabled?]}]
  (dom/div {:className (header-icon-wrapper-class {:first? first?})}
    (dom/button {:type      "button"
                 :className header-icon-btn-class
                 :title     label
                 :disabled  (boolean disabled?)
                 ;; Both the HTML `:disabled` attribute AND a nil
                 ;; onClick are set when disabled. The attribute
                 ;; covers real browsers (default click semantics
                 ;; skip disabled buttons); the nil handler covers
                 ;; the headless test framework, which invokes
                 ;; onClick directly without checking `:disabled`.
                 :onClick   (when-not disabled?
                              #(comp/transact! this
                                 [(toggle-open-modal {:ui/open-modal modal-id})]))}
      icon
      (dom/span {:className "clip"} label))))

(defn- close-current-modal!
  "Dispatch `set-open-modal :none`. Used by `modal-shell`'s :on-close."
  [this]
  (comp/transact! this [(set-open-modal {:ui/open-modal :none})]))

(defn- info-modal
  "Phase 12.3 — combines the previous About + Help modals under one
   `i`-icon trigger. Two sections under the parent `Info` heading;
   the `?`-icon Help button is gone from the header."
  [this theme]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label s/close-info-modal
                :theme       theme}
    (dom/h2 {:className "pb2 ma0"} s/heading-info)
    (dom/h3 {:className "f5 fw6 ma0 mb2 pt2"} s/heading-about)
    (dom/p {:className "pb2 ma0 lh-135"} s/info-string-1)
    (dom/p {:className "pb2 ma0 lh-135"} s/info-string-2)
    (dom/p {:className "pb3 ma0 lh-135 fw6"} (s/version-line))
    (dom/h3 {:className "f5 fw6 ma0 mb2 pt2"} s/heading-help)
    (dom/p {:className "pb2 ma0 lh-135"} s/instructions)
    (dom/p {:className "pb2 ma0 lh-135"} s/instructions-2)
    (dom/p {:className "pb3 ma0 lh-135"}
      s/how-to-report-issues
      (dom/a {:href   s/link-issues-href
              :target "_blank"
              :rel    "noopener noreferrer"
              :className "link underline blue hover-orange"}
        s/link-issues-text))
    (dom/p {:className "pt2 ma0 lh-135"} s/click-i-circle-to-close)))

(defn- settings-modal
  "Phase 12.3 — Settings modal shell. Body is intentionally minimal
   for now; Phase 12.5 will add the language dropdown. Future home
   for `S-pwa-debug-modal` and other user preferences."
  [this theme]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label s/close-settings-modal
                :theme       theme}
    (dom/h2 {:className "pb2 ma0"} s/heading-settings)
    (dom/p {:className "pt2 ma0 lh-135"} s/click-gear-to-close)))

(defn- stub-onclick
  "Returns a click handler that logs to the JS console and otherwise
   no-ops. Used for the Phase 7.6 Import/Export modal buttons whose
   real behaviour (URL serialization, JSON parse, etc.) lands in a
   later phase."
  [label]
  (fn [& _]
    #?(:cljs (js/console.log "[stub]" label)
       :clj  nil)))

#?(:cljs
   (defn- current-share-url
     "Build the `?list=...` share URL from the current browser location
      and the items snapshot."
     [items]
     (let [loc js/window.location]
       (url-encoding/list-share-url
         (.-origin loc)
         (.-pathname loc)
         (url-encoding/items->base64-url-segment items)))))

#?(:cljs
   (defn- copy-list-url!
     "Copy the share URL for `items` to the clipboard via
      `navigator.clipboard.writeText`. Best-effort: silently no-ops if
      the Clipboard API is missing (non-https context, very old
      browsers). The promise's `.catch` keeps a copy failure from
      surfacing as an uncaught rejection."
     [items]
     (let [clipboard (some-> js/navigator .-clipboard)]
       (when clipboard
         (-> (.writeText clipboard (current-share-url items))
           (.catch (fn [err] (js/console.warn "[copy-list-url] failed:" err))))))))

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

(def textarea-import-id
  "Stable id paired with the (clip-hidden) `<label htmlFor>` so headless
   tests can target the textarea via `h/type-into-labeled!`."
  "textarea-import")

(defn- save-modal
  [this theme todos textarea-import-text submit-import!]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label s/close-save-modal
                :theme       theme}
    (dom/h2 {:className "pb2 ph3 ma0"} s/heading-import-export)
    (dom/div {:className "ph3 pb2"}
      (dom/button {:className (save-modal-wide-btn-class theme)
                   :title     s/tooltip-copy-list-url
                   :onClick   (fn [_]
                                #?(:cljs (copy-list-url! todos)
                                   :clj  nil))}
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
      ;; Hidden label paired with the textarea id so headless tests can
      ;; find this control via `h/type-into-labeled!`. The JS port has
      ;; no such label; this is our test-affordance ↔ DOM bridge
      ;; (same pattern as the new-todo input).
      (dom/label {:htmlFor   textarea-import-id
                  :className "clip"}
        "Paste import")
      (dom/textarea {:id          textarea-import-id
                     :className   (str "db input-reset pa2 w-100 resize-none lh-135 "
                                       "br3 ba bw1 b--gray "
                                       ;; Phase 7.12 followup: use the same theme suffix
                                       ;; as the new-todo input (text color + bg + hover
                                       ;; states). The JS port's textarea uses the same
                                       ;; theme suffix as its top-level input.
                                       (theme-input-class theme))
                     :placeholder s/textarea-placeholder
                     :rows        2
                     :value       (or textarea-import-text "")
                     :onChange    #(m/set-string! this :ui/textarea-import-text
                                     :event %)})
      (dom/button {:type      "button"
                   :className (save-modal-wide-btn-class theme)
                   :onClick   #(submit-import!)}
        s/btn-submit))
    (dom/p {:className "pt2 ph3 pb3 ma0 lh-135"} s/click-disk-to-close)))

(defn- conflict-list-preview
  "Render a read-only list of items for the conflict modal. Mirrors the
   JS port's preview (`docs/js_ui_reference.md` line 122–124) AND
   matches TodoItem's visual treatment for cancelled / done rows so
   the user can see status accurately in the modal:
     - Cancelled rows: text strikethrough (`strike`) + 50% opacity
       (`o-50`), icon falls back to `:todo/was`.
     - Done rows: 50% opacity (`o-50`), no strikethrough.
     - Otherwise: normal text.

   Same icon-fallback recursion as TodoItem (the JS port's
   `statusToSymbol(task.was)`). B-4-related: cancelled rows were
   previously rendered without the visual marker, so URL and local
   lists looked identical even when statuses differed."
  [items]
  (dom/ul {:className "ph0 todo-list list ma0 tl measure-narrow ml-auto mr-auto"}
    (for [item items]
      (let [status      (:todo/status item)
            was         (:todo/was item)
            cancelled?  (= status :status/cancelled)
            dim?        (#{:status/done :status/cancelled} status)
            icon-status (if (and cancelled? was) was status)
            li-class    (str "flex lh-135 align-start mb1-butlast "
                             (when dim? "o-50"))
            text-class  (str "break-word"
                             (when cancelled? " strike"))]
        (dom/li {:key (str (:todo/id item))
                 :className li-class}
          (dom/span {:title     (name status)
                     :className "mr1 dib h-15"}
            (icons/status-icon icon-status))
          (dom/span {:className text-class}
            (:todo/text item)))))))

(defn- conflict-modal
  "Phase 7.18 — conflict resolution modal. Opens automatically from
   `init` when the URL list and the localStorage list both exist and
   differ. Shows both lists side-by-side (in vertical stacking
   actually — measure-narrow column), Copy URL buttons for each, and
   two Keep buttons. NO background close — user must pick one of the
   two Keeps (matches the JS port's `docs/js_ui_reference.md` C/6)."
  [this theme local-items url-items]
  (modal-shell {:theme theme}  ; no :on-close — must choose
    (dom/p {:className "ma0 pb2 lh-135"} s/mismatch-detected)
    (dom/p {:className "fw6 ma0 pt2"} s/label-link-list)
    (conflict-list-preview url-items)
    (dom/div {:className "tc pt2 pb2"}
      (dom/button {:type      "button"
                   :className (str "br3 f6 fw6 ba dib bw1 grow b--gray button-reset "
                                   (theme-primary-btn-suffix theme)
                                   " pa2 pointer ma1")
                   :title     s/tooltip-copy-link-url
                   :onClick   (fn [_]
                                #?(:cljs (copy-list-url! url-items)
                                   :clj  nil))}
        s/btn-copy-link-url))
    (dom/p {:className "fw6 ma0 pt2"} s/label-local-list)
    (conflict-list-preview local-items)
    (dom/div {:className "tc pt2 pb2"}
      (dom/button {:type      "button"
                   :className (str "br3 f6 fw6 ba dib bw1 grow b--gray button-reset "
                                   (theme-primary-btn-suffix theme)
                                   " pa2 pointer ma1")
                   :title     s/tooltip-copy-local-url
                   :onClick   (fn [_]
                                #?(:cljs (copy-list-url! local-items)
                                   :clj  nil))}
        s/btn-copy-local-url))
    (dom/div {:className "pb3 tc"}
      (dom/button {:type      "button"
                   :className (delete-confirm-btn-class theme)
                   :title     s/tooltip-keep-link-list
                   :onClick   #(comp/transact! this [(keep-link-list)])}
        s/btn-keep-link)
      (dom/button {:type      "button"
                   :className (delete-confirm-btn-class theme)
                   :title     s/tooltip-keep-local-list
                   :onClick   #(comp/transact! this [(keep-local-list)])}
        s/btn-keep-local))))

(defn- delete-confirm-modal
  "Phase 7.12 — confirm step for Delete List. Body text matches the JS
   port's `confirmListDelete` string. Yes empties + closes; No just
   closes; background click also cancels (matches the JS port's
   transparent-close overlay).

   `on-yes` / `on-no` are 0-arg handlers passed in from the TodoList
   render so they can close over the `submit-*!` helpers built there."
  [this theme on-yes on-no]
  (modal-shell {:on-close    on-no
                :close-label s/close-delete-modal
                :theme       theme}
    (dom/p {:className "ma0 pb3 lh-135 tc"} s/confirm-list-delete)
    (dom/div {:className "tc"}
      (dom/button {:type      "button"
                   :className (delete-confirm-btn-class theme)
                   :title     s/tooltip-cancel-delete
                   :onClick   #(on-no)}
        s/btn-no)
      (dom/button {:type      "button"
                   :className (delete-confirm-btn-class theme)
                   :title     s/tooltip-confirm-delete
                   :onClick   #(on-yes)}
        s/btn-yes))))

(defsc TodoList [this {:list/keys [todos]
                       :ui/keys   [new-todo-text textarea-import-text
                                   open-modal theme err-msg
                                   conflict-url-items]
                       :or        {theme :theme/light}}]
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
                               (btn-primary-dim-class theme)
                               (btn-primary-class theme)))
        clear-err!         #(comp/transact! this [(set-err-msg {:ui/err-msg nil})])
        set-err!           (fn [msg]
                             (comp/transact! this [(set-err-msg {:ui/err-msg msg})]))
        ;; Phase 7.9: each handler first checks the refusal condition
        ;; locally and either surfaces the relevant error or runs the
        ;; mutation and clears any prior error. The "successful action
        ;; clears the prior error" path matches the JS port's behaviour.
        submit-add!        (fn []
                             (if blank-input?
                               (set-err! s/empty-input-err)
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
                               (set-err! s/nothing-to-delete-err)
                               (comp/transact! this
                                 [(set-open-modal {:ui/open-modal :delete-confirm})])))
        confirm-delete!    (fn []
                             (comp/transact! this [(delete-all)])
                             (close-current-modal! this)
                             (clear-err!)
                             (focus-new-todo-input!))
        cancel-delete!     (fn [] (close-current-modal! this))
        submit-mark-done!  (fn []
                             (if (not actionable?)
                               (set-err! s/cannot-take-action-err)
                               (do (comp/transact! this [(complete-benchmark-item)])
                                   (clear-err!))))
        submit-prioritize! (fn []
                             (if (not prioritizable?)
                               (set-err! s/not-prioritizable-err)
                               (do (send-and-pump! this chart/event-start)
                                   (clear-err!))))
        ;; Phase 7.12 batch import. The textarea content `textarea-import-text`
        ;; is the controlled value. On Submit:
        ;;   - blank → surface `empty-textarea-err`.
        ;;   - non-blank → run the import-from-text mutation, clear the
        ;;     textarea, clear the prior error. The modal stays open
        ;;     (B-2 fix) so the user can verify the import or paste a
        ;;     second batch without re-opening the modal. Auto-close
        ;;     and a settings-modal preference are tracked as future
        ;;     ideas in `docs/ideas.md`.
        textarea-blank?    (str/blank? (or textarea-import-text ""))
        submit-import!     (fn []
                             (if textarea-blank?
                               (set-err! s/empty-textarea-err)
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
             :class-name  (input-class theme)
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
              s/btn-add-item))
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls (or active? delete-dim?))
                         :title     s/tooltip-delete-list
                         :disabled  active?
                         :onClick   #(submit-delete!)}
              s/btn-delete-list)))
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
              s/btn-prioritize))
          (dom/div {:className "ma1 dib"}
            (dom/button {:type      "button"
                         :className (btn-cls (or active? mark-done-dim?))
                         :title     s/tooltip-mark-done
                         :disabled  active?
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
        :info           (info-modal this theme)
        :settings       (settings-modal this theme)
        :save           (save-modal this theme todos
                          textarea-import-text submit-import!)
        :delete-confirm (delete-confirm-modal this theme
                          confirm-delete! cancel-delete!)
        :conflict       (conflict-modal this theme todos conflict-url-items)
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
  (let [theme            (or (:ui/theme list) :theme/light)
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
                           (contains? #{:delete-confirm :conflict} open-modal))]
    (dom/main {:className (str "app min-vh-100 flex flex-column f5 montserrat "
                               ;; Phase 12.1 (B-6 fix): bottom padding so the
                               ;; user can tell they've scrolled to the end of
                               ;; the page when content overflows. Goes on
                               ;; <main> so the theme bg extends through the
                               ;; padding zone.
                               "pb4 "
                               (theme-text-class theme)
                               " "
                               (theme-page-bg-class theme))}
      (dom/header {:className "app-header pa3 pb2 flex justify-center items-center"}
        (dom/h1 {:className "ma0 f2-ns f3 fw8 tracked-custom dib gray"}
          s/app-name)
        (header-icon-button this {:icon      icons/save-disk
                                  :label     s/tooltip-import-export
                                  :modal-id  :save
                                  :first?    true
                                  :disabled? menu-disabled?})
        ;; Phase 12.3: About + Help merged into one Info modal. The
        ;; `?` icon is gone from the header; clicking the `i` icon
        ;; shows both About and Instructions content under one modal.
        (header-icon-button this {:icon      icons/info-circle
                                  :label     s/tooltip-info
                                  :modal-id  :info
                                  :disabled? menu-disabled?})
        (header-icon-button this {:icon      icons/gear
                                  :label     s/tooltip-settings
                                  :modal-id  :settings
                                  :disabled? menu-disabled?})
        ;; Theme toggle — lightbulb-solid when in light mode (clicking
        ;; flips to dark), lightbulb-regular when in dark mode. Same
        ;; wrapper-div pattern as the modal toggles; explicit
        ;; `type="button"` per the JS port (and defensive against any
        ;; future enclosing <form>).
        (dom/div {:className (header-icon-wrapper-class {})}
          (dom/button {:type      "button"
                       :className header-icon-btn-class
                       :title     s/tooltip-toggle-theme
                       :onClick   #(comp/transact! this [(toggle-theme)])}
            (if (dark? theme) icons/lightbulb-regular icons/lightbulb-solid)
            (dom/span {:className "clip"} s/tooltip-toggle-theme))))
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

(defn import-from-text*
  "Phase 7.12 batch import: denormalize → model.list/import-from-string →
   sync-items back into the normalized state-map. Blank-or-whitespace
   text is a no-op (the model refuses with :error/empty-import; the UI
   layer surfaces the error before calling here, so we just guard
   defensively)."
  [state-map list-ident text]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/import-from-string items text)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

(defn- close-conflict-modal*
  "Clear the transient conflict-modal state at `list-ident` and close
   the modal."
  [state-map list-ident]
  (-> state-map
    (assoc-in (conj list-ident :ui/conflict-url-items) nil)
    (assoc-in (conj list-ident :ui/open-modal) :none)))

(defn keep-link-list*
  "Phase 7.18 — user chose to keep the URL-list when the conflict
   modal showed two divergent lists. Replaces the normalized list at
   `list-ident` with `:ui/conflict-url-items`, then closes the modal
   and clears the transient stash. No-op when `:ui/conflict-url-items`
   is absent (defensive — the mutation can only fire while the modal
   is open, but we guard anyway)."
  [state-map list-ident]
  (if-let [url-items (get-in state-map (conj list-ident :ui/conflict-url-items))]
    (-> state-map
      (norm/sync-items list-ident url-items)
      (close-conflict-modal* list-ident))
    state-map))

(defn keep-local-list*
  "Phase 7.18 — user chose to keep the localStorage-derived list. The
   normalized list at `list-ident` already holds those items (we
   hydrate from localStorage before showing the modal), so this is
   just: close the modal + clear the transient stash."
  [state-map list-ident]
  (close-conflict-modal* state-map list-ident))

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
;;   :none           — no modal open (default)
;;   :info           — Info modal (Phase 12.3: combines About + Help)
;;   :settings       — Settings modal (Phase 12.3: new)
;;   :save           — Import/Export modal
;;   :delete-confirm — Phase 7.12: Are-you-sure prompt for Delete List
;;   :conflict       — Phase 7.18: URL/localStorage conflict resolution
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
;; Error message surfacing (Phase 7.9)
;;
;; `:ui/err-msg` at `[:list/id 1]` holds either `nil` (no error showing)
;; or a string ready to render. Set on invalid click attempts (blank
;; Add Item, empty Delete List, non-actionable Mark Done); cleared on
;; the next successful action of the same kind.
;; ============================================================================

(defn set-err-msg*
  "Set or clear the page-level error message. `msg` may be `nil` to
   clear; any string sets the visible error."
  [state-map list-ident msg]
  (assoc-in state-map (conj list-ident :ui/err-msg) msg))

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

(defmutation import-from-text
  "Phase 7.12 — batch import from the save modal textarea. Splits the
   given text on newlines, drops blank lines, and appends each as a
   fresh todo following `add-todo`'s status rule. No-op when the model
   refuses (all-blank input)."
  [{:ui/keys [textarea-import-text]}]
  (action [{:keys [state ref]}]
    (swap! state import-from-text* ref textarea-import-text))
  (remote [env] (remote-list-items env)))

(defmutation keep-link-list
  "Phase 7.18 — user resolved the conflict modal by picking the URL
   list. Replace normalized state with the stashed URL items, then
   close the modal. The URL bar already reflects URL items (the user
   came via that URL); `install-url-sync!` will idempotently re-write
   it on the items-change anyway."
  [_]
  (action [{:keys [state ref]}]
    (swap! state keep-link-list* ref))
  (remote [env] (remote-list-items env)))

(defmutation keep-local-list
  "Phase 7.18 — user resolved the conflict modal by keeping the
   localStorage list. State already holds those items; this is just
   close-the-modal + clear-the-stash + force the URL bar to reflect
   the local items (without an items change, `install-url-sync!`'s
   watch wouldn't fire — see its docstring)."
  [_]
  (action [{:keys [state ref]}]
    (swap! state keep-local-list* ref)
    #?(:cljs
       (let [items (norm/denormalize-list-items @state ref)]
         (url-encoding/replace-url-with-items! items)))))

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

;; Phase 7.9: page-level error setter. `nil` clears, string sets.
(defmutation set-err-msg [{:ui/keys [err-msg]}]
  (action [{:keys [state]}]
    (swap! state set-err-msg* [:list/id 1] err-msg)))

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

#?(:cljs
   (defn- sync-body-theme-class!
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
   (defn- install-body-theme-sync!
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

      `install-ui-prefs-persistence!` (Phase 7.10 / B-1) runs AFTER
      `mount!` because Fulcro's app state-atom only exists once the
      app is mounted. It hydrates `:ui/theme` (and any future
      whitelisted UI prefs) from a separate localStorage key.

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
       ;; B-5 fix: SERVER-DB is `defonce`-initialized to `initial-state`,
       ;; which is the JVM-test seed (2 demo todos). For the deployed
       ;; CLJS app we want first-time visitors to see an empty list,
       ;; not those demo items. Reset BEFORE install-persistence! —
       ;; if localStorage has saved state, it'll overwrite this empty
       ;; baseline; otherwise the user sees an empty list.
       (reset! server/SERVER-DB server/empty-state)
       ;; Hydrate from localStorage and attach the persistence watch
       ;; BEFORE the initial load so any saved state is what we render.
       (let [{hydrated? :hydrated?} (storage/install-persistence! server/SERVER-DB)
             local-items (when hydrated?
                           (server/items @server/SERVER-DB server/list-id))
             url-items   (url-encoding/items-from-current-url)
             decision    (url-encoding/decide-initial-list local-items url-items)]
         ;; Phase 7.18: pure decision drives the initial-state strategy.
         ;; - :url     → overwrite SERVER-DB with the URL list
         ;; - :local   → SERVER-DB already has it (hydration ran)
         ;; - :seed    → SERVER-DB is the seed (no localStorage, no URL)
         ;; - :conflict → leave SERVER-DB as the local list; the modal
         ;;               is set up post-mount and the user picks one.
         (case (:source decision)
           :url      (swap! server/SERVER-DB server/write-items
                       server/list-id (:items decision))
           :conflict nil  ; defer to post-mount
           ;; :local / :seed — no-op (SERVER-DB already correct)
           nil)
         (start-chart! spa)
         (app/mount! spa Root "app")
         (when (= :conflict (:source decision))
           ;; The state-atom only exists after mount! — write the
           ;; transient stash + open the modal here, on the same
           ;; tick, before any user interaction.
           (let [state-atom (:com.fulcrologic.fulcro.application/state-atom spa)]
             (swap! state-atom
               (fn [s]
                 (-> s
                   (assoc-in [:list/id 1 :ui/conflict-url-items]
                     (:url-items decision))
                   (assoc-in [:list/id 1 :ui/open-modal] :conflict))))))
       ;; Mount populates the app state-atom; only now can we hydrate
       ;; the UI-prefs slice into it. The early position keeps theme
       ;; correct from the very first frame the user sees.
       (storage/install-ui-prefs-persistence!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       ;; Same hook point: keep `document.body.className` in sync with
       ;; the active theme so the browser's canvas bg matches the theme
       ;; even when the list overflows the viewport.
       (install-body-theme-sync!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       ;; Phase 7.16: URL sync — write the current list to
       ;; ?list=<encoded> on every items change so the address bar can
       ;; be copied directly (not just via the Copy List URL button).
       (url-encoding/install-url-sync!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       (load-todos! spa)
       spa))))

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
