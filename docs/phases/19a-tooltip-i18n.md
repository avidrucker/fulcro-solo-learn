# Phase 19a — Tooltip / aria-label / close-label i18n migration

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

All button accessible names that were still hardcoded English (modal close-buttons, header-button tooltips, the four primary action buttons, six row-action variants) routed through `learn.i18n.core` and pulled via `i18n/tr`. Each button now has localized `:title` and `:aria-label` pulling the same key. Commit `7d37ea6`.
