(ns learn.util.storage
  "Persistence adapter: round-trip the `learn.server/SERVER-DB` atom to
   `js/localStorage` in the browser so the user's list survives page
   reloads (Phase 7).

   Split:
     `->edn` / `<-edn` — pure CLJC, testable, no I/O. They are the
        adapter boundary; everything below assumes EDN strings.
     `save!` / `load!` — CLJS-only, wrap localStorage's blocking API.
     `install-persistence!` — CLJS-only, hydrates the server atom at
        startup and attaches a watch that writes on every change.

   JVM exposes a no-op `install-persistence!` so tests (and any future
   server-process re-use) can call it without conditional branches at
   call sites."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]))

(def storage-key
  "Namespaced localStorage key. Single key means our entire
   normalized atom is one EDN blob — fine at AutoFocus's scale; if the
   list ever grows we can switch to chunked keys without changing the
   public API."
  "autofocus.server-db")

(def ui-prefs-key
  "Second localStorage key — holds the small slice of Fulcro app state
   that should survive page reloads (currently just `:ui/theme`).
   Kept distinct from `storage-key` so corruption of one doesn't
   poison the other; either falls back to seed/default independently."
  "autofocus.ui-prefs")

(def ui-prefs-whitelist
  "Keys at `[:list/id 1]` that get dehydrated to `ui-prefs-key`.
   Explicit whitelist (not a blacklist) so adding state is opt-in:
   transient UI like `:ui/open-modal` and `:ui/err-msg` SHOULD reset
   on reload, so we want missing-by-default."
  #{:ui/theme})

;; ============================================================================
;; Pure EDN adapter — same on JVM and CLJS.
;; ============================================================================

(defn ->edn
  "Serialize a Clojure value to a string suitable for round-tripping
   via `<-edn`. Plain `pr-str` — keywords, uuids, namespaced keys all
   read back correctly."
  [v]
  (pr-str v))

(defn <-edn
  "Parse an EDN string with `clojure.edn/read-string`. Returns `nil`
   on:
     * `nil` or blank input
     * malformed EDN (reader throws — unbalanced parens, etc.)
     * EDN that requires the reader's eval path (e.g. `#=`)
     * EDN that parses to a non-map value (stray symbol, vector,
       number — anything that's not a normalized server-db shape).

   The map-only guard catches `clojure.edn/read-string` returning the
   first form of garbage text (e.g. `\"not edn at all\"` reads as the
   symbol `not`). The graceful-`nil` contract lets
   `install-persistence!` fall back to seed without a separate
   `try/catch` at the call site."
  [s]
  (cond
    (nil? s)        nil
    (str/blank? s)  nil
    :else (try
            (let [parsed (edn/read-string s)]
              (when (map? parsed) parsed))
            (catch #?(:clj Throwable :cljs :default) _ nil))))

;; ============================================================================
;; Side-effecting I/O — CLJS hits `js/localStorage`; JVM is a no-op.
;; ============================================================================

#?(:cljs
   (defn save!
     "Persist `state` to localStorage. Swallows storage exceptions
      (quota-exceeded, privacy-mode disabled, etc.) — losing one
      write is preferable to a runtime crash; the next swap will
      retry."
     [state]
     (try
       (.setItem js/localStorage storage-key (->edn state))
       (catch :default _ nil))))

#?(:cljs
   (defn load!
     "Read the saved state from localStorage. Returns `nil` if
      missing, blank, or unreadable — the caller falls back to seed."
     []
     (try
       (<-edn (.getItem js/localStorage storage-key))
       (catch :default _ nil))))

#?(:cljs
   (defn clear!
     "Remove the saved state. Useful for testing and for a future
      'Reset' affordance."
     []
     (try
       (.removeItem js/localStorage storage-key)
       (catch :default _ nil))))

(defn install-persistence!
  "Hydrate `server-atom` from localStorage (if a saved state is
   present and parseable), then attach a watch that re-saves on every
   change. Returns the atom for fluent composition.

   JVM: no-op, returns the atom unchanged. The CLJ test suite uses
   `server/seed!` directly and never wants persistence behaviour."
  [server-atom]
  #?(:cljs
     (do
       (when-let [hydrated (load!)]
         (reset! server-atom hydrated))
       (add-watch server-atom ::persistence
         (fn [_k _ref _old new-state] (save! new-state)))
       server-atom)
     :clj server-atom))

;; ============================================================================
;; UI preferences slice (Phase 7.10 / bugs.md B-1).
;;
;; Pure helpers live here so the JVM test suite can exercise them; the
;; CLJS-only `save-ui-prefs!`/`load-ui-prefs!`/`install-ui-prefs-persistence!`
;; below wrap them with `js/localStorage` access.
;; ============================================================================

(defn extract-ui-prefs
  "Slice of `state-map` at `[:list/id 1]` containing only the
   whitelisted UI preference keys. Empty map if no whitelisted keys
   are present (or the list entity is missing)."
  [state-map]
  (-> (get-in state-map [:list/id 1] {})
    (select-keys ui-prefs-whitelist)))

(defn apply-ui-prefs
  "Merge `slice` into `[:list/id 1]` of `state-map`, restricting to
   whitelisted keys so a corrupted-or-old slice can't smuggle in
   non-preference keys. `nil` slice is a no-op."
  [state-map slice]
  (if (nil? slice)
    state-map
    (let [safe-slice (select-keys slice ui-prefs-whitelist)]
      (update-in state-map [:list/id 1] merge safe-slice))))

;; ============================================================================
;; CLJS-only — ui-prefs localStorage I/O + Fulcro-state-atom watch.
;; ============================================================================

#?(:cljs
   (defn save-ui-prefs!
     "Persist the UI-prefs `slice` to localStorage. Swallows storage
      exceptions for the same reason `save!` does."
     [slice]
     (try
       (.setItem js/localStorage ui-prefs-key (->edn slice))
       (catch :default _ nil))))

#?(:cljs
   (defn load-ui-prefs!
     "Read the saved UI-prefs slice from localStorage. Returns `nil`
      on missing/blank/unparseable input."
     []
     (try
       (<-edn (.getItem js/localStorage ui-prefs-key))
       (catch :default _ nil))))

(defn install-ui-prefs-persistence!
  "Hydrate the whitelisted UI-prefs slice into `fulcro-state-atom` from
   localStorage (if a saved slice is present), then attach a watch
   that re-saves only when the extracted slice changes — avoids a
   write storm on every unrelated state change.

   JVM: no-op so the spec suite can call this without conditional
   branches in `learn.client/init`."
  [fulcro-state-atom]
  #?(:cljs
     (do
       (when-let [slice (load-ui-prefs!)]
         (swap! fulcro-state-atom apply-ui-prefs slice))
       (add-watch fulcro-state-atom ::ui-prefs
         (fn [_k _ref old-state new-state]
           (let [old-slice (extract-ui-prefs old-state)
                 new-slice (extract-ui-prefs new-state)]
             (when (not= old-slice new-slice)
               (save-ui-prefs! new-slice)))))
       fulcro-state-atom)
     :clj fulcro-state-atom))
