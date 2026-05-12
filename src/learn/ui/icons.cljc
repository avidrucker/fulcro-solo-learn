(ns learn.ui.icons
  "Inline SVG icons used by the AutoFocus UI.

   Ported verbatim (paths + viewBoxes) from the JS source's
   `src/core/icons.js`. Each icon is a `def`'d React element ready to
   drop into a `defsc` body. The icons use `fill='currentColor'` so the
   surrounding `color` CSS class controls their color — pair with
   Tachyons color utilities (e.g. `moon-gray`, `gray`) at the call site.

   This namespace currently exposes only the five icons referenced by
   `TodoItem` (status mapping + action buttons). The JS source has six
   more (info circle, question circle, save disk, lightbulb solid /
   regular, wrench) used by the header / theme toggle / modals — those
   land in later phases as their consuming UI is added."
  (:require
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])))

(def svg-attrs
  "Shared SVG root attributes — every icon is a circle/glyph that
   inherits color from the surrounding text."
  {:xmlns "http://www.w3.org/2000/svg"
   :fill  "currentColor"})

;; ----------------------------------------------------------------------
;; Status icons (TodoItem's `statusToSymbol`)
;; ----------------------------------------------------------------------

;; `dot-circle` — ring with a filled inner dot. Status `:status/ready`.
(def dot-circle
  (dom/svg (merge svg-attrs {:viewBox "0 0 512 512" :width "1.25rem"})
    (dom/path {:d (str "M256 56c110.532 0 200 89.451 200 200 0 110.532-89.451 200-200 200-110.532 "
                       "0-200-89.451-200-200 0-110.532 89.451-200 200-200m0-48C119.033 8 8 119.033 "
                       "8 256s111.033 248 248 248 248-111.033 248-248S392.967 8 256 8zm0 168c-44.183 "
                       "0-80 35.817-80 80s35.817 80 80 80 80-35.817 80-80-35.817-80-80-80z")})))

;; `empty-circle` — hollow ring. Status `:status/new`.
(def empty-circle
  (dom/svg (merge svg-attrs {:viewBox "0 0 512 512" :width "1.25rem"})
    (dom/path {:d (str "M256 8C119 8 8 119 8 256s111 248 248 248 248-111 248-248S393 8 256 8zm0 "
                       "448c-110.5 0-200-89.5-200-200S145.5 56 256 56s200 89.5 200 200-89.5 200-200 200z")})))

;; `filled-circle` — fully filled disc. Status `:status/done`.
(def filled-circle
  (dom/svg (merge svg-attrs {:viewBox "0 0 512 512" :width "1.25rem"})
    (dom/path {:d "M256 8C119 8 8 119 8 256s111 248 248 248 248-111 248-248S393 8 256 8z"})))

;; ----------------------------------------------------------------------
;; Action-button icons (per-row Cancel / Clone)
;; ----------------------------------------------------------------------

;; `cancel-x` — thick X / close glyph. Shown when the row's status is
;; `:status/new` or `:status/ready`.
(def cancel-x
  (dom/svg (merge svg-attrs {:viewBox "0 0 352 512" :height "1rem"})
    (dom/path {:d (str "M242.72 256l100.07-100.07c12.28-12.28 12.28-32.19 0-44.48l-22.24-22.24c-12.28-12.28"
                       "-32.19-12.28-44.48 0L176 189.28 75.93 89.21c-12.28-12.28-32.19-12.28-44.48 0L9.21 "
                       "111.45c-12.28 12.28-12.28 32.19 0 44.48L109.28 256 9.21 356.07c-12.28 12.28-12.28 "
                       "32.19 0 44.48l22.24 22.24c12.28 12.28 32.2 12.28 44.48 0L176 322.72l100.07 100.07c12.28 "
                       "12.28 32.2 12.28 44.48 0l22.24-22.24c12.28-12.28 12.28-32.19 0-44.48L242.72 256z")})))

;; `repeat-arrow` — circular rotate / clone glyph. Shown when the row's
;; status is `:status/done` or `:status/cancelled`.
(def repeat-arrow
  (dom/svg (merge svg-attrs {:viewBox "0 0 512 512" :height "1rem"})
    (dom/path {:d (str "M256.455 8c66.269.119 126.437 26.233 170.859 68.685l35.715-35.715C478.149 25.851 504 "
                       "36.559 504 57.941V192c0 13.255-10.745 24-24 24H345.941c-21.382 0-32.09-25.851"
                       "-16.971-40.971l41.75-41.75c-30.864-28.899-70.801-44.907-113.23-45.273-92.398-.798"
                       "-170.283 73.977-169.484 169.442C88.764 348.009 162.184 424 256 424c41.127 0 79.997"
                       "-14.678 110.629-41.556 4.743-4.161 11.906-3.908 16.368.553l39.662 39.662c4.872 4.872 "
                       "4.631 12.815-.482 17.433C378.202 479.813 319.926 504 256 504 119.034 504 8.001 "
                       "392.967 8 256.002 7.999 119.193 119.646 7.755 256.455 8z")})))

;; ----------------------------------------------------------------------
;; Status → icon
;; ----------------------------------------------------------------------

(defn status-icon
  "Returns the SVG icon element for a given status, or `nil` for
   `:status/cancelled` (and unknown statuses). Callers should fall back
   to the item's `:todo/was` for cancelled rows — matches the JS port's
   `statusToSymbol(task.was)` recursion."
  [status]
  (case status
    :status/new   empty-circle
    :status/ready dot-circle
    :status/done  filled-circle
    nil))
