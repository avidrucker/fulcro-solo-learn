# Bugs

Active and resolved bugs in the AutoFocus Fulcro port. Format: short
title with a deep-dive section. When a bug is fixed, link the fix
commit.

## Status legend

| Mark | Meaning |
|---|---|
| 🐛 | Open — reproducible, not yet diagnosed |
| 🔍 | Triaged — root cause identified, fix planned |
| 🛠️ | In progress — fix being implemented |
| ✅ | Fixed — link to fix commit |

Bugs are numbered `B-N` in chronological order of discovery, so the
ID stays stable when one is renamed or rescoped.

---

## B-1 — Theme resets to `:theme/light` on page reload

**Status:** ✅ Fixed in Phase 7.10
**Reported:** 2026-05-13 by user
**Related story:** [`S-theme-persist`](./user_stories.md)

### Symptom

Toggle theme to dark, reload the page. The page renders in light theme
again. The user's persisted todo list is restored correctly, but the
theme preference is lost.

### Root cause

`:ui/theme` lives in the Fulcro app state at `[:list/id 1 :ui/theme]`,
**not** in `learn.server/SERVER-DB`. Our Phase 7 persistence
(`learn.util.storage/install-persistence!`) attaches its watch only to
`SERVER-DB`, so theme is never dehydrated. On reload:

1. `init` hydrates `SERVER-DB` from localStorage — todos are restored.
2. Fulcro app is built fresh and `TodoList`'s `:initial-state` runs,
   which sets `:ui/theme :theme/light`.
3. `df/load!` populates `:list/todos` from the hydrated `SERVER-DB`.
   Note that the load **does not** touch `:ui/theme` — it's not part of
   the loaded query.

Result: list restored, theme reset.

### Why other `:ui/*` keys don't have the same problem

| Key | Lives where | Reset on reload? | Correct? |
|---|---|---|---|
| `:ui/theme` | TodoList initial-state | Yes (`:theme/light`) | **No (B-1)** |
| `:ui/open-modal` | TodoList initial-state | Yes (`:none`) | Yes — modal should not be open on first load |
| `:ui/err-msg` | TodoList initial-state | Yes (`nil`) | Yes — stale error should not survive |
| `:ui/new-todo-text` | TodoList initial-state | Yes (`""`) | Debatable — losing typed draft text on reload is a minor UX paper cut, not yet a logged bug |

### Fix sketch

Two options, leaning toward (A):

- **(A) Separate `ui-prefs` storage key.** Extend `learn.util.storage`
  with a second key (e.g. `"autofocus.ui-prefs"`) that holds a small
  map of UI preferences. `install-persistence!` grows a second watch
  on the Fulcro state atom at `[:list/id 1]` that `select-keys`-es a
  whitelist (currently just `:ui/theme`) and writes the map. `init`
  hydrates this slice into the Fulcro state after mount.

- **(B) Put `:ui/theme` in SERVER-DB.** Add `:ui/theme` to
  `learn.server/initial-state`, route reads through the parser. Zero
  new code — falls into the existing watch + hydration. Conceptually
  off — theme isn't a "server" concern — but pragmatic.

I'd write (A): keeps the conceptual separation (server is data; app
state is presentation) clean, and the whitelist gives us a hook for
future preference keys without reshaping the server. (B) would be the
right call if we expected zero growth in the preference set.

### Notes

- Both options are non-invasive — pure additions to the persistence
  layer plus init wiring.
- A small CLJC spec for the slice round-trip (mirroring
  `util.storage-test`) is the obvious test.

### Resolution

Implemented Option (A) in Phase 7.10:
- `learn.util.storage/extract-ui-prefs` + `apply-ui-prefs` (pure CLJC,
  whitelist `:ui/theme`) plus CLJS-only `save-ui-prefs!`,
  `load-ui-prefs!`, and `install-ui-prefs-persistence!` that watches
  the Fulcro state-atom and re-saves only when the extracted slice
  changes (avoids write-storm on every unrelated state edit).
- `learn.client/init` calls `install-ui-prefs-persistence!` after
  `mount!` (Fulcro state-atom only exists post-mount).
- 4 new specs / 19 new assertions cover the pure helpers + slice
  round-trip + whitelist defence-in-depth.

End-to-end verification is browser-manual via the snapshot pair —
the same `--reload` flag we used for the SERVER-DB persistence demo
works here: toggle theme → reload → snapshot shows the toggled
theme.
