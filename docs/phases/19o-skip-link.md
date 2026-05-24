# Phase 19o — Skip link for keyboard users

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

WCAG 2.1 §2.4.1 (Bypass Blocks). A keyboard-only user previously had to Tab through the four header icon buttons (save, info, settings, theme toggle) before reaching the primary content. Added a "Skip to main content" `<a>` as the first focusable element on the page — hidden off-screen via fixed positioning by default, slides into view on `:focus`. Pressing Enter sends focus to a new `#main-content` target on the `app-container` section (now `tabindex="-1"` so programmatic focus works).

i18n key `:nav/skip-to-main` × en/es/ja. New CSS rules in `app.css` for `.skip-link` + a dark-theme variant gated on `body.bg-black` (the existing body-class theme sync — Phase 12.5 — already provides this).
