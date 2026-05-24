# Phase 7.4 — Modal state foundation

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`:ui/open-modal` lives on `[:list/id 1]`. Default `:none`. Other values: `:about`, `:help`, `:save` (added in 7.5/7.6).

`set-open-modal*` is single-value, so it's mutex-by-construction — opening any modal overwrites whatever was open. `toggle-open-modal*` wraps it: if the requested modal is currently open, set to `:none`; otherwise open. Defmutations `set-open-modal` and `toggle-open-modal` expose both to transact!.

The existing `modal-shell` already supports `:on-close` — the per-modal phases (7.5/7.6) wire it to `(toggle-open-modal :none)` shorthand: `(comp/transact! this [(set-open-modal {:ui/open-modal :none})])`.

7 new specs cover open/replace/close/toggle/mutex. 44 specs / 353 assertions, all green. CLJS: 326 files, 0 warnings.

Implements **S-modal-mutex**, **S-modal-bg-close** (via existing `modal-shell` `:on-close`), **S-modal-toggle-via-button** (via `toggle-open-modal*`).
