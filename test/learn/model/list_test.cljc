(ns learn.model.list-test
  (:require
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.model.list :as sut]))

;; ============================================================================
;; Test fixtures — small denormalized todo vectors covering the cases we care
;; about. Using stable UUIDs makes failure messages readable.
;; ============================================================================

(def id-1 #uuid "11111111-1111-1111-1111-111111111111")
(def id-2 #uuid "22222222-2222-2222-2222-222222222222")
(def id-3 #uuid "33333333-3333-3333-3333-333333333333")
(def id-4 #uuid "44444444-4444-4444-4444-444444444444")

(defn todo
  "Concise constructor for a todo. Defaults status to :status/new."
  ([id text]        (todo id text :status/new))
  ([id text status] {:todo/id id :todo/text text :todo/status status}))

;; ============================================================================
;; Specifications
;; ============================================================================

(specification "benchmark-item"
  (component "no-ready cases"
    (assertions
      "empty list — returns nil"
      (sut/benchmark-item []) => nil

      "only :status/new items — returns nil"
      (sut/benchmark-item [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/new)])
      => nil

      "only :status/done items — returns nil"
      (sut/benchmark-item [(todo id-1 "A" :status/done)
                           (todo id-2 "B" :status/done)])
      => nil

      "only :status/cancelled items — returns nil"
      (sut/benchmark-item [(todo id-1 "A" :status/cancelled)
                           (todo id-2 "B" :status/cancelled)])
      => nil

      "mix of new/done/cancelled with no ready — returns nil"
      (sut/benchmark-item [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/done)
                           (todo id-3 "C" :status/cancelled)])
      => nil))

  (component "single ready item"
    (assertions
      "ready as the only item — returns it"
      (sut/benchmark-item [(todo id-1 "A" :status/ready)])
      => (todo id-1 "A" :status/ready)

      "ready surrounded by new items — returns it"
      (sut/benchmark-item [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/new)])
      => (todo id-2 "B" :status/ready)

      "ready surrounded by done/cancelled — returns it"
      (sut/benchmark-item [(todo id-1 "A" :status/done)
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/cancelled)])
      => (todo id-2 "B" :status/ready)))

  (component "multiple ready items — last in list order wins"
    (assertions
      "two consecutive ready — returns the second"
      (sut/benchmark-item [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/ready)])
      => (todo id-2 "B" :status/ready)

      "three ready separated by other statuses — returns the last"
      (sut/benchmark-item [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/ready)
                           (todo id-4 "D" :status/done)])
      => (todo id-3 "C" :status/ready)

      "ready at the very end of the list — returns it"
      (sut/benchmark-item [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/done)
                           (todo id-3 "C" :status/ready)
                           (todo id-4 "D" :status/ready)])
      => (todo id-4 "D" :status/ready))))
