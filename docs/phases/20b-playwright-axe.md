# Phase 20b — Playwright + axe-core keyboard a11y scaffold

**Status:** ✅ Complete
**Parent:** [Phase 20 — testing-pyramid fill-in](20-testing-pyramid.md)

New top-level `e2e/` directory. Plain JS, Chromium only, single spec covering things only a real browser can verify: skip-link visibility / Enter behavior (19o), header tab order (19i), modal focus management + Escape-to-close for the four dismissible modals (19g + 19h), `<html lang>` runtime sync (19c), plus axe-core scans at 5 page states (initial + each modal open). 13 test blocks total.

Files: `package.json`, `playwright.config.js`, `keyboard-and-a11y.spec.js`, `.gitignore`, `README.md`. Local-dev focused (no CI yet, no Lighthouse, no golden-path duplication). Assumes `npx shadow-cljs watch app` running on :8000 before `npx playwright test`. Conflict-modal coverage queued as a follow-up (needs URL + localStorage setup).
