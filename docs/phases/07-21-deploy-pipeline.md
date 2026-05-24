# Phase 7.21 — Deploy pipeline + content polish

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

User-driven housekeeping pass before moving on to Phase 8:

- **GitHub Actions workflow** (`.github/workflows/main.yml`):
  - Push to `main` builds + tests + deploys to GitHub Pages.
  - Setup: Java 21 + Clojure CLI + Node 20, cached.
  - `clojure -M:test:cljs -m test-runner` for tests (the `:cljs` alias is needed on the JVM classpath because fulcro-spec's macros pull in `cljs/test.cljc`, which references closure-compiler classes — we exclude the shaded closure-compiler jar in deps.edn for an unrelated shadow-cljs reflect.js bug, so the unshaded jar from shadow-cljs is the working dependency).
  - `npx shadow-cljs release app` for the release build.
  - `actions/upload-pages-artifact@v3` + `actions/deploy-pages@v4` publish `resources/public/`.
  - `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` so `npm install` doesn't pull a 250MB headless Chromium for the snapshot scripts that CI doesn't run.
- **`test/test_runner.clj`** — mirror of the master runner with a proper `(System/exit code)` so CI can fail the job on test failure. Discovers every `*-test` namespace under `test/`.
- **Relative asset paths in `index.html`**: `/css/app.css` → `css/app.css`, `/js/main/main.js` → `js/main/main.js`. Works both at the dev-server root and at the GH Pages subpath (`https://avidrucker.github.io/fulcro-solo-learn/`).
- **OG feature audit** (`/tmp/og-App.js` cross-referenced against our impl): five gaps remain — Import JSON file, Export JSON file, URL-length safeguard, review-state-persistence, online-event listener. First three promoted to ⬜ Planned stories; remaining two demoted to 🆒 Nice-to-have. `S-keyboard-shortcuts` moved to 🆒 (the og never shipped keyboard shortcuts beyond Enter either).
- **`docs/changes.md`** (new): catalogues intentional divergences from the JS port (Add-Item dim-when-blank, header icons hard-disable, batch-text Submit keeps modal open, UUIDs vs integer ids, conflict-detection ignores UUIDs, statechart for the review flow, dual-platform `<body>`+`<main>` theming, shadow-cljs/clj-nrepl toolchain, deterministic statechart tests). Cross-linked from `docs/README.md`.
- **About-modal tech-stack copy**: ReactJS-flavored `info-string-2` swapped for Fulcro 3.9 + Pathom 2 + statecharts + shadow-cljs + Font Awesome + Tachyons.
- **Help-modal GitHub link**: now points at `github.com/avidrucker/fulcro-solo-learn/issues`.

**Required repo settings** (user action, can't be automated by the workflow):
1. Settings → Pages → Build and deployment → Source = **"GitHub Actions"**.
2. Settings → Actions → General → Workflow permissions = **"Read and write permissions"** (or accept the per-job `pages: write` permission already declared in the workflow).
3. Push to `main` triggers deploy automatically; `workflow_dispatch` enables manual runs from the Actions tab.

Master runner: 87 specs / 599 assertions, all green. CLJS: 327 files, 0 warnings.
