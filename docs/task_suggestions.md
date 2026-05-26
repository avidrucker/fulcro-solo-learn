# Task suggestions / open work proposals

Living catalog of work that *could* be done but isn't currently committed to a phase. Sourced from a 2026-05-25 audit pass covering:

- `docs/user_stories.md` (🆒 stories — Nice-to-have)
- `docs/ideas.md` (speculative / decide-when tagged)
- `docs/phases.md` Queued/next + `docs/phases/23-3-recommendations.md` (deferred-by-rationale)
- `docs/infra-notes.md` (out-of-arc directions + deferred infrastructure)
- `docs/a11y_audit.md` Section B (user-driven manual work)
- `pwa-autofocus-app` source TODOs (the JS port reference)
- Session-derived ideas not yet in any tracked doc

This doc supersedes the previous practice of scattering "things to consider later" across `ideas.md`, `phases.md` Queued/next, and conversation-tail recommendations. Future audits add here; this is the single index of open proposals.

## How to read

- **Highest-leverage** = small effort × real user value × concrete spec. Likely to actually get done.
- **Medium-leverage** = clear value but bigger lift, or smaller value at low cost.
- **Low / discretionary** = legitimate proposal, no compelling reason to start.
- **Out-of-arc** = considered + rejected for this project's scope. Listed so the rejection is visible.

Source links are preserved so every entry traces back to its origin doc.

---

## Highest-leverage

### Undo button for last action
- **Source:** pwa-autofocus-app `src/App.js:15` TODO ("implement an 'undo' button that undo's the last action taken, use fa-history icon"). Not currently in any fulcro-solo-learn doc.
- **Why now:** new product surface (cancel an accidental Cancel, restore an accidental Mark Done, etc.). The JS port flagged it; we never picked it up.
- **Sketch:** ring buffer of `:learn.model.list/operation` events. Undo pops the last and inverts it. Likely 1-2 sub-phases of work.
- **Test surface:** model layer (inversion correctness) + UI (button enabled iff buffer non-empty).

### PWA update-available toast
- **Source:** Session 2026-05-25 — the stale-PWA conversation. Not yet in any tracked doc.
- **Why now:** half of the gap the SW-version-bump discipline addressed. Today's flow is "bump APP_VERSION → CI deploys → next page load triggers SW update on second reload." A toast tells users the second reload is needed. ~2 hr.
- **Sketch:** vanilla-JS `updatefound` / `controllerchange` listener in `resources/public/index.html`. Toast UI is a fixed `<div>` near the bottom.
- **Test surface:** Playwright probe that mocks a version bump and asserts the toast renders.

### Markdown export (S-markdown-export)
- **Source:** `user_stories.md` 🆒 (lines 982-1001).
- **Why now:** well-scoped, pure-CLJC encoder, TDD-friendly. Status-prefix mapping already specced.
- **Sketch:** `learn.util.markdown` encoder. UI button in Save modal next to Export-JSON.

### Reason text for "not prioritizable"
- **Source:** pwa-autofocus-app `core/reviewManager.js:68-71` TODO ("add clear reason why list isn't prioritizable") + fulcro-solo-learn's current `:err/not-prioritizable` is opaque.
- **Why now:** small UX win, real user signal ("I clicked Prioritize and got 'isn't prioritizable right now' — *why?*"). Phase 19's a11y live-region machinery already in place for the announcement.
- **Sketch:** branch the error message on the actual cause: no items, no ready item, no new item after last ready, etc. Adds ~3-4 i18n keys × 4 locales.

---

## Medium-leverage

### PWA debug-info modal (S-pwa-debug-modal)
- **Source:** `user_stories.md` 🆒 (lines 1064-1073) + `ideas.md` tag `dev-mode-toggles` (cross-ref).
- **Why now:** builds on Phase 21 dev-config infra (still warm). Today's B-15 stale-cache discovery would have been instantly diagnosable from this. Sibling to S-dev-mode-toggles.
- **Sketch:** new section in Settings → Debug. Renders current SW state, cache contents, `APP_VERSION`, offline status, general state-shape JSON dump. Wrapped in `^boolean goog.DEBUG`.

### Phase 19 Section-B browser-manual a11y sweep
- **Source:** `docs/a11y_audit.md` Section B (8 sub-items: Lighthouse, axe, WAVE, NVDA/VoiceOver, keyboard-only, zoom, reduced-motion, contrast).
- **Why now:** Phase 19 Section A is ✅. Section B is the qualitative validation that the agent-fixable work actually paid off.
- **Constraint:** **user-driven** — needs a real browser + real screen reader. Not delegatable.

### Truncate next-actionable text to 2 lines + ellipsis
- **Source:** pwa-autofocus-app `src/App.js:16` TODO.
- **Why now:** visual polish; today's text wraps unboundedly which looks awkward on mobile.
- **Sketch:** `text-overflow: ellipsis` + `-webkit-line-clamp: 2` on the `tr-next-actionable` `<p>`. CSS-only fix.

### Keyboard shortcuts during review (Y / N / Q)
- **Source:** pwa-autofocus-app `src/App.js:203, 865-866` TODO + `user_stories.md` 🆒 `S-keyboard-shortcuts` (lines 970-980).
- **Why now:** review modal is the most-keyboard-heavy flow. Y/N/Q is concrete and small; the broader `S-keyboard-shortcuts` is vague (`d` delete, `p` prioritize, etc.).
- **Sketch:** keydown listener on the review modal that maps Y → yes, N → no, Q → quit. Hook into the existing statechart events.

---

## Low / discretionary

### m/returning for `import-from-text` (Phase 23.3 #2)
- **Source:** `docs/phases/23-3-recommendations.md`.
- **Status:** deferred. Minor cleanup eliminating duplicated client/server model logic. Do if working in `import-from-text` for another reason.

### Settings preferences (3 candidates from `ideas.md` modal-auto-close)
- **Source:** `docs/ideas.md` tag `modal-auto-close`.
- **Status:** waiting on a second-preference forcing function. The three candidates:
  - Auto-close modal after action
  - Default startup theme override (currently last-used)
  - Larger-text / zoom

### RAD-vs-non-RAD side-by-side debug view
- **Source:** `docs/ideas.md` tag `rad-debug-side-by-side`.
- **Status:** was gated on debug mode landing (now ✅ Phase 21). Could promote if a second RAD-driven component appears.

### Import confirmation modal (S-import-confirmation)
- **Source:** `user_stories.md` 🆒 (lines 1109-1140).
- **Status:** gated on a real user complaint about silent-append behavior.

### Qualitative UX/copy review (S-ux-a11y-review-pass)
- **Source:** `user_stories.md` 🆒 (lines 1075-1107).
- **Status:** gated on "2+ users besides the author have walked the app end-to-end."

### Namespace split: `learn.model_rad.todo` / `learn.model.todo` (Phase 23.3 #4)
- **Source:** `docs/phases/23-3-recommendations.md`.
- **Status:** deferred. Promote if/when a second entity lands (currently only `:todo`).

### Guardrails consistency audit results
- **Source:** Phase 23.3 #3 — done 2026-05-25 (commit `6e7fb8c`). 12 functions in `learn.util.*` upgraded to `>defn`.
- **Status:** ✅ done. Listed here for reference; not a remaining task.

### `user_stories.md` "Planned (⬜)" section stale header
- **Source:** Session 2026-05-25.
- **Status:** ✅ fixed 2026-05-25 (section renamed to "Originally roadmapped, now implemented").

---

## Deferred infrastructure

### Guardrails `:all` mode + `:covers` proof-system sealing
- **Source:** `docs/infra-notes.md` "Deferred infrastructure items."
- **Status:** Decision crystallized 2026-05-25 in [`guardrails_policy.md`](./guardrails_policy.md). Result: **do not adopt at this scale.** Doc records the rejection rationale.

### Per-test `guardrails-test.edn` with `:throw? true`
- **Source:** `infra-notes.md`.
- **Status:** Decision crystallized 2026-05-25 in [`guardrails_policy.md`](./guardrails_policy.md). Result: **adopted** — config files added + `:test` alias updated.

### Pre-warm `dev/user.clj` for fast first-run REPL
- **Source:** `infra-notes.md`.
- **Status:** Defer further. Restart frequency hasn't crossed the pain threshold; CLAUDE.md's `clj-nrepl-eval` pattern keeps inner-loop iteration sub-second.

---

## Out-of-arc / gated

- **DataScript swap** — replace atom-as-server with DataScript for datalog queries + history. Learning detour, no product motivation. (`infra-notes.md`)
- **Real backend (Datomic / Postgres + HTTP + tempids)** — would require async coordination, tempid rewrites, server lifecycle. Excluded by the front-end-only decision. (`infra-notes.md`)
- **Phase 20c — Lighthouse shell scripts + expanded Playwright** — deferred until a concrete gap warrants it. (`docs/phases.md` Queued/next)

---

## When to add to this doc

Add an entry when a proposal:
- has a clear-enough scope to write 2-3 sentences about it, but
- doesn't have a forcing function urgent enough to promote to a phase / story, and
- the current state of the project doesn't yet warrant the work.

When a proposal *is* promoted to active work (phase / sub-phase / story ⬜ → 🟡 → ✅), keep the entry here with a status note and a link to where the work landed. The doc is also a record of what was *considered*, not just what's still pending.

When an audit (like Phase 23) surfaces new ideas, add them here under the appropriate priority bucket. The bucket assignment is a judgment call; it's fine to be wrong and re-bucket later.
