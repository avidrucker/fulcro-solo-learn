(ns learn.util.url-encoding-test
  "Specs for the Phase 7.11 URL-share encoder. The JS port serializes
   the list as `btoa(encodeURIComponent(JSON.stringify(items)))` and
   reads it back with the inverse chain. We pin the empty-list fixture
   from the deployed reference (`?list=JTVCJTVE` ← `[]`) so the
   compositional pieces stay byte-compatible with the JS port even if
   the JSON-shape compatibility for non-empty items is a later phase."
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.util.url-encoding :as sut]))

(specification "base64-encode"
  (assertions
    "empty input"
    (sut/base64-encode "") => ""
    "matches JS `btoa(\"%5B%5D\")` from the deployed reference"
    (sut/base64-encode "%5B%5D") => "JTVCJTVE"
    "matches JS `btoa(\"f\")`"
    (sut/base64-encode "f") => "Zg=="
    "matches JS `btoa(\"foo\")`"
    (sut/base64-encode "foo") => "Zm9v"))

(specification "js-url-encode"
  (assertions
    "literal `[]` is `%5B%5D` (the JS-port empty-list URL fragment)"
    (sut/js-url-encode "[]") => "%5B%5D"
    "ASCII letters and digits pass through unchanged"
    (sut/js-url-encode "abcXYZ123") => "abcXYZ123"
    "space is `%20`, not `+`, matching JS's encodeURIComponent"
    (sut/js-url-encode "a b") => "a%20b"
    "JS encodeURIComponent does NOT escape ! ' ( ) *"
    (sut/js-url-encode "!'()*") => "!'()*"
    "JS encodeURIComponent does NOT escape - _ . ~"
    (sut/js-url-encode "-_.~") => "-_.~"
    "characters like / : ? & = are escaped"
    (sut/js-url-encode "/") => "%2F"
    (sut/js-url-encode ":") => "%3A"
    (sut/js-url-encode "?") => "%3F"
    (sut/js-url-encode "&") => "%26"
    (sut/js-url-encode "=") => "%3D"
    "double-quote (appears in JSON output) is `%22`"
    (sut/js-url-encode "\"") => "%22"))

(specification "items->json"
  (assertions
    "empty items vector produces `[]`"
    (sut/items->json []) => "[]"))

(specification "items->base64-url-segment"
  (assertions
    "empty list round-trips to the JS port's fixture `JTVCJTVE`"
    (sut/items->base64-url-segment []) => "JTVCJTVE"))

(specification "list-share-url"
  (assertions
    "concatenates origin + pathname + ?list=<segment>"
    (sut/list-share-url "https://example.com" "/foo/" "JTVCJTVE")
    => "https://example.com/foo/?list=JTVCJTVE"
    "pathname `/` works"
    (sut/list-share-url "https://x.io" "/" "abc")
    => "https://x.io/?list=abc"
    "empty pathname is tolerated (concats as-is)"
    (sut/list-share-url "https://x.io" "" "abc")
    => "https://x.io?list=abc"))

;; ============================================================================
;; Phase 7.15 — OG-compatible shape transform.
;;
;; Our items have UUIDs and keyword statuses. The JS port uses
;; integer indices and string statuses. Phase 7.11's encoder dumped
;; our shape verbatim; this layer translates between them so URLs
;; we generate decode in the JS port (and vice versa).
;; ============================================================================

(def id-uuid #uuid "11111111-1111-1111-1111-111111111111")
(def id-uuid-2 #uuid "22222222-2222-2222-2222-222222222222")

(specification "status->og-string"
  (assertions
    ":status/new → \"new\""
    (sut/status->og-string :status/new) => "new"
    ":status/ready → \"ready\""
    (sut/status->og-string :status/ready) => "ready"
    ":status/done → \"done\""
    (sut/status->og-string :status/done) => "done"
    ":status/cancelled → \"cancelled\""
    (sut/status->og-string :status/cancelled) => "cancelled"))

(specification "og-string->status"
  (assertions
    "round-trips each status"
    (sut/og-string->status "new")       => :status/new
    (sut/og-string->status "ready")     => :status/ready
    (sut/og-string->status "done")      => :status/done
    (sut/og-string->status "cancelled") => :status/cancelled
    "unknown / nil string returns nil (caller validates)"
    (sut/og-string->status "bogus") => nil
    (sut/og-string->status nil)     => nil))

(specification "items->og-shape"
  (component "empty input"
    (assertions
      "empty vector in, empty vector out"
      (sut/items->og-shape []) => []))

  (component "single ready item"
    (let [items [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]
          out   (sut/items->og-shape items)]
      (assertions
        "one entry"
        (count out) => 1
        ":id is the integer index"
        (-> out first :id) => 0
        ":text passed through verbatim"
        (-> out first :text) => "a"
        ":status is the lowercase string"
        (-> out first :status) => "ready"
        "no :was key for non-cancelled items"
        (contains? (first out) :was) => false)))

  (component "cancelled item preserves :was"
    (let [items [{:todo/id     id-uuid
                  :todo/text   "x"
                  :todo/status :status/cancelled
                  :todo/was    :status/ready}]
          out   (sut/items->og-shape items)]
      (assertions
        ":status is \"cancelled\""
        (-> out first :status) => "cancelled"
        ":was is the prior status string"
        (-> out first :was) => "ready")))

  (component "multi-item preserves order and indexes from 0"
    (let [items [{:todo/id id-uuid   :todo/text "a" :todo/status :status/ready}
                 {:todo/id id-uuid-2 :todo/text "b" :todo/status :status/new}]
          out   (sut/items->og-shape items)]
      (assertions
        "ids are 0, 1 (list-position-derived)"
        (mapv :id out) => [0 1]
        "texts preserved in order"
        (mapv :text out) => ["a" "b"]
        "statuses lowercased"
        (mapv :status out) => ["ready" "new"]))))

(specification "items->json (OG-compat output)"
  (component "empty list still produces \"[]\" (fixture preserved)"
    (assertions
      (sut/items->json []) => "[]"))

  (component "single ready item produces the OG-port JSON shape"
    (let [items [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]]
      (assertions
        "matches the JS port's JSON.stringify output for an [{id:0,text:'a',status:'ready'}] list"
        (sut/items->json items)
        => "[{\"id\":0,\"text\":\"a\",\"status\":\"ready\"}]")))

  (component "cancelled item includes :was"
    (let [items [{:todo/id     id-uuid
                  :todo/text   "x"
                  :todo/status :status/cancelled
                  :todo/was    :status/ready}]]
      (assertions
        (sut/items->json items)
        => "[{\"id\":0,\"text\":\"x\",\"status\":\"cancelled\",\"was\":\"ready\"}]"))))

(specification "items->base64-url-segment (OG fixture)"
  (component "single :ready item \"a\" round-trips to the og's deployed URL fragment"
    ;; The og URL the user shared in the conversation:
    ;; ?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA==
    ;; This is the canonical cross-port compatibility fixture for non-empty lists.
    (let [items [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]
          expected "JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA=="]
      (assertions
        (sut/items->base64-url-segment items) => expected))))

;; ============================================================================
;; Decoder — Move 2b. Inverse of the encoder chain.
;; ============================================================================

(specification "base64-decode"
  (assertions
    "empty input"
    (sut/base64-decode "") => ""
    "round-trips base64-encode for the empty-list fixture"
    (sut/base64-decode "JTVCJTVE") => "%5B%5D"
    "round-trips for \"f\""
    (sut/base64-decode "Zg==") => "f"
    "round-trips for \"foo\""
    (sut/base64-decode "Zm9v") => "foo"
    "malformed input returns nil"
    (sut/base64-decode "!!!not base64!!!") => nil))

(specification "js-url-decode"
  (assertions
    "literal \"%5B%5D\" decodes to \"[]\""
    (sut/js-url-decode "%5B%5D") => "[]"
    "plain ASCII passes through"
    (sut/js-url-decode "abc") => "abc"
    "malformed %-sequence returns nil"
    (sut/js-url-decode "%ZZ") => nil))

(specification "og-shape->items (inverse of items->og-shape)"
  (component "happy path — single ready item"
    (let [og-items [{:id 0 :text "a" :status "ready"}]
          out      (sut/og-shape->items og-items)]
      (assertions
        "one item"
        (count out) => 1
        ":todo/id is a fresh UUID (we don't preserve OG integer ids)"
        (uuid? (-> out first :todo/id)) => true
        ":todo/text preserved"
        (-> out first :todo/text) => "a"
        ":todo/status parsed to keyword"
        (-> out first :todo/status) => :status/ready
        "no :todo/was on non-cancelled items"
        (contains? (first out) :todo/was) => false)))

  (component "cancelled item preserves :was as :todo/was"
    (let [og-items [{:id 0 :text "x" :status "cancelled" :was "ready"}]
          out      (sut/og-shape->items og-items)]
      (assertions
        (-> out first :todo/status) => :status/cancelled
        (-> out first :todo/was)    => :status/ready)))

  (component "invalid input returns nil (defensive)"
    (assertions
      "non-vector input"
      (sut/og-shape->items {:not "a list"}) => nil
      "item missing required keys"
      (sut/og-shape->items [{:no-id-here true}]) => nil
      "item with unknown status string"
      (sut/og-shape->items [{:id 0 :text "x" :status "bogus"}]) => nil)))

(specification "url-segment->items (full decode chain)"
  (component "empty-list fixture round-trips"
    (assertions
      (sut/url-segment->items "JTVCJTVE") => []))

  (component "single-ready-item fixture round-trips"
    (let [seg  "JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA=="
          out  (sut/url-segment->items seg)]
      (assertions
        (count out) => 1
        (-> out first :todo/text) => "a"
        (-> out first :todo/status) => :status/ready)))

  (component "corrupt input returns nil"
    (assertions
      "garbage base64"
      (sut/url-segment->items "!!!not base64!!!") => nil
      "valid base64 but not JSON array of items"
      (sut/url-segment->items (sut/base64-encode "not json")) => nil)))

;; ============================================================================
;; URL-sync watch — Move 2c (S-url-sync-current-list).
;;
;; `install-url-sync!` watches a Fulcro state-atom and invokes
;; `url-setter` with the new items vector whenever the denormalized
;; items at `[:list/id 1]` change. In CLJS the default url-setter
;; calls `history.replaceState`; in tests we inject a recording fn.
;; ============================================================================

(defn- state-with-items
  "Build a minimal normalized state-map containing the given items
   under [:list/id 1]."
  [items]
  (let [idents (mapv (fn [t] [:todo/id (:todo/id t)]) items)
        ents   (into {} (map (juxt :todo/id identity)) items)]
    {:list/id  {1 {:list/id 1 :list/todos idents :ui/theme :theme/light}}
     :todo/id  ents}))

(specification "extract-items"
  (assertions
    "empty state — empty items"
    (sut/extract-items {}) => []
    "denormalizes idents at [:list/id 1 :list/todos]"
    (sut/extract-items (state-with-items
                         [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]))
    => [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]))

(specification "install-url-sync!"
  (component "fires url-setter with new items when items change"
    (let [a       (atom (state-with-items []))
          calls   (atom [])
          setter  (fn [items] (swap! calls conj items))]
      (sut/install-url-sync! a setter)
      (reset! a (state-with-items
                  [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]))
      (assertions
        "setter called exactly once after the items change"
        (count @calls) => 1
        "setter received the new items vector"
        (-> @calls first first :todo/text) => "a")))

  (component "does NOT fire when items are unchanged"
    (let [a       (atom (state-with-items
                          [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]))
          calls   (atom [])
          setter  (fn [items] (swap! calls conj items))]
      (sut/install-url-sync! a setter)
      ;; Change a non-items path — :ui/theme. Items vector is identical.
      (swap! a assoc-in [:list/id 1 :ui/theme] :theme/dark)
      (assertions
        "setter NOT called (theme change is not an items change)"
        (count @calls) => 0)))

  (component "fires on every items-change, not just the first"
    (let [a       (atom (state-with-items []))
          calls   (atom [])
          setter  (fn [items] (swap! calls conj items))]
      (sut/install-url-sync! a setter)
      (reset! a (state-with-items
                  [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}]))
      (reset! a (state-with-items
                  [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}
                   {:todo/id id-uuid-2 :todo/text "b" :todo/status :status/new}]))
      (reset! a (state-with-items []))
      (assertions
        "three changes → three setter calls"
        (count @calls) => 3
        "last call's items vector is empty"
        (last @calls) => []))))

;; ============================================================================
;; URL load on init — Move 2d (S-url-load-on-init).
;;
;; Pure helper: parse `?list=<segment>` out of a query string, decode
;; through the chain, return our items vector. Wrapped in CLJS-only
;; `items-from-current-url` that reads `window.location.search`.
;; ============================================================================

(specification "parse-list-param"
  (assertions
    "happy path — leading ?, single param"
    (sut/parse-list-param "?list=JTVCJTVE") => "JTVCJTVE"
    "tolerates `=` padding in the base64 segment"
    (sut/parse-list-param "?list=Zg==") => "Zg=="
    "no leading `?` (some callers pre-strip it)"
    (sut/parse-list-param "list=JTVCJTVE") => "JTVCJTVE"
    "absent ?list — returns nil"
    (sut/parse-list-param "?theme=dark") => nil
    "empty string — returns nil"
    (sut/parse-list-param "") => nil
    "nil — returns nil"
    (sut/parse-list-param nil) => nil
    "?list with multiple params (anywhere in the string)"
    (sut/parse-list-param "?theme=dark&list=JTVCJTVE&zoom=2") => "JTVCJTVE"
    "list= with empty value — returns empty string (caller filters)"
    (sut/parse-list-param "?list=") => ""))

(specification "items-from-query-string"
  (component "happy path round-trip"
    (assertions
      "empty-list fixture"
      (sut/items-from-query-string "?list=JTVCJTVE") => []
      "single-:ready-item fixture from og deployed URL"
      (count (sut/items-from-query-string
               "?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA=="))
      => 1))

  (component "no list param — returns nil (NOT empty vector)"
    ;; nil tells the caller 'no URL list at all', distinct from
    ;; '[] = URL says empty list'. Move 2e (conflict modal) cares.
    (assertions
      (sut/items-from-query-string "")          => nil
      (sut/items-from-query-string "?theme=dark") => nil
      (sut/items-from-query-string nil)         => nil))

  (component "corrupt list param — returns nil"
    (assertions
      "garbage base64"
      (sut/items-from-query-string "?list=!!!notbase64!!!") => nil
      "valid base64 but not JSON array"
      (sut/items-from-query-string (str "?list=" (sut/base64-encode "not json"))) => nil)))

;; ============================================================================
;; Conflict-decision logic — Move 2e (S-conflict-modal).
;;
;; Pure decision: given the localStorage items (or nil) and URL items
;; (or nil), what should `init` do?
;;
;;   - both non-nil AND differ                → :conflict (modal opens)
;;   - both non-nil AND equal                 → :no-op (just pick one)
;;   - URL only                               → :url    (URL wins)
;;   - localStorage only                      → :local  (localStorage wins)
;;   - neither                                → :seed   (fall back to seed)
;; ============================================================================

(def ^:private items-a [{:todo/id id-uuid :todo/text "a" :todo/status :status/ready}])
(def ^:private items-b [{:todo/id id-uuid-2 :todo/text "b" :todo/status :status/new}])

(specification "decide-initial-list"
  (assertions
    "neither URL nor localStorage — :seed (use the seed)"
    (sut/decide-initial-list nil nil) => {:source :seed}

    "URL only — :url"
    (sut/decide-initial-list nil items-a) => {:source :url :items items-a}

    "localStorage only — :local"
    (sut/decide-initial-list items-a nil) => {:source :local :items items-a}

    "both equal — :no-op (pick either; we pick :url for symmetry with the URL-only case)"
    (sut/decide-initial-list items-a items-a) => {:source :url :items items-a}

    "both differ — :conflict, carries both for the modal to render"
    (sut/decide-initial-list items-a items-b)
    => {:source :conflict :local-items items-a :url-items items-b}

    "localStorage exists as `[]` (user emptied their list), URL has items — STILL a conflict"
    (sut/decide-initial-list [] items-a)
    => {:source :conflict :local-items [] :url-items items-a}

    "URL exists as `[]` (someone shared an empty list), localStorage has items — STILL a conflict"
    (sut/decide-initial-list items-a [])
    => {:source :conflict :local-items items-a :url-items []}))
