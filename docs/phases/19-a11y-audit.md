# Phase 19 — a11y / Section 508 audit pass

**Status:** 🟡 In progress

Programmatic accessibility pass. Living artifact: `docs/a11y_audit.md`, which holds the full Section-A (in-codebase) / Section-B (user must run) split and the per-sub-phase notes. The Section-A sub-phases (19a–19p) below are all complete; Section-B (Lighthouse, axe, WAVE, NVDA/VoiceOver, keyboard, zoom, reduced-motion, contrast measurement) remains a user-driven handoff list tracked in `docs/a11y_audit.md` as `S-ux-a11y-review-pass` in `docs/user_stories.md`.

## Sub-phases

- ✅ [19a — Tooltip / aria-label / close-label i18n migration](19a-tooltip-i18n.md)
- ✅ [19b — Modal dialog semantics](19b-modal-semantics.md)
- ✅ [19c — `<html lang>` sync](19c-html-lang-sync.md)
- ✅ [19d — Decorative SVG icons](19d-decorative-svgs.md)
- ✅ [19e — Localized tooltips on bare interactive controls](19e-bare-control-tooltips.md)
- ✅ [19f — Status-icon accessible names](19f-status-icon-names.md)
- ✅ [19g — Focus management on modal open/close](19g-modal-focus.md)
- ✅ [19h — Escape-to-close on dismissible modals](19h-escape-to-close.md)
- ✅ [19i — Keyboard-only navigation sweep (largely automated)](19i-keyboard-nav.md)
- ✅ [19j — Color-contrast pass on dark theme](19j-color-contrast.md)
- ✅ [19k — Error banner is an ARIA live region](19k-error-banner-live-region.md)
- ✅ [19l — Localize new-todo input placeholder + accessible name](19l-input-placeholder-i18n.md)
- ✅ [19m — Theme-toggle direction labeling + aria-pressed](19m-theme-toggle-direction.md)
- ✅ [19n — Per-element `lang` attrs for cross-locale text](19n-per-element-lang.md)
- ✅ [19o — Skip link for keyboard users](19o-skip-link.md)
- ✅ [19p — Respect `prefers-reduced-motion`](19p-reduced-motion.md)
