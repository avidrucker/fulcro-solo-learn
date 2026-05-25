# Phase 23.1 — Reference-repo reconnaissance

**Status:** ✅ Complete (2026-05-25)

Catalog of idioms in Tony Kay's onboarding-rad-project (branch `origin/10-report-row-actions`) and the companion curriculum-onboarding-rad-project lecture path. Produced by three parallel general-purpose agents (backend / UI / testing-and-quality), each given the same repo paths and asked to catalog patterns with `file:line` references and no recommendations — pure faithful observation.

## Path conventions in this doc

- `obrp:` = `~/Documents/Work/onboarding-rad-project/` (branch `origin/10-report-row-actions`). Read via `git -C ~/Documents/Work/onboarding-rad-project show origin/10-report-row-actions:path` since the branch isn't checked out.
- `curr:` = `~/Documents/Work/curriculum-onboarding-rad-project/` (on `main`, read directly).
- Unprefixed paths are relative to the respective repo root.

---

## 1. Backend / data-modelling idioms

### 1.1 Pathom 2

- **Custom wrapper macros replace `pc/defresolver` / `pc/defmutation`.** Project-local `pm/defresolver` / `pm/defmutation` (aliased from `com.example.lib.pathom.connect-macros`) bundle five behaviours uniformly: self-registration, exception isolation, per-request db-atom refresh, result normalization, and an internal-fn trampoline for live-reload. — `obrp:src/main/com/example/lib/pathom/connect_macros.cljc:23-92`
- **Resolvers self-register into a `defonce` atom registry.** `pathom-registry` + admin-only `admin-pathom-registry` live in `obrp:src/main/com/example/lib/pathom/registry.cljc:6-15`; macros emit `(register! sym)` so the parser never lexically references resolvers.
- **Single `[env params]` arglist convention.** The macro reads positionally — `(first arglist)` is the env binding, `(second arglist)` the params (`obrp:src/main/com/example/lib/pathom/connect_macros.cljc:34-35`). Resolvers always declare two args (e.g. `obrp:src/main/com/example/model/employee.cljc:62`).
- **Body wrapped in `enc/try*`** that logs and returns `nil` — a thrown resolver doesn't kill the parse. — `obrp:src/main/com/example/lib/pathom/connect_macros.cljc:42-46`
- **Result normalization at the wrapper boundary.** `nil → {}`; `sequential? → vec without nils`; otherwise passthrough. — `connect_macros.cljc:53-57`
- **Per-request db atom for read-your-writes consistency.** After every mutation body, the wrapper walks `(do/databases env)` and `reset!`s each schema's atom to the post-transact `(d/db conn)`. — `connect_macros.cljc:47-52`; resolvers read via `current-db` (`obrp:src/main/com/example/lib/pathom/env.clj:5-7`).
- **`__internal-fn__` trampoline.** The real body lives in a generated fn `<sym>__internal-fn__`; the `pc/defresolver` form is a thin shell. Dev can redefine the internal fn without rebuilding the parser. — `connect_macros.cljc:37-61`
- **Parser composition with five plugins.** `pathom/new-parser` takes config + plugin vector + resolver vector. — `obrp:src/main/com/example/components/parser.clj:60-73`
- **`config-plugin` merges config + `rad-env` into every parser call.** — `parser.clj:34-42`
- **`timezone-plugin` wraps each parse in `(dt/with-timezone "America/Los_Angeles" ...)`** and short-circuits empty tx to `{}`. — `parser.clj:44-52`
- **Resolver shape: input as set keyed on entity id; output as vector of qualified keys, optionally with join target.** E.g. `assignee-resolver` `::pc/input #{:equipment/id}` → `[{:equipment/assignee [:employee/id]}]`. — `obrp:src/main/com/example/model/equipment.cljc:67-73`
- **"All-X" entrypoint resolvers** return only the identity key, letting Pathom fan out for scalars: `(pm/defresolver all-employees …)` → `[{:employee/all [:employee/id]}]`. — `obrp:src/main/com/example/model/employee.cljc:62-67`
- **Mutations return ident-bearing key + `:tempids` for upserts.** `save-employee` returns `{:employee/id real-id :tempids {id real-id}}`. — `obrp:src/main/com/example/model/employee.cljc:79-91`
- **`index-explorer` resolver shipped in-tree** for live introspection from the REPL / Pathom Viz. — `parser.clj:26-32`
- **Single shared mutation parameterized by `:return-type`** — caller declares which row class to round-trip; the server can `case` on `(name return-type)` to shape the response. — `obrp:src/main/com/example/model/assignment.cljc:42-48`; rationale `curr:10-report-row-actions/tutorial.md:126-159`

### 1.2 RAD attributes

- **Single namespace per entity, two namespaces per entity-domain.** `model_rad/<entity>.cljc` holds RAD attribute *declarations*; `model/<entity>.cljc` holds Pathom *resolvers/mutations* that act over them. — `obrp:src/main/com/example/model_rad/employee.cljc` vs `obrp:src/main/com/example/model/employee.cljc`
- **Every namespace exports `(def attributes [...])`** aggregated into top-level `all-attributes`. — `obrp:src/main/com/example/model_rad/model.cljc:11-15`
- **`all-attributes` drives four lookup derivations.** `key->attribute`, `id-key->attributes`, `all-attribute-validator`, `rad-env`. — `model.cljc:17-26`
- **Identity vs scalar binding is `ao/identity? true` vs `ao/identities #{:entity/id}`** — the keyword's namespace is just a label, not the binding mechanism. — `obrp:src/main/com/example/model_rad/employee.cljc:7-9` vs `11-15`; framed in `curr:1-RAD-model/tutorial.md:80-92` ("the keyword name is a label; `ao/identities` is the binding").
- **Every storage-backed attribute carries `ao/schema :production`.** Used by Datomic adapter to pick a connection and by `generate-resolvers` to know whether to emit a stored-attribute resolver. — `employee.cljc:9, 14, 20, 26`
- **Closed-set keyword enums** via `ao/enumerated-values` + `ao/enumerated-labels`; validator rejects out-of-set values. — `obrp:src/main/com/example/model_rad/equipment.cljc:14-29`
- **Reference attributes use `:ref` type + `ao/target` + `ao/cardinality :one`.** — `obrp:src/main/com/example/model_rad/assignment.cljc:17-26, 28-40`
- **Virtual reference attributes.** `:equipment/assignee` declared in attribute file with `ro/column-EQL` + `ro/column-formatter` (`model_rad/equipment.cljc:42-49`), with a separate hand-written Pathom resolver supplying data (`model/equipment.cljc:67-73`).
- **`ro/column-EQL` co-located on the attribute** declares the join shape a report should pull. — `model_rad/assignment.cljc:20, 31-33`
- **`ro/column-formatter` co-located on the attribute** — formatters take `[report-instance value row-props column]`. — `model_rad/assignment.cljc:21-23, 34-37, 45-48, 55-58`
- **`ao/style :multiline`** chooses the form-field renderer at attribute level. — `model_rad/assignment.cljc:64`
- **`new-<entity>` constructor helpers** for seed-data — positional fields + variadic `& {:as addl}`, return a map ready for `d/transact`. — `model_rad/employee.cljc:28-33`, `equipment.cljc:51-57`, `assignment.cljc:67-74`

### 1.3 Server composition

- **Mount-managed singleton lifecycle.** Six `defstate`s under `com.example.components.*`, started in require-graph order: `config → automatic-resolvers → datomic-connections → parser → middleware → server`. Lifecycle explained `curr:2-server-composition/tutorial.md:42-58, 142-176`.
- **`development` namespace is the REPL entry-point**, exporting `start` / `stop` / `restart` (the latter calling `clj-reload/reload`). — `obrp:src/main/dev/development.clj:1-13`
- **`dev?` predicate driven by the `-Ddev` JVM property** is the single env switch. — `obrp:src/main/com/example/components/config.clj:9-10`
- **Layered EDN config merged in order `defaults.edn` → `<env>.edn` → `local.edn`** via `enc/nested-merge`; `local.edn` optional and untracked. — `config.clj:39-49`
- **HTTP server is http-kit + Ring defaults.** `wrap-defaults`, `wrap-spa-index`, transit middleware, `wrap-api` chained in `build-middleware`. — `obrp:src/main/com/example/components/ring_middleware.clj:65-83`
- **`wrap-api` is the parser entry-point.** Extracts `:transit-params`, calls `(parser/parser {:ring/request request} query)`. — `ring_middleware.clj:38-45`
- **Save/delete middleware split into per-concern namespaces.** `save_middleware.clj` composes `datomic/wrap-datomic-save` with `r.s.middleware/wrap-rewrite-values`; `delete_middleware.clj` wraps `datomic/wrap-datomic-delete`. — `obrp:src/main/com/example/components/save_middleware.clj:1-8`, `delete_middleware.clj:1-4`
- **`automatic-resolvers` defstate** concatenates RAD-attribute resolvers + Datomic-adapter resolvers. — `obrp:src/main/com/example/components/auto_resolvers.clj:7-12`
- **`all-resolvers` returns `[index-explorer (vals @registry/pathom-registry)]`** — Pathom's normaliser handles the nested vector. — `parser.clj:54-58`
- **CSRF token rendered into the SPA index** + shipped as `fulcro_network_csrf_token` for the client. — `ring_middleware.clj:18-19, 50-58`
- **`mount/stop` only owns the HTTP port.** The `server` defstate is the only one with a `:stop` clause. — `obrp:src/main/com/example/components/server.clj:15-19`

### 1.4 Data model

- **Datomic Cloud client API** (`com.datomic/local` 1.0.291 + `com.fulcrologic/fulcro-rad-datomic` 1.5.5). — `obrp:deps.edn:23-24`
- **`datomic-connections` defstate branches on `dev?`.** Dev path uses in-memory `:datomic-local` client kept alive in `defonce` atom across reloads (`obrp:src/main/com/example/lib/development_db.clj:13-44`); prod path delegates to `datomic/start-databases` (`obrp:src/main/com/example/components/database.clj:14-25`).
- **Schema is generated and ensured from `all-attributes`** via `common/ensure-schema!`. — `database.clj:18-22`. No hand-written Datomic schema EDN.
- **Per-request db atoms keyed by schema** maintained by `datomic/pathom-plugin`, refreshed by the mutation wrapper macro. — `parser.clj:66-67`; reset logic `connect_macros.cljc:47-52`
- **Two read helpers**: `current-db` and `current-conn` extract from `env` via RAD options `do/databases` and `do/connections`. — `obrp:src/main/com/example/lib/pathom/env.clj:5-12`
- **Datomic queries idiomatic to `datomic.client.api`.** `d/q` with pull, `d/datoms`, dynamic clause assembly. — `model/employee.cljc:11-16, 64-67`; `assignment.cljc:12-26`; `equipment.cljc:21-37, 51-62`
- **Mutations write via `(d/transact c {:tx-data [...]})`** with maps combining identity-key + scalar updates. — `model/employee.cljc:30-40, 83-87`
- **Cascading writes.** `deactivate` employee both flips `:employee/inactive?` *and* stamps `:assignment/return-date` on every active assignment in one tx vector. — `model/employee.cljc:30-40`
- **Test database harness.** Per-spec `gensym`'d in-memory Datomic, schema bootstrapped from `all-attributes`. — `obrp:src/test/testdb.clj:1-21`; used by `equipment_test.cljc:5-12`, `assignment_test.cljc:5-12`
- **Seed-data via `new-<entity>` helpers with stable `:db/id` strings** that the tx batch resolves into refs. — `model_rad/employee.cljc:28-33`, `assignment.cljc:67-74` (`:db/id (str employee "-" equipment)` ties an assignment row to its employee/equipment tx-strings)
- **Datomic-flavoured Pathom queries.** `equipment.cljc:21-37` combines two `d/q` queries; `assignment.cljc:12-26` dynamically composes `:in` / `:where` clauses based on optional filters.

---

## 2. UI / client-side idioms

### 2.1 Fulcro UI

- **One namespace per business entity** under `src/main/com/example/ui/`: `employee.cljs`, `equipment.cljs`, `assignment.cljs`, plus `root.cljs` (root + router) and `form_field.cljs` (shared multimethod renderer).
- **Routing is RAD-dynamic-routing.** `defrouter MainRouter` lists every route target in `:router-targets`, with `:always-render-body? true` and a SUI dimmer for the non-routed state. — `obrp:src/main/com/example/ui/root.cljs:31`
- **Top-level menu uses `rroute/route-to!`** (RAD wrapper) with SUI `ui-dropdown` items; busy spinner driven by `::app/active-remotes` queried at Root. — `root.cljs:51-72`
- **Row component as plain `defsc` with keyword-form ident** `:ident :employee/id` and per-row UI state via `:ui/selected?` toggled with `m/toggle!!`. — `obrp:src/main/com/example/ui/employee.cljs:18-35`
- **List screen with constant ident**: `(fn [] [:component/id ::AllEmployees])`, `:initial-state {:employee/all []}` (empty-list placeholder load-bearing). — `employee.cljs:57-66`
- **Deferred-routing `:will-enter` pattern**: returns `(dr/route-deferred ident (fn [] ... (dr/target-ready! ...)))` after kicking off `df/load!`. — `employee.cljs:59-65`
- **Two-mode form route segment**: `:route-segment ["employee" :mode :id]` with `:will-enter` branching on `(= "create" mode)` between a `start-employee-add` mutation and a `df/load!` with `:post-mutation `dr/target-ready`. — `employee.cljs:96-114`
- **`:pre-merge` calls `fs/add-form-config`** so form-state config materializes when data lands. — `employee.cljs:117-118`
- **Save button gated by `fs/dirty-fields`**; mutation passed `:diff dirty-fields`. — `employee.cljs:120-134`
- **Mutations split CLJ/CLJS via reader conditionals in one CLJC**. Server uses `pm/defmutation`; client uses `m/defmutation` with `(remote [env] (m/returning env Row))`. — `obrp:src/main/com/example/model/employee.cljc:18-58, 71-103`
- **Optimistic `action` + `ok-action` pattern** for save: `action` runs `fs/mark-complete*` optimistically, `ok-action` snapshots pristine with `fs/entity->pristine*`. — `employee.cljc:91-103`
- **Client-only mutation `start-employee-add`** uses `merge/merge-component` with `fs/add-form-config` to seed an unsaved entity. — `employee.cljc:106-114`
- **Multimethod-dispatched form-field renderer** keyed off `(ao/type attr)`, `:default` handles strings; `m/set-string!!` + `(fs/mark-complete! {:entity-ident ident :field k})` on every change. — `obrp:src/main/com/example/ui/form_field.cljs:10-30`
- **`:initial-state` at Root seeds router + ui-flags**: `{:ui/ready? false :ui/router {}}`; `init` ends with `(comp/transact! app [(application-ready {})])` to flip the gate. — `root.cljs:47-49`, `obrp:src/main/com/example/client.cljs:35-49`
- **HTML5 history installed before mount**: `(history/install-route-history! app (html5-history))` then `(hist5/restore-route! app LandingPage {})` after `app/mount!`. — `client.cljs:42-48`

### 2.2 RAD UI usage

- **`defsc-form` for CRUD forms**, declaring `fo/id`, `fo/validator`, `fo/attributes`, `fo/route-prefix`. — `obrp:src/main/com/example/ui/equipment.cljs:9-15`, `assignment.cljs:23-43`
- **Form-options for picker fields.** `fo/field-styles {:assignment/employee :pick-one}` + `fo/field-options` pointing at `{po/query .. po/query-key .. po/options-xform ..}`. — `assignment.cljs:14-21, 28-39`
- **Conditional visibility / read-only via predicates.** `fo/fields-visible?` and `fo/read-only-fields` are functions of `this`, hiding `:assignment/return-date` while id is a tempid. — `assignment.cljs:40-46`
- **`defsc-report` everywhere except Employees.** Declares `ro/title`, `ro/source-attribute`, `ro/row-pk`, `ro/columns`, `ro/route`, `ro/run-on-mount?`. — `equipment.cljs:17-33`, `assignment.cljs:48-83`
- **`ro/controls` map for top-of-report widgets.** Action buttons (`:type :button :local? true`) call `form/create!` / `report/run-report!`; filter inputs (`:type :picker`, `:type :boolean`) carry `:onChange` that re-runs the report. — `assignment.cljs:53-76`
- **`ro/control-layout`** separates `:action-buttons [::new ::reload]` from `:inputs [[:employee] [:historical?]]`. — `assignment.cljs:77-78`
- **`ro/form-links` routes cell-clicks to a form** without hand-rolling navigation. — `assignment.cljs:51`
- **Column rendering via `ro/column-formatter` on the attribute itself** (model layer), not the report. — `model_rad/assignment.cljc:21-23, 33-37, 47-49, 56-59`
- **`ro/column-EQL` joins extra props into the per-row query** — e.g. `[:employee/first-name :employee/last-name]` into assignment rows. — `model_rad/assignment.cljc:20, 31-33`
- **`ro/row-actions []` left empty as the branch-10 exercise scaffold.** The student fills in `:label`, `:action`, `:visible?` per-row buttons. — `assignment.cljs:80-82`, `equipment.cljs:30-32`
- **RAD UI install at boot.** `(rad-app/install-ui-controls! app sui/all-controls)` + `(report/install-formatter! app :boolean :affirmation ...)` for cell formatter registration. — `client.cljs:19-21`
- **SPA built with `rad-app/fulcro-rad-app` wrapped in `v18/with-react18`.** — `client.cljs:24-25`
- **Central RAD env aggregator** builds `key->attribute`, `id-key->attributes`, `all-attribute-validator`, reusable `rad-env`. — `model_rad/model.cljc:14-31`

### 2.3 Statecharts / state machines

**Not present in this branch.** Verified via `git grep` over `src/`: no `com.fulcrologic.fulcro.ui-state-machines`, no `com.fulcrologic.statecharts`, no `defstatemachine`.

### 2.4 State organization

- **Application atom held in a dependency-free namespace.** `com.example.application` is just `(defonce SPA (atom nil))`. — `obrp:src/main/com/example/application.cljs`
- **Constant-ident singleton screens use `:component/id`.** `[:component/id ::AllEmployees]`, `[:component/id ::LandingPage]`. — `employee.cljs:65`, `root.cljs:20`
- **Per-entity ident is keyword-form `:entity/id`** leveraging Fulcro's shorthand. — `employee.cljs:25, 95`
- **Lists held as denormalized vector of idents under the screen ident.** `df/load!` targets `[:component/id ::AllEmployees :employee/all]`. — `employee.cljs:54`
- **Top-of-tree query mixes UI flags, router, and Fulcro internals**: `[:ui/ready? {:ui/router (comp/get-query MainRouter)} ::app/active-remotes]`. — `root.cljs:42-44`
- **Form state via `fs/form-config-join` on each form's query**; `:pre-merge` injects config. — `employee.cljs:89, 117`
- **Reports' UI-state is owned by `defsc-report`** — student code never touches `:ui/current-rows` directly.
- **Tempid lifecycle is implicit.** `(tempid/tempid)` at UI on create, `tempid/tempid?` predicates gate field visibility, server returns `{:tempids {tid real-id}}` for Fulcro to remap. — `employee.cljs:99-100`, `assignment.cljs:42-46`, `employee.cljc:75-89`

### 2.5 Styling

Semantic UI classes inline on Fulcro DOM elements (`:.ui.button`, `:.ui.compact.table`, `:.ui.form`, `:.ui.segment`, `:.ui.top.menu`, `:.ui.loader`). SUI React components (`ui-dropdown`, `ui-dropdown-menu`, `ui-dropdown-item`) for the menu. RAD's SUI rendering pack supplies the form/report look (`sui/all-controls`). **No custom CSS, no SCSS, no Tachyons, no styled-components.** Inline `{:style {:color "red"}}` used once on a form-field label for invalid state. **No light/dark theme story; no theme provider.**

### 2.6 i18n

`com.fulcrologic/fulcro-i18n 1.1.2` is declared in `obrp:deps.edn:8` but **not used anywhere in `src/main`**. No `tr`, `tr-unsafe`, `tr-attrs`, no `::i18n/` keys, no `default-locale`, no locale switcher. The only mention is an exercise comment in `model_rad/equipment.cljc:25` prompting the student to consider it. All visible text is English string literals.

---

## 3. Testing / quality / dev workflow

### 3.1 fulcro-spec

- **Tests mirror source layout** under `src/test/com/example/...`, `_test.clj(c)` suffix, one spec file per subject. — `obrp:src/test/com/example/model/assignment_test.cljc`, `model/equipment_test.cljc`, `components/config_test.clj`, `components/ring_middleware_test.clj`
- **Standard import shape**: `:require [fulcro-spec.core :refer [=> assertions component specification when-mocking]]` (+ `=throws=>` / `mock/...` when needed). — `config_test.clj:4`
- **Subject under test aliased as `:as subj`.** — `config_test.clj:3`, `assignment_test.cljc:5`
- **Top-level: `(specification "name" :focus ...)`** with optional `:focus` marker for TDD development (left committed intentionally — `config_test.clj:33`, `assignment_test.cljc:48`, `equipment_test.cljc:34`).
- **Nested grouping via `(component "..." ...)`** (synonymous with `behavior`; choose by context). — `config_test.clj:7-12`
- **Assertion clusters** — single `(assertions "behavior" actual => expected ...)` block; strings alternate with arrow-triples. — `assignment_test.cljc:33-44`
- **Exception assertions** use `=throws=> #"regex"`. — `config_test.clj:42`
- **Mocking with `when-mocking`** — destructured args + `(subj/fn args) => result-expr`; `mock/real-return` to delegate, `mock/call-of` to introspect. — `config_test.clj:52-65`
- **Setup → Run → Assert structure** with whitespace separation; integration tests open with one `let` of seed UUIDs (`new-uuid n` for determinism), one `d/transact`, then per-scenario `component` blocks. — `assignment_test.cljc:13-33`

### 3.2 Guardrails

- **Plumbed but unused.** `git grep` shows no `>defn`, `>defn-`, `>def` anywhere in `src/main` on this branch. The lib is just a `deps.edn:13` line.
- **`guardrails.edn`** (root) — dev/runtime config: `{:throw? false :emit-spec? true}` with Expound — silent contracts in dev.
- **`guardrails-test.edn`** — test-time config: `{:throw? true :emit-spec? true}` — contracts fail loudly under tests.
- **CLJS build wiring** in `shadow-cljs.edn:24-28, 37-40` threads guardrails into `:test` + `:ci-tests` via `:compiler-options {:external-config {:guardrails {...}}}`.
- **Known regression**: `:main` (`:dev`) build's `:compiler-options` is empty (`shadow-cljs.edn:6`), suppressing the "GUARDRAILS IS ENABLED" startup banner. Tracked as Bug 5.3 / 10.3 in `curr:bug_fixes.md:153, 598`.

### 3.3 Test types & strategy

- **Two units, two integration specs, zero browser-driven tests.** All JVM-side.
- **Integration fixture** = `obrp:src/test/testdb.clj`: a `defonce test-client` (one Datomic-Local client per JVM) + `(test-connection)` that gensyms a fresh db-name, creates the db, connects, pre-applies RAD schema via `common/ensure-schema!` walking `r.model/all-attributes`. Every call → brand-new in-memory DB with full schema (`testdb.clj:6-19`).
- **Seed via RAD model_rad constructors** `new-assignment`, `new-employee`, `new-equipment` + `new-uuid n` for stable IDs. — `assignment_test.cljc:21-31`
- **No Playwright, no Cypress, no Karma e2e.** The only browser-build is `:ci-tests {:target :karma ...}` which is a *CLJS-test* runner, not a browser-driver. — `shadow-cljs.edn:35-43`
- **No CLJS specs exist** in this branch despite the `-spec$` regex.
- **Curriculum acknowledges UI tests are skipped**: *"Exercises 8, 11, 12, 13 are UI changes. fulcro-spec can test components but the setup is heavier… manual browser verification (per the runbook) is faster for those"* — `curr:8-assignment-support/answers.md:951`.

### 3.4 Dev workflow

- **`src/dev/development.clj`** is the dev entry namespace: `(start)` / `(stop)` / `(restart)` over Mount + `clj-reload`. — `obrp:src/dev/development.clj:1-15`
- **`src/dev/user.clj`** runs at REPL start: initializes `clj-reload` with `{:no-reload '#{development} :dirs ["src/dev" "src/main" "src/test"]}`, sets `rad.dev` system property, disables locals-clearing, stages a Portal tap-target in a `(comment …)` block. — `user.clj:1-15`
- **Canonical dev model = merged JVM.** Terminal 1: `clojure -J-Ddev -M:dev:cljs:test -m shadow.cljs.devtools.cli watch main` (CLJS compile + JVM-side dev state + nREPL on 9000). Terminal 2: thin nREPL client. — `shadow-cljs.edn:2` `:nrepl {:port 9000}`; walked in `curr:terminal-setup.md:82-95` and every branch runbook.
- **REPL TDD loop**: `(require '[fulcro-spec.reporters.repl])` + require test ns + `(fulcro-spec.reporters.repl/run-tests)`. Fast, no JVM startup. — `curr:8-assignment-support/runbook.md:384-388`
- **`(restart)` calls `(stop) → (reload/reload) → (start)`** — dev DB lives in `lib/development-db.clj`'s `defonce development-connections` atom, surviving `restart` but not JVM restart. — `obrp:src/main/com/example/lib/development_db.clj:1-7`
- **Dev-only deps** (`scope-capture`, `portal`, `clj-reload`) isolated under `:dev` alias in `deps.edn`.

### 3.5 Build config

- **`deps.edn` aliases**: `:test` (+ `src/test`, `fulcrologic/fulcro-spec 3.1.12`), `:cljs` (CLJS compiler + shadow + devtools), `:run-tests` (`-m kaocha.runner`), `:dev` (`src/dev`, `scope-capture`, `djblue/portal`, `clj-reload`). Source = `src/main` + `resources`.
- **`shadow-cljs.edn`** — three builds: `:main` (browser, `:after-load com.example.client/refresh`), `:test` (`:target :browser-test`, `:ns-regexp "-spec$"`, `:static-fns false` for mocking), `:ci-tests` (`:target :karma`, same regex/flags). — `shadow-cljs.edn:5-43`
- **`tests.edn`** (Kaocha) — JVM-only: `:kaocha/ns-patterns ["-test$"]`, sources `src/main`, tests `src/test`, plugins `:filter` + `:capture-output`, `:reporter [kaocha.report/dots]` (fulcro-spec terminal reporter commented out).
- **CI canonical path**: `clojure -M:test:run-tests` (Kaocha). REPL path: `fulcro-spec.reporters.repl/run-tests`. Curriculum frames both as "REPL = fast TDD; Kaocha = canonical CI." — `curr:8-assignment-support/runbook.md:401`
- **`package.json`** is npm-deps-only (`react`, `semantic-ui-react`, `@js-joda/*`, shadow-cljs); empty `scripts: {}`. No npm tasks.
- **Unified logging** via SLF4J → Timbre (`log4j-over-slf4j`, `jul-to-slf4j`, `jcl-over-slf4j`, `slf4j-timbre`). — `deps.edn`

### 3.6 Code quality tooling

- **`.clj-kondo/config.edn`** is a one-liner: `{:lint-as {mount.core/defstate clojure.core/def}}`. No broader rule set, no hooks, no namespace-loader config.
- **No formatter config** (no `.cljfmt.edn`, no `.cljstyle`, no `dprint`).
- **No pre-commit hooks, no Husky, no `.github/workflows`, no CI YAML** present in the branch.
- **No `.editorconfig`, no `.gitattributes`** beyond default.
- Curriculum recommends `clj-kondo --lint src/main/...` ad-hoc but doesn't wire it into the build. — `curr:terminal-setup.md:187`

---

## 4. Cross-cutting themes

### 4.1 Declaration-first data is load-bearing

Attributes are the unit of meaning — schema, validation, form rendering, report columns, formatter functions, and resolver generation all read from one `defattr`. The model never builds an `Employee` class; `ao/identities` is the binding glue and a global `all-attributes` vector is the only aggregation point.

### 4.2 Wrapper macros bundle policy

`pm/defresolver` / `pm/defmutation` silently add registration, error isolation, db-atom maintenance, and result normalization in one shot. The project never writes a resolver without those guarantees. Tony's quote on why (see §5): Pathom's primitives are *minimal* on purpose, and the wrapper macros are where project-wide policy lives.

### 4.3 Read-your-writes is non-negotiable

A Fulcro client can compose `[(create-X) (load-X-roster)]` in one tx and the roster reads the post-create db. The mechanism (`reset!` an atom after each mutation) is small but pervasive.

### 4.4 Manual-then-macro learning arc

Employees (`employee.cljs`) is *deliberately* the long-form CRUD — manual `defsc` for row + list + form, `:pre-merge`, two-branch `:will-enter`, hand-rolled save/cancel. Equipment + Assignment then show the RAD-macro endpoint (`defsc-form` + `defsc-report`). The arc is pedagogical: learn the primitives, *then* let the macros vanish them.

### 4.5 Tests are scarce but high-leverage

Four spec files in branch 10. Two unit, two integration (driving real Datomic-Local). The curriculum explicitly frames them as *contract specification* for query shape, not coverage chasing.

### 4.6 Surprises (things present-but-unused in the reference)

- **No statecharts.** `fulcrologic/fulcro` is a dep; statecharts are not used in any branch.
- **No Guardrails `>defn`.** Plumbed via `guardrails.edn` + shadow-cljs config but zero call-sites in src/main.
- **No i18n.** `fulcro-i18n` is in `deps.edn` but never used.
- **No CSS / theme system.** Pure Semantic UI inline classes; no light/dark, no design tokens.
- **No CI YAML, no formatters, no pre-commit hooks.** Tooling enforcement is doc-driven, not pipeline-driven.

This matters for the 23.2 comparison: fulcro-solo-learn uses all five of these (statecharts in 5K, Guardrails extensively, hand-rolled i18n, Tachyons + theme toggle, Playwright e2e + master JVM runner). Whether those are *additions Tony would endorse at scale* or *over-engineering relative to the curriculum's deliberate minimalism* is the central 23.2 question.

---

## 5. Curriculum doc highlights — Tony's intent in his own words

### On RAD attributes as first-class data, not classes

> "The library we use is **Fulcro RAD** (Rapid Application Development). It has an unusual approach: **there is no `Employee` class.** There's no Datomic-style schema-first definition either. Instead, every individual field — `:employee/first-name`, `:equipment/serial-number`, `:assignment/start-date` — is its own first-class thing called an **attribute**."
>
> "The binding mechanism that makes `:employee/first-name` part of the Employee entity is **the value of `ao/identities`** — *not* the keyword's namespace prefix. […] **the keyword name is a label; `ao/identities` is the binding.**"
>
> — `curr:1-RAD-model/tutorial.md:14-16, 80-92`

### On the wrapper macros — why not use `pc/defresolver` directly

> "Pathom's `pc/defresolver` and `pc/defmutation` are minimal. They don't: Register the resolver with anything (you'd have to thread the var into the parser yourself). Catch exceptions (a thrown resolver kills the parse). Maintain the per-request db atom (a mutation wouldn't trigger Step 7's `reset!`). Normalize the result shape […]. Have an authorization hook. The project wants all five behaviors uniformly. The wrapper macros bundle them."
>
> — `curr:3-pathom/tutorial.md:275-285`

### On read-your-writes as the reason for the per-request db atom

> "Datomic's `d/db` returns a database **value** as of a specific moment. If two resolvers in the same request each called `(d/db conn)` directly, they might see two different snapshots (if a write landed in between). That's surprising and non-deterministic. The fix: each request grabs *one* db value at the start, stores it in an atom, and every resolver derefs that atom. […] **The end result is read-your-writes within a single request.**"
>
> — `curr:3-pathom/tutorial.md:200-234`

### On Mount: what it owns and doesn't

> "Mount **never** sees the require graph. It only sees a flat list of registrations that happen *as a side effect* of Clojure loading each file's body. The require graph *causes* the registrations to happen in dependency order, but Mount is oblivious to *why*. The diagnosis for 'my defstate isn't starting' is **always** 'its namespace wasn't loaded.'"
>
> — `curr:2-server-composition/tutorial.md:180-183`

### On the manual-then-macro arc

> "Branch 6 walked you through the employee side of the app the long way: a plain `defsc` for the row, a plain `defsc` for the report (with manual `:will-enter`, `dr/route-deferred`, deferred load, hand-rolled table render), and another plain `defsc` for the form … Every concept involved was load-bearing — form state, tempids, deferred routing, optimistic mutations, `m/returning`. You wrote a lot of code, and the code taught the underlying primitives."
>
> "The macros buy you brevity but cost you visibility — the `:will-enter` you wrote in branch 6 was right there to read; the RAD-generated one is buried in macro-emitted scaffolding. … That's a fair trade for typical CRUD; for genuinely unusual flows, branch 6's manual style remains the right tool."
>
> — `curr:7-RAD-equipment/tutorial.md:5, 48`

### On tests as contract specification

> "Datomic queries' result shape is notoriously hard to remember — is the return a vector of vectors? A vector of maps? … Tests force the question into concrete terms before you write the query… That assertion fixes both the contract (what the resolver expects) and the implementation target (what your query must produce). The TDD red-green-refactor loop plays naturally with query-writing: red (empty body, failing test), green (a passing query, possibly awkward), refactor… watching the test stay green."
>
> — `curr:8-assignment-support/answers.md:830`

### On the two test runners

> "Both runners work because fulcro-spec `specification` blocks expand into `clojure.test/deftest` forms. The REPL runner is fast and incremental; Kaocha is the canonical CI path."
>
> — `curr:8-assignment-support/runbook.md:401`

---

## Process notes

- Three agents in parallel, all general-purpose (not Explore — Explore is for targeted file lookup, not open-ended cataloging).
- Agent IDs: backend `a82d96e88bd1d9583`, UI `a33daa09fbab2dec3`, testing `a49ed2aecb7f9e087`. Available via `SendMessage` if 23.2 needs follow-up.
- Token usage: ~241K total across three agents.
- Wall-clock: ~16 min (longest agent at 15.7 min; ran concurrently so end-to-end ≈ that).
