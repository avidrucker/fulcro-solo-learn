# Reference snapshots

PNGs of the deployed JS port (`avidrucker/pwa-autofocus-app`), captured by
`scripts/snapshot.mjs --url ...`. These are the "ground truth" that our
Fulcro port is aiming to match visually.

Filenames here drop the git-hash prefix used by our own snapshots —
external screenshots aren't tied to our commits.

## How to add a new reference

```bash
npm run snapshot -- <label> --url '<https-url>'
# saves to docs/snapshots/reference/<label>.png
```

The `--url` flag bypasses the localhost dev server and pins the
deployed app's UI state via query params (the JS port serializes the
list as base64 in `?list=...`).

## Quick decode of the test URLs

The JS port encodes the list array as base64 of its JSON form:
- `JTVCJTVE` = `%5B%5D` (url-encoded `[]`) — empty list
- (Other reference URLs would be longer; the JS port has a
  `MAX_URL_LENGTH = 8000` cap.)

## Current references

| File | URL | State |
|---|---|---|
| `empty-list-deployed.png` | `https://avidrucker.github.io/pwa-autofocus-app/?list=JTVCJTVE` | Empty list, dark theme (deployed default) |
| `phase-7.12-og-delete-confirm-dark.png` | `https://avidrucker.github.io/pwa-autofocus-app/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCU1RA==` then click Delete List | Delete-confirm modal over 1-item list, dark theme |

## How to diff against our local

For now, eyeball comparison: open the reference PNG next to our local
snapshot (`docs/snapshots/<hash>-phase-7.8-local-empty-dark.png` and
friends). A programmatic pixel-diff via `pixelmatch` is a possible
future addition (see Phase 7.8 in `phases.md`).

## Diff log — empty-list state (local 7.8 vs deployed)

Reference: `docs/snapshots/reference/empty-list-deployed.png`
Local (latest, after 7.8 fixes): `docs/snapshots/af49c75-phase-7.8-local-empty-dark.png`

### Confirmed fixed (this phase)

1. **Header icon sizing — first pass** — `info-circle` and
   `question-circle` were `width: 1.25rem` while `save-disk` and
   `lightbulb-*` used `height: 1.5rem`, making the first two visually
   smaller. First fix normalized to `height: 1.5rem` across all four.
2. **Header icon structure** — after seeing the deployed HTML at
   `docs/html_snapshots/snapshot_not_prioritizable_error.html`
   (gitignored), discovered the JS port carries `pl3`/`pl2` on a
   wrapper `<div>` and `pa1 w2 h2` on the button (no `pl` class on
   the button itself), with `info-circle`/`question-circle` SVGs
   having **no** explicit width/height. Matched the structure exactly.
3. **`save-disk` viewBox typo (root cause of "save-disk looks small")** —
   side-by-side diff of the two markup blocks made it obvious: the JS
   port has `viewBox="0 24 448 472"` (Font Awesome's intended
   y-offset), our port had `viewBox="0 0 448 472"`. The lost y=24
   offset meant the top 24/472 ≈ 5% of the rendered area was empty
   viewBox space, shrinking the visible glyph. Fixed at the source —
   not a CSS layout problem.
4. **Missing `type="button"` on header buttons** — also surfaced by
   the markup diff. The JS port sets it explicitly; ours relied on
   the default (`type="submit"` inside a form, `"button"` otherwise).
   Our header icons sit outside the `<form>` so behaviour is fine
   today, but adding the attribute is defensive against any future
   refactor that nests them inside one.
5. **New-todo input width** — Tachyons sets `* { box-sizing: inherit
   }` with `html { box-sizing: border-box }`, but a browser
   user-agent stylesheet was overriding it to `content-box` on
   `<input>`, so the `pa2` padding and `bw1` border were adding ~20px
   to the visible box width (`Playwright getBoundingClientRect`
   showed `340px` actual vs `320px` from `measure-narrow`). Forced
   `box-sizing: border-box` on `input, textarea, select` in
   `resources/public/css/app.css`. Input is now 320px exactly,
   centered with the buttons row.

### Known intentional differences

- **Extra `<span class="clip">` inside each header button** — keeps
  the label text in the DOM so `h/click-on-text!` finds the button
  by its accessible name. The JS port doesn't have this; we accept
  one extra DOM element per icon button in exchange for headless-test
  ergonomics. Could be removed if we switch the tests to find by
  `:aria-label` or `:title`.

## Diff log — delete-confirm modal (local 7.12 vs deployed)

Reference: `docs/snapshots/reference/phase-7.12-og-delete-confirm-dark.png`
Local (post-fix): `docs/snapshots/47d2cad-phase-7.12-delete-confirm-modal-dark.png`

### Confirmed fixed (this pass)

6. **Page background shade** — og uses `bg-black` on `<body>` (#000);
   we were using `bg-near-black` on `<main>` (#111). Visible below
   the modal where the page bg shows through. Swapped to `bg-black`
   on `<main>` for dark mode. (`theme-page-bg-class` in `client.cljc`.)
7. **Yes/No button width in delete-confirm modal** — JS UI reference
   line 99 specifies `w4` for delete-confirm buttons; we'd reused
   `review-btn-class` which uses `w3`. Added `delete-confirm-btn-class`
   with `w4`.
8. **Root-stack font smoothing + flex column** — og's `index.css`
   sets `-webkit-font-smoothing: antialiased`,
   `-moz-osx-font-smoothing: grayscale`, `display: flex; flex-direction:
   column`, and `min-height: 100dvh` on `html, body, #root`. Ported
   the same recipe (substituting `#app`) into our `app.css`. Text
   renders thinner; the dark bg reliably fills the viewport on all
   browsers.

### Outstanding (deliberately not fixed)

- **Modal inner padding** — og's inner section is bare
  `measure-narrow ml-auto mr-auto`; our `modal-shell` wraps with
  `relative z-1 pa3`. The extra `pa3` reduces usable text width and
  pushes the body `<p>` to wrap to 3 lines instead of og's 2. Could
  be lifted by making `pa3` opt-in on `modal-shell`, but the same
  helper is shared by About/Help/Save modals which look fine today —
  parking until we decide all modals should match exactly.
- **Body `<p>` styling** — og uses bare `<p class="lh-135">`; we
  add `ma0 pb3 lh-135 tc`. The `tc` is the only behavioural change
  (center vs left-align); the others (`ma0`, `pb3`) just tighten
  spacing. Left as-is for now.
- **Background-close button** — og's delete-confirm has no
  transparent overlay; we added one (clicking outside cancels).
  This is a UX choice we kept; the original markup forces an
  explicit Yes/No.

### Diffing methodology

Side-by-side markup comparison is the highest-signal first pass —
it surfaces typos (Diff 3 above) and structural mismatches (Diff 2)
that pixel-level eyeballing tends to miss. For computed-style
differences (Diff 5, the `box-sizing` issue), use Playwright:
`page.locator(selector).evaluate(el => window.getComputedStyle(el))`.

### Eyeball process

1. `npm run shadow-cljs watch app` running, dev server on :8000.
2. `npm run snapshot -- <local-label> [--click <action>]*` for the
   local state matching the deployed's setup.
3. `npm run snapshot -- <ref-label> --url '<deployed-url>'` for the
   reference. Saves to `reference/<ref-label>.png` without a hash.
4. Open both PNGs side by side. When a difference is spotted, add a
   row above with the diagnosis. Use Playwright's
   `boundingBox`/`evaluate(getComputedStyle)` to compare actual
   computed values rather than eyeballing pixels.

> If you spot additional differences, add a row above. This doc is a
> log, not a spec.
