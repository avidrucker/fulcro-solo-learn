(ns learn.util.storage-test
  "Specs for the pure CLJC half of `learn.util.storage` — serialize /
   deserialize. The CLJS-only `save!`/`load!`/`install-persistence!`
   functions touch `js/localStorage` and aren't exercised here; they're
   thin wrappers over the pure fns plus a try-catch."
  (:require
    [clojure.string :as str]
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.util.storage :as sut]))

;; A representative SERVER-DB snapshot. Mirrors `learn.server/initial-state`'s
;; shape so the spec catches breakage if the storage layer trips on the
;; production data shape.
(def sample-server-db
  {:list/id {1 {:list/id    1
                :list/todos [#uuid "11111111-1111-1111-1111-111111111111"
                             #uuid "22222222-2222-2222-2222-222222222222"]}}
   :todo/id {#uuid "11111111-1111-1111-1111-111111111111"
             {:todo/id     #uuid "11111111-1111-1111-1111-111111111111"
              :todo/text   "Read book"
              :todo/status :status/ready}
             #uuid "22222222-2222-2222-2222-222222222222"
             {:todo/id     #uuid "22222222-2222-2222-2222-222222222222"
              :todo/text   "Walk dog"
              :todo/status :status/cancelled
              :todo/was    :status/new}}})

(specification "->edn / <-edn round-trip"
  (component "round-trips a server-db with keywords, uuids, namespaced keys"
    (let [out  (sut/->edn sample-server-db)
          back (sut/<-edn out)]
      (assertions
        "serialized form is a string"
        (string? out) => true
        "round-trip preserves the value exactly"
        back => sample-server-db)))

  (component "round-trips an empty server-db"
    (let [empty-db {:list/id {1 {:list/id 1 :list/todos []}}
                    :todo/id {}}]
      (assertions
        "empty list round-trips unchanged"
        (sut/<-edn (sut/->edn empty-db)) => empty-db))))

(specification "<-edn corruption / missing-input handling"
  (component "garbage EDN string returns nil rather than throwing"
    (assertions
      "unbalanced parens (reader throws) — nil"
      (sut/<-edn "{:list/id {1 {:list/id 1") => nil
      "non-map first form (reader returns a symbol) — nil"
      (sut/<-edn "not edn at all }}}")     => nil
      "non-map first form (a vector) — nil"
      (sut/<-edn "[1 2 3]")                => nil
      "non-map first form (a number) — nil"
      (sut/<-edn "42")                     => nil))

  (component "nil and empty-string inputs return nil"
    (assertions
      "nil in, nil out"
      (sut/<-edn nil) => nil
      "empty string in, nil out"
      (sut/<-edn "") => nil
      "whitespace-only in, nil out"
      (sut/<-edn "   \n") => nil))

  (component "trusted but reader-unsafe edn (function literals, etc.) returns nil"
    ;; clojure.edn/read-string rejects anything that requires the
    ;; reader (e.g. `#=` eval forms) — the storage layer should
    ;; surface that rejection as a graceful nil, not a crash.
    (assertions
      "reader-eval form rejected to nil"
      (sut/<-edn "#=(println 'pwned)") => nil)))

(specification "storage-key constant"
  (assertions
    "exposed as a top-level def"
    (string? sut/storage-key)              => true
    "key is namespaced so it can't collide with arbitrary site keys"
    (str/includes? sut/storage-key ".")    => true))
