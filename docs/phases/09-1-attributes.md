# Phase 9.1 — Attribute definitions

**Status:** ✅ Complete
**Parent:** [Phase 9 — Fulcro RAD basics](09-rad-basics.md)

Added `com.fulcrologic/fulcro-rad 1.6.23` to deps.edn. New `learn.rad.attributes` ns with `defattr` declarations for `:todo/id`, `:todo/text`, `:todo/status`, `:todo/was`. Each carries data type, cardinality, required-flag, schema; text adds `:field/label` (sourced from `learn.ui.strings/input-placeholder`) and `:field/maxlength`; status + was enumerate the four valid values. 1 new spec / 15 new assertions verifying the registry shape.
