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

### Option B — User preference via a settings modal

Add a fifth menu modal — `:settings` — toggled by a gear icon in
the header. The first preference in it is "Close modals after
action" (boolean, default false). Add more prefs as they come up
(zoom, default theme override, etc.).

This is the better answer if/when we have ≥3 user-tweakable
preferences. Today's only candidate is auto-close, so the modal
would be sparsely populated.

### Decide when

When a third preference candidate appears (current candidates:
auto-close, larger-text/zoom, default-startup-theme) — then build
Option B and seed it with whatever's accumulated.

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

> Pattern: each idea section starts with a `## Title`, a short tag
> for cross-referencing (`Tag:` line), and a `Decide when:` trigger
> so we don't accidentally start building speculative work without
> a real prompt.
