utoFocus Fulcro project � agent context

## Goal & status

Hands-on TDD port of the AutoFocus productivity model to Fulcro/Pathom.
Multi-phase learning project; **never skip phases, always TDD red-green-refactor**.

- `docs/phases.md` � phase tracker. Current state: **Phase 5K next**
  (prioritize/review flow). Read this BEFORE doing anything.
- `docs/js_source_reference.md` � signatures + behavior summaries for every
  function in the original JS source, with divergence notes per phase.
  Consult before introducing any new model-layer function in 5J/5K.
- `docs/SCHEMA.md` � canonical domain reference. Read before making any
  decision that touches schema, invariants, or operation contracts.
- `docs/learned_while_making_this.md` � past mistakes by category. Skim
  before starting work; consult when stuck.

## Hard rules

- **No code without a failing test first.** Tests get added before
  implementation; verify they fail (red) before writing the function.
- **Stub-then-implement is preferred.** When adding a function, ship
  a stub returning the obvious validation error first; observe the
  partial-green; then implement fully. This proves each test exercises
  the behavior it claims to exercise.
- **Always run the master test runner after every change.** Code-level
  REPL evaluation is not a substitute for the spec runner.
- **Decisions that diverge from the JS source** (see phases.md for the
  open list) require explicit user approval before locking in. Ask in
  chat; don't just choose.
- **One phase sub-step at a time.** After completing a sub-phase (e.g.
  5J.2), stop and report. Do not chain into 5J.3 without confirmation.

## Project layout (memory aid)

- `src/learn/client.cljc` � Fulcro UI + mutations + state-helpers (*-suffixed)
- `src/learn/model/schema.cljc` � Malli schemas via Guardrails registry bridge
- `src/learn/model/list.cljc` � pure domain functions, `>defn` contracts
- `src/learn/parser.clj` `resolvers.clj` `server.clj` � Pathom 2 backend
- `test/learn/**` � fulcro-spec specs mirror src layout

## REPL workflow

A long-running nREPL is in a separate window. To discover and evaluate:

\```bash
clj-nrepl-eval --discover-ports
clj-nrepl-eval -p <port> <<'EOF'
(do
  ...forms wrapped in do...)
EOF
\```

ALWAYS wrap multi-form sends in `(do ...)` per the clojure-repl skill �
without it, output gets fragmented and downstream tooling breaks.

## Master test runner

This snippet is the source of truth for "is everything green?":

\```clojure
(do
  (require '[clojure.java.io :as io]
           '[clojure.string :as str])
  (letfn [(ms [start] (long (/ (- (System/nanoTime) start) 1e6)))
          (ns-syms-in [base-dir]
            (let [base (io/file base-dir)]
              (->> (file-seq base)
                   (filter #(.isFile %))
                   (filter #(re-find #"\.clj[cs]?$" (.getName %)))
                   (map (fn [f]
                          (let [rel (-> (.toPath base)
                                        (.relativize (.toPath f))
                                        str
                                        (str/replace "\\" "/")
                                        (str/replace #"\.clj[cs]?$" ""))]
                            (symbol (-> rel
                                        (str/replace "/" ".")
                                        (str/replace "_" "-"))))))
                   sort vec)))]
    (let [t-start (System/nanoTime)
          src-syms (ns-syms-in "src")
          test-syms (ns-syms-in "test")]
      (doseq [ns-sym (concat src-syms test-syms)]
        (require ns-sym :reload))
      (require 'fulcro-spec.reporters.repl)
      (let [results (mapv #(clojure.test/run-tests %) test-syms)
            totals (apply merge-with +
                          (map #(select-keys % [:test :pass :fail :error])
                               results))]
        (println "TOTALS:" totals)
        (println "TOTAL TIME:" (ms t-start) "ms")))))
\```

Send this via clj-nrepl-eval to verify green-ness.

## Conventions (locked in)

- `>defn` Guardrails contracts for all `learn.model.*` functions
- `:learn.model.schema/...` (fully-qualified, no alias) in gspecs
- `auto-mark` (no `*` suffix) in model layer; `*` suffix reserved for
  state-map ? state-map helpers in `client.cljc`
- Inline private helpers (`defn-`) until a third caller exists; only
  then extract to a sibling namespace (e.g. `learn.model.item`)
- Active-status ordering invariant: in any Phase 5I/5J-reachable list,
  all `:status/ready` items precede all `:status/new` items
  (see SCHEMA.md �5)
