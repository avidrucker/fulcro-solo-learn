# Browser dev — shadow-cljs workflow

How to build, run, and inspect the AutoFocus front-end in a real browser.
Phase 6 onward.

> Phase 6.1 ships only a trivial console-log entrypoint. Real UI mount
> lands in **Phase 6.3**. The commands below work today; what's visible
> on the page evolves per phase.

---

## One-time setup

JavaScript dependencies live under `node_modules/` (gitignored) and are
declared in `package.json`. Install them once after cloning, and again
any time `package.json` changes:

```bash
npm install
```

You need Node.js installed (current dev was on `v25.2.1`). On Windows,
[nvm-windows] is a clean way to manage Node versions.

[nvm-windows]: https://github.com/coreybutler/nvm-windows

---

## Dev loop (hot reload)

```bash
npx shadow-cljs watch app
```

This does three things at once:

1. Compiles the `:app` build defined in `shadow-cljs.edn`.
2. Starts a static file server on **http://localhost:8000** serving
   `resources/public/`.
3. Watches `src/` for changes — any save triggers an incremental
   recompile and pushes the new code into a connected browser (no page
   reload needed for most changes).

Open **http://localhost:8000** in your browser. The page shows
`Loading…` (from `index.html`) and the bundle runs `learn.main/init`.

**To verify the bundle ran:** open the browser's DevTools console
(F12 or Cmd-Option-I) and look for:

```
AutoFocus front-end starting (Phase 6.1)
```

If you don't see that line, the bundle didn't load — common causes:

- `npm install` wasn't run, so `node_modules/shadow-cljs` is missing.
- Port 8000 is occupied by another process.
- Browser cache is serving an old bundle. Hard reload (Ctrl-Shift-R /
  Cmd-Shift-R) clears it.

To stop the dev server, press `Ctrl-C` in the terminal.

---

## One-shot compile (no watch, no server)

Useful for "does it still build?" checks without taking over a terminal:

```bash
npx shadow-cljs compile app
```

Output goes to `resources/public/js/main/` (gitignored). The page at
`http://localhost:8000` won't work without the watch dev server running —
or you can serve `resources/public/` with any static file server.

---

## Production build

Closure Compiler advanced optimizations, dead-code elimination,
minification:

```bash
npx shadow-cljs release app
```

Result is a single small `main.js`. The `:devtools` block in
`shadow-cljs.edn` is ignored — release builds don't include the
hot-reload runtime.

---

## Browser REPL (cljs-repl)

shadow-cljs exposes a ClojureScript REPL that runs **inside the
connected browser**. With `watch app` running and the browser tab open:

```bash
npx shadow-cljs cljs-repl app
```

Type forms; they evaluate in the browser. Useful for poking at app
state, calling functions, inspecting the Fulcro app instance live.

The `learn.client/SPA` atom holds the live app once `init` runs (from
Phase 6.3 onward). Quick recipes:

```clojure
;; Print the current Fulcro state from the browser REPL:
(require '[com.fulcrologic.fulcro.application :as app])
(clojure.pprint/pprint (app/current-state @learn.client/SPA))

;; Send a chart event:
(require '[com.fulcrologic.statecharts.integration.fulcro :as scf])
(scf/send! @learn.client/SPA :review-session :event.review/start)
(scf/process-events! @learn.client/SPA)
```

---

## File map (for orientation)

```
shadow-cljs.edn               # build config (target :browser, init-fn)
package.json                  # shadow-cljs + react + react-dom
resources/public/index.html   # entrypoint HTML, mounts <script>
resources/public/js/main/     # build output (gitignored)
src/learn/main.cljs           # ^:export init — what the bundle runs
src/learn/*.cljc              # shared with JVM (server, parser, etc.)
```

---

## Troubleshooting cheat sheet

| Symptom | Likely cause | Fix |
|---|---|---|
| `command not found: shadow-cljs` | Missing local install | `npm install` |
| `Address already in use :8000` | Another process on 8000 | Kill it, or set a different `:http-port` in `shadow-cljs.edn` |
| Page is blank, no console log | Bundle didn't load — check Network tab for 404 on `/js/main/main.js` | Recompile; verify `resources/public/js/main/main.js` exists |
| `provide conflict for #{goog.reflect}` warning | Closure Library quirk, harmless | Ignore |
| `sun.misc.Unsafe` deprecation warnings | From transitive Guava/Protobuf | Ignore |
| Hot-reload stopped working mid-session | Connection severed (laptop slept, etc.) | Refresh the browser tab; the watch should reconnect |
