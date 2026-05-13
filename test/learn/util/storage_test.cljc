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

;; ============================================================================
;; Phase 7.10 — UI preferences slice (B-1 fix).
;;
;; `:ui/theme` lives in Fulcro app state at `[:list/id 1 :ui/theme]`. The
;; main SERVER-DB persistence doesn't reach it, so theme resets to
;; `:theme/light` on every page reload. The pure helpers below extract
;; and re-apply a whitelisted slice (currently only `:ui/theme`) so the
;; CLJS-side `install-ui-prefs-persistence!` can dehydrate it to a
;; second localStorage key and hydrate it on init.
;; ============================================================================

(def sample-fulcro-state
  "A representative Fulcro app state map after the user has toggled to
   dark mode. Mirrors what TodoList's initial-state + a toggle-theme
   mutation would produce."
  {:list/id {1 {:list/id          1
                :list/todos       [[:todo/id #uuid "11111111-1111-1111-1111-111111111111"]]
                :ui/new-todo-text ""
                :ui/open-modal    :none
                :ui/theme         :theme/dark
                :ui/err-msg       nil}}})

(specification "ui-prefs-key constant"
  (assertions
    "exposed as a top-level def"
    (string? sut/ui-prefs-key)             => true
    "namespaced and distinct from the server-db key"
    (str/includes? sut/ui-prefs-key ".")   => true
    (not= sut/ui-prefs-key sut/storage-key) => true))

(specification "extract-ui-prefs"
  (component "happy path — slice contains the whitelisted keys"
    (let [slice (sut/extract-ui-prefs sample-fulcro-state)]
      (assertions
        "returns a map"
        (map? slice) => true
        "contains :ui/theme"
        (:ui/theme slice) => :theme/dark
        "does not contain non-whitelisted ui keys (open-modal, err-msg, new-todo-text)"
        (contains? slice :ui/open-modal)    => false
        (contains? slice :ui/err-msg)       => false
        (contains? slice :ui/new-todo-text) => false
        "does not contain list-data keys"
        (contains? slice :list/todos) => false)))

  (component "absent ui keys"
    (let [no-theme-state (update-in sample-fulcro-state [:list/id 1] dissoc :ui/theme)
          slice          (sut/extract-ui-prefs no-theme-state)]
      (assertions
        "returns an empty map when no whitelisted keys are present"
        slice => {})))

  (component "absent list entity"
    (assertions
      "returns an empty map when [:list/id 1] is missing entirely"
      (sut/extract-ui-prefs {}) => {})))

(specification "apply-ui-prefs"
  (component "happy path — merges slice into [:list/id 1]"
    (let [fresh-state (assoc-in sample-fulcro-state [:list/id 1 :ui/theme] :theme/light)
          slice       {:ui/theme :theme/dark}
          after       (sut/apply-ui-prefs fresh-state slice)]
      (assertions
        ":ui/theme overwritten by slice value"
        (get-in after [:list/id 1 :ui/theme]) => :theme/dark
        "other keys at [:list/id 1] preserved"
        (get-in after [:list/id 1 :ui/new-todo-text]) => ""
        (get-in after [:list/id 1 :list/todos])
        => [[:todo/id #uuid "11111111-1111-1111-1111-111111111111"]])))

  (component "non-whitelisted keys in the slice are dropped (defence in depth)"
    (let [slice {:ui/theme       :theme/dark
                 :ui/open-modal  :about            ; should NOT be merged
                 :list/todos     []}               ; should NOT be merged
          after (sut/apply-ui-prefs sample-fulcro-state slice)]
      (assertions
        ":ui/theme applied"
        (get-in after [:list/id 1 :ui/theme]) => :theme/dark
        ":ui/open-modal NOT overwritten by the rogue slice"
        (get-in after [:list/id 1 :ui/open-modal]) => :none
        ":list/todos NOT overwritten"
        (count (get-in after [:list/id 1 :list/todos])) => 1)))

  (component "nil slice — state returned unchanged"
    (assertions
      "passing nil for the slice is a no-op"
      (sut/apply-ui-prefs sample-fulcro-state nil) => sample-fulcro-state)))

(specification "->edn / <-edn round-trip for ui-prefs slice"
  (component "small slices round-trip"
    (let [slice {:ui/theme :theme/dark}]
      (assertions
        "slice survives serialize → deserialize"
        (sut/<-edn (sut/->edn slice)) => slice))))
