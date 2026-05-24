# Phase 12.5 — Language dropdown in Settings

**Status:** ✅ Complete
**Parent:** [Phase 12 — i18n + visual polish + facade refactor](12-i18n-and-refactor.md)

Language dropdown in Settings. New `set-locale*` pure state-helper + `learn.client/set-locale` mutation (client-only; no remote). `<select>` populated from `i18n/supported-locales` with `i18n/locale-label` driving option text in each language's own script (English / Español / 日本語). onChange fires set-locale; modal heading + language label re-render in the new locale on the next frame.

## 12.5b — Extended translation coverage + dark-mode dropdown fix

Extended translation coverage + dark-mode dropdown fix. Info / Settings / Save modal body copy all go through `i18n/tr` now (about copy, instructions, version label, close-instruction footers, save button labels, textarea placeholder). Dark-mode `<select>` options panel was rendering white-on-white on Chromium/Windows where `color-scheme: dark` alone isn't enough — fixed by inline `background-color` + `color` on each `<option>` when the theme is dark.

## 12.5c — Three visual fixes

Three visual fixes:
1. **Modal textarea/select hover/focus**: introduced `theme/theme-modal-input-class` — gray-at-rest matching the primary-button bg, snap to solid black/white on hover/focus. `theme-input-class` keeps the page-level new-todo input verbatim with the JS port (transparent fade).
2. **Modal overlay extent**: dropped `height: 100%` from the html/body/#app root chain in `app.css` (kept `min-height: 100% / 100dvh`) so the root grows with overflow content; changed `.app-container` from `h-100` to `flex-1` so it fills available space AND grows with content; changed the overlay from `top-0 w-100 h-100` to `top-0 bottom-0 left-0 right-0` so it tracks `.app-container`'s full height. Header stays visible (it's outside `.app-container`), so icons remain reachable; Fulcro port now behaves better than the OG on this specific case (the OG's overlay still stops short at viewport height).
3. **Info + Settings modal bottom padding**: `pb3` on the close-instruction paragraph so the bottom text has breathing room. Save modal already had this; carried the same pattern.
