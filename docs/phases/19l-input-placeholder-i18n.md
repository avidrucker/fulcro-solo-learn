# Phase 19l — Localize new-todo input placeholder + accessible name

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

The page-level new-todo input had a hardcoded English placeholder ("Type new task here") AND a hardcoded English clip-hidden label ("New TODO:") regardless of locale — Spanish / Japanese users got an English-named input on focus and an English placeholder when the field was empty.

Added two i18n keys × three locales (`:input/new-todo-placeholder`, `:input/new-todo-label`) and extended `learn.rad.input/text-input` with an optional `:placeholder` override (was previously fixed to the attribute's `:field/label`). Call site passes both keys via `(i18n/tr locale ...)` so the strings flip with `:ui/locale`.
