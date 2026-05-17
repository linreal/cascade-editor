# Block Selection - Technical Overview

## 1. Feature Overview

Block selection introduces a multi-select mode to the CascadeEditor. Users long-press a block to select it, then tap additional blocks to toggle them in/out of the selection set. Selected blocks are highlighted with a semi-transparent overlay and cannot be edited or focused. The consumer observes selection state reactively via `EditorState.selectedBlockIds` and `EditorState.hasSelection`, and can perform bulk operations (delete, clear) by dispatching standard `EditorAction`s. When a selected block is dragged, the editor moves selected outline roots plus their descendants as one subtree-aware payload, preserving relative indentation and renumbering ordered lists. The feature follows the editor's existing unidirectional data flow and requires zero changes from custom renderers to work out of the box.

## 2. Architecture & Design Decisions

### New abstractions

| Symbol | Location | Purpose |
|--------|----------|---------|
| `handlesSelectionVisual` | `BlockRenderer<T>` interface | Boolean property (default `false`). Lets renderers opt out of the wrapper-level selection overlay and draw their own selection chrome |
| `selectionOverlay` | `CascadeEditorColors` data class | Theme color slot for the semi-transparent overlay (`Color(0x221A73E8)` light, `Color(0x228AB4F8)` dark) |
| `isDropAtOriginalPosition()` | `BlockGestureModifier.kt` | Pure function: returns `true` when a drag's drop target and indentation lane match the block's original state (meaning no movement occurred) |
| `DragState.payloadBlockIds` | `EditorState.kt` | Document-ordered block IDs included in the active drag payload, including selected root subtrees |
| `DragState.dragRootIds` | `EditorState.kt` | Selected or touched root IDs after descendant de-duplication |
| `detectDragAfterLongPress()` | `BlockGestureModifier.kt` | Custom gesture detector replacing Compose's `detectDragGesturesAfterLongPress`, adding configurable `longPressTimeoutMillis` and an `onTap` callback |
| `awaitCustomLongPress()` | `BlockGestureModifier.kt` | Low-level pointer wait loop with custom timeout |

### Key design decisions

**Focus/selection mutual exclusivity (enforced in reducers, not UI).** All selection reducers clear `focusedBlockId`; all focus reducers (with non-null target) clear `selectedBlockIds`. This guarantees no invalid state regardless of how actions are dispatched (gesture, programmatic, tests).

**Position-and-lane "no-move" detection over distance-based.** Whether a long-press-and-release triggers selection is determined by comparing the drop target index to the block's original index and the future indentation lane to the original root indentation, not by accumulated drag distance. This keeps the check immune to touch jitter while still allowing X-axis-only indentation drags.

**Wrapper-level interaction gate with renderer opt-out.** Selection overlay and tap interception live on the block wrapper in `CascadeEditor.kt`, guaranteeing all renderers (built-in and custom) participate in selection mode without any code changes. Renderers needing custom selection visuals set `handlesSelectionVisual = true`.

**Shortened long-press timeout (190ms).** The default platform timeout (~400ms) felt sluggish for block selection. A `BLOCK_LONG_PRESS_MS` constant at the `CascadeEditor` level controls this.

**Subtree-aware selected drag.** Dragging a selected block resolves selected roots in document order, filters out selected descendants of another selected root, and expands each root to its full flat-outline subtree. Unsupported selected block types can still move as drag roots; indentation support only affects indent/outdent commands, not drag payload membership.

**Config disables selection and drag affordances at the UI boundary.** The reducers remain mutable for app-owned code, but `CascadeEditorConfig(readOnly = true)` prevents editor-owned long-press selection, tap toggles, drag start, hover target updates, drag completion, drag preview/drop indicator rendering, and empty-space edit focus. Editable configs can disable block affordances independently with `blockSelectionEnabled = false` and/or `blockDraggingEnabled = false`; when dragging is disabled but selection remains enabled, long-press toggles block selection directly instead of starting a drag. Native text selection inside text fields remains available.

## 3. Data Flow

### Long-press to select (primary entry point)

```
User long-presses block
  -> awaitCustomLongPress() fires after 190ms
  -> onDragStart: dispatches StartDrag(blockId, touchOffsetY)
     (block appears at 0.5 alpha as drag feedback)
  -> User lifts finger without moving block to a different position or indentation lane
  -> onDragEnd: isDropAtOriginalPosition() returns true
     -> dispatches CancelDrag (clears DragState)
     -> dispatches ToggleBlockSelection(blockId)
        -> reducer adds blockId to selectedBlockIds
        -> reducer sets focusedBlockId = null  (invariant)
  -> Recomposition: block shows selection overlay
```

### Tap to toggle in selection mode

```
User taps a block while state.hasSelection == true
  -> detectDragAfterLongPress onTap callback fires (finger lifted before long-press timeout)
  -> findItemAtPosition() identifies the tapped block
  -> dispatches ToggleBlockSelection(blockId)
     -> reducer adds or removes blockId from selectedBlockIds
     -> if selectedBlockIds becomes empty, selection mode exits
  -> Recomposition: overlay added/removed
```

### Drag selected roots and subtrees

```
User long-presses and drags a selected block
  -> StartDrag(blockId, touchOffsetY)
     -> resolveDragPayload():
        1. Find selected root blocks in document order
        2. Drop selected descendants already covered by another selected root
        3. Expand each root to include following descendants with deeper indentation
        4. Store dragRootIds, payloadBlockIds, primaryRootId, and original depths
  -> Pointer movement updates the semantic hover target:
     -> Y chooses the visual gap
     -> X chooses futureRootIndentationLevel in whole indent-unit steps
     -> invalid gaps clear DragState.targetIndex
  -> CompleteDrag:
     -> MoveDragPayload removes the payload, inserts it at the resolved gap,
        applies one root-depth delta to all payload blocks, validates outline shape,
        and renumbers ordered lists
  -> Recomposition: selected roots and descendants appear in the new order/depth
```

### Focus suppression during selection

```
TextBlockField tap overlay fires onFocus(blockId)
  -> DefaultBlockCallbacks.onFocus() checks stateProvider().hasSelection
  -> Returns early without dispatching FocusBlock
  -> Additionally: CascadeEditor's LaunchedEffect watches hasSelection
     -> calls focusManager.clearFocus() to dismiss keyboard
```

### Exiting selection mode

1. **Deselect last block:** `ToggleBlockSelection` removes the only selected ID -> `selectedBlockIds` is empty -> `hasSelection` becomes false -> normal mode resumes.
2. **Consumer dispatches `ClearSelection`:** Directly empties `selectedBlockIds`.

## 4. Public API Surface

### State observation

| Property | Type | Description |
|----------|------|-------------|
| `EditorState.selectedBlockIds` | `Set<BlockId>` | Currently selected block IDs |
| `EditorState.hasSelection` | `Boolean` | `true` when any block is selected |
| `EditorState.hasSingleSelection` | `Boolean` | `true` when exactly one block is selected |
| `EditorState.selectedBlocks` | `List<Block>` | Selected blocks in list order |

### Drag payload state

`EditorState.dragState` is nullable, but while a drag is active it includes subtree-aware payload metadata:

| Property | Description |
|----------|-------------|
| `dragRootIds` | Document-ordered root IDs in the payload. In selection mode this is the selected root set after descendant de-duplication. |
| `payloadBlockIds` | Full payload IDs in document order, including every root subtree. |
| `primaryRootId` | Root that drives preview/depth intent; this is the root containing the block that started the gesture. |
| `originalRootIndentationLevels` | Original depth for each root, used to compute the final drag depth delta. |
| `futureRootIndentationLevel` | Current resolved future depth for `primaryRootId`, updated only when hover resolution crosses a semantic lane. |

All properties are on the `@Immutable` `EditorState` data class, observable via `stateHolder.state`.

### Actions (all pre-existing, reducer behavior modified)

| Action | Signature | Behavior change in this feature |
|--------|-----------|-------------------------------|
| `SelectBlock(blockId)` | `data class` | Now validates block exists; clears `focusedBlockId` |
| `ToggleBlockSelection(blockId)` | `data class` | Now validates block exists when adding; clears `focusedBlockId` when result is non-empty |
| `SelectBlockRange(fromId, toId)` | `data class` | Now clears `focusedBlockId` |
| `AddBlockRangeToSelection(fromId, toId)` | `data class` | Now clears `focusedBlockId` |
| `SelectAll` | `data object` | Now clears `focusedBlockId` |
| `ClearSelection` | `data object` | Unchanged |
| `DeleteSelectedOrFocused` | `data object` | Unchanged (delegates to `DeleteBlocks`) |
| `FocusBlock(blockId)` | `data class` | Now clears `selectedBlockIds` when `blockId` is non-null |
| `FocusNextBlock` | `data object` | Now clears `selectedBlockIds` |
| `FocusPreviousBlock` | `data object` | Now clears `selectedBlockIds` |

### Theme addition

```kotlin
CascadeEditorColors(
    // ...
    selectionOverlay: Color  // new, stable public API
)
```

Light default: `Color(0x221A73E8)` (primary blue at ~13% opacity).
Dark default: `Color(0x228AB4F8)`.

### Renderer interface addition

```kotlin
public interface BlockRenderer<T : BlockType> {
    public val handlesSelectionVisual: Boolean get() = false  // new, stable public API
    // ...
}
```

## 5. Integration Points

### Modified components

| Component | What changed |
|-----------|-------------|
| `EditorAction.kt` | Selection/focus reducers enforce the mutual exclusivity invariant; public drag actions and internal hover/move reducers carry subtree payload and future-depth state |
| `BlockGestureModifier.kt` | Custom long-press detector with configurable timeout; `onTap` callback for selection-mode taps; `isDropAtOriginalPosition()` pure function; `finishDrag()` helper dispatching `ToggleBlockSelection` when drop is at original position and indentation lane |
| `DragUtils.kt` | Depth-aware hover target resolver used during direct drag and auto-scroll, including invalid self-payload gap rejection, unsupported-root depth pinning, and future root depth calculation |
| `DropIndicator.kt` / `DragPreview.kt` | Use `futureRootIndentationLevel` so selection payloads preview and drop into the same depth lane; preview shows a compact `+N` badge for additional payload blocks |
| `CascadeEditor.kt` | Selection overlay modifier on block wrapper; clickable overlay `Box` that consumes all taps during selection mode; `LaunchedEffect` clearing Compose focus on `hasSelection`; subtree drag overlay wiring; `selectionOverlayColor` read from theme |
| `BlockRenderer.kt` | `handlesSelectionVisual` property on `BlockRenderer<T>` interface; focus suppression guard in `DefaultBlockCallbacks.onFocus()` |
| `CascadeEditorColors.kt` | `selectionOverlay` color slot with light/dark presets |
| `TextBlockField.kt` | Focus request routed through `callbacks.onFocus(block.id)` instead of direct `focusRequester.requestFocus()` |
| `Logger.kt` / platform variants | `internal` visibility (was `public`) |

### Dependencies

The feature depends on:
- `EditorState.selectedBlockIds` / `hasSelection` (pre-existing state fields)
- `DragState.targetIndex`, `primaryBlockOriginalIndex`, `payloadBlockIds`, and `futureRootIndentationLevel`
- `calculateDropTargetIndex()`, `resolveDepthAwareDragHoverTarget()`, and `recomputeDepthAwareDragHoverTarget()` from `DragUtils.kt`
- `BlockRegistry.getRenderer()` (for `handlesSelectionVisual` lookup)
- `LocalCascadeTheme` (for `selectionOverlay` color)

## 7. Edge Cases & Known Constraints

- **`FocusBlock(null)` does NOT clear selection.** This is intentional — `ClearFocus` is used in drag-start paths where selection may still be active. Only `FocusBlock(nonNullId)` enforces the invariant.

- **`ToggleBlockSelection` removing the last block does NOT restore focus.** After deselection, both `focusedBlockId` and `selectedBlockIds` are null/empty. The consumer must explicitly dispatch `FocusBlock` to re-enter editing.

- **Post-delete state is fully cleared.** `DeleteSelectedOrFocused` delegates to `DeleteBlocks`, which clears both focus (if the focused block was deleted) and selection (removing deleted IDs). No automatic focus restoration.

- **Selection overlay renders behind drag alpha.** A selected block that enters drag state shows the overlay at 50% opacity (due to `graphicsLayer { alpha = 0.5f }`).

- **Tap in selection mode uses a full-size overlay `Box`.** The `Box(Modifier.matchParentSize().clickable(enabled = false) {})` consumes all pointer events on block content (text fields, checkboxes, etc.) when `hasSelection` is true, preventing any child interaction. This is on top of the `DefaultBlockCallbacks.onFocus()` guard as defense-in-depth.

- **Scroll gestures during selection mode.** The `onTap` callback in `detectDragAfterLongPress` only fires when `wasFingerLifted` is true. If the pointer was consumed by scroll, the tap is suppressed — preventing accidental selection toggles during scrolling.

- **`ClearFocus` and `ClearSelection` are orthogonal.** Neither enforces the mutual exclusivity invariant on the other. This is correct because `ClearFocus` is used in drag paths where selection should persist.

- **Selected descendants are not duplicated in drag payloads.** If both a parent and child are selected, dragging the parent moves the child once as part of the parent's subtree.

- **Unsupported blocks can be dragged but not indented by selection commands.** Drag payload membership follows outline position and selection, while `IndentForward`/`IndentBackward` only target supported block types. If an unsupported block is the primary drag root, horizontal drag movement cannot give it indentation; the future root depth stays `0`.

- **Invalid drag hover clears the target.** Gaps inside the payload or gaps that cannot accept the payload without corrupting outline depth set `DragState.targetIndex = null`, hide the indicator, and make `CompleteDrag` clear drag state without moving blocks.

- **No "tap on empty area" to exit selection.** The consumer must clear selection via `ClearSelection` dispatch or by having the user deselect all blocks one by one.

- **Read-only transition cleanup.** If read-only mode becomes active while blocks are selected or a drag is active, `CascadeEditor` dispatches `ClearSelection` and `CancelDrag` as UI cleanup. This does not mutate document blocks. External `EditorStateHolder.dispatch(...)` calls can still create selection or drag state unless the application gates them.

## 8. Glossary

| Term | Definition |
|------|-----------|
| **Selection mode** | Editor state where `hasSelection == true`. Text editing, focus, and child interaction are suppressed. All taps toggle block selection. |
| **Focus/selection invariant** | The rule that `focusedBlockId` and `selectedBlockIds` cannot both be non-empty simultaneously, enforced by reducers. |
| **Interaction gate** | The wrapper-level `Box` and `clickable` overlay in `CascadeEditor.kt` that intercepts all pointer events during selection mode. |
| **`handlesSelectionVisual`** | `BlockRenderer` property allowing renderers to opt out of the default selection overlay and provide custom selection chrome. |
| **`selectionOverlay`** | `CascadeEditorColors` slot for the semi-transparent background drawn behind selected blocks. |
| **`isDropAtOriginalPosition`** | Pure function determining whether a drag ended at the block's starting position and indentation lane, used to convert long-press-without-move into a selection toggle. |
| **`BLOCK_LONG_PRESS_MS`** | Constant (190ms) defining the custom long-press timeout, shorter than the platform default (~400ms). |
| **Visual gap** | The drop indicator position during drag (index into the gaps between blocks). Gap N and N+1 for a block at index N both represent "no movement". |
| **Drag root** | A selected or touched block that owns a moved subtree in the drag payload. |
| **Drag payload** | The document-ordered set of blocks moved by a drag: roots plus descendants. |
