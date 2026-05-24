# Phase 19p — Respect `prefers-reduced-motion`

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

WCAG 2.3.3 (Animation from Interactions). The Phase 6 button-state transitions (`transition: all 0.2s ease-in` on `.hover-bg-light-gray` / `.hover-bg-dark-gray` etc.) are purely decorative — the rest/hover/focus background change itself carries the state info. When the user has expressed a reduced-motion preference at the OS level, the transitions suppress.

Implementation: universal-selector `@media (prefers-reduced-motion: reduce)` block in `app.css` that sets `transition-duration: 0.01ms` (effectively zero, but `transitionend` events still fire) and zeros animations. Pattern lifted from web.dev's reduced-motion guidance — catches all current transitions plus any future ones added without a separate change.

CSS-only; no Clojure changes; no spec changes. Browser-manual verification via DevTools → Rendering panel → Emulate CSS media feature `prefers-reduced-motion: reduce`.
