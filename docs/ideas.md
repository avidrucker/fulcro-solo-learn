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

> Pattern: each idea section starts with a `## Title`, a short tag
> for cross-referencing (`Tag:` line), and a `Decide when:` trigger
> so we don't accidentally start building speculative work without
> a real prompt.
