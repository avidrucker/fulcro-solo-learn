# Phase 7.18 — Conflict-resolution modal (S-conflict-modal)

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

When the page loads with `?list=<encoded>` AND localStorage has saved state AND the two differ, a modal opens auto-magically with both lists side-by-side and four buttons (Copy Link URL, Copy Local URL, Keep Link, Keep Local). User must explicitly choose — no background-click cancel (matches the JS port's "must choose" contract).

Pure decision in `learn.util.url-encoding/decide-initial-list` returns one of `{:source #{:seed :url :local :conflict} …}`. The conflict branch defers SERVER-DB updates and writes a transient stash + opens the modal post-mount.

`storage/install-persistence!` grew a `{:hydrated? bool}` return so init can distinguish "localStorage was present" from "fell back to seed". `:conflict` was already in the menu-disabled predicate from Phase 7.14 (B-3 fix).

UI: `conflict-list-preview` helper renders read-only items with status icons; cancelled rows fall back to `:todo/was`'s icon matching the JS port's `statusToSymbol` recursion. Two mutations: `keep-link-list` (syncs URL items → SERVER-DB via remote) and `keep-local-list` (close modal + force URL bar refresh since the items vector didn't change so `install-url-sync!` wouldn't fire).

4 new specs / 11 new assertions for the state-helpers + 1 spec / 7 assertions for `decide-initial-list`. Master runner: 87 / 596, all green.

Implements **S-conflict-modal** (Planned ⬜ → ✅).
