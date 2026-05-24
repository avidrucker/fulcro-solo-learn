# Phase 7.14 — B-3 fix: header menu icons disable during review / delete-confirm

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Per the JS port (`docs/js_ui_reference.md` line 149), Save / About / Help disable when `isPrioritizing || showingDeleteModal || showingConflictModal`; Toggle Theme always enabled.

`header-icon-button` grew `:disabled?`. Root computes the predicate `(or review-active? (contains? #{:delete-confirm :conflict} open-modal))`. `:conflict` included pre-emptively for Phase 7.18.

Belt-and-suspenders: both the HTML `:disabled` attribute AND a nil `onClick` are set when disabled. The attribute covers real browsers (default click semantics); the nil handler covers the headless test framework whose `click!` invokes onClick without checking `:disabled`.

6 new specs / 11 new assertions. Closes **B-3**.
