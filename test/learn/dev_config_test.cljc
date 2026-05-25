(ns learn.dev-config-test
  "Phase 21.2 — pure parts of the dev-config namespace:
   `dev-flags-defaults`, `merge-flags` (defensive against corrupted
   loaded data), the four-position list-cycler (`next-cycle-position`,
   `cycle-action`), and `position->items` (denormalized items vector
   per cycle position).

   CLJS-only side-effect helpers (`load-flags!`, `save-flags!`,
   `load-cursor!`, `save-cursor!`, `load-snapshot!`, `save-snapshot!`,
   `clear-snapshot!`, `cycle-list!`) are not exercised here — they
   touch `js/localStorage` and `learn.server/SERVER-DB`. Browser-manual
   verification once 21.4 wires the UI."
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.dev-config :as sut]
    [learn.dev-fixtures :as fixtures]
    [learn.server :as server]))

(specification "dev-flags-defaults"
  (assertions
    "has :debug-css/rainbow? as boolean false"
    (:debug-css/rainbow? sut/dev-flags-defaults) => false

    "has :debug-css/depth? as boolean false"
    (:debug-css/depth? sut/dev-flags-defaults) => false))

(specification "merge-flags — defensive merge of a possibly-corrupted loaded map"
  (assertions
    "nil input returns defaults verbatim"
    (sut/merge-flags nil) => sut/dev-flags-defaults

    "empty map returns defaults verbatim"
    (sut/merge-flags {}) => sut/dev-flags-defaults

    "partial map fills missing keys from defaults"
    (sut/merge-flags {:debug-css/rainbow? true})
    => {:debug-css/rainbow? true :debug-css/depth? false}

    "unknown keys are dropped (whitelist)"
    (sut/merge-flags {:debug-css/rainbow? true
                      :totally-bogus      :keep-me-out})
    => {:debug-css/rainbow? true :debug-css/depth? false}

    "non-boolean value on a known key falls back to the default"
    (sut/merge-flags {:debug-css/rainbow? "stringy"})
    => sut/dev-flags-defaults))

(specification "next-cycle-position — pure wrap-around"
  (assertions
    ":actual → :empty"
    (sut/next-cycle-position :actual) => :empty

    ":empty → :5"
    (sut/next-cycle-position :empty) => :5

    ":5 → :26"
    (sut/next-cycle-position :5) => :26

    ":26 wraps back to :actual"
    (sut/next-cycle-position :26) => :actual

    "nil safely defaults to :actual"
    (sut/next-cycle-position nil) => :actual

    "garbage keyword safely defaults to :actual"
    (sut/next-cycle-position :totally-bogus) => :actual))

(specification "cycle-action — pure dispatcher describing the side effect needed"
  (component "leaving :actual"
    (assertions
      "snapshots current SERVER-DB AND applies the next fixture"
      (sut/cycle-action :actual)
      => {:from :actual :to :empty :do :snapshot-and-apply}))

  (component "fixture → fixture (snapshot already exists)"
    (assertions
      ":empty → :5 — just applies"
      (sut/cycle-action :empty) => {:from :empty :to :5 :do :apply}

      ":5 → :26 — just applies"
      (sut/cycle-action :5) => {:from :5 :to :26 :do :apply}))

  (component "returning to :actual"
    (assertions
      ":26 → :actual — restores from snapshot and clears the snapshot key"
      (sut/cycle-action :26) => {:from :26 :to :actual :do :restore-and-clear}))

  (component "safe defaults"
    (assertions
      "nil starting position treats it as :actual"
      (sut/cycle-action nil)
      => {:from :actual :to :empty :do :snapshot-and-apply})))

(specification "position->items — denormalized items vector per cycle position"
  (assertions
    ":5 returns the items-5 fixture"
    (sut/position->items :5) => fixtures/items-5

    ":26 returns the items-26 fixture"
    (sut/position->items :26) => fixtures/items-26

    ":empty returns an empty vector"
    (sut/position->items :empty) => []

    ":actual returns nil (sentinel — caller restores from snapshot)"
    (sut/position->items :actual) => nil))

;; ============================================================================
;; cycle-step — 21.4a pure orchestrator. Given the current cursor,
;; current SERVER-DB, and current snapshot, returns the next world
;; state PLUS a :snapshot-op (:save | :keep | :clear) telling the
;; CLJS wrapper what to do with the snapshot localStorage key.
;; ============================================================================

(def ^:private user-id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
(def ^:private user-db
  (server/write-items server/empty-state server/list-id
    [{:todo/id user-id :todo/text "user task" :todo/status :status/new}]))

(specification "cycle-step — orchestrator transforming world state per cycle action"
  (component "leaving :actual (snapshot-and-apply)"
    (let [result (sut/cycle-step :actual user-db nil)]
      (assertions
        "cursor advances to :empty"
        (:cursor' result) => :empty

        "snapshot captures the input server-db verbatim"
        (:snapshot' result) => user-db

        "snapshot-op is :save (snapshot needs to be persisted)"
        (:snapshot-op result) => :save

        "server-db becomes the empty fixture (items vector is [])"
        (server/items (:server-db' result) server/list-id) => [])))

  (component "fixture → fixture (apply; snapshot already exists)"
    (let [existing-snapshot {:placeholder/key true}
          starting-db       (server/write-items server/empty-state server/list-id [])
          result            (sut/cycle-step :empty starting-db existing-snapshot)]
      (assertions
        "cursor advances to :5"
        (:cursor' result) => :5

        "snapshot is preserved unchanged"
        (:snapshot' result) => existing-snapshot

        "snapshot-op is :keep (don't touch the localStorage snapshot key)"
        (:snapshot-op result) => :keep

        "server-db has items-5 loaded (items vector matches fixtures/items-5)"
        (server/items (:server-db' result) server/list-id) => fixtures/items-5)))

  (component ":5 → :26 — apply continues"
    (let [existing-snapshot {:placeholder/key true}
          starting-db       (server/write-items server/empty-state server/list-id fixtures/items-5)
          result            (sut/cycle-step :5 starting-db existing-snapshot)]
      (assertions
        "cursor → :26"
        (:cursor' result) => :26

        "snapshot still kept"
        (:snapshot-op result) => :keep

        "server-db has items-26"
        (server/items (:server-db' result) server/list-id) => fixtures/items-26)))

  (component ":26 → :actual (restore-and-clear)"
    (let [restore-id     #uuid "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
          snapshot-shape (server/write-items server/empty-state server/list-id
                           [{:todo/id restore-id :todo/text "preserved task" :todo/status :status/ready}])
          starting-db    (server/write-items server/empty-state server/list-id fixtures/items-26)
          result         (sut/cycle-step :26 starting-db snapshot-shape)]
      (assertions
        "cursor wraps back to :actual"
        (:cursor' result) => :actual

        "server-db is the restored snapshot verbatim"
        (:server-db' result) => snapshot-shape

        "snapshot' is nil (the snapshot key gets cleared)"
        (:snapshot' result) => nil

        "snapshot-op is :clear"
        (:snapshot-op result) => :clear)))

  (component "defensive: :26 → :actual with a nil snapshot"
    (let [starting-db (server/write-items server/empty-state server/list-id fixtures/items-26)
          result      (sut/cycle-step :26 starting-db nil)]
      (assertions
        "cursor still wraps to :actual"
        (:cursor' result) => :actual

        "server-db falls back to empty-state (snapshot was lost / never captured)"
        (:server-db' result) => server/empty-state

        "snapshot-op is still :clear (clearing an absent key is a no-op)"
        (:snapshot-op result) => :clear)))

  (component "defensive: nil cursor treated as :actual"
    (let [result (sut/cycle-step nil user-db nil)]
      (assertions
        "cursor advances to :empty"
        (:cursor' result) => :empty

        "snapshot-op is :save (treated as leaving :actual)"
        (:snapshot-op result) => :save

        "snapshot captures the input server-db"
        (:snapshot' result) => user-db))))
