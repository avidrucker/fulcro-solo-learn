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
2. **Header icon sizing — second pass** — after seeing the deployed
   HTML at `docs/html_snapshots/snapshot_not_prioritizable_error.html`
   (gitignored), discovered the JS port carries `pl3`/`pl2` on a
   wrapper `<div>` and `pa1 w2 h2` on the button (no `pl` class on
   the button itself), with `info-circle`/`question-circle` SVGs
   having **no** explicit width/height. Matched the structure
   exactly — header now uses a wrapper-div pattern and `info`/`question`
   SVGs drop their `height` attribute. Icons render uniformly.
3. **New-todo input width** — Tachyons sets `* { box-sizing: inherit
   }` with `html { box-sizing: border-box }`, but a browser
   user-agent stylesheet was overriding it to `content-box` on
   `<input>`, so the `pa2` padding and `bw1` border were adding ~20px
   to the visible box width (`Playwright getBoundingClientRect`
   showed `340px` actual vs `320px` from `measure-narrow`). Forced
   `box-sizing: border-box` on `input, textarea, select` in
   `resources/public/css/app.css`. Input is now 320px exactly,
   centered with the buttons row.

### Minor / accepted

- **`save-disk` glyph density** — its viewBox is `0 24 448 472`, so
  the glyph itself doesn't fill the full 1.5rem height; visually a
  hair smaller than the other circle icons. Matches the deployed
  (both use the same source SVG), so we accept it.

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
