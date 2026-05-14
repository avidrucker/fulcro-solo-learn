(ns learn.client.ui.theme
  "Phase 12.7 — Tachyons class strings + theme-aware helpers extracted
   from `learn.client` (Phase 6.5.3 / 7.7 origin).

   Pure functions: theme keyword → class string. No DOM, no Fulcro
   component references, no mutations. Both `learn.client.ui.modals`
   and `learn.client.ui.components` (loaded later) require this
   namespace so its helpers are the single source of truth for the
   light/dark Tachyons class suffixes.

   Sourced verbatim from `docs/js_ui_reference.md` §B. The
   `theme-*-class` helpers return the JS port's light/dark suffix
   pair for each themed element. `:theme/light` is the default and
   what callers see if `:ui/theme` is missing.")

(defn dark? [theme] (= theme :theme/dark))

(defn theme-text-class
  "Foreground text color class for the page root."
  [theme] (if (dark? theme) "white" "black"))

(defn theme-page-bg-class
  "Page background class for `<main>` — `bg-black` in dark mode so
   the white-text content is readable AND the shade matches the JS
   port's `<body class=\"bg-black\">` exactly (#000 rather than #111).
   Light mode is the document's default (no class needed). The JS port
   sets the class on `<body>`; we apply it on `<main>` because that's
   the highest level our React component owns — paired with the
   html/body/#app flex-column reset in app.css so `<main>` fills the
   viewport."
  [theme] (if (dark? theme) "bg-black" ""))

(defn theme-modal-bg-class
  "Modal overlay tint."
  [theme] (if (dark? theme) "bg-black-90" "bg-white-90"))

(defn theme-input-class
  "Theme-suffix for the page-level new-todo input. Verbatim port of
   the JS source: `hover-bg-*-gray` fades the bg to transparent on
   hover/focus so the page background shows through (the JS port's
   button-style affordance applied to the input). Matches the OG's
   visual exactly.

   For inputs INSIDE modals, use `theme-modal-input-class` instead —
   modals already provide a translucent overlay, so doubling up the
   transparency washes out the field."
  [theme]
  (if (dark? theme)
    "white bg-black hover-bg-dark-gray active-bg-black"
    "black hover-bg-light-gray active-bg-white"))

(defn theme-modal-input-class
  "Theme-suffix for inputs/textareas/selects rendered INSIDE a modal.
   Default state matches the surrounding primary-button bg (dark-gray
   in dark mode, moon-gray in light) so the field reads as a clear
   tier of UI chrome at rest. Hover and focus snap to solid
   black/white respectively — the JS port's `hover-bg-*-gray` rules
   (app.css) fade to transparent, which inside a translucent modal
   washes the field out. Tachyons' `hover-bg-black` / `hover-bg-white`
   are NOT overridden in app.css so they stay solid, and the
   `focus-bg-*` rules (app.css) cover the focus state."
  [theme]
  (if (dark? theme)
    "white bg-dark-gray hover-bg-black focus-bg-black"
    "black bg-moon-gray hover-bg-white focus-bg-white"))

(defn theme-primary-btn-suffix
  "Theme-suffix for primary `<button>` text + bg (Add Item, Delete
   List, Prioritize, Mark Done, modal action buttons)."
  [theme]
  (if (dark? theme) "bg-dark-gray white" "bg-moon-gray black"))

(defn theme-icon-btn-color
  "Theme-suffix for per-row Cancel/Clone icon buttons."
  [theme]
  (if (dark? theme) "mid-gray" "moon-gray"))

(defn btn-icon-class
  "Cancel / clone icon buttons on each todo row. `hover-button` (custom
   class in `app.css`) hides the button until the row is hovered on
   pointer-capable devices and stays visible on touch."
  [theme]
  (str "button-reset pa1 hover-button w2 h-15 pointer bg-transparent bn "
       (theme-icon-btn-color theme)))

;; ----------------------------------------------------------------------
;; Main list / form / button row class strings.
;; ----------------------------------------------------------------------

(defn btn-primary-class
  "Theme-aware primary button class string (Add Item, Delete List,
   Prioritize, Mark Done)."
  [theme]
  (str "br3 w4 fw6 ba bw1 b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 ph1 pointer grow"))

(defn btn-primary-dim-class
  "Disabled/dimmed variant — same theme suffix, no `pointer grow`,
   `o-50` opacity for the visual."
  [theme]
  (str "br3 w4 fw6 ba bw1 b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 ph1 o-50"))

(defn input-class
  "Theme-aware new-todo input class string."
  [theme]
  (str "todo-input pa2 w-100 input-reset br3 ba bw1 b--gray "
       (theme-input-class theme)))

(defn review-btn-class
  "Theme-aware review modal action button class (Quit/No/Yes)."
  [theme]
  (str "br3 w3 fw6 ba bw1 b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 pointer grow ma1 dib"))

(defn delete-confirm-btn-class
  "Theme-aware Yes/No button class for the delete-confirm modal. Same
   recipe as `review-btn-class` but `w4` instead of `w3` — the JS port
   uses a wider button here (`docs/js_ui_reference.md` line 99)."
  [theme]
  (str "br3 w4 fw6 ba bw1 b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 pointer grow ma1 dib"))

;; ----------------------------------------------------------------------
;; Header icon button classes (Phase 7.8) — pl3/pl2 spacing wrapper +
;; fixed 2rem × 2rem button.
;; ----------------------------------------------------------------------

(def header-icon-btn-class
  "button-reset pa1 w2 h2 pointer f5 fw6 grow bg-transparent bn gray")

(defn header-icon-wrapper-class
  "Outer-div padding class — `pl3` for the leftmost icon, `pl2` for
   the rest (matches the JS port's spacing)."
  [{:keys [first?]}]
  (str (if first? "pl3" "pl2") " inline-flex items-center"))

;; ----------------------------------------------------------------------
;; Save modal button classes (Phase 7.6).
;; ----------------------------------------------------------------------

(defn save-modal-btn-class
  "Theme-aware Import/Export modal action-button class string."
  [theme]
  (str "br3 w4 f5 fw6 ba dib bw1 grow b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 pointer ma1"))

(defn save-modal-wide-btn-class
  "Full-width variant — Copy URL + Submit."
  [theme]
  (str "br3 w-100 f5 fw6 ba dib bw1 grow b--gray button-reset "
       (theme-primary-btn-suffix theme)
       " pa2 pointer"))
