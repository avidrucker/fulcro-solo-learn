(ns test-runner
  "CI-friendly entrypoint that discovers and runs every `*-test`
   namespace under `test/`. Mirrors the master runner in CLAUDE.md
   but exits with a non-zero status on any failure / error so
   GitHub Actions can fail the job.

   Run from `.github/workflows/main.yml` via:
     clojure -M:test -m test-runner"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :as t]))

(defn- ns-syms-in [base-dir]
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
      sort vec)))

(defn -main [& _]
  (let [test-syms (ns-syms-in "test")
        ;; Drop this namespace from the list so we don't try to run
        ;; it as a test suite.
        test-syms (filterv #(not= % 'test-runner) test-syms)]
    (doseq [ns-sym test-syms]
      (require ns-sym))
    (let [results (mapv #(t/run-tests %) test-syms)
          totals  (apply merge-with +
                    (map #(select-keys % [:test :pass :fail :error]) results))
          ok?     (zero? (+ (:fail totals 0) (:error totals 0)))]
      (println "TOTALS:" totals)
      (shutdown-agents)
      (System/exit (if ok? 0 1)))))
