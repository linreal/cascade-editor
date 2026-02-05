# T-005: Drag-and-Drop System Implementation

This document breaks down the drag-and-drop feature into manageable subtasks with theoretical background for each.

---

## Overview

The drag-and-drop system allows users to reorder blocks by dragging them to new positions. Key requirements:
- Every block should be draggable with a unified mechanism
- Show horizontal divider as drop position indicator
- Dragged items remain in place with 50% transparency
- Semi-transparent preview follows user's finger
- Auto-scroll when dragging to top/bottom edges
- **Priority: Smoothness and performance**

---

## Task List

### Task 1: Enhanced DragState Data Model

**Description:**
Extend the existing `DragState` to include additional information needed for visual feedback and gesture tracking.

**Why we need it:**
Current `DragState` only has `draggingBlockIds` and `targetIndex`. For visual feedback, we need:
- Current touch/drag position (Y coordinate for preview placement)
- Initial drag offset within the block (so preview doesn't "jump")
- Original positions/indices of dragged blocks (for placeholder rendering)

**Changes:**
- Add `dragOffset: Float` - current Y position relative to the editor
- Add `initialTouchOffset: Float` - touch position within the dragged block
- Add `draggedBlockOriginalIndex: Int` - for visual calculations

**Compose Concepts:**
- `@Immutable` data classes for efficient recomposition skipping
- State hoisting patterns
- `derivedStateOf` for computed values from state

---

### Task 2: Drag Gesture Detection Modifier

**Description:**
Create a reusable `Modifier` extension that detects long-press to initiate drag, then tracks drag movement.

**Why we need it:**
We need a unified mechanism that can be applied to any block type. Using a modifier keeps the drag logic decoupled from block rendering.

**Implementation approach:**
- Use `Modifier.pointerInput` with `detectDragGesturesAfterLongPress`
- Capture initial touch position on long press
- Report drag delta during movement
- Report drag end/cancel

**Compose Concepts:**
- `Modifier.pointerInput(key)` - gesture detection
- `detectDragGesturesAfterLongPress` - built-in gesture detector
- `awaitPointerEventScope` for raw pointer events
- `PointerInputScope` coordinate system
- Key parameter importance for gesture state reset
- `Modifier.onGloballyPositioned` - to capture block's size and absolute position for drop target calculations

---

### Task 3: Drag Actions and Reducers

**Description:**
Implement the EditorAction handlers for drag operations: `StartDrag`, `UpdateDrag`, `UpdateDragTarget`, `CompleteDrag`, `CancelDrag`.

**Why we need it:**
Following the existing unidirectional data flow pattern, all state changes go through actions. This ensures predictability and enables potential undo/redo.

**Actions to implement:**
- `StartDrag(blockId: BlockId, initialOffset: Float, touchOffset: Float)`
- `UpdateDrag(currentY: Float)` - updates drag position
- `UpdateDragTarget(targetIndex: Int?)` - updates drop indicator position
- `CompleteDrag` - commits the reorder
- `CancelDrag` - aborts and resets

**Compose Concepts:**
- Sealed class hierarchies for actions
- Reducer pattern: `(State, Action) -> State`
- Immutable state updates with `copy()`

---

### Task 4: Calculate Drop Target Index from Position

**Description:**
Create utility function to calculate the target drop index based on current drag Y position and item bounds.

**Why we need it:**
As the user drags, we need to continuously determine where the block would be dropped if released. This drives the drop indicator position.

**Implementation approach (Performance-focused):**
- Use `LazyListState.layoutInfo.visibleItemsInfo` to get positions
- Binary search or linear scan through visible items
- Calculate midpoint of each item to determine "above" or "below"
- Account for dragged item's original position

**Compose Concepts:**
- `LazyListState` and `LazyListLayoutInfo`
- `LazyListItemInfo.offset` and `LazyListItemInfo.size`
- `derivedStateOf` to avoid unnecessary recalculations
- Stable lambdas to prevent recomposition

---

### Task 5: Block Transparency During Drag

**Description:**
Apply 50% alpha to blocks that are being dragged while they remain in their original positions.

**Why we need it:**
Visual feedback that indicates which block is being moved. The block stays in place but appears "ghosted".

**Implementation approach (Performance-focused):**
- Use `Modifier.graphicsLayer { alpha = ... }` instead of `Modifier.alpha()`
- `graphicsLayer` doesn't trigger re-layout, only re-draw
- Conditionally apply based on `dragState.draggingBlockIds.contains(blockId)`

**Compose Concepts:**
- `Modifier.graphicsLayer` - hardware-accelerated layer modifications
- Difference between `alpha()` and `graphicsLayer { alpha }` (layout vs draw phase)
- Recomposition scope optimization
- `remember` + `derivedStateOf` for alpha calculation

---

### Task 6: Dragging Preview Overlay

**Description:**
Render a semi-transparent copy of the dragged block that follows the user's finger.

**Why we need it:**
Provides direct visual feedback of what's being moved and where it will go.

**Important:** `LazyColumn` clips its content by default, so the dragged item cannot simply be offset within the list - it must be rendered in a separate layer above the list to allow unrestricted movement across the entire editor area.

**Implementation approach:**
- Use `Box` with `Modifier.offset` for positioning
- Render at editor level (not inside LazyColumn) to allow free movement
- Use `Modifier.graphicsLayer` for translation and alpha
- Capture block content snapshot or re-render block with overlay styling
- Use `BlockRegistry` to look up the correct renderer for the ghost block so it looks exactly like the original

**Two approaches (prefer Option B for performance):**
- **Option A:** Re-compose the block in overlay position (simpler but may cause jank)
- **Option B:** Use `Modifier.drawWithCache` to capture block as bitmap, then draw bitmap in overlay (more complex but smoother)

**Compose Concepts:**
- Overlay composition with `Box`
- `Modifier.offset` with lambda variant for animated offsets
- `Modifier.graphicsLayer { translationY = ... }` for smooth movement
- `drawWithCache` for bitmap caching (advanced)
- Z-ordering with `Modifier.zIndex`

---

### Task 7: Drop Position Indicator (Horizontal Divider)

**Description:**
Show a horizontal line/divider between blocks indicating where the dragged item will be inserted.

**Why we need it:**
Clear visual feedback of the drop target position.

**Implementation approach:**
- Render indicator at editor level as overlay (not inside LazyColumn items)
- Position based on `dragState.targetIndex`
- Use visible item bounds from `LazyListState.layoutInfo` to calculate Y position
- Animate indicator movement with `animateFloatAsState`

**Performance Note (Critical):**
An alternative approach would be inserting a real "Divider" item into the `blocks` list during drag, which allows `LazyColumn` to animate space for it. However, this triggers heavy recomposition and creates state churn. **We must use the overlay approach** to avoid modifying the `blocks` list purely for visual indication. The overlay is more performant and smoother.

**Compose Concepts:**
- `Canvas` or `Divider` composable
- Absolute positioning with `Modifier.offset`
- `animateFloatAsState` for smooth indicator movement
- `LazyListState.layoutInfo.visibleItemsInfo` for position calculation

---

### Task 8: Auto-Scroll During Drag

**Description:**
When dragging near the top or bottom edges of the visible area, automatically scroll the list to reveal more items.

**Why we need it:**
Essential for reordering in lists that don't fit on screen. Without this, users can't drag items to positions outside the current viewport.

**Implementation approach:**
- Define "hot zones" at top and bottom (e.g., 80dp)
- When drag position enters hot zone, start scrolling
- Use `LazyListState.animateScrollBy()` or continuous scroll with `LazyListState.scroll { }`
- Scroll speed can vary based on how deep into the hot zone the finger is
- Implement as an **animation loop** that continuously scrolls while finger remains in hot zone

**Compose Concepts:**
- `LazyListState.animateScrollBy()` for animated scrolling
- `LazyListState.scroll { scrollBy() }` for imperative scrolling
- `LaunchedEffect` with drag position as key - runs animation loop while in hot zone
- `snapshotFlow` to convert state to Flow for reactive scrolling
- Coroutine cancellation for stopping scroll when leaving hot zone
- `while(true)` loop with `delay()` inside `LaunchedEffect` for continuous scrolling

---

### Task 9: Complete Drag - Reorder Blocks

**Description:**
When drag ends, move the dragged block(s) to the target index and update state.

**Why we need it:**
This is the actual reordering operation that persists the user's intent.

**Implementation approach:**
- `CompleteDrag` action triggers reducer
- Internally use/trigger `MoveBlocks(blockIds, toIndex)` action for the actual reordering
- Remove blocks from original positions
- Insert at target index (adjusting for removed items if needed)
- Clear `dragState`
- Optionally animate the final snap with `LazyColumn` item animations

**Compose Concepts:**
- List manipulation (remove/insert with index adjustment)
- `animateItem()` / `animateItemPlacement()` modifier for item placement animation
- This enables other (non-dragged) items to smoothly slide out of the way during reorder
- State immutability - create new list, don't mutate

---

### Task 10: Integrate Drag System into CascadeEditor

**Description:**
Wire all the pieces together in `CascadeEditor.kt` - add drag modifier to blocks, render overlay and indicator.

**Why we need it:**
The final integration that makes drag-and-drop functional in the editor.

**Changes to CascadeEditor:**
- Pass `LazyListState` to enable scroll control
- Add drag gesture modifier to block items
- Render drag preview overlay outside LazyColumn
- Render drop indicator at calculated position
- Connect gesture callbacks to dispatch actions

**Compose Concepts:**
- `rememberLazyListState()` for scroll state
- Composition structure for overlays
- State reading optimization (read state close to where it's used)
- Modifier ordering importance

---

### Task 11: Haptic Feedback

**Description:**
Add haptic feedback when drag starts and when dropping.

**Why we need it:**
Improves tactile user experience, especially on mobile where visual feedback alone may not be enough.

**Implementation approach:**
- Use `LocalHapticFeedback.current.performHapticFeedback()`
- Trigger on long-press detection (drag start)
- Optionally trigger on drop

**Compose Concepts:**
- `LocalHapticFeedback` CompositionLocal
- `HapticFeedbackType.LongPress`, `HapticFeedbackType.TextHandleMove`
- Side effects in gesture handlers

---

### Task 12: Multi-Block Drag Support

**Description:**
Support dragging multiple selected blocks together.

**Why we need it:**
When users have multi-selected blocks, they should be able to drag all of them at once.

**Implementation approach:**
- Check `selectedBlockIds` when starting drag
- If dragged block is in selection, drag all selected
- If dragged block is not in selection, drag only that block
- Preview should show stacked/grouped appearance
- All selected blocks get 50% alpha

**Compose Concepts:**
- Set operations on state
- Conditional rendering based on selection state
- Visual grouping techniques

---

## Performance Checklist

- [ ] Use `graphicsLayer` instead of `alpha()` modifier for transparency
- [ ] Use `Modifier.offset { }` (lambda) instead of `Modifier.offset(dp)` for animated positions
- [ ] Use `derivedStateOf` for computed values from drag state
- [ ] Avoid reading entire state in tight loops - read only what's needed
- [ ] Use `LazyListState.layoutInfo` instead of measuring composables
- [ ] Consider bitmap caching for drag preview if re-composition causes jank
- [ ] Use `animateItem()` modifier for smooth reorder animation
- [ ] Profile with Layout Inspector to verify minimal recomposition

---

## Dependency Graph

```
Task 1 (DragState)
    └── Task 3 (Actions)
            └── Task 10 (Integration)

Task 2 (Gesture Modifier)
    └── Task 10 (Integration)

Task 4 (Target Index Calculation)
    ├── Task 7 (Drop Indicator)
    └── Task 8 (Auto-Scroll)
            └── Task 10 (Integration)

Task 5 (Block Transparency)
    └── Task 10 (Integration)

Task 6 (Drag Preview)
    └── Task 10 (Integration)

Task 7 (Drop Indicator)
    └── Task 10 (Integration)

Task 9 (Complete Drag)
    └── Task 10 (Integration)

Task 11 (Haptic) - Independent, can be added anytime
Task 12 (Multi-Block) - Enhancement after basic drag works
```

---

## Suggested Implementation Order

1. **Task 1** - Enhanced DragState (foundation)
2. **Task 3** - Actions and Reducers (state management)
3. **Task 2** - Gesture Modifier (user input)
4. **Task 4** - Target Index Calculation (logic)
5. **Task 5** - Block Transparency (visual feedback)
6. **Task 7** - Drop Indicator (visual feedback)
7. **Task 6** - Drag Preview (visual feedback)
8. **Task 9** - Complete Drag (finalize operation)
9. **Task 10** - Integration (wire everything)
10. **Task 8** - Auto-Scroll (enhancement)
11. **Task 11** - Haptic Feedback (polish)
12. **Task 12** - Multi-Block Drag (advanced feature)

---

## Notes

- The existing `DragState` in `EditorState.kt` provides a starting point
- Existing drag-related actions in ARCHITECTURE.md: `StartDrag`, `UpdateDragTarget`, `CompleteDrag`, `CancelDrag`
- `BlockCallbacks.onDragStart(blockId)` is already defined but not implemented
- Consider using Compose 1.7+ `animateItem()` for smooth list animations
- **Inspiration:** While we're building a custom solution to match specific requirements (ghost view, specific indicator style), we can draw patterns from libraries like `ReorderableLazyColumn` from `org.burnoutcrew.composereorderable` or similar implementations

---

## References

- [Compose Drag and Drop](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling)
- [LazyListState API](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState)
- [GraphicsLayer documentation](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#graphics-modifier)
