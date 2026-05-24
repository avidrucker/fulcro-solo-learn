# Phase 7.7 — Theme toggle (light/dark)

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`lightbulb-solid` + `lightbulb-regular` SVGs added. `:ui/theme` lives on `[:list/id 1]` (default `:theme/light`). Toggled by a 4th header icon — `lightbulb-solid` while in light mode, `lightbulb-regular` in dark mode (matches the JS port's "the icon shows what theme is currently active").

Six private theme helpers (`dark?`, `theme-text-class`, `theme-modal-bg-class`, `theme-input-class`, `theme-primary-btn-suffix`, `theme-icon-btn-color`) return the appropriate class suffix for the current theme. The existing `btn-primary-class`, `btn-primary-dim-class`, `input-class`, `review-btn-class`, `btn-icon-class`, and the save-modal helpers all became 1-arg fns of theme. `modal-shell` accepts `:theme` in its options map. Theme is threaded explicitly to all callers (no React context / dynamic var).

Theme propagation:
- `Root` reads theme from `(:ui/theme list)` → applies `theme-text-class` to `<main>` → renders the lightbulb toggle.
- `TodoList` destructures `:ui/theme` from its props with `:or {theme :theme/light}` defaulting, passes to TodoItem via `comp/computed`, passes to modal helpers as an arg.
- `TodoItem` destructures `:theme` from computed; `btn-icon-class theme` picks `moon-gray` vs `mid-gray` for the per-row buttons.

3 new specs cover the state-helper flips and the toggle-via-click behaviour. 49 specs / 383 assertions, all green. CLJS: 326 files, 0 warnings.

Implements **S-theme-toggle**.
