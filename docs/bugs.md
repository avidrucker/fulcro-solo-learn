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

## B-14 — Modal close-gutter button doesn't stretch to page bottom when content overflows

**Status:** ✅ Fixed in Phase 20 prep (DOM-restructure H1)
**Reported:** 2026-05-15 by user

### Symptom

The transparent full-area close button that every dismissible
modal renders (the "click anywhere outside the content area to
close" affordance, from `modal-shell` in
`learn.client.ui.modals`) does NOT consistently extend to the
bottom of the page when the page content extends past the
viewport height. Result: scrolling down past the viewport, the
user can click in the lower portion of the page (below where
the gutter button reaches) and that click does NOT close the
modal — even though the visual overlay (the modal-shell's
`<section>`) appears to cover that area.

### Suspected root cause (not yet confirmed)

`modal-shell`'s outer `<section>` is positioned `absolute
top-0 bottom-0 left-0 right-0`. That anchors it to the
nearest positioned ancestor — `app-container` (set to
`position: relative` in `Root`). The transparent close button
inside it (`min-h-100`) is sized relative to its own parent.

If `app-container`'s height is constrained to viewport height
(via flex layout interacting with `<main>`'s
`min-vh-100`), the absolute overlay also clips at that
boundary. When the list overflows the viewport, the user
scrolls past the overlay's bottom edge and clicks land on
content that's still beneath the modal's z-index but outside
the close button's hit-target.

### Reproduction

1. Add ~20-30 items so the list overflows the viewport.
2. Open Info / Settings / Save modal.
3. Scroll the page so the bottom of the list is visible.
4. Click in the gutter region BELOW where the visible overlay
   appears to end.
5. Expected: modal closes (the overlay is supposed to cover
   that region).
6. Actual: click doesn't close the modal.

### Resolution

Fixed via hypothesis H1 (fragment-root DOM restructure +
modal-shell sizing change), landed across four commits:

- `809721d` — wip: e2e probe infrastructure
  (`modal-overflow.spec.js` as a behaviour-level red test,
  plus geometry / visual probes), local Tachyons + Pesticide
  for inspection, close-gutter temporarily styled
  `bg-blue` to make coverage observable in the browser.
- `58667a9` — experiment H1: `Root` returns a
  `comp/fragment` (skip-link + wrapper-div containing
  `<header>` and `<main>` as siblings); `<main>` becomes the
  positioned ancestor for `modal-shell` and uses
  `flex-auto` (not `flex-1`) so it grows with content rather
  than capping at basis-0. `modal-shell`'s outer
  `top-0 bottom-0 left-0 right-0` swapped for
  `top-0 left-0 right-0 min-h-100` — floors the overlay at
  100% of `<main>` while letting it grow to contain inner
  overflow.
- `9adc520` — merge of H1 into main. Chosen over H2 for
  single-source theme/font, easier extensibility for
  cross-cutting concerns (live regions, app-wide overlays,
  footer), and to avoid `display: contents` indirection.
- `2feab35` — finalise: close-gutter button reverted from
  the dev `bg-blue` probe back to `o-0`; pesticide swapped
  from the depth variant to the vanilla rainbow-outline
  variant, gated behind a `learn.client/debug-css?` toggle
  (default `false`).

Verified by `e2e/modal-overflow.spec.js` (green post-fix) —
scrolling to the bottom of an overflowing list and clicking
in the gutter region below the visible overlay now closes
the modal. Escape-to-close (Phase 19h) continues to work
unchanged.

---

## B-13 — Modal body copy not translated (delete-confirm / review / list-conflict)

**Status:** ✅ Fixed in the same conversation that logged it.
**Reported:** 2026-05-15 by user

### Symptom

After Phase 16 (B-8) closed the page-level error strings, three
modals still rendered visible English body copy / buttons /
tooltips regardless of `:ui/locale`:

- **Delete-confirm modal** — "Are you sure you want to delete
  your list? This action cannot be undone."
- **Review modal** — the prompt template "In this moment, are
  you more ready to '<X>' than '<Y>'?" (parameterized, so
  needs a localized constructor, not just a key lookup).
- **List-conflict modal** — "The link list and local storage
  list do not match. Which will you keep?" plus both list
  labels, the four buttons (Copy / Keep × Link / Local), and
  the four tooltips.

A Spanish or Japanese user opening any of these saw mostly
translated UI with an English island in the middle.

### Root cause

These strings were the last ones still pulled from
`learn.ui.strings` directly (rather than via
`(i18n/tr locale :key)`). Phase 16 deliberately scoped the
migration to err-msg strings; modal body copy was deferred and
this bug formally closes that deferral.

The review prompt was a special case — its English template
was string-concatenated inside
`learn.model.review/current-question` rather than living in a
strings file. Localizing it required refactoring that function
to return DATA (`{:cursor-text :benchmark-text}`) and adding a
new `learn.i18n.core/tr-review-question` to format the data
per-locale.

### Fix

- 12 new i18n keys × 3 locales added to
  `learn.i18n.core/translations`:
    - `:modal/confirm-delete`, `:tooltip/cancel-delete`,
      `:tooltip/confirm-delete`
    - `:tooltip/quit-review`, `:tooltip/review-no`,
      `:tooltip/review-yes`
    - `:conflict/mismatch`, `:conflict/label-link`,
      `:conflict/label-local`
    - `:btn/copy-link-url`, `:btn/copy-local-url`,
      `:btn/keep-link`, `:btn/keep-local`
    - Plus tooltip variants
- New `i18n/tr-review-question` parameterized fn (sibling to
  `tr-list-count` / `tr-next-actionable`). Japanese phrasing
  reverses the order to read naturally ("X yori mo Y wo suru
  junbi…").
- `learn.model.review/current-question` now returns
  `{:cursor-text "..." :benchmark-text "..."}` or nil (was a
  pre-formatted string). The model stays locale-agnostic; the
  UI calls `tr-review-question` to format.
- All three modal call sites in
  `learn.client.ui.modals` / `learn.client.ui.components`
  switched from `s/<name>` to `(i18n/tr locale :<key>)`.

`learn.ui.strings` entries for these strings stay as
historical references — they're no longer the source of
truth for surfacing.

---

## B-12 — Changing language via Settings left `?lang=` in URL, re-firing the conflict modal on reload

**Status:** ✅ Fixed in the same conversation that logged it.
**Reported:** 2026-05-14 by user

### Symptom

Workflow that surfaced the bug:
1. Receive a list URL from someone else carrying `?lang=es`.
2. The Phase 18 locale-conflict modal opens, user picks (or
   the user dismisses by changing language via Settings later).
3. The address bar's `?lang=es` stays in place.
4. On reload, `locale-decision` sees saved-locale ≠ URL-lang
   again and re-opens the conflict modal even though the user
   has already made their explicit choice.

The Phase 18 `keep-locale` mutation tried to fix this by
writing `?lang=<chosen>` to match, but that only handled the
conflict-modal path. A user changing language via the Settings
dropdown (`set-locale`) had no corresponding URL update — so
the URL stayed stale and the modal re-fired on reload.

### Root cause + fix

Two mutations needed parallel CLJS side effects.

Both `set-locale` and `keep-locale` now call
`url-encoding/update-current-url-lang!` with `nil` (strip the
lang param entirely) after the state swap. Reasoning:

- The user has just made an explicit choice. Their preference
  is now in `localStorage :ui/locale`. The URL hint has done
  its job; keeping it around risks future conflict-modal
  re-fires.
- Stripping (rather than rewriting to match) keeps bare share
  URLs locale-neutral. If a user wants their language preference
  embedded in their copy URL, that's the Phase 17
  "Include language in URL" checkbox — opt-in, not automatic.

After this fix the locale-conflict modal fires exactly once
per share URL with a language mismatch. The user's resolution
sticks across reloads.

### Tests

State-level tests for `set-locale*` / `keep-locale*` are
unaffected (the CLJS side effect is browser-only). Manual
verification: change language in Settings, observe URL bar
stripped of `?lang=`; reload, no modal.

---

## B-11 — Empty-vs-non-empty list conflict modal can trigger on refresh

**Status:** ✅ Fixed in the same conversation that logged it.
**Reported:** 2026-05-14 by user

### Symptom

Refreshing the page sometimes produced a list-conflict modal
between an empty list and the user's non-empty list. The
conflict modal is for resolving genuine divergence between
URL-encoded items and localStorage items; an empty-vs-non-empty
mismatch isn't a real conflict — the non-empty list is
unambiguously the one to keep.

### Root cause

`learn.util.url-encoding/decide-initial-list` compared the two
sides via `items-content-shape` then `not=`. When one side was
`[]` and the other had items, the shapes differed, so the
function returned `{:source :conflict}` and the modal opened.

### Fix

`decide-initial-list` now has two priority branches BEFORE the
content-shape comparison: if exactly one side is empty (present
as `[]`) and the other has content (`(seq …)`), the non-empty
side wins automatically (`:source :url` or `:source :local`,
no modal). The "both non-empty and disagreeing" branch
unchanged; both-empty falls through to `:url` (matches the
both-equal case).

Test surface: two existing assertions that asserted the buggy
behavior (`[]` + items → conflict) were flipped to encode the
new contract, plus one new assertion for the both-empty case.

---

## B-10 — Conflict modal button row layout is suboptimal

**Status:** ✅ Fixed in the same conversation that logged it.
**Reported:** 2026-05-14 by user

### Symptom

The list-conflict modal renders its four action buttons
spread out:
- Copy Link URL — directly below the link list preview
- Copy Local URL — directly below the local list preview
- Keep Link / Keep Local — bottom row

### Desired layout

All four buttons grouped at the bottom of the modal in two
rows:
- Row 1: `Copy Link URL` | `Copy Local URL`
- Row 2: `1. Keep Link List` | `2. Keep Local List`

### Where to fix

`learn.client.ui.modals/conflict-modal` — restructure the body:
keep the two list previews stacked (with their labels), then a
two-row footer containing the four buttons.

### Scope

Pure visual / DOM restructure; no test-breaking changes since
the buttons keep their click handlers + accessible names.

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
