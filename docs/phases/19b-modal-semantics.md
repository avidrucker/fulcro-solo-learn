# Phase 19b — Modal dialog semantics

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

`modal-shell` now emits `role="dialog"` + `aria-modal="true"` on every modal; opt-in `aria-labelledby="<id-of-title>"` extension wired through to every caller, with stable IDs on each modal's heading/question element (info-modal-title, settings-modal-title, save-modal-title, delete-confirm-question, locale-conflict-question, list-conflict-question, review-question). Commit `bb0e44b`.
