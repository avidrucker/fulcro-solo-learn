(ns learn.i18n.core-test
  "Phase 12.4 — pure specs for the i18n lookup helper."
  (:require
    [clojure.string :as str]
    [fulcro-spec.core :refer [specification component assertions =>]]
    [learn.i18n.core :as sut]))

(specification "tr"
  (component "happy path"
    (assertions
      "looks up a translated string for the requested locale"
      (sut/tr :en :btn/add-item) => "Add Item"
      (sut/tr :es :btn/add-item) => "Añadir Tarea"
      (sut/tr :ja :btn/add-item) => "項目を追加"))

  (component "fallback to :en when key missing for requested locale"
    (assertions
      ;; If a key is only defined for :en (not yet translated), the
      ;; requested locale should fall back to :en rather than show
      ;; the raw keyword name to the user.
      "missing :es key falls back to :en"
      (sut/tr :es :_missing/key) => (sut/tr :en :_missing/key)
      "missing :ja key falls back to :en"
      (sut/tr :ja :_missing/key) => (sut/tr :en :_missing/key)))

  (component "fallback to key-as-string when missing everywhere"
    (assertions
      "unknown key in :en too — render keyword name so the gap is visible"
      (sut/tr :en :totally-unknown/key)
      => ":totally-unknown/key"))

  (component "unknown locale falls back to :en"
    (assertions
      "an unsupported locale gets :en's translation"
      (sut/tr :fr :btn/add-item) => "Add Item")))

(specification "supported-locales"
  (assertions
    "at minimum :en, :es, :ja are present"
    (contains? sut/supported-locales :en) => true
    (contains? sut/supported-locales :es) => true
    (contains? sut/supported-locales :ja) => true))

(specification "locale-label"
  (assertions
    "human-readable label for each supported locale (used in the dropdown)"
    (sut/locale-label :en) => "English"
    (sut/locale-label :es) => "Español"
    (sut/locale-label :ja) => "日本語"
    "unknown locale returns its keyword name as a string"
    (sut/locale-label :fr) => ":fr"))

(specification "tr-list-count (parameterized)"
  (component "English pluralization"
    (assertions
      "0 → plural"
      (sut/tr-list-count :en 0) => "You have 0 items in your list."
      "1 → singular"
      (sut/tr-list-count :en 1) => "You have 1 item in your list."
      "many → plural"
      (sut/tr-list-count :en 5) => "You have 5 items in your list."))

  (component "Spanish pluralization"
    (assertions
      (sut/tr-list-count :es 1) => "Tienes 1 tarea en tu lista."
      (sut/tr-list-count :es 5) => "Tienes 5 tareas en tu lista."))

  (component "Japanese — no plural form"
    (assertions
      (sut/tr-list-count :ja 1) => "リストに1個の項目があります。"
      (sut/tr-list-count :ja 5) => "リストに5個の項目があります。")))

(specification "tr — Phase 19e tooltip keys"
  ;; Phase 19e — four tooltips added for controls that previously had
  ;; no accessible name beyond their visible label. Each must resolve
  ;; to a translated string (not the fallback `:keyword-as-string`)
  ;; in every supported locale so screen reader announce on focus.
  (let [keys [:tooltip/include-lang
              :tooltip/import-json
              :tooltip/submit-text-import
              :tooltip/language-dropdown]
        translated? (fn [locale k]
                      (let [s (sut/tr locale k)]
                        (and (string? s)
                             ;; The `tr` fallback path turns missing
                             ;; keys into `(str key)` → ":ns/name".
                             ;; A real translation never starts with ':'.
                             (not (str/starts-with? s ":")))))]
    (assertions
      "each key has a real :en translation"
      (every? #(translated? :en %) keys) => true
      "each key has a real :es translation"
      (every? #(translated? :es %) keys) => true
      "each key has a real :ja translation"
      (every? #(translated? :ja %) keys) => true
      "locked en string for the include-language checkbox"
      (sut/tr :en :tooltip/include-lang)
      => "When checked, the share link will open in this app's current language for whoever clicks it.")))

(specification "tr — Phase 19l/19m a11y i18n keys"
  ;; 19l added the localized new-todo input placeholder + clip-label.
  ;; 19m added the locale-aware theme-toggle direction labels. Both
  ;; need to resolve to real translations in every locale so the
  ;; UI flips with `:ui/locale` instead of silently rendering the
  ;; English fallback or the raw keyword name.
  (let [keys [:input/new-todo-placeholder
              :input/new-todo-label
              :tooltip/switch-to-dark
              :tooltip/switch-to-light]
        translated? (fn [locale k]
                      (let [s (sut/tr locale k)]
                        (and (string? s)
                             (not (str/starts-with? s ":")))))]
    (assertions
      "each key has a real :en translation"
      (every? #(translated? :en %) keys) => true
      "each key has a real :es translation"
      (every? #(translated? :es %) keys) => true
      "each key has a real :ja translation"
      (every? #(translated? :ja %) keys) => true)))

(specification "tr-status (parameterized — Phase 19f)"
  ;; Locale-aware accessible name for a todo's status indicator.
  ;; Used on the wrapping `<span role=\"img\">` in TodoItem and the
  ;; review-confirm preview so screen readers can announce per-row
  ;; status. When the row is cancelled, the pre-cancel status
  ;; (`:todo/was`) is surfaced — matches the JS port's
  ;; `statusToSymbol(task.was)` recursion.
  (component "simple statuses"
    (assertions
      "English"
      (sut/tr-status :en :status/new       nil) => "new"
      (sut/tr-status :en :status/ready     nil) => "ready"
      (sut/tr-status :en :status/done      nil) => "done"
      (sut/tr-status :en :status/cancelled nil) => "cancelled"
      "Spanish"
      (sut/tr-status :es :status/new       nil) => "nuevo"
      (sut/tr-status :es :status/ready     nil) => "listo"
      (sut/tr-status :es :status/done      nil) => "hecho"
      (sut/tr-status :es :status/cancelled nil) => "cancelado"
      "Japanese"
      (sut/tr-status :ja :status/new       nil) => "新規"
      (sut/tr-status :ja :status/ready     nil) => "準備完了"
      (sut/tr-status :ja :status/done      nil) => "完了"
      (sut/tr-status :ja :status/cancelled nil) => "キャンセル"))

  (component "cancelled with prior state surfaces both"
    (assertions
      "English: 'cancelled (was X)'"
      (sut/tr-status :en :status/cancelled :status/ready)
      => "cancelled (was ready)"
      (sut/tr-status :en :status/cancelled :status/new)
      => "cancelled (was new)"
      "Spanish: 'cancelado (antes: X)'"
      (sut/tr-status :es :status/cancelled :status/ready)
      => "cancelado (antes: listo)"
      "Japanese: 'キャンセル済み（元：X）'"
      (sut/tr-status :ja :status/cancelled :status/ready)
      => "キャンセル済み（元：準備完了）"))

  (component "cancelled without prior state falls back to plain cancelled"
    (assertions
      "no `was` → plain cancelled"
      (sut/tr-status :en :status/cancelled nil) => "cancelled"
      (sut/tr-status :es :status/cancelled nil) => "cancelado"
      (sut/tr-status :ja :status/cancelled nil) => "キャンセル"))

  (component "unknown locale falls back to English"
    (assertions
      "matches :en for simple statuses"
      (sut/tr-status :fr :status/ready nil) => "ready"
      "matches :en for cancelled-with-was"
      (sut/tr-status :fr :status/cancelled :status/ready)
      => "cancelled (was ready)")))

(specification "tr-review-question (parameterized — B-13)"
  ;; Counterpart to `learn.model.review/current-question` (which now
  ;; returns the two texts as data). The UI calls this to format the
  ;; locale-appropriate review prompt.
  (component "English"
    (assertions
      "quotes both texts, cursor first"
      (sut/tr-review-question :en "Walk the dog" "Read the Fulcro book")
      => "In this moment, are you more ready to 'Walk the dog' than 'Read the Fulcro book'?"))

  (component "Spanish"
    (assertions
      "uses inverted ¿…? punctuation"
      (sut/tr-review-question :es "Pasear al perro" "Leer el libro de Fulcro")
      => "En este momento, ¿estás más listo/a para 'Pasear al perro' que para 'Leer el libro de Fulcro'?"))

  (component "Japanese"
    (assertions
      "reverses cursor/benchmark order to read naturally in Japanese
       (literally 'than benchmark, are you ready to cursor?')"
      (sut/tr-review-question :ja "犬の散歩" "Fulcroの本を読む")
      => "今この瞬間、「Fulcroの本を読む」よりも「犬の散歩」をする準備ができていますか？")))
