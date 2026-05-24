# Phase 7.6 — Import/Export modal (stubbed)

**Status:** ✅ Complete
**Parent:** [Phase 7 — localStorage persistence + UI feature parity](07-persistence-and-features.md)

`save-disk` SVG added; third header icon button rendered before the About/Help pair. `save-modal` renders the full JS-port markup: Copy List URL button, Import (styled `<label>` wrapping a `type="file" accept=".json"` hidden input), Export button, textarea + Submit. All four interactive elements use the new `stub-onclick` helper — `(js/console.log "[stub]" label)` in CLJS, no-op on JVM. Real behaviour lands in a later phase.

2 new specs cover: open via header icon → expected markup visible; bg-close dismisses.

**47 specs / 378 assertions, all green. CLJS: 326 files, 0 warnings.**

Implements **S-import-export** (stubbed status, markup verified).
