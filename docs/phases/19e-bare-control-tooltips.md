# Phase 19e — Localized tooltips on bare interactive controls

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

Four controls that previously had no accessible name beyond their visible label now carry both `:title` (hover) and `:aria-label` (screen reader) sourced from `learn.i18n.core`:

- `:tooltip/include-lang` — "Include language in link" checkbox. Locked en wording: "When checked, the share link will open in this app's current language for whoever clicks it."
- `:tooltip/import-json` — JSON import button (styled label that triggers the hidden file input).
- `:tooltip/submit-text-import` — text-list import submit button under the textarea.
- `:tooltip/language-dropdown` — language `<select>` in the settings modal.

Added a guard spec (`tr — Phase 19e tooltip keys`) that asserts each key resolves to a real translation (not the keyword-as-string fallback) in :en/:es/:ja, plus an exact-match assertion on the locked include-lang en string.
