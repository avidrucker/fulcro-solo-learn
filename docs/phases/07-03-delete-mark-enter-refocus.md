# Phase 7.3 — Delete List + Mark Done + Enter-to-submit + refocus

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

Two remaining JS-port primary buttons added; the button row is now two `dib` groups matching the JS source (Add/Delete on the left, Prioritize/Mark Done on the right). `submit-add!` / `submit-delete!` / `submit-mark-done!` are inline `let`-bound handlers that wrap `comp/transact!`, with the add/delete variants also calling `focus-new-todo-input!` (a CLJC fn whose `:cljs` branch hits `document.getElementById(...).focus()`; `:clj` no-op).

The form's `onSubmit` routes Enter through `submit-add!`. Action buttons are `type="button"` so clicking them doesn't accidentally submit the form.

The client-side `delete-all` defmutation now has a `(remote [env] (remote-list-items env))` so persistence covers list-clear; a matching `learn.client/delete-all` Pathom mutation was added to `resolvers.cljc` (one-line `record-list-items` like the others).

Two new specs (`Delete List button`, `Mark Done button`) exercise the click-through path on both client and SERVER-DB. Refocus and Enter-to-submit are **browser-manual** — `h/` headless doesn't track focus the way a real browser does, and lacks key-press simulation.

Implements **S-complete-benchmark** (UI), **S-delete-list** (UI + persistence), **S-input-enter-submit** (browser-manual), **S-input-refocus-after-delete** (browser-manual).

**Bonus fix in CLAUDE.md** — the master test runner now does an extra `(require 'learn.parser :reload)` after the main reload loop, because the alphabetical order makes `learn.parser` reload BEFORE `learn.resolvers` — capturing a stale `all-resolvers` snapshot that omits any newly-added Pathom mutation. The second reload fixes this.

**42 specs / 346 assertions, all green. CLJS: 326 files, 0 warnings.**
