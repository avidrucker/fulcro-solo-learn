# Project Phases

Chronological status index for the Fulcro learning project's evolution from a toy todo app to an AutoFocus implementation on a Fulcro/Pathom stack. Full per-phase content lives under [`docs/phases/`](./phases/) — each entry below links to its outline file, which in turn links to sub-phase detail files where applicable.

**Status legend:**
- ✅ Complete
- 🟡 In progress
- ⬜ Pending

For cross-cutting infrastructure (test runner, REPL workflow, deferred mandates, out-of-arc directions), see [`infra-notes.md`](./infra-notes.md).

---

## ✅ [Phase 1 — Single defsc](phases/01-single-defsc.md)

Single Fulcro component (`Todo`). Hand-built initial state, no normalization yet.

## ✅ [Phase 2 — Composition and normalization](phases/02-composition-normalization.md)

Parent component (`TodoList`) querying for child idents. Normalized client DB via `merge/merge-component`; `nsh/dissoc-in` and friends.

## ✅ [Phase 3 — Mutations with `*`-suffix helpers](phases/03-mutations-star-suffix.md)

Pure state-map → state-map helpers (`add-todo*`, `delete-todo*`) wrapped by thin `defmutation` shells. Business logic stays out of mutation bodies.

## ✅ [Phase 4 — Fake remote via `lr/sync-remote`](phases/04-fake-remote.md)

Headless TDD setup with an atom-as-server and a synchronous loopback remote that runs against the parser.

## ✅ [Phase 5 — Pathom 2](phases/05-pathom-2.md)

Hand-rolled `cond` parser replaced with Pathom 2 resolvers + mutations. Plugins: logging (`*debug?*`-gated), error-handling (`Throwable` catch), `elide-not-found`. Sub-phases 5A–5G inline in the outline.

## ✅ [Phase 5H — Schema migration: `:todo/done?` → `:todo/status`](phases/05h-todo-status.md)

Boolean → 4-value enum (`:status/{new,ready,done,cancelled}`) plus `:todo/was` for capturing prior status during cancellation. Touches every layer.

## ✅ [Phase 5I — AutoFocus domain operations](phases/05i-autofocus-domain.md)

Pure AutoFocus domain model in `learn.model.list` with Guardrails `>defn` contracts. Sub-phases 5I.0 schema doc → 5I.6 coverage check.

## ✅ [Phase 5J — Cancel, complete-benchmark, clone](phases/05j-cancel-complete-clone.md)

`cancel-todo`, `complete-benchmark-item`, `clone-todo` model functions, Fulcro mutations that delegate to them, and server-side Pathom mutations so `(remote [_] true)` lights up.

## ✅ [Phase 5K — Prioritize/review flow](phases/05k-prioritize-review.md)

`learn.model.review` plus a Fulcrologic statechart orchestrating the binary review. Statecharts introduced in 5K.4; client wiring in 5K.5 (Cycles A/B/C); server sync in 5K.6.

## ✅ [Phase 6 — shadow-cljs + browser app (no real backend)](phases/06-shadow-cljs.md)

First time the project runs in a real browser. Loopback `parser/handler` reused — just compiled to JS. Phase 6.5 ports the JS port's strings, Tachyons styling, SVG icons, and custom CSS.

## ✅ [Phase 7 — localStorage persistence + UI feature parity](phases/07-persistence-and-features.md)

Lists survive page reload. 22 sub-phases cover storage, modals (About / Help / Save / Delete-confirm / Conflict), theme toggle + persistence, URL-share encoder + sync watch, JSON file IO stubs, PWA service worker, and the GitHub Pages deploy pipeline.

## ✅ Phase 8 — Statecharts in depth (closed as a doc artifact)

The originally-planned "refactor conflict modal into a chart" turned out to be shoe-horning on honest analysis — 2 states + 2 events + 1 implicit guard is a keyword flag with a payload, not a state machine. Closed as [`when-to-statechart.md`](./when-to-statechart.md) — decision criteria for when to reach for a chart, with the review chart (yes) and conflict modal (no) as worked examples.

## ✅ [Phase 9 — Fulcro RAD basics](phases/09-rad-basics.md)

Capped scope: 9.1 (attribute definitions) + 9.2 (replace Add Item input with attribute-driven rendering) + 9.4 (analysis doc). 9.3 (RAD report) skipped deliberately — would have fought our custom per-row rendering for no learning win.

## ✅ Phase 10 — RAD reports and forms (closed as a doc artifact)

Both `defsc-form` and `defsc-report` would be net-negative refactors at this scale. Closed as [`when-to-use-RAD-forms-and-reports.md`](./when-to-use-RAD-forms-and-reports.md) — criteria for when each pays off, with our app's shape as the worked counter-example.

## ✅ Phase 11 — Production Pathom patterns (closed as a doc artifact)

Per-request env, batch resolvers, mutation return values — all zero load-bearing at our atom-as-DB / single-user / in-process scale. Closed as [`when-to-use-pathom-prod-patterns.md`](./when-to-use-pathom-prod-patterns.md). We do ship the two plugins that genuinely pay off (`error-handling-plugin`, `logging-plugin`).

## ✅ [Phase 12 — i18n + visual polish + facade refactor](phases/12-i18n-and-refactor.md)

Hand-rolled i18n (rejected `fulcro-i18n` as overkill for 3 locales / ~30 keys). 7 sub-phases cover B-6 modal-padding fix, gear icon, Info+Settings restructure, i18n core, language dropdown (12.5b/c folded inline), documentation sweep, and the long-overdue `learn.client.cljc` split into 7 focused namespaces.

## ✅ [Phase 13 — Close S-import-export (JSON file import + export)](phases/13-import-export-json.md)

Closes the last 🟡 partial story. JSON file IO via new `learn.util.tasks-io`. Cross-app round-trip with the OG ReactJS port works.

## ✅ [Phase 14 — `?lang=<code>` URL-level locale hint](phases/14-url-lang-param.md)

Query-param entrypoint so publishers can write locale-specific links without breaking the list-share flow. Precedence: `localStorage :ui/locale > URL ?lang= > :en`.

## ✅ [Phase 15 — URL-length safeguard (S-max-url-length)](phases/15-max-url-length.md)

Above `MAX_URL_LENGTH` (8000), URL-sync watch freezes the URL at its last fitting value and surfaces `:err/url-too-long`. localStorage continues normally — only URL sharing is paused.

## ✅ [Phase 16 — Translate error messages (closes B-8)](phases/16-translate-errors.md)

7 error keys × 3 locales (`:en` / `:es` / `:ja`) added to `learn.i18n.core`. Every user-visible string the app shows is now localized.

## ✅ [Phase 17 — Include-language checkbox for Copy List URL](phases/17-share-with-locale.md)

Opt-in default-off `&lang=<code>` stamp on the share URL. Completes the Phase 14 outgoing-share round-trip; respects recipient preferences by default.

## ✅ [Phase 18 — Locale-conflict modal (S-language-conflict-modal)](phases/18-locale-conflict-modal.md)

Non-cancellable resolution modal when saved locale and URL `?lang=` disagree. Completes the Phase 14 / 17 / 18 i18n-URL round-trip (incoming silent-apply, outgoing opt-in stamp, incoming conflict explicit resolution).

## 🟡 [Phase 19 — a11y / Section 508 audit pass](phases/19-a11y-audit.md)

All 16 in-codebase sub-phases (19a–19p) ✅. Yellow flag is for the user-driven Section-B handoff sweep (Lighthouse, axe live-run, NVDA / VoiceOver, keyboard, zoom, reduced-motion, contrast measurement) tracked in [`a11y_audit.md`](./a11y_audit.md) as `S-ux-a11y-review-pass`.

## 🟡 [Phase 20 — testing-pyramid fill-in](phases/20-testing-pyramid.md)

20a (long-form algorithm cross-validation scenarios) ✅, 20b (Playwright + axe-core keyboard a11y scaffold) ✅, **20c deferred** — Lighthouse shell scripts + expanded Playwright coverage (conflict modals, review-modal focus, more locales). Revisit only if 20a/20b leave specific gaps that warrant the additional tooling.

---

## Queued / next

- **`S-dev-mode-toggles`** — designed in [`ideas.md`](./ideas.md) under tag `dev-mode-toggles`; handoff doc on the `handoffs` branch carries the implementation brief. Becomes the next numbered phase when work starts.
