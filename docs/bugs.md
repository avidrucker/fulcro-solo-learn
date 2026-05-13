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

---

## B-2 — Batch-import Submit closes the save modal

**Status:** ✅ Fixed in Phase 7.12 followup
**Reported:** 2026-05-13 by user
**Related story:** [`S-import-batch-text`](./user_stories.md)

### Symptom

Open the Import/Export modal, paste a list into the textarea, click
Submit. The import works (items get added) but the modal also closes.
The user expected the modal to stay open so they could verify the
import landed, paste a follow-up batch, or continue using other
modal actions.

### Root cause

`submit-import!` (in `learn.client/TodoList`'s let-binding) had a
`(close-current-modal! this)` call in its success path — leftover
from the initial impl where I assumed Add-Item parity ("act + close
+ refocus"). Add Item doesn't have its own modal, so that pattern
doesn't apply here.

### Resolution

Removed the `close-current-modal!` call from `submit-import!`. The
mutation still runs, textarea still clears, prior errors still
clear; only the modal-close step was dropped. Test
`Save modal — batch import textarea flow` updated to assert
`:ui/open-modal => :save` after Submit.

Whether auto-close is ever the right default — and whether to expose
it as a settings preference — is tracked in
`docs/ideas.md#modal-auto-close`.

---

## B-3 — Header menu icons clickable during review / delete-confirm modals

**Status:** ✅ Fixed in Phase 7.14
**Reported:** 2026-05-13 by user

### Symptom

While the prioritization review modal is open (`active?`) or the
delete-confirm modal is open (`:ui/open-modal = :delete-confirm`),
the user can still click the header icon buttons (Import/Export,
About, Help) and open a second modal on top of (or in place of) the
current one. The og JS port disables those icons during these
states — only the theme-toggle (lightbulb) stays clickable.

### Reference (JS port)

From `docs/js_ui_reference.md` line 149:

> All header buttons except Toggle Theme are
> `disabled={isPrioritizing || showingDeleteModal || showingConflictModal}`.
> Toggle Theme is always enabled.

### Resolution

`header-icon-button` grew a `:disabled?` arg. When true, two
mechanisms together cover both real browsers and the headless test
framework:
1. **HTML `:disabled` attribute** — real browsers suppress the click.
2. **Nil onClick** — the headless test framework's `click!` invokes
   `onClick` directly without checking `:disabled`, so we replace
   the handler with `nil` instead.

Root computes the predicate:
```clojure
(or review-active?
    (contains? #{:delete-confirm :conflict} open-modal))
```

`:conflict` is included pre-emptively for Move 2e (conflict modal
landing in the same session). Theme-toggle button is rendered
separately and never receives `:disabled?` — it stays enabled in
all states.

6 new specs / 11 new assertions (3 menu-icons × 1 review-active
case; 1 theme-toggle-still-works during review; 1 menu-icon during
delete-confirm; 1 theme-toggle during delete-confirm).
