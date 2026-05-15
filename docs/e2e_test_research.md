# E2E testing research — synthesis & Phase 20 proposal

Companion to `docs/e2e_tool_research.md` (which surveyed
Playwright / Puppeteer / Selenium in general). This doc folds in
two project-specific reference points the user asked me to review:

1. **fp-autofocus** — earlier TypeScript implementation, `tests/
   index.test.ts`. Mocha + Chai + `mocha-steps`. ~80–90 tests.
2. **pwa-autofocus-app** — earlier ReactJS PWA implementation,
   `tests/` directory. Six bash scripts orchestrating Lighthouse,
   manifest/service-worker validation, and mobile simulation.

The headline: the user has already shipped two prior iterations of
this app and each one staked out a *different* slice of the test
pyramid. The Fulcro port can pull the pattern forward and end up
with a complete pyramid for the first time.

---

## What the two reference repos actually do

### fp-autofocus — `tests/index.test.ts`

- Framework: **Mocha + Chai**, with `mocha-steps` for ordered
  step blocks (shared state across steps).
- Volume: ~80–90 tests across ~900 lines.
- Layering, explicit in section comments:
  1. **Utility tests** — pluralization, validation, numeric
     helpers.
  2. **Review-mode unit tests** — `findFirstMarkable`,
     readiness checks.
  3. **FP tests** — list counting / transformation.
  4. **Focus-mode integration tests** — state transitions.
  5. **E2E tests** — full scenarios (3- to 11-item lists,
     5–25 sequential steps).
  6. **Review-mode integration tests** — multi-step scenarios.
  7. **TODO-list integration tests** — list iteration + CMWTD
     updates.
- Helpers in `af-test-utils.ts`: `makeNewDemoDataOfLength(n)`,
  `SIMenterMarkAndReviewState`, `populateDemoAppByList`,
  `expectMarksString` (visual-comparison helper).
- **No mocks. No DOM. No browser.** Pure functions tested
  directly. The "E2E" framing means "exercises the algorithm
  end-to-end" — not "drives a real browser."

Representative "short" test:
```typescript
it("returns true when -1 argument is passed in", () => {
  expect(isNegOne(-1)).equals(true);
});
```

Representative "long" test (excerpt):
```typescript
describe("E2E test to 'sort' a list of number items from lowest to highest", () => {
  let myApp: IAppData = createBlankData();
  const numberList = ["25","16","104","39","5","86","23","1","105","94","34"];

  step("should confirm N items have been added", () => {
    myApp = populateDemoAppByList(myApp)(numberList);
    expect(myApp.myList.length).equals(numberList.length);
    expectMarksString(myApp)("[ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]");
  });

  step("should confirm that list has been marked in a descending order", () => {
    myApp = SIMenterMarkAndReviewState(myApp)(['y','n','n','y','n','n','y','n','n','n']);
    expectMarksString(myApp)("[o] [o] [ ] [ ] [o] [ ] [ ] [o] [ ] [ ] [ ]");
  });
  // ... 12 more steps
});
```

The "long" testness comes from **sequenced state-machine
simulation**, not from external dependencies.

### pwa-autofocus-app — `tests/`

- Framework: **Bash scripts**.
- No JS test framework. Lighthouse CLI + curl + `chrome --headless`.
- Files (per README's "essential keep" list):
  - `lighthouse-pwa.sh` — PWA Lighthouse audit, output to JSON.
  - `mobile-simulation.sh` — launches Chrome with mobile emulation
    for manual testing.
  - `validate-components.sh` — runtime HTTP checks against
    `localhost:8080`: manifest fields, service worker handlers,
    icon files, offline routes, HTTPS readiness, viewport meta.
  - `debug-environment.sh` — environment troubleshooting.
  - `run-tests.sh` — top-level dispatcher with CLI flags
    (`lighthouse | mobile | components | debug | all`).
- TESTING_GUIDE.md emphasizes manual Chrome DevTools setup
  (Device Toolbar, Network Throttling) alongside the scripts.
- **All tests target the running build artifact**, not the
  source. They validate "does the deployed thing pass PWA /
  Lighthouse checks?" — they don't validate algorithm
  correctness.

The README explicitly does NOT split into fast/slow tiers; the
TESTING_GUIDE doesn't either. The scripts are coarse-grained;
you run them all or one at a time.

---

## Where each repo sits in the test pyramid

```
                                ▲
                                │  This Fulcro port
                                │  └─ fulcro-spec base (123 specs)
                                │  └─ headless integration (client_test.clj)
                                │  └─ NO browser-artifact validation
                                │  └─ NO automated keyboard / focus tests
                                │
        ┌───────────────────────┤
        │                       │
        │                       │  fp-autofocus
        │                       │  └─ Mocha unit + integration + "stepped E2E"
        │                       │  └─ Algorithm-only; no DOM
        │                       │
   pyramid                      │
        │                       │
        │  pwa-autofocus-app    │
        │  └─ Bash + Lighthouse │
        │  └─ Artifact-only;    │
        │     no algorithm      │
        └───────────────────────┘
```

Each prior iteration filled a different layer. The Fulcro port
sits where fp-autofocus did (strong base+middle, weak top), but
with deeper tooling (Fulcro + Pathom + statecharts).

**Important:** the Fulcro port *already has* what `mocha-steps`
provides. fulcro-spec's `(component ...)` blocks with shared
`let` bindings give the same sequenced-state-machine shape.
`client_test.clj` uses it routinely:

```clojure
(component "clicking Toggle Theme during review IS allowed"
  (let [spa (sut/init)
        _   (h/click-on-text! spa "Prioritize")
        _   (h/render-frame! spa)
        theme-before (get-in (app/current-state spa) ...)
        _   (h/click-on-text! spa "Switch to dark mode")
        _   (h/render-frame! spa)
        theme-after  ...]
    (assertions ...)))
```

We don't need a new framework for stepped scenarios. We just
need to write a few more, ideally porting fp-autofocus's
longest E2E case (11-item priority-sort) verbatim against our
model layer.

---

## What this changes about the previous recommendation

`docs/e2e_tool_research.md` recommended Playwright + axe-core
as the starting point. That recommendation is **still right
for the things only a real browser can verify** — focus
management, Escape key, localStorage, `<html lang>` sync,
contrast.

What I'd revise:

1. **Lighthouse + axe as shell scripts is a lower-friction
   starting point than Playwright** for the artifact-validation
   slice. pwa-autofocus-app proves the pattern works at
   < 300 lines of bash. PowerShell equivalents would be ~the
   same. This buys us Section B (Lighthouse, axe, WAVE, PWA
   manifest) from `docs/manual_tests.md` with no Node test
   framework.

2. **Algorithm cross-validation against fp-autofocus is high
   ROI**. The same AutoFocus algorithm, three implementations,
   should produce the same end states on the same scenarios.
   Lifting fp-autofocus's longer E2E specs into our
   `learn.model.list-test` would catch any subtle divergence
   from the canonical algorithm — and act as living
   documentation of the spec.

3. **Playwright moves later in the queue**, not first. Once
   shell-script Lighthouse + algorithm cross-validation are
   in place, the *remaining* gaps are exactly the ones
   Playwright is best for: keyboard sequences, real focus,
   modal-open/close.

---

## Refined pyramid for this codebase

| Tier | Tool | Frequency | What it owns | Status |
|---|---|---|---|---|
| Base | fulcro-spec on JVM | Every save | Pure fns, mutations, state helpers, i18n | ✅ have it (124 specs) |
| Middle (algorithm) | fulcro-spec headless | Every save | Render → click → mutation → state | ✅ have it (`client_test.clj`) |
| Middle (cross-port) | fulcro-spec stepped scenarios mirroring fp-autofocus | Every save | Algorithm fidelity vs OG TypeScript port | ➕ Phase 20a |
| Top (artifact) | Lighthouse + axe via PowerShell scripts | On demand / pre-release / nightly | PWA score, accessibility violations, build integrity | ➕ Phase 20b |
| Top (browser) | Playwright + axe-core programmatic | PR / nightly | Keyboard, focus, Escape, localStorage, `<html lang>` runtime | ➕ Phase 20c (optional / later) |

`scripts/snapshot.mjs` stays where it is — it's a different
concern (compare our render against the deployed OG ReactJS
port for visual parity).

---

## Phase 20 proposal — three sub-phases

### Phase 20a — Algorithm cross-validation against fp-autofocus

**What**: Port the most useful 3–5 of fp-autofocus's E2E
scenarios as fulcro-spec `(component ...)` blocks under
`test/learn/model/`. Use the existing model functions; no UI
involved. The cross-port mapping:

- fp-autofocus `myList` → our `:list/todos`
- fp-autofocus mark string `"[o] [o] [ ] ..."` → our
  `:status/ready` / `:status/new` enum
- fp-autofocus `SIMenterMarkAndReviewState(myApp)(['y','n','n'...])`
  → our `(prioritize-* state ...)` + `(answer-yes / answer-no)`
  helpers
- fp-autofocus `populateDemoAppByList` → our `(add-todo state ...)`
  in a loop

**Why**: catches subtle algorithm divergences (we already log
"divergence from JS source" decisions in `docs/phases.md` —
this turns those into executable assertions).

**Effort**: Small. ~1 day. The algorithm functions exist;
this is just porting test data + assertions.

**Risk**: Low. Tests are pure; can't break the running app.

### Phase 20b — Lighthouse + axe via PowerShell

**What**: Three PowerShell scripts in `scripts/test-e2e/`:

- `lighthouse.ps1` — builds the release (`npx shadow-cljs
  release app`), serves it on port 9630, runs Lighthouse CLI
  against the URL, writes JSON to `reports/lighthouse-
  <date>.json`. CLI flags: `-mode pwa | accessibility | all`.
- `axe.ps1` — same setup + `@axe-core/cli` against each
  modal-open state (URL fragments or page parameters). Writes
  axe JSON.
- `validate-components.ps1` — runtime HTTP checks against the
  served build: manifest, service worker (when we add PWA),
  asset 200s, no `http://` references.

Roughly mirrors pwa-autofocus-app's three keep-scripts. Output
in `reports/` (gitignored except for a summary file).

**Why**: Closes the entire Section B of `docs/manual_tests.md`
without needing Playwright. Lighthouse + axe CLI are
single-binary tools; no test framework needed.

**Effort**: Medium. ~1–2 days. Bulk is in figuring out the
right Lighthouse CLI flags + serving the release build from
shadow-cljs without the watch process attached.

**Risk**: Low. Scripts are external; can't break the app.

**Open question for the user**: PowerShell vs. bash. Windows
is the dev platform per CLAUDE.md, but CI could go either way.
Recommend PowerShell for parity with the dev env; bash as a
parallel implementation only when CI is on Linux.

### Phase 20c (deferred) — Playwright for keyboard / focus

**What**: ~5 specs in `e2e/` directory:

- `a11y.spec.ts` — opens each modal, runs `injectAxe`,
  asserts zero violations. (Overlaps with 20b's axe.ps1 but
  this version asserts on programmatically-driven states the
  shell script can't reach — e.g., "Settings modal with
  Spanish locale active.")
- `keyboard.spec.ts` — full keyboard sweep: tab order
  through all controls, Escape closes dismissible modals,
  focus restoration after close, the 19g review-modal focus.
- `localstorage.spec.ts` — verify `:ui/locale`, `:ui/theme`,
  and `:list/todos` persist across reload.
- `html-lang.spec.ts` — verify `<html lang>` reflects active
  locale across switches.
- `golden-path.spec.ts` — add → prioritize → review (yes/no)
  → mark done → delete list. Mostly a smoke test.

**Why**: covers everything 20a + 20b cannot — real keyboard
event dispatch, real focus tracking, real localStorage.

**Effort**: Larger. ~2–3 days for setup + writing the five
specs. Risk is in flakiness (Fulcro's mutation queue +
statechart timing can produce races).

**Risk**: Medium. Browser e2e is notoriously flaky if not
written carefully. The mitigation is: assert on the state-
atom (via `page.evaluate`) BEFORE asserting on the DOM —
gives you a layered failure ("state was correct but render
lagged" vs. "mutation didn't fire").

**Recommendation**: do this last, or skip if 20a + 20b leave
the manual_tests checklist mostly automated.

---

## Open decisions for the user

Listed here so we can agree on them before starting Phase 20.

1. **Do all three sub-phases, or just 20a + 20b?** 20c earns
   its slot if there are specific gaps left after 20a + 20b
   that genuinely matter. Otherwise skip it — adding a JS
   test framework is a non-trivial dependency-graph and CI
   complication.

2. **PowerShell vs. bash for 20b?** Dev env is Windows
   (PowerShell preferred). CI may not be (bash usually wins
   on Linux runners). Could do both, or pick one and
   convert later.

3. **Algorithm cross-validation scope.** Just the 11-item
   number-sort scenario, or also the hide/unhide, the
   focus-mode cycles, etc.? Recommend starting with the
   number-sort because it has the highest invariant density
   (every step has a known expected state).

4. **Test-data port format.** fp-autofocus uses strings like
   `"[o] [o] [ ] [ ] [o]"`. We could either port that visual
   format as a helper or use vectors of keywords. The visual
   format reads better in test names but adds a tiny parser.
   Recommend: vectors of keywords for the fulcro-spec
   assertions, but include the visual string as a `;;`
   comment above each step so the test is greppable against
   the original.

5. **Reports directory.** Should Lighthouse / axe JSON
   outputs be checked in (track score trends over time), or
   gitignored (avoid noise)? Recommend: gitignore the raw
   JSON, check in a `reports/SUMMARY.md` that lists the
   latest scores + a date.

6. **CI scope.** Run all tiers on every PR, or only the cheap
   ones on PR + the expensive ones nightly? Recommend: PR
   runs Base + Middle (current fulcro-spec suite) + 20b
   shell scripts. Nightly adds 20c Playwright (if shipped).
   Visual regression / snapshot-mjs stays manual.

---

## What this means for Phase 19 right now

Phase 19's manual_tests.md has §19a–§19o checklists. With
Phase 20b's Lighthouse + axe scripts in place, roughly:

- §19a/19d/19e/19g/19h tooltip & aria-label checks → axe
- §19c `<html lang>` runtime → axe (or 20c Playwright)
- §19j contrast → Lighthouse + axe
- §19k role="alert" → axe
- §19n `lang` attrs → axe
- §19i keyboard sweep → 20c Playwright OR stays manual
- §19o skip-link → 20c Playwright (focus on first Tab is
  hard to test from Lighthouse alone)

So Phase 19's QA task (#74) can become *much* shorter once
Phase 20b ships. The user's per-locale screen-reader pass
(NVDA / VoiceOver) still has to happen for high-confidence
verification, but the volume of clicks-to-verify drops.

---

## Summary recommendation

1. **Ship Phase 19** as-is. Run the manual_tests.md pass to
   close the loop on a11y. We're at 124 specs / 829
   assertions; this is a clean stopping point for the
   in-codebase work.

2. **Start Phase 20a (algorithm cross-validation)** next.
   Cheapest, highest-fidelity gain — proves we match the
   reference TypeScript implementation. Fits the existing
   fulcro-spec layer; no new dependencies.

3. **Then Phase 20b (Lighthouse + axe shell scripts)**.
   Automates 70%+ of Section B in manual_tests.md.

4. **Defer Phase 20c (Playwright)** until we know what gaps
   remain. May not be needed at all.

This pyramid stays balanced because the expensive tier
(20b/20c) gets run rarely (pre-release / nightly), while the
cheap tier (existing fulcro-spec + 20a) runs every save.
