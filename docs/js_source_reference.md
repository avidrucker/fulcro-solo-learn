# JS Source Reference

Captured from [`pwa-autofocus-app`](https://github.com/avidrucker/pwa-autofocus-app/tree/main/src/core) at the time of writing — signatures, behavior summaries, and known quirks for every domain function in the original JavaScript implementation.

Purpose: so future phases of the Clojure port don't need to re-read the JS source. When this port diverges from the JS source, the divergence is noted inline.

---

## `taskUtils.js`

```js
hasNew(tasks)   // → boolean. True iff any task has status === "new".
hasReady(tasks) // → boolean. True iff any task has status === "ready".
```

Trivially the same as the Clojure `defn- new?` / `ready?` predicates in `learn.model.list`, except the JS versions operate on the *list* (any-of) and the Clojure ones operate on a *single todo*. Both forms are needed.

---

## `tasksManager.js`

### `nextId(tasks)`
Returns `0` if empty, else `max(...ids) + 1`. **Not used in the Clojure port** — UUIDs replace integer ids (see SCHEMA.md §2).

### `benchmarkItem(tasks)` → task | null
Last ready task in list order, or null. **Matches Clojure `model.list/benchmark-item`.**

### `isAutoMarkableList(tasks)` → boolean *(unexported)*
`hasNew(tasks) && !hasReady(tasks)`. **Matches Clojure `auto-markable?`.**

### `automark(tasks)` → tasks *(unexported)*
Promotes first new → ready if list is auto-markable; else returns tasks unchanged.

**JS bug (JS-discrepancy #5, fixed in Clojure port):** `if(!isAutoMarkableList)` — reads the function reference instead of *calling* it. So the early-return is dead code; `automark` runs its promotion path unconditionally. The Clojure port calls `(auto-markable? items)` correctly.

### `isActionableList(tasks)` → boolean
`hasReady(tasks)`. **Not yet ported** — used by `completeBenchmarkTask`; the Clojure port inlines the check.

### `completeBenchmarkTask(tasks)` → tasks
If list isn't actionable, returns tasks unchanged (silent no-op). Otherwise marks the last ready as done, then calls `automark` if the result is auto-markable.

**Clojure port divergence (Phase 5J.2):** silent no-op replaced with explicit `{:ok? false :error/type :error/no-actionable-items}`. Result-shaped return shape replaces raw tasks. Same auto-mark behavior.

### `addTask(tasks, text)` → tasks
Appends `{id: nextId, text, status: hasReady ? "new" : "ready"}`. Always succeeds; no blank-text check.

**Clojure port divergence (Phase 5I.4):** blank text returns `{:ok? false :error/type :error/blank-item}`. UUID id instead of integer. Result-shaped return shape.

### `emptyList()` → `[]`
Trivial. Not yet ported.

### `addAll(initialTasks, newTasks)` → tasks
Reassigns ids to imported tasks (max id + 1, +2, ...) and concatenates. Used for import/merge flows.

**Not yet ported.** Will need UUID-equivalent id-rewriting strategy (probably: import comes in with fresh UUIDs, no rewrite needed).

### `cancelItem(tasks, id)` → tasks
Maps over tasks; for the matching id, sets `{status: "cancelled", was: prior_status}`. Then calls `automark` if auto-markable.

**JS quirks** (all addressed in Phase 5J.1):
- No id-existence check — missing id is a silent no-op.
- No status check — cancelling a `done` or `cancelled` item silently overwrites; double-cancel overwrites `:was` with `:cancelled` (JS-discrepancy #2).

**Clojure port** explicitly refuses with `:error/item-not-found` (missing id) and `:error/cannot-cancel` (status is `:done` or `:cancelled`).

### `cloneItem(tasks, id)` → tasks
```js
const itemText = tasks.filter(x => x.id === id).at(0).text;
return addTask(tasks, itemText);
```

**Behavior:**
- Looks up the source task by id, reads its text, delegates to `addTask` with that text.
- The new task gets a fresh id and `addTask`'s status rule (`:ready` if no ready exists else `:new`).
- The *source* task is unchanged (clone copies text, doesn't transform the original).

**JS quirks:**
- No id-existence check — missing id throws TypeError (`.at(0)` returns undefined, `.text` blows up).
- **No status check** — happily clones any status, including `:new` and `:ready`. SCHEMA.md §3 / §12 describe clone as primarily for `:done`/`:cancelled`, but the implementation doesn't enforce that.

**Clojure port plan (Phase 5J.3):** add explicit `:error/item-not-found` for missing id (consistency with cancel-todo). Open question: match the permissive any-status JS behavior, or refuse `:new`/`:ready` with new `:error/cannot-clone`. See PHASES.md 5J.3 entry.

---

## `reviewManager.js`

### `genCurrentQuestion(tasks, cursor)` → string
Returns the prompt `"In this moment, are you more ready to '{cursor.text}' than '{benchmark.text}'?"`. Returns one of three error strings if cursor invalid (`-1` or out of range), or no benchmark item exists.

### `isPrioritizableList(tasks)` → boolean
`tasks.length > 1 && hasReady && hasNew && lastNewItem.id > lastReadyItem.id`.

**Important caveat:** the `lastNewItem.id > lastReadyItem.id` check assumes ids are monotonically allocated (matches `nextId`'s behavior). It does NOT actually check list-order position — it checks integer-id ordering. In the JS source these coincide because ids are append-only integers, but the *intent* is "last new comes after last ready in list order" (per SCHEMA.md §15).

**Clojure port (Phase 5K) must use list-position, not id ordering**, because UUIDs aren't ordered. This is JS-discrepancy #1, already noted in PHASES.md.

### `getInitialCursor(tasks)` → int
Returns the index of the first new task at-or-after the last-ready position.

```js
const lastReadyIndex = tasks.indexOf(lastReadyItem);
const slicedList = tasks.slice(lastReadyIndex);
const firstNewItem = slicedList.filter(status === "new").at(0);
return tasks.indexOf(firstNewItem);
```

### `nextCursor(tasks, currentCursor)` → int
First new task at-or-after `currentCursor`, or `-1` if none. Note: `currentCursor` is the *raw* index to slice from; `handleReviewDecision` calls it with `cursor + 1` to advance past the current item.

### `markReadyAtIndex(tasks, cursor)` → tasks
Sets `tasks[cursor].status = "ready"`. Used by the "Yes" branch of `handleReviewDecision`.

### `startReview(tasks)` → `{cursor}` | `{error: string}`
If list isn't prioritizable, returns `{error: "The list isn't prioritizable right now."}`. Else returns `{cursor: getInitialCursor(tasks)}`.

**Clojure port plan (Phase 5K) divergence:** Result-shaped return — `{:ok? false :error/type :error/not-prioritizable-list}` on failure, `{:ok? true :review/cursor n :review/active? true}` on success. String error message belongs to the UI layer.

### `handleReviewDecision(tasks, cursor, decision)` → `{tasks, cursor, endReview?}`
- `'Yes'`: marks `tasks[cursor]` ready, advances cursor to next-new.
- `'No'`: leaves tasks unchanged, advances cursor to next-new.
- *(else / 'Quit')*: cursor → -1.
- If new cursor is `-1` or `tasks.length`, returns with `endReview: true`.

**JS quirks:**
- Invalid decision (not "Yes"/"No"/"Quit") is treated as Quit — no explicit error.
- No `endReview: false` on the still-active branch — caller must `?? false`.

**Clojure port plan (Phase 5K) divergence (JS-discrepancy #4):** Result-shaped return — `{:ok? true :items ... :review/cursor n :review/active? boolean}`. Invalid decision → `:error/invalid-review-decision` (per SCHEMA.md §8).

---

## Cross-cutting divergences (running list)

| # | JS behavior | Clojure port behavior | Phase |
|---|-------------|----------------------|-------|
| 1 | `isPrioritizableList` uses id ordering | Uses list-position | 5K |
| 2 | `cancelItem` silently overwrites on double-cancel / done | Refuses with `:error/cannot-cancel` | 5J.1 ✅ |
| — | `cancelItem` silently no-ops on missing id | Refuses with `:error/item-not-found` | 5J.1 ✅ |
| — | `addTask` accepts blank text | Refuses with `:error/blank-item` | 5I.4 ✅ |
| — | `completeBenchmarkTask` silent no-op on inactionable | Refuses with `:error/no-actionable-items` | 5J.2 ✅ |
| 4 | Review functions return `{error: string}` or raw `{tasks, cursor, endReview}` | Result-shaped `{:ok? ... :items ... :review/cursor ...}` | 5K |
| 5 | `automark` dead-code early-return (reads function ref) | Properly calls `(auto-markable? items)` | 5I.3 ✅ |
| TBD | `cloneItem` clones any status without check | TBD — see PHASES.md 5J.3 | 5J.3 |
| TBD | `cloneItem` throws on missing id | Refuses with `:error/item-not-found` | 5J.3 |

---

## What's *not* in the JS source

These are domain concerns the Clojure port introduces from scratch (no JS reference):

- Guardrails `>defn` contracts and Malli schemas.
- The Result-shape convention (`{:ok? true/false ...}`) as a uniform return type.
- Structured `:error/type` keywords (the JS source uses raw strings for errors).
- The normalized/denormalized state-projection boundary between Fulcro mutations and `model.list` functions.

These are intentional Clojure-side improvements driven by Fulcro/Pathom idioms; they don't need JS approval since the JS source has no analogue.
