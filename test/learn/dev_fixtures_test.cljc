(ns learn.dev-fixtures-test
  "Phase 21.1 — verifies the static dev fixtures `items-5` and `items-26`
   in `learn.dev-fixtures` are schema-valid and preserve the SCHEMA.md
   §5 active-status ordering invariant (all :ready precede all :new)."
  (:require
    [fulcro-spec.core :refer [specification assertions =>]]
    [learn.dev-fixtures :as sut]
    [learn.model.schema :as schema]))

(defn- active? [todo]
  (#{:status/new :status/ready} (:todo/status todo)))

(defn- ready-before-new?
  "True iff every :ready in the active subsequence precedes every :new.
   Vacuously true when the active subsequence has no :ready or no :new."
  [items]
  (let [active-statuses (->> items (filter active?) (mapv :todo/status))
        last-ready (.lastIndexOf active-statuses :status/ready)
        first-new  (.indexOf active-statuses :status/new)]
    (or (neg? last-ready) (neg? first-new) (< last-ready first-new))))

(specification "items-5 fixture"
  (assertions
    "has exactly 5 items"
    (count sut/items-5) => 5

    "status sequence is [cancelled, cancelled, done, ready, new]"
    (mapv :todo/status sut/items-5)
    => [:status/cancelled :status/cancelled :status/done :status/ready :status/new]

    "first cancelled item carries :todo/was :status/ready"
    (:todo/was (nth sut/items-5 0)) => :status/ready

    "second cancelled item carries :todo/was :status/new"
    (:todo/was (nth sut/items-5 1)) => :status/new

    "is schema-valid (::schema/items)"
    (schema/valid? :learn.model.schema/items sut/items-5) => true

    "satisfies the active-status ordering invariant (SCHEMA.md §5)"
    (ready-before-new? sut/items-5) => true))

(specification "items-26 fixture"
  (assertions
    "has exactly 26 items"
    (count sut/items-26) => 26

    "first item is :status/ready"
    (:todo/status (first sut/items-26)) => :status/ready

    "remaining 25 items are all :status/new"
    (mapv :todo/status (rest sut/items-26)) => (vec (repeat 25 :status/new))

    "is schema-valid (::schema/items)"
    (schema/valid? :learn.model.schema/items sut/items-26) => true

    "satisfies the active-status ordering invariant (SCHEMA.md §5)"
    (ready-before-new? sut/items-26) => true))
