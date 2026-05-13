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

---

## B-4 — Conflict modal re-triggers on refresh / back; cancelled rows look uncancelled

**Status:** ✅ Fixed in Phase 7.20 (a follow-up to 7.18)
**Reported:** 2026-05-13 by user
**Related story:** [`S-conflict-modal`](./user_stories.md)

### Symptom (two facets reported together)

1. **Re-trigger on refresh / back.** After resolving a conflict
   (Keep Link or Keep Local), refreshing the page (or hitting the
   back button) immediately re-opens the conflict modal — even
   though the URL and localStorage now contain the same list. The
   two list previews shown in the modal look identical.
2. **Cancelled items appear uncancelled in the modal.** The list
   previews inside the conflict modal don't visually distinguish
   cancelled rows (no strikethrough, no opacity dim), so
   `[a:ready, b:cancelled(was=ready), c:cancelled(was=new)]` looks
   the same as `[a:ready, b:ready, c:new]` at a glance.

### Root cause

**Facet 1**: `learn.util.url-encoding/og-shape->items` assigns
`(random-uuid)` to every decoded item — it can't recover the
original UUIDs because the OG-compatible JSON shape encodes integer
ids derived from list position, not UUIDs. So even when the user-
visible content is identical, localStorage items (with persisted
UUIDs) and URL-decoded items (with fresh UUIDs) never `=`.
`decide-initial-list` was comparing the full item maps with
`(not= local-items url-items)`, so the UUID-only difference looked
like a real divergence → phantom conflict on every reload.

**Facet 2**: `conflict-list-preview` (the read-only list rendering
inside the conflict modal) had the icon-fallback to `:todo/was` but
didn't apply the `strike` text class or `o-50` opacity that
`TodoItem` uses for cancelled / done rows. Visually indistinguishable
from non-cancelled.

Both facets surfaced together because the phantom conflict (facet
1) gave the user repeated chances to study the previews and notice
the cancelled visual was missing (facet 2).

### Resolution

**Facet 1**: `decide-initial-list` now compares via a private
`items-content-shape` projection that strips `:todo/id` and keeps
only user-visible content (`:todo/text`, `:todo/status`,
`:todo/was`). Same-content-different-UUIDs → no conflict.

Tests:
- "B-4 fix — UUIDs differ but content matches → NO conflict"
- "cancelled items with different UUIDs are still semantically equal"
- Regression guard: "differing :todo/was IS a real conflict" — we
  still flag a conflict when the cancel-prior-status differs,
  because that's a real difference in semantic state.

**Facet 2**: `conflict-list-preview` now mirrors `TodoItem`'s
visual treatment:
- Cancelled rows: `strike` text + `o-50` opacity, icon falls back
  to `:todo/was`.
- Done rows: `o-50` opacity.
- Otherwise: normal text.

3 new specs / 6 new assertions on `decide-initial-list`. Cancelled-
visual fix is browser-manual.

---

## B-5 — Deployed app preloads the dev-seed (2 dummy todos) instead of an empty list

**Status:** ✅ Fixed in Phase 7.22
**Reported:** 2026-05-13 by user (visiting the deployed
`https://avidrucker.github.io/fulcro-solo-learn/`)

### Symptom

First-time visitors to the deployed app see two pre-populated todos
("Read the Fulcro book" and "Try out remotes") in their list before
they've added anything. localStorage is empty, the URL has no
`?list=`, but the list still has content. Expected behaviour: an
empty list on first visit.

### Root cause

`learn.server/SERVER-DB` is a `defonce` atom initialized to
`learn.server/initial-state`, which is the *dev seed* the JVM test
suite relies on (two specific UUIDs + texts so spec assertions can
reference them). In the browser, that seed has no business being
the user's starting state — but the CLJS `init` never overrides it.
Flow on first visit:
1. `defonce SERVER-DB (atom initial-state)` — already has 2 todos.
2. `install-persistence!` reads localStorage; nothing there;
   `:hydrated? false`; SERVER-DB stays at the seed.
3. `items-from-current-url` returns nil (no `?list=`).
4. `decide-initial-list nil nil` → `{:source :seed}` → no override.
5. Render: 2 dummy todos.

The bug was latent until deploy because in dev (a) we usually had
localStorage already populated from earlier sessions and (b) the
two demo todos looked plausible enough that they read as a
"default tour content" rather than an obvious problem.

### Resolution

Added `learn.server/empty-state` — same shape as `initial-state`
but with `:list/todos` = `[]` and `:todo/id` = `{}`. The CLJS-only
branch of `learn.client/init` resets SERVER-DB to `empty-state`
BEFORE `install-persistence!` so that, on first-ever visit (no
localStorage, no URL), the user sees an empty list.

JVM `init` is unchanged. The dev seed remains in
`learn.server/initial-state` and `learn.server/seed!` so the spec
suite keeps running with the same fixture. The only difference is
the first-paint state in a real browser.
