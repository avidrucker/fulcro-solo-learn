(ns learn.model.review-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.model.review :as sut]))

;; ============================================================================
;; Test fixtures
;; ============================================================================

(def id-1 #uuid "11111111-1111-1111-1111-111111111111")
(def id-2 #uuid "22222222-2222-2222-2222-222222222222")
(def id-3 #uuid "33333333-3333-3333-3333-333333333333")
(def id-4 #uuid "44444444-4444-4444-4444-444444444444")

(defn todo
  ([id text]        (todo id text :status/new))
  ([id text status] {:todo/id id :todo/text text :todo/status status}))

;; ============================================================================
;; Specifications
;; ============================================================================

(specification "prioritizable?"
  (component "false when :ready or :new is missing"
    (assertions
      "empty list — false"
      (sut/prioritizable? []) => false

      "only :new items — false (no :ready)"
      (sut/prioritizable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/new)])
      => false

      "only :ready items — false (no :new)"
      (sut/prioritizable? [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/ready)])
      => false

      "only :done items — false"
      (sut/prioritizable? [(todo id-1 "A" :status/done)])
      => false

      "mix of :done and :cancelled — false (no :ready, no :new)"
      (sut/prioritizable? [(todo id-1 "A" :status/done)
                           (-> (todo id-2 "B" :status/cancelled)
                             (assoc :todo/was :status/new))])
      => false))

  (component "false when last :new is at-or-before last :ready in list order"
    ;; SCHEMA.md §15 / JS-port discrepancy #1: a "prioritizable" list is one
    ;; where a new item exists AFTER the current benchmark in list order, so
    ;; the review walk has something to do.
    (assertions
      "[:new :ready] — false (last new at 0, last ready at 1)"
      (sut/prioritizable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)])
      => false

      "[:new :new :ready] — false (last new at 1, last ready at 2)"
      (sut/prioritizable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/ready)])
      => false

      "[:ready :new :ready] — false (last new at 1, last ready at 2)"
      (sut/prioritizable? [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/ready)])
      => false

      ":done/:cancelled tail doesn't promote a prior :new past the last :ready"
      (sut/prioritizable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/done)])
      => false))

  (component "true when last :new comes after last :ready in list order"
    (assertions
      "[:ready :new] — true"
      (sut/prioritizable? [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/new)])
      => true

      "[:ready :new :new] — true (last new at 2, last ready at 0)"
      (sut/prioritizable? [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/new)])
      => true

      "[:new :ready :new] — true (last new at 2, last ready at 1)"
      (sut/prioritizable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/new)])
      => true

      "[:ready :ready :new] — true (last new at 2, last ready at 1)"
      (sut/prioritizable? [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/new)])
      => true

      "interleaved :done/:cancelled don't affect the rule"
      (sut/prioritizable? [(-> (todo id-1 "A" :status/cancelled)
                             (assoc :todo/was :status/new))
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/done)
                           (todo id-4 "D" :status/new)])
      => true)))

(specification "next-cursor"
  (component "returns -1 when no :new exists at-or-after from-index"
    (assertions
      "empty list — -1"
      (sut/next-cursor [] 0) => -1

      "no :new items at all — -1"
      (sut/next-cursor [(todo id-1 "A" :status/ready)
                        (todo id-2 "B" :status/done)]
        0)
      => -1

      "from-index past the last :new — -1"
      (sut/next-cursor [(todo id-1 "A" :status/new)
                        (todo id-2 "B" :status/ready)]
        1)
      => -1

      "from-index past end of list — -1"
      (sut/next-cursor [(todo id-1 "A" :status/new)] 5)
      => -1))

  (component "returns first :new index at-or-after from-index"
    (assertions
      "from-index 0, :new at 0 — returns 0"
      (sut/next-cursor [(todo id-1 "A" :status/new)] 0)
      => 0

      "from-index 0, first :new at 2 — returns 2"
      (sut/next-cursor [(todo id-1 "A" :status/ready)
                        (todo id-2 "B" :status/done)
                        (todo id-3 "C" :status/new)]
        0)
      => 2

      "from-index lands on a :new — returns that index (inclusive at-or-after)"
      (sut/next-cursor [(todo id-1 "A" :status/ready)
                        (todo id-2 "B" :status/new)
                        (todo id-3 "C" :status/new)]
        1)
      => 1

      "from-index skips a :new before it, picks next :new"
      (sut/next-cursor [(todo id-1 "A" :status/new)
                        (todo id-2 "B" :status/ready)
                        (todo id-3 "C" :status/new)]
        2)
      => 2

      "from-index after a :new, returns the next :new further down"
      (sut/next-cursor [(todo id-1 "A" :status/new)
                        (todo id-2 "B" :status/ready)
                        (todo id-3 "C" :status/new)
                        (todo id-4 "D" :status/new)]
        2)
      => 2)))

(specification "initial-cursor"
  (component "returns -1 when the list has no actionable starting point"
    (assertions
      "empty list — -1"
      (sut/initial-cursor []) => -1

      "no :ready items — -1 (no benchmark to start from)"
      (sut/initial-cursor [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/new)])
      => -1

      ":ready exists but no :new at-or-after it — -1"
      (sut/initial-cursor [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)])
      => -1))

  (component "returns the first :new at-or-after the last :ready"
    (assertions
      "[:ready :new] — 1"
      (sut/initial-cursor [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/new)])
      => 1

      "[:ready :new :new] — first new is 1"
      (sut/initial-cursor [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/new)])
      => 1

      "[:new :ready :new] — first :new after the last :ready is at 2"
      (sut/initial-cursor [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/new)])
      => 2

      "multiple :ready, first :new after the LAST :ready"
      (sut/initial-cursor [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/ready)
                           (todo id-4 "D" :status/new)])
      => 3

      ":done/:cancelled between last :ready and the first :new are skipped"
      (sut/initial-cursor [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/done)
                           (-> (todo id-3 "C" :status/cancelled)
                             (assoc :todo/was :status/ready))
                           (todo id-4 "D" :status/new)])
      => 3)))
