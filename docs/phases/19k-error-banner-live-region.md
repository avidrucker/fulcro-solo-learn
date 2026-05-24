# Phase 19k — Error banner is an ARIA live region

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

Found while extending 19g/h to the review modal: the page-level error `<p>` that renders when `:ui/err-msg` is truthy had no `role` / `aria-live`. Screen readers silently see the new node and skip it. Added `role="alert"` (shorthand for `aria-live="assertive" aria-atomic="true"`) so any new error announces immediately. The render condition `(when err-msg ...)` is unchanged — the node only exists while there's an error, which is the correct shape for `role="alert"`.
