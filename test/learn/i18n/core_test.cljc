(ns learn.i18n.core-test
  "Phase 12.4 — pure specs for the i18n lookup helper."
  (:require
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
