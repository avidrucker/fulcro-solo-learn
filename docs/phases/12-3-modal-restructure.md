# Phase 12.3 — Modal restructure (Info + Settings)

**Status:** ✅ Complete
**Parent:** [Phase 12 — i18n + visual polish + facade refactor](12-i18n-and-refactor.md)

Modal restructure. About + Help merged into one Info modal under the existing `i` icon (the `?`-Help button dropped from the header). New Settings modal under a new gear icon, body intentionally empty in 12.3 — populated in 12.5. `:ui/open-modal` enum gains `:info` and `:settings`, loses `:about` and `:help`.
