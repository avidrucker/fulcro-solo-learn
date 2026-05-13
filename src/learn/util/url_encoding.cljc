(ns learn.util.url-encoding
  "Encode the AutoFocus list into a URL-safe segment for the
   `?list=<segment>` share link (Phase 7.11).

   The JS port's recipe is `btoa(encodeURIComponent(JSON.stringify(items)))`
   and the empty-list fixture `[] → \"JTVCJTVE\"` ships in
   `docs/snapshots/reference/README.md`. We mirror the three steps so a
   URL produced by either port shares the same shape at the byte level.

   Split:
     `items->json`     — produces a JSON string. CLJS uses `js/JSON.stringify`;
                         JVM uses a small hand-rolled encoder that covers the
                         shapes we round-trip in tests (empty vector + the
                         primitives that show up in todo records).
     `js-url-encode`   — matches JS `encodeURIComponent` exactly: lowercase
                         alpha+digits and the unreserved set `-_.~!'()*` pass
                         through; space becomes `%20`, not `+`; everything
                         else is %-encoded by UTF-8 byte.
     `base64-encode`   — matches JS `btoa`: standard base64 alphabet with
                         `=` padding, no line breaks.
     `items->base64-url-segment` — composition of the three.
     `list-share-url`  — wraps the segment into the final shareable URL."
  (:require
    [clojure.string :as str])
  #?(:clj (:import (java.util Base64))))

;; ============================================================================
;; Step 1 — JSON encoding
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
  "Serialize `items` to a JSON string. The empty-vector case must produce
   the literal `\"[]\"` so the chain matches the JS port's deployed
   `?list=JTVCJTVE` fixture."
  [items]
  #?(:cljs (js/JSON.stringify (clj->js items))
     :clj  (->json* items)))

;; ============================================================================
;; Step 2 — URL component encoding (mirrors JS `encodeURIComponent`)
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

;; ============================================================================
;; Step 3 — base64 (mirrors JS `btoa`)
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

;; ============================================================================
;; Composition + URL construction
;; ============================================================================

(defn items->base64-url-segment
  "Apply the three-step JS-port encoding to `items` and return the
   string suitable for appending after `?list=`."
  [items]
  (-> items items->json js-url-encode base64-encode))

(defn list-share-url
  "Construct the shareable URL from a browser `origin`, `pathname`, and
   pre-encoded list `segment`. Pure string concat — kept separate so it
   stays testable on JVM (no `js/window` dependency)."
  [origin pathname segment]
  (str origin pathname "?list=" segment))
