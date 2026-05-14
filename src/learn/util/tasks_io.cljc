(ns learn.util.tasks-io
  "Phase 13 — JSON import for the AutoFocus list. Counterpart to
   `learn.util.url-encoding/items->json` (the export side).

   The OG ReactJS reference is `pwa-autofocus-app/src/utils/tasksIO.js`
   (`importTasksFromJSON`, `exportTasksToJSON`). We mirror the
   import semantics:
     - JSON.parse failure → `:error/non-json` (UI surfaces 'Please
       select a valid JSON file')
     - Parseable but wrong structure → `:error/bad-json` (UI surfaces
       'Failed to import tasks. Ensure the JSON file has the correct
       format')
     - Valid OG-shape array → `:ok? true` with items translated to
       our namespaced-key schema (fresh UUIDs, keyword statuses).

   Export reuses `learn.util.url-encoding/items->json` directly — no
   wrapper needed; that function already emits the OG-compatible
   shape because the URL encoder shares the same JSON layer."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [learn.util.url-encoding :as url-encoding]))

#?(:clj
   (defn- valid-json-top-level?
     "EDN can read more shapes than JSON (symbols, characters,
      regexes, etc.). After our EDN rewrite, a result that ISN'T one
      of JSON's valid top-level types means the input wasn't JSON to
      begin with — `not json at all` reads as the symbol `not` for
      example. This predicate rejects those, so `try-parse-json-clj`
      can map them to ::parse-failed (== :error/non-json downstream)
      instead of letting them slide through as :error/bad-json."
     [v]
     (or (map? v) (vector? v) (string? v) (number? v) (boolean? v) (nil? v))))

#?(:clj
   (defn- try-parse-json-clj
     "JVM-side JSON parser. Reuses the EDN-rewrite trick from
      `url-encoding` and adds a strict top-level type check so the
      'EDN read a symbol' fallback doesn't swallow non-JSON input.
      Returns ::parse-failed when the reader threw OR when the
      result isn't a valid JSON top-level type; otherwise returns
      the parsed value."
     [^String s]
     (try
       (let [edn-ish (-> s
                       ;; JSON object keys → EDN keywords:
                       ;;   "key": value  →  :key value
                       (str/replace #"\"([a-zA-Z_][a-zA-Z0-9_]*)\"\s*:" ":$1 ")
                       ;; null → nil so EDN reader accepts it
                       (str/replace "null" "nil"))
             parsed  (edn/read-string edn-ish)]
         (if (valid-json-top-level? parsed) parsed ::parse-failed))
       (catch Throwable _ ::parse-failed))))

(defn- try-parse-json
  "Parse `s` as JSON. Returns ::parse-failed sentinel if the parser
   itself threw; otherwise returns Clojure data (vector / map /
   scalar). Distinguishing parse-failure from 'parsed something we
   don't want' is the whole reason this helper exists —
   `parse-tasks-json` needs to map those two cases to different
   error types.

   IMPORTANT: the CLJS branch does `js->clj :keywordize-keys true`
   inline. Without it, `js/JSON.parse` returns a native JS array
   (for valid OG-shape input), which is NOT `sequential?` in CLJS
   and would slip into the `:error/bad-json` branch even though
   the input was perfectly valid. The JVM branch already returns
   Clojure data."
  [s]
  (cond
    (nil? s) ::parse-failed
    (and (string? s) (str/blank? s)) ::parse-failed
    :else
    #?(:cljs
       (try
         (js->clj (js/JSON.parse s) :keywordize-keys true)
         (catch :default _ ::parse-failed))
       :clj
       (try-parse-json-clj s))))

(defn parse-tasks-json
  "Parse a JSON string into a vector of our items, or return a
   structured error. Result shapes:
     {:ok? true  :items <vector>}                         — success
     {:ok? false :error/type :error/non-json}             — JSON.parse threw
     {:ok? false :error/type :error/bad-json}             — parsed OK but wrong structure
                                                            (not an array, items missing
                                                            required fields, bad status)

   The valid-shape contract delegates to
   `learn.util.url-encoding/og-shape->items` — it returns nil if any
   item fails validation, so a nil result there maps to `:error/bad-json`.

   On a successful import the parsed items get FRESH UUIDs (the
   OG int-ids don't map to our UUID schema), so re-importing a
   list you previously exported appends a copy with new ids — the
   OG ReactJS port's `addAll` does the same."
  [s]
  (let [parsed (try-parse-json s)]
    (cond
      (= ::parse-failed parsed)
      {:ok? false :error/type :error/non-json}

      (not (sequential? parsed))
      {:ok? false :error/type :error/bad-json}

      :else
      (if-let [items (url-encoding/og-shape->items parsed)]
        {:ok? true :items items}
        {:ok? false :error/type :error/bad-json}))))
