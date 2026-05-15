(ns learn.client.state
  "Phase 12.7 — pure state-helpers extracted from `learn.client`.

   Every fn here takes a Fulcro state-map plus the path / args it
   needs and returns a new state-map. No side effects, no Fulcro
   components referenced, no Pathom — just data in / data out.

   Naming: `*`-suffix marks 'pure state-map → state-map' so callers
   (mutations) can `swap!` straight through. Most delegate to
   `learn.model.list` for domain logic and use `norm` projections to
   move between normalized state and the denormalized items vector
   the model operates on.

   Phase 12.7 changed `add-todo*` from `merge/merge-component
   TodoItem` to `norm/sync-items` — the latter doesn't need a
   reference to the TodoItem component, which kept this namespace a
   pure leaf (no UI-component dependency). Behaviour is identical
   for our flat schema; revisit if TodoItem ever grows nested
   queries."
  (:require
    [com.fulcrologic.fulcro.algorithms.normalized-state :as nsh]
    [learn.model.list :as model.list]
    [learn.util.normalized :as norm]))

;; ============================================================================
;; List mutations
;; ============================================================================

(defn add-todo*
  "Append a fresh todo to the given list and clear :ui/new-todo-text.

   Status determination delegates to learn.model.list/add-todo, which
   applies the AutoFocus add rule (SCHEMA.md §7):
     - If the list has zero :status/ready items → new todo gets :status/ready.
     - Otherwise (at least one ready exists)    → new todo gets :status/new.

   Blank text returns state unchanged. The model returns
   {:ok? false :error/type :error/blank-item}; we no-op here. The UI
   layer surfaces the error via :ui/err-msg before reaching this."
  [state-map list-ident text]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/add-todo items text)]
    (if (:ok? result)
      (-> state-map
        (norm/sync-items list-ident (:items result))
        (assoc-in (conj list-ident :ui/new-todo-text) ""))
      state-map)))

(defn import-from-text*
  "Phase 7.12 batch import: denormalize → model.list/import-from-string →
   sync-items back. Blank-or-whitespace text is a no-op (model refuses
   with :error/empty-import; UI layer surfaces it before reaching here)."
  [state-map list-ident text]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/import-from-string items text)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

(defn import-from-json*
  "Phase 13 — JSON-file batch import: append already-parsed items
   to the list. Unlike `import-from-text*`, no domain-layer rule
   application happens here: the imported items keep their UUIDs and
   statuses verbatim (the OG behaviour). Validation and shape
   translation are the parse layer's job (`learn.util.tasks-io/parse-tasks-json`).

   Empty / nil `new-items` is a no-op so the UI layer can blanket-pass
   a parsed-empty result without a precondition check."
  [state-map list-ident new-items]
  (if (seq new-items)
    (let [existing (norm/denormalize-list-items state-map list-ident)]
      (norm/sync-items state-map list-ident (vec (concat existing new-items))))
    state-map))

(defn delete-all*
  "Removes every todo referenced by the given list-ident's :list/todos.
   Used by the 'Delete List' operation in the AutoFocus model."
  [state-map list-ident]
  (let [todo-idents (get-in state-map (conj list-ident :list/todos))]
    (reduce nsh/remove-entity state-map todo-idents)))

(defn cancel-todo*
  "State-helper for the cancel-todo mutation. Refusal is a no-op."
  [state-map list-ident todo-id]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/cancel-todo items todo-id)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

(defn complete-benchmark-item*
  "State-helper for the complete-benchmark-item mutation. Refusal is a no-op."
  [state-map list-ident]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/complete-benchmark-item items)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

(defn clone-todo*
  "State-helper for the clone-todo mutation. Refusal is a no-op."
  [state-map list-ident todo-id]
  (let [items  (norm/denormalize-list-items state-map list-ident)
        result (model.list/clone-todo items todo-id)]
    (if (:ok? result)
      (norm/sync-items state-map list-ident (:items result))
      state-map)))

;; TODO: add status change enforcement mechanics - perhaps this
;; could/should be a state chart?
(defn set-status*
  "Sets :todo/status on a single todo. Centralizes the path so any future
   schema change happens in one place. If todo is set to cancelled, then
   :todo/was will also be set to the previous status for rendering purposes."
  [state-map todo-id status]
  (let [path [:todo/id todo-id :todo/status]
        prev-status (get-in state-map path)]
    (cond
      ;; when transitioning into cancelled, we store the previous status
      ;; and then update
      (and (= status :status/cancelled)
        (not= prev-status :status/cancelled))
      (-> state-map
        (assoc-in [:todo/id todo-id :todo/was] prev-status)
        (assoc-in path :status/cancelled))
      ;; any other status: just set it
      :else
      (assoc-in state-map path status))))

;; ============================================================================
;; Conflict modal (Phase 7.18)
;; ============================================================================

(defn- close-conflict-modal*
  "Clear the transient conflict-modal state at `list-ident` and close
   the modal."
  [state-map list-ident]
  (-> state-map
    (assoc-in (conj list-ident :ui/conflict-url-items) nil)
    (assoc-in (conj list-ident :ui/open-modal) :none)))

(defn keep-link-list*
  "Phase 7.18 — user chose to keep the URL-list when the conflict
   modal showed two divergent lists. Replaces the normalized list at
   `list-ident` with `:ui/conflict-url-items`, then closes the modal
   and clears the transient stash. No-op when `:ui/conflict-url-items`
   is absent (defensive — the mutation can only fire while the modal
   is open, but we guard anyway)."
  [state-map list-ident]
  (if-let [url-items (get-in state-map (conj list-ident :ui/conflict-url-items))]
    (-> state-map
      (norm/sync-items list-ident url-items)
      (close-conflict-modal* list-ident))
    state-map))

(defn keep-local-list*
  "Phase 7.18 — user chose to keep the localStorage-derived list. The
   normalized list at `list-ident` already holds those items (we
   hydrate from localStorage before showing the modal), so this is
   just: close the modal + clear the transient stash."
  [state-map list-ident]
  (close-conflict-modal* state-map list-ident))

;; ============================================================================
;; Modal mutex (Phase 7.4)
;;
;; `[:list/id 1 :ui/open-modal]` carries one of:
;;   :none           — no modal open (default)
;;   :info           — Info modal (Phase 12.3: combines About + Help)
;;   :settings       — Settings modal (Phase 12.3: new)
;;   :save           — Import/Export modal
;;   :delete-confirm — Phase 7.12: Are-you-sure prompt for Delete List
;;   :conflict       — Phase 7.18: URL/localStorage conflict resolution
;;
;; `set-open-modal*` is mutex-by-construction (single value), so opening
;; any modal closes whatever else was open. `toggle-open-modal*` lets the
;; header icon buttons toggle: click while closed → open; click again
;; while open → close.
;; ============================================================================

(defn set-open-modal*
  "Mutex setter — overwrites whatever modal is currently open.

   B-9 fix (S-modal-open-clears-error): when opening any non-`:none`
   modal, also dissoc `:ui/err-msg`. The user's mental model:
   acting on the app (= opening a menu modal) moves them past
   whatever transient error was showing, so the page-level error
   should clear with the transition. Closing (transition to `:none`)
   does NOT clear — if a user dismisses a modal, we don't
   second-guess whether their pre-modal error is still relevant."
  [state-map list-ident modal-id]
  (cond-> (assoc-in state-map (conj list-ident :ui/open-modal) modal-id)
    (not= modal-id :none)
    (update-in list-ident dissoc :ui/err-msg)))

(defn toggle-open-modal*
  "If `modal-id` is currently open at `list-ident`, close it (set to
   :none); otherwise open it. Used by the header icon buttons so the
   same click both opens and dismisses."
  [state-map list-ident modal-id]
  (let [current (get-in state-map (conj list-ident :ui/open-modal))]
    (set-open-modal* state-map list-ident
      (if (= current modal-id) :none modal-id))))

;; ============================================================================
;; Theme + error message (Phase 7.7 / Phase 7.9)
;; ============================================================================

(defn toggle-theme*
  "Flip between :theme/light and :theme/dark. Defaults missing/unknown
   values to :theme/light → :theme/dark on first toggle."
  [state-map list-ident]
  (update-in state-map (conj list-ident :ui/theme)
    (fn [t] (if (= t :theme/dark) :theme/light :theme/dark))))

(defn set-locale*
  "Phase 12.5 — set the i18n locale at the given list-ident. Locale is
   a keyword in `learn.i18n.core/supported-locales`; validation is
   intentionally absent here so the helper stays a one-liner — the
   Settings dropdown is the only call site and it can only pick a
   supported value. The storage watch persists `:ui/locale` via
   `learn.util.storage/ui-prefs-whitelist`."
  [state-map list-ident locale]
  (assoc-in state-map (conj list-ident :ui/locale) locale))

(defn set-share-with-locale*
  "Phase 17 — set the 'Include language in URL' checkbox at the given
   list-ident. Boolean toggle controlling whether the Copy List URL
   action appends `&lang=<locale>` to the shareable URL.

   Persisted via `learn.util.storage/ui-prefs-whitelist` so the user's
   choice survives reloads — a sticky preference rather than a
   per-session reset."
  [state-map list-ident value]
  (assoc-in state-map (conj list-ident :ui/share-with-locale?) value))

(defn set-err-msg*
  "Set or clear the page-level error message. `msg` may be `nil` to
   clear; any string sets the visible error."
  [state-map list-ident msg]
  (assoc-in state-map (conj list-ident :ui/err-msg) msg))
