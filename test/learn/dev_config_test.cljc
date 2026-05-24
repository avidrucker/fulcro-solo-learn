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
    [learn.dev-fixtures :as fixtures]))

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
