# Phase 7.8 — Visual comparison vs the deployed reference

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`scripts/snapshot.mjs` grew a `--url <url>` flag — passing an external URL bypasses the localhost dev server and saves the snapshot under `docs/snapshots/reference/<label>.png` (no git-hash prefix; the deployed app's UI state isn't tied to our commits). Captured the deployed JS port at `?list=JTVCJTVE` (base64 of `[]` — empty list) for an apples-to-apples eyeball comparison.

`docs/snapshots/reference/README.md` documents the workflow and is the running diff log between our local and the deployed reference. Eyeball pass against `6f992c0-phase-7.8-local-empty-dark.png` turned up one mechanical fix: the JS port pads the FIRST header icon with `pl3` and the rest with `pl2`. Wired via a `:first?` flag on `header-icon-button`.

Future ratchet: a `pixelmatch`-based diff script would automate the side-by-side diff. Not implemented; out-of-scope unless we want visual-regression gating.

Implements **S-deployed-reference-comparison** (new story).
