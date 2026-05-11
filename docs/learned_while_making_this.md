# Learned While Making This: A Retrospective on the Fulcro TODO App

A running ledger of mistakes made (by both Claude and Avi), what triggered them,
and what we learned. Compiled at the end of Phase 5I after reviewing the
complete transcripts of Phases 1 through 5I (~13K lines across three
conversations: 2026-05-08, 2026-05-10, 2026-05-11).

The document has three sections:

1. **Avi's mistakes** — tagged with domain area for later drill-and-practice.
2. **Claude's mistakes** — detailed reports tagged with the affected skill,
   intended to inform skill improvements.
3. **Suggested skills to add to Claude** — gaps identified by comparing the
   mistakes against the existing skill inventory.

---

## Extended Summary

**Part 1 — Avi's mistakes (categorized):** Sixteen mistakes tagged by domain
(tooling, REPL workflow, Fulcro UI, Clojure idioms, tests, migration). Each
has what happened, root cause, and the distilled lesson. These categories
double as drill targets: heavier mistake density in a domain = a useful
practice direction.

**Part 2 — Claude's mistakes (detailed reports):** Eleven distinct mistakes,
each with phase, affected skill, what I said, what was wrong, why I made
the mistake, the correct answer, and what the skill should have surfaced.
This is the format that can feed back into skill improvements — every entry
maps to a specific edit a skill author could make. The "why I made it"
column is the most useful for skill design: it shows the failure mode
(pattern-matching to defn, guessing from variable names, treating
configuration as separate from correctness).

**Part 3 — Suggested skills:** A new `malli` skill plus six updates to
existing skills (`guardrails`, `pathom` ×2, `fulcro`, `clojure-repl`,
`fulcro-spec-tdd`, `deps-upgrade`), plus a smaller proposed
`fulcro-ecosystem-versioning` skill. I cross-checked these against the
existing skill content (read sections of `guardrails`, `pathom/SKILL.md`,
`pathom/resources/resolver-patterns.md`, `fulcro`, `fulcro-spec-tdd`,
`clojure`, `clojure-repl`) to avoid duplicating coverage that already
exists. Where coverage exists but is buried, the suggestion is to surface
it more prominently rather than re-write.

**Appendix — cross-reference table:** Maps each Claude mistake (C1–C11) to
its triggering phase, domain, affected skill, and proposed fix location.
Useful as a single-glance summary.

---

## Part 1 — Avi's Mistakes (Categorized)

Each mistake is recorded as `[domain] / [sub-domain]` with what happened, why
it tripped, and the lesson distilled.

### Tooling & Environment

**[tooling / IntelliJ-Cursive] — Right-click "Run test file" doesn't inherit REPL aliases**
- **What happened (Phase 4):** When `fulcro-spec` was first added, the test
  `require` failed with `FileNotFoundException` because the right-click runner
  spawned a new JVM without the `:test` alias active.
- **Root cause:** Cursive's "Run test file" creates an ad-hoc run config that
  doesn't inherit aliases from the main REPL.
- **Lesson:** REPL-driven workflow is the default in Clojure: `(require
  'foo-test :reload)` + `(clojure.test/run-tests 'foo-test)` runs in the
  already-configured REPL. Avoid right-click "Run" for test files unless you
  configure aliases per-file.

**[tooling / IntelliJ-Cursive] — Aliases field in Run Configuration is easy to miss**
- **What happened (Phase 4):** The `:test` alias was in `deps.edn`, but
  IntelliJ's REPL launched without it. The Run Configuration has an "Aliases"
  field separate from "JVM Args" / "Parameters" / "Options".
- **Lesson:** When deps aren't being resolved by the REPL, check the Run
  Configuration's Aliases / Options field first. For our setup, `-A:test`
  in the Options field is what threads the alias through.

**[tooling / file-layout] — Tests landed in `src/` instead of `test/`**
- **What happened (Phase 4):** First test file was placed at
  `src/learn/client_test.clj`. The `:test` alias's `:extra-paths ["test"]`
  couldn't see it.
- **Lesson:** Tests live under `test/`, mirroring the source ns directory
  structure. The directory and the `:extra-paths` declaration have to agree.

**[tooling / REPL-state] — Assumed one REPL was wrong, asked about needing two**
- **What happened (Phase 4):** Confusion about whether to have separate REPLs
  for source vs tests.
- **Lesson:** One REPL with `:test` active is enough. Both source and test
  namespaces are loadable; both can be reloaded; both can be tested from the
  same prompt. Two-REPL setups are an anti-pattern in Clojure unless you have
  specific isolation needs.

### REPL Workflow

**[REPL / namespace-awareness] — Pasted a REPL form into the wrong namespace**
- **What happened (Phase 5F):** Claude shared a REPL form referencing
  `server/seed!`, `sut/init`, `snapshot`. Avi evaluated it from a namespace
  that didn't have those aliases. Got `No such namespace: server`.
- **Root cause partially Claude's** — the form wasn't self-contained. But the
  underlying habit to internalize: **always check `*ns*` before evaluating an
  alias-dependent form.** In Cursive, the modeline shows the active ns.
- **Lesson:** When in doubt about aliases, either use fully-qualified names
  (`learn.server/seed!`) or `(require ...)` at the top of the form.

**[REPL / stale-state] — Forgot to reload parser after editing resolvers**
- **What happened (Phase 5F):** Edited the resolver to read `:ast :params`,
  reran the REPL form, saw both todos return as if no filter applied. The
  resolver's `println` never fired.
- **Root cause:** `(pc/connect-plugin {::pc/register all-resolvers})` runs
  once at parser-load time and bakes in references to resolver function
  values. Reloading `resolvers.clj` doesn't update the parser's frozen index.
- **Lesson:** Edit a resolver → reload the parser. Production codebases avoid
  this by using a registry atom that `connect-plugin` reads dynamically each
  request — but for the explicit-vector pattern we use, you reload parser
  manually.

**[REPL / Cursive-keybindings] — Alt+P sent `(comment ...)` form, got `nil`**
- **What happened (Phase 1):** With the cursor placed *after* the closing paren
  of `(comment ...)`, Alt+P "Send Form Before Caret" sent the comment block
  itself. `comment` returns nil — looked like nothing happened.
- **Lesson:** For evaluating things *inside* a comment block, put the cursor
  *inside* the inner form and use Cursive's "Send Top Form to REPL" (Ctrl+Shift+P
  by default). It walks up to the innermost non-comment enclosing form.

### Fulcro UI Concepts

**[Fulcro / root-component] — Tried to put `:ident` on Root**
- **What happened (Phase 2):** Fulcro raised a FATAL error: "Root is not
  allowed to have an `:ident`. It is a special node co-located over the
  entire database."
- **Root cause:** Claude steered toward this with "Option A: give Root an
  ident" as the more idiomatic move. It isn't; Fulcro forbids it.
- **The real principle:** Root is *structural*, not *data-bearing*. It composes
  children but shouldn't own form fields or interactive state. If you want
  state on what feels like Root, introduce a `TodoList`-style component layer
  between Root and the inputs. That component has an ident; Root just composes
  it in.
- **Lesson:** Fulcro encodes architectural opinions through framework
  errors. When you hit "X is not allowed," the lesson is usually structural.

**[Fulcro / initial-state] — Skipped Step 1B (didn't add `:query` and `:initial-state` to Root)**
- **What happened (Phase 1):** Mounted Root with hardcoded hiccup strings.
  `:state` came back as `nil`. The whole point of Phase 1 — witnessing data
  flow from DB to UI — got skipped.
- **Lesson:** `:state nil` in your snapshot is diagnostic. It means no
  component declared `:query` + `:initial-state` chains starting at Root.
  Without those, Fulcro's normalized DB stays empty. Always sanity-check
  `:state` is *non-nil* before assuming the component pipeline works.

**[Fulcro / mutations] — Used `m/set-string!` from a context with no ident**
- **What happened (Phase 2):** Typed-in input text landed at a *nil* key in
  the state map (`{nil {:ui/new-todo-text "..."}}`) instead of at the actual
  root level. The mutation read from the wrong path, new todos came out empty.
- **Root cause:** `m/set-string!` writes to `[:ref ...]` where `:ref` is the
  component's ident. The component invoking it had no ident, so `:ref` was
  nil, and the write went to a nil-keyed bucket.
- **Lesson:** If you see a `nil` key in your state map, you're almost
  certainly running a mutation from a no-ident context. The fix is structural,
  not behavioral: give the component an ident (which usually means it
  shouldn't have been Root).

### Clojure / Macro Idioms

**[Clojure / mutation-arity] — Used `[]` for a no-params mutation**
- **What happened (Phase 5H):** `(defmutation delete-all [] ...)` failed
  because Fulcro's `defmutation` macro requires *one* arg (the params map),
  even when ignoring it.
- **Root cause:** Claude initially gave the wrong shape. Should be `[_]`.
- **Lesson:** Fulcro mutations need `[_]` not `[]`. The single-param shape is
  enforced so mutations remain serializable as data (`(some-mutation {})` is
  the canonical no-args call shape).

**[Clojure / kondo-vs-runtime] — Confused linter warnings with real errors**
- **What happened (Phase 1):** clj-kondo flagged `in-ns` and
  `app/current-state` as unresolved. Avi treated these as blockers.
- **Lesson:** clj-kondo runs static analysis. It may not have analyzed the
  Fulcro jar yet and reports false positives on library symbols. Static lint
  ≠ runtime correctness. Verify with an actual evaluation; warnings are
  often cosmetic.

**[Clojure / ns-management] — Embedded `(require :reload)` inside a function defined in that same ns**
- **What happened (Phase 1):** Wrote `re-init-app` *inside* `learn.client`
  that called `(require 'learn.client :reload)`. Asks Clojure to re-evaluate
  the file currently executing.
- **Lesson:** Reload is editor-driven (Alt+Shift+L), not code-driven. Keep
  reload logic out of source code.

### Test Hygiene

**[Tests / coupling] — Hand-built framework env shapes in every test, no helper**
- **What happened (Phase 5F):** Test fixtures constructed
  `{::p/parent-query (with-meta [] {:params ...})}` inline in each
  `component`. When the resolver contract changed, every fixture broke
  identically.
- **Lesson:** When tests construct framework-shaped data (env maps, request
  maps, state shapes), build a helper (`make-test-env`) the moment you have
  3+ uses. Insulates tests from framework-internal shape changes.

**[Tests / forgotten-requires] — Used `comp/transact!` without aliasing `comp`**
- **What happened (Phase 5H):** Test ns referenced `comp/transact!` but ns
  form didn't `:require [com.fulcrologic.fulcro.components :as comp]`. Got
  `No such namespace: comp`. Claude flagged the missing require, but it
  wasn't applied in the next patch and the same error appeared again.
- **Lesson:** When porting tests or copying patterns, also copy the relevant
  `:require` lines. When a "no such namespace" error fires, the fix is in
  the ns form 99% of the time.

**[Tests / refactor-discipline] — Renamed `reset!` to `seed!`, missed test call sites**
- **What happened (Phase 5H):** Server function renamed in source, but tests
  still called the old name. Discovered at test-run time, not edit time.
- **Lesson:** When renaming a public function, grep `src/` *and* `test/` for
  the old name. IntelliJ "Find Usages" works once Cursive has parsed
  everything, but a `grep -rn` is a fast belt-and-suspenders check.

### Domain & Migration

**[Migration / search-discipline] — Migrating `:todo/done?` → `:todo/status`, leftover refs in tests**
- **What happened (Phase 5H):** The keyword change touched many files;
  some test references to `:todo/done?` lingered.
- **Lesson:** For any keyword migration in a Clojure codebase, the verification
  step is *literally* `grep -rn "old-keyword" src/ test/`. Tests are the
  most-error-prone surface because they reference data shape explicitly.

---

## Part 2 — Claude's Mistakes (Detailed Reports)

Each entry: what was wrong, *why* I made the mistake, what the fix was, and
what the skill should have prevented or surfaced.

### Mistake C1 — Suggested `:ident` on Root component

- **Phase:** 2
- **Skill tagged:** `fulcro`
- **What I said:** "Option A (faithful to the helpers): give Root an ident.
  This is the more idiomatic move."
- **What was wrong:** Fulcro explicitly forbids idents on Root. The framework
  raises FATAL at mount time. I framed an outright illegal pattern as "the
  more idiomatic move."
- **Why I made it:** I was pattern-matching the "constant ident" idea from
  general Fulcro patterns without remembering the Root-is-special restriction.
- **Correct answer:** Introduce an intermediate `TodoList` component between
  Root and the inputs. Root composes TodoList; TodoList has the ident, owns
  the new-todo-text and the list.
- **What the skill should say:** The `fulcro` skill should have a
  *prominent* "Root is special — restrictions" callout. Currently the
  skill discusses normalization and idents in general but doesn't carve out
  the Root constraint. A one-line "If you want state, you're not on Root"
  rule would have prevented this.

### Mistake C2 — Recommended `[]` arglist for parameterless mutations

- **Phase:** 3 (and again briefly in 5H)
- **Skill tagged:** `fulcro` (defmutation)
- **What I said:** Showed `(defmutation delete-all [] ...)` as a valid
  no-args mutation.
- **What was wrong:** Fulcro's `defmutation` enforces the params-map
  convention. The arglist must be exactly one element. Should be `[_]`.
- **Why I made it:** Pattern-matched to `defn`'s convention where `[]`
  means "no args." Forgot that mutations are EQL-serialized and need a
  consistent shape.
- **Correct answer:** `(defmutation delete-all [_] ...)` — the underscore
  signals "ignore this param".
- **What the skill should say:** The `fulcro` skill / defmutation section
  should call out the `[_]`-not-`[]` rule explicitly. The current skill
  discusses defmutation but doesn't make this single-element-arglist
  constraint visible at a glance.

### Mistake C3 — Pathom `parent-query` pattern without the required plugin

- **Phase:** 5F
- **Skill tagged:** `pathom` (resolver-patterns.md)
- **What I said:** Use `(-> env ::p/parent-query meta :params)` to read query
  params in a resolver.
- **What was wrong:** That pattern requires a `query-params-to-env-plugin`
  to lift params from the AST into a known location. Without the plugin, the
  expression returns `nil`. We hadn't installed the plugin.
- **Why I made it:** The Pathom skill's `resolver-patterns.md` shows this
  pattern as the example for "Accessing Query Parameters." I copied the
  shape without checking whether the prereq (plugin) was present in our
  project.
- **Correct answer:** Without the plugin, use `(-> env :ast :params)` to read
  params directly from the AST node attached to the current resolver's env.
  With the plugin, use `(:query-params env)`.
- **What the skill should say:** `pathom/resources/resolver-patterns.md`
  should split "Accessing Query Parameters" into three labeled patterns:
    1. **Direct AST access** `(-> env :ast :params)` — works without plugin
    2. **Via query-params plugin** `(:query-params env)` — requires plugin (show plugin def)
    3. **Parent-query metadata** — legacy/plugin-dependent, mention briefly
       Each labeled with "works without plugin" or "requires plugin X" prominently.

### Mistake C4 — Test fixtures hand-built framework env, no helper

- **Phase:** 5F (with Avi's prompting in Phase 5G to refactor)
- **Skill tagged:** `fulcro-spec-tdd`, `pathom`
- **What I said:** Wrote test fixtures inline:
  `{::p/parent-query (with-meta [] {:params ...})}`
- **What was wrong:** Each spec hand-rolled the env shape. When the
  resolver's contract drifted (we changed where it read params from), every
  test fixture had to change in lockstep. A tiny shape change cascaded into
  ~6 simultaneous edits across tests.
- **Why I made it:** I optimized for line count in the first draft, putting
  fixtures inline for "readability." That's the wrong trade for
  framework-shaped data: insulation matters more than locality.
- **Correct answer:** Build `(defn test-env [params] {:ast {:params params}})`
  early. Every spec calls `(test-env {:done? true})`. When the framework
  shape changes, *one* function updates.
- **What the skill should say:** `fulcro-spec-tdd` already has the "testability
  as design" framing; it should explicitly add a "helper threshold" rule:
  > "If three or more specs construct the same framework-shaped value
  > (an env, request, state map), extract a helper *immediately*. Do
  > not 'add it later'; framework contracts drift and you'll do every
  > test in lockstep."

### Mistake C5 — Recommended Guardrails 1.2.9 without version compatibility check

- **Phase:** 5I
- **Skill tagged:** `deps-upgrade`, `fulcro-spec-tdd`, `guardrails`
- **What I said:** Pinned Guardrails to `1.2.9` in the schema artifact.
- **What was wrong:** `1.2.9`'s `gr.externs` namespace doesn't have the
  `transitive-calls` var that fulcro-spec 3.2.8 tries to call. Adding
  Guardrails 1.2.9 with fulcro-spec 3.2.8 produces a load-time error that's
  *not* present when either alone is on the classpath.
- **Why I made it:** Arbitrary version pick. I didn't consult the
  fulcro-spec-tdd skill (which mandates 1.2.16), didn't check RAD's deps,
  didn't search Clojars for compatible combinations.
- **Correct answer:** `1.2.16` is what the fulcro-spec-tdd skill mandates
  for Fulcro 3.9.0+ / fulcro-spec 3.2.0+. RAD's deps.edn is the canonical
  source of truth for known-working combinations in the Tony Kay family.
- **What the skill should say:** The `guardrails` and `fulcro-spec-tdd`
  skills both touch on versions. They should share a single canonical
  paragraph: **"When adding any `com.fulcrologic/*` family library to an
  existing project, check RAD's current `deps.edn` for the version it uses
  with its companion libraries. RAD's combination is battle-tested."**
  And: explicit cross-version compatibility for Guardrails ↔ fulcro-spec
  (1.2.16 ↔ 3.2.0+).

### Mistake C6 — `>def` called with 3 args (docstring slot)

- **Phase:** 5I
- **Skill tagged:** `guardrails`
- **What I said:** Wrote 11 `>def` forms with a docstring between the
  keyword and the schema:
  ```clojure
  (>def ::status
    "Schema for a single status value."  ;; ← invalid 2nd arg
    (into [:enum] status-values))
  ```
- **What was wrong:** `>def` is strictly 2-arg: `(>def keyword schema)`.
  No docstring slot, unlike `defn`. Caused `ArityException` at reload time.
- **Why I made it:** Pattern-matched to `defn`'s shape. The skill's
  `>def` examples (`(>def :user/id :uuid)`, `(>def :user/email [:string {:min
  5}])`) all show 2 args, but I didn't notice the consistency and added a
  3rd by analogy with `defn`.
- **Correct answer:** Docstrings move to `;;` comments above each `>def`.
- **What the skill should say:** `guardrails` should have an explicit:
  > "**`>def` is a 2-arg macro.** Unlike `defn`, `>def` does *not* accept
  > a docstring between the keyword and the schema. Documentation for each
  > schema lives in the `;;` comment above the form. Calling
  > `(>def ::foo "doc" schema)` raises `ArityException` at load time."
  A one-paragraph "common mistakes" callout would catch this.

### Mistake C7 — `mr/set-default-registry! gr.reg/registry` → StackOverflowError

- **Phase:** 5I
- **Skill tagged:** `guardrails` (Malli registry integration)
- **What I said:** "The fix is `(mr/set-default-registry! gr.reg/registry)`."
- **What was wrong:** `gr.reg/registry` is itself a composite/dynamic
  structure that references Malli's default registry. Setting it *as* the
  default creates a cycle: default → registry → default → ... When
  `m/validate` traverses the schema graph, the lookup ping-pongs until the
  stack blows.
- **Why I made it:** I gave a guess based on the variable name pattern
  (`gr.reg/registry` "looks right") without consulting Malli's own docs.
  The canonical pattern is on Malli's `reusable-schemas.md` page, which I
  didn't read until after the second failure.
- **Correct answer:**
  ```clojure
  (mr/set-default-registry!
    (mr/composite-registry
      (m/default-schemas)
      (mr/mutable-registry gr.reg/schema-atom)))
  ```
  Bypass the registry wrapper; use the underlying `schema-atom` directly,
  composed with Malli's static built-ins.
- **What the skill should say:** The `guardrails` skill (or a new `malli`
  skill — see Part 3) should have a dedicated **"Bridging the Guardrails
  registry to Malli's default"** section showing the canonical
  composite-registry pattern. The reasoning matters: there's a *reason*
  passing `gr.reg/registry` directly fails. Document that pitfall by name.

### Mistake C8 — Shared REPL form referenced aliases not available in any single ns

- **Phase:** 5F
- **Skill tagged:** `clojure-repl`, `pathom`
- **What I said:** Pasted a REPL form using `server/seed!`, `sut/init`,
  `(snapshot)` without specifying which ns to run from. No single namespace
  in the project had all three aliases.
- **What was wrong:** Avi pasted the form from his current REPL prompt
  (`learn.parser` or similar), hit `No such namespace: server`.
- **Why I made it:** Drafted the form with a "mental REPL" in
  `learn.client-test` in mind, then realized that wouldn't have `snapshot`
  either, but shipped anyway.
- **Correct answer:** Make REPL forms shared with users **self-contained**:
  open with `(require '[ns :as alias] ...)` for every alias used. Or
  prominently mark "**Run from `learn.client-test`**."
- **What the skill should say:** The `clojure-repl` skill is currently
  about *configuring* and *connecting* to REPLs. It should also have a
  short section on **sharing REPL forms with users**: prefer self-contained
  `(do (require ...) ...)` blocks; if the form must run from a specific ns,
  say so before the code.

### Mistake C9 — Forgot to flag parser-reload dependency on resolver edits

- **Phase:** 5F
- **Skill tagged:** `pathom`
- **What I said:** Recommended editing `learn.resolvers`, then re-running
  the test form. Didn't mention reloading `learn.parser`.
- **What was wrong:** `(pc/connect-plugin {::pc/register all-resolvers})`
  runs once at parser-load and captures *value-of-the-var-at-that-moment*.
  Editing `resolvers.clj` updates the var, but the parser still holds the
  old value. Tests proceed against stale code; failures look mysterious.
- **Why I made it:** I know about the connect-plugin caching but didn't
  surface it as a workflow rule when first introducing the parser pattern.
- **Correct answer:** A workflow rule: *edit a resolver → reload the
  parser*. Alternatively, build the parser around a dynamic registry
  pattern (the "auto-registry" approach) so reloads propagate naturally.
- **What the skill should say:** The `pathom` skill's `parser-setup.md`
  should have a "REPL workflow gotcha" callout near the parser definition:
  > "`pc/connect-plugin` captures resolver function values at parser-load
  > time. After editing a resolver, **reload the parser** (or use a
  > dynamic registry pattern). Without this, tests run against stale code
  > and the failure mode is silent (no error, just wrong results)."

### Mistake C10 — Didn't surface that Guardrails is OFF by default

- **Phase:** 5I (Avi caught it himself by asking about deps options)
- **Skill tagged:** `guardrails`
- **What I said:** Recommended adding Guardrails and `>defn`. Didn't lead
  with "by the way, Guardrails is disabled by default — your contracts
  won't actually fire until you set a JVM property."
- **What was wrong:** A naive user could write `>defn` everywhere, see
  tests pass, and be unaware that none of the contracts are validating
  anything. The skill *does* mention this, but down in the configuration
  section. I didn't surface it when introducing `>defn`.
- **Why I made it:** I treated the JVM property as a configuration concern
  rather than a correctness concern.
- **Correct answer:** Whenever introducing `>defn` for the first time in
  a project, flag the enablement immediately:
  > "Guardrails contracts are **off by default**. Set
  > `-Dguardrails.enabled=true` in your `:test` alias's `:jvm-opts`
  > (or pass `-J-Dguardrails.enabled=true` at REPL launch). Without this,
  > `>defn` falls back to plain `defn` and contracts don't fire — even
  > though your code looks like it has them."
- **What the skill should say:** The `guardrails` skill's opening section
  should *lead* with the off-by-default warning, not bury it. A first-time
  reader should see "Guardrails is off by default; here's how to turn it on"
  within the first 100 lines.

### Mistake C11 — Forgot to mention `[com.fulcrologic.fulcro.components]` in test ns when copy-pasting test code

- **Phase:** 5H
- **Skill tagged:** `fulcro-spec-tdd`
- **What I said:** Shipped test code using `comp/transact!`. Flagged the
  missing require *once*. The follow-up patch from Avi still omitted it.
  I had to flag it again.
- **What was wrong:** I caught it the first time but not robustly — should
  have written it into the patch instructions, not as a side remark.
- **Why I made it:** Trusted that "I mentioned it" would carry through.
  Didn't account for the fact that during multi-turn editing, side remarks
  get dropped.
- **Correct answer:** When a code change requires a corresponding `:require`
  update, put the new ns form *in the patch itself*, not in surrounding prose.
- **What the skill should say:** `fulcro-spec-tdd` could mention: "**When
  test code uses `comp/transact!`, `m/set-string!`, or other Fulcro APIs,
  the test ns must `:require` them too.** Cross-check the test's ns form
  against every namespaced symbol used in the body. The most common
  missing requires for Fulcro tests are `[com.fulcrologic.fulcro.components
  :as comp]` and `[com.fulcrologic.fulcro.mutations :as m]`."

---

## Part 3 — Suggested Skills to Add to Claude

This section identifies gaps in the current skill inventory based on the
mistakes above. For each, I describe the proposed skill (or skill update),
its scope, and where it would have prevented a specific mistake.

### Skill Inventory I Verified

Current `/mnt/skills/user/` contains: `clj-stubs`, `clojure`, `clojure-repl`,
`create-trello-card`, `date-time`, `datomic`, `deps-upgrade`,
`eql-processing`, `fulcro`, `fulcro-headless`, `fulcro-i18n`, `fulcro-rad`,
`fulcro-rad-reports`, `fulcro-spec-tdd`, `fulcro-uism`, `guardrails`,
`io-layer-testing`, `macos-sandboxing`, `pathom`, `seed-data`,
`statechart`, `tufte`.

There is no standalone `malli` skill. Malli is referenced inside
`guardrails`, but the integration patterns are sparse.

### Proposed Skill 1: `malli` (new skill)

**Why it doesn't yet exist:** Malli is treated as a sub-component of
Guardrails in the current setup. But Malli has its own concerns —
registries, schema authoring patterns, validation outside Guardrails
contracts — that warrant their own treatment.

**Scope:**
- Schema authoring conventions (`:map`, `:vector`, `:enum`, `:and`, `:or`,
  `:fn`, `:tuple`, regex schemas)
- Custom predicates with `:fn` and proper error messages
- The Malli registry model: built-in default, custom registries,
  `mr/composite-registry`, `mr/mutable-registry`, `mr/set-default-registry!`
- **The canonical "reusable schemas" bootstrap pattern** (from Malli's own
  `docs/reusable-schemas.md`) — this is the pattern that resolves Mistake C7.
- When and how to pass `{:registry r}` to `m/validate` / `m/explain` vs.
  setting a global default
- Integration with Guardrails: how `>def` populates Guardrails' registry,
  how to bridge to Malli's default so `m/validate` sees everything
- `m/deref-recursive` and reference schemas (when one schema mentions
  another by keyword)

**Mistakes prevented:**
- C7 (StackOverflowError from naive registry bridging) — directly.
- Future variants: any project mixing user schemas with built-ins via
  global validation will hit this.

**Suggested layout:**
- `SKILL.md` — overview and registry pattern (the most important page)
- `schema-authoring.md` — common patterns for entities, enums, errors
- `registry-integration.md` — the canonical bootstrap, including Guardrails
- `validation-helpers.md` — `valid?` / `explain` / `parse` / `decode`

### Skill Update 1: `guardrails` — three concrete additions

The current `guardrails` skill is comprehensive (848 lines) but the three
mistakes I made (C6, C7, C10) would have been caught by:

**Add: "Common mistakes" callout near the top**
- `>def` is a *2-arg* macro. **No docstring.** Use `;;` comments above.
- Guardrails is **off by default**. Set `-Dguardrails.enabled=true` in
  `:jvm-opts`. Without this, `>defn` no-ops — your contracts don't fire
  even though the code looks like it has them.
- The Guardrails registry is **not** Malli's default registry. To make
  `m/validate` see schemas registered via `>def`, you need a bridge —
  see `malli/registry-integration.md` (or the inline section below).

**Add: "Bridging to Malli's default registry" section**
- Include the canonical pattern: `(mr/set-default-registry! (mr/composite-registry (m/default-schemas) (mr/mutable-registry gr.reg/schema-atom)))`
- Explicitly state why passing `gr.reg/registry` directly causes
  StackOverflow (the cycle), so the next person doesn't recapitulate.

**Add: A versioning compatibility note**
- For an existing fulcro-spec project, the minimum compatible Guardrails
  is 1.2.16 (per fulcro-spec-tdd skill). Cite RAD's deps.edn as the
  canonical source of truth for the Tony Kay library family.
- List known-bad pairings (e.g., Guardrails 1.2.9 + fulcro-spec 3.2.8 →
  `gr.externs/transitive-calls` error).

### Skill Update 2: `pathom` — clarify params access patterns

The current `resolver-patterns.md` (297 lines) shows the `parent-query
meta :params` pattern with the `query-params-to-env-plugin` later. It
doesn't clearly say "you need *both*."

**Add to `resolver-patterns.md`:**

> ### Reading Query Parameters — three patterns, each with prerequisites
>
> **(A) Direct AST access** — works without any plugin
> ```clojure
> (let [params (-> env :ast :params)] ...)
> ```
> Use when: simple projects, learning, or when you want the minimum
> machinery.
>
> **(B) Via `:query-params` env entry** — requires `query-params-to-env-plugin`
> ```clojure
> (let [params (:query-params env)] ...)
> ```
> Use when: production codebase has the plugin installed (most do at
> scale). Cleaner at every call site.
>
> **(C) Via parent-query metadata** — legacy/plugin-dependent
> ```clojure
> (let [params (-> env ::p/parent-query meta :params)] ...)
> ```
> Mention briefly; not recommended for new code.

**Add to `parser-setup.md`:**

> ### REPL workflow gotcha: parser caches resolver references
>
> `(pc/connect-plugin {::pc/register all-resolvers})` runs *once* at
> parser-load time and captures resolver function values into an
> internal index. Editing a resolver does **not** propagate to the
> parser's index. After editing any resolver, mutation, or plugin,
> *reload the parser too* — otherwise tests run against stale code
> with no error message.
>
> Production codebases avoid this by registering resolvers into an
> atom that `connect-plugin` reads dynamically each request.

### Skill Update 3: `fulcro` — Root component restrictions

**Add a callout near the top of `SKILL.md`:**

> ### Root is structural — restrictions
>
> The root component has unique constraints:
> - **No `:ident`.** Fulcro raises FATAL at mount time. Root is
    >   co-located over the entire database; it doesn't *have* an identity
    >   distinct from the database itself.
> - **No interactive state.** If you want a form field, a toggle, or
    >   any other UI state — that's a child component, not Root.
>
> If you're tempted to put state on Root, introduce a thin layer
> (`AppShell`, `TodoList`, etc.) underneath. Root composes; child owns
> state.

**Add to defmutation section:**

> ### Mutation arglist is always one element
>
> `defmutation`'s arglist must have exactly one element (the params map),
> even when ignoring it. Use `[_]`, not `[]`. The single-element shape
> ensures mutations remain EQL-serializable.

### Skill Update 4: `clojure-repl` — sharing REPL forms

**Add a short section:**

> ### Sharing REPL forms with users
>
> When you write a REPL form for someone to run, **make it
> self-contained**. The user may evaluate it from any namespace; an
> alias-dependent form fails opaquely.
>
> **Prefer:**
> ```clojure
> (do
>   (require '[some.ns :as alias1]
>            '[other.ns :as alias2])
>   (alias1/do-thing (alias2/setup))
>   ...)
> ```
>
> **Avoid** (unless you specify the ns in surrounding prose):
> ```clojure
> (alias1/do-thing (alias2/setup))
> ```
>
> If the form *must* run from a specific namespace (e.g., to access
> bare names like `init`), prefix the code block with: "**Run from
> `learn.client-test`.**"

### Skill Update 5: `fulcro-spec-tdd` — helper threshold rule

**Add:**

> ### Helper threshold for framework-shaped fixtures
>
> If three or more specs construct the same framework-shaped value (a
> Pathom env, a Fulcro state map, a Ring request), **extract a helper
> immediately**. Do not "add it later":
>
> ```clojure
> (defn test-env [params]
>   {:ast {:params params}})
> ```
>
> Framework contracts drift. Hand-built fixtures break in lockstep
> when they drift. A helper localizes the change to one definition.

### Proposed Skill 2 (Smaller): `fulcro-ecosystem-versioning`

Or: a section added to `fulcro-spec-tdd` and `guardrails` referencing
each other.

**Scope:** The Tony Kay library family co-evolves but doesn't declare
strict version constraints. Compatibility is empirical.

- Canonical recipe: check RAD's `deps.edn` for the latest known-working
  combination
- Known incompatibilities:
    - Guardrails 1.2.9 ↔ fulcro-spec 3.2.8 → `gr.externs/transitive-calls`
      missing
    - Older Guardrails (`1.1.x`) is paired with older fulcro-spec
      (`3.1.x`); upgrading one without the other produces silent feature loss
- Coordinate-rename traps:
    - `fulcrologic/fulcro-spec` (legacy) vs `com.fulcrologic/fulcro-spec`
      (current) — both can resolve simultaneously and produce overlapping
      namespaces

**Mistake prevented:** C5 (version conflict from arbitrary pick).

### Skill Update 6: `deps-upgrade` — apply existing skill to Tony Kay family

The `deps-upgrade` skill exists for general dependency maintenance. Its
recipes (antq, test before and after, clean git tree for rollback) apply
to the Tony Kay family, but the skill doesn't mention the family-specific
considerations:

**Add a note:**

> ### Special case: `com.fulcrologic/*` library family
>
> Tony Kay's libraries (Fulcro, Guardrails, fulcro-spec, RAD,
> statecharts, fulcro-i18n) co-evolve. They depend on each other at
> specific point releases but don't declare strict
> compatibility constraints. When upgrading any one, check what RAD's
> current `deps.edn` declares — that combination is the
> battle-tested baseline.

---

## Appendix — Cross-reference table

| Mistake # | Triggered by Phase | Domain | Existing skill | Resolution location |
|-----------|--------------------|--------|----------------|---------------------|
| C1 | 2 | Fulcro Root | `fulcro` | Add Root restrictions callout |
| C2 | 3, 5H | Fulcro mutations | `fulcro` | Arglist `[_]` callout |
| C3 | 5F | Pathom params | `pathom/resolver-patterns.md` | Three-pattern split |
| C4 | 5F | Test hygiene | `fulcro-spec-tdd` | Helper-threshold rule |
| C5 | 5I | Versioning | `guardrails`, `fulcro-spec-tdd` | RAD baseline reference |
| C6 | 5I | Guardrails macro | `guardrails` | `>def` 2-arg constraint |
| C7 | 5I | Malli registry | `guardrails` + new `malli` | Canonical bridge pattern |
| C8 | 5F | REPL workflow | `clojure-repl` | Self-contained form rule |
| C9 | 5F | Pathom REPL | `pathom/parser-setup.md` | Parser-cache gotcha |
| C10 | 5I | Guardrails enablement | `guardrails` | Off-by-default warning |
| C11 | 5H | Test ns requires | `fulcro-spec-tdd` | Cross-check rule |

## How to read this document going forward

When something breaks in this project — or in any future Fulcro/Pathom/
Guardrails work — check whether the failure mode matches one of the
mistakes above. If yes, the fix is already documented. If no, this
document gets a new entry.

Avi's mistakes from Part 1 also serve as drill targets: each is tagged
with a domain. Spending time on a domain's drills should reduce the
frequency of that domain's mistakes.

The skill updates in Part 3 are recommendations to be brought up with
the skill authors (or with Anthropic via thumbs-down feedback when
similar mistakes recur). They are *not* yet implemented — they're a
wishlist for future skill versions.
