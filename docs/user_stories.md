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

### S-modal-open-clears-error — Opening a menu modal clears the page-level error
**Phase:** B-9 fix (post-Phase-17)
**Status:** ✅
**Tests:** `client_test:set-open-modal* — opening a modal clears any stale :ui/err-msg`, `client_test:set-open-modal* — closing a modal preserves :ui/err-msg`.

**As a user**, when I have a transient error showing
(e.g. "New items cannot be empty…" after clicking Add Item
with no text) and I then open a menu modal (Save / Info /
Settings / etc.), I expect the stale error message to clear —
I've moved on to a new action.

**Closing a modal does NOT clear** the page-level error. If
the user dismisses a modal, we don't second-guess whether
their pre-modal error is still relevant. The clearing
behaviour is intentionally asymmetric: opening clears, closing
preserves.

`learn.client.state/set-open-modal*` is the single mutation
point — it dissocs `:ui/err-msg` when the new modal-id is
non-`:none`. `toggle-open-modal*` inherits the behaviour via
delegation. See B-9 in `bugs.md` for the surfacing report.

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

### S-info-version — Fulcro port version visible in the Info modal
**Phase:** 7.5 (display) / 12.6 (single-source-of-truth wiring)
**Status:** ✅
**Tests:** `client_test:Info modal — Phase 12.3 combines About + Help` (asserts the version line renders)

**As a user**, when I click the Info modal button (`i` icon in the
header), I can see in its display contents the version number of
the app.

The version is the Fulcro port's own (`0.0.1` at time of writing),
distinct from the JS port's. The single source of truth is
`package.json`'s `version` field; `learn.version-macros/fulcro-version`
is a compile-time macro that reads that file and inlines the
string into `learn.ui.strings/app-version`, which the Info modal
renders alongside the translated "Version" / "Versión" /
"バージョン" label.

### S-import-export — Import/Export modal
**Phase:** 7.6 (stubbed) / 7.11 (Copy URL) / 7.12 (batch-text Submit) / 13 (Import + Export JSON)
**Status:** ✅
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
that re-saves only the whitelisted UI-pref slice. Currently
whitelists `:ui/theme` (Phase 7.10) and `:ui/locale` (Phase 12.4).
`init` hydrates the slice after `mount!`. See `bugs.md` B-1 for the
full diagnosis.

---

## Localisation

### S-i18n-locale-switch — Pick the app's language
**Phase:** 12.4 / 12.5
**Status:** ✅
**Tests:** `client_test:TodoList — :ui/locale propagates to button text`, `client_test:set-locale*`, `client_test:set-locale mutation`, `client_test:Settings modal — language dropdown`

As a user, when I open the Settings modal (gear icon) and pick a
language from the dropdown, every translated UI string snaps to the
chosen locale on the next render. Currently English (default),
Spanish, and Japanese are supported, with each option rendered in
its own script (`English` / `Español` / `日本語`).

The curated translation surface covers the four primary action
buttons, the three review-modal buttons, the four header tooltips,
the three modal headings, the Info / Settings / Save modal body
copy, the Save modal button labels, and the two parameterised
footer lines (`tr-list-count`, `tr-next-actionable`). Strings that
stay English are listed under "Out of scope" in
`learn.i18n.core`'s docstring.

Locale lives at `[:list/id 1 :ui/locale]`. The `set-locale`
mutation (client-only, no remote) is the single write path; the
storage watch persists the choice across reloads via
`ui-prefs-whitelist`. See
[`benefits-of-i18n-in-this-project.md`](./benefits-of-i18n-in-this-project.md)
for the decision rationale (hand-rolled lookup vs `fulcro-i18n`).

### S-i18n-persist — Locale survives page reload
**Phase:** 12.4
**Status:** ✅ (data layer)
**Tests:** Same suite as `S-theme-persist` — `:ui/locale` rides the same `ui-prefs-whitelist` pipe as `:ui/theme`.

As a user, when I pick a language and refresh the page, the
language I picked should still be active. Implemented by joining
`:ui/locale` to `learn.util.storage/ui-prefs-whitelist` so the same
storage watch that persists theme also persists locale.

### S-language-conflict-modal — Resolve mismatch between URL `?lang=` and saved locale
**Phase:** 18
**Status:** ✅ (decision logic + state helpers + mutation + modal render tested); 🟢 (the `history.replaceState` URL update is browser-manual)
**Tests:** `url-encoding-test:replace-lang-param`, `url-encoding-test:locale-decision` (all four cases), `client_test:set-locale-conflict-pair*`, `client_test:keep-locale*`, `client_test:keep-locale mutation` (state update + client-only), `client_test:Locale-conflict modal renders both locale labels`.

**As a user**, when someone sends me a list with a `?lang=es`
parameter and my saved locale is English, the Phase 14 silent-
apply rule (saved wins) means I never get to see the sender's
intended language. I'd like the option to switch — but I don't
want it forced on me.

When the URL's `?lang=` differs from my saved locale, the app
opens a **non-cancellable modal** asking which language to use:

> Which language do you want to use? / ¿Qué idioma quieres usar?
>
> [ English ] [ Español ]

The question is shown bilingually so either reader can answer.
Buttons render each locale's label in its own script (`English`
/ `Español` / `日本語`). After I pick, `:ui/locale` is set to
my choice, the modal closes, and the address bar's `?lang=` is
rewritten to match — so a reload doesn't reopen the modal.

**Asymmetry with Phase 14**: the silent-apply path
(`{:action :apply}`) still fires when localStorage has NO
saved locale. First-time visitors following `/?lang=ja` still
get Japanese without a modal. The modal only surfaces when
both signals are present and disagree.

**Header icons hard-disable during `:locale-conflict`** (same
pattern as `:delete-confirm` and `:conflict`) so the user
can't sidetrack into Settings / Save / etc. before resolving.

**Implementation**:
- `learn.util.url-encoding/locale-decision` — pure dispatcher
  on `(saved, url) → :apply | :conflict | :no-op`.
- `learn.util.url-encoding/replace-lang-param` — pure URL-query
  rewriter (overwrites/removes `lang=`); used by the CLJS-only
  `update-current-url-lang!` after resolution.
- `learn.client.state/set-locale-conflict-pair*` +
  `keep-locale*` (pure state helpers).
- `learn.client/keep-locale` defmutation — client-only, state
  swap + CLJS-only `history.replaceState` side effect.
- `learn.client.ui.modals/locale-conflict-modal` body — same
  shape as the list-conflict modal (no `:on-close`, full-area
  close button OMITTED).
- `learn.client.lifecycle/install-url-locale-fallback!` —
  extended to dispatch on `locale-decision`'s three-way result.

### S-i18n-share-with-locale — Checkbox to stamp Copy List URL with current language
**Phase:** 17
**Status:** ✅ (URL builder + state helper + mutation + checkbox render tested); 🟢 (clipboard write of the stamped URL is browser-manual)
**Tests:** `url-encoding-test:list-share-url — with optional locale` (no locale / nil / each supported locale / round-trip with `locale-from-url-search`), `client_test:set-share-with-locale*`, `client_test:set-share-with-locale mutation` (client-only confirmation), `client_test:Save modal — Include-language checkbox`.

**As a user**, when I open the import/export modal, I see a
checkbox labeled "Include language in URL" (translated) just
above the "Copy List URL" button. When I tick it and then click
Copy List URL, the copied URL has both `?list=…` AND
`&lang=<my-locale>`. Recipients who don't have a saved language
preference yet land in my language; recipients who already chose
a language keep their choice (Phase 14's precedence rule still
holds — saved `>` URL).

The checkbox state is persisted via
`learn.util.storage/ui-prefs-whitelist` (`:ui/share-with-locale?`
joined `:ui/theme` and `:ui/locale` in Phase 17). Once toggled
on, it stays on across reloads — most users who want
language-stamped sharing want it as a default, not a per-action
choice.

**Why a checkbox rather than always-stamp**: forcing your locale
onto recipients overrides their preference if they haven't saved
one yet, and most sharing flows don't actually want that.
Opt-in default-off respects the recipient.

**Implementation**:
- `learn.util.url-encoding/list-share-url` extended to 4-arity
  with an optional locale; the locale gets appended as
  `&lang=<code>` when non-nil. Round-trips with
  `locale-from-url-search` (Phase 14).
- `learn.client.state/set-share-with-locale*` + matching
  `set-share-with-locale` defmutation (client-only).
- Save modal's `<input type="checkbox">` reads
  `:ui/share-with-locale?` from props; `onChange` fires the
  mutation. The Copy List URL button reads the same flag and
  passes `locale` (or nil) into `copy-list-url!`.

### S-i18n-url-locale — `?lang=<code>` URL parameter sets initial language
**Phase:** 14
**Status:** ✅ (parser + precedence rule tested); 🟢 (window.location read + state mutation is browser-manual)
**Tests:** `util.url-encoding-test:locale-from-url-search` (happy paths covering `:en` / `:es` / `:ja`, case-insensitive code, coexistence with `?list=`; failure paths covering unsupported / empty / malformed input).

**As a user**, when I follow a link like `…/?lang=es` to the app
for the first time, the UI should open in Spanish so I can read
it. But if I've already used the app and set my preferred
language to Japanese, that preference should NOT be overridden by
someone else's `?lang=es` link — my saved choice wins.

**Precedence rule** (highest-priority signal first):

```
localStorage :ui/locale  >  URL ?lang=<code>  >  :en (default)
```

Implementation lives in
`learn.client.lifecycle/install-url-locale-fallback!`. On
`init` (CLJS branch), AFTER
`storage/install-ui-prefs-persistence!` has hydrated the saved
preference:
- If localStorage's ui-prefs slice already contains
  `:ui/locale`, do nothing (saved preference wins).
- Else, if the URL has a valid `?lang=<code>` with `<code>` in
  `i18n/supported-locales`, set `:ui/locale` to that. The
  storage-watch (already installed) saves the URL-derived
  locale on the next state change, so the visitor's first-load
  choice becomes their saved preference for subsequent visits.

**Sharing semantics**:
- The Copy List URL feature does NOT include `?lang=`. Shared
  list links (`?list=…`) load with the recipient's saved
  locale, never overriding their preference.
- Locale-specific links are an explicit, opt-in action: a
  publisher writes `…/?lang=ja` for their Japanese audience;
  the link only takes effect on visitors who haven't already
  chosen a language.

**Edge cases**:
- `?lang=ES` (uppercase) → normalised to `:es`.
- `?lang=fr` (unsupported) → ignored; visitor sees `:en`
  default or their saved preference.
- `?list=…&lang=es` (both params) → list loads, locale rule
  applies the same way (only when localStorage is empty).

---

## Accessibility (Phase 19)

Stories covering screen reader, keyboard, and a11y semantics work
landed in Phase 19. The mechanical pass split as 19a–19j; user-facing
outcomes below. Browser-manual checks live in `docs/manual_tests.md`.

### S-a11y-localized-button-labels — Every button announces its purpose in the active locale
**Phase:** 19a
**Status:** 🟢 (browser-manual — verified by inspecting `aria-label` /
`title` in DevTools; screen reader confirmation lives in manual_tests
§19a)

Every interactive button — header icons, primary action buttons, per-
row cancel/clone, modal close-buttons — has both `:title` (mouse
hover tooltip) and `:aria-label` (screen reader announcement) pulled
from `learn.i18n.core` so the wording flips with `:ui/locale`. Tab-
focusing any control announces the same text the hover tooltip shows,
in the same language as the rest of the UI.

### S-a11y-modal-semantics — Modals announce as dialogs with their visible title as the accessible name
**Phase:** 19b
**Status:** 🟢 (browser-manual; manual_tests §19b)

Every modal carries `role="dialog"` + `aria-modal="true"`, plus an
opt-in `aria-labelledby` pointing to the visible heading or question
element (with a stable id). On open, AT announces "<modal title>,
dialog" instead of generic structure.

### S-a11y-html-lang-sync — `<html lang>` follows the active locale
**Phase:** 19c
**Status:** 🟢 (browser-manual; manual_tests §19c)

When the user switches language in Settings, the page's `<html lang>`
attribute updates immediately (no reload). Screen readers use this to
pick the right voice/pronunciation per locale, so Spanish UI is read
with the Spanish voice, Japanese with the Japanese voice.

### S-a11y-decorative-icons — SVG icons don't double-announce alongside their button labels
**Phase:** 19d
**Status:** 🟢 (browser-manual; manual_tests §19d)

Every inline SVG icon has `aria-hidden="true"` + `focusable="false"`
applied via the shared `svg-attrs`. The parent button's accessible
name is the sole announcement; AT no longer reads "<label>, graphic"
on every focus.

### S-a11y-bare-control-tooltips — Controls that previously had no name announce localized hints
**Phase:** 19e
**Status:** 🟢 (browser-manual; manual_tests §19e)

The four interactive controls in the Save/Settings modals that had
no accessible name beyond their visible label (the include-language
checkbox, the JSON import button, the text-list submit button, the
language `<select>`) now carry localized `:title` + `:aria-label`.
The include-language checkbox's en wording was locked with the user:
"When checked, the share link will open in this app's current
language for whoever clicks it." es / ja translations are equivalents.

### S-a11y-status-announced — Per-row status is read aloud, including the prior status of cancelled rows
**Phase:** 19f
**Status:** ✅ (i18n function tested in spec; render-tree wiring browser-manual)
**Tests:** `i18n.core-test:tr-status (parameterized — Phase 19f)`

The status indicator span on each todo row now carries
`role="img"` + a localized `aria-label` produced by
`learn.i18n.core/tr-status`. Plain statuses announce as "new" /
"ready" / "done" / "cancelled" (plus the es/ja equivalents).
Cancelled rows surface the prior state in parentheses
("cancelled (was ready)" / "cancelado (antes: listo)" /
"キャンセル済み（元：準備完了）") so the user can hear what was
cancelled.

### S-a11y-modal-focus-management — Opening a modal moves focus into it; closing restores focus
**Phase:** 19g (+ extension)
**Status:** 🟢 (browser-manual; manual_tests §19g)

When any of the six `:ui/open-modal`-driven modals opens (info,
settings, save, delete-confirm, list-conflict, locale-conflict)
**or the statechart-driven review modal**, keyboard focus moves to
the modal's heading/question element on the next tick. The
previously-focused element is snapshotted; on close, focus returns
to it. Without this, tabbing from a now-dismissed modal landed back
on `<body>` and the user lost context.

The review modal coverage is a separate watcher
(`install-review-modal-focus-sync!`) that reads
`:review.state/active` entry/exit from the statechart session
configuration. The two watchers share the prev-focus snapshot ref,
which is safe because the modal families are mutually exclusive by
construction.

### S-a11y-escape-to-close — Escape closes dismissible modals
**Phase:** 19h
**Status:** 🟢 (browser-manual; manual_tests §19h)

Pressing Escape with the Info / Settings / Save / Delete-confirm
modal open dispatches `set-open-modal :none` — same code path as
background-click or the close-button. The two conflict modals (list-
conflict, locale-conflict) are deliberately excluded: the user must
resolve the conflict, and silent dismissal would leave the app in an
ambiguous state. The review modal also stays Quit-only for now.

### S-a11y-error-live-region — Refused actions announce via an ARIA live region
**Phase:** 19k
**Status:** 🟢 (browser-manual; manual_tests §19k)

When a user-triggered action is refused (add-blank, delete-empty,
mark-done-no-actionable, prioritize-not-prioritizable, bad-json
import, etc.), the page-level error `<p>` flips `:ui/err-msg` from
nil to a localized string. The `<p>` carries `role="alert"`
(shorthand for `aria-live="assertive" aria-atomic="true"`) so
screen readers announce the new error immediately — no manual
re-navigation needed.

Sighted users see the red copy; AT users hear it.

### S-a11y-localized-new-todo-input — New-todo input announces and prompts in the active locale
**Phase:** 19l
**Status:** 🟢 (browser-manual; manual_tests §19l)

The page-level new-todo input's placeholder (visible) and
clip-hidden `<label>` (accessible name for AT) both flip with
`:ui/locale`. Previously hardcoded English — Spanish / Japanese
users got "New TODO:" / "Type new task here" regardless of mode.

### S-a11y-theme-toggle-direction — Theme toggle announces direction + pressed state
**Phase:** 19m
**Status:** 🟢 (browser-manual; manual_tests §19m)

The theme toggle's accessible name reflects what pressing it would
do — "Switch to dark mode" / "Switch to light mode" (localized).
Backed up by `aria-pressed="true"` when dark is active,
`"false"` when light is active, so screen readers also announce
the explicit toggle state alongside the action.

### S-a11y-cross-locale-pronunciation — Cross-locale text gets the right voice
**Phase:** 19n
**Status:** 🟢 (browser-manual; manual_tests §19n)

Sister story to S-a11y-html-lang-sync. When an element contains
text in a language different from the page's `<html lang>` (the
settings dropdown options always show "English" / "Español" /
"日本語" regardless of UI language; the locale-conflict modal's
buttons and bilingual question similarly mix scripts), each
such element has its own `lang` attribute so the screen reader
voices the text with the right pronunciation. Without this, a
Spanish UI reading "日本語" would use the Spanish voice.

### S-a11y-skip-link — Bypass the header with a skip link
**Phase:** 19o
**Status:** 🟢 (browser-manual; manual_tests §19o)

A "Skip to main content" link is the first focusable element on
the page — hidden off-screen by default, slides into view when
Tab-focused. Pressing Enter sends keyboard focus past the four
header icon buttons directly to the main content section. WCAG
2.1 §2.4.1 (Bypass Blocks). The label is localized via
`:nav/skip-to-main`.

### S-a11y-reduced-motion — Respect prefers-reduced-motion
**Phase:** 19p
**Status:** 🟢 (browser-manual; manual_tests §19p)

When the user has set `prefers-reduced-motion: reduce` at the
OS level, the 0.2s button-background transitions suppress. WCAG
2.3.3 (Animation from Interactions). The state still updates
visually (hover/focus background change is preserved) — only
the *animation* between states is skipped.

### S-a11y-keyboard-only — Use the AutoFocus app fully with the keyboard
**Phase:** 19a–19h together (19i for the explicit sweep)
**Status:** ✅ (Playwright-asserted golden path; one browser-manual
visual check remains — focus-indicator visibility, see manual_tests §19i.3)
**Tests:** `e2e/keyboard-and-a11y.spec.js`:
- `19o — skip link` (2 tests)
- `19i — header tab order`
- `19g + 19h — dismissible modal focus + Escape` (4 modal tests)
- `19g (ext) — review modal focus`
- `19g + 19h — non-dismissible conflict modals` (2 tests)
- `19i — keyboard-only golden path` (full pipeline)

As a user who can't or doesn't want to use a mouse, I can complete
every primary action — add items, prioritize, mark done, delete the
list, switch language, toggle theme, import/export — using only Tab
/ Shift-Tab / Enter / Space / Escape / arrow keys.

Builds on Phase 7.3 (Enter submits Add Item; input refocus after
delete) and Phase 19g/h (modal focus + Escape). Modal close is
keyboard-reachable both via Escape (where applicable) and via the
explicit close button at the modal foot.

### S-a11y-contrast-aa — Dark theme meets WCAG AA contrast
**Phase:** 19j
**Status:** ✅ (axe-asserted; manual_tests §19j for the remaining
spot-check)
**Tests:** `e2e/keyboard-and-a11y.spec.js` axe-core scans across
5 page states (initial + 4 open modals); asserts zero violations.

Light + dark themes meet WCAG AA contrast targets (4.5:1 normal
text, 3:1 large text + UI components). Two systemic gaps found
+ fixed in Phase 19j:
  - Dim primary buttons (`btn-primary-dim-class`) — was `o-50`
    opacity = 3.16:1; replaced with explicit lighter-bg +
    lighter-text suffix per theme.
  - GitHub Issues link in Info modal — was Tachyons `blue` =
    4.05:1 on white; now theme-aware `dark-blue` / `light-blue`.

The axe-core scan locks the fix in — any future regression
breaks the Playwright suite.

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
**Phase:** 7.16
**Status:** ✅ (watch logic); 🟢 (end-to-end is browser-manual — `history.replaceState` isn't reachable from JVM)
**Tests:** `util.url-encoding-test:install-url-sync!`, `util.url-encoding-test:extract-items`

As a user looking at the address bar, the URL always carries my
current list as `?list=<encoded>` so I can copy any URL from the
browser bar (not just via the modal's Copy List URL button) and
share or bookmark it.

Implemented via `learn.util.url-encoding/install-url-sync!` —
state-atom watch (same shape as `install-ui-prefs-persistence!`)
that calls `history.replaceState` whenever the denormalized items
vector at `[:list/id 1]` changes. Skipped during identical-state
swaps so unrelated state edits (theme, modal open, err-msg) don't
trigger redundant writes.

### S-url-load-on-init — Page load reads `?list=<encoded>`
**Phase:** 7.17
**Status:** ✅
**Tests:** `util.url-encoding-test:url-segment->items`, `util.url-encoding-test:items-from-query-string`, `util.url-encoding-test:parse-list-param`, `util.url-encoding-test:og-shape->items`

As a user opening a shared link, the list encoded in the URL
populates the app on load. Companion (read-side) to
`S-url-sync-current-list`.

Full decode chain: `base64-decode → js-url-decode → parse-json-array
→ og-shape->items` with corrupt-input → nil at every layer. `init`
calls `items-from-current-url`; if it returns items, they overwrite
SERVER-DB (deferred to `S-conflict-modal` if localStorage also has
state and the two differ).

### S-conflict-modal — Conflict-resolution modal (URL ≠ localStorage)
**Phase:** 7.18
**Status:** ✅
**Tests:** `url-encoding-test:decide-initial-list`, `client_test:keep-link-list*`, `client_test:keep-local-list*`

As a user opening a shared link with localStorage already populated
with a different list, a modal asks me which list to keep — the one
from the URL or the one from localStorage. Markup follows the JS
port (`js_ui_reference.md` C/6): heading + mismatch message + two
read-only list previews + Copy Link URL / Copy Local URL buttons +
Keep Link / Keep Local choice buttons. No transparent close —
explicit choice required.

Pure decision in `decide-initial-list` classifies the state as
`:seed / :url / :local / :conflict`. Init wires it: `:url`
overrides SERVER-DB; `:conflict` defers SERVER-DB updates and
post-mount opens the modal with both lists stashed at
`:ui/conflict-url-items` (URL items) and the live list (local).
`keep-link-list` mutation replaces normalized state with URL items
and syncs to SERVER-DB; `keep-local-list` is just close-modal +
force URL bar refresh.

### S-pwa-offline — Progressive Web App with offline support
**Phase:** 7.19
**Status:** ✅ (manifest + SW shipped); 🟢 (offline-mode behaviour is browser-manual)
**Tests:** `scripts/verify-sw.mjs` (Playwright probe; not in spec suite)

As a user deploying this to GitHub Pages, the app installs as a
PWA and runs offline once the shell has been cached.

Implementation under `resources/public/`:
- `sw.js` — service worker. Pre-caches the shell on install
  (HTML, CSS, JS, manifest, icon, Tachyons CDN, Google Fonts CDN).
  Network-first for navigations (fall back to cached index, then
  offline.html); cache-first for static assets.
- `manifest.webmanifest` — basic manifest, scope-relative
  start_url, SVG icon.
- `offline.html` — fallback page.
- `icon.svg` — placeholder AF monogram.

Adapted from the og JS port's `serviceWorker.js` (simpler — single
JS bundle, no `static/` split). New-version-available reload banner
is deferred to a follow-up if/when needed.

### S-import-json-file — Import a JSON file from the save modal
**Phase:** 13
**Status:** ✅ (parser + state-helper + mutation tested); 🟢 (file-upload `<input>` + FileReader is browser-manual)
**Tests:** `util.tasks-io-test:parse-tasks-json` (happy paths + failure-type discrimination), `client_test:import-from-json*`, `client_test:import-from-json mutation`, `resolvers_test:import-from-json wired`

As a user with a previously-exported list, I want to upload the
JSON file via the save modal's Import button and have its items
appended to my current list. The JS port (`handleImportTasks` in
`App.js`) does:
1. Read the file via `FileReader.readAsText`.
2. Parse + validate via `importTasksFromJSON` (`tasksIO.js` —
   refuses non-arrays or items missing `id`/`text`).
3. `addAll` to merge into current tasks (regenerates ids to avoid
   collisions).
4. Surface `bad-json-import-err` / `non-json-import-err` on
   failures.

Our port has `S-import-batch-text` (Phase 7.12) covering the
paste-text path; this story covers the file-upload path. The pure
`json-text → items` parser lives in `learn.util.tasks-io`; the
FileReader + UI wiring lives in
`learn.client.ui.modals/import-json-file!`.

**Merge semantics: APPEND + fresh UUIDs** (matches the OG's
`addAll`). Importing a file you previously exported adds a second
copy of those items to your list — predictable, non-destructive,
zero confirmation friction. Users wanting "replace current list
with this file" go Delete List → confirm → Import (two clicks,
each with their own affordance). The "ask first" alternative is
queued as a nice-to-have (`S-import-confirmation` below).

### S-export-json-file — Export the current list as a JSON file
**Phase:** 13
**Status:** ✅ (encoder reused from `url-encoding/items->json`, already tested); 🟢 (Blob + download click is browser-manual)
**Tests:** `url-encoding-test:items->json` covers the encoder; the Blob/anchor-click trigger in `learn.client.ui.modals/export-items-json!` is CLJS-only and browser-manual.

As a user, I want the Export button in the save modal to download
my list as `tasks.json`. The JS port (`handleExportTasks`) does
`JSON.stringify(tasks)` → `Blob` → `URL.createObjectURL` → triggers
a hidden `<a download>` click. Companion to `S-import-json-file` —
together they close the `S-import-export` story.

### S-max-url-length — URL-length safeguard
**Phase:** 15
**Status:** ✅ (predicate + watch over-limit branch tested); 🟢 (real browser uses `history.replaceState` — confirm by manual scroll)
**Tests:** `url-encoding-test:MAX_URL_LENGTH` (constant), `url-encoding-test:items-encode-fits?` (pure predicate, empty/small/200-item cases), `url-encoding-test:install-url-sync! — over-limit handling` (watch skips url-setter and triggers on-over-limit callback).

**As a user**, when my list grows past the URL-encodable limit
(~8000 chars of encoded JSON), I want the app to keep working —
my items stay in the browser's localStorage and I can keep
adding, cancelling, marking done, etc. — but the URL should
freeze at its last valid value so I don't end up with a
truncated / unsharable link. The app should also tell me what's
happening so I can back up my list (text or JSON) if I want to
preserve a particular state.

**Implementation** (`learn.util.url-encoding`):
- `MAX_URL_LENGTH` constant = 8000 (matches the OG JS port).
- `items-encode-fits?` pure predicate — encodes via the
  existing `items->base64-url-segment` chain, returns whether
  the result is short enough.
- `install-url-sync!` extended to a 3-arity: 1-arity production
  default uses `replace-url-with-items!` + an `on-over-limit`
  callback that swaps the i18n `:err/url-too-long` string into
  `:ui/err-msg`. 2-arity remains for legacy tests; 3-arity for
  observability.

**Divergence from OG**: the JS port lets the URL grow unbounded
and produces unsharable links. We freeze instead — predictable,
no broken URLs, error message points the user to recovery
(text-copy or JSON Export). See `docs/changes.md`.

**Note**: the error message is currently the only fully-localized
error string. Other errors (empty-input, nothing-to-delete, etc.)
remain English-only — tracked as `bugs.md` B-8.

---

## Nice-to-have (🆒) — would be cool, no urgency

No commitment to build. If it surfaces organically (e.g. a user
asks for the export) we revisit.

### S-keyboard-shortcuts — Keyboard shortcuts beyond Enter
**Phase:** —
**Status:** 🆒
**Tests:** TBD when promoted to ⬜

The JS port has a commented-out shortcuts block in the Help modal
documenting intent (e.g. `d` to delete, `p` to prioritize, etc.) —
the og itself never shipped them. Demoted from Planned to 🆒
because parity is achieved without them.

---

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

### S-dev-mode-toggles — Debug mode controls in Settings
**Phase:** 21
**Status:** ✅
**Tests:** see [`docs/ideas.md#debug-mode-controls-in-settings`](./ideas.md)
for the full test plan. `learn.dev-fixtures-test` covers 21.1.
`learn.dev-config-test` covers the 21.2 pure parts + the 21.4a
`cycle-step` orchestrator. 21.3 + 21.4b verified via Playwright
snapshots (`docs/snapshots/ef4e9fd-dirty-phase-21.4b-*.png`) confirming
the Debug section renders, the rainbow checkbox toggles Pesticide CSS
live, and the cycle button advances through fixtures with the UI
refreshing via `df/load!`.

As a developer working on the AutoFocus port, I want a "Debug mode"
section in Settings that gives me runtime control over dev-only
visualisations and state inspection — without source-editing flags
or restarting shadow-cljs. Four controls planned: rainbow-outlines
toggle, depth-backgrounds toggle, app-state dump button, and a
list-fixture cycler (actual → empty → 5-item → 26-item).

The whole section is gated on `goog.DEBUG` so prod builds drop it
via Closure dead-code elimination. The list-cycler preserves the
user's actual data via a localStorage snapshot, so cycling through
fixtures is non-destructive.

Full design (architecture, dep graph, fixture invariants, test
plan, coexistence of source-default flags with the runtime UI):
[`docs/ideas.md#debug-mode-controls-in-settings`](./ideas.md).

### S-pwa-debug-modal — PWA debug info modal
**Phase:** —
**Status:** 🆒
**Tests:** TBD when promoted to ⬜

The JS port's debug modal (`docs/js_ui_reference.md` C/7) shows the
service worker state, cache contents, offline status, and a
general-info JSON dump. Useful once `S-pwa-offline` is live and we
need to debug install/cache behaviour. Until then there's nothing
to debug.

### S-ux-a11y-review-pass — Qualitative UX + content-copy review of all modals
**Phase:** —
**Status:** 🆒
**Tests:** N/A (qualitative; outcome is doc + small commits if changes warranted)

Separate from the mechanical a11y audit (Phase 19), this is a
QUALITATIVE pass over the user-facing copy and overall UX. Things
to question:

- **Info modal**: is the About copy concise? Does the
  "Instructions" section read well in all three locales, or do
  the translations sound awkward? Is the GitHub-issues link
  prominent enough?
- **Import/Export modal**: are the section headers / button
  labels self-explanatory? Does the "paste raw text"
  affordance compete with the JSON file-upload?
- **Settings modal**: as more preferences land (Phase 17's
  share-locale checkbox is already there), does the modal need
  visual grouping / dividers?
- **Review flow**: is "Prioritize" the right verb for the
  outer button, vs. "Start prioritizing" / "Begin review"?
  The Japanese 優先する shipped in B-13 prep is concise but
  could be more action-explicit.
- **Conflict modals** (list + locale): are the bilingual /
  side-by-side patterns intuitive on first encounter?
- **Error messages**: the strings are now localized (B-8 /
  B-13) but are they user-friendly? "The list isn't
  prioritizable right now." — does the user know WHY?

**Decide when to promote ⬜**: when 2+ users besides the
author have walked the app end-to-end and reported specific
copy / UX friction, OR when the project is being prepared for
a wider audience.

### S-import-confirmation — Ask before importing into a non-empty list
**Phase:** —
**Status:** 🆒
**Tests:** TBD when promoted to ⬜

Today the Import button (`S-import-json-file`, Phase 13) silently
appends parsed items to whatever's already in the list — matches
the OG's `addAll` semantics, zero confirmation friction, but means
re-importing a file you previously exported produces two copies of
those items in your list. Not destructive, but occasionally
surprising.

Future enhancement: when the user picks a file AND the current list
is non-empty, intercept with a confirmation modal:

> "You have N items in your list. The import has M items.
> [Replace] [Append] [Cancel]"

Where:
- **Replace** = clear current list + add imported items.
- **Append** = today's behaviour.
- **Cancel** = no change.

Empty-list import skips the modal (nothing to clobber).

**Decide when to promote ⬜**: if a real user trips over the
silent append, OR if we ever build a Settings preference for
"default import mode" (in which case the modal can pre-select
the user's preferred default and show as a one-click confirm).
Until then the workflow "Delete List → confirm → Import" covers
the replace case in two-clicks-each-with-an-affordance.

---

## Won't implement (❌) — acknowledged scope cuts

Currently empty. The two items previously listed here
(conflict-resolution modal, PWA debug modal) have been promoted —
conflict modal to **Planned ⬜** (the Phase 7.11 encoder removed
its main blocker), debug modal to **Nice-to-have 🆒** (depends on
S-pwa-offline which is now planned).
