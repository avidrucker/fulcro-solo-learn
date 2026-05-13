(ns learn.util.url-encoding
  "Encode and decode the AutoFocus list into a URL-safe segment for the
   `?list=<segment>` share link.

   The JS port's recipe is `btoa(encodeURIComponent(JSON.stringify(items)))`
   and the empty-list fixture `[] → \"JTVCJTVE\"` ships in
   `docs/snapshots/reference/README.md`. We mirror the three steps so a
   URL produced by either port is byte-compatible.

   Shape translation (Phase 7.15):
   The JS port's JSON items have plain keys + integer ids + string
   statuses (`{\"id\":0,\"text\":\"a\",\"status\":\"ready\"}`). Our items
   have namespaced keys + UUID ids + keyword statuses. `items->og-shape`
   /  `og-shape->items` translate between them; encoding always emits the
   OG shape, decoding always returns our shape.

   Split:
     `status->og-string` / `og-string->status` — status keyword ↔ string
     `items->og-shape`   / `og-shape->items`   — vector-of-items shape conversion
     `items->json`       / *parsed via the chain below* — JSON step
     `js-url-encode`     / `js-url-decode`     — `encodeURIComponent` ↔ `decodeURIComponent`
     `base64-encode`     / `base64-decode`     — `btoa` ↔ `atob`
     `items->base64-url-segment` / `url-segment->items` — full round-trip
     `list-share-url`    — wrap a segment into a shareable URL"
  (:require
    [clojure.string :as str]
    [learn.util.normalized :as norm])
  #?(:clj (:import (java.util Base64))))

;; ============================================================================
;; Shape translation — status keyword ↔ string, item ↔ og-item, vector
;; ============================================================================

(def ^:private status-keyword->string
  {:status/new       "new"
   :status/ready     "ready"
   :status/done      "done"
   :status/cancelled "cancelled"})

(def ^:private status-string->keyword
  (into {} (map (fn [[k v]] [v k])) status-keyword->string))

(defn status->og-string
  "Map a status keyword (`:status/new` etc.) to the JS port's lowercase
   string form. Returns nil for unknown keywords (caller validates)."
  [s]
  (get status-keyword->string s))

(defn og-string->status
  "Inverse of `status->og-string`. Returns nil for unknown strings or
   non-strings — caller decides whether nil is an error."
  [s]
  (get status-string->keyword s))

(defn- item->og-item
  "Translate one of our items into the JS-port's plain-keyword shape.
   Uses an `array-map` so JSON encoders that follow insertion order
   (CLJS `clj->js` + `JSON.stringify`; our JVM hand-rolled encoder)
   emit keys in the order id, text, status, was."
  [idx item]
  (let [base (array-map
               :id     idx
               :text   (:todo/text item)
               :status (status->og-string (:todo/status item)))]
    (if (= :status/cancelled (:todo/status item))
      (assoc base :was (status->og-string (:todo/was item)))
      base)))

(defn items->og-shape
  "Translate a vector of our items into a vector of OG-shape maps.
   Ids are derived from list position (0, 1, …) — the JS port's
   sequence is order-stable too, so this is information-preserving
   for round-trips through encode → URL → decode."
  [items]
  (vec (map-indexed item->og-item items)))

(defn- og-item->item
  "Inverse of `item->og-item`. Generates a fresh UUID for `:todo/id`
   (OG integer ids don't map onto our schema). Returns nil if the
   item shape is invalid — caller filters / aborts."
  [og-item]
  (when (and (map? og-item)
          (contains? og-item :text)
          (string? (:text og-item))
          (contains? og-item :status))
    (let [status (og-string->status (:status og-item))]
      (when status
        (cond-> {:todo/id     (random-uuid)
                 :todo/text   (:text og-item)
                 :todo/status status}
          (= status :status/cancelled)
          (assoc :todo/was (or (og-string->status (:was og-item))
                             ;; `:was` is omitted in some legacy fixtures
                             ;; (e.g. items cancelled before status-tracking).
                             ;; Default to :status/new so the schema
                             ;; invariant (`:was` present iff cancelled) holds.
                             :status/new)))))))

(defn og-shape->items
  "Inverse of `items->og-shape`. Returns nil if `og-items` isn't a
   vector or any item fails validation (defensive — corrupt URL =
   caller falls back to seed / localStorage)."
  [og-items]
  (when (sequential? og-items)
    (let [parsed (mapv og-item->item og-items)]
      (when (every? some? parsed) parsed))))

;; ============================================================================
;; Step 1 — JSON encoding (writes the OG shape)
;; ============================================================================

#?(:clj
   (declare ->json*))

#?(:clj
   (defn- escape-str
     "JSON-string escape — quotes, backslashes, and the control whitespace
      sequences JS `JSON.stringify` emits as escape codes."
     [^String s]
     (-> s
       (str/replace "\\" "\\\\")
       (str/replace "\"" "\\\"")
       (str/replace "\n" "\\n")
       (str/replace "\r" "\\r")
       (str/replace "\t" "\\t"))))

#?(:clj
   (defn- map->json [m]
     (str "{"
       (str/join ","
         (map (fn [[k v]]
                (str (->json* (cond
                                (keyword? k) (subs (str k) 1)
                                :else        (str k)))
                  ":"
                  (->json* v)))
           m))
       "}")))

#?(:clj
   (defn- ->json* [v]
     (cond
       (nil? v)        "null"
       (boolean? v)    (str v)
       (number? v)     (str v)
       (string? v)     (str "\"" (escape-str v) "\"")
       (keyword? v)    (str "\"" (escape-str (subs (str v) 1)) "\"")
       (uuid? v)       (str "\"" (str v) "\"")
       (map? v)        (map->json v)
       (sequential? v) (str "[" (str/join "," (map ->json* v)) "]")
       :else           (str "\"" (escape-str (str v)) "\""))))

(defn items->json
  "Serialize `items` (our shape) to a JSON string in the JS-port-
   compatible OG shape. Empty list produces literal `\"[]\"`."
  [items]
  (let [og (items->og-shape items)]
    #?(:cljs (js/JSON.stringify (clj->js og))
       :clj  (->json* og))))

;; ============================================================================
;; Step 1 (inverse) — JSON decoding
;; ============================================================================

#?(:clj
   (defn- json-str->seq-of-maps
     "Tiny hand-rolled JSON reader covering exactly the shape we receive:
      a top-level array of objects with `id` (int), `text` (string),
      `status` (string), optional `was` (string). Reuses `clojure.edn`
      after a controlled string-rewrite — JSON and EDN agree on
      `null`, numbers, strings, arrays, and objects with string keys,
      so converting `\"key\":` → `:key ` is enough.

      Returns nil on any failure."
     [^String s]
     (try
       (let [edn-ish (-> s
                       ;; "key": → :key — JSON keys to EDN keywords
                       (str/replace #"\"([a-zA-Z_][a-zA-Z0-9_]*)\"\s*:" ":$1 ")
                       ;; null → nil
                       (str/replace "null" "nil"))
             parsed  (clojure.edn/read-string edn-ish)]
         (when (sequential? parsed) parsed))
       (catch Throwable _ nil))))

(defn- parse-json-array
  "Parse a JSON string assumed to be an array of OG-shape items.
   Returns a Clojure sequence of maps with keyword keys (`:id` etc.),
   or nil on parse failure / non-array input."
  [s]
  #?(:cljs (try
             (let [parsed (js/JSON.parse s)]
               (when (array? parsed)
                 (js->clj parsed :keywordize-keys true)))
             (catch :default _ nil))
     :clj  (json-str->seq-of-maps s)))

;; ============================================================================
;; Step 2 — URL component encode / decode (mirrors JS encodeURIComponent / decodeURIComponent)
;; ============================================================================

#?(:clj
   (def ^:private url-encode-unreserved
     "Characters JS `encodeURIComponent` leaves untouched (in addition to
      letters and digits). RFC 3986 unreserved (`-`, `_`, `.`, `~`) plus
      the JS-specific exemptions (`!`, `'`, `(`, `)`, `*`)."
     #{\- \_ \. \~ \! \' \( \) \*}))

#?(:clj
   (defn- byte->%xx [b]
     (format "%%%02X" (bit-and (int b) 0xFF))))

#?(:clj
   (defn- encode-char-jvm [^Character c]
     (let [code (int c)]
       (cond
         ;; ASCII alpha / digit pass through unchanged.
         (or (<= (int \A) code (int \Z))
             (<= (int \a) code (int \z))
             (<= (int \0) code (int \9)))
         (str c)

         ;; JS encodeURIComponent leaves these alone.
         (contains? url-encode-unreserved c)
         (str c)

         :else
         ;; Encode by UTF-8 bytes. A single char may be in the surrogate
         ;; range; `String.getBytes` handles the conversion safely here
         ;; because we receive `c` from a full string traversal.
         (apply str
           (map byte->%xx
             (.getBytes (str c) "UTF-8")))))))

(defn js-url-encode
  "JS `encodeURIComponent` equivalent. Space becomes `%20` (not `+`); the
   unreserved set `-_.~!'()*` plus ASCII letters and digits pass through;
   everything else is %-encoded by UTF-8 byte."
  [s]
  #?(:cljs (js/encodeURIComponent s)
     :clj  (apply str (map encode-char-jvm s))))

(defn js-url-decode
  "JS `decodeURIComponent` equivalent. Returns nil on malformed input
   instead of throwing — caller treats nil as 'corrupt URL, fall back'."
  [s]
  #?(:cljs (try (js/decodeURIComponent s)
                (catch :default _ nil))
     :clj
     (try
       (let [u (java.net.URLDecoder/decode (.replace ^String s "+" "%2B") "UTF-8")]
         u)
       (catch Throwable _ nil))))

;; ============================================================================
;; Step 3 — base64 (mirrors JS btoa / atob)
;; ============================================================================

(defn base64-encode
  "Base64-encode `s`. Mirrors JS `btoa`: standard alphabet, `=` padding,
   no line breaks. On JVM we use ISO-8859-1 to byte-encode the string
   because `btoa` is itself a byte-by-byte operation over Latin-1 code
   points (it throws on chars > 0xFF); our callers feed it ASCII-only
   URL-encoded output, so either encoding agrees."
  [s]
  #?(:cljs (js/btoa s)
     :clj  (.encodeToString (Base64/getEncoder) (.getBytes ^String s "ISO-8859-1"))))

(defn base64-decode
  "Inverse of `base64-encode`. Returns nil on malformed input — caller
   treats nil as 'corrupt URL'."
  [s]
  #?(:cljs (try (js/atob s)
                (catch :default _ nil))
     :clj
     (try
       (String. (.decode (Base64/getDecoder) ^String s) "ISO-8859-1")
       (catch Throwable _ nil))))

;; ============================================================================
;; Composition + URL construction
;; ============================================================================

(defn items->base64-url-segment
  "Encode our items vector into the URL segment suitable for `?list=<here>`."
  [items]
  (-> items items->json js-url-encode base64-encode))

(defn url-segment->items
  "Decode a URL `?list=` segment back into our items vector. Returns
   nil on any failure (corrupt base64, malformed URL-encoded JSON,
   non-array JSON, item shape validation failure)."
  [segment]
  (when-let [json (some-> segment base64-decode js-url-decode)]
    (some-> (parse-json-array json) og-shape->items)))

(defn list-share-url
  "Construct the shareable URL from a browser `origin`, `pathname`, and
   pre-encoded list `segment`. Pure string concat — kept separate so it
   stays testable on JVM (no `js/window` dependency)."
  [origin pathname segment]
  (str origin pathname "?list=" segment))

;; ============================================================================
;; URL-sync watch — Phase 7.16 / S-url-sync-current-list.
;;
;; Pushes the current items vector into the URL bar as `?list=<encoded>`
;; on every items change so the address bar can be copied directly
;; (not just the Copy URL modal action). Same shape as the existing
;; `install-ui-prefs-persistence!` watch — state-atom watcher that
;; change-detects a pure projection and acts only when it actually
;; changes.
;; ============================================================================

(defn extract-items
  "Pure: denormalize items at `[:list/id 1]` from a Fulcro state-map.
   Returns an empty vector when the path is absent — used by the
   url-sync watch as the projection to change-detect on."
  [state-map]
  (norm/denormalize-list-items state-map [:list/id 1]))

#?(:cljs
   (defn- replace-url-with-items!
     "Default CLJS url-setter. Builds the share-URL for `items` using
      the current `window.location` and calls `history.replaceState`
      so the browser bar reflects the new state without a navigation."
     [items]
     (let [loc js/window.location
           seg (items->base64-url-segment items)
           url (list-share-url (.-origin loc) (.-pathname loc) seg)]
       (.replaceState js/history nil "" url))))

(defn install-url-sync!
  "Watch `fulcro-state-atom`. When the denormalized items at
   `[:list/id 1]` change, call `url-setter` with the new items
   vector.

   - 1-arity (production): defaults `url-setter` to
     `replace-url-with-items!` in CLJS; no-op on JVM.
   - 2-arity (tests): inject a recording setter to assert what would
     have been written.

   Returns the atom for fluent composition."
  ([fulcro-state-atom]
   #?(:cljs (install-url-sync! fulcro-state-atom replace-url-with-items!)
      :clj  fulcro-state-atom))
  ([fulcro-state-atom url-setter]
   (add-watch fulcro-state-atom ::url-sync
     (fn [_k _ref old-state new-state]
       (let [old-items (extract-items old-state)
             new-items (extract-items new-state)]
         (when (not= old-items new-items)
           (url-setter new-items)))))
   fulcro-state-atom))
