# Phase 19h — Escape-to-close on dismissible modals

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

New `install-escape-to-close!` window-keydown listener fires `set-open-modal :none` when Escape is pressed AND the active modal is in `#{:info :settings :save :delete-confirm}`. The two conflict modals stay non-dismissible by design. Routes through the existing mutation so any future side effects of closing stay consistent.
