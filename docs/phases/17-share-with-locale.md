# Phase 17 — Include-language checkbox for Copy List URL

**Status:** ✅ Complete

Closes `S-i18n-share-with-locale` (a new user story added this phase). Completes the Phase 14 round-trip — Phase 14 added URL parsing for `?lang=<code>` on incoming links; Phase 17 gives the sharing user a way to ACTUALLY produce such links from the UI.

**Why a checkbox** (opt-in default-off): forcing your locale on recipients overrides their preference if they haven't saved one yet, and most sharing flows don't actually want that. Opt-in respects the recipient. See `docs/changes.md` for the divergence note (the OG has no i18n, so this entire flow is Fulcro-port-only).

- **`learn.util.url-encoding/list-share-url`** — 4-arity overload. Accepts an optional locale; appends `&lang=<code>` only when non-nil. 3-arity remains for callers that don't stamp. Round-trips with `locale-from-url-search` (Phase 14).
- **`learn.client.state/set-share-with-locale*`** + matching `learn.client/set-share-with-locale` defmutation. Client-only; the value is `:ui/share-with-locale?` on `[:list/id 1]`.
- **Persistence** — `:ui/share-with-locale?` joins `:ui/theme` / `:ui/locale` in `learn.util.storage/ui-prefs-whitelist`. Once toggled on, it stays on across reloads.
- **i18n** — `:save/include-lang` key added to all three locales (`:en` "Include language in URL" / `:es` "Incluir idioma en la URL" / `:ja` 「URLに言語を含める」).
- **UI** — `<input type="checkbox">` in the save modal (`learn.client.ui.modals/save-modal`), positioned ABOVE the Copy List URL button. Reads `share-with-locale?` from TodoList's props; `onChange` fires the mutation. The Copy URL button reads the same flag and passes locale (or nil) into `copy-list-url!`.

**Numbers**: 109 → 113 specs / 752 → 765 assertions, all green via fresh JVM. New specs: `list-share-url — with optional locale` (URL builder + Phase-14 round-trip), `set-share-with-locale*` (pure helper + affects-only), `set-share-with-locale mutation` (state update + no-remote confirmation), `Save modal — Include-language checkbox` (label renders in both `:en` and `:es`).

**Implements**: `S-i18n-share-with-locale`. Completes the language-share UX loop introduced in Phase 14.
