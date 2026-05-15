# Phase 20b — Playwright + axe-core suite

Browser-driven tests for things the JVM-side fulcro-spec suite can't
verify: real keyboard event dispatch, real focus tracking, real
`<html lang>` sync, automated WCAG rule checks via axe-core.

This directory is **separate** from the Clojure tooling. Node side.

See `../docs/e2e_test_research.md` for the strategy write-up.

## What's covered

One spec file: `keyboard-and-a11y.spec.js`. Roughly:

- **19o** Skip link is off-screen by default; becomes visible on
  first Tab; pressing Enter sends focus to `#main-content`.
- **19i** Header tab order: skip-link → Save → Info → Settings →
  Theme-toggle.
- **19g + 19h** Each dismissible modal (info / settings / save /
  delete-confirm) opens with focus on its heading element, closes
  on Escape, restores focus to the trigger button.
- **axe-core** scans on initial page + each of the four modal-open
  states. Asserts zero WCAG violations.
- **19c** `<html lang>` flips when the user changes locale via the
  Settings dropdown.

**Not covered here**:
- Conflict modals (list-conflict, locale-conflict) — need URL +
  localStorage setup; queue as a follow-up if/when needed.
- Review-modal focus management (19g extension) — needs a list with
  ≥1 `:status/new` after the last `:status/ready` to trigger; same
  follow-up bucket.
- Lighthouse / PWA scoring — separate concern (see
  `docs/e2e_test_research.md` Phase 20b discussion).
- The golden path (add → prioritize → mark done → delete) — already
  covered by `test/learn/client_test.clj` at the data plane.

## Prerequisites

1. **Node.js** installed (any recent LTS).
2. **Shadow-cljs dev server running** at `http://localhost:8000/`.
   In a separate terminal from the project root:
   ```
   npx shadow-cljs watch app
   ```
   Wait for it to print "build successful". The dev server stays up
   until you Ctrl-C it.

## Install

From this `e2e/` directory:

```
npm install
npx playwright install chromium
```

`npm install` pulls in `@playwright/test` + `@axe-core/playwright`
(~150 MB combined, mostly Playwright's own browser machinery).
`playwright install chromium` downloads the Chromium binary
Playwright will drive (~150 MB more).

Both are one-time costs.

## Run

```
npx playwright test
```

Default is headless. Add `--headed` to watch the browser in action:

```
npx playwright test --headed
```

A failing test opens an HTML report by default. To re-open the
last report later:

```
npx playwright show-report
```

## Debugging a failing test

```
npx playwright test --debug
```

Opens Playwright's Inspector — step through the spec line by line,
inspect the DOM, see network activity. Useful when a selector
silently doesn't match what you expect.

## Conventions

- **Plain JS, not TypeScript.** Phase 20 scoping decision — see
  `docs/e2e_test_research.md`.
- **Chromium only** for now. Firefox / WebKit add cost without
  much marginal coverage at this stage; revisit if engine-specific
  bugs surface.
- **Each test starts at `/`** with a fresh page (Playwright's
  default). No state bleeds between tests.
- **Find elements by accessible name** (`getByRole`) wherever
  possible. Doubles as an a11y assertion — if the role/name lookup
  fails, the control isn't properly labeled. Falls back to
  `locator('#stable-id')` only when role-based lookup is ambiguous
  (e.g. the language `<select>`).

## CI

Not wired up. The project doesn't currently have a CI pipeline; if
one is added later, the Playwright config already respects `CI=true`
(retries, single worker, list reporter).
