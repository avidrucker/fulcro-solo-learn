(ns learn.dev-fixtures
  "Phase 21.1 — dev-mode list fixtures. Pure data; no Fulcro / no Pathom /
   no IO. Loadable on JVM and CLJS. Consumed by `learn.dev-config`'s
   list-cycler.

   Each fixture is a `::learn.model.schema/items` vector that preserves
   the SCHEMA.md §5 active-status ordering invariant: all `:status/ready`
   items precede all `:status/new` items in list order.")

#?(:clj  (defn- ->uuid [s] (java.util.UUID/fromString s))
   :cljs (defn- ->uuid [s] (cljs.core/uuid s)))

(def items-5
  "Five-item fixture covering all four statuses. List order is
   [cancelled, cancelled, done, ready, new] — the active subsequence
   (non-cancelled, non-done) is [:ready :new], satisfying SCHEMA.md §5."
  [{:todo/id     #uuid "51111111-1111-1111-1111-111111111111"
    :todo/text   "Cancelled task (was ready)"
    :todo/status :status/cancelled
    :todo/was    :status/ready}
   {:todo/id     #uuid "52222222-2222-2222-2222-222222222222"
    :todo/text   "Cancelled task (was new)"
    :todo/status :status/cancelled
    :todo/was    :status/new}
   {:todo/id     #uuid "53333333-3333-3333-3333-333333333333"
    :todo/text   "Done task"
    :todo/status :status/done}
   {:todo/id     #uuid "54444444-4444-4444-4444-444444444444"
    :todo/text   "Ready task"
    :todo/status :status/ready}
   {:todo/id     #uuid "55555555-5555-5555-5555-555555555555"
    :todo/text   "New task"
    :todo/status :status/new}])

(def items-26
  "Twenty-six-item fixture: one :ready (item 'a') followed by 25 :new
   ('b'..'z'). Stress-test for long-list overflow / scrolling behaviour."
  (mapv
    (fn [i]
      (let [letter (char (+ 97 i))
            n-str  (str (inc i))
            pad    (apply str (repeat (- 12 (count n-str)) "0"))]
        {:todo/id     (->uuid (str "26000000-0000-0000-0000-" pad n-str))
         :todo/text   (str "Item " letter)
         :todo/status (if (zero? i) :status/ready :status/new)}))
    (range 26)))
