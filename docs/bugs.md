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

---

## B-6 — No bottom padding on `<main>`; theme bg cuts off at last line

**Status:** ✅ Fixed in Phase 12.1
**Reported:** 2026-05-12 by user (visual snapshot inspection vs the OG)

### Symptom

When the todo list overflows the viewport (lots of items, or
zoomed-in browser) and the user scrolls to the bottom, the last
line of content sits right at the bottom edge of `<main>` with no
breathing room. In dark mode this is especially noticeable because
the theme background also stops there, exposing a hint of the
canvas background past `<main>`'s edge.

### Root cause

`<main>` had no bottom padding. The flex column laid out the
header + `.app-container` flush, with nothing trailing.

### Resolution

Added `pb4` (Tachyons `padding-bottom: 2rem`) to `<main>`'s class
string in `learn.client.ui.components/Root`. Goes on `<main>` (not
on `.app-container`) so the theme background carries through the
padding zone — the canvas-bg-leak issue stays fixed.

---

## B-9 — Stale error message persists when a menu modal opens

**Status:** ✅ Fixed in the same conversation that logged it.
**Reported:** 2026-05-14 by user

### Symptom

When the user triggers a page-level error
(e.g. clicking Add Item with blank input → "New items cannot
be empty…") and then opens a menu modal (Save / Info /
Settings via the header icons), the error message stays
visible behind the modal overlay. The user's mental model:
once they've moved on to interact with the modal, the
previous transient error should clear.

### Root cause

`learn.client.state/set-open-modal*` only mutated
`:ui/open-modal`. `:ui/err-msg` was left untouched by modal
transitions; only explicit `(clear-err!)` calls in TodoList's
action handlers cleared it.

### Fix

`set-open-modal*` now also dissocs `:ui/err-msg` when the new
modal-id is non-`:none`. Closing a modal (transition to
`:none`) is deliberately NOT a clear trigger — if a user
dismisses a modal we don't second-guess whether their
pre-modal error is still relevant. Because `toggle-open-modal*`
delegates to `set-open-modal*`, the toggle path picks the
clear-on-open behaviour up for free.

See `S-modal-open-clears-error` in `user_stories.md` for the
user-facing contract.

---

## B-8 — Error messages aren't translated (mostly English-only)

**Status:** ✅ Fixed in Phase 16 (same conversation that logged it).
The seven actively-surfaced error strings now route through
`(i18n/tr locale :err/<key>)` with full `:en` / `:es` / `:ja`
translations. The unused reserved strings (`max-list-length-err`,
`invalid-query-params-err`, `export-fail-err`) stay in
`learn.ui.strings` as historical artefacts; they're not surfaced
and would be removed when their respective features land (or get
formally cut).
**Reported:** 2026-05-14 by user

### Symptom

Phase 12.4's i18n integration translated buttons, headers,
tooltips, modal body copy, and footer lines — but the error
messages that surface in `:ui/err-msg` are still pulled from
`learn.ui.strings` (English only). When a user has the app in
Spanish or Japanese and an error fires, the rest of the UI is
localized but the error text reads in English.

Phase 15 (`:err/url-too-long`) was the first error key to ship
with full `:en` / `:es` / `:ja` translations; the existing
errors below haven't been migrated:

| Source key in `learn.ui.strings` | Surfaced when |
|---|---|
| `empty-input-err` | Adding a blank-text item |
| `nothing-to-delete-err` | Delete List on an empty list |
| `cannot-take-action-err` | Mark Done with no `:status/ready` item |
| `not-prioritizable-err` | Prioritize on a non-prioritizable list |
| `empty-textarea-err` | Submit on the import textarea when blank |
| `bad-json-import-err` | JSON import — structure invalid |
| `non-json-import-err` | JSON import — file isn't JSON |
| `max-list-length-err` | (OG-port string; unused post-Phase-15) |
| `invalid-query-params-err` | Reserved; not currently surfaced |
| `non-json-import-err` | (dup, already above) |
| `export-fail-err` | Reserved; not currently surfaced |

### Likely fix

For each English string above, add an `:err/<name>` key to the
three `learn.i18n.core/translations` maps, then route the
existing `set-err!` call sites through `(i18n/tr locale
:err/<key>)` instead of `s/<name>`. The call sites are in
`learn.client.ui.components/TodoList` (most page-level errors)
and `learn.client.ui.modals/import-json-file!` (import errors).

`learn.ui.strings` entries for these can either stay as
historical / canonical-English references OR be deleted once
i18n has full coverage. Phase 12.4's docstring on
`learn.ui.strings` already notes the migration intent.

### Scope discipline

Surfacing every error in three languages adds ~30 translation
strings. Probably worth a dedicated mini-phase rather than
folding into Phase 15. Track as `B-8` until promoted into a
phase.

---

## B-7 — Modal-internal textarea bg snaps instantly, doesn't match the new-todo input

**Status:** ✅ Fixed in Phase 12.6 (same commit that logged the bug)
**Reported:** 2026-05-14 by user

### Symptom

The import/export modal's textarea should visually match the
page-level new-todo input:
- **Light mode rest state**: slightly gray (`#eee`) so it
  clearly differentiates from the white modal body.
- **Dark mode rest state**: very dark gray (`#333`) so it
  reads as a distinct input field against the surrounding
  black-90 overlay.
- **Hover / focus end state**: solid white (light) or solid
  black (dark) — visually crisp signal that the field is the
  user's active input.
- **Transition**: smooth `.2s ease-in` fade between rest and
  hover/focus, matching the OG ReactJS app and the page-level
  new-todo input.

In the Fulcro port since Phase 12.5c (the `theme-modal-input-class`
introduction), the textarea has the right rest color (dark-gray
in dark, moon-gray in light) but snaps INSTANTLY to white/black
on hover/focus — no transition. The OG and the new-todo input
both fade smoothly.

**As a user**, when I hover or focus the import/export modal's
textarea, I should see the background color transition smoothly
from its gray rest state to solid white (light mode) or solid
black (dark mode) — matching how the new-todo input on the
main app screen behaves.

### Root cause

`app.css`'s `.hover-bg-light-gray` and `.hover-bg-dark-gray`
rules carry `transition: all .2s ease-in` on the BASE class —
that's the JS port's smooth-fade machinery for the page-level
input. Tachyons' `hover-bg-black` and `hover-bg-white` (which
the current `theme-modal-input-class` uses) don't carry a
`transition` rule, so the bg switch is instantaneous.

We also can't reuse `.hover-bg-light-gray` / `.hover-bg-dark-gray`
directly because their `:hover/:focus` end state is fade-to-
TRANSPARENT (which shows the modal overlay through), not solid
white/black. We need the transition machinery from those classes
PLUS a different end state.

### Fix sketch

Reuse the JS port's `.hover-bg-light-gray` / `.hover-bg-dark-gray`
for rest state + the `.2s ease-in` transition, but override the
`:hover/:focus` end state with a higher-specificity rule keyed on
a new `.modal-input` marker class:

```css
.modal-input.hover-bg-light-gray:hover,
.modal-input.hover-bg-light-gray:focus {
  background-color: white;
}
.modal-input.hover-bg-dark-gray:hover,
.modal-input.hover-bg-dark-gray:focus {
  background-color: black;
}
```

Then `theme-modal-input-class` becomes:
- Light: `"modal-input black hover-bg-light-gray"`
- Dark:  `"modal-input white hover-bg-dark-gray"`

Specificity wins (`.modal-input.hover-bg-light-gray:hover` is
0,2,1 vs the base rule's 0,1,1), so the modal-input variant ends
at solid white/black while still inheriting the base class's
transition.
