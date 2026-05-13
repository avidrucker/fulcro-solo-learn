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
| ⬜ | Planned — will build, not started yet |
| 🆒 | Nice-to-have — would be cool, no urgency (no phase commitment) |
| ❌ | Won't implement — acknowledged scope cut (rationale recorded) |

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
**Phase:** 3 (state-helper) / 7.3 (UI button + server sync) / 7.12 (confirm modal)
**Status:** ✅
**Tests:** `client_test:delete-all mutation`, `client_test:Delete List button`

As a user who wants to start over, I want a "Delete List" button that
empties the entire list. The button is dimmed when the list is already
empty. **After confirming the delete, the new-todo input refocuses** so
the user can immediately start typing the replacement list. The
refocus piece is browser-manual — headless doesn't track focus.

In Phase 7.3, the client-side `delete-all` defmutation grew a `(remote
[env] (remote-list-items env))` so persistence reflects deletes (no
ghost items after reload). In Phase 7.12, Delete List now goes through
the confirm modal (see [S-delete-list-confirm]) instead of acting
immediately — the underlying `delete-all` mutation is unchanged.

### S-delete-list-confirm — Confirmation modal for Delete List
**Phase:** 7.12
**Status:** ✅
**Tests:** `client_test:Delete-confirm modal — opens via Delete List click`, `client_test:Delete-confirm modal — Yes commits, No cancels`

As a user clicking Delete List on a non-empty list, I want a
confirmation prompt ("Are you sure you want to delete your list? This
action cannot be undone.") before the list is destroyed, so an
accidental click doesn't wipe my work.

Behavior:
- Delete List on a non-empty list opens `:delete-confirm` (the JS
  port's `confirmListDelete` text + No/Yes buttons).
- Yes empties the list (client + SERVER-DB), clears any prior error,
  refocuses the input, and closes the modal.
- No just closes the modal.
- Delete List on an empty list bypasses the modal and surfaces the
  existing `nothing-to-delete-err` (Phase 7.9) — no modal for a
  no-op.
- Background click on the transparent overlay cancels (matches the
  other modals; matches the JS port's "tap outside" behavior).

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
**Status:** 🟡 (markup tested; Copy List URL real as of 7.11; batch-text Submit real as of 7.12; Import/Export JSON still stubbed)
**Tests:** `client_test:Import/Export modal` (markup visible after click, bg-close works)

UI present with Copy List URL, Import (file upload via styled
`<label>` + hidden `<input type="file">`), Export, and a raw text
textarea + Submit. **Copy List URL is real (Phase 7.11 — see
[S-copy-list-url]); batch-text Submit is real (Phase 7.12 — see
[S-import-batch-text]); the remaining two (Import-JSON-file,
Export-JSON-file) log to console only via `stub-onclick`.** Real
JSON file round-trip is deferred to a future phase.

### S-import-batch-text — Batch import via the modal textarea
**Phase:** 7.12
**Status:** ✅
**Tests:** `model.list-test:import-from-string`, `client_test:import-from-text*`, `client_test:import-from-text mutation`, `client_test:Save modal — batch import textarea flow`

As a user with a list of tasks in plain-text form, I want to paste
them into the Import/Export modal's textarea (one per line) and click
Submit to add them all to my list in one go.

Behavior (mirrors `pwa-autofocus-app/src/core/tasksIO.js`
`importTasksFromString`):
- Split on `\n`, drop any line that's empty or whitespace-only, then
  append the rest in order using the same status rule as Add Item
  (`add-todo` — first new item gets `:status/ready` if the existing
  list has no ready items; subsequent ones are `:status/new`).
- Leading / trailing whitespace WITHIN a non-blank line is preserved
  verbatim (matches the JS port — the filter only checks `trim`, it
  doesn't mutate the line).
- Submit on blank-or-whitespace-only textarea surfaces
  `:ui/err-msg = empty-textarea-err` and leaves the modal open so
  the user can correct.
- Successful Submit: clears the textarea and any prior error,
  imports the items, and leaves the modal open so the user can
  verify the import or paste a second batch. (Per B-2 fix —
  whether to auto-close is tracked in
  [`ideas.md#modal-auto-close`](./ideas.md).)
- Persistence: the mutation has a remote so SERVER-DB stays in sync
  (same wire pattern as `add-todo`, `delete-all`, etc.).

### S-copy-list-url — Copy share URL to clipboard
**Phase:** 7.11
**Status:** ✅ (data layer); 🟢 (end-to-end is browser-manual — clipboard isn't reachable from JVM tests)
**Tests:** `util.url-encoding-test:base64-encode`, `util.url-encoding-test:js-url-encode`, `util.url-encoding-test:items->json`, `util.url-encoding-test:items->base64-url-segment`, `util.url-encoding-test:list-share-url`

As a user, when I click Copy List URL inside the Import/Export modal,
the current page URL with a `?list=<encoded>` query string is written
to my clipboard so I can share my list by pasting the link elsewhere.

The encoder mirrors the JS port's three-step recipe
(`btoa(encodeURIComponent(JSON.stringify(items)))`); the empty-list
case round-trips to the deployed fixture `JTVCJTVE` (the same value
seen at `?list=JTVCJTVE` in `docs/snapshots/reference/`). Best-effort
clipboard write — silently no-ops on non-https/old browsers where
`navigator.clipboard` is absent.

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

### S-theme-persist — Theme survives page reload
**Phase:** 7.10
**Status:** ✅ (data layer); 🟢 (end-to-end is browser-manual via snapshot)
**Tests:** `util.storage-test:extract-ui-prefs`, `util.storage-test:apply-ui-prefs`, `util.storage-test:->edn / <-edn round-trip for ui-prefs slice`, `util.storage-test:ui-prefs-key constant`

As a user, when I switch themes and refresh the page, the theme I
picked should be preserved between reloads.

The fix (Phase 7.10) adds a second localStorage key
`"autofocus.ui-prefs"` and a parallel watch on the Fulcro state-atom
that re-saves only the whitelisted UI-pref slice (currently just
`:ui/theme`). `init` hydrates the slice after `mount!`. See
`bugs.md` B-1 for the full diagnosis.

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

## Errors and feedback

### S-error-add-blank — Adding with blank text shows error
**Phase:** 7.9
**Status:** ✅
**Tests:** `client_test:Error surfacing — Add Item with blank text`

Clicking Add Item (or pressing Enter) with empty or whitespace-only
text in the input sets `:ui/err-msg` to `s/empty-input-err`
("New items cannot be empty or only whitespace.") instead of silently
no-opping. The error renders below the input. Typing valid text and
re-submitting clears the error and adds the item.

### S-error-delete-empty — Deleting an empty list shows error
**Phase:** 7.9
**Status:** ✅
**Tests:** `client_test:Error surfacing — Delete List on empty list`

Clicking Delete List on an empty list sets `:ui/err-msg` to
`s/nothing-to-delete-err` ("There is nothing to delete."). On a
non-empty list, Delete List clears the prior error and empties the
list as before.

### S-error-mark-done-no-actionable — Mark Done with no actionable items
**Phase:** 7.9
**Status:** ✅
**Tests:** `client_test:Error surfacing — Mark Done with no actionable items`

Clicking Mark Done when no `:status/ready` items exist sets
`:ui/err-msg` to `s/cannot-take-action-err` ("There are no actionable
tasks in your list."). On an actionable list, Mark Done clears the
prior error and completes the benchmark as before.

### S-error-not-prioritizable — Prioritize on non-prioritizable list
**Phase:** 7.9 (follow-up)
**Status:** ✅
**Tests:** `client_test:Error surfacing — Prioritize on non-prioritizable list`

Clicking Prioritize when the list isn't prioritizable (empty, or no
`:new` items after the last `:ready` per SCHEMA.md §15) sets
`:ui/err-msg` to `s/not-prioritizable-err` ("The list isn't
prioritizable right now."). Matches the JS port verbatim — confirmed
against the deployed HTML reference. On a prioritizable list,
clicking starts the review chart and clears the prior error.

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

## Planned (⬜) — will build, not started

Stories that are clearly on the roadmap but haven't been built yet.
Phase assignment is TBD until we slot them. Open bugs that block or
relate live in [`bugs.md`](./bugs.md) — currently `B-3` (header
icons clickable during review / delete-confirm).

### S-url-sync-current-list — URL bar reflects the current list
**Phase:** TBD
**Status:** ⬜
**Tests:** TBD

As a user looking at the address bar, I want it to always carry my
current list as `?list=<encoded>` so I can copy any URL from the
browser bar (not just via the modal's Copy List URL button) and
share or bookmark it. We already have the encoding side
(`learn.util.url-encoding/items->base64-url-segment` from Phase
7.11); this story adds the WRITE side: a state-atom watch (same
shape as `install-ui-prefs-persistence!`) that calls
`history.replaceState` whenever the denormalized item vector
changes. Skipped during init / hydration to avoid a redundant write
of the just-loaded value.

### S-url-load-on-init — Page load reads `?list=<encoded>`
**Phase:** TBD
**Status:** ⬜
**Tests:** TBD

As a user opening a shared link, I want the list encoded in the URL
to populate the app on load. Companion to `S-url-sync-current-list`
— this is the READ side. Needs a pure decoder (inverse of
`items->base64-url-segment`: base64-decode → URL-decode →
JSON.parse → validate shape), then `init` reads `window.location`
and merges the decoded list into the Fulcro state-atom before the
initial `df/load!`. Validation refuses gracefully (corrupt URL =
fall back to seed / localStorage).

### S-conflict-modal — Conflict-resolution modal (URL ≠ localStorage)
**Phase:** TBD (depends on S-url-sync-current-list + S-url-load-on-init)
**Status:** ⬜
**Tests:** TBD

As a user opening a shared link with localStorage already
populated, I want a modal that asks me which list to keep — the one
from the URL or the one from localStorage. Markup follows the JS
port (`js_ui_reference.md` line 120–125; see also
`js_ui_reference.md` C/6 for full content): heading + mismatch
message + two non-interactive list previews (offsets 100/200 per
the JS port) + Copy Link URL / Copy Local URL buttons + Keep Link /
Keep Local choice buttons. No transparent close — must choose.
Opens automatically on init when both lists exist and differ;
closing dismisses for the rest of the session (does NOT remember
across reloads — re-trigger on each load).

### S-pwa-offline — Progressive Web App with offline support
**Phase:** TBD (post-Phase 7 stretch)
**Status:** ⬜
**Tests:** TBD

As a user deploying this to GitHub Pages, I want a service worker
+ web app manifest so the app installs as a PWA and runs offline
(reading the localStorage-backed list and adding/editing items
while disconnected). Includes:
- `manifest.webmanifest` with icons, name, start URL, display
- Service worker that pre-caches the shell on install, then
  network-falls-back-to-cache for assets
- shadow-cljs build that emits a stable file naming pattern the
  service worker can hash
- A "new version available — reload to update" banner (standard
  PWA refresh-prompt UX)

### S-keyboard-shortcuts — Keyboard shortcuts beyond Enter
**Phase:** TBD
**Status:** ⬜
**Tests:** TBD

The JS port has a commented-out shortcuts block in the Help modal
documenting intent (e.g. `d` to delete, `p` to prioritize, etc.).
Land these once the rest of the parity work is closed.

---

## Nice-to-have (🆒) — would be cool, no urgency

No commitment to build. If it surfaces organically (e.g. a user
asks for the export) we revisit.

### S-markdown-export — Export the list as a Markdown checklist
**Phase:** —
**Status:** 🆒
**Tests:** TBD when promoted to ⬜

As a user who keeps notes in Markdown, I want to export the current
list as a `.md` checklist with the AutoFocus statuses preserved as
distinct prefixes:

| Status | Prefix |
|---|---|
| `:status/new` | `- [ ] ` |
| `:status/ready` | `- [o] ` |
| `:status/done` | `- [x] ` |
| `:status/cancelled` | `- [~] ` |

Pure CLJC encoder: `items → markdown-string`. UI plug-in point would
be a new button in the save modal (next to Export-JSON) and/or a
clipboard-write action. Pairs naturally with a future
markdown-import story (parse Markdown checklist → items).

### S-pwa-debug-modal — PWA debug info modal
**Phase:** —
**Status:** 🆒
**Tests:** TBD when promoted to ⬜

The JS port's debug modal (`docs/js_ui_reference.md` C/7) shows the
service worker state, cache contents, offline status, and a
general-info JSON dump. Useful once `S-pwa-offline` is live and we
need to debug install/cache behaviour. Until then there's nothing
to debug.

---

## Won't implement (❌) — acknowledged scope cuts

Currently empty. The two items previously listed here
(conflict-resolution modal, PWA debug modal) have been promoted —
conflict modal to **Planned ⬜** (the Phase 7.11 encoder removed
its main blocker), debug modal to **Nice-to-have 🆒** (depends on
S-pwa-offline which is now planned).
