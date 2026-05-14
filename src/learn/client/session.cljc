(ns learn.client.session
  "Phase 12.7 — tiny ns for cross-namespace constants that would
   otherwise create circular requires.

   `review-session-id` is referenced by both the UI components
   namespace (TodoList queries chart state via this id) and the
   lifecycle namespace (init starts the chart under this id). Both
   would have to require each other if these lived in either; an
   independent leaf namespace breaks the cycle.")

;; Well-known singleton session id for the review chart. The chart
;; runs at most one session at a time per app (SCHEMA.md §13 'One
;; per app instance'), so a keyword id is sufficient; no need to
;; mint random UUIDs.
(def review-session-id :review-session)

;; Registry key for the review chart definition on the Fulcro app.
;; Resolves to `:learn.client.session/review-chart`. Existing
;; Phase 5K code referenced `:learn.client/review-chart`; the alias
;; is preserved in `learn.client` as a back-compat re-export.
(def review-chart-key ::review-chart)
