# Phase 19d — Decorative SVG icons

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

`learn.ui.icons/svg-attrs` now sets `aria-hidden="true"` + `focusable="false"`, applied via merge to all eleven icons (status icons, header icons, lightbulbs, gear, cancel-x, repeat-arrow). Prevents screen readers from double-reading button labels alongside a generic "graphic" announcement.
