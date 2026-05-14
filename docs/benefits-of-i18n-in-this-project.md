# i18n in this project — why hand-rolled, not `fulcro-i18n`

Phase 12.4 added i18n (English / Spanish / Japanese) to the Fulcro
port. The phase scope originally called for `com.fulcrologic/fulcro-i18n`;
on analysis a small hand-rolled lookup was the right shape for this
project. This doc is the honest write-up — what `fulcro-i18n` does,
where it would have paid off, why it didn't here, and the design we
went with instead.

Companion to [`benefits-of-RAD-in-this-project.md`](./benefits-of-RAD-in-this-project.md)
and [`when-to-statechart.md`](./when-to-statechart.md) — same exercise
for two other powerful tools at our scale. The recurring lesson: a
library earns its keep when it removes work your project would
otherwise have to do; below that bar it's framework ceremony.

---

## What `fulcro-i18n` provides

- **Message extraction.** A build-time scan of `(tr {:msgctxt "btn" :msgid "Add Item"})` (or `trf` for templated) calls produces a `.pot` file you hand to translators.
- **Translation file pipeline.** Round-trip through gettext-style `.po`/`.pot`/`.mo` so a translator can work in their own tooling. The compiled `.mo` ships as a CLJS resource the runtime can hydrate.
- **ICU MessageFormat support.** Plural / select / number / date formatting via `formatjs`. Spelling pluralisation rules per locale ("1 item" vs "2 items" in English; complex agreement in Polish; no plural form in Japanese) is the load-bearing feature.
- **Live language switch.** A `change-locale` mutation that pulls the matching translation bundle from a remote and hot-swaps it in the running app.
- **Fallback chain hooks.** Customise how missing keys fall back through locales.
- **A `tr` macro** that records the source string at compile time AND looks up the translation at runtime.

Pricing: the moving parts are the build-time `.pot` extractor, the round-trip with `.po` editors, the `IntlMessageFormat` runtime, and the `change-locale` mutation infrastructure with its bundle-fetching pipeline.

---

## Where `fulcro-i18n` pays off

- **You translate strings hundreds at a time.** With a `.po` editor, a 500-string update is a single workflow; with manual maps it's a chore.
- **Translators are not engineers.** `.po` files are tool-supported (Poedit, web platforms like Crowdin, etc.). Translators never touch the codebase.
- **Multi-team rollout.** Engineering ships a `.pot`, translation team works in parallel on `.po`, ops ships the bundle. Decouples the two work streams.
- **Real pluralisation across many locales.** Polish has 3 forms, Arabic has 6, English has 2 — the ICU `{count, plural, one {…} other {…}}` syntax pays for itself once you have 5+ locales with non-English plural rules.
- **Strings change frequently.** The compile-time extractor catches new strings automatically; no risk of "forgot to add the key to the translation map".
- **Lazy-loaded translation bundles.** If you have 30 locales and ship to global users, hot-loading only the active locale's bundle saves real bytes.

---

## Why it didn't fit here

- **Scale: ~30 keys.** The full curated translation surface (4 page buttons, 3 review buttons, 4 header tooltips, 3 modal headings, 12 modal-body strings, 2 parameterised lines, the language label) fits comfortably in a single `def translations` map. There's no `.po`-file payoff at this size — opening a `.po` editor to change "Add Item" → "Añadir Tarea" is more friction than editing the map.
- **Solo developer.** No translator team to decouple from. I'm the one entering the Spanish and the Japanese; a `.po` round-trip adds latency without saving work.
- **Three locales, simple pluralisation.** English needs `1 item / N items`, Spanish needs `1 tarea / N tareas`, Japanese has no plural form at all. Two `case`-statement fns (`tr-list-count`, `tr-next-actionable`) cover this in 8 lines. ICU's `{count, plural, …}` syntax handles the 47-locale edge cases I don't have.
- **No live-translation hot-swap need.** A locale change is a `swap!` on app state plus a re-render. `learn.client/set-locale` does exactly that in five lines. No remote, no bundle load, no pipeline.
- **Compile-time extraction is anti-value here.** With the map literally in the code, "where is this string defined?" is a `grep '<key>'` away. The macro-based extraction is solving a problem (finding all strings) I don't have.
- **Avoid a new build step.** `fulcro-i18n`'s `.pot` extraction wants to live in the build; introducing it for 30 keys is overhead the project doesn't earn back.

The OG ReactJS port doesn't have i18n at all. Adding it as a Fulcro-learning exercise is the load-bearing payoff of this phase — the goal is to understand Fulcro app state + persistence + UI threading for a cross-cutting concern, not to roll out 47 locales.

---

## What we built instead

A single 130-line namespace, [`learn.i18n.core`](../src/learn/i18n/core.cljc):

- **`translations`** — `:en` / `:es` / `:ja` → namespaced-keyword → string. `:en` is the canonical source; other locales fall back to it for missing keys. Keys are namespaced (`:btn/add-item`, `:info/about-1`, `:settings/click-gear`) so call-site searches are trivially greppable.
- **`tr [locale key]`** — the lookup. Three-step fallback: requested locale → `:en` → keyword name as string (so an undefined key shows visibly in the UI instead of silently rendering nil). 8 lines, no macro.
- **`tr-list-count [locale n]`** / **`tr-next-actionable [locale text]`** — the two parameterised lines. `case` on locale; English and Spanish handle their own pluralisation; Japanese skips the plural form entirely. ~10 lines each.
- **`supported-locales`** — the set driving the Settings dropdown.
- **`locale-label [locale]`** — `:en → "English"`, `:es → "Español"`, `:ja → "日本語"`. Each language's own script (not romanised) so the dropdown is readable by speakers of each.
- **`default-locale`** — `:en`. The seed value for `:ui/locale`.

The runtime side:
- `:ui/locale` lives on `[:list/id 1]`, alongside `:ui/theme`. The `set-locale` mutation is client-only — no remote, no Pathom involvement (the locale never leaves the browser).
- Persistence piggybacks on `learn.util.storage/ui-prefs-whitelist` — same watch, separate localStorage key (`autofocus.ui-prefs`), so the choice survives reloads.
- The Settings modal's `<select>` is plain HTML: `i18n/supported-locales` for options, `i18n/locale-label` for display text, `onChange` dispatches `(set-locale {:ui/locale <keyword>})`.

That's the entire i18n surface. 4 specs / 21 assertions of unit tests on the lookup helper, plus integration assertions in `learn.client-test` confirming the locale propagates through TodoList → Root → modal bodies.

---

## When to revisit

If any of these become true, `fulcro-i18n` (or any heavier solution) starts to earn its keep:

- **5+ locales with non-English plural rules.** Arabic, Polish, Russian, Czech, Welsh — locales where ICU's plural categories save real code over per-locale `case` branches.
- **Non-engineer translators.** A bilingual designer or volunteer community translates content — the `.po` workflow becomes a real productivity multiplier.
- **100+ keys.** When the canonical map gets large enough that "what strings have we added since last release?" stops being a `git diff` away, message extraction pays off.
- **Live-loaded bundles.** Tree-shaking out 29 unused locales' translation strings saves bytes.
- **Date / number / currency formatting at scale.** ICU's `{date, …}` / `{number, currency, …}` syntax is the gold standard once you need it.

Until then: the hand-rolled `learn.i18n.core` map + `tr` lookup is the right tier of tool for the job, and the work it doesn't do is work the project doesn't need.

---

## Cross-references

- Decision-criteria companions: [`benefits-of-RAD-in-this-project.md`](./benefits-of-RAD-in-this-project.md), [`when-to-statechart.md`](./when-to-statechart.md), [`when-to-use-RAD-forms-and-reports.md`](./when-to-use-RAD-forms-and-reports.md), [`when-to-use-pathom-prod-patterns.md`](./when-to-use-pathom-prod-patterns.md).
- Phase log: [`phases.md`](./phases.md) Phase 12.
- The namespace: [`src/learn/i18n/core.cljc`](../src/learn/i18n/core.cljc).
- The dev cheat sheet: [`dev_scripts.md`](./dev_scripts.md) for how to flip locale from the CLJS REPL before 12.5's dropdown.
