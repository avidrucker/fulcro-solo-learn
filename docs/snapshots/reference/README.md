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

Local: `docs/snapshots/6f992c0-phase-7.8-local-empty-dark.png`
Reference: `docs/snapshots/reference/empty-list-deployed.png`

Eyeball pass on the two PNGs:

1. **Header icon spacing.** The JS port's reference shows the icons
   spaced slightly differently from the title. Our port currently uses
   `pl2` on every icon button; the JS source uses `pl3` on the FIRST
   icon and `pl2` on the rest. Tiny visual offset.
2. **Empty-state footer line.** The deployed shows
   "You have 0 items in your list." with a clear gap to the button
   row; our spacing is similar but the JS port's footer block uses
   `pt2 pb3` on the wrapper, which our local already does — so this
   may just be a rendering nuance from different browsers or fonts.

> If you spot additional differences when eyeballing, add a row here.
> Bullet-point style is fine; this doc is a log, not a spec.
