# Phase 7.12 — Delete-list confirmation modal + batch-text import

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Phase 7.12 covers two distinct features that landed under the same phase number — the delete-confirm modal (original) and the batch-text import via the save-modal textarea (followup).

## Delete-list confirmation modal

Phase 7.3 had Delete List empty the list immediately, with a footnote in `user_stories.md` that the JS port shows a confirm modal first. This phase closes that gap.

`:ui/open-modal` grew a fourth value `:delete-confirm`, joining the existing mutex (`:about`, `:help`, `:save`). `submit-delete!` now splits two ways:
- Empty list → still surfaces `nothing-to-delete-err` (no modal for a no-op, matching the JS port).
- Non-empty list → opens `:delete-confirm` via `set-open-modal`. The actual `delete-all` mutation only fires when the user clicks Yes.

New `delete-confirm-modal` helper reuses `modal-shell` (transparent background close = cancel, matching the other modals) and the `review-btn-class` styling for its No/Yes buttons. Body text is `s/confirm-list-delete` (already in strings.cljc since Phase 6.5). Yes calls `delete-all`, closes the modal, clears any prior error, and refocuses the input; No just closes.

3 new specs / 11 new assertions (opens-modal path, empty-list-bypass, Yes commits, No cancels). Two existing specs (`Delete List button` and `Error surfacing — Delete List on empty list`, plus `Prioritize on non-prioritizable list`) were updated for the new two-click flow. **65 specs / 456 assertions, all green. CLJS: 327 files, 0 warnings.**

Implements **S-delete-list-confirm** (new story); updates **S-delete-list** for the two-step flow.

## Followup — Batch import via the save modal textarea

Closes the last stubbed action in the save modal alongside Copy List URL (Phase 7.11). The Submit button now wires through a full TDD-built stack:

- `learn.model.list/import-from-string` — pure CLJC. Mirrors the JS port's `importTasksFromString` (`tasksIO.js`): split on `\n`, drop blank lines, reduce `add-todo` over the rest. Refuses with `:error/empty-import` on all-blank input. Each new todo follows `add-todo`'s status rule fresh per iteration — first non-blank line into an empty list becomes `:ready`, subsequent lines become `:new` (because :ready now exists).
- `learn.client/import-from-text*` — state-helper. Denormalize → model → `norm/sync-items` back. No-op on refusal.
- `learn.client/import-from-text` defmutation with remote (server side `learn.resolvers/import-from-text-mutation`, registered under `'learn.client/import-from-text`).
- UI wiring in `save-modal`: textarea is a controlled input bound to `:ui/textarea-import-text` via `m/set-string!`; Submit handler splits two ways: blank → `set-err! empty-textarea-err`; non-blank → run the mutation, clear textarea, clear err. **Modal stays open** per B-2 fix — see Phase 7.13.
- `:error/empty-import` added to the model schema enum so the `>defn` contract on `import-from-string` accepts the new error shape.

**8 new specs / 36 new assertions** (Layer 1: 8 specs / 20 assertions in `model.list-test`; Layer 2-4: 3 specs / 16 assertions in `client_test`). **69 specs / 498 assertions, all green. CLJS: 327 files, 0 warnings.**

Implements **S-import-batch-text** (new story); partially closes **S-import-export**.
