# Dev scripts — REPL recipes & dev-time poking

Cheat sheet of "I want to verify/tweak X, what do I run?" recipes for
local dev. Complements `browser_dev.md` (build/serve workflow) and
`CLAUDE.md` (the master test-runner snippet).

---

## Two REPLs, two purposes

| What you want to do | Which REPL | How to connect |
|---|---|---|
| Run the JVM spec suite, exercise pure CLJC code, poke server-side state | JVM nREPL on **7888** | Started by `clojure -M:test:cljs:nrepl`. Connect any nREPL client to `localhost:7888`. |
| Read/write the live Fulcro app state in the browser, fire mutations, dispatch chart events | CLJS REPL inside the running browser tab | `npx shadow-cljs cljs-repl app` from any terminal (talks to the watch on **61946**). |

Both must be running for the full workflow. `npx shadow-cljs watch app`
keeps the build hot AND hosts the CLJS REPL endpoint; the JVM nREPL is
separate.

To exit the CLJS REPL without killing shadow-cljs: type `:cljs/quit`.

---

## Verify a build is good

```clojure
;; In the JVM REPL on :7888. Same snippet as CLAUDE.md.
;; Drops file-seq → require :reload-all → run-tests → totals.
(do
  (require '[clojure.java.io :as io]
           '[clojure.string :as str])
  ;; … see CLAUDE.md for the full block …
  )
```

Current baseline (Phase 12.4): **93 specs / 655 assertions, all green**.

---

## Poke the live browser state from CLJS REPL

After `npx shadow-cljs cljs-repl app`:

```clojure
;; Pretty-print the entire normalized Fulcro DB
(require '[com.fulcrologic.fulcro.application :as app])
(clojure.pprint/pprint (app/current-state @learn.client/SPA))

;; Read a single path
(get-in (app/current-state @learn.client/SPA) [:list/id 1 :ui/theme])

;; Write directly into app state (bypasses mutations — fine for
;; dev pokes; don't use this pattern in production code).
(swap! (:com.fulcrologic.fulcro.application/state-atom @learn.client/SPA)
       assoc-in [:list/id 1 :ui/locale] :es)
```

---

## Phase 12.4: flip the locale without the dropdown

The Settings dropdown lands in 12.5. Until then:

```clojure
;; English (default)
(swap! (:com.fulcrologic.fulcro.application/state-atom @learn.client/SPA)
       assoc-in [:list/id 1 :ui/locale] :en)

;; Spanish
(swap! (:com.fulcrologic.fulcro.application/state-atom @learn.client/SPA)
       assoc-in [:list/id 1 :ui/locale] :es)

;; Japanese
(swap! (:com.fulcrologic.fulcro.application/state-atom @learn.client/SPA)
       assoc-in [:list/id 1 :ui/locale] :ja)
```

The change persists across page reloads — `:ui/locale` is in
`learn.util.storage/ui-prefs-whitelist`, so the storage watch writes
it to `localStorage`.

**To reset to default**: set `:en` (the seed value) or clear the
`autofocus.ui-prefs` localStorage key (see "Clear localStorage" below).

### Same thing via Fulcro Inspect (no REPL)

DB tab is read-only. Use the **Transact** tab instead:

- **Ref**: `[:list/id 1]`
- **Mutation**: `(com.fulcrologic.fulcro.mutations/set-props {:ui/locale :es})`
- Click **Transact**

Swap `:es` for `:ja` / `:en`.

---

## Other dev pokes

### Flip the theme

```clojure
(swap! (:com.fulcrologic.fulcro.application/state-atom @learn.client/SPA)
       update-in [:list/id 1 :ui/theme]
       #(if (= % :theme/dark) :theme/light :theme/dark))
```

Or fire the proper mutation (matches what the lightbulb icon does):

```clojure
(require '[com.fulcrologic.fulcro.components :as comp])
(comp/transact! @learn.client/SPA [(learn.client/toggle-theme)])
```

### Open / close a modal

```clojure
;; Open the Settings modal
(comp/transact! @learn.client/SPA
  [(learn.client/set-open-modal {:ui/open-modal :settings})])

;; Close any modal
(comp/transact! @learn.client/SPA
  [(learn.client/set-open-modal {:ui/open-modal :none})])
```

Modal IDs: `:none`, `:info`, `:settings`, `:save`, `:delete-confirm`, `:conflict`.

### Drive the review chart manually

```clojure
(require '[com.fulcrologic.statecharts.integration.fulcro :as scf])

;; Start a session
(scf/send! @learn.client/SPA :review-session :event.review/start)
(scf/process-events! @learn.client/SPA)

;; Answer yes / no / quit
(scf/send! @learn.client/SPA :review-session :event.review/yes)
(scf/process-events! @learn.client/SPA)

;; Inspect chart state
(scf/current-configuration @learn.client/SPA :review-session)
```

### Inspect server-side state

The Pathom "server" runs in-process. Its store is just an atom:

```clojure
(require '[learn.server :as server])
@server/SERVER-DB

;; Seed with the canonical demo data
(server/seed!)

;; Wipe to empty (different from seed — no demo todos)
(reset! server/SERVER-DB server/empty-state)
```

---

## Clear localStorage (force a fresh first-load)

In the browser dev console:

```js
localStorage.removeItem('autofocus.server-db');   // todos + list state
localStorage.removeItem('autofocus.ui-prefs');    // theme + locale
location.reload();
```

Or all-keys nuke:

```js
localStorage.clear();
location.reload();
```

---

## Service worker — reset / unregister

If hot-reload starts feeling stale, or you've toggled the localhost
bypass in `sw.js` and want to confirm: DevTools → **Application** →
**Service Workers** → **Unregister** → reload.

The new SW (with the localhost bypass for `/js/main/*`) takes effect
immediately on the next page load. See `sw.js` comments for the
rationale.

---

## Cross-reference

- **`browser_dev.md`** — building, serving, snapshots, troubleshooting
- **`CLAUDE.md`** — master test-runner script + project hard rules
- **`docs/SCHEMA.md`** — what each `:ui/*` and `:todo/*` key means
- **`docs/learned_while_making_this.md`** — past mistakes worth not repeating
