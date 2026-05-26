# UX research findings — AutoFocus Fulcro port

**Researcher:** Claude (Opus 4.7, 1M ctx)
**Date:** 2026-05-25
**Method:** Heuristic evaluation + cognitive walkthrough of `learn.i18n.core`'s English copy, `learn.client.ui.modals` markup, and the documented user flows in `docs/user_stories.md` / `docs/SCHEMA.md`. No live user sessions — this is a *researcher-as-proxy* pass.

## Target user (assumed)

- Likely encountered AutoFocus through Mark Forster's blog or community discussion.
- Has some tech literacy (web app, URL sharing, PWA install).
- May be new to AutoFocus *algorithm* specifically (knows productivity systems generally, but the benchmark/prioritize loop is new).
- Sometimes shares lists via URL with collaborators (Phase 14/17/18 i18n round-trip confirms this).
- Uses the app on both desktop and mobile (the recent Android-DuckDuckGo stale-PWA discovery confirms mobile is a real surface).

## Severity scale

| Mark | Meaning |
|---|---|
| 🔴 Critical | First-time users get confused or stuck; existing users lose work or trust |
| 🟠 High | Real friction every session; clarity loss for any user |
| 🟡 Medium | Polish; affects perception of quality more than function |
| 🔵 Low | Nice-to-have; would never block anyone |

---

## 🔴 Critical

### F-1 — "The list isn't prioritizable right now" doesn't say why

**Observation:** `:err/not-prioritizable` (`i18n/core.cljc:132`) is shown when the user clicks Prioritize but the list state can't enter the review flow. The error doesn't explain the precondition. AutoFocus has a *specific* prioritizability rule (per SCHEMA.md §15: at least one `:ready` item AND at least one `:new` item after the last `:ready`). New users won't infer this from the bare error.

**Why it matters:** A user with a list of three new items clicks Prioritize, sees "isn't prioritizable right now," and has no path forward. They can't tell whether they need to add an item, mark one ready, wait for something, or whether the app is broken.

**Suggested fix:** Branch the error message on the actual reason. Three variants (with i18n keys):
- Empty list: "Add some tasks first."
- All cancelled / done: "All tasks are done or cancelled. Add fresh tasks to prioritize."
- No new tasks after the ready: "All tasks are already prioritized — use Mark Done to complete the next one."

**Effort:** S. ~4 new i18n keys × 4 locales + a branching function in `learn.model.list` (`why-not-prioritizable`). Already flagged as a real candidate in `task_suggestions.md`; cross-referenced from a pwa-autofocus-app TODO (`core/reviewManager.js:68-71`).

### F-2 — No first-time-user empty state / onboarding

**Observation:** A new user lands on an empty app and sees only the new-todo input + four primary action buttons (`Add Item`, `Delete List`, `Prioritize`, `Mark Done`) — three of which are dimmed because the list is empty. No inline guidance explains what AutoFocus does or how to start.

**Why it matters:** The Info modal (`Click on the 'i' icon` to see About + Help) has the explanation, but it's behind a click. A user who didn't arrive via the README has zero context. Three dimmed buttons + an empty list feels broken, not "ready to start."

**Suggested fix:** Below the input, when the list is empty, render a single line: *"Add your first task above. AutoFocus helps you choose which to do next via a guided question."* — with a "Learn more" link that opens the Info modal. Disappear once the list has ≥1 item.

**Effort:** S. ~1 new i18n key × 4 locales + a conditional render in TodoList. CSS hidden via existing theme classes.

### F-3 — "Cancel" is overloaded terminology

**Observation:** AutoFocus uses "cancel" in a domain-specific way: cancelling a task marks it `:status/cancelled` and captures `:was` — it doesn't remove the task. The button label `Cancel Task` (`:tooltip/cancel-task`, `i18n/core.cljc:159`) reuses a word that, in app UX, conventionally means "abort this dialog / undo this action." New users may click Cancel expecting "never mind" and instead permanently flag the task.

**Why it matters:** The conflict modals (`list-conflict`, `locale-conflict`) DO use Cancel-like semantics through "Keep Link" / "Keep Local" labels — those got intentional re-labeling (B-10 fix). The per-row Cancel button never got the same treatment.

**Suggested fix:** Either rename the action label (e.g. "Strike out" / "Cross off" — both used in to-do conventions) OR keep the label but add a one-time hover-state explainer: "Mark as cancelled (stays visible, strikes through)." Lower-effort option: just the second.

**Effort:** S. Either a label rename across 4 locales OR an expanded tooltip in 4 locales.

---

## 🟠 High

### F-4 — Review modal question phrasing is intellectually correct but feels stilted

**Observation:** The review prompt (`tr-review-question`, `i18n/core.cljc:572`) reads:
> *"In this moment, are you more ready to 'wash dishes' than 'reply to Anna'?"*

The phrasing is faithful to Mark Forster's AutoFocus language. But "more ready to X than Y" is unusual English — both grammatically (modal stacking) and conceptually ("readiness" as a comparative attribute).

**Why it matters:** The review flow is the *core differentiator* of AutoFocus vs other to-do apps. If the question feels awkward, users push through fewer items per session. The cognitive load of parsing the prompt competes with the cognitive load of answering it.

**Suggested fix:** Test a more natural phrasing. Options:
- *"Right now, would you rather do 'wash dishes' than 'reply to Anna'?"*
- *"Which feels more pressing right now: 'wash dishes' or 'reply to Anna'?"* (with Yes = first, No = second)
- *"Pick one to do next: 'wash dishes' or 'reply to Anna'."* (most direct)

The third departs furthest from Forster's exact words but matches what the user actually does. Worth A/B testing if/when a real user round-trip happens.

**Effort:** XS to change copy. M if doing user research to validate.

### F-5 — Two near-identical errors with inconsistent wording

**Observation:** Two error strings describe the same thing in different words:
- `:err/empty-input` (`i18n/core.cljc:129`): *"New items cannot be empty or only whitespace."*
- `:err/empty-textarea` (`i18n/core.cljc:133`): *"New items cannot be empty or whitespace only."*

Same situation (blank input), different word order, used in different surfaces (single-line input vs textarea).

**Why it matters:** Two strings means two translations to maintain × 4 locales = 8 copies of essentially the same idea. Users seeing both (likely) won't notice the difference; reviewers/translators absolutely will.

**Suggested fix:** Consolidate to one i18n key. Default to the simpler: *"Cannot be empty."* — drops the "or only whitespace" qualifier since trimming-internal behavior is invisible to the user anyway.

**Effort:** XS. Drop one key, update one call site.

### F-6 — "Mark Done" doesn't say which task gets marked done

**Observation:** `:btn/mark-done` is "Mark Done" (`i18n/core.cljc:62`). When pressed, it marks the *benchmark* (the last `:ready` item) as done — not the first, not the focused, not the user's choice. A new user can't infer this.

**Why it matters:** AutoFocus's whole point is that the LAST ready item is "the one you're most ready to do." But the button label doesn't reflect this — it says "Mark Done" as if there's an obvious "this one" the app would pick. Users may click it expecting to choose a task, then be surprised when a specific one disappears.

**Suggested fix:** Either:
- Rename the button: *"Done with Benchmark"* or *"Done with current task"* (with "current" cross-referencing the visual benchmark indicator).
- OR: visually highlight the benchmark task in the list (different background / "←" arrow / "next" label) so the user can see which task the button targets.

Best is probably **both** — clearer label + visual reinforcement.

**Effort:** S for label change; M for visual indicator.

### F-7 — Modal close instructions are partial and inconsistent

**Observation:** Three modals carry close-instruction footers, each pointing at the modal-icon:
- `:info/click-i-circle`: *"Click on the 'i' icon above to close this window."*
- `:settings/click-gear`: *"Click on the 'gear' icon above to close this window."*
- `:save/click-disk`: *"Click on the 'disk' icon above to close this window."*

But each modal *also* supports:
- **Escape** to close (Phase 19h, `S-a11y-escape-to-close`)
- **Background click** to dismiss (`S-modal-bg-close`)
- Explicit close button at the bottom

Documenting only one path is misleading — users tab-focusing the modal don't know Escape works; touch users don't know they can tap outside.

**Why it matters:** Discoverability of accessibility features. Existing UX work made these paths reachable; the copy is one revision behind.

**Suggested fix:** Replace all three with a single generic phrasing: *"Press Esc, click outside, or close button below to dismiss."* Same key (e.g. `:modal/dismiss-hint`), same wording across modals — drops three keys × 4 locales = 12 strings, saves maintenance. Skip on the non-dismissible conflict modals.

**Effort:** S. Localization sweep + one render template change.

### F-8 — Terminology drift: "task" vs "item" vs "TODO"

**Observation:** The visible copy uses three terms for the same domain concept:
- `:input/new-todo-placeholder`: "Type new task here"
- `:input/new-todo-label`: "New TODO:"
- `:btn/add-item`: "Add Item"
- `:err/empty-input`: "New **items** cannot be empty…"
- `:tooltip/cancel-task`: "Cancel **Task**"
- `:tooltip/add-item`: "add a new **item** to your list"
- `:err/cannot-take-action`: "no actionable **tasks** in your list"

**Why it matters:** Inconsistency makes the app feel less polished. Worse: each translator has to make the same choice independently across locales, multiplying maintenance.

**Suggested fix:** Pick one term project-wide. **Recommendation: "task"** — it's the most natural and user-friendly. "Item" sounds technical; "TODO" is dev shorthand. Rename across i18n keys + all 4 locales.

**Effort:** S. Find/replace + locale-team review.

---

## 🟡 Medium

### F-9 — No undo

**Observation:** Most actions (Cancel, Clone, Mark Done, Delete List) are permanent. Delete List has a confirm modal (B-2 fix). The rest don't. A misclick on Cancel against a real `:ready` task can't be undone — clone gives a *new* row with a new ID, not a restoration.

**Why it matters:** "I'll add a Cancel button" feels safe to design, but in this domain "cancel" is itself the action — there's no second-level undo. Users with one wrong click feel out of control.

**Suggested fix:** Add an undo affordance for the last destructive action (Cancel / Mark Done / Delete List). The pwa-autofocus-app's `App.js:15` TODO already flagged this — it was never built. Ring buffer of `:learn.model.list/operation` events; undo pops + inverts. Already listed as a high-leverage item in `task_suggestions.md`.

**Effort:** M.

### F-10 — Info modal combines About + Help under one heading

**Observation:** Phase 12.3 combined the old About and Help modals into a single Info modal. Result: one modal with TWO section headings (`:info/heading-about` "About AutoFocus" + `:info/heading-help` "Instructions & Help") plus a version line and a GitHub-issues link.

**Why it matters:** The single modal is denser than either standalone. A user who just wants the help text scrolls past About; a user who just wants the version sees two paragraphs of background. Cognitive load mismatch.

**Suggested fix:** Either:
- Keep the merged modal but add a small visual divider (Tachyons `bb b--gray pa3 mb3`) between sections so they read as distinct.
- OR: split back into two modal-tabs within the same icon (tab strip at top: "About" / "Help" / "Credits"). Bigger refactor; probably overkill at the current content volume.

**Effort:** XS for the divider; M for the tab structure.

### F-11 — "Clone" is dev-speak

**Observation:** `:tooltip/cancel-task` says "Cancel Task" but its sibling `:tooltip/clone-task` says "Clone Task" (`i18n/core.cljc:160`). Most users hear "clone" as a software-engineering term, not a productivity term.

**Why it matters:** A polished app uses *productivity* vocabulary, not git vocabulary. "Clone" feels alien in a context about doing dishes and replying to email.

**Suggested fix:** Rename to "Duplicate" or "Copy" — both convey the action without invoking version-control connotations. "Duplicate" is slightly more accurate (it makes a new instance of the task), "Copy" is shorter.

**Effort:** XS. Rename one key across 4 locales + update tooltip text.

### F-12 — Conflict modal uses technical term "local storage"

**Observation:** `:conflict/mismatch` (`i18n/core.cljc:146`): *"The link list and local storage list do not match. Which will you keep?"* — exposes the implementation term "local storage" to the user.

**Why it matters:** "Local storage" is a browser-developer concept. End users have no model for what that means. A non-tech user sees this and either (a) panics because something technical is broken, or (b) ignores both options.

**Suggested fix:** Rephrase: *"The link's list and your saved list are different. Which do you want to keep?"* — "saved list" is user-friendly; "link's list" tracks the URL share semantics.

**Effort:** XS. One i18n key × 4 locales.

### F-13 — "Prioritize" verb could be more action-explicit

**Observation:** `:btn/prioritize` is "Prioritize" (`i18n/core.cljc:61`). The verb is correct but not *concrete* — it doesn't suggest an interaction will follow (a modal, a binary question, multiple decisions).

**Why it matters:** Button labels that hint at outcome ("Start review", "Begin prioritizing") feel more clickable than verb-only labels. Marginal effect on first-time clarity.

**Suggested fix:** Rename to "Start prioritizing" or "Begin review." Locked Japanese is currently "優先する" (lit. "prioritize") — could become "優先順位付けを開始" ("begin prioritization"). Lower-priority polish; flagged in the existing `S-ux-a11y-review-pass` 🆒 story.

**Effort:** XS.

---

## 🔵 Low

### F-14 — "Disk" terminology in modal-close instruction

**Observation:** `:save/click-disk` says "Click on the 'disk' icon above to close this window." Younger users (Gen Z, mobile-first) may not recognize the floppy-disk metaphor as the "save" icon. Even older users say "save icon," not "disk icon."

**Why it matters:** Slight friction. Mostly invisible if F-7 is fixed (which removes these instructions entirely).

**Suggested fix:** Subsumed by F-7's recommendation.

### F-15 — Long "next actionable" text wraps unboundedly

**Observation:** `:foot/next-actionable` (parameterized via `tr-next-actionable`) renders the benchmark task's text under the action buttons. Long task text wraps to multiple lines, pushing layout around.

**Why it matters:** Visual chunk-shift between page renders is mildly disorienting. Also referenced in `pwa-autofocus-app/App.js:16` TODO.

**Suggested fix:** Truncate to 2 lines + ellipsis via `text-overflow: ellipsis` + `-webkit-line-clamp: 2`. CSS-only fix. Already in `task_suggestions.md` as a medium-leverage item.

**Effort:** XS.

### F-16 — No keyboard hints in review modal

**Observation:** Review modal has Yes/No/Quit buttons but no visible "Y / N / Q" hints. Users have to mouse-click each — slower than necessary for the keyboard-friendly user.

**Why it matters:** The whole point of the review flow is throughput. Y/N/Q shortcuts are referenced in pwa-autofocus-app `App.js:203` TODO and `S-keyboard-shortcuts` story.

**Suggested fix:** Add a small "(Y)" / "(N)" / "(Q)" suffix to each button label, and wire keydown listeners on the modal that dispatch the same events. Already a candidate in `task_suggestions.md`.

**Effort:** S.

---

## Cross-cutting / observations

### O-1 — Microcopy is more "correct" than "warm"

The visible English copy is well-engineered (precise, internally consistent within tooltips, accessible). But it feels written-by-a-developer, not written-by-a-product-designer:

- *"add a new item to your list"* — accurate; *"Add a task you're thinking about"* would be warmer.
- *"start a list prioritizing session"* — accurate; *"Decide what to do next"* would be more direct.
- *"mark the next actionable item as complete"* — accurate; *"Done with the next task!"* would be more celebratory.

**Recommendation:** When/if a copy-edit pass happens (e.g. via `S-ux-a11y-review-pass`), prefer warm + direct over technically-precise. The accessibility names (`aria-label`) can stay precise; the visible labels can warm up.

### O-2 — No celebration / completion feedback

When the user marks a task done, the task transitions to `:status/done` and gets a visual style — but no celebratory feedback (animation, sound, "Nice!"). Most productivity apps include some flavor of this.

**Recommendation:** Defer. Animation / sound would conflict with the `prefers-reduced-motion` work (Phase 19p). A subtle "✓" briefly appearing might be tasteful; would need user research to size up.

### O-3 — Mobile is a real surface, not a side-effect

The recent stale-PWA discovery on Android DuckDuckGo confirmed mobile is in use. The app *renders* on mobile (Tachyons responsive utilities cover most cases) but no explicit mobile-UX work has been done. Touch target sizes, swipe gestures, viewport behavior — all uninvestigated.

**Recommendation:** A small "mobile sweep" task — open the deployed app on a real phone, run through the golden path with a thumb, log friction. Could fit as a sub-phase of `S-ux-a11y-review-pass`.

### O-4 — Discoverability of features is low

Several features exist but aren't obvious from the UI:
- Keyboard navigation works fully (Phase 19) — no surface signal
- Background-click dismisses modals — no surface signal
- Escape closes modals — no surface signal
- The PWA is installable — no install prompt visible
- URL-share encoding round-trips — only discoverable via the Save modal

**Recommendation:** A small "tips" affordance — a `?` icon next to the input that opens a 5-bullet primer ("Press Enter to add", "Tab to skip header", "Escape closes modals", "Click outside to dismiss", "Use ?lang= in URLs to share in a specific language"). Or fold into the Info modal's Help section if F-10 is also addressed.

---

## Recommended priority order

If you can only do 5 things:

1. **F-1** — Branch "isn't prioritizable" error on actual reason. Critical UX gap with concrete fix.
2. **F-2** — Empty-state onboarding hint. New-user friction at the highest-impact moment.
3. **F-3** — Rename or re-explain "Cancel Task". Domain-conventional terminology trap.
4. **F-8** — Pick one term for the domain concept (task vs item vs TODO). Internal consistency win across 4 locales.
5. **F-7** — Single modal-dismiss hint replacing the three locale-icon-specific instructions. Closes the discoverability gap from Phase 19h's Escape support.

Together, those five are ~1–2 days of work (mostly i18n key edits + one branching function), and they collectively shift the app from "engineering-correct" to "user-friendly."

The next five (F-4, F-9, F-10, F-12, F-16) take the app from "user-friendly" to "polished." They're worth doing but not load-bearing.

The remaining findings are nice-to-haves.

---

## Methodology notes (transparency)

- This was a **researcher-as-proxy** pass, not real user research. Findings are hypotheses based on heuristics + cognitive walkthrough, NOT validated by real-user sessions.
- The strongest findings (F-1, F-2, F-3) are based on universal usability principles + the AutoFocus domain semantics. The medium-priority findings (F-4 specifically) would benefit most from real A/B testing.
- A real follow-up would be a 30-min recorded session with 3 first-time users + 1 returning user. The `S-ux-a11y-review-pass` story (🆒 in `user_stories.md`) is the right vehicle when 2+ external users walk the app.
- This doc lives in `docs/` because future copy decisions should reference it. Promote individual findings to `task_suggestions.md` (or directly to phases) as they become actionable.
