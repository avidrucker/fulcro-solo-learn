(ns learn.util.tasks-io-test
  "Phase 13 — specs for `learn.util.tasks-io/parse-tasks-json`.

   The OG ReactJS reference is `pwa-autofocus-app/src/utils/tasksIO.js`
   (`importTasksFromJSON`). Behaviour we mirror:
     - Valid JSON array of OG-shape items → `:ok? true` with items
       translated to our schema (UUIDs, namespaced keys, keyword
       statuses).
     - JSON.parse failure (file isn't JSON at all) → `:ok? false`
       with `:error/type :error/non-json` so the UI can surface the
       'Please select a valid JSON file' message.
     - Parseable but wrong structure (not an array, items missing
       required fields, items with bad status strings) → `:ok? false`
       with `:error/type :error/bad-json` so the UI surfaces
       'Failed to import tasks. Ensure the JSON file has the correct
       format.'

   The encoder side already lives in
   `learn.util.url-encoding/items->json` (covered by that ns's tests
   in `url-encoding-test`). This file covers the inverse only."
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.util.tasks-io :as sut]))

(specification "parse-tasks-json — happy path"
  (component "valid OG-shape array round-trips into our schema"
    (let [json "[{\"id\":0,\"text\":\"alpha\",\"status\":\"ready\"},{\"id\":1,\"text\":\"beta\",\"status\":\"new\"}]"
          result (sut/parse-tasks-json json)]
      (assertions
        "result reports success"
        (:ok? result) => true
        "two items present"
        (count (:items result)) => 2
        "first item text preserved"
        (-> result :items first :todo/text) => "alpha"
        "first item status keyword preserved"
        (-> result :items first :todo/status) => :status/ready
        "second item status keyword preserved"
        (-> result :items second :todo/status) => :status/new
        "each item gets a fresh UUID (OG int ids don't map to our schema)"
        (uuid? (-> result :items first :todo/id)) => true
        (uuid? (-> result :items second :todo/id)) => true
        "UUIDs are distinct"
        (not= (-> result :items first :todo/id)
              (-> result :items second :todo/id)) => true)))

  (component "cancelled item with `was` field"
    (let [json "[{\"id\":0,\"text\":\"x\",\"status\":\"cancelled\",\"was\":\"ready\"}]"
          result (sut/parse-tasks-json json)
          item   (-> result :items first)]
      (assertions
        ":ok?"
        (:ok? result) => true
        ":todo/status is :status/cancelled"
        (:todo/status item) => :status/cancelled
        ":todo/was carries the prior status"
        (:todo/was item) => :status/ready)))

  (component "cancelled item without `was` field defaults `:todo/was` to :status/new"
    (let [json "[{\"id\":0,\"text\":\"x\",\"status\":\"cancelled\"}]"
          result (sut/parse-tasks-json json)
          item   (-> result :items first)]
      (assertions
        "still parses successfully (legacy fixture compat)"
        (:ok? result) => true
        ":todo/was defaults to :status/new so the schema invariant holds"
        (:todo/was item) => :status/new)))

  (component "empty array is a successful empty list"
    (let [result (sut/parse-tasks-json "[]")]
      (assertions
        ":ok?"
        (:ok? result) => true
        "no items"
        (:items result) => []))))

(specification "parse-tasks-json — round-trips a Fulcro-port-exported JSON file"
  ;; Regression guard for the bug where re-importing a list we just
  ;; exported produced :error/bad-json. The exact JSON below is what
  ;; the export path writes for a 10-item list with mixed statuses
  ;; (matching what the user-reported case produced). Re-importing
  ;; must succeed, must keep every text and status, and must
  ;; regenerate UUIDs.
  (let [exported "[{\"id\":0,\"text\":\"a\",\"status\":\"done\"},{\"id\":1,\"text\":\"b\",\"status\":\"cancelled\",\"was\":\"ready\"},{\"id\":2,\"text\":\"c\",\"status\":\"cancelled\",\"was\":\"new\"},{\"id\":3,\"text\":\"d\",\"status\":\"ready\"},{\"id\":4,\"text\":\"e\",\"status\":\"new\"},{\"id\":5,\"text\":\"f\",\"status\":\"new\"},{\"id\":6,\"text\":\"g\",\"status\":\"new\"},{\"id\":7,\"text\":\"h\",\"status\":\"new\"},{\"id\":8,\"text\":\"i\",\"status\":\"new\"},{\"id\":9,\"text\":\"j\",\"status\":\"new\"}]"
        result (sut/parse-tasks-json exported)]
    (assertions
      "parse succeeds"
      (:ok? result) => true
      "all 10 items recovered"
      (count (:items result)) => 10
      "texts preserved in original order"
      (mapv :todo/text (:items result))
      => ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j"]
      "statuses preserved exactly"
      (mapv :todo/status (:items result))
      => [:status/done :status/cancelled :status/cancelled :status/ready
          :status/new  :status/new       :status/new       :status/new
          :status/new  :status/new]
      ":todo/was preserved for both cancelled items (ready and new)"
      (->> (:items result) (filter #(= :status/cancelled (:todo/status %))) (mapv :todo/was))
      => [:status/ready :status/new]
      "every imported item has a fresh UUID (not the OG int ids)"
      (every? uuid? (mapv :todo/id (:items result))) => true)))

(specification "parse-tasks-json — failure paths"
  (component ":error/non-json when JSON.parse fails"
    (assertions
      "not-JSON-at-all (garbage)"
      (sut/parse-tasks-json "not json at all") => {:ok? false :error/type :error/non-json}
      "truncated array"
      (sut/parse-tasks-json "[{\"id\":0,") => {:ok? false :error/type :error/non-json}
      "blank input"
      (sut/parse-tasks-json "") => {:ok? false :error/type :error/non-json}
      "nil input"
      (sut/parse-tasks-json nil) => {:ok? false :error/type :error/non-json}))

  (component ":error/bad-json when parsed but structure invalid"
    (assertions
      "top-level object instead of array"
      (sut/parse-tasks-json "{\"id\":0}")
      => {:ok? false :error/type :error/bad-json}

      "top-level scalar (number)"
      (sut/parse-tasks-json "42")
      => {:ok? false :error/type :error/bad-json}

      "top-level scalar (string)"
      (sut/parse-tasks-json "\"hello\"")
      => {:ok? false :error/type :error/bad-json}

      "array of non-objects"
      (sut/parse-tasks-json "[1,2,3]")
      => {:ok? false :error/type :error/bad-json}

      "item missing :text"
      (sut/parse-tasks-json "[{\"id\":0,\"status\":\"new\"}]")
      => {:ok? false :error/type :error/bad-json}

      "item missing :status"
      (sut/parse-tasks-json "[{\"id\":0,\"text\":\"a\"}]")
      => {:ok? false :error/type :error/bad-json}

      "item with unknown status string"
      (sut/parse-tasks-json "[{\"id\":0,\"text\":\"a\",\"status\":\"in-progress\"}]")
      => {:ok? false :error/type :error/bad-json})))
