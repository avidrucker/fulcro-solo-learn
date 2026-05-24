# Phase 19j — Color-contrast pass on dark theme

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

axe-core surfaced two distinct failures the first time the suite ran:

  (a) Dim primary buttons (Add Item / Delete List / Prioritize / Mark Done in their dim-but-clickable state). The old pattern was `bg-moon-gray black o-50` (active suffix + 50% opacity) = 3.16:1 — below WCAG AA 4.5:1. Replaced with explicit lighter-bg + lighter-text suffix (`theme-primary-dim-btn-suffix` in `theme.cljc`):
        Light: `bg-light-gray dark-gray`   ≈ 11.6:1
        Dark:  `bg-mid-gray near-white`    ≈ 7.1:1
      Divergence note: the JS port uses `o-50`; we diverge for a11y compliance.

  (b) GitHub Issues link inside the Info modal. Old: Tachyons `blue` (#357edd) on white = 4.05:1 — just under AA. Now theme-aware in `info-modal`:
        Light: `dark-blue`  (#00449e) on white     ≈ 10:1
        Dark:  `light-blue` (#96ccff) on near-black ≈ 11.5:1

The Playwright spec asserts ZERO axe violations at each page state (initial + 4 open modals) — any future contrast regression breaks the suite. Dark-theme spot-check across more surfaces (review modal + non-default themes for the per-row status icons + the dim row indicators on done/cancelled items) stays browser-manual; the dominant systemic violation is fixed.

The Section-B handoff list (Lighthouse, axe, WAVE, NVDA/VoiceOver, keyboard, zoom, reduced-motion, contrast measurement) lives in `docs/a11y_audit.md` and tracks as `S-ux-a11y-review-pass` in `docs/user_stories.md`.
