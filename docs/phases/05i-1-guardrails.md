# Phase 5I.1 — Add Guardrails 1.2.16 + refactor schema to `>def` registry

**Status:** ✅ Complete
**Parent:** [Phase 5I — AutoFocus domain operations](05i-autofocus-domain.md)

Upgraded Guardrails to the version mandated by the fulcro-spec-tdd skill. Refactored `schema.cljc` to use `>def` with namespaced keywords so schemas register in the Guardrails-extended Malli registry and can be referenced by keyword from `>defn` specs elsewhere.

Enablement: added `-Dguardrails.enabled=true` to the `:test` alias `:jvm-opts`. Bridge from Guardrails registry to Malli's default via `(mr/set-default-registry! (mr/composite-registry (m/default-schemas) (mr/mutable-registry gr.reg/schema-atom)))` so `m/validate` resolves `>def`'d schemas.

**Files:** `deps.edn`, `src/learn/model/schema.cljc`
**Acceptance met:** Existing 16 specs pass; `(s/valid? ::s/todo s/example-todo)` returns `true` from REPL.
