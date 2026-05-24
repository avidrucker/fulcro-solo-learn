# Phase 7 — localStorage persistence + UI feature parity

**Status:** ✅ Complete

The user's list survives page reloads. `learn.util.storage` watches `SERVER-DB` and dumps it to `js/localStorage` on every change; the CLJS init branch hydrates the atom from storage before `df/load!` runs so the first render shows the persisted state.

Choice (locked in): localStorage over IndexedDB. Sync API matches the project's sync-everything design, the AutoFocus list will never approach the ~5 MB limit, and `pr-str` / `clojure.edn/read-string` round-trip is fewer moving parts than IndexedDB's request-callback dance.

## Sub-phases

- ✅ [7.1 — `learn.util.storage` ns](07-01-storage-ns.md)
- ✅ [7.2 — Wire into init](07-02-wire-into-init.md)
- ✅ [7.3 — Delete List + Mark Done + Enter-to-submit + refocus](07-03-delete-mark-enter-refocus.md)
- ✅ [7.4 — Modal state foundation](07-04-modal-state.md)
- ✅ [7.5 — About + Help modals](07-05-about-help-modals.md)
- ✅ [7.6 — Import/Export modal (stubbed)](07-06-import-export-stub.md)
- ✅ [7.7 — Theme toggle (light/dark)](07-07-theme-toggle.md)
- ✅ [7.8 — Visual comparison vs the deployed reference](07-08-visual-comparison.md)
- ✅ [7.9 — Error message surfacing](07-09-error-surfacing.md)
- ✅ [7.10 — Theme persists across reload (B-1 fix)](07-10-theme-persists.md)
- ✅ [7.11 — Wire Copy List URL action](07-11-copy-list-url.md)
- ✅ [7.12 — Delete-list confirmation modal + batch-text import](07-12-delete-confirm-and-batch-import.md)
- ✅ [7.13 — Visual parity sweep + B-2 fix](07-13-visual-parity-sweep.md)
- ✅ [7.14 — B-3 fix: header menu icons disable during review / delete-confirm](07-14-header-icons-disable.md)
- ✅ [7.15 — URL encoder OG-compat shape + decoder](07-15-url-encoder-og-compat.md)
- ✅ [7.16 — URL sync watch (S-url-sync-current-list)](07-16-url-sync-watch.md)
- ✅ [7.17 — Read `?list=` on page load (S-url-load-on-init)](07-17-url-load-on-init.md)
- ✅ [7.18 — Conflict-resolution modal (S-conflict-modal)](07-18-conflict-modal.md)
- ✅ [7.19 — PWA service worker + manifest (S-pwa-offline)](07-19-pwa-service-worker.md)
- ✅ [7.21 — Deploy pipeline + content polish](07-21-deploy-pipeline.md)
- ✅ [7.22 — B-5 fix: empty initial list for deployed app](07-22-empty-initial-list.md)

> Note: Phase 7.20 is referenced in `bugs.md` as the B-4 fix phase but doesn't have an entry in the original `phases.md`. Surfaced for cross-reference audit.
