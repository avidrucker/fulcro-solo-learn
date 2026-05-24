# Phase 19f — Status-icon accessible names

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

New `learn.i18n.core/tr-status [locale status was]` (pure, JVM-testable) produces a localized accessible name for each todo row's status indicator. Cancelled rows surface the prior status in parentheses ("cancelled (was ready)" / "cancelado (antes: listo)" / "キャンセル済み（元：準備完了）"). The wrapping span on each row (in TodoItem AND the list-conflict modal preview) now has `role="img"` + localized `aria-label`, with the inner SVG still aria-hidden — single, meaningful announcement per row. Spec: `tr-status (parameterized — Phase 19f)` with 4 components.
