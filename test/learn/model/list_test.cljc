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

(specification "auto-markable?"
  (component "false when no new items"
    (assertions
      "empty list — false"
      (sut/auto-markable? []) => false

      "only ready items — false (no new)"
      (sut/auto-markable? [(todo id-1 "A" :status/ready)
                           (todo id-2 "B" :status/ready)])
      => false

      "only done items — false (no new)"
      (sut/auto-markable? [(todo id-1 "A" :status/done)])
      => false

      "only cancelled items — false (no new)"
      (sut/auto-markable? [(todo id-1 "A" :status/cancelled)])
      => false

      "mix of done and cancelled — false (still no new)"
      (sut/auto-markable? [(todo id-1 "A" :status/done)
                           (todo id-2 "B" :status/cancelled)])
      => false))

  (component "false when ready items exist (regardless of news present)"
    (assertions
      "one new + one ready — false"
      (sut/auto-markable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)])
      => false

      "many news + one ready — false"
      (sut/auto-markable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/ready)])
      => false

      "news, ready, plus done/cancelled — false"
      (sut/auto-markable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/ready)
                           (todo id-3 "C" :status/done)
                           (todo id-4 "D" :status/cancelled)])
      => false))

  (component "true when at least one new and no ready"
    (assertions
      "single new — true"
      (sut/auto-markable? [(todo id-1 "A" :status/new)])
      => true

      "multiple news, nothing else — true"
      (sut/auto-markable? [(todo id-1 "A" :status/new)
                           (todo id-2 "B" :status/new)])
      => true

      "done + new — true (no ready)"
      (sut/auto-markable? [(todo id-1 "A" :status/done)
                           (todo id-2 "B" :status/new)])
      => true

      "cancelled + new — true (no ready)"
      (sut/auto-markable? [(todo id-1 "A" :status/cancelled)
                           (todo id-2 "B" :status/new)])
      => true

      "done + new + cancelled (no ready) — true"
      (sut/auto-markable? [(todo id-1 "A" :status/done)
                           (todo id-2 "B" :status/new)
                           (todo id-3 "C" :status/cancelled)])
      => true)))

(specification "auto-mark"
  (component "returns items unchanged when not auto-markable"
    (assertions
      "empty list — unchanged (empty)"
      (sut/auto-mark []) => []

      "only ready items — unchanged"
      (sut/auto-mark [(todo id-1 "A" :status/ready)
                      (todo id-2 "B" :status/ready)])
      => [(todo id-1 "A" :status/ready)
          (todo id-2 "B" :status/ready)]

      "ready + new (has ready, so not auto-markable) — unchanged"
      (sut/auto-mark [(todo id-1 "A" :status/ready)
                      (todo id-2 "B" :status/new)])
      => [(todo id-1 "A" :status/ready)
          (todo id-2 "B" :status/new)]

      "only done/cancelled (no new) — unchanged"
      (sut/auto-mark [(todo id-1 "A" :status/done)
                      (todo id-2 "B" :status/cancelled)])
      => [(todo id-1 "A" :status/done)
          (todo id-2 "B" :status/cancelled)]))

  (component "promotes first :status/new to :status/ready when auto-markable"
    (assertions
      "single new — becomes ready"
      (sut/auto-mark [(todo id-1 "A" :status/new)])
      => [(todo id-1 "A" :status/ready)]

      "two news — first becomes ready, second stays new"
      (sut/auto-mark [(todo id-1 "A" :status/new)
                      (todo id-2 "B" :status/new)])
      => [(todo id-1 "A" :status/ready)
          (todo id-2 "B" :status/new)]

      "first new among done/cancelled — that one promotes, rest untouched"
      (sut/auto-mark [(todo id-1 "A" :status/done)
                      (todo id-2 "B" :status/new)
                      (todo id-3 "C" :status/cancelled)
                      (todo id-4 "D" :status/new)])
      => [(todo id-1 "A" :status/done)
          (todo id-2 "B" :status/ready)
          (todo id-3 "C" :status/cancelled)
          (todo id-4 "D" :status/new)]))

  (component "idempotence — auto-marking an already-auto-marked list is a no-op"
    (assertions
      "applying twice yields the same result as applying once"
      (let [start [(todo id-1 "A" :status/new)
                   (todo id-2 "B" :status/new)]
            once  (sut/auto-mark start)
            twice (sut/auto-mark once)]
        twice)
      => [(todo id-1 "A" :status/ready)
          (todo id-2 "B" :status/new)])))
