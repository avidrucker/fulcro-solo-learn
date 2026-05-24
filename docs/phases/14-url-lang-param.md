# Phase 14 — `?lang=<code>` URL-level locale hint

**Status:** ✅ Complete

Closes `S-i18n-url-locale`. Adds a URL query-param entrypoint so publishers can write locale-specific links (e.g. `https://avidrucker.github.io/fulcro-solo-learn/?lang=ja` for "the app, in Japanese") without breaking the list-share flow.

Path-based routing (`/jp/`, `/es/`) was considered and rejected: GitHub Pages doesn't natively SPA-route, so the path approach would have needed a `404.html` redirect trick, hash-routing, or per-locale duplicated `index.html`. Query param is one-line URL parsing, coexists cleanly with `?list=`, no hosting changes. See `docs/changes.md` for the user-facing summary and `docs/user_stories.md` `S-i18n-url-locale` for the precedence rule write-up.

Precedence: `localStorage :ui/locale > URL ?lang= > :en`. Saved preferences always win over URL hints, so list-share links (`?list=…`) never override the recipient's chosen language. First-time visitors following `/?lang=es` get Spanish, and that becomes their saved preference for the next visit.

- **Pure parser** in `learn.util.url-encoding`: `parse-lang-param` (private) extracts the raw value; `locale-from-url-search` validates against `i18n/supported-locales` and returns a keyword or nil. Case-insensitive code normalisation (`?lang=ES` → `:es`).
- **CLJS-only wrapper** `locale-from-current-url` reads `window.location.search`.
- **Lifecycle integration** `learn.client.lifecycle/install-url-locale-fallback!` runs in the CLJS `init` AFTER `storage/install-ui-prefs-persistence!`. It reads the raw ui-prefs slice from localStorage; if `:ui/locale` isn't there (first-time visitor) AND the URL has a valid `?lang=`, swap it into state. The storage save-watch is already attached, so the URL-derived locale persists on the next state change. JVM branch: no-op.
- **Copy List URL** unchanged — the share-URL helper (`learn.client.ui.modals/copy-list-url!`) still only writes `?list=…`. Shared lists stay locale-neutral.

**Numbers**: 104 → 105 specs / 728 → 741 assertions, all green. 1 new spec / 13 new assertions in `url-encoding-test:locale-from-url-search` covering happy paths (`:en` / `:es` / `:ja`, case insensitivity, coexistence with `?list=`) and failure paths (unsupported / empty / malformed input).

**Implements**: `S-i18n-url-locale`. No new bugs surfaced; no JS-port equivalent (the OG ReactJS app is English-only).
