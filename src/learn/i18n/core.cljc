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
        ;; Save modal buttons (12.5b)
        :btn/copy-list-url  "Copy List URL"
        :btn/import         "Import"
        :btn/export         "Export"
        :btn/submit         "Submit"
        ;; Header icon tooltips
        :tooltip/import-export "Import/Export"
        :tooltip/info          "Info"
        :tooltip/settings      "Settings"
        :tooltip/toggle-theme  "Toggle Theme"
        ;; Modal headings
        :modal/info           "Info"
        :modal/settings       "Settings"
        :modal/import-export  "Import/Export"
        ;; Info modal body (12.5b)
        :info/heading-about         "About AutoFocus"
        :info/heading-help          "Instructions & Help"
        :info/about-1
        (str "The AutoFocus algorithm was designed by Mark Forster as a pen and "
             "paper method to help increase productivity. It does so by limiting "
             "list interaction and providing a simple (binary) decision-making "
             "framework.")
        :info/about-2
        (str "This web app is a Fulcro port of Avi Drucker's original "
             "ReactJS implementation. The port is built with Fulcro 3.9, "
             "Pathom 2 (in-process), com.fulcrologic/statecharts, "
             "shadow-cljs, Font Awesome (SVG), and Tachyons CSS.")
        :info/version-label         "Version"
        :info/instructions
        (str "Add new items to your list by typing into the input box and clicking "
             "'Add Item'. To prioritize your list, click 'Prioritize'. To mark the "
             "next actionable item as complete, click 'Mark Done'. To delete all "
             "items from your list, click 'Delete List'.")
        :info/instructions-2
        (str "Click the 'disk' icon to see options for list import/export. Click "
             "the 'i' icon for info about AutoFocus and these instructions. Click "
             "the 'gear' icon to change settings (including language). Click the "
             "'lightbulb' icon to toggle light/dark mode.")
        :info/report-issues         "To report any issues/bugs, please leave a ticket on the GitHub repo 'Issues' page here: "
        :info/click-i-circle        "Click on the 'i' icon above to close this window."
        ;; Settings modal footer (12.5b)
        :settings/language          "Language"
        :settings/click-gear        "Click on the 'gear' icon above to close this window."
        ;; Save modal body (12.5b)
        :save/info-1                "You can import and export JSON lists into and out of AutoFocus."
        :save/info-2                "You can also import a list by pasting in raw text below, and then clicking the 'Submit' button."
        :save/textarea-placeholder  "Paste your list here, with each item on a new line"
        :save/click-disk            "Click on the 'disk' icon above to close this window."
        ;; Phase 17 — Include-language checkbox in the save modal.
        :save/include-lang          "Include language in URL"
        ;; Phase 18 — locale-conflict modal question, shown in both
        ;; languages side-by-side so either reader can answer.
        :locale-conflict/question   "Which language do you want to use?"
        ;; Phase 15 — URL-length-safeguard error (S-max-url-length).
        ;; Surfaced when the encoded list would exceed MAX_URL_LENGTH;
        ;; the URL freezes at its last fitting value, localStorage
        ;; continues normally.
        :err/url-too-long           "Current list cannot be saved as URL: Please back up your list to text or JSON."
        ;; Phase 16 — translated error messages (B-8 closure). English
        ;; text is verbatim from `learn.ui.strings/<name>-err` so
        ;; existing tests that assert exact strings keep passing in
        ;; :en. Other locales are new.
        :err/empty-input            "New items cannot be empty or only whitespace."
        :err/nothing-to-delete      "There is nothing to delete."
        :err/cannot-take-action     "There are no actionable tasks in your list."
        :err/not-prioritizable      "The list isn't prioritizable right now."
        :err/empty-textarea         "New items cannot be empty or whitespace only."
        :err/bad-json-import        "Failed to import tasks. Ensure the JSON file has the correct format."
        :err/non-json-import        "Please select a valid JSON file."
        ;; B-13 — delete-confirm modal body + tooltips.
        :modal/confirm-delete       "Are you sure you want to delete your list? This action cannot be undone."
        :tooltip/cancel-delete      "cancel the delete list action"
        :tooltip/confirm-delete     "confirm the delete list action"
        ;; B-13 — review modal tooltips. The prompt itself is built
        ;; via `tr-review-question` below.
        :tooltip/quit-review        "quit the prioritization session"
        :tooltip/review-no          "answer no to the question"
        :tooltip/review-yes         "answer yes to the question"
        ;; B-13 — list-conflict modal text + buttons + tooltips.
        :conflict/mismatch          "The link list and local storage list do not match. Which will you keep?"
        :conflict/label-link        "1. List from the link address:"
        :conflict/label-local       "2. List from local storage:"
        :btn/copy-link-url          "Copy Link URL"
        :btn/copy-local-url         "Copy Local URL"
        :btn/keep-link              "1. Keep link list"
        :btn/keep-local             "2. Keep local list"
        :tooltip/copy-link-url      "Copy the link list URL to clipboard"
        :tooltip/copy-local-url     "Copy the local storage list URL to clipboard"
        :tooltip/keep-link          "keep the list from the link"
        :tooltip/keep-local         "keep the list from local storage"
        ;; Phase 19 — a11y audit. Migrating the last batch of
        ;; English-only tooltip / aria-label strings to i18n.
        :tooltip/cancel-task        "Cancel Task"
        :tooltip/clone-task         "Clone Task"
        :tooltip/add-item           "add a new item to your list"
        :tooltip/delete-list        "delete all tasks from your list"
        :tooltip/prioritize         "start a list prioritizing session"
        :tooltip/mark-done          "mark the next actionable item as complete"
        :tooltip/copy-list-url      "Copy the current URL to clipboard for sharing"
        :tooltip/export-json        "Export your list to a JSON file"
        :tooltip/include-lang       "When checked, the share link will open in this app's current language for whoever clicks it."
        :tooltip/import-json        "Click here to import a JSON file of to-do items."
        :tooltip/submit-text-import "Click here to import a text list of to-do items."
        :tooltip/language-dropdown  "Select a language from this list to change this app's language."
        :tooltip/switch-to-dark     "Switch to dark mode"
        :tooltip/switch-to-light    "Switch to light mode"
        :input/new-todo-placeholder "Type new task here"
        :input/new-todo-label       "New TODO:"
        :close/info                 "Close Info Modal"
        :close/settings             "Close Settings Modal"
        :close/save                 "Close Save Modal"
        :close/delete               "Close Delete Modal"}

   :es {:btn/add-item       "Añadir Tarea"
        :btn/delete-list    "Eliminar Lista"
        :btn/prioritize     "Priorizar"
        :btn/mark-done      "Marcar Hecha"
        :btn/yes            "Sí"
        :btn/no             "No"
        :btn/quit           "Salir"
        :btn/copy-list-url  "Copiar URL de la Lista"
        :btn/import         "Importar"
        :btn/export         "Exportar"
        :btn/submit         "Enviar"
        :tooltip/import-export "Importar/Exportar"
        :tooltip/info          "Información"
        :tooltip/settings      "Ajustes"
        :tooltip/toggle-theme  "Cambiar Tema"
        :modal/info           "Información"
        :modal/settings       "Ajustes"
        :modal/import-export  "Importar/Exportar"
        :info/heading-about         "Acerca de AutoFocus"
        :info/heading-help          "Instrucciones y Ayuda"
        :info/about-1
        (str "El algoritmo AutoFocus fue diseñado por Mark Forster como un "
             "método con papel y lápiz para ayudar a aumentar la productividad. "
             "Lo hace limitando la interacción con la lista y proporcionando un "
             "marco simple (binario) para la toma de decisiones.")
        :info/about-2
        (str "Esta aplicación web es una adaptación a Fulcro de la "
             "implementación original en ReactJS de Avi Drucker. La adaptación "
             "está construida con Fulcro 3.9, Pathom 2 (en proceso), "
             "com.fulcrologic/statecharts, shadow-cljs, Font Awesome (SVG) y "
             "Tachyons CSS.")
        :info/version-label         "Versión"
        :info/instructions
        (str "Añade nuevos elementos a tu lista escribiéndolos en el cuadro de "
             "texto y haciendo clic en 'Añadir Tarea'. Para priorizar tu lista, "
             "haz clic en 'Priorizar'. Para marcar el siguiente elemento "
             "accionable como completado, haz clic en 'Marcar Hecha'. Para "
             "eliminar todos los elementos de tu lista, haz clic en 'Eliminar "
             "Lista'.")
        :info/instructions-2
        (str "Haz clic en el icono de 'disco' para ver las opciones de "
             "importación/exportación de la lista. Haz clic en el icono de 'i' "
             "para información sobre AutoFocus y estas instrucciones. Haz clic "
             "en el icono de 'engranaje' para cambiar los ajustes (incluido el "
             "idioma). Haz clic en el icono de 'bombilla' para alternar entre "
             "modo claro y oscuro.")
        :info/report-issues         "Para informar de cualquier problema/error, deja un ticket en la página 'Issues' del repositorio de GitHub aquí: "
        :info/click-i-circle        "Haz clic en el icono de 'i' arriba para cerrar esta ventana."
        :settings/language          "Idioma"
        :settings/click-gear        "Haz clic en el icono de 'engranaje' arriba para cerrar esta ventana."
        :save/info-1                "Puedes importar y exportar listas JSON dentro y fuera de AutoFocus."
        :save/info-2                "También puedes importar una lista pegando texto sin formato a continuación y haciendo clic en el botón 'Enviar'."
        :save/textarea-placeholder  "Pega tu lista aquí, con cada elemento en una nueva línea"
        :save/click-disk            "Haz clic en el icono de 'disco' arriba para cerrar esta ventana."
        :save/include-lang          "Incluir idioma en la URL"
        :locale-conflict/question   "¿Qué idioma quieres usar?"
        :err/url-too-long           "La lista actual no se puede guardar como URL: respalda tu lista en formato texto o JSON."
        :err/empty-input            "Los elementos nuevos no pueden estar vacíos o contener solo espacios en blanco."
        :err/nothing-to-delete      "No hay nada que eliminar."
        :err/cannot-take-action     "No hay tareas accionables en tu lista."
        :err/not-prioritizable      "La lista no se puede priorizar en este momento."
        :err/empty-textarea         "Los elementos nuevos no pueden estar vacíos o contener solo espacios en blanco."
        :err/bad-json-import        "Error al importar tareas. Asegúrate de que el archivo JSON tenga el formato correcto."
        :err/non-json-import        "Por favor selecciona un archivo JSON válido."
        :modal/confirm-delete       "¿Estás seguro de que quieres eliminar tu lista? Esta acción no se puede deshacer."
        :tooltip/cancel-delete      "cancelar la acción de eliminar la lista"
        :tooltip/confirm-delete     "confirmar la acción de eliminar la lista"
        :tooltip/quit-review        "salir de la sesión de priorización"
        :tooltip/review-no          "responder no a la pregunta"
        :tooltip/review-yes         "responder sí a la pregunta"
        :conflict/mismatch          "La lista del enlace y la lista del almacenamiento local no coinciden. ¿Cuál quieres conservar?"
        :conflict/label-link        "1. Lista desde la dirección del enlace:"
        :conflict/label-local       "2. Lista desde el almacenamiento local:"
        :btn/copy-link-url          "Copiar URL del Enlace"
        :btn/copy-local-url         "Copiar URL Local"
        :btn/keep-link              "1. Conservar lista del enlace"
        :btn/keep-local             "2. Conservar lista local"
        :tooltip/copy-link-url      "Copiar la URL de la lista del enlace al portapapeles"
        :tooltip/copy-local-url     "Copiar la URL de la lista local al portapapeles"
        :tooltip/keep-link          "conservar la lista del enlace"
        :tooltip/keep-local         "conservar la lista del almacenamiento local"
        :tooltip/cancel-task        "Cancelar Tarea"
        :tooltip/clone-task         "Clonar Tarea"
        :tooltip/add-item           "añadir un nuevo elemento a tu lista"
        :tooltip/delete-list        "eliminar todas las tareas de tu lista"
        :tooltip/prioritize         "iniciar una sesión de priorización de la lista"
        :tooltip/mark-done          "marcar el siguiente elemento accionable como completado"
        :tooltip/copy-list-url      "Copiar la URL actual al portapapeles para compartir"
        :tooltip/export-json        "Exportar tu lista a un archivo JSON"
        :tooltip/include-lang       "Cuando esté marcado, el enlace de compartir se abrirá en el idioma actual de esta aplicación para quien haga clic en él."
        :tooltip/import-json        "Haz clic aquí para importar un archivo JSON de tareas."
        :tooltip/submit-text-import "Haz clic aquí para importar una lista de tareas en formato texto."
        :tooltip/language-dropdown  "Selecciona un idioma de esta lista para cambiar el idioma de esta aplicación."
        :tooltip/switch-to-dark     "Cambiar a modo oscuro"
        :tooltip/switch-to-light    "Cambiar a modo claro"
        :input/new-todo-placeholder "Escribe una nueva tarea aquí"
        :input/new-todo-label       "Nueva tarea:"
        :close/info                 "Cerrar Modal de Información"
        :close/settings             "Cerrar Modal de Ajustes"
        :close/save                 "Cerrar Modal de Guardado"
        :close/delete               "Cerrar Modal de Eliminación"}

   :ja {:btn/add-item       "項目を追加"
        :btn/delete-list    "リストを削除"
        :btn/prioritize     "優先する"
        :btn/mark-done      "完了にする"
        :btn/yes            "はい"
        :btn/no             "いいえ"
        :btn/quit           "終了"
        :btn/copy-list-url  "リストURLをコピー"
        :btn/import         "インポート"
        :btn/export         "エクスポート"
        :btn/submit         "送信"
        :tooltip/import-export "インポート／エクスポート"
        :tooltip/info          "情報"
        :tooltip/settings      "設定"
        :tooltip/toggle-theme  "テーマ切替"
        :modal/info           "情報"
        :modal/settings       "設定"
        :modal/import-export  "インポート／エクスポート"
        :info/heading-about         "AutoFocusについて"
        :info/heading-help          "使い方とヘルプ"
        :info/about-1
        (str "AutoFocusアルゴリズムは、生産性向上のための紙とペンの方法として "
             "Mark Forster によって考案されました。リストとの操作を制限し、"
             "シンプルな（二者択一の）意思決定の枠組みを提供することで実現します。")
        :info/about-2
        (str "このウェブアプリは、Avi Drucker のオリジナルの ReactJS 実装を "
             "Fulcro に移植したものです。Fulcro 3.9、Pathom 2（インプロセス）、"
             "com.fulcrologic/statecharts、shadow-cljs、Font Awesome (SVG)、"
             "および Tachyons CSS で構築されています。")
        :info/version-label         "バージョン"
        :info/instructions
        (str "入力欄に入力して「項目を追加」をクリックすると、リストに新しい項目を"
             "追加できます。リストの優先順位を付けるには「優先する」を"
             "クリックします。次の実行可能な項目を完了にするには「完了にする」を"
             "クリックします。リストからすべての項目を削除するには「リストを削除」"
             "をクリックします。")
        :info/instructions-2
        (str "「ディスク」アイコンをクリックすると、リストのインポート／エクスポート"
             "オプションが表示されます。「i」アイコンをクリックすると、AutoFocus と"
             "この使い方の情報が表示されます。「歯車」アイコンをクリックすると、"
             "設定（言語を含む）を変更できます。「電球」アイコンをクリックすると、"
             "ライト／ダークモードを切り替えます。")
        :info/report-issues         "問題やバグを報告するには、GitHub リポジトリの「Issues」ページにチケットを残してください: "
        :info/click-i-circle        "上の「i」アイコンをクリックすると、このウィンドウを閉じます。"
        :settings/language          "言語"
        :settings/click-gear        "上の「歯車」アイコンをクリックすると、このウィンドウを閉じます。"
        :save/info-1                "AutoFocus に JSON リストをインポート／エクスポートできます。"
        :save/info-2                "下のテキストエリアに生のテキストを貼り付けて「送信」ボタンをクリックすることでも、リストをインポートできます。"
        :save/textarea-placeholder  "リストをここに貼り付けてください。1 行につき 1 項目です。"
        :save/click-disk            "上の「ディスク」アイコンをクリックすると、このウィンドウを閉じます。"
        :save/include-lang          "URLに言語を含める"
        :locale-conflict/question   "どの言語を使用しますか？"
        :err/url-too-long           "現在のリストはURLとして保存できません。リストをテキストまたはJSONにバックアップしてください。"
        :err/empty-input            "新しい項目は空または空白のみにすることはできません。"
        :err/nothing-to-delete      "削除するものがありません。"
        :err/cannot-take-action     "リストに実行可能なタスクがありません。"
        :err/not-prioritizable      "現在、リストは優先順位を付けられません。"
        :err/empty-textarea         "新しい項目は空または空白のみにすることはできません。"
        :err/bad-json-import        "タスクのインポートに失敗しました。JSONファイルの形式が正しいことを確認してください。"
        :err/non-json-import        "有効なJSONファイルを選択してください。"
        :modal/confirm-delete       "リストを本当に削除しますか？この操作は元に戻せません。"
        :tooltip/cancel-delete      "リスト削除をキャンセル"
        :tooltip/confirm-delete     "リスト削除を確定"
        :tooltip/quit-review        "優先順位付けセッションを終了"
        :tooltip/review-no          "質問に「いいえ」と答える"
        :tooltip/review-yes         "質問に「はい」と答える"
        :conflict/mismatch          "リンクのリストとローカルのリストが一致しません。どちらを保存しますか？"
        :conflict/label-link        "1. リンクアドレスからのリスト:"
        :conflict/label-local       "2. ローカル保存からのリスト:"
        :btn/copy-link-url          "リンクURLをコピー"
        :btn/copy-local-url         "ローカルURLをコピー"
        :btn/keep-link              "1. リンクのリストを保存"
        :btn/keep-local             "2. ローカルのリストを保存"
        :tooltip/copy-link-url      "リンクリストのURLをクリップボードにコピー"
        :tooltip/copy-local-url     "ローカル保存リストのURLをクリップボードにコピー"
        :tooltip/keep-link          "リンクのリストを保存する"
        :tooltip/keep-local         "ローカル保存のリストを保存する"
        :tooltip/cancel-task        "タスクをキャンセル"
        :tooltip/clone-task         "タスクを複製"
        :tooltip/add-item           "リストに新しい項目を追加"
        :tooltip/delete-list        "リストからすべてのタスクを削除"
        :tooltip/prioritize         "リストの優先順位付けセッションを開始"
        :tooltip/mark-done          "次の実行可能な項目を完了としてマーク"
        :tooltip/copy-list-url      "現在のURLをクリップボードにコピーして共有"
        :tooltip/export-json        "リストをJSONファイルにエクスポート"
        :tooltip/include-lang       "チェックすると、共有リンクをクリックした相手にこのアプリの現在の言語で開かれます。"
        :tooltip/import-json        "クリックして、ToDo項目のJSONファイルをインポートします。"
        :tooltip/submit-text-import "クリックして、ToDo項目のテキストリストをインポートします。"
        :tooltip/language-dropdown  "このリストから言語を選択して、このアプリの言語を変更します。"
        :tooltip/switch-to-dark     "ダークモードに切り替える"
        :tooltip/switch-to-light    "ライトモードに切り替える"
        :input/new-todo-placeholder "ここに新しいタスクを入力"
        :input/new-todo-label       "新しいToDo:"
        :close/info                 "情報モーダルを閉じる"
        :close/settings             "設定モーダルを閉じる"
        :close/save                 "保存モーダルを閉じる"
        :close/delete               "削除モーダルを閉じる"}})

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

(defn tr-status
  "Phase 19f a11y — locale-aware accessible name for a todo's
   status indicator. Used on the wrapping `<span role=\"img\">` in
   TodoItem and the review-confirm preview so screen readers
   announce per-row status.

   When the row is cancelled, `was` (the pre-cancel status)
   surfaces inside parentheses — matches the JS port's
   `statusToSymbol(task.was)` recursion. A `nil` `was` falls back
   to the plain cancelled label.

   Pure: no side effects, no DOM. JVM-testable."
  [locale status was]
  (let [simple (fn [s]
                 (case s
                   :status/new
                   (case locale :es "nuevo" :ja "新規" "new")
                   :status/ready
                   (case locale :es "listo" :ja "準備完了" "ready")
                   :status/done
                   (case locale :es "hecho" :ja "完了" "done")
                   :status/cancelled
                   (case locale :es "cancelado" :ja "キャンセル" "cancelled")
                   nil))]
    (if (and (= status :status/cancelled) was)
      (case locale
        :es (str "cancelado (antes: " (simple was) ")")
        :ja (str "キャンセル済み（元：" (simple was) "）")
        (str "cancelled (was " (simple was) ")"))
      (simple status))))

(defn tr-review-question
  "B-13 — review modal prompt, parameterized over the cursor item's
   text and the benchmark item's text.
   - English / Spanish: cursor-first phrasing ('more ready to <cursor>
     than <benchmark>?').
   - Japanese: reverses the order so it reads naturally — literally
     'than <benchmark>, are you ready to <cursor>?'.
   Caller (`learn.client.ui.components` TodoList review branch)
   pulls the two texts from `learn.model.review/current-question`."
  [locale cursor-text benchmark-text]
  (case locale
    :es (str "En este momento, ¿estás más listo/a para '"
          cursor-text "' que para '" benchmark-text "'?")
    :ja (str "今この瞬間、「" benchmark-text "」よりも「"
          cursor-text "」をする準備ができていますか？")
    (str "In this moment, are you more ready to '"
      cursor-text "' than '" benchmark-text "'?")))
