# Phase 12 — i18n + visual polish + facade refactor

**Status:** ✅ Complete

Originally scoped as "internationalize via `fulcro-i18n`". On analysis the third-party lib was overkill for three locales and a ~30-key surface — see [`benefits-of-i18n-in-this-project.md`](../benefits-of-i18n-in-this-project.md) for the decision. Phase grew to include the visual-polish work (`i` + gear icon header restructure, modal padding fixes, dark-mode dropdown rendering, modal overlay extent) and a long-overdue `learn.client.cljc` namespace refactor as the work surfaced cross-namespace touchpoints.

**Numbers**: 99 specs / 675 assertions, all green (88 → 99 specs, 614 → 675 assertions). New tests added: i18n core unit specs (4 specs / 21 assertions), TodoList locale propagation, set-locale helper + mutation, Settings dropdown rendering, modal body copy translations.

**Infrastructure notes**:
- `learn.util.storage/ui-prefs-whitelist` extended from `#{:ui/theme}` to `#{:ui/theme :ui/locale}`.
- New `scripts/compare-snapshots.mjs` (one-off diagnostic for the OG-vs-Fulcro visual comparison at small viewport) and `scripts/inspect-heights.mjs` (DOM-height probe used during the overlay-extent debugging).
- `resources/public/sw.js` localhost bypass for `/js/main/*` added separately (paired with the SW diagnosis surfaced during 12.4); committed as a discrete dev-experience fix, documented in [`dev_scripts.md`](../dev_scripts.md).
- New [`dev_scripts.md`](../dev_scripts.md) cheat sheet for the two REPLs (JVM :7888 / shadow-cljs CLJS) and common state-poke recipes — most useful for locale switching before 12.5 landed but reusable for any dev-time inspection.

**Implements**: i18n architecture (decline `fulcro-i18n` in favor of the hand-rolled lookup), language switching UX, the namespace refactor that paid off the technical debt accumulated through Phase 11, and three drive-by visual polish items.

## Sub-phases

- ✅ [12.1 — B-6 modal-bottom-padding fix](12-1-modal-bottom-padding.md)
- ✅ [12.2 — Gear icon](12-2-gear-icon.md)
- ✅ [12.3 — Modal restructure (Info + Settings)](12-3-modal-restructure.md)
- ✅ [12.4 — Hand-rolled i18n core](12-4-i18n-core.md)
- ✅ [12.5 — Language dropdown in Settings](12-5-language-dropdown.md)
- ✅ [12.6 — Documentation sweep](12-6-documentation-sweep.md)
- ✅ [12.7 — `learn.client.cljc` namespace refactor](12-7-namespace-refactor.md)
