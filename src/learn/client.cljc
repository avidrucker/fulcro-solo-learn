(ns learn.client
  "Fulcro client entry point for the AutoFocus app.

   Phase 12.7 made this a thin facade. The real work lives in:
     `learn.client.session`        — cross-ns constants
     `learn.client.state`          — pure state-map helpers
     `learn.client.mutations`      — Fulcro defmutations (server-wired)
     `learn.client.ui.theme`       — Tachyons class strings + theme helpers
     `learn.client.ui.modals`      — modal shell + bodies + header icon button
     `learn.client.ui.components`  — TodoItem / TodoList / Root

   What stays here:
     - Re-exports preserving `learn.client/<name>` for tests and any
       external symbol reader (state helpers, mutations, components).
     - App construction: `SPA`, `start-chart!`, `load-todos!`, the
       CLJ + CLJS `init` fns, and `snapshot` for the REPL. These will
       move to `learn.client.lifecycle` in a follow-up commit.

   Status enum (per AutoFocus spec):
     :status/new       — added but not yet reviewed
     :status/ready     — actionable
     :status/done      — completed
     :status/cancelled — explicitly cancelled (preserves prior status in :todo/was)"
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    ;; The Fulcro headless library is JVM-only — used by the spec suite
    ;; via `init`. The browser build doesn't need it; see `learn.util.remote`
    ;; for the CLJC `sync-remote` shim used in the CLJS init branch.
    #?(:clj [com.fulcrologic.fulcro.headless :as h])
    #?(:clj [com.fulcrologic.fulcro.headless.loopback-remotes :as lr])
    ;; Fulcro Inspect 1.x requires explicit registration via
    ;; `add-fulcro-inspect!` at app-build time. CLJS-only because the
    ;; Chrome extension is browser-side only.
    #?(:cljs [fulcro.inspect.tool :as inspect-tool])
    [com.fulcrologic.fulcro.mutations :as m]
    [learn.client.lifecycle :as lifecycle]
    [learn.client.mutations :as mutations]
    [learn.client.session :as session]
    [learn.client.state :as state]
    [learn.client.ui.components :as components]
    [learn.client.ui.modals :as modals]
    [learn.parser :as parser]
    [learn.server :as server]
    [learn.util.remote :as remote]
    [learn.util.storage :as storage]
    [learn.util.url-encoding :as url-encoding]))

;; ============================================================================
;; Constants — Phase 12.7 moved to `learn.client.session`. The two re-
;; exports below preserve `learn.client/review-session-id` and
;; `learn.client/review-chart-key` for any external readers (tests,
;; REPL bindings) that captured the old symbols.
;; ============================================================================

(def review-session-id session/review-session-id)
(def review-chart-key  session/review-chart-key)

;; ============================================================================
;; Dev-config — flip these options to toggle dev-only visuals.
;;
;; SAFETY: `install-debug-css!` (below) gates the WHOLE installer on
;; `^boolean goog.DEBUG`. In a release build, `goog.DEBUG` is replaced
;; with `false` at compile time and Google Closure Compiler's advanced
;; optimisation drops the `(when ...)` block as dead code. So even if
;; an option is accidentally committed `true`, RELEASE builds will
;; never load any debug CSS. Dev (`shadow-cljs watch`) honours the
;; options as written.
;; ============================================================================

(def debug-css-options
  "Each option, when `true`, injects a `<link>` to the matching
   stylesheet via the CLJS `init` function:

     :rainbow — `css/pesticide.css` — different OUTLINE colour per
                element tag (rainbow outlines).
     :depth   — `css/pesticide-depth.css` — translucent BACKGROUND
                colour keyed to nesting depth.

   Combinable. Hot-reload picks up edits to this map, but the
   browser must be refreshed for the new options to apply (init
   only runs on page load). Browser-side only; headless JVM
   `init` ignores this entirely."
  {:rainbow true
   :depth   true})

;; ============================================================================
;; UI components — Phase 12.7 moved to `learn.client.ui.components`. The
;; aliases below preserve `learn.client/Root` and `learn.client/TodoItem`,
;; which `init` and `load-todos!` reference unqualified.
;; ============================================================================

(def Root     components/Root)
(def TodoItem components/TodoItem)

;; DOM-id constant — headless tests target the import textarea via
;; `h/type-into!` which needs the id. Re-exported here so existing
;; `sut/textarea-import-id` references in client_test keep resolving
;; (without this the CI test compile fails with `No such var:
;; sut/textarea-import-id`).
(def textarea-import-id modals/textarea-import-id)

;; ============================================================================
;; Pure state helpers — Phase 12.7 moved to `learn.client.state`. The
;; `def` aliases preserve `learn.client/foo*` for tests, which use
;; `sut/add-todo*` etc.
;; ============================================================================

(def add-todo*                state/add-todo*)
(def cancel-todo*             state/cancel-todo*)
(def clone-todo*              state/clone-todo*)
(def complete-benchmark-item* state/complete-benchmark-item*)
(def delete-all*              state/delete-all*)
(def import-from-text*        state/import-from-text*)
(def import-from-json*        state/import-from-json*)
(def keep-link-list*          state/keep-link-list*)
(def keep-local-list*         state/keep-local-list*)
(def set-err-msg*             state/set-err-msg*)
(def set-locale*              state/set-locale*)
(def set-share-with-locale*   state/set-share-with-locale*)
(def set-locale-conflict-pair* state/set-locale-conflict-pair*)
(def keep-locale*             state/keep-locale*)
(def set-open-modal*          state/set-open-modal*)
(def set-status*              state/set-status*)
(def toggle-open-modal*       state/toggle-open-modal*)
(def toggle-theme*            state/toggle-theme*)

;; ============================================================================
;; Mutations — Phase 12.7 moved to `learn.client.mutations`.
;;
;; Each defmutation is registered under its full wire sym
;; (`learn.client/add-todo` etc.) by passing a qualified symbol to the
;; Fulcro macro; this preserves the server's `::pc/sym` dispatch
;; without renaming the wire protocol. The macro skips creating a var
;; in `learn.client.mutations` when it sees a qualified sym, so we
;; declare callable Mutation records here via `m/declare-mutation` for
;; the UI layer (`comp/transact! [(add-todo {…})]`) and for tests
;; (`sut/add-todo` etc.). Requiring `mutations` above is what actually
;; registers the multimethods.
;; ============================================================================

(m/declare-mutation add-todo                 learn.client/add-todo)
(m/declare-mutation cancel-todo              learn.client/cancel-todo)
(m/declare-mutation clone-todo               learn.client/clone-todo)
(m/declare-mutation complete-benchmark-item  learn.client/complete-benchmark-item)
(m/declare-mutation delete-all               learn.client/delete-all)
(m/declare-mutation import-from-json         learn.client/import-from-json)
(m/declare-mutation import-from-text         learn.client/import-from-text)
(m/declare-mutation keep-link-list           learn.client/keep-link-list)
(m/declare-mutation keep-local-list          learn.client/keep-local-list)
(m/declare-mutation set-err-msg              learn.client/set-err-msg)
(m/declare-mutation set-locale               learn.client/set-locale)
(m/declare-mutation set-share-with-locale    learn.client/set-share-with-locale)
(m/declare-mutation keep-locale              learn.client/keep-locale)
(m/declare-mutation set-open-modal           learn.client/set-open-modal)
(m/declare-mutation set-status               learn.client/set-status)
(m/declare-mutation sync-list                learn.client/sync-list)
(m/declare-mutation toggle-open-modal        learn.client/toggle-open-modal)
(m/declare-mutation toggle-theme             learn.client/toggle-theme)

#?(:cljs
   (defn- ensure-debug-link!
     "Idempotent: append a `<link rel=\"stylesheet\" href=...>` to
      `<head>` with the given marker id. No-op if a tag with that
      marker already exists (so hot-reload runs don't duplicate)."
     [marker-id href]
     (when-not (.getElementById js/document marker-id)
       (let [link (.createElement js/document "link")]
         (set! (.-id link)   marker-id)
         (set! (.-rel link)  "stylesheet")
         (set! (.-href link) href)
         (.appendChild (.-head js/document) link)))))

#?(:cljs
   (defn install-debug-css!
     "Inspect `debug-css-options` and inject one `<link>` per enabled
      option. Gated on `^boolean goog.DEBUG` so RELEASE builds drop
      the entire body via dead-code elimination — debug CSS can NEVER
      ship to prod, even if an option is accidentally committed
      `true`. No-op when all options are false."
     []
     (when ^boolean goog.DEBUG
       (when (:rainbow debug-css-options)
         (ensure-debug-link! "debug-css-pesticide-rainbow"
                             "css/pesticide.css"))
       (when (:depth debug-css-options)
         (ensure-debug-link! "debug-css-pesticide-depth"
                             "css/pesticide-depth.css")))))

;; ============================================================================
;; App construction
;;
;; The helpers below — start-chart!, sync-body-theme-class!,
;; install-body-theme-sync!, load-todos!, and the SPA atom — live in
;; `learn.client.lifecycle`. `init` and `snapshot` stay here because
;; shadow-cljs's `:init-fn learn.client/init` config references the
;; qualified symbol, and tests import `learn.client/init` via the
;; sut alias.
;; ============================================================================

(def SPA lifecycle/SPA)

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
       (reset! lifecycle/SPA spa)
       (lifecycle/start-chart! spa)
       (app/mount! spa Root :app)
       (lifecycle/load-todos! spa TodoItem)
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
     ;; Dev-only: load the Pesticide rainbow-outline stylesheet IFF
     ;; `debug-css?` is true. Runs first so element outlines appear
     ;; on the initial paint, not after the first render. No-op when
     ;; the flag is false.
     (install-debug-css!)
     (let [spa (app/fulcro-app
                 {:remotes {:remote (remote/sync-remote parser/handler)}})]
       (reset! lifecycle/SPA spa)
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
         (lifecycle/start-chart! spa)
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
       ;; Phase 19g a11y: focus management on modal open/close.
       ;; MUST run BEFORE `install-url-locale-fallback!` —
       ;; locale-fallback can open the locale-conflict modal during
       ;; init, and if the focus watcher isn't already attached, the
       ;; :none → :locale-conflict transition fires unobserved.
       (lifecycle/install-modal-focus-sync!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       ;; Same precedence reasoning for the review-modal focus watcher,
       ;; though `install-url-locale-fallback!` itself never enters
       ;; review-state — keeps the two focus syncs grouped.
       (lifecycle/install-review-modal-focus-sync!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       ;; Phase 14 — apply ?lang= ONLY when localStorage has no
       ;; :ui/locale yet (first-time visitor). localStorage > URL > :en.
       ;; Runs AFTER install-ui-prefs-persistence! so the save-watch
       ;; is in place to persist the URL-derived locale on the next
       ;; swap (visitor's choice becomes their saved preference).
       (lifecycle/install-url-locale-fallback!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       ;; Same hook point: keep `document.body.className` in sync with
       ;; the active theme so the browser's canvas bg matches the theme
       ;; even when the list overflows the viewport.
       (lifecycle/install-body-theme-sync!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       ;; Phase 19 a11y: keep `<html lang>` in sync with :ui/locale so
       ;; screen readers pick the right voice / pronunciation.
       (lifecycle/install-html-lang-sync!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       ;; Phase 19h a11y: Escape closes dismissible modals.
       (lifecycle/install-escape-to-close! spa)
       ;; Phase 7.16: URL sync — write the current list to
       ;; ?list=<encoded> on every items change so the address bar can
       ;; be copied directly (not just via the Copy List URL button).
       (url-encoding/install-url-sync!
         (:com.fulcrologic.fulcro.application/state-atom spa))
       (lifecycle/load-todos! spa TodoItem)
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
