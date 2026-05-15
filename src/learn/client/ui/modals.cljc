(ns learn.client.ui.modals
  "Phase 12.7 — modal body renderers + the header icon button that
   toggles them. Extracted from `learn.client`.

   Pieces:
     `modal-shell`           — Tachyons overlay used by every modal
     `header-icon-button`    — header trigger that calls `toggle-open-modal`
     `close-current-modal!`  — convenience for the shell's :on-close
     `info-modal`            — Phase 12.3 combined About + Help
     `settings-modal`        — Phase 12.3 Settings shell (lang dropdown 12.5)
     `save-modal`            — Phase 7.6 Import/Export modal
     `conflict-modal`        — Phase 7.18 URL/local conflict modal
     `delete-confirm-modal`  — Phase 7.12 confirm-step for Delete List

   `header-icon-button` lives here (not in `ui.components`) because
   its sole responsibility is opening a modal; co-locating it keeps
   the modal-trigger ↔ modal-body pair adjacent.

   Mutation aliases are re-declared at the top via `m/declare-mutation`
   so we can issue `[(set-open-modal {…})]` etc. without requiring
   `learn.client` (which would create a cycle: client → ui.modals →
   client). The Fulcro multimethod registration still lives in
   `learn.client.mutations`; these aliases are pure Mutation records
   that resolve to the same wire sym `learn.client/<name>`."
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.mutations :as m]
    [learn.client.ui.theme :as theme]
    [learn.i18n.core :as i18n]
    [learn.ui.icons :as icons]
    [learn.ui.strings :as s]
    [learn.util.tasks-io :as tasks-io]
    [learn.util.url-encoding :as url-encoding]
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])))

;; ============================================================================
;; Mutation aliases — Mutation records resolving to wire syms
;; `learn.client/<name>`. See learn.client.mutations docstring for
;; why these aliases exist alongside the registrations.
;; ============================================================================

(m/declare-mutation set-open-modal     learn.client/set-open-modal)
(m/declare-mutation toggle-open-modal  learn.client/toggle-open-modal)
(m/declare-mutation keep-link-list     learn.client/keep-link-list)
(m/declare-mutation keep-local-list    learn.client/keep-local-list)
(m/declare-mutation set-locale            learn.client/set-locale)
(m/declare-mutation set-share-with-locale learn.client/set-share-with-locale)
(m/declare-mutation keep-locale           learn.client/keep-locale)
(m/declare-mutation set-err-msg           learn.client/set-err-msg)
(m/declare-mutation import-from-json      learn.client/import-from-json)

;; ============================================================================
;; Modal overlay shell
;; ============================================================================

(defn modal-shell
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
     :labelled-by — Phase 19a11y: DOM id of an element inside the body
                    (typically the modal's `<h2>`). Wired to
                    `aria-labelledby` so screen readers announce the
                    modal's title when focus enters. Optional; omit if
                    the modal has no canonical heading (the locale-
                    conflict modal doesn't, for example — its question
                    is bilingual and not a heading).

   `children` are positional DOM nodes for the modal body."
  [{:keys [on-close close-label theme labelled-by]
    :or   {theme :theme/light}}
   & children]
  ;; Anchor the overlay to all four edges of `.app-container` (its
  ;; nearest positioned ancestor) so it stretches to whatever height
  ;; the containing block actually has. `top-0 bottom-0` does this
  ;; without relying on `h-100`, which resolves to 100% of an
  ;; ill-defined parent height when `<main>` uses min-height + flex.
  ;;
  ;; Phase 19a11y: role=dialog + aria-modal=true so screen readers
  ;; treat this as a modal interaction surface. `aria-labelledby`
  ;; points to the modal's <h2> id when provided.
  (dom/section (cond-> {:className (str "absolute f5 top-0 bottom-0 left-0 right-0 "
                                        (theme/theme-modal-bg-class theme))
                        :role        "dialog"
                        :aria-modal  "true"}
                 labelled-by (assoc :aria-labelledby labelled-by))
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

;; ============================================================================
;; Header icon button (Phase 7.8) — opens/toggles a modal.
;;
;; Matches the JS port's structure exactly:
;;   <div class="pl3 inline-flex items-center">       ; or pl2
;;     <button class="button-reset pa1 w2 h2 ... gray">
;;       <svg ...>
;;       <span class="clip">label</span>               ; our addition for tests
;;     </button>
;;   </div>
;; The pl3/pl2 lives on the WRAPPER div (not the button), and the button
;; stays a fixed 2rem × 2rem. SVGs without explicit width/height (info,
;; question) fill the button's content area uniformly.
;; ============================================================================

(defn header-icon-button
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
  (dom/div {:className (theme/header-icon-wrapper-class {:first? first?})}
    (dom/button {:type      "button"
                 :className theme/header-icon-btn-class
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

(defn close-current-modal!
  "Dispatch `set-open-modal :none`. Used by `modal-shell`'s :on-close."
  [this]
  (comp/transact! this [(set-open-modal {:ui/open-modal :none})]))

;; ============================================================================
;; Info & Settings modals (Phase 12.3)
;; ============================================================================

(defn info-modal
  "Phase 12.3 — combines the previous About + Help modals under one
   `i`-icon trigger. Two sections under the parent `Info` heading;
   the `?`-icon Help button is gone from the header.

   Phase 12.5b — all body copy now goes through `i18n/tr`. The link
   text (`fulcro-solo-learn Issues`) stays as a proper noun across
   locales; only the surrounding sentence is translated."
  [this theme locale]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label (i18n/tr locale :close/info)
                :theme       theme
                :labelled-by "info-modal-title"}
    (dom/h2 {:id "info-modal-title" :className "pb2 ma0"}
      (i18n/tr locale :modal/info))
    (dom/h3 {:className "f5 fw6 ma0 mb2 pt2"} (i18n/tr locale :info/heading-about))
    (dom/p {:className "pb2 ma0 lh-135"} (i18n/tr locale :info/about-1))
    (dom/p {:className "pb2 ma0 lh-135"} (i18n/tr locale :info/about-2))
    (dom/p {:className "pb3 ma0 lh-135 fw6"}
      (str (i18n/tr locale :info/version-label) " " s/app-version))
    (dom/h3 {:className "f5 fw6 ma0 mb2 pt2"} (i18n/tr locale :info/heading-help))
    (dom/p {:className "pb2 ma0 lh-135"} (i18n/tr locale :info/instructions))
    (dom/p {:className "pb2 ma0 lh-135"} (i18n/tr locale :info/instructions-2))
    (dom/p {:className "pb3 ma0 lh-135"}
      (i18n/tr locale :info/report-issues)
      (dom/a {:href   s/link-issues-href
              :target "_blank"
              :rel    "noopener noreferrer"
              :className "link underline blue hover-orange"}
        s/link-issues-text))
    (dom/p {:className "pt2 pb3 ma0 lh-135"} (i18n/tr locale :info/click-i-circle))))

(defn settings-modal
  "Phase 12.3 / 12.5 — Settings modal. Hosts the language dropdown
   (Phase 12.5) and is the future home for the PWA debug toggle and
   any other user preferences.

   `locale` (Phase 12.4) translates the h2 heading and the language
   label; `i18n/supported-locales` drives the option list and
   `i18n/locale-label` provides the human-readable option text in
   each language's own script (English / Español / 日本語)."
  [this theme locale]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label (i18n/tr locale :close/settings)
                :theme       theme
                :labelled-by "settings-modal-title"}
    (dom/h2 {:id "settings-modal-title" :className "pb2 ma0"}
      (i18n/tr locale :modal/settings))
    (dom/div {:className "pt2 pb2 flex items-center"}
      (dom/label {:htmlFor   "settings-locale"
                  :className "fw6 mr2"}
        (i18n/tr locale :settings/language))
      ;; Dropdown options panel is OS-rendered. `color-scheme` is the
      ;; CSS-standard hint but Chromium on Windows often ignores it
      ;; for form controls when the system is in light mode, leaving
      ;; the panel white-on-white. So we ALSO set explicit
      ;; background-color + color on each <option> in dark mode —
      ;; that inline style is the de facto cross-browser way to make
      ;; the option list themable.
      (let [dark?       (theme/dark? theme)
            option-style (when dark?
                           {:backgroundColor "#1a1a1a"
                            :color           "#ffffff"})]
        (dom/select {:id         "settings-locale"
                     :className  (str "pa1 br3 ba bw1 b--gray "
                                      (theme/theme-modal-input-class theme))
                     :title      (i18n/tr locale :tooltip/language-dropdown)
                     :aria-label (i18n/tr locale :tooltip/language-dropdown)
                     :style      {:colorScheme (if dark? "dark" "light")}
                     :value      (name locale)
                     :onChange   (fn [e]
                                   (let [v (-> e .-target .-value)]
                                     (comp/transact! this
                                       [(set-locale {:ui/locale (keyword v)})])))}
          (for [loc (sort i18n/supported-locales)]
            (dom/option (cond-> {:key   (name loc)
                                 :value (name loc)}
                          option-style (assoc :style option-style))
              (i18n/locale-label loc))))))
    (dom/p {:className "pt2 pb3 ma0 lh-135"} (i18n/tr locale :settings/click-gear))))

;; ============================================================================
;; Save (Import / Export) modal (Phase 7.6)
;; ============================================================================

#?(:cljs
   (defn- current-share-url
     "Build the `?list=...` share URL from the current browser
      location and the items snapshot. Phase 17 — optional
      `locale` arg appends `&lang=<code>` for explicit
      language-stamped sharing (driven by the Save modal's
      Include-language checkbox)."
     ([items] (current-share-url items nil))
     ([items locale]
      (let [loc js/window.location]
        (url-encoding/list-share-url
          (.-origin loc)
          (.-pathname loc)
          (url-encoding/items->base64-url-segment items)
          locale)))))

#?(:cljs
   (defn- copy-list-url!
     "Copy the share URL for `items` to the clipboard via
      `navigator.clipboard.writeText`. Best-effort: silently no-ops if
      the Clipboard API is missing (non-https context, very old
      browsers). The promise's `.catch` keeps a copy failure from
      surfacing as an uncaught rejection.

      Phase 17 — optional `locale` arg flows through to
      `current-share-url`; nil = no lang stamping (today's default
      everywhere except Copy List URL with the checkbox ticked)."
     ([items] (copy-list-url! items nil))
     ([items locale]
      (let [clipboard (some-> js/navigator .-clipboard)]
        (when clipboard
          (-> (.writeText clipboard (current-share-url items locale))
            (.catch (fn [err] (js/console.warn "[copy-list-url] failed:" err)))))))))

#?(:cljs
   (defn- import-json-file!
     "Phase 13 — file-upload handler for the Save modal's Import
      button. Reads the selected file via FileReader, runs the
      result through `tasks-io/parse-tasks-json`, and either
      dispatches the import-from-json mutation (success) or sets
      `:ui/err-msg` to the matching error string (failure). Stays
      a thin wrapper — all validation logic lives in tasks-io.

      `locale` (Phase 16) translates the error messages — caller
      passes the active locale from TodoList props."
     [this evt locale]
     (let [files  (-> evt .-target .-files)
           file   (when files (aget files 0))]
       (when file
         (let [reader (js/FileReader.)]
           (set! (.-onload reader)
             (fn [_]
               (let [text   (.-result reader)
                     result (tasks-io/parse-tasks-json text)]
                 (if (:ok? result)
                   (comp/transact! this
                     [(import-from-json {:items (:items result)})])
                   (comp/transact! this
                     [(set-err-msg
                        {:ui/err-msg
                         (case (:error/type result)
                           :error/non-json (i18n/tr locale :err/non-json-import)
                           (i18n/tr locale :err/bad-json-import))})])))))
           (set! (.-onerror reader)
             (fn [_]
               (comp/transact! this
                 [(set-err-msg
                    {:ui/err-msg (i18n/tr locale :err/bad-json-import)})])))
           (.readAsText reader file)))
       ;; Clear the input's value so the user can re-select the SAME
       ;; file after a parse error (browsers suppress onChange when the
       ;; selected file is identical to the previous selection).
       (set! (.-value (.-target evt)) ""))))

#?(:cljs
   (defn- export-items-json!
     "Phase 13 — Export button handler. Serializes `items` to a JSON
      string in OG-compatible shape, wraps in a Blob, and triggers a
      download via a synthetic anchor click. `tasks.json` matches the
      OG ReactJS port's filename so the round-trip (export this app,
      import in the OG, or vice versa) stays straightforward."
     [items]
     (let [json (url-encoding/items->json items)
           blob (js/Blob. #js [json] #js {:type "application/json"})
           url  (.createObjectURL js/URL blob)
           a    (.createElement js/document "a")]
       (set! (.-href a) url)
       (set! (.-download a) "tasks.json")
       ;; Append-to-body before click is required by some browsers
       ;; (Firefox in particular ignores the click on detached nodes).
       (.appendChild (.-body js/document) a)
       (.click a)
       (.removeChild (.-body js/document) a)
       (.revokeObjectURL js/URL url))))

(def textarea-import-id
  "Stable id paired with the (clip-hidden) `<label htmlFor>` so headless
   tests can target the textarea via `h/type-into-labeled!`."
  "textarea-import")

(defn save-modal
  [this theme locale todos textarea-import-text submit-import! share-with-locale?]
  (modal-shell {:on-close    #(close-current-modal! this)
                :close-label (i18n/tr locale :close/save)
                :theme       theme
                :labelled-by "save-modal-title"}
    (dom/h2 {:id "save-modal-title" :className "pb2 ph3 ma0"}
      (i18n/tr locale :modal/import-export))
    ;; Phase 17 — "Include language in URL" checkbox sits ABOVE the
    ;; Copy List URL button so the user toggles intent first, then
    ;; clicks Copy. When checked, the URL gains `&lang=<locale>`.
    (dom/div {:className "ph3 pt1 pb2"}
      (dom/label {:className "pointer"
                  :title     (i18n/tr locale :tooltip/include-lang)}
        (dom/input {:type       "checkbox"
                    :className  "mr1"
                    :title      (i18n/tr locale :tooltip/include-lang)
                    :aria-label (i18n/tr locale :tooltip/include-lang)
                    :checked    (boolean share-with-locale?)
                    :onChange   (fn [e]
                                  (comp/transact! this
                                    [(set-share-with-locale
                                       {:value (-> e .-target .-checked)})]))})
        (i18n/tr locale :save/include-lang)))
    (dom/div {:className "ph3 pb2"}
      (dom/button {:className (theme/save-modal-wide-btn-class theme)
                   :title     (i18n/tr locale :tooltip/copy-list-url)
                   :onClick   (fn [_]
                                #?(:cljs (copy-list-url! todos
                                           (when share-with-locale? locale))
                                   :clj  nil))}
        (i18n/tr locale :btn/copy-list-url)))
    (dom/p {:className "ph3 ma0 lh-135"} (i18n/tr locale :save/info-1))
    (dom/div {:className "ph3 pt2 tc"}
      ;; File-upload "button" is a styled <label> wrapping a hidden
      ;; <input type="file"> — same pattern the JS port uses.
      (dom/label {:className (str "br3 grow dib button-reset border-box w4 f5 fw6 "
                                  "ba bw1 b--gray "
                                  (theme/theme-primary-btn-suffix theme)
                                  " pa2 pointer ma1")
                  :htmlFor   "save-modal-file-upload"
                  :title     (i18n/tr locale :tooltip/import-json)}
        (i18n/tr locale :btn/import))
      (dom/input {:id         "save-modal-file-upload"
                  :type       "file"
                  :accept     ".json"
                  :className  "dn input-reset"
                  :aria-label (i18n/tr locale :tooltip/import-json)
                  :onChange   (fn [e]
                                #?(:cljs (import-json-file! this e locale)
                                   :clj  nil))})
      (dom/button {:className (theme/save-modal-btn-class theme)
                   :title     (i18n/tr locale :tooltip/export-json)
                   :onClick   (fn [_]
                                #?(:cljs (export-items-json! todos)
                                   :clj  nil))}
        (i18n/tr locale :btn/export)))
    (dom/p {:className "ph3 pt2 ma0 lh-135"} (i18n/tr locale :save/info-2))
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
                                       ;; 12.5c divergence from JS port: the JS source
                                       ;; uses `theme-input-class` here, which fades the
                                       ;; bg to transparent on hover/focus. That fade is
                                       ;; designed for a page-level input on a solid
                                       ;; page bg; in a modal (where the overlay is
                                       ;; already translucent) the stacked transparency
                                       ;; washes the textarea out. Solid bg here.
                                       (theme/theme-modal-input-class theme))
                     :placeholder (i18n/tr locale :save/textarea-placeholder)
                     :rows        2
                     :value       (or textarea-import-text "")
                     :onChange    #(m/set-string! this :ui/textarea-import-text
                                     :event %)})
      (dom/button {:type       "button"
                   :className  (theme/save-modal-wide-btn-class theme)
                   :title      (i18n/tr locale :tooltip/submit-text-import)
                   :aria-label (i18n/tr locale :tooltip/submit-text-import)
                   :onClick    #(submit-import!)}
        (i18n/tr locale :btn/submit)))
    (dom/p {:className "pt2 ph3 pb3 ma0 lh-135"} (i18n/tr locale :save/click-disk))))

;; ============================================================================
;; Conflict resolution modal (Phase 7.18)
;; ============================================================================

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

(defn conflict-modal
  "Phase 7.18 — conflict resolution modal. Opens automatically from
   `init` when the URL list and the localStorage list both exist and
   differ. Shows both lists side-by-side (in vertical stacking
   actually — measure-narrow column), Copy URL buttons for each, and
   two Keep buttons. NO background close — user must pick one of the
   two Keeps (matches the JS port's `docs/js_ui_reference.md` C/6).

   `locale` (Phase 12.4) is accepted for signature symmetry with the
   other modal body fns; conflict-modal body copy stays English in
   the curated 12.4 scope (no keys defined yet).

   B-10 fix: layout matches the OG JS port — both list previews
   stacked at the top, then a two-row button footer at the
   bottom. Row 1: Copy Link URL / Copy Local URL. Row 2: Keep
   link list / Keep local list. Previously the Copy buttons were
   interleaved between the previews, which made the modal feel
   taller than it needed to and visually fragmented the
   decision surface."
  [this theme locale local-items url-items]
  (let [copy-btn-class (str "br3 f6 fw6 ba dib bw1 grow b--gray button-reset "
                            (theme/theme-primary-btn-suffix theme)
                            " pa2 pointer ma1")]
    (modal-shell {:theme theme  ; no :on-close — must choose
                  :labelled-by "list-conflict-question"}
      (dom/p {:id "list-conflict-question" :className "ma0 pb2 lh-135"}
        (i18n/tr locale :conflict/mismatch))
      ;; Both list previews stacked first, so the user can read
      ;; through them before picking — no buttons interleaved.
      (dom/p {:className "fw6 ma0 pt2"} (i18n/tr locale :conflict/label-link))
      (conflict-list-preview url-items)
      (dom/p {:className "fw6 ma0 pt2"} (i18n/tr locale :conflict/label-local))
      (conflict-list-preview local-items)
      ;; Row 1 — Copy URL buttons, side-by-side.
      (dom/div {:className "tc pt3 pb1"}
        (dom/button {:type      "button"
                     :className copy-btn-class
                     :title     (i18n/tr locale :tooltip/copy-link-url)
                     :onClick   (fn [_]
                                  #?(:cljs (copy-list-url! url-items)
                                     :clj  nil))}
          (i18n/tr locale :btn/copy-link-url))
        (dom/button {:type      "button"
                     :className copy-btn-class
                     :title     (i18n/tr locale :tooltip/copy-local-url)
                     :onClick   (fn [_]
                                  #?(:cljs (copy-list-url! local-items)
                                     :clj  nil))}
          (i18n/tr locale :btn/copy-local-url)))
      ;; Row 2 — Keep <side> list buttons, side-by-side.
      (dom/div {:className "tc pb3"}
        (dom/button {:type      "button"
                     :className (theme/delete-confirm-btn-class theme)
                     :title     (i18n/tr locale :tooltip/keep-link)
                     :onClick   #(comp/transact! this [(keep-link-list)])}
          (i18n/tr locale :btn/keep-link))
        (dom/button {:type      "button"
                     :className (theme/delete-confirm-btn-class theme)
                     :title     (i18n/tr locale :tooltip/keep-local)
                     :onClick   #(comp/transact! this [(keep-local-list)])}
          (i18n/tr locale :btn/keep-local))))))

;; ============================================================================
;; Delete-confirm modal (Phase 7.12)
;; ============================================================================

(defn locale-conflict-modal
  "Phase 18 (S-language-conflict-modal) — surfaces when the URL
   `?lang=<code>` differs from the user's saved locale. The user
   has to pick one before the app proceeds (no `:on-close`, no
   background dismiss; identical UX shape to the
   list-conflict modal).

   Question text is shown bilingually so either reader can
   understand. Buttons render each locale's label in its own
   script (`English` / `Español` / `日本語`), so the user can
   recognise their language regardless of which way the prompt
   was shown to them. `keep-locale` mutation persists the choice
   and rewrites the URL (CLJS-only side effect)."
  [this theme {saved-locale :saved url-locale :url}]
  (modal-shell {:theme theme  ; no :on-close — must choose
                :labelled-by "locale-conflict-question"}
    (dom/p {:id "locale-conflict-question" :className "ma0 pb2 lh-135 tc fw6"}
      (str (i18n/tr saved-locale :locale-conflict/question)
        " / "
        (i18n/tr url-locale :locale-conflict/question)))
    (dom/div {:className "tc pt2 pb3"}
      (dom/button {:type      "button"
                   :className (theme/delete-confirm-btn-class theme)
                   :onClick   #(comp/transact! this
                                 [(keep-locale {:value saved-locale})])}
        (i18n/locale-label saved-locale))
      (dom/button {:type      "button"
                   :className (theme/delete-confirm-btn-class theme)
                   :onClick   #(comp/transact! this
                                 [(keep-locale {:value url-locale})])}
        (i18n/locale-label url-locale)))))

(defn delete-confirm-modal
  "Phase 7.12 — confirm step for Delete List. Body text matches the JS
   port's `confirmListDelete` string. Yes empties + closes; No just
   closes; background click also cancels (matches the JS port's
   transparent-close overlay).

   `on-yes` / `on-no` are 0-arg handlers passed in from the TodoList
   render so they can close over the `submit-*!` helpers built there.
   `locale` (Phase 12.4) translates the Yes/No button text."
  [_this theme locale on-yes on-no]
  (modal-shell {:on-close    on-no
                :close-label (i18n/tr locale :close/delete)
                :theme       theme
                :labelled-by "delete-confirm-question"}
    (dom/p {:id "delete-confirm-question" :className "ma0 pb3 lh-135 tc"}
      (i18n/tr locale :modal/confirm-delete))
    (dom/div {:className "tc"}
      (dom/button {:type      "button"
                   :className (theme/delete-confirm-btn-class theme)
                   :title     (i18n/tr locale :tooltip/cancel-delete)
                   :onClick   #(on-no)}
        (i18n/tr locale :btn/no))
      (dom/button {:type      "button"
                   :className (theme/delete-confirm-btn-class theme)
                   :title     (i18n/tr locale :tooltip/confirm-delete)
                   :onClick   #(on-yes)}
        (i18n/tr locale :btn/yes)))))
