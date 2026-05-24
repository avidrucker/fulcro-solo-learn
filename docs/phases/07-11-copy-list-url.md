# Phase 7.11 — Wire Copy List URL action

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

The Phase 7.6 Import/Export modal shipped with all four interactive buttons hitting `stub-onclick`. This phase makes Copy List URL real: clicking it writes the share URL to the user's clipboard.

New `learn.util.url-encoding` namespace implements the JS port's three-step recipe (`btoa(encodeURIComponent(JSON.stringify(items)))`). The empty-list case is pinned to the deployed reference fixture (`[] → "JTVCJTVE"` — same value used in `?list=JTVCJTVE` on the deployed JS port). Pure-CLJC: `js-url-encode` matches JS `encodeURIComponent` (unreserved set `-_.~!'()*` + alpha/digit pass through, space → `%20`, everything else UTF-8 %-encoded); `base64-encode` uses `java.util.Base64` on JVM and `js/btoa` in CLJS; `items->json` uses `js/JSON.stringify` in CLJS and a tiny hand-rolled JSON encoder in CLJ (covers our items shape — vectors, maps, keywords, strings, UUIDs).

`learn.client/copy-list-url!` (CLJS-only) reads `window.location` and calls `navigator.clipboard.writeText` on the constructed URL. Best-effort: silently no-ops on non-https/old-browser contexts where the Clipboard API isn't present. `save-modal` grew a `todos` arg and the Copy URL button's `onClick` now invokes `copy-list-url!` with the current snapshot.

5 new specs / 20 new assertions cover each step (base64, URL-encode, JSON), the full chain at the empty-list fixture, and the URL composition. **63 specs / 437 assertions, all green. CLJS: 327 files, 0 warnings.**

Implements **S-copy-list-url** (new story); partially closes **S-import-export** stub.
