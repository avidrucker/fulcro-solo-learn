# Phase 15 — URL-length safeguard (S-max-url-length)

**Status:** ✅ Complete

Closes the last ⬜ on the tracker. When the encoded `?list=` segment would exceed `MAX_URL_LENGTH` (8000, matching the OG), the URL-sync watch freezes the URL at its last fitting value and surfaces `:err/url-too-long`. localStorage continues normally — the user's list keeps growing locally; only URL sharing is paused until the encoded length comes back under limit (e.g. by deleting items, marking done, etc.).

**Divergence from OG** (logged in `docs/changes.md`): the JS port lets the URL grow unbounded and produces unsharable links. We freeze instead — predictable, no broken URLs, error message points the user to manual recovery (Export JSON, paste text elsewhere).

- **`learn.util.url-encoding/MAX_URL_LENGTH`** — 8000-char constant.
- **`learn.util.url-encoding/items-encode-fits?`** — pure predicate. Encodes via the existing `items->base64-url-segment` chain, returns boolean.
- **`learn.util.url-encoding/install-url-sync!`** — extended to a 3-arity. 1-arity (production) injects the default `replace-url-with-items!` setter PLUS an `on-over-limit` callback that swaps the i18n `:err/url-too-long` string into `[:list/id 1 :ui/err-msg]`. 2-arity remains for legacy tests that don't exercise the over-limit branch; 3-arity for tests that do.
- **`:err/url-too-long`** i18n key — added to all three locales (`:en` / `:es` / `:ja`). First fully-localized error string; rest are still English-only (logged as `bugs.md` B-8 for future cleanup).

**Numbers**: 105 → 108 specs / 741 → 749 assertions, all green via fresh JVM (`clojure -M:test:cljs -m test-runner`). New specs: `MAX_URL_LENGTH` constant, `items-encode-fits?` (empty / single-item / 200-item-overflow cases), `install-url-sync!` over-limit branch (url-setter NOT called when over, on-over-limit IS called).

**Implements**: `S-max-url-length` (was the last ⬜).
**Surfaces**: `B-8` for tracking — other error messages remain English-only and would benefit from the same i18n migration.
