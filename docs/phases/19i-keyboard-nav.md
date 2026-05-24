# Phase 19i — Keyboard-only navigation sweep (largely automated)

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

The Playwright suite (`e2e/keyboard-and-a11y.spec.js`) now covers tab order through the header (`19i — header tab order`), modal focus management for all dismissible + non-dismissible + review modals (`19g + 19h` and `19g (ext)` describes), Escape behavior on each, and a full golden-path sweep (`19i — keyboard-only golden path`: add → prioritize → review :yes → mark done → delete list, every step keyboard-activated).

Remaining browser-manual check: **visible focus indicator** — that every focusable element gets a visible outline on focus. axe-core doesn't check this and Playwright can't infer "is it visually distinct from the surrounding UI." The remaining ~10-minute keyboard pass is exclusively this visual property. Listed in `docs/manual_tests.md` §19i.3.
