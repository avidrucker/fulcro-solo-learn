(ns learn.ui.strings
  "All user-facing strings for the AutoFocus UI, centralized.

   Source of truth (Phase 6.5.1): the original JS port at
   https://github.com/avidrucker/pwa-autofocus-app — captured in
   `docs/js_ui_reference.md` §A. Strings are mirrored verbatim where
   the JS has a named constant, and given kebab-case names where the
   JS source had them inline.

   Some strings here are for features we haven't ported yet (delete-list
   confirmation, import/export, conflict resolution, debug modal, theme
   toggle). They live here anyway so future phases don't reinvent them.

   Phase 12 will turn this namespace into the i18n attachment point
   (`fulcro-i18n` registers translations by string identity). Keeping
   one canonical name per string now means the migration is mechanical.

   Templated strings (count-with-pluralization, next-actionable preview)
   are exposed as 1-arg functions rather than format-template constants
   so the call site stays a single readable expression.")

;; ============================================================================
;; App-level
;; ============================================================================

(def app-name           "AutoFocus")
(def app-version        "0.1.4")    ; sem-ver from JS App.js — tracks upstream

;; ============================================================================
;; Inline labels — input/button text, placeholders. Sourced from JSX literals
;; in App.js and TodoItem.js.
;; ============================================================================

(def input-placeholder    "Type new task here")
(def textarea-placeholder "Paste your list here, with each item on a new line")

(def btn-add-item         "Add Item")
(def btn-delete-list      "Delete List")
(def btn-prioritize       "Prioritize")
(def btn-mark-done        "Mark Done")

;; Review/prioritization modal
(def btn-quit             "Quit")
(def btn-no               "No")
(def btn-yes              "Yes")

;; Save / Import-Export modal
(def heading-import-export "Import/Export")
(def btn-copy-list-url    "Copy List URL")
(def btn-import           "Import")
(def btn-export           "Export")
(def btn-submit           "Submit")

;; About modal
(def heading-about        "About AutoFocus")
(def btn-enable-debug     "Enable Debug Mode")
(def btn-disable-debug    "Disable Debug Mode")
(def label-debug-visible  "Debug tools are visible")
(def label-debug-hidden   "Debug tools are hidden")
(def heading-debug-mode   "Debug Mode")              ; visually hidden in JS

;; Help modal
(def heading-help         "Instructions & Help")
(def link-issues-text     "fulcro-solo-learn Issues")
(def link-issues-href     "https://github.com/avidrucker/fulcro-solo-learn/issues")

;; Conflict-resolution modal
(def label-link-list      "1. List from the link address:")
(def label-local-list     "2. List from local storage:")
(def btn-copy-link-url    "Copy Link URL")
(def btn-copy-local-url   "Copy Local URL")
(def btn-keep-link        "1. Keep link list")
(def btn-keep-local       "2. Keep local list")

;; PWA Debug modal
(def heading-pwa-debug    "PWA Debug Info")
(def btn-refresh-debug    "Refresh Debug Info")
(def btn-close-debug      "Close Debug Info")
(def heading-sw-status    "Service Worker Status:")
(def heading-cache-status "Cache Status:")
(def heading-offline-status "Offline Status:")
(def heading-general-info "General Info:")
(def fallback-debug-empty "Click \"Refresh Debug Info\" to run diagnostics")

;; TodoItem (item row)
(def title-cancel-task    "Cancel Task")
(def title-clone-task     "Clone Task")

;; Modal close-overlay labels (full-area transparent buttons)
(def close-save-modal     "Close Save Modal")
(def close-info-modal     "Close Info Modal")
(def close-help-modal     "Close Help Modal")
(def close-debug-modal    "Close Debug Modal")
(def close-delete-modal   "Close Delete Modal")

;; ============================================================================
;; Named error / info constants — verbatim from App.js constants block.
;; Sub-groupings preserved.
;; ============================================================================

;; About-modal copy
(def info-string-1
  (str "The AutoFocus algorithm was designed by Mark Forster as a pen and "
       "paper method to help increase productivity. It does so by limiting "
       "list interaction and providing a simple (binary) decision-making "
       "framework."))

(def info-string-2
  (str "This web app is a Fulcro port of Avi Drucker's original "
       "ReactJS implementation. The port is built with Fulcro 3.9, "
       "Pathom 2 (in-process), com.fulcrologic/statecharts, "
       "shadow-cljs, Font Awesome (SVG), and Tachyons CSS."))

;; Save / Import-Export modal copy
(def save-info-1
  "You can import and export JSON lists into and out of AutoFocus.")

(def save-info-2
  (str "You can also import a list by pasting in raw text below, and "
       "then clicking the 'Submit' button."))

;; Top-of-page error messages
(def empty-input-err
  "New items cannot be empty or only whitespace.")

(def cannot-take-action-err
  "There are no actionable tasks in your list.")

(def not-prioritizable-err
  "The list isn't prioritizable right now.")

(def max-list-length-err
  (str "Maximum list length reached. Please create a new list to continue "
       "adding items."))

(def non-json-import-err
  "Please select a valid JSON file.")

(def invalid-query-params-err
  "Invalid list query parameters detected. Reverting to local storage list data.")

(def nothing-to-delete-err
  "There is nothing to delete.")

(def export-fail-err
  "Failed to export tasks.")

;; Import-modal-scoped error messages
(def empty-textarea-err
  "New items cannot be empty or whitespace only.")

(def bad-json-import-err
  "Failed to import tasks. Ensure the JSON file has the correct format.")

;; Conflict-resolution modal copy
(def mismatch-detected
  "The link list and local storage list do not match. Which will you keep?")

;; Delete-confirm modal copy
(def confirm-list-delete
  "Are you sure you want to delete your list? This action cannot be undone.")

;; Modal close-instruction footers
(def click-disk-to-close
  "Click on the 'disk' icon above to close this window.")

(def click-question-circle-to-close
  "Click on the 'question mark' icon above to close this window.")

(def click-i-circle-to-close
  "Click on the 'i' icon above to close this window.")

;; Help modal instructional copy
(def instructions
  (str "Add new items to your list by typing into the input box and clicking "
       "'Add Item'. To prioritize your list, click 'Prioritize'. To mark the "
       "next actionable item as complete, click 'Mark Done'. To delete all "
       "items from your list, click 'Delete List'."))

(def instructions-2
  (str "Click the 'disk' icon to see options for list import/export. Click "
       "the 'i' icon to learn more about AutoFocus. Click the 'lightbulb' "
       "icon to toggle light/dark mode. Click the 'question mark' icon for "
       "instructions on how to use this app."))

(def how-to-report-issues
  "To report any issues/bugs, please leave a ticket on the GitHub repo 'Issues' page here: ")

;; ============================================================================
;; Button title (tooltip) strings — double as a11y labels.
;; ============================================================================

;; Header buttons
(def tooltip-import-export "Import/Export")
(def tooltip-about        "About")
(def tooltip-help         "Help")
(def tooltip-toggle-theme "Toggle Theme")
(def tooltip-pwa-debug    "PWA Debug Info")

;; Form / main affordances
(def tooltip-add-item     "add a new item to your list")
(def tooltip-delete-list  "delete all tasks from your list")
(def tooltip-prioritize   "start a list prioritizing session")
(def tooltip-mark-done    "mark the next actionable item as complete")

;; Review modal
(def tooltip-quit-review  "quit the prioritization session")
(def tooltip-review-no    "answer no to the question")
(def tooltip-review-yes   "answer yes to the question")

;; Delete-confirm modal
(def tooltip-cancel-delete  "cancel the delete list action")
(def tooltip-confirm-delete "confirm the delete list action")

;; Save modal
(def tooltip-copy-list-url   "Copy the current URL to clipboard for sharing")
(def tooltip-upload-json     "Upload a JSON file to import tasks")
(def tooltip-export-json     "Export your list to a JSON file")

;; About modal
(def tooltip-enable-debug    "Enable debug mode")
(def tooltip-disable-debug   "Disable debug mode")

;; Conflict modal
(def tooltip-copy-link-url   "Copy the link list URL to clipboard")
(def tooltip-copy-local-url  "Copy the local storage list URL to clipboard")
(def tooltip-keep-link-list  "keep the list from the link")
(def tooltip-keep-local-list "keep the list from local storage")

;; PWA debug modal
(def tooltip-run-pwa-debug   "Run PWA Debugger")

;; ============================================================================
;; Templated strings — render as 1-arg fns rather than format templates so
;; the caller stays a single expression. Pure `str` (no `format`) keeps both
;; CLJ and CLJS targets compiling.
;; ============================================================================

(defn list-count-line
  "`You have N item(s) in your list.` — pluralizes correctly for N=0,1,N."
  [n]
  (str "You have " n " item" (when (not= n 1) "s") " in your list."))

(defn next-actionable-line
  "`The next actionable item is '<text>'.` — only shown when a benchmark
   exists; caller is responsible for the `nil?`-guard."
  [text]
  (str "The next actionable item is '" text "'."))

(defn version-line
  "`Version X.Y.Z` — About modal."
  ([] (version-line app-version))
  ([sem-ver] (str "Version " sem-ver)))

;; ============================================================================
;; Notes
;;
;; The review question prompt itself ("In this moment, are you more ready
;; to '{X}' than '{Y}'?") is generated by `learn.model.review/current-question`
;; rather than living here, because that function is in the pure domain
;; layer and the template is the function's reason for existing. If i18n
;; lands (Phase 12), the prompt template moves here and `current-question`
;; becomes a thin wrapper.
;; ============================================================================
