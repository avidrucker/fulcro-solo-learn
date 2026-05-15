(ns learn.rad.input
  "Attribute-driven input rendering. Phase 9.2 (RAD basics).

   For a single inline input, RAD's full `defsc-form` is wrong scope —
   it expects an entity with an identity attribute, a render plugin
   registered against the app, and (typically) routing. None of that
   pays off for one text input.

   What DOES pay off, even at our scale: **the attribute is the
   source of truth for input metadata**. Instead of hard-coding the
   placeholder + maxlength on the `<input>` element directly, we
   read them off the attribute map at render time. Change the
   attribute, every input using it updates.

   This namespace provides a thin `text-input` helper that takes a
   RAD attribute + our usual Fulcro plumbing (the `this` component,
   the controlled-value Fulcro state key, current value, change
   handler) and emits a Tachyons-styled `<input>`. The renderer is
   intentionally NOT pluggable — that's the line where a one-input
   project crosses into 'should be defsc-form territory.'

   See `docs/benefits-of-RAD-in-this-project.md` for the full
   tradeoff write-up."
  (:require
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.fulcro.components :as comp]
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])
    [com.fulcrologic.fulcro.mutations :as m]))

(defn text-input
  "Render a text `<input>` driven by `attribute`'s metadata.

   Reads:
     `:field/label`      — fallback `:placeholder` when the call site
                           doesn't pass `:placeholder` explicitly. The
                           visible `<label>` is `clip`-hidden for
                           headless tests, see `client.cljc` comment.
     `:field/maxlength`  — `:maxLength` on the `<input>`.

   Required call-site props:
     `:this`        — the Fulcro component for `m/set-string!`.
     `:state-key`   — the Fulcro state key to bind the value to (e.g.
                      `:ui/new-todo-text`).
     `:value`       — current value (denormalized prop).
     `:class-name`  — Tachyons class string for theming.
     `:input-id`    — DOM id for the `<input>` (used by the hidden
                      `<label htmlFor>` for headless test access).

   Optional:
     `:label-text`  — text inside the `clip`-hidden `<label>` so
                      `h/type-into-labeled!` can find it AND screen
                      readers announce a name for the input. Defaults
                      to the attribute's `:field/label`. Phase 19l —
                      pass a localized string here.
     `:placeholder` — placeholder text on the `<input>`. Defaults to
                      the attribute's `:field/label`. Phase 19l —
                      pass a localized string here.

   Returns the `<label>` + `<input>` pair as a Fulcro `fragment` so
   call sites can drop it inline into their form markup."
  [attribute
   {:keys [this state-key value class-name input-id label-text placeholder]
    :or   {label-text  (:field/label attribute)
           placeholder (:field/label attribute)}}]
  (comp/fragment
    (dom/label {:htmlFor input-id :className "clip"} label-text)
    (dom/input {:id          input-id
                :className   class-name
                :placeholder placeholder
                :maxLength   (:field/maxlength attribute)
                :value       (or value "")
                :onChange    #(m/set-string! this state-key :event %)})))
