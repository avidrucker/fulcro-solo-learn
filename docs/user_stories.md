# User Stories — AutoFocus Fulcro port

Thin index of what the app does, what it should do, and **where to look**
for the BDD-flavored description and the test that proves it. The full
narrative for each story lives in the corresponding fulcro-spec
`specification` / `component` / `behavior` strings — those serve as both
the test name and the user-facing description, so we don't duplicate.

This file is intentionally small. If you need the full acceptance prose,
follow the test pointer; if a story is stubbed, the prose lives here
until a test catches up with it.

## Status legend

| Mark | Meaning |
|---|---|
| ✅ | Functional **and** tested |
| 🟢 | Functional, **not** tested in the spec suite (browser-manual or pure-UX) |
| 🟡 | Stubbed — UI present but the underlying action is a no-op / `console.log` |
| ⬜ | Not yet implemented |

Test pointers use `ns:specification` form, e.g. `client_test:add-todo*`.
"Browser-manual" means the behavior is observed via the dev server +
Playwright snapshot rather than an automated headless spec.

---

## List management

### S-add — Add an item
**Phase:** 5I.4 (model) / 5I.5 (UI) / 7.3 (enter + refocus).
**Status:** ✅
**Tests:**
- `model.list-test`: `add-todo` blank/non-blank, AutoFocus add rule
- `client_test`: `add-todo*`, `add-todo mutation (with :remote true)`

As a user who just thought of a task, I want to add it to my list. Non-blank
text gets a fresh UUID and is appended; status follows the AutoFocus
add rule (`:status/ready` if no ready exists, otherwise `:status/new`).
Blank input is a no-op. After Phase 7.3, pressing **Enter** in the
input also submits, and the input refocuses for consecutive typing.

### S-cancel — Cancel an item
**Phase:** 5J.1 / 5J.4
**Status:** ✅
**Tests:** `model.list-test:cancel-todo`, `client_test:cancel-todo*`, `client_test:cancel-todo mutation`

Cancelling a `:new`/`:ready` item flips it to `:status/cancelled` and
captures `:todo/was`. Cancelling a `:done`/`:cancelled` item is refused
(`:error/cannot-cancel`). Auto-mark fires if the cancelled item was the
sole `:ready`.

### S-clone — Clone an item
**Phase:** 5J.3 / 5J.4
**Status:** ✅
**Tests:** `model.list-test:clone-todo`, `client_test:clone-todo*`, `client_test:clone-todo mutation`

The "Clone" affordance appears on `:done`/`:cancelled` rows (replacing
"Cancel"), copying the item's text into a fresh row. New row's status
follows the AutoFocus add rule, not the source's.

### S-complete-benchmark — Mark Done
**Phase:** 5J.2 (model) / 5J.4 (mutation) / 7.3 (UI button)
**Status:** ✅
**Tests:** `model.list-test:complete-benchmark-item`, `client_test:complete-benchmark-item*`, `client_test:complete-benchmark-item mutation`, `client_test:Mark Done button`

Clicking the "Mark Done" button marks the benchmark (last `:ready`)
`:status/done`. Auto-mark promotes the next `:new` to `:ready` if no
`:ready` remains. The button is dimmed when no actionable items exist.

### S-delete-list — Delete the list
**Phase:** 3 (state-helper) / 7.3 (UI button + server sync)
**Status:** ✅
**Tests:** `client_test:delete-all mutation`, `client_test:Delete List button`

As a user who wants to start over, I want a "Delete List" button that
empties the entire list. The button is dimmed when the list is already
empty. **After deletion, the new-todo input refocuses** so the user can
immediately start typing the replacement list. The refocus piece is
browser-manual — headless doesn't track focus.

In Phase 7.3, the client-side `delete-all` defmutation grew a `(remote
[env] (remote-list-items env))` so persistence reflects deletes (no
ghost items after reload).

> Future: the JS port shows a confirm modal first
> (`confirmListDelete`). Our Phase 7.3 implementation skips the
> confirm modal and acts immediately; the confirm-modal can land in a
> later phase if we want to match the JS UX exactly.

---

## Review / prioritize

### S-prioritize-start — Start a review session
**Phase:** 5K (model + chart) / 5K.5 (UI) / 6.5.4 (modal overlay)
**Status:** ✅
**Tests:** `review.chart-test:review chart — :start`, `client_test:review UI affordances:clicking 'Start Review'…`

Clicking "Prioritize" while the list is prioritizable (at least one
`:ready`, at least one `:new`, last `:new` after last `:ready`) enters
the review modal. The button is dimmed otherwise.

### S-prioritize-decision — Answer Yes / No / Quit
**Phase:** 5K.5 / 6.5.4 (modal) / 5K.6 (server sync)
**Status:** ✅
**Tests:** `review.chart-test:review chart — :yes/:no/:quit`, `client_test:review UI affordances:clicking 'Yes'/'Quit'`

Inside the review modal, **Yes** promotes the cursor todo to
`:status/ready` and advances; **No** advances without changing status;
**Quit** ends the session. Walking off the last `:new` auto-ends the
session. Yes also syncs the promotion to SERVER-DB (Phase 5K.6).

---

## Persistence

### S-persist-reload — List survives page reload
**Phase:** 7.1 + 7.2
**Status:** ✅ (data layer) / 🟢 (end-to-end is browser-manual via snapshot)
**Tests:** `util.storage-test` (round-trip + corruption), `docs/snapshots/05763ca-phase-7-persisted-after-reload.png` (visual)

Every change to `SERVER-DB` is dehydrated to `js/localStorage` under
`"autofocus.server-db"`. On `init`, the app hydrates from storage
before `df/load!` runs. Corruption, missing key, or non-map content
falls back to seed silently.

---

## Modals — toggle behavior

### S-modal-mutex — Only one modal open at a time
**Phase:** 7.4 (state) / 7.5–7.6 (UI wiring)
**Status:** ✅ (state-helpers); UI exercised in 7.5/7.6 specs
**Tests:** `client_test:set-open-modal*`, `client_test:toggle-open-modal*`

`:ui/open-modal` is a single keyword value at `[:list/id 1]`, so
opening any modal mechanically overwrites whatever was previously open.
The review modal doesn't participate (header buttons are disabled
while reviewing), so Quit/Yes/No still own dismissal of that modal.

### S-modal-bg-close — Click outside the modal to close it
**Phase:** 7.4 (existing `modal-shell` supports it) / 7.5–7.6 (per-modal wiring)
**Status:** ✅ (mechanism); per-modal tests in 7.5/7.6
**Tests:** `client_test:About modal`, `client_test:Help modal`, `client_test:Import/Export modal` (added in 7.5/7.6)

`modal-shell` renders an `absolute z-0 top-0 left-0 w-100 o-0 min-h-100`
transparent button behind the content when `:on-close` is non-nil.
Clicking anywhere outside the modal hits that button and dismisses.
The review modal omits `:on-close` (must use Quit).

### S-modal-toggle-via-button — Same icon button toggles open ↔ closed
**Phase:** 7.4 (state-helper) / 7.5–7.6 (UI wiring)
**Status:** ✅ (state-helper); UI exercised in 7.5/7.6
**Tests:** `client_test:toggle-open-modal*`

`toggle-open-modal*` flips: if `modal-id` is currently open at
`[:list/id 1]`, set to `:none`; otherwise open it (which by mutex
closes any other open modal).

---

## Modal content

### S-about — About modal
**Phase:** 7.5
**Status:** ✅
**Tests:** `client_test:About modal` (open via header icon, content visible, bg-close dismisses)

Shows `appName` heading, two paragraphs of background (`infoString1` /
`infoString2`), the current version, and a close-instruction footer.
A "Debug Mode" toggle (visually hidden a11y label) is **deferred** — it
ships with the PWA debug modal in a much later phase.

### S-help — Help modal
**Phase:** 7.5
**Status:** ✅
**Tests:** `client_test:Help modal` (open via header icon, content visible, About→Help mutex)

Shows two paragraphs of usage instructions (`instructions` /
`instructions2`), a "report issues" line with an external GitHub link,
and a close-instruction footer.

### S-import-export — Import/Export modal
**Phase:** 7.6 (stubbed)
**Status:** 🟡 (markup tested, actions stubbed)
**Tests:** `client_test:Import/Export modal` (markup visible after click, bg-close works)

UI present with Copy List URL, Import (file upload via styled
`<label>` + hidden `<input type="file">`), Export, and a raw text
textarea + Submit. **All four interactive elements log to console
only via `stub-onclick`.** Real implementation (base64-URL list,
JSON round-trip, paste-text parsing) is deferred to a future phase.

---

## Theming

### S-theme-toggle — Light / dark mode toggle
**Phase:** 7.7
**Status:** ✅
**Tests:** `client_test:toggle-theme*` (state-helper), `client_test:Toggle Theme button` (click round-trip)

A lightbulb icon in the header flips `:ui/theme` between
`:theme/light` (default) and `:theme/dark`. Root, buttons, input, and
modal-shell apply matching class suffixes (`black`/`bg-moon-gray` vs
`white`/`bg-dark-gray`, etc.) via the `theme-*-class` helpers in
`learn.client`. The toggle button is always enabled, even while
reviewing or while a modal is open.

---

## Inputs and keyboard

### S-input-enter-submit — Enter key submits Add Item
**Phase:** 7.3
**Status:** 🟢 (browser-manual; headless lacks key-press simulation in this lib)

Pressing Enter inside the new-todo input is equivalent to clicking
"Add Item". After the add fires (whether successful or refused for
blank text), the input keeps keyboard focus so the user can keep typing
new items consecutively.

### S-input-refocus-after-delete — Refocus input after Delete List
**Phase:** 7.3
**Status:** 🟢 (browser-manual)

After the user clicks "Delete List" (and the list empties), keyboard
focus returns to the new-todo input — the user is most likely about to
start a fresh list.

---

## Tooling / dev affordances

### S-deployed-reference-comparison — Snapshot the deployed JS port for diffing
**Phase:** 7.8
**Status:** 🟢 (workflow + initial reference; programmatic pixel-diff not implemented)
**Tests:** none (visual / manual)

`scripts/snapshot.mjs --url <https-url>` captures any URL into
`docs/snapshots/reference/<label>.png`. The empty-list reference at
`?list=JTVCJTVE` is committed. The running diff log lives in
`docs/snapshots/reference/README.md` — add a row each time a visible
divergence is spotted.

---

## Out of scope (so far)

These exist in the JS port but aren't on the current Fulcro roadmap.
Adding here so they're discoverable when we pick them up.

- **Conflict-resolution modal** — auto-opens when URL `?list=` and
  localStorage diverge. Requires URL-shareable lists, which we don't
  have.
- **PWA debug modal** — requires service worker + manifest work.
- **Delete-list confirmation modal** — Phase 7.3 acts immediately;
  confirm modal could come back as a smaller follow-up.
- **Keyboard shortcuts** beyond Enter (the JS port has a commented-out
  block in the Help modal documenting intent).
