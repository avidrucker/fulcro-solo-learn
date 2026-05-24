# Phase 19g — Focus management on modal open/close

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

New `learn.client.lifecycle/install-modal-focus-sync!` watches the state-atom and, on `:none → modal-id` transition, snapshots `document.activeElement` then focuses the modal's heading element (looked up by id from a new `modal-id->heading-id` table; ids reused from 19b's `aria-labelledby`). Deferred via `setTimeout 0` so React mounts the modal DOM first. On modal close, focus restores to the snapshotted element. Covers the six `:ui/open-modal`-driven modals.

Extension: `install-review-modal-focus-sync!` does the same for the statechart-driven review modal, watching for `:review.state/active` entering/leaving the review session configuration in the state-atom. Shares the `prev-focus-element` ref with the menu-modal sync — safe because the two modal families are mutually exclusive by construction (Prioritize is disabled while menu modals are open, and menu modals are disabled while review is active).
