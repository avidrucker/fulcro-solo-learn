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
