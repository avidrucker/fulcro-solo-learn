# Phase 7.5 — About + Help modals

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`info-circle` and `question-circle` SVGs added to `learn.ui.icons`. Two header icon buttons rendered in Root via the new `header-icon-button` helper, which puts the tooltip label in a visually-hidden `<span class="clip">` so `h/click-on-text!` can find the button by its label text in headless mode. About + Help modals defined as small private fns (`about-modal`, `help-modal`) returning `modal-shell` with the appropriate strings from `learn.ui.strings`.

The modals render inside TodoList's fragment via a `case` on `:ui/open-modal` (the value driven by 7.4's mutations). `:on-close` on each modal calls `(set-open-modal {:ui/open-modal :none})` — clicking the transparent background button dismisses.

5 new specs cover: About content visible after click, About content gone after bg-close, Help content visible, About→Help mutex (only one open at a time).

**Bonus runner fix:** the master test runner now uses `:reload-all` on each test namespace, which transitively reloads src namespaces in dependency order. This is the general fix for the client-references-icons / parser-references-resolvers cross-file ref-capture issue. There's some `BUG: Internal error validating ...` malli-registry noise during reload-all that doesn't affect test correctness.

**46 specs / 368 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-about**, **S-help**, and exercises **S-modal-mutex** / **S-modal-bg-close** end-to-end.
