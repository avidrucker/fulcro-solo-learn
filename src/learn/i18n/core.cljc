(ns learn.i18n.core
  "Phase 12.4 — minimal i18n. Hand-rolled translation map + a `tr`
   lookup fn. See `docs/benefits-of-i18n-in-this-project.md` for the
   tradeoff write-up (why this and not `com.fulcrologic/fulcro-i18n`).

   Translations are keyed by namespaced keywords (`:btn/add-item`,
   `:err/empty-input`, etc.) so the call sites read naturally and
   the lookup graph is greppable. Each language gets a map of those
   keys to strings; `:en` is the canonical source and the fallback
   for missing keys in other locales.

   Parameterized strings (e.g. 'You have N items') get their own
   per-locale fn (`tr-list-count`, `tr-next-actionable`) because
   pluralization rules differ by language and a string-template
   approach gets ugly fast at this scale. With more languages we'd
   look at message-format / ICU, but for 3 languages the explicit
   per-locale function shape is clear.")

;; ============================================================================
;; Locale registry
;; ============================================================================

(def supported-locales
  "The set of locales we currently ship translations for. Driving the
   Settings modal's dropdown reads from this set."
  #{:en :es :ja})

(def default-locale
  "Fallback locale when none is set in app state or user prefs."
  :en)

(def ^:private locale-labels
  {:en "English"
   :es "Español"
   :ja "日本語"})

(defn locale-label
  "Human-readable display name for a locale (used by the Settings
   dropdown). Unknown locales return their keyword name as a string."
  [locale]
  (or (get locale-labels locale) (str locale)))

;; ============================================================================
;; Simple-string translations
;; ============================================================================

(def ^:private translations
  "Locale → key → translated string. `:en` is the canonical source;
   other locales fall back to `:en` for missing keys. Keep keys
   namespaced so call-site searches like `(tr ... :btn/...)` are
   easy to grep."
  {:en {;; Page-level buttons
        :btn/add-item       "Add Item"
        :btn/delete-list    "Delete List"
        :btn/prioritize     "Prioritize"
        :btn/mark-done      "Mark Done"
        ;; Review modal buttons
        :btn/yes            "Yes"
        :btn/no             "No"
        :btn/quit           "Quit"
        ;; Header icon tooltips
        :tooltip/import-export "Import/Export"
        :tooltip/info          "Info"
        :tooltip/settings      "Settings"
        :tooltip/toggle-theme  "Toggle Theme"
        ;; Modal headings
        :modal/info           "Info"
        :modal/settings       "Settings"
        :modal/import-export  "Import/Export"
        ;; Settings labels (added in 12.5)
        :settings/language     "Language"}

   :es {:btn/add-item       "Añadir Tarea"
        :btn/delete-list    "Eliminar Lista"
        :btn/prioritize     "Priorizar"
        :btn/mark-done      "Marcar Hecha"
        :btn/yes            "Sí"
        :btn/no             "No"
        :btn/quit           "Salir"
        :tooltip/import-export "Importar/Exportar"
        :tooltip/info          "Información"
        :tooltip/settings      "Ajustes"
        :tooltip/toggle-theme  "Cambiar Tema"
        :modal/info           "Información"
        :modal/settings       "Ajustes"
        :modal/import-export  "Importar/Exportar"
        :settings/language     "Idioma"}

   :ja {:btn/add-item       "項目を追加"
        :btn/delete-list    "リストを削除"
        :btn/prioritize     "優先順位を付ける"
        :btn/mark-done      "完了にする"
        :btn/yes            "はい"
        :btn/no             "いいえ"
        :btn/quit           "終了"
        :tooltip/import-export "インポート／エクスポート"
        :tooltip/info          "情報"
        :tooltip/settings      "設定"
        :tooltip/toggle-theme  "テーマ切替"
        :modal/info           "情報"
        :modal/settings       "設定"
        :modal/import-export  "インポート／エクスポート"
        :settings/language     "言語"}})

(defn tr
  "Look up a translation for `key` in `locale`. Fallback order:
   1. The requested locale's map.
   2. `:en` (the canonical source).
   3. The keyword name as a string — so missing keys are visible
      in the UI instead of silently rendering nil."
  [locale key]
  (or (get-in translations [locale key])
      (get-in translations [default-locale key])
      (str key)))

;; ============================================================================
;; Parameterized translations
;; ============================================================================

(defn tr-list-count
  "`You have N item(s) in your list.` — locale-aware pluralization."
  [locale n]
  (case locale
    :es (str "Tienes " n " tarea" (when (not= 1 n) "s") " en tu lista.")
    :ja (str "リストに" n "個の項目があります。")
    ;; :en default
    (str "You have " n " item" (when (not= 1 n) "s") " in your list.")))

(defn tr-next-actionable
  "`The next actionable item is '<text>'.` — only rendered when a
   benchmark exists; caller is responsible for the nil-guard."
  [locale text]
  (case locale
    :es (str "El próximo elemento accionable es '" text "'.")
    :ja (str "次の実行可能な項目は '" text "' です。")
    (str "The next actionable item is '" text "'.")))
