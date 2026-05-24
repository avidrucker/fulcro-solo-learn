# Phase 19c — `<html lang>` sync

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

New `learn.client.lifecycle/sync-html-lang!` / `install-html-lang-sync!` pair (CLJS-only, parallel to the body-theme watch) keeps `<html lang>` aligned with `[:list/id 1 :ui/locale]` so screen readers pick the right voice. Mapping is locale-keyword `name` → IETF tag (1:1 for :en/:es/:ja). Commit `e445453`.
