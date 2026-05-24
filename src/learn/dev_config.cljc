(ns learn.dev-config
  "Phase 21 — dev-mode toggle infrastructure.

   Two concerns:

   1. **Debug-CSS flags.** `dev-flags-defaults` is the source-of-truth
      map; the `dev-flags` atom holds runtime overrides; localStorage
      under `dev-flags-key` persists them across reloads.
      `install-dev-flags-persistence!` ties the round-trip together
      (CLJS hydrates + attaches a save-watch; JVM is a no-op).

   2. **List-cycler.** A four-position cursor
      (`:actual | :empty | :5 | :26`) advanced one step at a time.
      `next-cycle-position` / `cycle-action` / `position->items` are
      pure (JVM-testable). The orchestrator that performs the actual
      snapshot/restore + SERVER-DB swap lands in 21.4 alongside the
      Settings UI button that triggers it.

   Pure pieces stay CLJC; localStorage I/O is wrapped in
   `#?(:cljs ...)` so the JVM spec runner doesn't blow up on
   `js/localStorage`. Style mirrors `learn.util.storage`."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [learn.dev-fixtures :as fixtures]))

;; ============================================================================
;; localStorage keys
;; ============================================================================

(def dev-flags-key       "autofocus.dev-flags")
(def dev-list-cursor-key "autofocus.dev-list-cursor")

(def dev-list-snapshot-key
  "Snapshot of SERVER-DB captured on the FIRST cycle step away from
   `:actual`, so the user's real data is restored when the cursor
   wraps back. Cleared on restore."
  "autofocus.dev-list-snapshot")

;; ============================================================================
;; Flags — defaults + defensive merge
;; ============================================================================

(def dev-flags-defaults
  "Source-of-truth defaults for the dev-toggle booleans. Both default
   `false` so a release build (which drops this whole namespace via
   `^boolean goog.DEBUG` gating at call sites) never accidentally
   surfaces debug visuals."
  {:debug-css/rainbow? false
   :debug-css/depth?   false})

(defn merge-flags
  "Defensive merge: for each key in `dev-flags-defaults`, use the
   `loaded` value iff it's a boolean — otherwise fall back to the
   default. Unknown keys in `loaded` are dropped, so a stray entry in
   localStorage can't smuggle itself into the flags atom."
  [loaded]
  (reduce-kv
    (fn [acc k default]
      (let [loaded-v (get loaded k)]
        (assoc acc k (if (boolean? loaded-v) loaded-v default))))
    {}
    dev-flags-defaults))

(def dev-flags
  "Runtime-mutable atom. Initialized to defaults;
   `install-dev-flags-persistence!` hydrates it from localStorage at
   startup if a saved slice exists."
  (atom dev-flags-defaults))

;; ============================================================================
;; List-cycler — pure
;; ============================================================================

(def ^:private cycle-next
  "Wrap-around: :actual → :empty → :5 → :26 → :actual."
  {:actual :empty
   :empty  :5
   :5      :26
   :26     :actual})

(defn next-cycle-position
  "Returns the next cycle position. Unknown / nil input defaults to
   `:actual` so a stale-cursor reload can't get stuck on an unrecognised
   value."
  [current]
  (get cycle-next current :actual))

(defn cycle-action
  "Pure dispatcher. Given the current cursor, returns
   `{:from <pos> :to <next-pos> :do <action>}` describing the one
   side-effect step the orchestrator should perform:

     :snapshot-and-apply  — leaving `:actual` (capture SERVER-DB)
     :apply               — fixture → fixture (snapshot already exists)
     :restore-and-clear   — returning to `:actual` (restore + clear)

   Unknown input normalizes to `:actual`, matching `next-cycle-position`."
  [current]
  (let [from   (if (contains? cycle-next current) current :actual)
        to     (cycle-next from)
        action (cond
                 (= :actual from) :snapshot-and-apply
                 (= :actual to)   :restore-and-clear
                 :else            :apply)]
    {:from from :to to :do action}))

(defn position->items
  "Denormalized items vector for the named fixture position.
   `:actual` returns `nil` (sentinel — the orchestrator restores from
   the snapshot instead of applying a fixture)."
  [position]
  (case position
    :5     fixtures/items-5
    :26    fixtures/items-26
    :empty []
    nil))

;; ============================================================================
;; localStorage round-trip — CLJS hits `js/localStorage`; JVM has no
;; defs (callers must guard via `#?(:cljs ...)` or wait for the
;; CLJC `install-*!` orchestrators below).
;;
;; All wrapped in try/catch so quota / privacy-mode failures degrade
;; gracefully — one missed write/read is preferable to a runtime crash.
;; ============================================================================

(defn- read-edn-str
  "Pure: parse a string with `clojure.edn/read-string`. nil/blank input
   or any read error yields nil. Shared by the cljs load-*! helpers."
  [s]
  (cond
    (nil? s)       nil
    (str/blank? s) nil
    :else (try (edn/read-string s)
               (catch #?(:clj Throwable :cljs :default) _ nil))))

#?(:cljs
   (defn load-flags!
     "Read the saved flags slice, pass it through `merge-flags`, and
      return a clean defaults-keyed map. Returns `dev-flags-defaults`
      verbatim if nothing's there."
     []
     (try
       (merge-flags (read-edn-str (.getItem js/localStorage dev-flags-key)))
       (catch :default _ dev-flags-defaults))))

#?(:cljs
   (defn save-flags!
     "Persist `flags` to localStorage. Swallows storage exceptions."
     [flags]
     (try
       (.setItem js/localStorage dev-flags-key (pr-str flags))
       (catch :default _ nil))))

#?(:cljs
   (defn load-cursor!
     "Read the saved cycle position. Returns `nil` for missing,
      unreadable, or unrecognised values — callers treat nil as
      `:actual`."
     []
     (try
       (let [v (read-edn-str (.getItem js/localStorage dev-list-cursor-key))]
         (when (contains? cycle-next v) v))
       (catch :default _ nil))))

#?(:cljs
   (defn save-cursor!
     [position]
     (try
       (.setItem js/localStorage dev-list-cursor-key (pr-str position))
       (catch :default _ nil))))

#?(:cljs
   (defn load-snapshot!
     "Read the snapshotted SERVER-DB from localStorage. Returns nil on
      missing/blank/unparseable input. The map shape isn't validated
      here — callers feed it back into SERVER-DB and let the existing
      schema-tolerant paths catch any corruption."
     []
     (try
       (read-edn-str (.getItem js/localStorage dev-list-snapshot-key))
       (catch :default _ nil))))

#?(:cljs
   (defn save-snapshot!
     [server-db]
     (try
       (.setItem js/localStorage dev-list-snapshot-key (pr-str server-db))
       (catch :default _ nil))))

#?(:cljs
   (defn clear-snapshot!
     []
     (try
       (.removeItem js/localStorage dev-list-snapshot-key)
       (catch :default _ nil))))

;; ============================================================================
;; Higher-level install — CLJC with internal branching
;; ============================================================================

(defn install-dev-flags-persistence!
  "Hydrate `dev-flags` from localStorage (if a saved slice is
   present and parseable), then attach a watch that re-saves on every
   change. Returns the atom.

   JVM: no-op — returns the atom unchanged so callers can use a single
   signature."
  []
  #?(:cljs
     (do
       (when-let [loaded (load-flags!)]
         (reset! dev-flags loaded))
       (add-watch dev-flags ::persistence
         (fn [_k _ref _old new-flags] (save-flags! new-flags)))
       dev-flags)
     :clj dev-flags))
