(ns learn.rad.attributes-test
  "Phase 9.1 — sanity specs for the RAD attribute registry. RAD's
   `defattr` builds an attribute map at compile time. These tests
   pin the wire shape and the metadata we care about; they're not
   exhaustive (RAD has its own validation) but they catch regressions
   if an attribute is renamed, retyped, or dropped from
   `all-attributes`."
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.attributes-options :as ao]
    [learn.rad.attributes :as sut]))

(defn- attr-by-key
  "Find an attribute in `sut/all-attributes` by its qualified key."
  [k]
  (some (fn [a] (when (= k (ao/qualified-key a)) a))
    sut/all-attributes))

(specification "RAD attribute registry"
  (component "every attribute is in `all-attributes`"
    (assertions
      "four attributes total (id, text, status, was)"
      (count sut/all-attributes) => 4
      ":todo/id is registered"
      (some? (attr-by-key :todo/id)) => true
      ":todo/text is registered"
      (some? (attr-by-key :todo/text)) => true
      ":todo/status is registered"
      (some? (attr-by-key :todo/status)) => true
      ":todo/was is registered"
      (some? (attr-by-key :todo/was)) => true))

  (component ":todo/id is the identity attribute"
    (let [id-attr (attr-by-key :todo/id)]
      (assertions
        "marked as identity"
        (ao/identity? id-attr) => true
        "data type is :uuid (matches our schema)"
        (ao/type id-attr) => :uuid
        "required"
        (ao/required? id-attr) => true)))

  (component ":todo/text is a required string keyed by :todo/id"
    (let [text-attr (attr-by-key :todo/text)]
      (assertions
        ":type is :string"
        (ao/type text-attr) => :string
        ":required? is true"
        (ao/required? text-attr) => true
        "identities include :todo/id"
        (contains? (ao/identities text-attr) :todo/id) => true)))

  (component ":todo/status enumerates the four valid statuses"
    (let [status-attr (attr-by-key :todo/status)
          values      (ao/enumerated-values status-attr)]
      (assertions
        ":type is :keyword"
        (ao/type status-attr) => :keyword
        "values match the canonical status set"
        values => #{:status/new :status/ready :status/done :status/cancelled})))

  (component ":todo/was is optional (only present when cancelled)"
    (let [was-attr (attr-by-key :todo/was)]
      (assertions
        ":required? is false (schema invariant — only set when cancelled)"
        (ao/required? was-attr) => false
        ":type matches :todo/status"
        (ao/type was-attr) => :keyword))))
