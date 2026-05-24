# Phase 7.9 — Error message surfacing

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`:ui/err-msg` (string or nil) lives on `[:list/id 1]`. The `set-err-msg` mutation + `set-err-msg*` state-helper set or clear it. The error renders inside the form, just below the input, with the JS port's classes: `lh-135 red ml-auto mr-auto measure-narrow ma0 pt2`.

Switched the click semantics for Add Item, Delete List, and Mark Done from "disabled when the action can't fire" to the JS port's pattern: the button stays clickable but dimmed; clicking sets the relevant error string. Strings already lived in `learn.ui.strings`:
- `s/empty-input-err` for blank Add Item
- `s/nothing-to-delete-err` for Delete List on an empty list
- `s/cannot-take-action-err` for Mark Done with no `:ready` items

Successful actions clear the prior error. Prioritize keeps its hard `:disabled` when not prioritizable — the JS port has no matching error string for that case.

6 new specs cover state-helper round-trip, the 3 invalid-action error paths, and the clear-on-success path.

**53 specs / 395 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-error-add-blank**, **S-error-delete-empty**, **S-error-mark-done-no-actionable** (new stories).
