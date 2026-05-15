(ns learn.model.scenarios-test
  "Phase 20a — long-form algorithm cross-validation scenarios.

   These specs port the `tests/index.test.ts` E2E scenarios from
   fp-autofocus (the canonical TypeScript reference implementation
   of AutoFocus) into the Fulcro model layer. They're stepped
   scenarios — each `(component ...)` walks a list through 5-10
   model operations and asserts on intermediate AND final state.

   The goal is **algorithm fidelity vs. the OG TypeScript port**.
   See `docs/e2e_test_research.md` for the strategy write-up.

   Helpers mirror fp-autofocus's `af-test-utils.ts`:
     items->marks    ≅ expectMarksString
     add-many        ≅ populateDemoAppByList
     simulate-yes/no ≅ chart yes-action/no-action (model layer only)
     simulate-answers ≅ SIMenterMarkAndReviewState

   Divergence note: our `add-todo` applies the AutoFocus add rule
   immediately (first item → :status/ready), while fp-autofocus
   leaves added items unmarked until review-mode enters. This is
   the documented divergence from SCHEMA.md §7. The cross-port
   scenarios capture our model's behavior, not fp-autofocus's
   exact pre-review state — what matches is the algorithm's
   *terminal* invariants."
  (:require
    [clojure.string :as str]
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.model.list :as list-model]
    [learn.model.review :as review]))

;; ============================================================================
;; Helpers (lift from fp-autofocus's af-test-utils.ts, adapted)
;; ============================================================================

(defn- items->marks
  "Convert an items vector to fp-autofocus's visual mark string:
     :status/new       → [ ]
     :status/ready     → [o]
     :status/done      → [x]
     :status/cancelled → [~]
   Joined with single spaces, matching `expectMarksString`."
  [items]
  (->> items
    (map (fn [{:keys [todo/status]}]
           (case status
             :status/new       "[ ]"
             :status/ready     "[o]"
             :status/done      "[x]"
             :status/cancelled "[~]")))
    (str/join " ")))

(defn- add-many
  "Add multiple items in sequence, returning the final items vector.
   Each `add-todo` call applies the AutoFocus add rule against the
   CURRENT items — the first item becomes :status/ready, subsequent
   ones :status/new."
  [items texts]
  (reduce
    (fn [acc text]
      (let [result (list-model/add-todo acc text)]
        (if (:ok? result) (:items result) acc)))
    items
    texts))

(defn- simulate-yes
  "Mirror of `learn.review.chart/yes-action` at the model layer:
   promote items[cursor] to :ready, return [items', next-cursor]."
  [items cursor]
  (let [items'  (assoc-in items [cursor :todo/status] :status/ready)
        cursor' (review/next-cursor items' (inc cursor))]
    [items' cursor']))

(defn- simulate-no
  "Mirror of `learn.review.chart/no-action`: advance cursor, items
   unchanged."
  [items cursor]
  [items (review/next-cursor items (inc cursor))])

(defn- simulate-answers
  "Walk a review session from `initial-cursor` through a sequence of
   `:yes` / `:no` answers. Stops if cursor becomes -1 (no more :new
   items at-or-after the benchmark)."
  [items answers]
  (reduce
    (fn [acc answer]
      (let [{:keys [items cursor]} acc]
        (if (or (nil? cursor) (= -1 cursor))
          (reduced acc)
          (let [[items' cursor'] (case answer
                                   :yes (simulate-yes items cursor)
                                   :no  (simulate-no  items cursor))]
            {:items items' :cursor cursor'}))))
    {:items items :cursor (review/initial-cursor items)}
    answers))

;; ============================================================================
;; Scenario 1 — Simple 3-item workflow
;; Cross-port of fp-autofocus "Simple E2E test".
;; ============================================================================

(specification "Scenario — simple 3-item workflow"
  ;; fp-autofocus's "Simple E2E test": add 3 items, mark each, complete the
  ;; last. Their version uses unmarked starts ("[ ] [ ] [ ]") then marks
  ;; via review. Ours auto-marks item 0 immediately on add — so our
  ;; intermediate state differs even though terminal invariants match.
  (component "add → review yes/yes → mark done"
    (let [items                (add-many [] ["Write report" "Check email" "Tidy desk"])
          marks-after-add      (items->marks items)
          ;; First :new is at idx 1 (after auto-promoted idx 0). Cursor
          ;; starts there; :yes :yes promotes both 1 and 2 to :ready.
          {items' :items}      (simulate-answers items [:yes :yes])
          marks-after-review   (items->marks items')
          ;; Mark Done targets the LAST :ready (now idx 2). After complete,
          ;; idx 2 is :done; auto-mark stays a no-op because :ready items
          ;; still exist (0 and 1).
          {items'' :items}     (list-model/complete-benchmark-item items')
          marks-after-complete (items->marks items'')]
      (assertions
        "3 items were added"
        (count items) => 3
        "after add: item 0 auto-promoted to :ready, 1 and 2 stay :new"
        marks-after-add => "[o] [ ] [ ]"
        "after :yes :yes review: all 3 items are :ready"
        marks-after-review => "[o] [o] [o]"
        "after Mark Done: the last :ready (idx 2) becomes :done"
        marks-after-complete => "[o] [o] [x]"
        "benchmark fell back to the next-last :ready"
        (:todo/status (list-model/benchmark-item items'')) => :status/ready))))

;; ============================================================================
;; Scenario 2 — Mark Done triggers auto-mark when no :ready remains
;; Validates SCHEMA.md §6 auto-mark rule under the complete-benchmark path.
;; ============================================================================

(specification "Scenario — auto-mark promotion on Mark Done"
  ;; Cross-port of the fp-autofocus pattern where completing the last
  ;; marked item leaves zero marks, then auto-marking kicks in to
  ;; nominate the first unmarked item as the new ready.
  (component "single :ready completes → auto-mark promotes first :new"
    (let [items              (add-many [] ["Task A" "Task B" "Task C" "Task D"])
          ;; After add: [o] [ ] [ ] [ ]. Single :ready at idx 0.
          marks-before       (items->marks items)
          ;; Mark Done: idx 0 → :done. No :ready left → auto-mark fires,
          ;; promoting idx 1 (first :new) to :ready.
          {items' :items}    (list-model/complete-benchmark-item items)
          marks-after        (items->marks items')]
      (assertions
        "starting state has exactly one :ready at idx 0"
        marks-before => "[o] [ ] [ ] [ ]"
        "after Mark Done: idx 0 done; idx 1 auto-promoted to :ready"
        marks-after => "[x] [o] [ ] [ ]"
        "benchmark moved to idx 1"
        (:todo/text (list-model/benchmark-item items')) => "Task B")))

  (component "multi-ready: Mark Done leaves :ready intact, no auto-mark"
    ;; Counterpart: when other :ready items remain after completion,
    ;; auto-mark is a no-op (it only fires when ZERO :ready exists).
    (let [;; After 4 adds + :yes :yes review: [o] [o] [o] [ ]
          items              (add-many [] ["A" "B" "C" "D"])
          {items-r :items}   (simulate-answers items [:yes :yes])
          ;; Mark Done: last :ready (idx 2) → :done. :ready still exists
          ;; at idx 0 and 1, so auto-mark is a no-op.
          {items' :items}    (list-model/complete-benchmark-item items-r)
          marks-after        (items->marks items')]
      (assertions
        "after Mark Done: only idx 2 changed, idx 3 stays :new (no auto-mark)"
        marks-after => "[o] [o] [x] [ ]"))))

;; ============================================================================
;; Scenario 3 — Cancel triggers auto-mark
;; Validates that cancel-todo composes auto-mark just like complete does.
;; ============================================================================

(specification "Scenario — auto-mark promotion on cancel of sole :ready"
  (component "cancel the only :ready → auto-mark promotes first :new"
    (let [items             (add-many [] ["Task X" "Task Y" "Task Z"])
          ;; After add: [o] [ ] [ ]. Cancel idx 0.
          target-id         (:todo/id (nth items 0))
          marks-before      (items->marks items)
          {items' :items}   (list-model/cancel-todo items target-id)
          marks-after       (items->marks items')]
      (assertions
        "starting state: one :ready at idx 0"
        marks-before => "[o] [ ] [ ]"
        "after cancel: idx 0 :cancelled; idx 1 auto-promoted to :ready"
        marks-after => "[~] [o] [ ]"
        "cancelled item retains its prior status in :todo/was"
        (:todo/was (nth items' 0)) => :status/ready)))

  (component "cancel a :new item with :ready still present → no auto-mark needed"
    ;; Auto-mark only fires when ZERO :ready exists. Cancelling a :new
    ;; while :ready exists is a normal cancel with no promotion.
    (let [items             (add-many [] ["A" "B" "C"])
          ;; After add: [o] [ ] [ ]. Cancel idx 1 (a :new item).
          target-id         (:todo/id (nth items 1))
          {items' :items}   (list-model/cancel-todo items target-id)
          marks-after       (items->marks items')]
      (assertions
        "after cancel: idx 1 is :cancelled, idx 0 still :ready, idx 2 still :new"
        marks-after => "[o] [~] [ ]"
        "no promotion happened (auto-mark was a no-op)"
        (:todo/was (nth items' 1)) => :status/new))))
