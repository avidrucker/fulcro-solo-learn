# Phase 19n — Per-element `lang` attrs for cross-locale text

**Status:** ✅ Complete
**Parent:** [Phase 19 — a11y / Section 508 audit pass](19-a11y-audit.md)

Sister to 19c (page-level `<html lang>` sync): three spots display text in a language that isn't the page's active locale — the language `<select>` options ("English" / "Español" / "日本語"), the locale-conflict modal's buttons, and its bilingual question. Without per-element `lang` attrs, screen readers voice the off-locale text with whatever voice the page-level `lang` points to (so a Japanese reader hears "Español" pronounced with the Japanese voice).

Changes:
  - Each `<option>` in the settings dropdown gets `:lang (name loc)`.
  - The locale-conflict question's `<p>` was a single string joining both locales' translations with " / " — split into two `<span lang>` segments per locale.
  - Each locale-conflict button gets `:lang (name loc)` since its content is the locale label in its own script.

No new i18n keys (the existing translations are correct text); just lang-attr threading.
