# fulcro-solo-learn

Hands-on Fulcro / ClojureScript port of the AutoFocus productivity
model — built TDD-first across many small phases, with a focus on
understanding each layer of the stack as it lands.

**Status:** v0.0.1 · 104 specs / 728 assertions · Phase 13 closed.

## Live demo

The deployed Fulcro port: <https://avidrucker.github.io/fulcro-solo-learn/>

The original JS port (source of truth for behaviour comparisons):
<https://github.com/avidrucker/pwa-autofocus-app> · <https://avidrucker.github.io/pwa-autofocus-app/>

## What this is (and isn't)

A learning project, not a replacement for the OG. The goal is to
internalise Fulcro / Pathom / statecharts / shadow-cljs / Tachyons by
porting a real app (Mark Forster's AutoFocus system) one feature at a
time, with red-green-refactor TDD as the development discipline.

Each phase has its own retrospective entry in `docs/phases.md`; each
intentional divergence from the JS port is logged in `docs/changes.md`;
each defect we surfaced and fixed has a `B-N` entry in `docs/bugs.md`.
Decision write-ups for the "should we adopt X?" calls live alongside as
`benefits-of-X.md` / `when-to-X.md` docs.

## Tech stack

| Layer | Choice |
|---|---|
| UI / state | [Fulcro 3.9](https://github.com/fulcrologic/fulcro) |
| Schemas / contracts | [Malli](https://github.com/metosin/malli) + [Guardrails](https://github.com/fulcrologic/guardrails) (`>defn`) |
| Server-side resolvers | [Pathom 2](https://github.com/wilkerlucio/pathom) (in-process, no real backend) |
| State machines | [com.fulcrologic/statecharts](https://github.com/fulcrologic/statecharts) (review/prioritize flow) |
| Attribute-driven UI | [Fulcro RAD](https://github.com/fulcrologic/fulcro-rad) (1.6.23 — `defattr` for the new-todo input) |
| Browser build | [shadow-cljs](https://github.com/thheller/shadow-cljs) |
| CSS | [Tachyons](https://tachyons.io/) + a small `app.css` |
| Tests | [fulcro-spec](https://github.com/fulcrologic/fulcro-spec) (CLJC, JVM-headless) |
| i18n | Hand-rolled lookup (`learn.i18n.core` — see `docs/benefits-of-i18n-in-this-project.md`) |
| PWA | Manifest + service worker for offline shell |

## Quick start

```bash
# 1. JS deps
npm install

# 2. Start the dev build (shadow-cljs watch + static server on :8000)
npx shadow-cljs watch app

# 3. Open the app
#    http://localhost:8000
```

To run the JVM spec suite, start a Clojure REPL with the test alias:

```bash
clojure -M:test:cljs:nrepl
```

Then evaluate the master test-runner snippet from `CLAUDE.md` (`do
(require ...) (run-tests ...)`). Or invoke per-namespace tests directly
from any nREPL client connected to the printed port.

For browser-side REPL access (poking live app state, firing mutations,
flipping locale), see `docs/dev_scripts.md`.

## Where to look next

Start with `docs/README.md` — that's the docs index, lens-per-doc, no
duplication. From there:

- **First time?** `docs/SCHEMA.md` for the domain model; `docs/phases.md`
  for the dev arc; `docs/user_stories.md` for what the app does today.
- **Contributing?** `CLAUDE.md` at root has the hard rules (TDD-first,
  stub-then-implement, master-test-runner-after-every-change). The
  per-doc lenses in `docs/README.md` tell you which file gets updated
  for which kind of change.
- **Curious about a specific design decision?** The
  `benefits-of-*.md` / `when-to-*.md` docs in `docs/` capture the
  "why we did / didn't reach for X" for RAD, statecharts, i18n,
  Pathom production patterns, and RAD forms/reports.

## Project layout

```
src/learn/
  client.cljc                 # thin facade — re-exports + init + snapshot
  client/                     # split sub-namespaces (Phase 12.7)
    session.cljc              #   cross-ns constants
    state.cljc                #   pure state-map helpers
    mutations.cljc            #   Fulcro defmutations
    lifecycle.cljc            #   SPA atom, chart bootstrap, body-theme sync
    ui/
      theme.cljc              #   Tachyons class strings + theme helpers
      modals.cljc             #   modal-shell + body fns + header icon button
      components.cljc         #   TodoItem / TodoList / Root
  model/                      # pure domain (Malli + >defn)
    schema.cljc               #   :status/* enum, item/list shapes
    list.cljc                 #   add / cancel / clone / benchmark logic
    review.cljc               #   prioritization predicates + question template
  rad/
    attributes.cljc           #   defattr declarations (Phase 9)
    input.cljc                #   attribute-driven text-input helper
  review/
    chart.cljc                #   statechart for the review flow
  i18n/
    core.cljc                 #   translation map + `tr` lookup
  ui/
    icons.cljc                #   Font Awesome SVG paths
    strings.cljc              #   centralized English source
  util/
    normalized.cljc           #   denormalize / sync-items helpers
    remote.cljc               #   CLJC sync-remote shim for the loopback parser
    storage.cljc              #   localStorage adapter + persistence watch
    tasks-io.cljc             #   JSON import parser (Phase 13)
    url-encoding.cljc         #   ?list=<base64-JSON> encoder + decoder
    version-macros.clj        #   compile-time package.json version reader
  resolvers.cljc              # Pathom resolvers + mutation handlers
  parser.clj                  # Pathom parser construction
  server.cljc                 # in-process SERVER-DB atom + seed
  main.cljs                   # CLJS entrypoint (calls learn.client/init)

test/learn/                   # mirrors src layout; CLJC fulcro-spec
docs/                         # everything else — see docs/README.md
resources/public/             # index.html, app.css, manifest, sw.js, icons
scripts/                      # Playwright snapshot tools, paren-balance check
shadow-cljs.edn               # CLJS build config
deps.edn                      # JVM deps + :test / :cljs / :nrepl aliases
package.json                  # JS deps + version (single source of truth)
CLAUDE.md                     # agent-instructions / hard rules
```

## License

No license declared; this is a private learning project. Don't reuse
code from here in production without checking with the author first.
