# Phase 16 — Close B-8: error messages translate with `:ui/locale`

**Status:** ✅ Complete

Mechanical follow-up to Phase 12.4 — the i18n infrastructure was in place, errors were the last user-visible English-only strings. Closes B-8.

Seven error keys added to `learn.i18n.core` in all three locales (`:en` / `:es` / `:ja`). English values are verbatim copies of the existing `learn.ui.strings/<name>-err` constants so the dozen-plus test assertions that compare against exact English text continue to pass. New :es and :ja translations cover idiomatic equivalents.

Keys added:
- `:err/empty-input` — blank-text Add Item
- `:err/nothing-to-delete` — Delete List on empty list
- `:err/cannot-take-action` — Mark Done with no actionable items
- `:err/not-prioritizable` — Prioritize on non-prioritizable list
- `:err/empty-textarea` — blank Submit on import textarea
- `:err/bad-json-import` — JSON file structure invalid
- `:err/non-json-import` — file isn't JSON

Call sites updated:
- `learn.client.ui.components/TodoList` — 5 `set-err!` sites switch from `s/<name>-err` to `(i18n/tr locale :err/<name>)`.
- `learn.client.ui.modals/import-json-file!` — both error branches + the FileReader onerror handler. Function signature gained a `locale` parameter (passed from save-modal's existing locale binding).

**Numbers**: 108 → 109 specs / 749 → 752 assertions, all green via fresh JVM. New spec: `TodoList errors — translate with :ui/locale` covering Spanish empty-input + Japanese nothing-to-delete (3 assertions).

**Reserved strings** (`max-list-length-err`, `invalid-query-params-err`, `export-fail-err`) stay in `learn.ui.strings` for now — they aren't surfaced anywhere, no translation needed yet. Will revisit when / if they get wired into a real surfacing flow.

**Implements**: closes B-8. Every user-visible string the app shows the user is now localized for all three supported locales.
