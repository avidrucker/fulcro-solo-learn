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
    [learn.i18n.core :as i18n]
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
   stays testable on JVM (no `js/window` dependency).

   Phase 17 — optional 4-arity appends `&lang=<code>` when the caller
   opts in (Save modal's 'Include language in URL' checkbox). Recipients
   with no saved locale pick up the URL's lang via Phase 14's
   precedence rule. Nil `locale` is treated as 'don't append'."
  ([origin pathname segment]
   (list-share-url origin pathname segment nil))
  ([origin pathname segment locale]
   (cond-> (str origin pathname "?list=" segment)
     locale (str "&lang=" (name locale)))))

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
   (defn replace-url-with-items!
     "Default CLJS url-setter. Builds the share-URL for `items` using
      the current `window.location` and calls `history.replaceState`
      so the browser bar reflects the new state without a navigation.

      Public because some flows (e.g. the conflict modal's Keep Local
      path) need to force a URL refresh even when the state items
      vector hasn't changed — `install-url-sync!`'s watch only fires
      on items-vector diff, so callers that change *which* list
      we're committed to (without changing items themselves) call
      this directly."
     [items]
     (let [loc js/window.location
           seg (items->base64-url-segment items)
           url (list-share-url (.-origin loc) (.-pathname loc) seg)]
       (.replaceState js/history nil "" url))))

(defn- items-content-shape
  "Strip UUIDs from items so two vectors can be compared on user-
   visible content alone. The decoder (`og-shape->items`) assigns
   fresh UUIDs on every call, so the localStorage and URL items
   never `=` even when they were derived from the same source —
   without this projection we'd flag a phantom conflict on every
   refresh / back-button (B-4)."
  [items]
  (mapv #(select-keys % [:todo/text :todo/status :todo/was]) items))

(defn decide-initial-list
  "Pure: classify what `init` should do given the localStorage items
   and the URL items. Either side may be `nil` (absent) or any vector
   (including `[]`).

   Returns a tagged map with `:source` ∈ #{:seed :url :local :conflict}.
   For :url and :local, also returns `:items`. For :conflict, returns
   `:local-items` and `:url-items` so the modal can render both.

   When both are present, we compare on **content shape** (text,
   status, :was) — NOT on UUID — because the URL decoder assigns
   fresh UUIDs every load. If the content is the same we collapse to
   `:url` (the URL is the more recent thing the user explicitly
   visited). B-4 fix.

   B-11 fix: an empty side isn't a real conflict. When one side is
   `[]` (empty list, present but nothing in it) and the other is
   non-empty, the non-empty side wins automatically — no conflict
   modal. Refreshing a page where the user emptied their local list
   (or just shared an empty link) shouldn't prompt them to choose
   between content and nothing."
  [local-items url-items]
  (cond
    ;; B-11: one side empty, the other non-empty → non-empty wins
    (and (some? local-items) (empty? local-items) (seq url-items))
    {:source :url :items url-items}

    (and (some? url-items) (empty? url-items) (seq local-items))
    {:source :local :items local-items}

    ;; Content-shape conflict — both non-empty and disagreeing
    (and (some? local-items) (some? url-items)
      (not= (items-content-shape local-items)
            (items-content-shape url-items)))
    {:source :conflict :local-items local-items :url-items url-items}

    (some? url-items)   {:source :url   :items url-items}
    (some? local-items) {:source :local :items local-items}
    :else               {:source :seed}))

(defn parse-list-param
  "Pure: extract the value of `?list=<value>` from a URL query string.
   Returns the raw segment (still base64-encoded) or nil if absent.

   Accepts strings with or without a leading `?`. Multi-param query
   strings work — searches for `list=` anywhere in the string, bounded
   by `?`, `&`, or start-of-string."
  [query-string]
  (when (string? query-string)
    (let [;; Strip leading `?` if present.
          s (if (.startsWith ^String query-string "?")
              (subs query-string 1)
              query-string)]
      (some (fn [pair]
              (let [[k v] (str/split pair #"=" 2)]
                (when (= "list" k) (or v ""))))
        (str/split s #"&")))))

(defn items-from-query-string
  "Pure: combine `parse-list-param` with `url-segment->items`. Returns
   nil if there's no `?list=` param OR if it fails to decode."
  [query-string]
  (when-let [seg (parse-list-param query-string)]
    (when-not (str/blank? seg)
      (url-segment->items seg))))

#?(:cljs
   (defn items-from-current-url
     "Read `window.location.search` and decode `?list=` into items.
      Returns nil if no list param or if decode fails — caller falls
      back to seed / localStorage."
     []
     (items-from-query-string (.-search js/window.location))))

;; ============================================================================
;; Phase 14 — `?lang=<code>` query-param locale (S-i18n-url-locale).
;;
;; Standalone helpers (don't share infrastructure with the `?list=`
;; pipeline). Could be extracted to a separate ns if more
;; query-param features land, but at this scale colocating with the
;; other URL-parsing fns keeps the touchpoints to one file.
;; ============================================================================

(defn- parse-lang-param
  "Pure: extract the value of `?lang=<value>` from a URL query string.
   Returns the raw value or nil if absent. Same shape contract as
   `parse-list-param`. Lowercased so `?lang=ES` matches `:es`."
  [query-string]
  (when (string? query-string)
    (let [s (if (.startsWith ^String query-string "?")
              (subs query-string 1)
              query-string)]
      (some (fn [pair]
              (let [[k v] (str/split pair #"=" 2)]
                (when (= "lang" k)
                  (when (and v (seq v))
                    (str/lower-case v)))))
        (str/split s #"&")))))

(defn locale-from-url-search
  "Pure: parse a URL query string and return a supported locale
   keyword (`:en` / `:es` / `:ja`), or nil if no valid `?lang=<code>`
   is present.

   Validation: the value must be present, non-empty, and the
   lowercased value as a keyword must be in
   `learn.i18n.core/supported-locales`. Unsupported codes (`fr`,
   `42`, etc.) return nil so the caller falls through to the
   localStorage / default path — matches the Phase 14 precedence
   rule (localStorage > URL > :en)."
  [query-string]
  (when-let [raw (parse-lang-param query-string)]
    (let [kw (keyword raw)]
      (when (contains? i18n/supported-locales kw)
        kw))))

#?(:cljs
   (defn locale-from-current-url
     "Read `window.location.search` and parse `?lang=` into a
      supported locale keyword. Returns nil if absent or
      unsupported — caller falls back to the saved preference or
      `:en` default. CLJS-only counterpart to the JVM-testable
      `locale-from-url-search`."
     []
     (locale-from-url-search (.-search js/window.location))))

;; ============================================================================
;; Phase 18 — Locale-conflict modal helpers (S-language-conflict-modal).
;;
;; When the URL `?lang=` differs from the user's saved locale, the
;; locale-conflict modal asks them which to use. After they pick, the
;; URL needs to be updated to match their choice (so a future reload
;; doesn't re-trigger the modal). `replace-lang-param` builds the new
;; query string; `locale-decision` dispatches the three init-time
;; cases (silent apply / conflict / no-op) for the lifecycle layer.
;; ============================================================================

(defn replace-lang-param
  "Pure: given a URL query string and a target locale, return the
   query string with `lang=<code>` set to that locale (overwriting any
   existing `lang=` pair). `nil` locale REMOVES the lang pair instead.
   Other params are preserved; the new lang pair is appended at the
   end so the function is deterministic. The leading `?` is preserved
   on output when the result is non-empty."
  [query-string locale]
  (let [s     (cond
                (nil? query-string) ""
                (and (string? query-string)
                     (.startsWith ^String query-string "?")) (subs query-string 1)
                :else query-string)
        pairs (filter seq (str/split s #"&"))
        without-lang (remove #(.startsWith ^String % "lang=") pairs)
        with-new     (if locale
                       (concat without-lang [(str "lang=" (name locale))])
                       without-lang)
        joined       (str/join "&" with-new)]
    (if (seq joined) (str "?" joined) "")))

(defn locale-decision
  "Pure: given a saved-locale (from localStorage) and a url-locale
   (from `?lang=`), return one of:
     {:action :apply :locale <loc>}              — first-time visitor with
                                                     URL hint; lifecycle
                                                     applies silently.
     {:action :conflict :saved <s> :url <u>}     — returning user, two
                                                     supported locales
                                                     disagree; lifecycle
                                                     opens the conflict modal.
     {:action :no-op}                            — neither, or saved
                                                     matches URL.

   See `learn.client.lifecycle/install-url-locale-fallback!` for the
   call site. JVM-testable; keeps the lifecycle thin."
  [saved-locale url-locale]
  (cond
    (and (nil? saved-locale) url-locale)
    {:action :apply :locale url-locale}

    (and saved-locale url-locale (not= saved-locale url-locale))
    {:action :conflict :saved saved-locale :url url-locale}

    :else
    {:action :no-op}))

#?(:cljs
   (defn update-current-url-lang!
     "CLJS-only side effect: rewrite the address bar's `?lang=` to
      `locale` (or drop the lang pair if `locale` is nil), preserving
      other query params. Used after the locale-conflict modal closes
      so subsequent reloads pick up the user's choice without
      re-triggering the modal."
     [locale]
     (let [loc       js/window.location
           new-search (replace-lang-param (.-search loc) locale)
           new-url    (str (.-pathname loc) new-search (.-hash loc))]
       (.replaceState js/history nil "" new-url))))

;; ============================================================================
;; Phase 15 — URL-length safeguard (S-max-url-length).
;;
;; The JS port's `MAX_URL_LENGTH = 8000` was a defensive cap on the
;; encoded `?list=` segment. Our divergence: when the encoded segment
;; would exceed this, we FREEZE the URL at its last-fitting value and
;; surface an error message via a caller-injected callback. The list
;; keeps growing in app state + localStorage; only URL-sharing is
;; affected.
;;
;; This differs from the OG, which lets the URL grow unbounded and
;; produces unsharable links. See `docs/changes.md` for the rationale.
;; ============================================================================

(def MAX_URL_LENGTH
  "Maximum length of the encoded `?list=<segment>` value (the
   base64-url-segment part, NOT including the `?list=` prefix). 8000
   matches the JS port's constant — practical browsers/servers accept
   ~2-8KB URLs reliably; 8000 is a safe upper bound across most
   modern stacks."
  8000)

(defn items-encode-fits?
  "Pure: would the URL-encoded representation of `items` fit within
   `MAX_URL_LENGTH`? Returns true if the encoded segment is short
   enough to safely write to the URL, false otherwise. Used by the
   URL-sync watch to decide whether to skip `history.replaceState`."
  [items]
  (<= (count (items->base64-url-segment items)) MAX_URL_LENGTH))

(defn install-url-sync!
  "Watch `fulcro-state-atom`. When the denormalized items at
   `[:list/id 1]` change, call `url-setter` with the new items
   vector — unless the encoded segment would exceed
   `MAX_URL_LENGTH`, in which case `url-setter` is skipped (URL
   freezes) and `on-over-limit` is invoked so the caller can
   surface an error.

   - 1-arity (production): defaults `url-setter` to
     `replace-url-with-items!` in CLJS; `on-over-limit` swaps the
     URL-too-long error string into `:ui/err-msg`. JVM: no-op
     install (returns the atom unchanged).
   - 2-arity (legacy tests): inject `url-setter` only;
     `on-over-limit` defaults to no-op so older tests that
     don't exercise the over-limit path continue to work.
   - 3-arity (tests): inject both callbacks for full
     observability.

   Returns the atom for fluent composition."
  ([fulcro-state-atom]
   #?(:cljs
      (install-url-sync! fulcro-state-atom
        replace-url-with-items!
        (fn [state-atom]
          (let [locale (get-in @state-atom [:list/id 1 :ui/locale] :en)]
            (swap! state-atom assoc-in [:list/id 1 :ui/err-msg]
              (i18n/tr locale :err/url-too-long)))))
      :clj fulcro-state-atom))
  ([fulcro-state-atom url-setter]
   (install-url-sync! fulcro-state-atom url-setter (constantly nil)))
  ([fulcro-state-atom url-setter on-over-limit]
   (add-watch fulcro-state-atom ::url-sync
     (fn [_k _ref old-state new-state]
       (let [old-items (extract-items old-state)
             new-items (extract-items new-state)]
         (when (not= old-items new-items)
           (if (items-encode-fits? new-items)
             (url-setter new-items)
             (on-over-limit fulcro-state-atom))))))
   fulcro-state-atom))
