# Project line-count stats (Clojure)

A tiny [`bb`](https://babashka.org/) script that walks `src/` and `test/`,
classifies each line as code / comment / blank, and prints a per-file table
plus totals. Useful as a before/after sanity check during cleanup passes.

## How to use

```bash
bb docs/clj_project_stats.md     # treats the file as a script (bb skips the
                                 # markdown wrapper and runs the (ns ...) form
                                 # — works because everything outside the
                                 # fenced clojure block is comment-like)
```

That won't actually work — bb wants a `.bb` file. Instead, save the script
section below as `project_stats.bb` (or whatever) at the repo root and run:

```bash
bb project_stats.bb
```

Or paste the body into a REPL.

## Sample output

```
 total   code comments  blank  file
 ----- ------ -------- ------  ----
   308    218       47     43  src/learn/client.cljc
   151    104       21     26  src/learn/model/list.cljc
   186     97       57     32  src/learn/model/schema.cljc
    78     53       17      8  src/learn/parser.clj
    90     43       36     11  src/learn/resolvers.clj
    57     43        5      9  src/learn/server.clj
   429    372       21     36  test/learn/client_test.clj
   638    535       21     82  test/learn/model/list_test.cljc
   154    114       21     19  test/learn/resolvers_test.clj
 ----- ------ -------- ------  ----
  2091   1579      246    266  TOTAL across 9 files
```

## The script

```clojure
#!/usr/bin/env bb
;; Lines-of-code stats for Clojure source under src/ and test/.
;; Reports total / code / comment / blank line counts per file and overall.
(ns project-stats
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def dirs ["src" "test"])
(def exts #{"clj" "cljc" "cljs"})

(defn ext-of [name]
  (let [i (.lastIndexOf name ".")]
    (when (pos? i) (subs name (inc i)))))

(defn collect-files [base]
  (->> (file-seq (io/file base))
       (filter #(.isFile %))
       (filter #(contains? exts (ext-of (.getName %))))
       (map #(-> % .getPath (str/replace "\\" "/")))
       sort))

(defn classify [line]
  (let [t (str/triml line)]
    (cond
      (str/blank? t)           :blank
      (str/starts-with? t ";") :comment
      :else                    :code)))

(defn file-stats [path]
  (let [lines  (str/split-lines (slurp path))
        groups (frequencies (map classify lines))]
    {:path     path
     :total    (count lines)
     :code     (get groups :code 0)
     :comments (get groups :comment 0)
     :blank    (get groups :blank 0)}))

(let [files (mapcat collect-files dirs)
      stats (mapv file-stats files)
      sum   (fn [k] (apply + (map k stats)))]
  (println " total   code comments  blank  file")
  (println " ----- ------ -------- ------  ----")
  (doseq [{:keys [total code comments blank path]} stats]
    (println (format "%6d %6d %8d %6d  %s" total code comments blank path)))
  (println " ----- ------ -------- ------  ----")
  (println (format "%6d %6d %8d %6d  TOTAL across %d files"
                   (sum :total) (sum :code) (sum :comments) (sum :blank)
                   (count stats))))
```

## Notes

- Counts only `.clj` / `.cljc` / `.cljs` files. Edit `dirs` and `exts` to
  scope differently (e.g. include `scripts/` or `.bb` files).
- A "comment" is any line whose first non-whitespace character is `;`.
  Trailing comments on a code line still count as code.
- "Blank" is whitespace-only.
- Reading is whole-file `slurp` + `split-lines` — fine for the size of this
  project. Switch to `line-seq` if you ever point it at a large repo.

## When this is useful

Snapshot the totals before and after a cleanup pass (e.g. a simplify pass,
a refactor that extracts a helper, a doc-trim sweep) to quantify the
delta. Particularly useful for documenting "scope creep" cleanups in a
phase-tracker entry.
