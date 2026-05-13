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
