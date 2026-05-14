(ns learn.rad.attributes
  "Fulcro-RAD attribute definitions for the Todo entity. Phase 9.1
   (RAD basics) introduces RAD's attribute-driven schema model
   alongside our existing Malli schemas in `learn.model.schema`.

   The two coexist intentionally:
     - `learn.model.schema` (Malli + Guardrails registry) covers the
       *function-contract* layer — `>defn` signatures, validation of
       items vectors in pure domain functions, schema validation in
       tests. Function-level invariants live here.
     - `learn.rad.attributes` (this ns) covers the *RAD-knows-about-it*
       layer — attribute metadata RAD reads to derive form rendering,
       validation, and (eventually) report columns + server schema.
       UI-driving metadata lives here.

   For a learning project with one entity, this dual representation is
   redundant — `docs/benefits-of-RAD-in-this-project.md` is the honest
   write-up of when it would pay off and why it doesn't here. We keep
   both so the comparison is concrete in the code itself.

   defattr macro: `(defattr name :namespace/keyword :data-type
   options-map)`. The symbol holds the attribute map; the keyword is
   the wire identity; the data-type drives RAD's default form rendering
   + validation. Options live under the `::attr/*` namespace from
   `com.fulcrologic.rad.attributes`."
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [learn.ui.strings :as s]))

(defattr id :todo/id :uuid
  {ao/identity?   true
   ao/schema      :production
   ao/cardinality :one
   ao/required?   true})

(defattr text :todo/text :string
  {ao/identities  #{:todo/id}
   ao/schema      :production
   ao/cardinality :one
   ao/required?   true
   ao/style       :input
   ;; UI-only metadata RAD reads when rendering form fields.
   ;; `:field/label` doubles as the placeholder in our minimal
   ;; renderer (the visible `<label>` is `clip`-hidden). Source of
   ;; truth lives in `learn.ui.strings` so a content-string change
   ;; ripples through both the live UI and any test affordances.
   :field/label   s/input-placeholder
   :field/maxlength 1024})

(defattr status :todo/status :keyword
  {ao/identities    #{:todo/id}
   ao/schema        :production
   ao/cardinality   :one
   ao/required?     true
   ao/enumerated-values   #{:status/new :status/ready :status/done :status/cancelled}
   ao/enumerated-labels   {:status/new       "New"
                           :status/ready     "Ready"
                           :status/done      "Done"
                           :status/cancelled "Cancelled"}})

(defattr was :todo/was :keyword
  {ao/identities    #{:todo/id}
   ao/schema        :production
   ao/cardinality   :one
   ;; Optional: only present when status is :status/cancelled.
   ao/required?     false
   ao/enumerated-values   #{:status/new :status/ready :status/done :status/cancelled}})

(def all-attributes
  "The full set of attributes for the Todo entity, in a registration-
   friendly order (identity first). Passed to RAD's app builder so the
   registry knows about every attribute the UI references."
  [id text status was])
