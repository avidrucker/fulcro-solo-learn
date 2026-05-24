# Phase 19m — Theme-toggle direction labeling + aria-pressed

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

The theme-toggle button's accessible name was "Toggle Theme" regardless of state. Now reads "Switch to dark mode" (when light is active) / "Switch to light mode" (when dark is active), localized. Also added `aria-pressed="true"` when dark mode is active, `"false"` when light is active — the explicit ARIA toggle-state announcement that complements the icon flip (which is aria-hidden, so AT users can't infer state from it).

The generic `:tooltip/toggle-theme` key stays in the registry for back-compat (other callers may exist in future), but the toggle button itself now uses the direction-aware pair `:tooltip/switch-to-dark` / `:tooltip/switch-to-light`.
