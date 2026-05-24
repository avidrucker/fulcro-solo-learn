# Phase 7.15 — URL encoder OG-compat shape + decoder

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Phase 7.11's encoder dumped our Fulcro shape verbatim — URLs we produced wouldn't decode in the JS port. This phase makes the output cross-compatible:

- `status->og-string` / `og-string->status` — status keyword ↔ lowercase string.
- `items->og-shape` / `og-shape->items` — vector translation. Integer ids derived from list position; UUIDs assigned fresh on decode. `:was` preserved for cancelled items.
- `items->json` now translates to OG shape first. Single-:ready-item fixture pinned to the og's deployed URL fragment `JTVCJTdCJTIyaWQlMjI…JTdEJTVE`.

Decoder added: `base64-decode`, `js-url-decode`, `parse-json-array` (JVM hand-rolled JSON, CLJS `js/JSON.parse`), `url-segment->items` (full round-trip). Corrupt input returns nil at every layer — caller treats as "fall back to seed".

14 specs / 69 assertions (was 5/20). All TDD-built.
