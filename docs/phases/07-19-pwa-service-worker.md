# Phase 7.19 — PWA service worker + manifest (S-pwa-offline)

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

App is now installable as a PWA and runs offline once the shell has been cached. Adapted from the og JS port's `serviceWorker.js` (avidrucker/pwa-autofocus-app/public/serviceWorker.js), simplified for our single-bundle shadow-cljs output.

Files added under `resources/public/`:
- **`sw.js`** — service worker. Version-bumped cache key (`autofocus-cache-v7.19`) cleared on activate. Pre-caches the shell on install (index.html, offline.html, manifest, CSS, JS, icon, Tachyons CDN, Google Fonts CDN). Network-first for navigations (fall back to cached index → offline.html); cache-first for static assets.
- **`manifest.webmanifest`** — basic PWA manifest with `standalone` display, scope-relative `start_url ./?source=pwa`, SVG icon.
- **`offline.html`** — minimal fallback page.
- **`icon.svg`** — AF-monogram placeholder.
- `scripts/verify-sw.mjs` — Playwright probe that dumps SW registration state + cache contents for browser-manual review.

Index.html got manifest link, theme-color meta, and an inline SW-registration block. Browser-manual verification only — no JVM tests.

Implements **S-pwa-offline** (Planned ⬜ → ✅).
