# AutoFocus JS UI Reference (porting spec)

Source: `pwa-autofocus-app/src/App.js` (~984 lines) and `pwa-autofocus-app/src/TodoItem.js` (~43 lines).
Tachyons CSS throughout. App version string in JS source: `"0.1.4"`.

Captured 2026-05-12 to support Phase 6.5 (strings + Tachyons port).
Pairs with `docs/js_source_reference.md` (which covers the domain functions).

---

## File 1: `App.js`

### A. User-facing strings

**Constants block (top of file)**
- `appName` — `"AutoFocus"` — page `<h1>` and About modal title fragment.
- `infoString1` — `"The AutoFocus algorithm was designed by Mark Forster as a pen and paper method to help increase productivity. It does so by limiting list interaction and providing a simple (binary) decision-making framework."` — About modal body, 1st paragraph.
- `infoString2` — `"This web app was built by Avi Drucker using ReactJS, Font Awesome, and Tachyons CSS."` — About modal body, 2nd paragraph.
- `saveInfo1` — `"You can import and export JSON lists into and out of AutoFocus."` — Import/Export modal, above Import/Export buttons.
- `saveInfo2` — `"You can also import a list by pasting in raw text below, and then clicking the 'Submit' button."` — Import/Export modal, above textarea.
- `emptyInputErrMsg1` — `"New items cannot be empty or only whitespace."` — Top-of-page error when "Add Item" pressed with empty input.
- `cannotTakeActionErrMsg1` — `"There are no actionable tasks in your list."` — Error when "Mark Done" pressed on non-actionable list.
- `maxListLengthErrMsg1` — `"Maximum list length reached. Please create a new list to continue adding items."` — Error when list serialization exceeds URL length cap (top-level err and importErrMsg).
- `emptyTextAreaErrMsg1` — `"New items cannot be empty or whitespace only."` — Import modal error when textarea import is empty.
- `badJSONimportErrMsg1` — `"Failed to import tasks. Ensure the JSON file has the correct format."` — Import modal error when JSON parse fails.
- `nonJSONimportAttemptedErrMsg1` — `"Please select a valid JSON file."` — Top-of-page error when non-JSON file uploaded.
- `mismatchDetectedMsg1` — `"The link list and local storage list do not match. Which will you keep?"` — Conflict resolution modal prompt.
- `confirmListDelete` — `"Are you sure you want to delete your list? This action cannot be undone."` — Delete confirmation modal body.
- `clickDiskToClose` — `"Click on the 'disk' icon above to close this window."` — Footer text in Save/Import-Export modal.
- `instructions` — `"Add new items to your list by typing into the input box and clicking 'Add Item'. To prioritize your list, click 'Prioritize'. To mark the next actionable item as complete, click 'Mark Done'. To delete all items from your list, click 'Delete List'."` — Help modal paragraph 1.
- `instructions2` — `"Click the 'disk' icon to see options for list import/export. Click the 'i' icon to learn more about AutoFocus. Click the 'lightbulb' icon to toggle light/dark mode. Click the 'question mark' icon for instructions on how to use this app."` — Help modal paragraph 2.
- `clickQuestionCircleToClose` — `"Click on the 'question mark' icon above to close this window."` — Help modal footer.
- `clickIcircleToClose` — `"Click on the 'i' icon above to close this window."` — About modal footer.
- `invalidQueryParamsErrMsg1` — `"Invalid list query parameters detected. Reverting to local storage list data."` — Top-of-page error when URL `?list=...` is malformed.
- `nothingToDeleteErrMsg1` — `"There is nothing to delete."` — Error when "Delete List" clicked with empty list.
- `exportFailErrMsg1` — `"Failed to export tasks."` — Top-of-page error if `exportTasksToJSON` returns falsy.
- `howToReportIssues` — `"To report any issues/bugs, please leave a ticket on the GitHub repo 'Issues' page here: "` — Help modal, immediately followed by link.

**Inline strings (in JSX)**
- Input placeholder: `"Type new task here"`.
- Textarea placeholder: `"Paste your list here, with each item on a new line"`.
- Add Item button label: `"Add Item"`.
- Delete List button label: `"Delete List"`.
- Prioritize button label: `"Prioritize"`.
- Mark Done button label: `"Mark Done"`.
- Review modal buttons: `"Quit"`, `"No"`, `"Yes"` (tabIndex 0, 1, 2 respectively).
- Delete-confirm modal buttons: `"No"` (cancel), `"Yes"` (confirm).
- Save modal heading: `"Import/Export"` (`<h2>`).
- Save modal buttons: `"Copy List URL"`, `"Import"` (label span), `"Export"`, `"Submit"` (text import).
- About modal heading: `"About AutoFocus"` (`<h2>`).
- About modal version line: `` `Version ${semVer}` `` rendered in an `<h3>`.
- About modal hidden-but-present heading: `"Debug Mode"` (rendered with `o-0 h0` — visually hidden a11y label).
- About modal debug button label: `` `${debugMode ? 'Disable' : 'Enable'} Debug Mode` ``.
- About modal debug status text: `"Debug tools are visible"` / `"Debug tools are hidden"`.
- Help modal heading: `"Instructions & Help"` (`<h2>`).
- Help modal link text: `"AutoFocus Issues"` (href `https://github.com/avidrucker/pwa-autofocus-app/issues`, target `_blank`).
- Conflict modal labels (with `<em>` emphasis): `"1. List from the link address:"`, `"2. List from local storage:"`, `"Copy Link URL"`, `"Copy Local URL"`, `"1. Keep link list"`, `"2. Keep local list"`.
- Debug modal heading: `"PWA Debug Info"`, buttons `"Refresh Debug Info"`, `"Close Debug Info"`, sub-headings `"Service Worker Status:"`, `"Cache Status:"`, `"Offline Status:"`, `"General Info:"`, fallback text `'Click "Refresh Debug Info" to run diagnostics'`.
- Modal-close overlay button text (sr-only-ish, full-area transparent buttons): `"Close Save Modal"`, `"Close Info Modal"`, `"Close Help Modal"`, `"Close Debug Modal"`.
- List-count line (templated): `` `You have ${n} item${plural ? 's' : ''} in your list.` ``.
- Next-actionable line (templated, only when benchmark exists): `` `The next actionable item is '${text}'.` ``.

**Button `title` (tooltip) strings** — non-trivial because they double as a11y labels:
- Header: `"Import/Export"`, `"About"`, `"Help"`, `"Toggle Theme"`, `"PWA Debug Info"`.
- Form buttons: `"add a new item to your list"`, `"delete all tasks from your list"`, `"start a list prioritizing session"`, `"mark the next actionable item as complete"`.
- Review modal: `"quit the prioritization session"`, `"answer no to the question"`, `"answer yes to the question"`.
- Delete modal: `"cancel the delete list action"`, `"confirm the delete list action"`.
- Save modal: `"Copy the current URL to clipboard for sharing"`, `"Upload a JSON file to import tasks"`, `"Export your list to a JSON file"`.
- About modal: `"Disable debug mode"` / `"Enable debug mode"` (toggled).
- Conflict modal: `"Copy the link list URL to clipboard"`, `"Copy the local storage list URL to clipboard"`, `"keep the list from the link"`, `"keep the list from local storage"`.
- Debug modal: `"Run PWA Debugger"`.

### B. Tachyons class strings (by element/region)

**Root `<main>`** — `app h-100 flex flex-column f5 montserrat` + theme color `black` (light) or `white` (dark).

**Header `<header>`** — `app-header pa3 pb2 flex justify-center items-center`.
- `<h1>` (title) — `ma0 f2-ns f3 fw8 tracked-custom dib gray`.
- Each icon-button wrapper `<div>` — first `pl3 inline-flex items-center`, the rest `pl2 inline-flex items-center`.
- Icon `<button>` — `button-reset pa1 w2 h2 pointer f5 fw6 grow bg-transparent bn gray`.

**Main `<section>` (app container)** — `app-container relative flex flex-column h-100`.
- Form `<form>` — `ph3`.
- Input wrapper inner `<div>` — `measure-narrow ml-auto mr-auto`.
- Text input — `todo-input pa2 w-100 input-reset br3 ba bw1 b--gray` + theme suffix `black hover-bg-light-gray active-bg-white` or `white bg-black hover-bg-dark-gray active-bg-black`.
- Error `<p>` — `lh-135 red ml-auto mr-auto measure-narrow ma0 pt2`.
- Button row `<section>` — `pt2 pb2 flex justify-center flex-wrap measure-wide ml-auto mr-auto`.
- Button group `<div>` — `dib`; button cell `<div>` — `ma1 dib`.
- Primary buttons (Add Item / Delete List / Prioritize / Mark Done) — `br3 w4 fw6 ba bw1 b--gray button-reset` + theme suffix `bg-moon-gray black` or `bg-dark-gray white` + `pa2 ph1` + conditional `o-50` (disabled-look) or `pointer grow`.

**Task list container** — outer `<section>` `task-list`; inner `<div>` `ph3`; `<ul>` `ph0 todo-list list ma0 tl measure-narrow ml-auto mr-auto`.

**List footer** — outer `<div>` `ph3 pt2 pb3`; both `<p>` `ma0 o-70 measure-narrow ml-auto mr-auto lh-135`; the next-actionable paragraph adds `line-clamp-3 overflow-hidden`.

**Modal shell pattern** (every modal uses essentially this structure):
- Outer `<section>` — `absolute f5 top-0 w-100 h-100` (or `min-h-100` for some) plus theme bg `bg-white-90` or `bg-black-90`. Some modals add `ph3`.
- Inner content `<section>` — `measure-narrow ml-auto mr-auto` (left-aligned modals also include `tl`, and many add `relative z-1`).
- Modal action row `<div>` — `tc`.
- Modal action button — same recipe as primary buttons but width `w3` (review) or `w4` (delete confirm) or `f6` size and `dib` for some — see exact lines.
- Full-area close overlay button — `absolute z-0 top-0 left-0 w-100 o-0 min-h-100`.

**Save (Import/Export) modal specifics:**
- `<h2>` `pb2 ph3 ma0`.
- Copy URL button container `<div>` `ph3 pb2`; the button itself `br3 w-100 f5 fw6 ba dib bw1 grow b--gray button-reset` + theme `bg-moon-gray black`/`bg-dark-gray white` + `pa2 pointer`.
- Info paragraphs `<p>` `ph3 ma0 lh-135` (and `ph3 pt2 ma0 lh-135` for `saveInfo2`).
- Import file label — `br3 grow dib button-reset border-box w4 f5 fw6 ba bw1 b--gray` + theme suffix + `pa2 pointer ma1`.
- Hidden file `<input id="file-upload">` — `dn input-reset`, `type="file" accept=".json"`.
- Export button — `br3 w4 f5 fw6 ba dib bw1 grow b--gray button-reset` + theme + `pa2 pointer ma1`.
- Import err `<p>` — `ph3 pt2 ma0 lh-135 measure ml-auto mr-auto red`.
- Textarea wrapper `<div>` `ph3 pt1`; textarea classes `db input-reset pa2 w-100 resize-none lh-135 br3 ba bw1 b--gray` + theme suffix; `rows="2"`.
- Submit button `br3 w-100 f5 fw6 ba dib bw1 grow b--gray button-reset` + theme + `pa2 pointer`.
- Footer `<p>` `pt2 ph3 pb3 ma0 lh-135`.

**About modal specifics:**
- `<h2>` `pb2 ma0`; body `<p>` `pb3 ma0 lh-135`; version block `<div>` `pb3`; visible `<h3>` `f5 fw6 ma0 mb2`; hidden a11y `<h3>` `f5 fw6 ma0 o-0 h0`; flex row `<div>` `flex items-center`; toggle button `br3 f6 fw6 ba dib bw1 grow b--gray button-reset` + theme + `pa2 pointer mr2`; status `<span>` `f6 o-70`.

**Help modal specifics:**
- `<h2>` `pb2 ma0`; paragraphs `pb2 ma0 lh-135` and final `pb3 ma0 lh-135`; link `link underline blue hover-orange`.

**Conflict modal specifics:**
- Outer `absolute ph3 f5 top-0 w-100` + theme bg (no `h-100`).
- Inner `measure-narrow ml-auto mr-auto tl`.
- Section labels `<p>` `fw6 ma0 pt2`.
- Copy buttons row `<div>` `tc pt2 pb2`; copy buttons `br3 f6 fw6 ba dib bw1 grow b--gray button-reset` + theme + `pa2 pointer ma1`.
- Bottom action row `<div>` `pb3 tc`; keep-list buttons `br3 f5 fw6 ba dib bw1 grow b--gray button-reset` + theme + `pa2 pointer ma1`.

**Debug modal specifics:**
- Outer `absolute ph3 f5 top-0 w-100 min-h-100 pb3` + theme bg.
- Inner `measure-narrow ml-auto mr-auto tl z-1 relative`.
- Heading column `<div>` `flex flex-column`; `<h2>` `ma0 pb2`; refresh button `br3 f6 fw6 ba dib bw1 grow b--gray button-reset` + theme + `pa2 pointer`.
- Results wrapper `<div>` `mt3`; inner `<div>` `f6 lh-copy`; section `<h3>` `f5 fw6 mt3 mb2`; `<pre>` `pa2 br2 overflow-auto` + theme `bg-light-gray` or `bg-near-black`; empty-state `<p>` `i`.
- Close-row `<div>` `tc mt3`; close button `br3 f5 fw6 ba dib bw1 grow b--gray button-reset` + theme + `pa2 pointer`.

### C. Modals and their structure

There are seven distinct overlays/modals; all but one (prioritization review) are dismissible via a transparent full-area button behind the content. Only one modal can be open at a time — every toggle handler closes the others and clears `errMsg`/`importErrMsg`.

1. **Prioritization review modal** — shown when `isPrioritizing && cursor !== -1 && cursor < tasks.length`. Renders the current question (from `genCurrentQuestion(tasks, cursor)`) and three buttons: Quit / No / Yes (tabIndex 0/1/2). No background-dismiss button — must use Quit.
2. **Delete-list confirmation** — shown when `showingDeleteModal`. Shows `confirmListDelete` text and No/Yes buttons. Toggled by `handleToggleDeleteModal`; "Yes" empties the list, clears errMsg, and dismisses.
3. **Save / Import-Export modal** (`showingSaveModal`) — heading "Import/Export"; Copy List URL button; `saveInfo1`; Import (file-upload label hiding a `<input type="file" accept=".json">`) + Export buttons; per-modal error line if `importErrMsg`; `saveInfo2`; textarea + Submit button for text-import; `clickDiskToClose` footer; transparent background button to close.
4. **About modal** (`showingMoreInfo`) — heading "About AutoFocus"; `infoString1`; `infoString2`; version header `Version 0.1.4`; visually-hidden "Debug Mode" h3; toggle button "Enable/Disable Debug Mode" + status text; `clickIcircleToClose` footer; transparent close.
5. **Help modal** (`showingHelpModal`) — heading "Instructions & Help"; `instructions`; `instructions2`; large block of commented-out keyboard-shortcut markup (NOT shipped, but documents intent); issue-reporting line ending with an external link to GitHub issues; `clickQuestionCircleToClose` footer; transparent close.
6. **Conflict-resolution modal** (`showingConflictModal`) — auto-opens on mount when URL list and localStorage list both exist and differ. Shows `mismatchDetectedMsg1`, then two non-interactive list renderings (offsets 100 and 200) labeled "1. List from the link address:" and "2. List from local storage:", with Copy-link-URL / Copy-local-URL buttons above and "Keep link list" / "Keep local list" choice buttons below. No transparent close — user must choose.
7. **PWA Debug modal** (`showingDebugModal`, only reachable when `debugMode === true`) — heading "PWA Debug Info"; "Refresh Debug Info" button; four `<pre>` blocks (Service Worker / Cache / Offline / General Info) showing `JSON.stringify(...)` of the debug payload; empty-state `'Click "Refresh Debug Info" to run diagnostics'`; "Close Debug Info" button; transparent close.

### D. Conditional rendering and disabled-state logic

- **Header debug button** is only rendered when `debugMode === true`.
- **All header buttons except Toggle Theme** are `disabled={isPrioritizing || showingDeleteModal || showingConflictModal}`. Toggle Theme is always enabled.
- **Task-input, Add Item, Delete List, Prioritize, Mark Done** are all `disabled={isPrioritizing || showingDeleteModal || showingMoreInfo || showingConflictModal || showingSaveModal || showingDebugModal}` — i.e. disabled whenever any modal/review is up.
- **Add Item** also dims to `o-50` while `isPrioritizing`, else `pointer grow`.
- **Delete List** dims to `o-50` when `tasks.length === 0`.
- **Prioritize** dims to `o-50` when `!isPrioritizableList(tasks)`.
- **Mark Done** dims to `o-50` when `!isActionableList(tasks)`.
- **Error paragraph** `<p>` only renders when `errMsg` is truthy.
- **Active task list** only rendered when `tasks.length > 0`; passed `interactive={true}` (per-row cancel/clone buttons).
- **"Next actionable item is …"** paragraph only renders when `benchmarkItem(tasks) !== null`.
- **List-count pluralizes** "item"/"items" via `tasks.length !== 1`.
- **Theme** toggles between `'light'` and `'dark'`. In light mode the root gets text-color `black`, modal bgs `bg-white-90`, primary button `bg-moon-gray black`, input `black hover-bg-light-gray active-bg-white`. In dark mode the inverses (`white`, `bg-black-90`, `bg-dark-gray white`, `white bg-black hover-bg-dark-gray active-bg-black`).
- **Conflict modal** opens automatically on first mount when URL and localStorage lists both non-empty and unequal (via `objectArraysAreEqual`).
- **Review modal** is hidden the moment the cursor resets to `-1` (which also flips `isPrioritizing` off via a useEffect side-effect).
- **Delete List click** with `tasks.length === 0` does NOT open the modal — it sets `errMsg = nothingToDeleteErrMsg1`.
- **Import** of a non-JSON file sets top-level `errMsg`, not `importErrMsg`. Other import errors (bad JSON content, empty textarea, URL too long) set `importErrMsg` inside the modal.

### E. Non-string app behaviors worth knowing for the port

- Hardcoded constants: `semVer = "0.1.4"`, `MAX_URL_LENGTH = 8000`, list ID offsets `activeListOffset = 0`, `queryStringListOffset = 100`, `initialTasksListOffset = 200` (used to dedupe React keys when rendering three lists at once in the conflict modal).
- URL list state is base64-encoded JSON in a `?list=` param; serializing throws `URL_TOO_LONG` if it would exceed `MAX_URL_LENGTH`. The Fulcro port can replicate this contract or punt on URL-shareable lists.
- On `'online'` window event, URL is resynced from `tasks`.
- Many TODOs in source are aspirational (keyboard shortcuts, hover-color refactor, undo, ellipsis on "next actionable" line — already partly implemented via `line-clamp-3 overflow-hidden`); the keyboard-shortcut markup is commented out and not part of the visible help modal.

---

## File 2: `TodoItem.js`

### A. User-facing strings

- Title (tooltip) on the status icon `<span>`: bound to `task.status` (one of `"new"`, `"ready"`, `"done"`, `"cancelled"`).
- Cancel button `title`: `"Cancel Task"`.
- Clone button `title`: `"Clone Task"`.
- The task body itself is `task.text` (rendered verbatim, no escaping logic).
- No other literal strings; icons (`dotCircle`, `emptyCircle`, `filledCircle`, `cancelX`, `repeatArrow`) come from `./core/icons` and are visual-only.

### B. Tachyons class strings

- `<li>` — `flex lh-135 align-start mb1-butlast` + conditional `o-50` when `task.status` is `"done"` or `"cancelled"` + `fw6` when `isBenchmark` else `fw4`.
- Status icon `<span>` — `mr1 dib h-15` (`title={task.status}`).
- Task text `<span>` — `break-word` + conditional `strike` when `task.status === "cancelled"`.
- Action button wrapper `<div>` — `relative ml1 h-15 w3`.
- Cancel/clone `<button>` — `button-reset pa1 hover-button w2 h-15 pointer bg-transparent bn` + theme suffix `moon-gray` (light) or `mid-gray` (dark).

### C. Structure / conditional rendering

- Status-to-icon mapping (`statusToSymbol`): `"done"` → `filledCircle`, `"ready"` → `dotCircle`, `"new"` → `emptyCircle`, `"cancelled"` → `null` (so the renderer falls back to `task.was` — the prior status — to keep the row visually grounded).
- Cancel/clone buttons only render when BOTH `cancelFunc` and `cloneFunc` props are supplied (i.e. only in the interactive active list, not in the conflict-modal previews).
- When both funcs are present: show **Cancel** button if `status` is `"new"` or `"ready"`, otherwise show **Clone** button (i.e. only one of the two per row at a time). This means done/cancelled rows show the repeat-arrow clone affordance; pending rows show the X cancel affordance.
- Light-mode buttons use `moon-gray`, dark-mode uses `mid-gray` — only difference between themes for this component.

---

## Quick port-checklist for the Fulcro/CLJS rewrite

- Mirror the 24 constant strings (lines 18–49 of App.js) into a single `learn.ui.strings` namespace or i18n bundle — they are referenced from many places and several appear in both `errMsg` and `importErrMsg` flows.
- Implement a `modal-shell` component that renders the `absolute top-0 w-100 h-100` overlay + the `bg-white-90`/`bg-black-90` theme tint + an optional full-area transparent close button. Six of seven modals use it; only the review modal omits the close-overlay.
- Mutual-exclusion behavior of modals (open one ⇒ close the others, clear both error strings) is a useful single mutation in the Fulcro port.
- The "disabled when any modal/review is up" pattern affects six different controls — derive a single computed prop (`any-modal-open?`).
- The conflict modal renders the list-renderer three times with three distinct id offsets purely to keep React keys unique; in Fulcro you can either keep separate idents per render or namespace the ids similarly.
- TodoItem's "fallback to `task.was` when status icon is null" is the only place that surfaces the previous status — confirm the data layer keeps `:status/was` on cancelled items.
