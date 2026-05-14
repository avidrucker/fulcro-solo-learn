(ns learn.version-macros
  "Compile-time version-from-package.json macro. The Fulcro port's
   version is the single source of truth in `package.json` (`version`
   field) — this macro reads that file at macro-expansion time and
   emits the value as a string literal into the calling code.

   Why a macro: the version is needed in both CLJ (specs, headless
   spec suite) and CLJS (the Info modal). CLJS can't slurp at
   runtime; CLJ can but we want the build to be self-contained.
   A macro runs at compile time (in the JVM hosting the CLJS
   compile) and inlines the string, so both targets see the same
   value with no runtime I/O."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

(defmacro fulcro-version
  "Returns the value of the `version` field in `package.json` as a
   string literal at compile time. Uses a small regex (no JSON
   dep) — the package.json shape we care about is stable. Falls
   back to the literal string `unknown` if the file isn't readable
   or the field can't be parsed; that's loud enough in the Info
   modal that we'd notice."
  []
  (let [text (try (slurp (io/file "package.json"))
                  (catch Exception _ nil))
        m    (when text (re-find #"\"version\"\s*:\s*\"([^\"]+)\"" text))]
    (if m (second m) "unknown")))
