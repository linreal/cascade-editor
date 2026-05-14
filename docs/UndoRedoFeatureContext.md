# Undo/Redo Feature Context

## 1. Feature Overview

The undo/redo system introduces a **hybrid linear history model** that tracks two kinds of edits: compact per-block text entries (`BlockTextEntry`) for typing, deletion, and single-block formatting, and full-document checkpoint pairs (`StructuralEntry`) for semantic or multi-block operations like split, merge, drag reorder, and slash commands. The system coalesces continuous typing into batches, restores exact focused-block cursor position and pending formatting styles on replay, and exposes a public API (`canUndo`, `canRedo`, `undo()`, `redo()`) plus keyboard shortcuts (`Cmd/Ctrl+Z`, `Shift+Cmd/Ctrl+Z`).

The hybrid approach was chosen because the editor has two independent mutation pipelines — snapshot/reducer state (`EditorAction`) and live runtime state (`TextFieldState` + `BlockSpanStates`) — and neither alone captures the full editing context. Full-document checkpoints for every keystroke would be too expensive; pure action-log replay would miss plain typing entirely.

## 2. Architecture & Design Decisions

### New Classes & Interfaces

| File | Key Symbols | Purpose |
|------|-------------|---------|
| `state/EditorHistory.kt` | `EditingUiState`, `EditorCheckpoint`, `HistoryEntry`, `StructuralEntry`, `BlockTextEntry`, `MergePolicy`, `HistoryManager` | Core history model: entry types, stack management, merge policy, depth trimming |
| `state/EditorHistoryUiState.kt` | `captureFocusedEditingUiState()`, `restoreFocusedEditingUiState()` | Capture and restore focused selection + pending styles in visible-text coordinates |
| `state/EditorHistoryCheckpoint.kt` | `captureCheckpoint()`, `applyCheckpoint()` | Full-document checkpoint capture (runtime over snapshot) and structural replay |
| `state/EditorHistoryBlockText.kt` | `buildHistoryEntryFromCheckpoints()`, `buildBlockTextEntryOrNull()`, `applyBlockTextEntry()` | Strict promotion predicate, compact one-block replay |
| `state/EditorTextHistory.kt` | `TextEditHistoryTracker`, `TextEditCoalescer`, `TextHistoryTrackerSink` | Per-block typing batch capture, coalescing logic, holder-facing batch-break interface |
| `ui/LocalEditorStateHolder.kt` | `LocalEditorStateHolder` | CompositionLocal providing history-aware holder to internal infrastructure |

### Patterns Used

- **Sealed interface hierarchy** — `HistoryEntry` is sealed with two concrete data classes. Replay uses `when` exhaustive matching to choose the correct scope (full-document vs. single-block).
- **Two-stack linear history** — `HistoryManager` owns `undoStack` and `redoStack` as `ArrayDeque`. Fresh edits clear redo. No branching.
- **Explicit merge policy** — `MergePolicy` is a sealed interface (`Isolate` | `TryMerge(merger)`). The stack layer is generic; higher layers decide when merging is valid.
- **Strategy via `fun interface`** — `HistoryEntryMerger` is a single-method interface passed into `TryMerge`, keeping coalescing logic outside the stack.
- **Replay guard** — `EditorStateHolder.isApplyingHistory` is a plain boolean (synchronous main-thread replay). Observers and history push both check this guard.
- **Tracker registry** — `EditorStateHolder` maintains a `Map<BlockId, Set<TextHistoryTrackerSink>>` so external commands (formatting, structural transactions) can break or resync live typing batches without reaching into `TextBlockField`.
- **`@Immutable` data classes** — `EditingUiState`, `EditorCheckpoint`, `StructuralEntry`, `BlockTextEntry` are all `@Immutable`.
- **`@Stable` manager** — `HistoryManager` and `EditorStateHolder` are `@Stable` with `mutableStateOf` for Compose reactivity on `canUndo` / `canRedo`.

### Why Hybrid, Not Pure Checkpoint or Pure Command

| Approach | Problem in this codebase |
|----------|-------------------------|
| Action-log only | Plain typing bypasses `EditorAction` — most common edit path would be invisible |
| Snapshot-only | `EditorState` lacks cursor, selection, pending styles, unsync'd runtime text |
| Pure inverse ops | Would require invertible handlers for every mutation source — too expensive before a single transaction pipeline exists |
| Checkpoint only | Full-document snapshots on every keystroke are wasteful; most edits touch one block |

The hybrid model keeps structural edits safe with full checkpoints and typing cheap with per-block before/after pairs.

## 3. Data Flow

### Typing (hot path)

```
User types in TextBlockField
  → TextFieldState.text changes (Compose runtime)
  → snapshotFlow in TextBlockField fires
  → isApplyingHistory check — skip if replay
  → SpanMaintenanceTextObserver.onCommittedVisibleText()  (span upkeep)
  → captureCheckpoint() → EditorCheckpoint (after state)
  → TextEditHistoryTracker.onUserTextCommit(afterCheckpoint)
      → buildHistoryEntryFromCheckpoints(baseline, after)
          → buildBlockTextEntryOrNull() — strict promotion predicate
          → falls back to StructuralEntry if predicate fails
      → TextEditCoalescer.policyFor(entry)
          → checks: same block, same direction, ≤500ms, adjacent, no breaker
          → returns MergePolicy.TryMerge or MergePolicy.Isolate
  → EditorStateHolder.pushHistoryEntry(entry, policy)
      → HistoryManager.push() — merge or isolate, clear redo, trim to maxDepth
  → ListAutoDetectObserver.onTextChanged()
```

### Structural edit (e.g., Enter split)

```
User presses Enter in TextBlockField
  → TextBlockKeyHandler → callbacks.onEnter()
  → DefaultBlockCallbacks.onEnter()
  → runStructuralMutation() → stateHolder.runStructuralHistoryTransaction()
      → breakAllTextHistoryBatches()
      → captureCheckpoint() (before)
      → mutation: runtime split + dispatch(SplitBlock)
      → captureCheckpoint() (after)
      → pushHistoryEntry(StructuralEntry(before, after))
      → syncAllTextHistoryTrackers(afterCheckpoint)
```

### Undo

```
User presses Cmd+Z
  → TextBlockKeyHandler → onUndo() → EditorStateHolder.undo()
  → isApplyingHistory guard (skip if already replaying)
  → HistoryManager.undo(apply = { entry → ... })
      → move entry from undoStack to redoStack
      → withHistoryReplay { ... }  (sets isApplyingHistory = true)
      → StructuralEntry:
          → applyCheckpoint(entry.before, textStates, spanStates)
              → cleanup stale runtime holders
              → restore/update text + span state per block
              → replaceStateForReplay(replayState)
              → restoreFocusedEditingUiState()
          → syncAllTextHistoryTrackers(checkpoint)
      → BlockTextEntry:
          → applyBlockTextEntry(blockId, content, ui, textStates, spanStates)
              → patch snapshot content for one block
              → patch runtime text + spans for one block
              → restore focused selection + pending styles
          → syncTextHistoryTracker(blockId, content, ui)
```

### Redo

Same flow as undo but reads from `redoStack` and applies the `after` side.

## 4. Public API Surface

All public API lives on `EditorStateHolder`:

```kotlin
public class EditorStateHolder {
    /** True when at least one undo step is available. Compose-observable. */
    public val canUndo: Boolean

    /** True when at least one redo step is available. Compose-observable. */
    public val canRedo: Boolean

    /** Applies the previous history entry. No-op during active replay. */
    public fun undo()

    /** Re-applies the next history entry. No-op during active replay. */
    public fun redo()
}
```

**Usage by external toolbars** (demonstrated in `sample/.../EditorDemoScreen.kt`):

```kotlin
IconButton(
    onClick = { editorState.undo() },
    enabled = !readOnly && editorState.canUndo,
) { /* icon */ }

IconButton(
    onClick = { editorState.redo() },
    enabled = !readOnly && editorState.canRedo,
) { /* icon */ }
```

History binds to runtime holders internally via `DisposableEffect`; it does not add history-specific `CascadeEditor` parameters.

`CascadeEditorConfig(readOnly = true)` disables only editor-owned undo/redo shortcuts handled by the text-field key path. Public `EditorStateHolder.undo()` and `redo()` remain app-owned mutation APIs, so external toolbar/header buttons should gate them when the app is in read-only mode.

## 5. Integration Points

### Where history hooks into the existing codebase

| Integration Point | What It Does | Entry Type |
|-------------------|-------------|------------|
| `TextBlockField` `snapshotFlow` | Captures user text edits after span maintenance | `BlockTextEntry` (via tracker) |
| `DefaultBlockCallbacks.onEnter` | Split on Enter | `StructuralEntry` (via transaction) |
| `DefaultBlockCallbacks.onBackspaceAtStart` | Merge on backspace | `StructuralEntry` (via transaction) |
| `DefaultBlockCallbacks.onDeleteAtEnd` | Merge on delete | `StructuralEntry` (via transaction) |
| `DefaultBlockCallbacks.dispatch` | `CompleteDrag`, `ToggleTodo`, `DeleteSelectedOrFocused` | `StructuralEntry` (via transaction) |
| `SpanActionDispatcher.mutateFormatting` | Selected-range formatting | `BlockTextEntry` or `StructuralEntry` (via checkpoints) |
| `SlashCommandExecutor` | Slash command execution | `StructuralEntry` (via suspend transaction) |
| `TextBlockField` list auto-detect | List prefix detection + conversion | `StructuralEntry` (via transaction) |
| `TextBlockKeyHandler` | `Cmd/Ctrl+Z` → undo, `Shift+Cmd/Ctrl+Z` → redo | Triggers replay |
| `CascadeEditor` `DisposableEffect` | Binds/unbinds `BlockTextStates` + `BlockSpanStates` to holder | Enables runtime-aware replay |
| `EditorStateHolder.setState` / `loadFromJson` | Hard replacement paths | Clears history |

### What bypasses history

- Direct external `EditorStateHolder.dispatch(action)` — by contract in v1
- Direct external `EditorStateHolder.undo()` / `redo()` while the app is in read-only mode
- Focus-only changes (`FocusBlock`, `FocusPreviousBlock`, etc.)
- Block selection changes (`SelectBlock`, `ClearSelection`, etc.)
- Slash menu navigation (`OpenSlashCommand`, `HighlightSlashCommand`, etc.)
- Drag hover updates (`UpdateDragTarget`)
- Collapsed-cursor pending-style toggles (no document change)

## 6. Edge Cases & Known Constraints

### Replay Guard

`isApplyingHistory` is a plain `Boolean`, not atomic or snapshot state. This works because replay is synchronous on the Compose/UI thread. All observers (`snapshotFlow` in `TextBlockField`, `pushHistoryEntry`) check this guard. If replay were made async, this would need to change.

### Tracker Lifecycle vs. Composition

`TextEditHistoryTracker` instances are created per-block by `TextBlockField` and registered with `EditorStateHolder`. Structural replay clears runtime holders, which triggers `generation` key changes that recreate trackers on the next composition pass. Between structural replay and recomposition, stale trackers may briefly exist — `cleanupStaleTextHistoryTrackers` eagerly prunes deleted blocks.

### Checkpoint Normalization Invariant

All checkpoints require the document to end with a text-supporting block. `EditorCheckpoint.requireNormalizedForHistory()` enforces this at capture time. If a mutation produces a document that violates the trailing-text-block invariant, `ensureTrailingTextBlock()` in the state replacement path corrects it before the checkpoint is captured.

### Paste Detection

Compose does not expose reliable paste origin metadata on the committed text path. The system uses two signals:
1. **Explicit keyboard paste**: `Cmd/Ctrl+V` key event detected in `TextBlockKeyHandler`
2. **Conservative fallback**: any multi-character single-commit insertion is treated as paste-like

Both break the typing coalescing batch. Multi-block paste is out of v1 scope.

### Selection-Only Updates

Cursor/selection motion updates only the checkpoint's `EditingUiState` (via `noteSelectionChanged`), not the full block snapshot. This avoids unnecessary block content comparison on every caret move. A non-collapsed selection expansion is a batch breaker.

### Structural Transactions Always Force `StructuralEntry`

Even when the before/after delta of a structural command (e.g., `ToggleTodo`) would pass the `BlockTextEntry` predicate, it is always stored as `StructuralEntry`. This keeps semantic commands replaying through the full-document checkpoint path.

### Suspend Variant for Slash Execution

`runStructuralHistoryTransactionSuspend` exists because `SlashCommandExecutor`'s public API is suspending. The important contract: the lambda must not suspend between document-mutating calls. The suspend signature exists for API compatibility, not for concurrent mutations.

### IME Reconnection Avoidance

`applyCheckpoint` reuses existing `TextFieldState` instances where possible (comparing visible text before calling `setText`). This prevents `BasicTextField` from reconnecting the IME and reopening the keyboard during undo/redo.

### Direct `dispatch()` Bypass

Direct external `EditorStateHolder.dispatch(action)` does not create history entries. This is an intentional v1 contract. Future built-in structural sources must route through holder transaction helpers if they need undo/redo coverage.

### History Depth

Default max depth: **100** entries. Oldest entries are trimmed from the undo stack on each push. No persistence across app restarts.

### Typing Coalescing Window

**500ms** between commits. Breaks occur on: focus change, non-collapsed selection, caret jump, insert↔delete direction change, paste, formatting command, structural command, and programmatic commits.

## 7. Glossary

| Term | Definition |
|------|-----------|
| `BlockTextEntry` | Compact history entry for edits confined to one existing text block. Stores block ID + before/after `BlockContent.Text` + before/after `EditingUiState`. |
| `StructuralEntry` | Full-document history entry storing before/after `EditorCheckpoint` pairs. Used for any edit that changes block count, order, types, or touches multiple blocks. |
| `EditorCheckpoint` | Snapshot of the entire document (`List<Block>`) plus focused editing UI state. Resolved from mixed runtime/snapshot state without JSON. |
| `EditingUiState` | The focused editing context: `focusedBlockId`, `focusedTextSelection` (visible-text coordinates), `focusedPendingStyles`. |
| `HistoryManager` | Linear two-stack (undo + redo) controller with depth trimming and redo invalidation. Owns stack behavior only — does not know how entries are applied. |
| `TextEditHistoryTracker` | Per-block capture state machine that turns committed text snapshots into history entries. Owns the baseline checkpoint and delegates coalescing to `TextEditCoalescer`. |
| `TextEditCoalescer` | Decides whether a new text edit can merge into the previous typing batch based on time, adjacency, direction, and break conditions. |
| `TextHistoryTrackerSink` | Holder-facing interface for external commands to break or resync a live typing batch without reaching into `TextBlockField`. |
| `MergePolicy` | Sealed interface: `Isolate` (new undo step) or `TryMerge(merger)` (attempt to combine with previous entry). |
| `isApplyingHistory` | Boolean replay guard on `EditorStateHolder`. While true, observers skip capture and `pushHistoryEntry` is a no-op. |
| `Visible-text coordinates` | Text positions excluding the internal ZWSP sentinel character that `TextFieldState` uses. All history selection/position values use these coordinates. |
| `Structural transaction` | A holder-owned wrapper (`runStructuralHistoryTransaction`) that breaks typing batches, captures before/after checkpoints, pushes a forced `StructuralEntry`, and re-anchors surviving trackers. |
