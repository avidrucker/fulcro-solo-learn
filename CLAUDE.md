utoFocus Fulcro project � agent context

## Goal & status

Hands-on TDD port of the AutoFocus productivity model to Fulcro/Pathom.
Multi-phase learning project; **never skip phases, always TDD red-green-refactor**.

- `docs/phases.md` � phase tracker. Current state: **Phase 5K.5 next**
  (client wiring — chart session lifecycle, alias wiring, mutations that
  `scf/send!`). Read this BEFORE doing anything.
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
- **After any refactor that removes/renames a public var, verify with a
  fresh JVM.** `(require :reload-all)` in a long-running REPL keeps stale
  vars from earlier sessions alive — local tests pass, CI fails. Run
  `clojure -M:test:cljs -m test-runner` as a fresh process before push
  whenever you've touched re-exports / aliases / sub-namespace
  boundaries. See `docs/learned_while_making_this.md` Part 1 →
  "REPL / stale-vars-after-refactor".
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

This snippet is the source of truth for "is everything green?". Scans
`.clj` and `.cljc` only — `.cljs` files (e.g. `learn.main`) are
browser-only and not loadable on the JVM, so they're deliberately
excluded:

\```clojure
(do
  (require '[clojure.java.io :as io]
           '[clojure.string :as str])
  (letfn [(ms [start] (long (/ (- (System/nanoTime) start) 1e6)))
          (ns-syms-in [base-dir]
            (let [base (io/file base-dir)]
              (->> (file-seq base)
                   (filter #(.isFile %))
                   (filter #(re-find #"\.cljc?$" (.getName %)))
                   (map (fn [f]
                          (let [rel (-> (.toPath base)
                                        (.relativize (.toPath f))
                                        str
                                        (str/replace "\\" "/")
                                        (str/replace #"\.cljc?$" ""))]
                            (symbol (-> rel
                                        (str/replace "/" ".")
                                        (str/replace "_" "-"))))))
                   sort vec)))]
    (let [t-start (System/nanoTime)
          test-syms (ns-syms-in "test")]
      ;; `:reload-all` on each test namespace transitively re-loads all
      ;; required src namespaces in dependency order — so a fresh `def`
      ;; in `learn.ui.icons` is visible by the time `learn.client`
      ;; re-evaluates, and a fresh defmutation in `learn.resolvers` is
      ;; captured by `learn.parser`. Generates some `BUG: Internal error
      ;; validating ...` noise from malli's registry during reload; this
      ;; is cosmetic and assertions still run correctly.
      (doseq [ns-sym test-syms]
        (require ns-sym :reload-all))
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
