# Phase 18 — Locale-conflict modal (S-language-conflict-modal)

**Status:** ✅ Complete

Closes a UX gap surfaced after Phases 14 + 17 landed: when a user has a saved locale (e.g. English) and someone sends them a list link with `?lang=es`, Phase 14's silent-apply rule (saved wins, URL silently ignored) means the sender's intent never reaches the recipient. Phase 18 adds an explicit non-cancellable resolution modal whenever saved and URL disagree.

The modal asks "Which language do you want to use? / ¿Qué idioma quieres usar?" (bilingual, so either reader can answer) and offers two buttons labelled in their own scripts (`English` / `Español` / `日本語`). After the user picks, `:ui/locale` is set, the modal closes, and the address bar's `?lang=` is rewritten to match — so a reload doesn't re-trigger the modal.

**Decision matrix** (`locale-decision`):

| saved | url | result |
|---|---|---|
| nil | nil | `:no-op` |
| nil | :es | `:apply` (silent — Phase 14 behaviour) |
| :en | nil | `:no-op` |
| :en | :en | `:no-op` (no conflict) |
| :en | :es | `:conflict` (modal opens) |

The `:apply` path stays from Phase 14 — first-time visitors following `/?lang=ja` still get Japanese without a prompt.

**Pieces**:
- `learn.util.url-encoding/locale-decision` — pure dispatcher, JVM-testable. Returns `{:action :apply :locale ...}` / `{:action :conflict :saved ... :url ...}` / `{:action :no-op}`.
- `learn.util.url-encoding/replace-lang-param` — pure URL-query rewriter (overwrites/removes `lang=`); JVM-testable.
- `learn.util.url-encoding/update-current-url-lang!` — CLJS-only side-effect wrapper around `history.replaceState`.
- `learn.client.state/set-locale-conflict-pair*` + `keep-locale*` — pure state helpers.
- `learn.client/keep-locale` defmutation — client-only; state swap via `keep-locale*` + CLJS-only `replaceState`.
- `learn.client.ui.modals/locale-conflict-modal` body — bilingual question + two locale buttons. No `:on-close`, no full-area dismiss (same UX shape as the list-conflict modal).
- `learn.client.lifecycle/install-url-locale-fallback!` — extended to dispatch on the three-way `locale-decision` result instead of just the binary "saved present?" check.
- `:locale-conflict/question` i18n key — added in all three locales.
- `:locale-conflict` joins `:delete-confirm` and `:conflict` in the `menu-disabled?` set in Root, so header icons hard-disable while the modal is up.

**Numbers**: 113 → 119 specs / 769 → 794 assertions, all green via fresh JVM. New specs: `replace-lang-param` (URL builder), `locale-decision` (4-case decision), `set-locale-conflict-pair*` (state helper), `keep-locale*` (state helper), `keep-locale mutation` (state round-trip + client-only check), `Locale-conflict modal renders both locale labels` (UI rendering check).

**Implements**: `S-language-conflict-modal`. Completes the Phase 14 / Phase 17 / Phase 18 i18n-URL round-trip:
- 14: incoming URL → silent apply for new visitors
- 17: outgoing Copy URL → opt-in language stamp
- 18: incoming URL with conflict → user resolves explicitly

**Surfaces**: `B-10` (existing conflict modal button layout) and `B-11` (empty-vs-non-empty conflict-modal trigger) — both logged this phase but not fixed; next-up after Phase 18 ships.
