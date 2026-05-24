# Phase 13 — Close S-import-export (JSON file import + export)

**Status:** ✅ Complete

The last 🟡 stale-partial story in the tracker. Phase 7.6 stubbed the save modal's Import + Export buttons (the Phase 7.11 Copy-URL and Phase 7.12 batch-text-paste paths were real). Phase 13 closes both file-IO halves.

- **`learn.util.tasks-io`** (new) — `parse-tasks-json` returns one of three shapes:
    - `{:ok? true :items <vector>}` on success
    - `{:ok? false :error/type :error/non-json}` when `JSON.parse` throws (UI surfaces "Please select a valid JSON file")
    - `{:ok? false :error/type :error/bad-json}` when JSON parsed but structure was wrong (UI surfaces "Failed to import tasks. Ensure the JSON file has the correct format")
  Reuses `learn.util.url-encoding/og-shape->items` for the shape translation (UUIDs fresh-generated; statuses preserved verbatim; legacy items without `:was` default to `:status/new` to keep the schema invariant). JVM-side parser adds a strict top-level JSON-type check so EDN's relaxed reader doesn't swallow non-JSON input as a symbol.
- **`learn.client.state/import-from-json*`** — state-helper. Appends parsed items to the existing list; empty / nil input is a no-op. No domain-rule application (imported items keep their statuses, matching the OG's `addAll`).
- **`learn.client/import-from-json` defmutation** with remote (server has a matching `record-list-items` handler under `'learn.client/import-from-json`).
- **`learn.client.ui.modals` CLJS-only helpers**:
    - `import-json-file!` — reads the selected file via `FileReader.readAsText`, runs `tasks-io/parse-tasks-json`, dispatches the mutation on success or sets `:ui/err-msg` with the right error string on failure. Clears the `<input>`'s value after each pick so the user can re-select the same file after fixing an error.
    - `export-items-json!` — `items` → `items->json` → `Blob` → `URL.createObjectURL` → synthetic anchor click downloading `tasks.json`. Filename matches the OG ReactJS port so cross-app round-trips work.

OG reference: `pwa-autofocus-app/src/utils/tasksIO.js` (`importTasksFromJSON`, `exportTasksToJSON`) + `App.js` (`handleImportTasks`, `handleExportTasks`).

**Numbers**: 103 specs / 722 assertions (99 → 103 / 675 → 722). New specs: 2 in `tasks-io-test` (parse happy + failure paths), 2 in `client-test` (state-helper + mutation round-trip); resolvers-test got 2 more registry assertions for import-from-text + import-from-json wire-up.

**Closes**: `S-import-export` (was 🟡 since 7.6 / 7.12), `S-import-json-file` (was ⬜), `S-export-json-file` (was ⬜).
