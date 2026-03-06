# Task 10. Selection Status, Toolbar Contracts, and Formatting UI

## Overview

Expose reactive rich-text selection status and formatting actions to UI without violating core architecture constraints:
- no cursor/selection in `EditorState`
- runtime span source of truth remains `BlockSpanStates`
- no full-tree recomposition on cursor movement

This task also introduces a default toolbar and a custom toolbar contract.

---

## Architecture Fit Check (Current Editor Architecture)

The proposed direction is valid for current architecture if we keep one strict boundary:
- `FormattingState` and toolbar actions are integration-layer contracts on top of `BlockTextStates` + `BlockSpanStates`.
- They must not mutate reducers directly or duplicate span algorithms.
- All formatting mutations go through `SpanActionDispatcher`.

This keeps Task 10 aligned with existing Task 7-9 choices and avoids runtime/snapshot drift.

---

## Critical Decisions — Locked

### D1. Style identity for toolbar buttons
`SpanStyle` equality is exact. For parametric styles (`Highlight(color)`, `Link(url)`, `Custom`), exact equality is often too strict for button status.

**Decision for V1:** keep exact matching for default controls (one fixed highlight color).
**Follow-up note:** if multi-color highlight/link-editing enters scope, add style-family matching in Task 11/12.

### D2. New-block continuation semantics
Current split flow clears pending styles on both source and new block. That means "start a new block with the current span style" is not supported without this fix.

**Decision for V1:** support continuation when Enter creates a new block from a collapsed caret.
- If pending styles exist at split time, transfer them to `newBlockId`.
- If no pending and split occurs at end-of-block, inherit from `activeStylesAt(splitPosition - 1)`.
- For ranged split (selection was non-collapsed), do not transfer pending styles.

This requires a change in `DefaultBlockCallbacks.onEnter` — continuation logic runs BEFORE calling `blockSpanStates.split()`, captures pending/continuation styles, then assigns them to `newBlockId` after split.

### D3. Disable policy scope
**Decision for V1:** disable for:
- `BlockType.Code` blocks
- No focused block (`focusedBlockId == null`)
- Non-text blocks (Divider, Image)
- Multi-block selection active (`selectedBlockIds.isNotEmpty()`)
- During drag-and-drop (`dragState != null`) — implicitly handled via `ClearFocus` on drag start, but toolbar should also check `dragState` directly for visual hide

Consolidated in one `canFormat` computation path in `FormattingStateCalculator`.

### D4. Toolbar extensibility model
**Decision for V1:** default toolbar is driven by a config list (`RichTextToolbarConfig` + button specs), not by inline hardcoded button calls.

### D5. Link handling
**Decision for V1:** Link is excluded from default toolbar buttons. No URL input UX in this task. `SpanStyle.Link` remains a valid style — custom toolbars can implement URL dialogs via `ToolbarSlot.Custom`.

### D6. Highlight color model
**Decision for V1:** single fixed color (yellow, `0xFFFFEB3B`). Configurable highlight palette deferred to future task.

### D7. InlineCode mutual exclusivity
**Decision for V1:** cumulative application, no special rules. InlineCode can overlap with Bold/Italic/Highlight. No mutual exclusion logic.

### D8. Block type conversion span policy
**Decision for V1:**
- Converting TO `Code` → strip spans from content (clear `BlockContent.Text.spans`)
- Converting FROM `Code` to any text type → spans already empty, no action
- Converting between non-Code text types (Paragraph, Heading, Todo, etc.) → preserve spans unchanged

This requires updating `ConvertBlockType` reducer to clear spans when target type is `Code`, and updating runtime `BlockSpanStates` at the callback boundary.

### D9. Toolbar focus stealing
**Decision for V1:** toolbar buttons must NOT steal focus from the text field. Use `Modifier.focusProperties { canFocus = false }` on all toolbar buttons and the toolbar container. This prevents `focusedBlockId` from going null mid-action.

### D10. Undo/Redo interaction
**Known V1 gap (documented):** `SpanActionDispatcher` dispatches `UpdateBlockContent` to sync snapshot. Future undo would need to reverse this, but runtime `BlockSpanStates` is not snapshot-captured. Undo could revert the snapshot while runtime stays styled → drift. This will be addressed when Undo/Redo is implemented. For now, document the constraint: undo system must coordinate with `BlockSpanStates` runtime state.

---

## Refined Contracts

### 10.1 `FormattingState`

**Location:** `editor/.../richtext/FormattingState.kt`

```kotlin
@Immutable
public data class FormattingState(
    val styles: Map<SpanStyle, StyleStatus>,
    val canFormat: Boolean,
    val focusedBlockId: BlockId?,
    val selectionCollapsed: Boolean,
) {
    public fun styleStatusOf(style: SpanStyle): StyleStatus =
        styles[style] ?: StyleStatus.Absent

    public companion object {
        public val Empty: FormattingState = FormattingState(
            styles = emptyMap(),
            canFormat = false,
            focusedBlockId = null,
            selectionCollapsed = true,
        )
    }
}
```

**State rules:**
- collapsed selection:
  - if pending styles are set (including empty set), they are canonical → styles in pending = `FullyActive`, styles not in pending = `Absent`
  - otherwise use continuation semantics (`activeStylesAt(position - 1)`) → styles at position-1 = `FullyActive`, others = `Absent`
- ranged selection:
  - pending styles ignored
  - status from `queryStyleStatus` for each tracked style

### 10.2 `FormattingActions`

**Location:** `editor/.../richtext/FormattingActions.kt`

```kotlin
@Stable
public interface FormattingActions {
    public fun toggleStyle(style: SpanStyle)
    public fun applyStyle(style: SpanStyle)
    public fun removeStyle(style: SpanStyle)
}
```

`DefaultFormattingActions` resolves focused block + visible selection from runtime state **at invocation time** (not at creation time) and delegates to `SpanActionDispatcher`. No-op if formatting is disallowed.

Must hold references to:
- `EditorStateHolder` (for `focusedBlockId`)
- `BlockTextStates` (for `TextFieldState.selection` → visible selection)
- `SpanActionDispatcher` (for style mutations)

### 10.3 `ToolbarSlot` + Default Toolbar Config

**Location:** `editor/.../ui/ToolbarSlot.kt`, `editor/.../ui/RichTextToolbar.kt`

```kotlin
public sealed interface ToolbarSlot {
    public data class Default(
        val config: RichTextToolbarConfig = RichTextToolbarConfig.Default
    ) : ToolbarSlot
    public data object None : ToolbarSlot
    public data class Custom(
        val content: @Composable (
            formattingState: State<FormattingState>,
            actions: FormattingActions,
        ) -> Unit
    ) : ToolbarSlot
}
```

`RichTextToolbarConfig` owns button list/order. V1 default buttons:
- Bold, Italic, Underline, StrikeThrough, InlineCode, Highlight(yellow)

**Not included in V1:** Link (requires URL input UX).

Adding new default buttons is a data/config change, not toolbar internals rewrite. Button spec:
```kotlin
public data class ToolbarButtonSpec(
    val style: SpanStyle,
    val label: String,       // For accessibility
    // Icon TBD: ImageVector, Painter, or resource reference
)
```

For V1, all buttons are simple toggles. Parametric styles (color picker, URL input) are a future extension to the spec.

---

## Observer and Computation Design

### 10.4 `FormattingStateCalculator` (Pure, Testable)
**Location:** `editor/.../richtext/FormattingStateCalculator.kt`

Pure function from inputs → `FormattingState`. Unit-testable without Compose.

**Inputs:**
- `focusedBlockId: BlockId?`
- `focusedBlockType: BlockType?`
- `hasBlockSelection: Boolean` (selectedBlockIds.isNotEmpty())
- `isDragging: Boolean` (dragState != null)
- `visibleSelectionStart: Int`
- `visibleSelectionEnd: Int`
- `spans: List<TextSpan>`
- `pendingStyles: Set<SpanStyle>?`
- `trackedStyles: List<SpanStyle>` (styles the toolbar cares about)

**`canFormat` computation:**
```
canFormat = focusedBlockId != null
    && focusedBlockType?.supportsText == true
    && focusedBlockType !is BlockType.Code
    && !hasBlockSelection
    && !isDragging
```

### 10.5 `FormattingStateObserver` (Composable Bridge)
**Location:** `editor/.../richtext/FormattingStateObserver.kt`

Composable that reads snapshot state and produces `State<FormattingState>`.

**Snapshot read inputs:**
- `derivedStateOf { stateHolder.state.focusedBlockId }` — narrows recomposition scope to focus changes only (avoids recomposing on any EditorState field change)
- focused block type (derived from focusedBlockId + blocks)
- `derivedStateOf { stateHolder.state.selectedBlockIds.isNotEmpty() }`
- `derivedStateOf { stateHolder.state.dragState != null }`
- focused block `TextFieldState.visibleSelection()` — high-frequency
- focused block span state (`State<List<TextSpan>>`) + pending styles

**Performance constraints:**
- no whole-document scan on cursor move
- only focused-block reads after focus resolution
- use `derivedStateOf` to ensure toolbar only recomposes when `FormattingState` output actually changes (cursor moves within same-style region → no recompose)
- pass `State<FormattingState>` down; do not read `.value` in `CascadeEditor` root

---

## Prerequisite: `visibleSelection()` Utility

**Location:** `editor/.../ui/BackspaceAwareTextEdit.kt` (alongside existing `visibleText()` and `visibleCursorPosition()`)

```kotlin
public fun TextFieldState.visibleSelection(): TextRange {
    val start = (selection.start - 1).coerceAtLeast(0)
    val end = (selection.end - 1).coerceAtLeast(0)
    return TextRange(start, end)
}
```

Required by: FormattingStateObserver, DefaultFormattingActions, and any consumer that needs text selection in visible coordinates. Without this, sentinel offset math would be scattered across 4+ files.

---

## New-Block Style Continuation Requirement

### Enter split integration update
**Files touched:** `registry/BlockRenderer.kt` (`DefaultBlockCallbacks.onEnter`)

When Enter creates `newBlockId`:
1. **Before split mutation:** compute continuation styles
   - If `blockSpanStates.getPendingStyles(blockId)` is non-null → use those
   - Else if split at end-of-block (collapsed cursor at text end) → inherit from `activeStylesAt(splitPosition - 1)`
   - Else (ranged split) → no continuation
2. Call existing split flow (`blockSpanStates.split(...)` which clears pending on both blocks)
3. If continuation styles were computed (non-empty) → `blockSpanStates.setPendingStyles(newBlockId, continuationStyles)`

This closes the explicit product gap: starting a new block while keeping intended inline style context.

---

## CascadeEditor Layout Change

### Current layout:
```
Box(dragGesture) {
    LazyColumn { blocks }
    AutoScrollDuringDrag(...)
    DropIndicator(...)
    DragPreview(...)
}
```

### New layout with toolbar:
```
Column {
    Box(weight=1f, dragGesture) {
        LazyColumn { blocks }
        AutoScrollDuringDrag(...)
        DropIndicator(...)
        DragPreview(...)
    }
    if (toolbar != ToolbarSlot.None) {
        Toolbar(formattingState, actions)  // keyboard-adjacent, bottom
    }
}
```

**Critical:** The toolbar must be OUTSIDE the drag gesture `Box`. Otherwise drag events would hit toolbar children. The drag gesture modifier stays on the inner `Box`, preserving existing behavior.

The outer `Column` replaces the current root `Box`. The `modifier` parameter from `CascadeEditor` applies to the `Column`.

---

## Default Toolbar UI

### `RichTextToolbar`
**Location:** `editor/.../ui/RichTextToolbar.kt`

V1 default controls:
- Bold, Italic, Underline, StrikeThrough, InlineCode, Highlight(yellow)

Behavior:
- `FullyActive` = selected/active visual state
- `Partial` = mixed indicator, click applies
- `Absent` = idle, click applies
- `canFormat == false` = visually disabled, clicks are no-op

UI constraints:
- horizontal scroll support for overflow
- 44dp min touch height
- accessibility content descriptions for each control
- keyboard-adjacent placement (bottom of editor)
- `Modifier.focusProperties { canFocus = false }` on all buttons — MUST NOT steal focus from text field
- hidden/disabled during drag-and-drop

---

## External Consumer Callback

### `onFormattingStateChanged`

In addition to `ToolbarSlot.Custom` (full composable control), provide a simple callback for consumers who want style state without implementing a full toolbar:

```kotlin
@Composable
public fun CascadeEditor(
    stateHolder: EditorStateHolder,
    registry: BlockRegistry = remember { createEditorRegistry() },
    modifier: Modifier = Modifier,
    toolbar: ToolbarSlot = ToolbarSlot.Default(),
    onFormattingStateChanged: ((FormattingState) -> Unit)? = null,
)
```

The callback fires from a `LaunchedEffect` on `FormattingState` changes. Uses structural equality to avoid redundant calls.

---

## Subtask Decomposition

### Subtask 10.0: Prerequisite Utility — DONE
**Files:**
- Modify `ui/BackspaceAwareTextEdit.kt`

Add `TextFieldState.visibleSelection(): TextRange` helper. Needed by subtasks 10.3, 10.4, 10.5.

**Tests:** Unit test for sentinel offset adjustment (0, 1, collapsed, reversed).

**Completed:** Added `visibleSelection()` public extension on `TextFieldState` in `BackspaceAwareTextEdit.kt` alongside existing `visibleText()` and `visibleCursorPosition()`. Subtracts 1 from both `selection.start` and `selection.end` for ZWSP sentinel offset, clamped to 0. 9 test cases in `VisibleSelectionTest.kt` covering: collapsed cursor at raw 0 (clamped), after sentinel (position 0), mid-text, end-of-text; ranged selections (start, middle, full); reversed selection preservation; empty text. Pending build verification.

### Subtask 10.1: Core Contracts — DONE
**Files:**
- New `richtext/FormattingState.kt`
- New `richtext/FormattingActions.kt`
- New `ui/ToolbarSlot.kt`
- New `ui/RichTextToolbarConfig.kt`

Data classes and interfaces only. No implementation yet.

**Completed:** Four contract files created exactly per spec. `FormattingState` — `@Immutable` data class with `styles: Map<SpanStyle, StyleStatus>`, `canFormat`, `focusedBlockId`, `selectionCollapsed`, `styleStatusOf()` helper, and `Empty` companion constant. `FormattingActions` — `@Stable` interface with `toggleStyle`/`applyStyle`/`removeStyle`. `ToolbarSlot` — sealed interface with `Default(config)`, `None`, and `Custom(content)` variants; `Custom` receives `State<FormattingState>` + `FormattingActions`. `RichTextToolbarConfig` — `@Immutable` data class with `buttons: List<ToolbarButtonSpec>` and `Default` companion with V1 buttons (Bold, Italic, Underline, StrikeThrough, InlineCode, Highlight yellow `0xFFFFEB3B`). `ToolbarButtonSpec` — `@Immutable` data class with `style: SpanStyle` and `label: String`. Pending build verification.

### Subtask 10.2: New-Block Continuation on Enter — DONE
**Files:**
- Modify `registry/BlockRenderer.kt` (`DefaultBlockCallbacks.onEnter`)

Capture continuation styles before split, assign to `newBlockId` after split per D2 policy. This is critical path code — do it early and test before building on top.

**Tests:**
- Pending styles transferred to new block
- End-of-block inheritance when no pending (cursor at end of bold → new block starts bold)
- Mid-block split does NOT transfer pending for ranged selection
- Empty block with pending styles → Enter → new block inherits pending

**Completed:** Updated `DefaultBlockCallbacks.onEnter` to compute continuation styles BEFORE calling `blockSpanStates.split()` (which clears pending on both blocks). Guarded by `selectionCollapsed` check on the `TextFieldState` — ranged splits produce no continuation per D2 policy. For collapsed splits, three-way priority: (1) if `getPendingStyles(blockId)` is non-null → use those, (2) else if cursor is at end-of-block and position > 0 → inherit from `activeStylesAt(splitPosition - 1)`, (3) else (mid-block split or empty block without pending) → no continuation. After split, non-empty continuation styles are set on `newBlockId` via `setPendingStyles`. 15 test cases in `EnterContinuationTest.kt` covering: pending transfer, pending cleared on source, end-of-block inheritance (single style, multi-style, partial-styled text, unstyled text), mid-block no positional continuation, mid-block collapsed with pending (transfers), ranged selection with pending (no transfer), ranged selection at end of styled text (no transfer), split-at-0 no-transfer, empty block with/without pending, and pending-overrides-position-inheritance. Pending build verification.

### Subtask 10.3: Pure Formatting Calculator — DONE
**Files:**
- New `richtext/FormattingStateCalculator.kt` (internal)

Implement pure function from inputs → `FormattingState` per rules in section 10.4.

**Tests:** `FormattingStateCalculatorTest.kt`
- Collapsed caret + pending styles (non-empty and empty-set override)
- Collapsed caret fallback to `position - 1` (continuation)
- Collapsed caret at position 0 → empty styles
- Cursor at end of bold text → Bold is `FullyActive`
- Ranged selection mixed coverage → `Partial`
- Ranged selection full coverage → `FullyActive`
- No focus → `canFormat = false`
- Non-text block → `canFormat = false`
- Code block → `canFormat = false`
- Block selection active → `canFormat = false`
- Dragging → `canFormat = false`
- Reversed selection bounds handling

**Completed:** `FormattingStateCalculator` internal object with single `compute()` pure function. `canFormat` logic: requires focused text block (not Code), no block selection, not dragging. Collapsed caret: pending styles are canonical when non-null (including empty set override); otherwise falls back to `activeStylesAt(position - 1)` continuation; position 0 yields empty. Ranged selection: delegates to `queryStyleStatus` per tracked style, ignores pending. Selection bounds are normalized (handles reversed). 24 test cases in `FormattingStateCalculatorTest.kt` covering all listed scenarios plus multi-styled regions, metadata passthrough, and empty-set pending override. Pending build verification.

### Subtask 10.4: Reactive Observer Bridge — DONE
**Files:**
- New `richtext/FormattingStateObserver.kt`

Wire focused selection + spans + pending styles into calculator. Produces `State<FormattingState>`.

Uses `derivedStateOf` chains:
1. `focusedBlockId` derived from `stateHolder.state`
2. Block type derived from focused block
3. Selection derived from `TextFieldState`
4. Span + pending state derived from `BlockSpanStates`
5. Final `FormattingState` derived from calculator

Ensures: cursor-move-within-same-style does NOT trigger recomposition.

**Completed:** `rememberFormattingState` internal composable function in `FormattingStateObserver.kt`. Two-layer `derivedStateOf` chain: Layer 1 extracts EditorState-derived values (`focusedBlockId`, `focusedBlockType`, `hasBlockSelection`, `isDragging`) — each re-evaluates on every EditorState dispatch but only propagates when its specific output changes, shielding the final derivation from irrelevant state churn (drag position updates, block reordering, etc.). Layer 2 reads Layer 1 outputs plus per-block snapshot state (`TextFieldState.visibleSelection()`, `BlockSpanStates.getSpans()`, `BlockSpanStates.getPendingStyles()`) — only for the focused block — and delegates to `FormattingStateCalculator.compute()`. Output uses `FormattingState` structural equality so cursor movement within the same style region does NOT cause downstream recomposition. `trackedStyles` parameter allows the caller (CascadeEditor) to pass toolbar-relevant styles. Pending build verification.

### Subtask 10.5: Action Adapter — DONE
**Files:**
- New `richtext/DefaultFormattingActions.kt`

Resolve focused block + visible selection from runtime state **at invocation time** and delegate to `SpanActionDispatcher`. No-op if `canFormat == false`.

**Tests:** `DefaultFormattingActionsTest.kt`
- Toggle on ranged selection → delegates to dispatcher
- Toggle on collapsed cursor → toggles pending styles
- No-op when no focus
- No-op when Code block focused
- No-op when block selection active
- Action resolves fresh selection (not stale)

**Completed:** `DefaultFormattingActions` internal class implementing `FormattingActions`. All three methods (`toggleStyle`, `applyStyle`, `removeStyle`) resolve formatting context at invocation time via `resolveContext()` — reads `focusedBlockId` from `EditorStateHolder.state`, resolves block type, checks `canFormat` guards (supportsText, not Code, no block selection, not dragging), reads `visibleSelection()` from the focused block's `TextFieldState` — then delegates to `SpanActionDispatcher`. Returns null (no-op) for any disallowed context. Selection is passed through as-is (SpanAlgorithms normalize internally). 15 test cases in `DefaultFormattingActionsTest.kt` covering: ranged toggle apply/remove, collapsed cursor pending style toggle, no-op for no focus/Code block/block selection/dragging/non-text block, fresh selection resolution, applyStyle/removeStyle pass-through, and no-op guards for apply/remove. Pending build verification.

### Subtask 10.6: `CascadeEditor` Wiring + Layout Change — DONE
**Files:**
- Modify `ui/CascadeEditor.kt`
- New `ui/RichTextToolbar.kt` (placeholder for Subtask 10.7)

Changes:
1. Add `toolbar: ToolbarSlot = ToolbarSlot.Default()` parameter
2. Add `onFormattingStateChanged: ((FormattingState) -> Unit)? = null` parameter
3. Restructure layout: `Box` → `Column { Box(weight=1f) { ... }; Toolbar }`
4. Create observer + actions inside composition
5. Wire `State<FormattingState>` to toolbar slot
6. Wire `onFormattingStateChanged` callback via `LaunchedEffect`
7. Render Default/Custom/None toolbar based on slot

**Verify:** Drag gesture still works correctly with layout change. Auto-scroll still works. Drop indicator renders correctly.

**Completed:** `CascadeEditor` now accepts `toolbar: ToolbarSlot = ToolbarSlot.Default()` and `onFormattingStateChanged: ((FormattingState) -> Unit)? = null`. Layout restructured from root `Box` to `Column { Box(weight=1f, dragGesture) { LazyColumn + overlays }; Toolbar }` — toolbar is OUTSIDE the drag gesture Box. Formatting state computed via `rememberFormattingState()` with `trackedStyles` derived from `ToolbarSlot.Default` config buttons or `RichTextToolbarConfig.Default` for other slots. `DefaultFormattingActions` created with `stateHolder`, `blockTextStates`, and `spanActionDispatcher`. Toolbar slot `when`-dispatch renders `RichTextToolbar` for Default, invokes `content` lambda for Custom, no-op for None. External callback uses `rememberUpdatedState` + `LaunchedEffect(Unit)` + `snapshotFlow { formattingState.value }.collect {}` for structural-equality-deduped notifications without effect restarts on callback identity change. `RichTextToolbar.kt` created as placeholder (empty composable) for Subtask 10.7. Pending build verification.

### Subtask 10.7: Default Toolbar UI — DONE
**Files:**
- Modify `ui/RichTextToolbar.kt` (replace placeholder)

Implement config-driven toolbar rendering + state visuals.
- Horizontal scrollable row
- Toggle buttons with active/partial/absent/disabled states
- `focusProperties { canFocus = false }` on all buttons
- Accessibility content descriptions
- 44dp min touch target

**Completed:** `RichTextToolbar` renders a horizontally scrollable `Row` of toggle buttons driven by `RichTextToolbarConfig`. Each `ToolbarToggleButton` reflects `StyleStatus` visually: `FullyActive` = blue filled background + white text, `Partial` = semi-transparent blue background, `Absent` = transparent background, disabled (`canFormat == false`) = gray text + transparent background with clicks suppressed. Buttons use self-describing display text (B, I, U, S, <>, H) with matching text styles (bold B, italic I, underlined U, strikethrough S, monospace <>). All buttons have `focusProperties { canFocus = false }` to prevent stealing text field focus. 44dp min touch target via `sizeIn`. Accessibility content descriptions set via `semantics { contentDescription }` from `ToolbarButtonSpec.label`. Toolbar container has a top `HorizontalDivider` separator. `clickable` only applied when `enabled` to prevent disabled-state interactions. Pending build verification.

### Subtask 10.8: Block Type Conversion Span Policy
**Files:**
- Modify `action/EditorAction.kt` (`ConvertBlockType` reducer)
- Modify `DefaultBlockCallbacks` or wherever `ConvertBlockType` is dispatched (for runtime `BlockSpanStates` cleanup)

When converting to `Code`:
- Reducer: clear `BlockContent.Text.spans` in snapshot
- Runtime: clear `BlockSpanStates` for that block, clear pending styles

When converting between non-Code text types: preserve spans unchanged.

**Tests:** Conversion to Code strips spans, conversion back keeps empty, non-Code-to-non-Code preserves spans.

### Subtask 10.9: Tests + Integration Verification
**Files:**
- New `FormattingStateCalculatorTest.kt` (if not already done per subtask)
- New `DefaultFormattingActionsTest.kt` (if not already done per subtask)
- Extend `DefaultBlockCallbacks` split tests for continuation
- Extend `EditorStateTest.kt` for ConvertBlockType span policy

Additional integration tests:
- Observer produces correct state through focus/unfocus cycles
- Toolbar correctly reflects pending style state for empty block
- Toolbar disabled during drag
- `onFormattingStateChanged` callback fires on style change, not on same-style cursor move
- Enter at end of bold text → new block toolbar shows Bold active

---

## Undo/Redo Gap Documentation (Subtask 10.10)

**Files:**
- Update `ARCHITECTURE.md` — add note in Conventions or a new "Known Gaps" section

Document:
- `SpanActionDispatcher` dispatches `UpdateBlockContent` for snapshot sync
- Runtime `BlockSpanStates` is NOT part of the undo/redo snapshot chain
- Future undo system must either: (a) capture `BlockSpanStates` snapshots alongside `EditorState`, or (b) rebuild runtime state from undo'd snapshot
- Until then, formatting actions cannot be undone

---

## Expanded Gap List (V1 Decisions Locked)

| # | Gap | V1 Decision |
|---|-----|------------|
| 1 | Highlight color model | Single yellow (`0xFFFFEB3B`), exact match |
| 2 | Link UX | Excluded from default toolbar, no URL dialog |
| 3 | InlineCode exclusivity | Cumulative, no mutual exclusion |
| 4 | Cross-block selection | Toolbar disabled when block selection active |
| 5 | Toolbar placement | Bottom of editor, `Column` layout, outside drag `Box` |
| 6 | Disabled reason UX | Visual disabled state on buttons, not silent no-op |
| 7 | Accessibility/localization | Content descriptions on all controls from V1 |
| 8 | Block type conversion + spans | Strip on convert to Code; preserve for non-Code conversions |
| 9 | Undo/Redo interaction | Documented gap — runtime state not undo-captured |
| 10 | Toolbar focus stealing | `focusProperties { canFocus = false }` — must not take focus |
| 11 | Multi-block selection | Toolbar disabled, `canFormat = false` |
| 12 | Performance: same-style cursor move | `derivedStateOf` ensures no redundant recompose |
| 13 | External callback | `onFormattingStateChanged` parameter on `CascadeEditor` |
| 14 | `visibleSelection()` helper | New utility in `BackspaceAwareTextEdit.kt` |
| 15 | Drag-and-drop | Toolbar hidden/disabled (focus is null, `dragState != null`) |

---

## Test Matrix

1. Collapsed caret + pending styles (non-empty and empty-set override).
2. Collapsed caret fallback to `position - 1` (bold continuation at end of styled text).
3. Ranged selection mixed coverage → `Partial`.
4. Ranged selection full coverage → `FullyActive`.
5. No focus / non-text / Code block / block selection / dragging → `canFormat = false`.
6. Actions no-op for disallowed contexts.
7. Reversed selection bounds and out-of-range clamping.
8. Enter split continuation:
   - pending styles transferred to new block
   - end-of-block inheritance when no pending
   - ranged split does not transfer pending
   - empty block with pending → Enter → new block inherits
9. Default toolbar config extensibility (adding a new button spec is data-only).
10. Cursor at end of bold → types → text is bold, toolbar shows Bold active.
11. Empty block → tap Bold → type → text is bold.
12. `visibleSelection()` sentinel offset correctness.
13. ConvertBlockType to Code strips spans; non-Code preserves.
14. `onFormattingStateChanged` fires on actual change, not on same-style cursor move.
15. Toolbar hidden during drag-and-drop.
16. Toolbar buttons do not steal focus.

---

## Dependencies

- Reads: `EditorStateHolder.state`, `BlockTextStates`, `BlockSpanStates`
- Writes: `SpanActionDispatcher`, split callback continuation update, `ConvertBlockType` reducer update
- Layout change in `CascadeEditor` (Box → Column + Box)

---

## Non-Goals (Task 10)

- Floating selection toolbar
- Full link editing dialog
- Highlight palette/color picker
- Undo/redo controls (gap documented only)
- Keyboard shortcuts for formatting
- Cross-block text selection formatting
- Style-family matching for parametric styles