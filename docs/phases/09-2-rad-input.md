# Phase 9.2 — RAD-driven Add Item input

**Status:** ✅ Complete
**Parent:** [Phase 9 — Fulcro RAD basics](09-rad-basics.md)

New `learn.rad.input/text-input` helper reads `:field/*` metadata from an attribute and renders the Tachyons-styled input + `clip`-hidden label. The Add Item input swaps from a 5-key inline `dom/input` to a 7-key call to the helper. Visual output identical; source of truth for placeholder + maxlength moved from hard-coded literals to attribute metadata. Browser-manual verification confirmed type + Add Item + list-update flow unchanged.
