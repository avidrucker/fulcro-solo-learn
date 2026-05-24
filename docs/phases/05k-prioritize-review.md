# Phase 5K — Prioritize/review flow

**Status:** ✅ Complete

Build `learn.model.review` plus a Fulcrologic statechart that orchestrates the binary review process. JS-port `handle-review-decision` is replaced by the chart itself (transitions express Yes/No/Quit decisions).

**Decisions locked in:**
- JS discrepancy #1 (prioritizable list): list-position rule — last `:new` must come after last `:ready` in list order. Diverges from the JS `lastNew.id > lastReady.id` (which assumed monotonic int ids; UUIDs in the port can't use ordering).
- JS discrepancy #4 (review-decision return shape): when a `handle-review-decision`-equivalent is needed, return Result-shaped — but in practice this work is absorbed by the statechart's transitions.
- Statecharts introduced in 5K.4 (skill imported from Desktop). Pure functions in 5K.1–5K.3 stay testable in isolation.

## Sub-phases

- ✅ [5K.1 — `model.review/prioritizable?`](05k-1-prioritizable.md)
- ✅ [5K.2 — `model.review/initial-cursor` + `next-cursor`](05k-2-initial-cursor.md)
- ✅ [5K.3 — `model.review/current-question`](05k-3-current-question.md)
- ✅ [5K.4 — Review statechart (`learn.review.chart`)](05k-4-statechart.md)
- ✅ [5K.5 — Client wiring (chart session lifecycle + UI affordances)](05k-5-client-wiring.md)
- ✅ [5K.6 — Server sync of review decisions](05k-6-server-sync.md)
