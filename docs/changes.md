# Changes from the JS port

What this Fulcro port does **differently** from
[`avidrucker/pwa-autofocus-app`](https://github.com/avidrucker/pwa-autofocus-app),
either deliberately (the Fulcro / Pathom / statecharts architecture
asks for different shapes) or as a UX nudge we judged worth taking.
Bugs in the JS port that we *fix* are listed under "JS-discrepancies"
in [`SCHEMA.md`](./SCHEMA.md) and `js_source_reference.md`; this doc
is for *intentional* divergences that aren't bug fixes.

Each entry follows the same shape: **what's different**, **why**,
**where in the code**.

---

## UI / UX

### Add Item button is dimmed when the input is blank

**Difference:** With an empty / whitespace-only input, the Add Item
button gets the `bg-moon-gray` dim suffix instead of the bright
`bg-dark-gray` action variant. Clicking it still fires
`submit-add!`, which surfaces `empty-input-err` ("New items cannot
be empty or whitespace only.") — same error message as the JS
port, but visually telegraphed.

**Why:** Phase 7.9 settled on "dim-when-invalid, never disable" so
the click still gets a chance to surface an error message and the
button never goes truly inert. The og only dims via opacity when
disabled (`:disabled` attribute), which removes the click surface
entirely.

**Where:** `learn.client/TodoList` render — `add-dim?` /
`delete-dim?` / `prioritize-dim?` / `mark-done-dim?` flags compose
into `btn-cls`; the `<button>` itself never carries `:disabled`.

### Header menu icons hard-disable during review + delete-confirm + conflict

**Difference:** While a review session is active OR
`:ui/open-modal` is `:delete-confirm` / `:conflict`, the three menu
icons (Save / About / Help) are hard-disabled (HTML `:disabled`
attribute AND nil `onClick`). Theme toggle stays clickable.

**Why:** Matches the JS port's intent (`docs/js_ui_reference.md`
line 149) but the JS port disables only via the `:disabled`
attribute; we belt-and-suspenders the click handler too because the
headless test framework's `click!` invokes onClick directly without
checking `:disabled`. See B-3 in [`bugs.md`](./bugs.md).

**Where:** `learn.client/header-icon-button` `:disabled?` arg + the
predicate computed in `Root`.

### Batch-text import keeps the save modal open after Submit

**Difference:** After a successful batch-text import via the save
modal's textarea + Submit, the modal **stays open** (with the
textarea cleared) rather than closing.

**Why:** Lets the user verify the new items landed without re-
opening, or paste a second batch immediately. Whether to add
auto-close as a preference is tracked in [`ideas.md`](./ideas.md).
The bug-report that drove this (modal closing) is B-2 in
[`bugs.md`](./bugs.md).

**Where:** `learn.client/submit-import!`.

### Background-click cancels the delete-confirm modal

**Difference:** The delete-confirm modal's transparent overlay
button (when clicked) is treated as "No" — the modal closes and the
list is preserved.

**Why:** All our other modals use `modal-shell`'s background-close
overlay; making delete-confirm the one exception would surprise
users. The JS port has *no* background close on delete-confirm (the
markup omits the overlay). Yes and No are still required to be
clicked when the user is committing to the action.

**Where:** `learn.client/delete-confirm-modal` passes `:on-close
on-no` to `modal-shell`.

---

## Architecture / shape

### Items use UUIDs, not integer ids

**Difference:** Our items have `:todo/id <uuid>`; the JS port uses
incrementing integers.

**Why:** Distributed-systems hygiene — UUIDs play well with offline
generation, with future multi-user sync, and with Fulcro's
normalized-state ident model (`[:todo/id <uuid>]`). The JS port's
integer ids would require id reassignment on every import/merge.

**Where:** `learn.model.list/add-todo` generates UUIDs; encoding to
the OG URL-share format (`learn.util.url-encoding/items->og-shape`)
derives integer ids from list position so the URLs stay cross-
compatible with the JS port.

### Conflict detection ignores UUIDs

**Difference:** When `init` decides whether the URL list and
localStorage list conflict, we compare on **content** (text +
status + was) only — not UUIDs.

**Why:** The decoder assigns fresh UUIDs every load (the URL JSON
shape stores integer ids, not UUIDs — see above), so a literal
`(= local-items url-items)` would phantom-conflict on every
refresh. B-4 in [`bugs.md`](./bugs.md).

**Where:** `learn.util.url-encoding/decide-initial-list` +
`items-content-shape` helper.

### Review flow is a statechart, not flat state

**Difference:** `isPrioritizing`, `cursor`, and the Yes/No/Quit
event dispatch live in a SCXML-inspired statechart
(`learn.review.chart`) rather than React `useState` flags.

**Why:** It's a learning project for the statecharts tech, and
review *is* a real state machine (active vs. inactive, with the
cursor walking off the end auto-popping back to inactive via an
eventless transition). The chart shape makes the invariant
visible.

**Where:** `learn.review.chart` for the chart, `learn.client` for
the integration helpers (`scf/install-fulcro-statecharts!`,
`scf/send!` from click handlers).

### `<main>` carries the dark bg, `<body>` carries the canvas bg

**Difference:** In dark mode, we apply `bg-black` to `<main>` (via
`theme-page-bg-class`) AND to `<body>` via a state-atom watch
(`install-body-theme-sync!`). The JS port toggles only `<body>`.

**Why:** Defense in depth. `<main>`'s class makes the dark bg
declaratively part of the Fulcro render tree; the body-class watch
handles the canvas-bg propagation that the browser uses for the
area past `<body>`'s box (visible when the list overflows the
viewport). See Phase 7.13 / 7.15.

**Where:** `learn.client/theme-page-bg-class` +
`install-body-theme-sync!`.

---

## Tooling / build

### shadow-cljs + clj-nrepl, not Create-React-App

**Difference:** Built via `npx shadow-cljs release app` rather than
`npm run build`. Tests run via the JVM clj nREPL master runner
(`clojure.test` + fulcro-spec) rather than `npm test`.

**Why:** It's a ClojureScript app. CRA doesn't apply.

**Where:** `shadow-cljs.edn`, `deps.edn`, the master test runner
in `CLAUDE.md`. The GitHub Actions workflow
(`.github/workflows/main.yml`) sets up Clojure + Node.

### Tests are deterministic via `:event-loop? false`

**Difference:** Statechart sessions are installed with
`:event-loop? false` and the spec suite drains the event queue
manually via `scf/process-events!` after each event.

**Why:** Tests can't rely on the real timer-based event loop.
Matches the pattern recommended by `install-fulcro-statecharts!`'s
docstring.

**Where:** `learn.client/start-chart!`, `client_test:review UI
affordances:*`.

### Header has an `i`-Info modal + a gear-Settings modal (no `?`-Help)

**Difference:** Phase 12.3 merged the JS port's two `i`-About and
`?`-Help modals into one Info modal opened by the existing `i` icon.
The `?` icon is gone from the header. A new gear icon opens a
Settings modal (introduced in 12.3, populated with the language
dropdown in 12.5).

**Why:** Reduces header-icon visual noise; the About + Help bodies
share a single overlay with two sections under one heading. Phase
12's i18n work needs a Settings home, and a dedicated icon fits
better than overloading About.

**Where:** `learn.client.ui.modals/info-modal`,
`learn.client.ui.modals/settings-modal`,
`learn.client.ui.components/Root` header.

### App is localised to Spanish + Japanese in addition to English

**Difference:** A language dropdown in the Settings modal switches
between `:en` / `:es` / `:ja`. Curated translation surface covers
the four primary action buttons, three review-modal buttons, four
header tooltips, three modal headings, the parameterised "You have
N items" / "Next actionable" footer lines, the Info + Settings +
Save modal bodies (about copy, instructions, version label,
close-instruction footers), and the Save modal button labels.

**Why:** Learning exercise for a cross-cutting concern. The JS port
ships English-only.

**Where:** `learn.i18n.core` (translation map + `tr` lookup),
`:ui/locale` on `[:list/id 1]`, `learn.client/set-locale` mutation,
`learn.client.ui.modals/settings-modal` `<select>`. Design rationale
for hand-rolling vs `fulcro-i18n` in
[`benefits-of-i18n-in-this-project.md`](./benefits-of-i18n-in-this-project.md).

### Modal overlay covers the full document, not just the viewport

**Difference:** When the todo list overflows the viewport (long
list or zoomed-in browser), the semi-transparent overlay extends
through the entire scrollable content area below the header. The
JS port's overlay stops at viewport height, leaving the bottom of
the list and the footer visible at full opacity beneath the modal.

**Why:** Phase 12.5c. The OG's bug is observable at 200% zoom with
10+ items. The fix is structural: drop `height: 100%` from the
html/body/#app root chain (kept `min-height`), switch
`.app-container` from `h-100` to `flex-1`, and anchor the overlay
to all four edges (`top-0 bottom-0 left-0 right-0`) instead of
relying on `h-100`. Header is excluded from the overlay zone (it
lives outside `.app-container`) so its icons remain visible.

**Where:** `resources/public/css/app.css` (root reset),
`learn.client.ui.components/Root` (`.app-container` class),
`learn.client.ui.modals/modal-shell` (overlay class).

### Modal-internal inputs stay solid on hover/focus

**Difference:** The save-modal textarea and the settings-modal
language dropdown render with a gray bg at rest (matching the
primary-button bg), and snap to solid white (light) or solid black
(dark) on hover/focus. The JS port's hover/focus state fades to
transparent (a button-style affordance that washes out on top of
the modal's translucent overlay).

**Why:** Phase 12.5c. The page-level new-todo input keeps the JS
port's fade behavior (`theme-input-class`); only the in-modal
fields use the new solid variant (`theme-modal-input-class`).

**Where:** `learn.client.ui.theme/theme-modal-input-class`,
applied in `learn.client.ui.modals/save-modal` (textarea) and
`learn.client.ui.modals/settings-modal` (`<select>`).

### Settings dropdown options stay themed in dark mode

**Difference:** Each `<option>` in the language dropdown carries an
inline `background-color`/`color` style when the theme is dark, so
the OS-rendered dropdown panel paints dark instead of falling back
to system light.

**Why:** Chromium on Windows ignores CSS `color-scheme: dark` on
form-control panels when the system is in light mode. The inline
option style is the de-facto cross-browser fix.

**Where:** `learn.client.ui.modals/settings-modal`.

### URL freezes (no unsharable links) when the encoded list exceeds 8000 chars

**Difference:** When the user's list grows large enough that
the encoded `?list=<segment>` would exceed 8000 chars
(`MAX_URL_LENGTH`), our URL-sync watch SKIPS the
`history.replaceState` call — the URL stays at its last
fitting value. The JS port lets the URL grow unbounded and
produces truncated / unsharable links.

**Why:** Phase 15 (`S-max-url-length`). Predictability:
either the URL in your address bar is a valid shareable
encoding of your list, or you've been told it's no longer
syncing. Never both / never broken. localStorage continues
normally — the list keeps growing locally; users back up to
JSON (Export) or paste-as-text via copy/paste before pruning.

The error surfaces as `:err/url-too-long` ("Current list
cannot be saved as URL: Please back up your list to text or
JSON.") in the user's selected locale.

**Where:** `learn.util.url-encoding/MAX_URL_LENGTH`,
`learn.util.url-encoding/items-encode-fits?`,
`learn.util.url-encoding/install-url-sync!` (3-arity with
the over-limit callback wired by default in production).

### Locale can be hinted via `?lang=<code>` URL parameter

**Difference:** First-time visitors following a URL like
`/?lang=es` open the app in Spanish (or `:ja` for Japanese);
subsequent visits use whatever the visitor saved via the
Settings dropdown. The JS port has no localisation at all.

**Why:** Phase 14. Lets publishers write locale-specific
landing links ("here's the app in Japanese — `…/?lang=ja`")
without forcing that locale onto recipients of separate list-
share links. The precedence rule
(`localStorage > URL > :en default`) means saved preferences
always win, so `?list=…` share-links never override the
recipient's chosen language.

**Where:** `learn.util.url-encoding/locale-from-url-search`
(pure parser); `learn.util.url-encoding/locale-from-current-url`
(CLJS wrapper); `learn.client.lifecycle/install-url-locale-fallback!`
(applies the URL value only when localStorage has no saved
preference); call site in `learn.client/init` CLJS branch.

### Service worker bypasses cache for `/js/main/*` on localhost

**Difference:** The SW's cache-first branch skips
`/js/main/cljs-runtime/*.js` chunks when the hostname is
`localhost`, so shadow-cljs hot-reload always serves fresh
chunks during dev. Production hosts still get the full PWA cache.

**Why:** Without the bypass, the SW pinned the dev browser to
whichever JS shadow-cljs wrote on first visit, masking every
subsequent code change. Not a JS-port divergence per se — the JS
port has the same cache-first SW — but a Fulcro-port-only dev
quality-of-life fix worth recording.

**Where:** `resources/public/sw.js` fetch handler.

---

## Where things are **the same** (worth knowing)

For symmetry, a couple of major design points where we deliberately
*don't* diverge:

- **URL encoding chain.** `btoa(encodeURIComponent(JSON.stringify(items)))`
  matches the JS port byte-for-byte for our supported shapes (empty
  list and single-:ready-item fixtures pinned).
- **Status icons.** Same SVG paths from Font Awesome (extracted in
  `learn.ui.icons`), same `statusToSymbol` cancel-fallback recursion.
- **AutoFocus algorithm.** The benchmark / auto-mark / cancel /
  clone semantics are documented in [`SCHEMA.md`](./SCHEMA.md) and
  match the JS port (with the JS-discrepancies enumerated there
  fixed).
