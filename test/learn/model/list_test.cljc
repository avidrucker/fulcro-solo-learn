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

(specification "add-todo"
  (component "blank text returns error result"
    (assertions
      "empty string — error"
      (sut/add-todo [] "" id-1)
      => {:ok? false :error/type :error/blank-item}

      "whitespace-only — error"
      (sut/add-todo [] "   " id-1)
      => {:ok? false :error/type :error/blank-item}

      "tabs and newlines — error"
      (sut/add-todo [] "\t\n" id-1)
      => {:ok? false :error/type :error/blank-item}

      "blank on a populated list — still error, items unchanged"
      (sut/add-todo [(todo id-1 "A" :status/ready)] "" id-2)
      => {:ok? false :error/type :error/blank-item}))

  (component "empty list — new todo gets :status/ready"
    (assertions
      "single addition into empty list"
      (sut/add-todo [] "First task" id-1)
      => {:ok? true
          :items [(todo id-1 "First task" :status/ready)]}))

  (component "list with no ready items — new todo gets :status/ready"
    (assertions
      "only new items"
      (sut/add-todo [(todo id-1 "A" :status/new)] "Second" id-2)
      => {:ok? true
          :items [(todo id-1 "A" :status/new)
                  (todo id-2 "Second" :status/ready)]}

      "only done items"
      (sut/add-todo [(todo id-1 "A" :status/done)] "Second" id-2)
      => {:ok? true
          :items [(todo id-1 "A" :status/done)
                  (todo id-2 "Second" :status/ready)]}

      "only cancelled items"
      (sut/add-todo [(todo id-1 "A" :status/cancelled)] "Second" id-2)
      => {:ok? true
          :items [(todo id-1 "A" :status/cancelled)
                  (todo id-2 "Second" :status/ready)]}

      "mix of new/done/cancelled (no ready)"
      (sut/add-todo [(todo id-1 "A" :status/new)
                     (todo id-2 "B" :status/done)
                     (todo id-3 "C" :status/cancelled)]
        "Fourth" id-4)
      => {:ok? true
          :items [(todo id-1 "A" :status/new)
                  (todo id-2 "B" :status/done)
                  (todo id-3 "C" :status/cancelled)
                  (todo id-4 "Fourth" :status/ready)]}))

  (component "list with at least one ready item — new todo gets :status/new"
    (assertions
      "single ready item"
      (sut/add-todo [(todo id-1 "A" :status/ready)] "Second" id-2)
      => {:ok? true
          :items [(todo id-1 "A" :status/ready)
                  (todo id-2 "Second" :status/new)]}

      "multiple ready items"
      (sut/add-todo [(todo id-1 "A" :status/ready)
                     (todo id-2 "B" :status/ready)]
        "Third" id-3)
      => {:ok? true
          :items [(todo id-1 "A" :status/ready)
                  (todo id-2 "B" :status/ready)
                  (todo id-3 "Third" :status/new)]}

      "mix with at least one ready"
      (sut/add-todo [(todo id-1 "A" :status/new)
                     (todo id-2 "B" :status/ready)
                     (todo id-3 "C" :status/done)]
        "Fourth" id-4)
      => {:ok? true
          :items [(todo id-1 "A" :status/new)
                  (todo id-2 "B" :status/ready)
                  (todo id-3 "C" :status/done)
                  (todo id-4 "Fourth" :status/new)]}))

  (component "2-arity form auto-generates a UUID"
    (assertions
      "non-blank text returns ok? true"
      (:ok? (sut/add-todo [] "Hello"))
      => true

      "the new todo's :todo/id is a UUID"
      (-> (sut/add-todo [] "Hello") :items first :todo/id uuid?)
      => true

      "each call generates a different UUID"
      (= (-> (sut/add-todo [] "Hello") :items first :todo/id)
        (-> (sut/add-todo [] "Hello") :items first :todo/id))
      => false)))

(specification "cancel-todo"
  (component "refuses on missing id"
    (assertions
      "empty list with any id — :error/item-not-found"
      (sut/cancel-todo [] id-1)
      => {:ok? false :error/type :error/item-not-found}

      "id not present in items — :error/item-not-found"
      (sut/cancel-todo [(todo id-1 "A" :status/ready)] id-2)
      => {:ok? false :error/type :error/item-not-found}))

  (component "refuses to cancel a :status/done todo"
    (assertions
      "single :done item — :error/cannot-cancel"
      (sut/cancel-todo [(todo id-1 "A" :status/done)] id-1)
      => {:ok? false :error/type :error/cannot-cancel}

      ":done in a mixed list — :error/cannot-cancel"
      (sut/cancel-todo [(todo id-1 "A" :status/ready)
                        (todo id-2 "B" :status/done)] id-2)
      => {:ok? false :error/type :error/cannot-cancel}))

  (component "refuses to cancel an already :status/cancelled todo (double-cancel)"
    ;; Departs from the JS source's silent idempotence. The model layer
    ;; treats double-cancel as an explicit error per SCHEMA.md §15.
    (let [cancelled-todo (-> (todo id-1 "A" :status/cancelled)
                           (assoc :todo/was :status/new))]
      (assertions
        "single :cancelled item — :error/cannot-cancel"
        (sut/cancel-todo [cancelled-todo] id-1)
        => {:ok? false :error/type :error/cannot-cancel})))

  (component "cancels a :status/new todo, captures :todo/was"
    (let [items   [(todo id-1 "A" :status/ready)
                   (todo id-2 "B" :status/new)]
          result  (sut/cancel-todo items id-2)
          updated (-> result :items second)]
      (assertions
        ":ok? true"
        (:ok? result) => true
        "target :todo/status becomes :status/cancelled"
        (:todo/status updated) => :status/cancelled
        ":todo/was captures the previous :status/new"
        (:todo/was updated) => :status/new
        "the :status/ready item is unchanged"
        (-> result :items first) => (todo id-1 "A" :status/ready))))

  (component "cancels a :status/ready todo, captures :todo/was"
    (let [items   [(todo id-1 "A" :status/ready)
                   (todo id-2 "B" :status/ready)]
          result  (sut/cancel-todo items id-1)
          updated (-> result :items first)]
      (assertions
        ":ok? true"
        (:ok? result) => true
        "target :todo/status becomes :status/cancelled"
        (:todo/status updated) => :status/cancelled
        ":todo/was captures the previous :status/ready"
        (:todo/was updated) => :status/ready)))

  (component "auto-mark fires after cancelling the sole :ready item"
    (let [items   [(todo id-1 "A" :status/ready)
                   (todo id-2 "B" :status/new)
                   (todo id-3 "C" :status/new)]
          result  (sut/cancel-todo items id-1)]
      (assertions
        ":ok? true"
        (:ok? result) => true
        "cancelled item is :status/cancelled"
        (:todo/status (nth (:items result) 0)) => :status/cancelled
        "first :new is promoted to :status/ready (auto-mark fired)"
        (:todo/status (nth (:items result) 1)) => :status/ready
        "second :new stays :status/new"
        (:todo/status (nth (:items result) 2)) => :status/new)))

  (component "no auto-mark when other :ready items remain"
    (let [items   [(todo id-1 "A" :status/ready)
                   (todo id-2 "B" :status/ready)
                   (todo id-3 "C" :status/new)]
          result  (sut/cancel-todo items id-1)]
      (assertions
        "cancelled item :status/cancelled"
        (:todo/status (nth (:items result) 0)) => :status/cancelled
        "other :ready item stays :status/ready"
        (:todo/status (nth (:items result) 1)) => :status/ready
        ":status/new item stays :status/new (no auto-mark fired)"
        (:todo/status (nth (:items result) 2)) => :status/new)))

  (component "no auto-mark when no :new items to promote"
    (let [items   [(todo id-1 "A" :status/ready)
                   (todo id-2 "B" :status/done)]
          result  (sut/cancel-todo items id-1)]
      (assertions
        ":ok? true"
        (:ok? result) => true
        "cancelled item :status/cancelled"
        (:todo/status (nth (:items result) 0)) => :status/cancelled
        ":done item is unchanged"
        (nth (:items result) 1) => (todo id-2 "B" :status/done)))))
