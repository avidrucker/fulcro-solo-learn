// @ts-check
// Phase 20b — Playwright config for the AutoFocus Fulcro port.
// Local-dev focused: assumes `npx shadow-cljs watch app` is running on
// :8000 BEFORE `npx playwright test` is invoked. See README.md.
//
// No `webServer` block is configured here because shadow-cljs's watch
// command is long-running (REPL + live reload) and doesn't cleanly fit
// Playwright's start-wait-kill lifecycle. Manual orchestration is
// simpler than a brittle process-management workaround.

const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './',
  testMatch: '*.spec.js',

  // The whole suite is ~10 small specs against a local dev server;
  // fully parallel is fine and keeps iteration fast.
  fullyParallel: true,

  // Be strict in CI; lenient locally. CI=true is a Playwright convention
  // and matches what GitHub Actions / GitLab set automatically.
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,

  reporter: process.env.CI ? 'list' : 'html',

  use: {
    // Shadow-cljs's :dev-http serves files by exact path — `/` doesn't
    // auto-resolve to `/index.html`. So the baseURL points directly at
    // index.html; tests call `page.goto('')` (empty string) to land
    // there, or use absolute paths for anything else.
    baseURL: 'http://localhost:8000/index.html',
    // Headless by default; override per-run with --headed.
    headless: true,
    // Useful when a test fails — opens the trace viewer to inspect
    // network, console, DOM at every step.
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Firefox / WebKit deliberately omitted in this first pass. The
    // a11y assertions are framework-level (axe-core, ARIA semantics)
    // and don't usually diverge by engine. Add a second project later
    // if cross-browser-specific issues surface.
  ],
});
