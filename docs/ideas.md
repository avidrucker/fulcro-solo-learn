# Ideas

Speculative features and behaviour tweaks that aren't bugs (current
behaviour isn't wrong) and aren't on the phase roadmap yet. Logged
here so the trail isn't lost when they come up mid-conversation.

Each idea has a one-line summary, the motivating context, options if
relevant, and a "decide-when" pointer so we know what triggers a
real planning conversation.

---

## Modal auto-close

**Tag:** `modal-auto-close`
**Origin:** B-2 conversation (Phase 7.12 followup)
**Related:** [`S-import-batch-text`](./user_stories.md)

After a successful Submit on the Import/Export modal's batch
textarea, the modal currently stays open (B-2 fix). Two ideas
worth considering:

### Option A — Auto-close after successful Submit

Re-add the `close-current-modal!` call, but only on success. Pros:
clears the way for the user to see the imported items in the list.
Cons: forces a re-open if the user wants to import a second batch
or read the in-modal info-text again.

### Option B — User preference via the Settings modal

The Settings modal landed in Phase 12.3 (gear icon, dedicated
`:settings` open-modal value); Phase 12.5 populated it with the
language dropdown. So the *modal infrastructure* part of this
option is no longer speculative — what remains is the
auto-close preference itself plus deciding what other prefs to
slot in.

Today's preference inventory:
- Language (✅ landed, 12.5)
- Auto-close-modal-after-action (⬜)
- Default startup theme override (⬜ — currently last-used)
- Larger-text / zoom (⬜)
- PWA debug toggle (⬜ — see `S-pwa-debug-modal` in user_stories.md)

### Decide when

Pick this back up the next time a second preference candidate
becomes load-bearing (auto-close itself qualifies if a user
asks for it; or whenever S-pwa-debug-modal gets prioritised).
The Settings modal is no longer the blocker — the dropdown
layout already shows how a preference renders.

---

## RAD-vs-non-RAD side-by-side debug view

**Tag:** `rad-debug-side-by-side`
**Origin:** Phase 9.2 conversation (RAD basics)
**Related:** [`benefits-of-RAD-in-this-project.md`](./benefits-of-RAD-in-this-project.md)

When we did Phase 9.2 we replaced the Add Item input with the
attribute-driven `rad-input/text-input`. We *considered* rendering
both versions side-by-side behind a debug toggle so a curious
reader could A/B them visually — same value, same flow, just two
implementations.

We chose not to build it now because the swap was complete and
the comparison lives in the doc instead. Worth picking up if
either: (a) a real debug-mode toggle lands in the app (the JS port
has one for PWA diagnostics; see [`user_stories.md`](./user_stories.md)
S-pwa-debug-modal in 🆒), or (b) we add a second RAD-driven
component and want a visual regression check that the RAD path
still looks right.

### Sketch

- A `:ui/rad-debug?` flag in `[:list/id 1]` toggled by a hidden
  shortcut (Shift+R, say) or the future PWA debug modal.
- When true, render both the RAD input and a hand-rolled
  duplicate input side-by-side. Both controlled by the same
  `:ui/new-todo-text` so typing in one updates the other.
- Cleanup: a small visual delimiter ("RAD" / "non-RAD" labels)
  so the reader can tell which is which.

### Decide when

When debug-mode lands as a real feature (S-pwa-debug-modal
promotion ⬜ → ✅), OR when we add a second RAD component and want
a visual regression demo. Until then, the
`benefits-of-RAD-in-this-project.md` doc is the comparison.

---

## Debug-mode controls in Settings

**Tag:** `dev-mode-toggles`
**Origin:** B-14 conversation (2026-05-23) — debug CSS toggle started
as a static def, kept clean prod-safety via `goog.DEBUG`. Discussion
expanded the surface to four runtime-toggleable dev affordances.
**Related:** [`S-dev-mode-toggles`](./user_stories.md),
[`S-pwa-debug-modal`](./user_stories.md) (sibling debug surface,
PWA-specific).

A "Debug mode" section in the Settings modal, gated on `goog.DEBUG`
so prod builds drop it via Closure's dead-code elimination. Four
controls:

1. **Rainbow outlines toggle** — loads/unloads
   `css/pesticide.css` (different outline colour per element tag).
2. **Depth backgrounds toggle** — loads/unloads
   `css/pesticide-depth.css` (translucent bg colour per nesting depth).
3. **Dump app state** — button that logs the current Fulcro app
   state to the dev console TWICE: as a pprint'd EDN string (searchable
   text) and as `clj->js`-converted object (collapsible DevTools tree).
4. **List-fixture cycler** — button that cycles `server/SERVER-DB`
   through four list states: **actual → empty → 5-item → 26-item →
   actual** (and so on). All fixtures are schema-valid per
   `SCHEMA.md §5` (ready-before-new ordering).

### Architecture (decided)

- **`learn.dev-config.cljc`** — new namespace owning:
  - `dev-flags-defaults` (source-level initial values for the
    rainbow/depth toggles; e2e tests can flip these by editing source
    OR by clearing localStorage to get reproducible state)
  - `dev-flags` atom (runtime mutable, mirrored to localStorage at
    key `autofocus.dev-flags`; survives reloads)
  - `install-debug-css!` / `uninstall-debug-css-*!` helpers
  - List-cycler logic (with snapshot/restore — see below)
  - Whole module wrapped or guarded by `^boolean goog.DEBUG` so
    release builds drop it via DCE
- **`learn.dev-fixtures.cljc`** — pure data, ONE namespace for ALL
  fixture lists (so CLJS + JVM tests can share). Depends only on
  `learn.model.schema`. No `learn.client` or `learn.server`
  dependency — keeps the dep graph acyclic.
- **`learn.client.cljc`** — drops `debug-css-options` (moved to
  `dev-config`) and gains a `(when ^boolean goog.DEBUG ...)` call to
  `learn.dev-config/install-debug-css-from-runtime!` during init.

### Coexistence of source-default and runtime-UI toggle

The two layers don't fight if you treat them as **default vs.
override**:

| Layer | Where it lives | When it applies |
|---|---|---|
| Source defaults | `dev-flags-defaults` def in `learn.dev-config` | Only when `autofocus.dev-flags` localStorage entry is missing (fresh first load, or after clearing). |
| Runtime state | `dev-flags` atom + `autofocus.dev-flags` localStorage | Always when present. UI toggles swap! the atom, which persists. |

**Manual dev workflow**: defaults `false`, flip via Settings UI,
sticks across reloads. Clear localStorage to reset.

**Agentic e2e workflow**: clear localStorage in test setup →
defaults apply. To assert behaviour with debug visuals ON, either
flip source defaults `true` OR `page.evaluate(() =>
localStorage.setItem('autofocus.dev-flags', '...'))` before
navigation.

### List-cycler — snapshot/restore (preserves user data)

The cycler MUST NOT lose the user's actual list. Strategy:

- Track current cycle position in an atom (`:actual | :empty | :5 |
  :26`), persisted to `autofocus.dev-list-cursor` localStorage.
- **First cycle away from `:actual`**: snapshot the current
  `autofocus.server-db` to `autofocus.dev-list-snapshot`. Apply the
  next fixture (which goes through the existing persistence watch,
  so `autofocus.server-db` is overwritten by the fixture).
- **Subsequent cycles between fixtures**: just apply the next
  fixture; the snapshot stays untouched.
- **Cycle back to `:actual`**: restore from
  `autofocus.dev-list-snapshot` into `SERVER-DB`; delete the
  snapshot key.
- **Edge case (reload mid-cycle)**: page comes up with whatever
  fixture is in `autofocus.server-db`; cycle button still works;
  cycling back to `:actual` restores correctly because the snapshot
  key persisted too.

### Fixture defs (schema-valid)

All four cycle positions are schema-valid (preserve `SCHEMA.md §5`
ready-before-new ordering). Exact shapes per
`docs/SCHEMA.md` invariants:

- **`:actual`** — restored from `autofocus.dev-list-snapshot`.
- **`:empty`** — re-uses `learn.server/empty-state`.
- **`:5`** — five items, in schema-valid order:
  ```
  [cancelled (:todo/was :ready)
   cancelled (:todo/was :new)
   :done
   :ready
   :new]
  ```
  Active sequence (non-cancelled, non-done): `[:ready :new]` —
  satisfies ready-before-new.
- **`:26`** — 26 items `'a'`..`'z'`, first item `:ready`, rest
  `:new`. Active sequence: `[:ready :new × 25]` — satisfies the
  invariant.

### State dump format

Single button, dual output for searchability AND navigability:

```clojure
(let [state (app/current-state @SPA)]
  (.log js/console (with-out-str (cljs.pprint/pprint state)))
  (.dir js/console (clj->js state)))
```

The pprint'd string is Ctrl-F-searchable in DevTools; the
`clj->js` object is expandable tree. Either alone is awkward.

### A11y

- All controls keyboard-reachable + activatable via Enter/Space
  (standard `<button>` / `<input type=checkbox>`).
- ARIA labels in English (no i18n — dev only).
- Focus order: standard DOM order within the debug section, after
  the existing locale dropdown.

### Tests

| Surface | Test |
|---|---|
| Fixture data shape + invariants | fulcro-spec specs in `test/learn/dev_fixtures_test.cljc`. JVM-runnable. |
| `install-debug-css!` / `uninstall-debug-css!` idempotency | fulcro-spec spec mocking `js/document` or e2e probe. |
| List-cycler snapshot/restore | fulcro-spec spec on pure cycle function (no DOM). |
| Settings UI rendering when goog.DEBUG=true | e2e (browser-manual acceptable). |
| Settings UI absent when goog.DEBUG=false | Manual: build a release bundle, grep for "debug-css" / "dev-fixtures" — should be 0 hits. |

### Decide when

Pick this up when the next debug surface (PWA diagnostics —
`S-pwa-debug-modal`) gets prioritised, OR the next time list-state
shape needs visual stress-testing. The B-14 fix landed without this
feature, so there's no immediate forcing function — but the
investment is small and pays off every subsequent visual or
state-shape bug.

---

> Pattern: each idea section starts with a `## Title`, a short tag
> for cross-referencing (`Tag:` line), and a `Decide when:` trigger
> so we don't accidentally start building speculative work without
> a real prompt.
