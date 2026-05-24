# Phase 12.4 — Hand-rolled i18n core

**Status:** ✅ Complete
**Parent:** [Phase 12 — i18n + visual polish + facade refactor](12-i18n-and-refactor.md)

Hand-rolled i18n integration. `learn.i18n.core` ships the canonical `:en`/`:es`/`:ja` translation map, `tr` lookup with fallback chain (requested → :en → key-as-string), and two parameterised fns for the pluralised footer lines (`tr-list-count`, `tr-next-actionable`). TodoList gains `:ui/locale`, Root threads it to the modal bodies, components swap curated `s/*` references for `(i18n/tr locale :…)`. Locale persists via `storage/ui-prefs-whitelist` (joined `:ui/theme`).
