# CascadeEditor Architecture

Block-based editor (Craft/Notion-like) for Compose Multiplatform. Unidirectional data flow: actions → reducers → state → recomposition.

## Quick Reference

| Concept | File | Key Symbol |
|---------|------|------------|
| Main composable | `ui/CascadeEditor.kt` | `CascadeEditor(stateHolder, textStates, spanStates, registry, slashRegistry, ...)` |
| Text input | `ui/BackspaceAwareTextEdit.kt` | `BackspaceAwareTextField()` |
| Shared text field | `ui/renderers/TextBlockField.kt` | `TextBlockField()` |
| Text renderer | `ui/renderers/TextBlockRenderer.kt` | `TextBlockRenderer` |
| Block indentation rendering helper | `ui/renderers/BlockIndentationModifier.kt` | `withBlockIndentation()` (internal) |
| Indentation animation tokens | `ui/IndentationAnimation.kt` | `IndentationAnimation` (internal) |
| Ordered-list prefix formatter | `ui/renderers/OrderedListPrefixFormatter.kt` | `formatOrderedListPrefix()` (internal) |
| Todo renderer | `ui/renderers/TodoBlockRenderer.kt` | `TodoBlockRenderer` |
| Divider renderer | `ui/renderers/DividerBlockRenderer.kt` | `DividerBlockRenderer` |
| Unknown block renderer | `ui/renderers/UnknownBlockRenderer.kt` | `UnknownBlockRenderer` (internal, via `BlockRegistry.setUnknownBlockRenderer`) |
| Editor registry setup | `ui/EditorRegistry.kt` | `createEditorRegistry()` |
| Drop indicator | `ui/DropIndicator.kt` | `DropIndicator()` |
| Drag preview | `ui/DragPreview.kt` | `DragPreview()` |
| Block gestures (tap, drag, selection) | `ui/BlockGestureModifier.kt` | — |
| Auto-scroll | `ui/AutoScrollEffect.kt` | `AutoScrollDuringDrag()` |
| Drag hover utils | `ui/utils/DragUtils.kt` | `calculateDropTargetIndex()`, `resolveDepthAwareDragHoverTarget()`, `recomputeDepthAwareDragHoverTarget()` |
| Text state local | `ui/LocalBlockTextStates.kt` | `LocalBlockTextStates` |
| Span state local | `ui/LocalBlockSpanStates.kt` | `LocalBlockSpanStates` |
| State snapshot | `state/EditorState.kt` | `EditorState`, `DragState`, `SlashCommandState`, `SlashQueryRange` |
| Slash command ID | `slash/SlashCommandId.kt` | `SlashCommandId` |
| Slash command model | `slash/SlashCommandModel.kt` | `SlashCommandItem`, `SlashCommandAction`, `SlashCommandMenu`, `SlashCommandIconKey`, `SlashQueryTextPolicy`, `SlashCommandResult` |
| Slash command context | `slash/SlashCommandContext.kt` | `SlashCommandContext`, `SlashCommandEditor` |
| Slash command registry | `slash/SlashCommandRegistry.kt` | `SlashCommandRegistry` |
| State holder | `state/EditorStateHolder.kt` | `EditorStateHolder`, `rememberEditorState()` |
| History model | `state/EditorHistory.kt` | `HistoryManager`, `StructuralEntry`, `BlockTextEntry`, `EditorCheckpoint`, `EditingUiState` |
| History UI helpers | `state/EditorHistoryUiState.kt` | `captureFocusedEditingUiState()`, `restoreFocusedEditingUiState()` |
| Text history capture | `state/EditorTextHistory.kt` | `TextEditHistoryTracker`, `TextEditCoalescer` |
| Block-text history helpers | `state/EditorHistoryBlockText.kt` | `buildHistoryEntryFromCheckpoints()`, `applyBlockTextEntry()` |
| History checkpoint helpers | `state/EditorHistoryCheckpoint.kt` | `captureCheckpoint()`, `applyCheckpoint()` |
| Text state manager | `state/BlockTextStates.kt` | `BlockTextStates` |
| Span state manager | `state/BlockSpanStates.kt` | `BlockSpanStates` |
| All actions | `action/EditorAction.kt` | `sealed class EditorAction` |
| Block model | `core/Block.kt` | `Block`, factory methods |
| Block attributes | `core/BlockAttributes.kt` | `BlockAttributes`, indentation depth constants |
| Block types | `core/BlockType.kt` | `sealed interface BlockType` |
| Block content | `core/BlockContent.kt` | `sealed interface BlockContent` |
| Span style | `core/SpanStyle.kt` | `sealed interface SpanStyle` |
| Text span | `core/TextSpan.kt` | `TextSpan` |
| Block ID | `core/BlockId.kt` | `BlockId` |
| List utilities | `core/ListUtils.kt` | `renumberNumberedLists()` (internal) |
| Outline utilities | `core/OutlineUtils.kt` | `shiftIndentation()` (internal), `resolveDragPayload()` (internal), `moveDragPayload()` (internal), `IndentationDirection` (internal) |
| Registry | `registry/BlockRegistry.kt` | `BlockRegistry` |
| Descriptors | `registry/BlockDescriptor.kt` | `BlockDescriptor` |
| Built-in slash spec | `slash/BuiltInSlashCommandSpec.kt` | `BuiltInSlashCommandSpec`, `BuiltInBlockSlashBehavior` |
| Built-in slash factory | `slash/BuiltInSlashCommandFactory.kt` | `BuiltInSlashCommandFactory` |
| Built-in slash executor | `slash/SlashCommandExecutor.kt` | `createBuiltInSlashExecutor()` (internal) |
| Slash editor host | `slash/SlashCommandEditorHost.kt` | `SlashCommandEditorHost` (internal) |
| List auto-detect observer | `ui/observers/ListAutoDetectObserver.kt` | `ListAutoDetectObserver` (internal) |
| Slash text observer | `slash/SlashCommandTextObserver.kt` | `SlashCommandTextObserver` (internal) |
| Renderer interface | `registry/BlockRenderer.kt` | `BlockRenderer<T>` (+ `handlesSelectionVisual`), `BlockCallbacks`, `DefaultBlockCallbacks` |
| Unknown block type | `core/UnknownBlockType.kt` | `UnknownBlockType` (implements `CustomBlockType`) |
| Document serialization | `serialization/DocumentSchema.kt` | `DocumentSchema` (encode/decode full document) |
| Rich text serialization | `serialization/RichTextSchema.kt` | `RichTextSchema` |
| Doc serialization types | `serialization/BlockIdMode.kt` | `BlockIdMode` |
| Doc serialization types | `serialization/DuplicateIdMode.kt` | `DuplicateIdMode` |
| Doc serialization types | `serialization/CustomDataMode.kt` | `CustomDataMode` |
| Doc encode options | `serialization/DocumentEncodeOptions.kt` | `DocumentEncodeOptions` |
| Doc decode options | `serialization/DocumentDecodeOptions.kt` | `DocumentDecodeOptions` |
| Doc decode warnings | `serialization/DocumentDecodeWarning.kt` | `DocumentDecodeWarning` (sealed class) |
| Doc decode result | `serialization/DocumentDecodeResult.kt` | `DocumentDecodeResult` |
| Block type codec | `serialization/BlockTypeCodec.kt` | `BlockTypeCodec` |
| Block content codec | `serialization/BlockContentCodec.kt` | `BlockContentCodec` |
| Editor serialization ext | `serialization/DocumentSerializationExt.kt` | `EditorStateHolder.toJson()`, `EditorStateHolder.loadFromJson()` |
| Span algorithms | `richtext/SpanAlgorithms.kt` | `SpanAlgorithms`, `StyleStatus` |
| Span mapper | `richtext/SpanMapper.kt` | `SpanMapper` |
| Span edit observer | `richtext/SpanMaintenanceTextObserver.kt` | `SpanMaintenanceTextObserver` |
| Span action dispatcher | `richtext/SpanActionDispatcher.kt` | `SpanActionDispatcher` |
| Span dispatcher local | `ui/LocalSpanActionDispatcher.kt` | `LocalSpanActionDispatcher` |
| Formatting actions local | `ui/LocalFormattingActions.kt` | `LocalFormattingActions` |
| Indentation state | `indentation/IndentationState.kt` | `IndentationState` |
| Indentation actions | `indentation/IndentationActions.kt` | `IndentationActions` |
| Indentation state calculator | `indentation/IndentationStateCalculator.kt` | `IndentationStateCalculator` (internal), `rememberIndentationState()` (internal) |
| Indentation actions impl | `indentation/DefaultIndentationActions.kt` | `DefaultIndentationActions` (internal) |
| Indentation locals | `ui/LocalIndentationState.kt`, `ui/LocalIndentationActions.kt` | `LocalIndentationState`, `LocalIndentationActions` |
| Keyboard handler | `ui/renderers/TextBlockKeyHandler.kt` | `TextBlockKeyHandler` |
| Formatting state | `richtext/FormattingState.kt` | `FormattingState` |
| Formatting actions | `richtext/FormattingActions.kt` | `FormattingActions` |
| Toolbar slot | `ui/ToolbarSlot.kt` | `ToolbarSlot` |
| Toolbar config | `ui/RichTextToolbarConfig.kt` | `RichTextToolbarConfig`, `ToolbarButtonSpec` |
| Default toolbar UI | `ui/RichTextToolbar.kt` | Formatting buttons plus indent/outdent buttons |
| Formatting calculator | `richtext/FormattingStateCalculator.kt` | `FormattingStateCalculator` |
| Formatting observer | `richtext/FormattingStateObserver.kt` | `rememberFormattingState()` |
| Formatting actions impl | `richtext/DefaultFormattingActions.kt` | `DefaultFormattingActions` |
| Hide keyboard button | `ui/HideKeyboardToolbarButton.kt` | `HideKeyboardToolbarButton()` (public, iOS-only in default toolbar) |
| Platform detection | `Platform.kt` | `internal expect val isIos: Boolean` |
| Slash popup defaults | `ui/SlashPopupDefaults.kt` | `SlashPopupDefaults` |
| Slash popup overlay | `ui/SlashCommandPopup.kt` | `SlashCommandPopup()` |
| Slash command row | `ui/SlashCommandRow.kt` | `SlashCommandRow()` |
| Slash caret rect local | `ui/LocalSlashCaretRect.kt` | `LocalSlashCaretRect`, `SlashCaretRectHolder` |
| Slash registry local | `ui/LocalSlashCommandRegistry.kt` | `LocalSlashCommandRegistry` |
| Slash popup items local | `ui/LocalSlashPopupItems.kt` | `LocalSlashPopupItems` |
| Theme colors | `theme/CascadeEditorColors.kt` | `CascadeEditorColors` |
| Theme typography | `theme/CascadeEditorTypography.kt` | `CascadeEditorTypography` |
| Theme dimensions | `theme/CascadeEditorDimensions.kt` | `CascadeEditorDimensions` |
| Theme top-level | `theme/CascadeEditorTheme.kt` | `CascadeEditorTheme` |
| Theme local | `theme/LocalCascadeTheme.kt` | `LocalCascadeTheme` |
| UI strings | `theme/CascadeEditorStrings.kt` | `CascadeEditorStrings` |
| Block strings | `theme/CascadeEditorBlockStrings.kt` | `CascadeEditorBlockStrings`, `BlockLocalizedStrings` |
| Strings locals | `theme/LocalCascadeStrings.kt` | `LocalCascadeStrings`, `LocalCascadeBlockStrings` |

All paths relative to `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/`.

## Layer Diagram

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (CascadeEditor, renderers, drag overlays)     │
├─────────────────────────────────────────────────────────┤
│  Text State Layer (BlockTextStates, TextFieldState)     │
├─────────────────────────────────────────────────────────┤
│  State Layer (EditorState, EditorStateHolder)           │
├─────────────────────────────────────────────────────────┤
│  Action Layer (EditorAction sealed hierarchy)           │
├─────────────────────────────────────────────────────────┤
│  Registry Layer (BlockRegistry, BlockDescriptor)        │
├─────────────────────────────────────────────────────────┤
│  Core Layer (Block, BlockType, BlockContent, TextSpan)   │
└─────────────────────────────────────────────────────────┘
```

## Core Concepts

**Block** = id (`BlockId`) + type (`BlockType`) + content (`BlockContent`) + attributes (`BlockAttributes`). Factory methods: `Block.paragraph()`, `Block.heading()`, `Block.todo()`, `Block.bulletList()`, `Block.numberedList()`, `Block.divider()`.

**BlockAttributes** — block-level document metadata that is independent of `BlockType`. V1 stores `indentationLevel` with default `0` and validates persisted depth against the module-internal indentation range (`0..3`). `Block.attributes` is a trailing constructor property with `BlockAttributes.Default`, so existing `Block(id, type, content)` call sites remain source-compatible. Use `Block.withAttributes(...)` or `copy(attributes = ...)` when changing attributes. Structural reducers preserve indentation when the resulting block type supports indentation and clear indentation to `0` when converting into unsupported block types.

**Document serialization** — `DocumentSchema.CURRENT_VERSION` is `2`. Version 2 can write a block-level `attributes.indentationLevel` field; default depth `0` is omitted on encode, and unsupported block types always encode as unindented. Version 1 and missing-version documents decode with default attributes when the field is absent. Malformed, non-integer, out-of-range, or structurally invalid indentation emits `DocumentDecodeWarning.InvalidBlockAttributeParam` and falls back to the normalized depth. Decode clears indentation on block types that do not support indentation, normalizes the flat outline, then runs depth-aware numbered-list renumbering after attributes are applied.

**BlockType** — sealed interface:

| Type | Supports Text | Supports Indentation | Notes |
|------|:---:|:---:|-------|
| `Paragraph` | Yes | Yes | Default block type |
| `Heading(level)` | Yes | No | H1-H6 |
| `Todo(checked)` | Yes | Yes | Has `checked` boolean |
| `BulletList` | Yes | Yes | |
| `NumberedList(number)` | Yes | Yes | Has `number` int (>= 1, default 1) |
| `Quote` | Yes | No | |
| `Divider` | No | No | |

Custom blocks: implement `CustomBlockType` interface.

**BlockContent** — `Text(text, spans)` | `Empty` | `Custom(typeId, data)`.

**Numbered list renumbering** — `renumberNumberedLists()` stores numbering in `BlockType.NumberedList(number)` and recomputes it after structural changes. The algorithm is outline-aware over the flat `List<Block>` model: sequences are scoped by indentation depth and derived parent, deeper descendants do not break ancestor-depth numbering, same-depth non-numbered supported blocks reset the sequence for their own derived parent, and unsupported blocks reset the outline segment entirely. Unchanged blocks preserve referential identity, and the original list instance is returned when no numbers need changing.

**Indent/outdent reducers** — `IndentForward` and `IndentBackward` are structural actions over the flat outline model. Selection takes precedence over focus; selected unsupported blocks are ignored; selected supported descendants of another selected supported root are shifted once through the ancestor subtree. Focus mode targets only the focused supported block. Unsupported blocks are hard outline boundaries: they do not own descendants, terminate preceding supported subtrees, and reset the nearest-supported-depth validation segment. Successful shifts update supported target roots plus supported descendants, validate first-block/root-gap/max-depth rules, preserve focus and selection, normalize structural reducers that can orphan descendants, and then renumber ordered lists.

**Subtree drag payloads** — `StartDrag` resolves a semantic payload from the flat outline before any blocks move. Single-block drags use the touched block as the root and include its full subtree. Selection drags use selected roots that are not descendants of another selected root, then include each root subtree once. `DragState.payloadBlockIds` is the ordered payload contract; `payloadBlockIdSet`, `payloadBlockIndices`, `payloadBlockIndexSet`, and `payloadIndexRanges` cache membership and original ranges for frame-rate hover resolution. `draggingBlockIds` remains a legacy containment set for existing UI checks. `DragState.primaryRootId` is the root that contains the block that started the gesture, even when another selected root appears earlier in document order. `resolveDepthAwareDragHoverTarget(...)` resolves both the visual gap and `futureRootIndentationLevel`; invalid gaps clear `targetIndex` so `CompleteDrag` cannot mutate the document. Unsupported primary roots keep future depth `0` regardless of horizontal drag movement.

**Subtree drag moves** — `MoveDragPayload` is the drag-specific structural reducer. It removes payload blocks, inserts them at the visual gap after removal, applies one primary-root depth delta to every payload block, validates the resulting outline, and renumbers ordered lists. Drops into a gap inside the payload are rejected by returning the original block list. `MoveBlocks` remains the flat reorder primitive for non-drag callers and does not perform drag-style depth rewriting, but structural normalization can still adjust invalid outlines after the move. `convertVisualGapToMoveBlocksIndex(...)` remains public as a deprecated compatibility shim for legacy flat drag integrations.

**Public indentation API** — custom chrome reads `LocalIndentationState` (`State<IndentationState>?`) and `LocalIndentationActions` (`IndentationActions?`) from inside `CascadeEditor`. `IndentationState.targetBlockIds` contains supported root targets in document order, not moved descendants. `IndentationStateCalculator` is internal and pure; it reuses reducer target resolution plus `shiftIndentation(...)` validation so enablement matches the actual `IndentForward`/`IndentBackward` reducers. `DefaultIndentationActions` evaluates state at invocation time and dispatches indentation through the structural history wrapper when runtime history holders are bound. `ToolbarSlot.Custom` stays source-compatible; indentation is intentionally exposed through locals rather than new lambda parameters.

**Theme dimensions and indentation animation** — `CascadeEditorTheme` combines colors, typography, and `CascadeEditorDimensions`. `CascadeEditorDimensions.indentUnit` is the shared visual depth unit used by supported block renderers, the drop indicator, and drag preview, while `blockHorizontalPadding` is the normal editor-list horizontal padding token. `IndentationAnimation` centralizes the 150 ms FastOutSlowIn tween used by normal indentation, drop indicator depth/Y movement, and drag preview badge indentation. Normal rendering animates supported block indentation by applying `indentationLevel * indentUnit` as a leading inset; unsupported block types ignore hidden indentation during rendering.

**TextSpan** — `TextSpan(start, end, style)` with half-open `[start, end)` visible coordinates. Validates `start >= 0` and `end >= start`.

**SpanStyle** — sealed interface: `Bold`, `Italic`, `Underline`, `StrikeThrough`, `InlineCode`, `Highlight(colorArgb)`, `Custom(typeId, payload?)`. `Custom.payload` is opaque `String?` (raw JSON); core layer must not parse it.

## State Management

**EditorState** — immutable snapshot: `blocks`, `focusedBlockId`, `selectedBlockIds`, `dragState`, `slashCommandState`. Cursor position is NOT in EditorState — it lives in `TextFieldState` managed by `BlockTextStates`. **Invariant:** `focusedBlockId` and `selectedBlockIds` are mutually exclusive — enforced by reducers, not UI code. Selection reducers clear focus; focus reducers (with non-null target) clear selection. `ClearFocus` and `ClearSelection` are orthogonal and do not enforce this on each other.

**EditorStateHolder** — Compose-friendly mutable wrapper. Use `rememberEditorState(initialBlocks)` to create. Call `stateHolder.dispatch(action)` to modify state. Undo/redo history is owned internally by the holder in v1 and exposed via `canUndo`, `canRedo`, `undo()`, and `redo()`. The finalized v1 model is hybrid and linear: `BlockTextEntry` is used only for strict one-block text/span deltas, while `StructuralEntry` is used for semantic or multi-block document changes and replays through full checkpoints. Direct external `dispatch(action)` calls still bypass history unless they are routed through a history-aware integration point. `setState(...)` and `loadFromJson(...)` are hard replacement paths and clear history. When rendered through `CascadeEditor`, the holder is internally bound to the live `BlockTextStates` / `BlockSpanStates` instances so replay can choose the correct scope per entry type: `StructuralEntry` clears runtime holders, replaces snapshot state, rebuilds runtime text/span state for current text blocks, restores block selection when there is no focused block, and then restores focused text selection plus focused pending styles; `BlockTextEntry` patches only the target block snapshot/runtime text/runtime spans plus the same replayable UI state. Structural checkpoints include block selection because structural commands such as subtree drag are selection-defined; they still exclude slash UI and active drag state. Live typing capture currently happens in `TextBlockField` through `TextEditHistoryTracker`, which promotes committed one-block text edits into history after span maintenance and coalesces only caret-preserving insert/backspace/delete-forward batches within `500ms`; focus, selection, paste-like, programmatic, replay, and externally triggered formatting commands reset that local batch state through holder-registered tracker hooks. Structural edit sources now route through holder-owned transaction helpers that always push a forced `StructuralEntry`, break all open typing batches before capture, and re-anchor surviving trackers to the post-transaction checkpoint so later typing compares against the new structural baseline. Custom runtime-holder instances should remain stable for the lifetime of the bound holder; the default remembered instances already satisfy that contract.

**BlockTextStates** — single source of truth for text content. One `TextFieldState` per block. Key methods: `getOrCreate()`, `getVisibleText()`, `getSelection()`, `mergeInto()`, `setText()`, `setSelection()`, `replaceVisibleRange()`, `consumeProgrammaticCommit()`, `extractAllText()`, `cleanup()`. Programmatic text mutations (`mergeInto` / `setText`) register per-block expected committed text so `SpanMaintenanceTextObserver` can skip/rebase non-user commits and avoid duplicate span adjustment. Programmatic selection restore uses visible-text coordinates through `getSelection()` / `setSelection()` so history replay does not need to reason about the internal ZWSP sentinel. Internal observers (like `SlashCommandTextObserver`) can also perform a non-destructive pending-commit peek when needed without consuming the authoritative span observer entry. Provided to renderers via `LocalBlockTextStates` CompositionLocal.

**BlockSpanStates** — single source of truth for rich text spans during editing. One `MutableState<List<TextSpan>>` per block plus snapshot-aware pending-style state. Key methods: `getOrCreate(..., textLength)`, `getSpans()`, `set(..., textLength)`, `adjustForUserEdit()`, `split()`, `mergeInto()`, `applyStyle()`, `removeStyle()`, `toggleStyle()`, `queryStyleStatus()`, `activeStylesAt()`, `resolveStylesForInsertion()`. Invariants are enforced at API ingress (`getOrCreate` / `set`) by normalizing and clamping spans with current visible text length. Created and remembered in `CascadeEditor`, cleaned up in `LaunchedEffect(state.blocks)` with text-only IDs (`collectTextBlockIds`) to prevent stale span state on non-text transitions, and provided to renderers via `LocalBlockSpanStates` CompositionLocal. Per-block span state is initialized in `TextBlockRenderer` from `BlockContent.Text.spans`. Rendering is applied through a stable per-block `BasicTextField` `outputTransformation` that reads latest spans at render time via `SpanMapper.applyStyles(...)` with defensive clamping in visible coordinates. User-edit span maintenance runs post-commit via `SpanMaintenanceTextObserver`, which consumes/rebases programmatic commit baselines from `BlockTextStates` before applying diff-based user edit maintenance. Programmatic split/merge runtime transfer is executed in `DefaultBlockCallbacks`, and `mergeInto(...)` clears pending styles on both source and target to avoid pending-style bleed after merge. External formatting operations (toolbar, keyboard shortcuts) should use `SpanActionDispatcher` (provided via `LocalSpanActionDispatcher`) which coordinates runtime `BlockSpanStates` update (immediate visual) with full snapshot sync via `UpdateBlockContent` (avoids stale-text-length mismatch). Collapsed-cursor `toggleStyle` toggles pending styles instead of applying zero-width spans. `ApplySpanStyle`/`RemoveSpanStyle` actions are snapshot-only and should not be dispatched directly during active editing. Snapshot span reducers use the same `SpanAlgorithms` normalization contract as runtime for canonical output. `SplitBlock` accepts `newBlockSpans` parameter for runtime-provided spans and always updates source block snapshot. `MergeBlocks` reducer merges snapshot spans alongside text. `DefaultBlockCallbacks` syncs merged text+spans to snapshot via `UpdateBlockContent` before `DeleteBlock` dispatch on merge paths. `UpdateBlockText` explicitly resets spans (callers needing span preservation use `UpdateBlockContent`).

> **Why not sync text via LaunchedEffect?** Causes cursor jumps, race conditions, and double-init. `BlockTextStates` avoids all of this by owning the `TextFieldState` directly.

## Action System

All state changes go through `EditorAction.reduce(state) → newState`.

**Block Manipulation:** `InsertBlock`, `InsertBlockAfter`, `DeleteBlocks`, `DeleteBlock`, `UpdateBlockContent`, `UpdateBlockText`, `ConvertBlockType`, `MoveBlocks`, `MoveDragPayload`, `IndentForward`, `IndentBackward`, `MergeBlocks`, `SplitBlock`, `ReplaceBlock`, `ToggleTodo`

**Span Styles:** `ApplySpanStyle`, `RemoveSpanStyle`

**Selection:** `SelectBlock`, `ToggleBlockSelection`, `SelectBlockRange`, `AddBlockRangeToSelection`, `ClearSelection`, `SelectAll`, `DeleteSelectedOrFocused`

**Focus:** `FocusBlock`, `FocusNextBlock`, `FocusPreviousBlock`, `ClearFocus`

**Drag & Drop:** `StartDrag`, `UpdateDragTarget`, `MoveDragPayload`, `CompleteDrag`, `CancelDrag`

**Slash Commands:** `OpenSlashCommand`, `UpdateSlashCommandSession`, `NavigateSlashSubmenu`, `NavigateSlashBack`, `HighlightSlashCommand`, `CloseSlashCommand`

## Data Flow

**Standard flow:** User Input → `BlockCallbacks` → `EditorAction` → `dispatch()` → `reduce()` → recomposition.

**Text operations (merge/split):** `BlockCallbacks` performs runtime transfer first (`BlockTextStates` + `BlockSpanStates`) and then dispatches block-structure actions. `onEnter` returns without mutation while block selection is active; otherwise it routes empty `BulletList`, `NumberedList`, and `Todo` blocks at depth greater than `0` to `IndentBackward` before splitting. Empty root `BulletList` and `NumberedList` blocks exit to `Paragraph`; empty root `Todo` blocks keep the normal split-continuation path and create a new unchecked `Todo`. Split paths generate `newBlockId` only when splitting and pass runtime payload (`newBlockSpans`, `sourceBlockText`, `sourceBlockSpans`) into `SplitBlock` for deterministic runtime/snapshot alignment. `onBackspaceAtStart` returns without mutation while block selection is active, then routes any supported block at depth greater than `0` to `IndentBackward` before root-level un-list or merge handling. Merge flows use captured pre-merge target length from `BlockTextStates.mergeInto(...)` to shift source spans exactly once, then sync merged content to snapshot via `UpdateBlockContent` before dispatching `DeleteBlock`. `SplitBlock` and `MergeBlocks` reducers split/merge snapshot spans using `SpanAlgorithms` for snapshot consistency. `SplitBlock` carries supported source indentation to supported continuation blocks; merge keeps the target block's attributes authoritative.

**Live typing history:** `TextBlockField` observes committed `TextFieldState` snapshots directly. For user text edits, the pipeline is: slash observer -> span maintenance -> checkpoint-based history capture/coalescing -> list auto-detect. Programmatic commits bypass that capture path by re-anchoring the local tracker from a full checkpoint. Replay also bypasses capture, but block-local undo/redo now re-anchors the registered tracker directly from the replay payload while structural replay recreates trackers by clearing and rebuilding runtime holders, avoiding replay-time full-document checkpoint churn inside `TextBlockField`. Structural commands also terminate live typing explicitly through the holder transaction wrapper before they mutate document shape or block type, so sequences like `type -> split` stay in separate undo steps.

**Style formatting:** External code uses `SpanActionDispatcher` (via `LocalSpanActionDispatcher`) which first updates runtime `BlockSpanStates` (immediate visual), then syncs snapshot via `UpdateBlockContent` (full text + spans). When the dispatcher is constructed with `EditorStateHolder` it also captures isolated history for selected-range formatting via before/after checkpoints and resets the registered text-history tracker for the target block so formatting never merges into surrounding typing. Collapsed-cursor toggle still updates pending styles without a standalone history push, but it refreshes the block-local history baseline so later entries can replay the correct pending-style state.

**Indentation commands:** External custom toolbar code uses `LocalIndentationActions.current?.indentForward()` / `indentBackward()` and `LocalIndentationState.current?.value` for enablement. The public actions no-op when their corresponding state capability is false. The built-in default toolbar renders indent/outdent icon buttons using the same state/actions and localized `CascadeEditorStrings.indentForward` / `indentBackward` accessibility labels. There is no `onIndentationStateChanged` callback in v1.

**Structural transactions:** Built-in semantic document edits now capture explicit structural transactions instead of relying on raw reducer dispatch. The current in-scope boundaries are split/merge in `DefaultBlockCallbacks`, slash command execution in `SlashCommandExecutor`, list auto-detect conversion in `TextBlockField`, and single-action structural dispatches such as `ToggleTodo`, `CompleteDrag`, `DeleteSelectedOrFocused`, `IndentForward`, and `IndentBackward`. Accepted structural entries replay through full-document checkpoints even when the before/after delta would otherwise fit the one-block text predicate; no-op entries are dropped by the history stack.

## Registry System

**BlockRegistry** — maps `typeId` string to `BlockDescriptor` (metadata + factory) and `BlockRenderer` (UI). Use `registry.search(query)` for slash command filtering. Use `registry.getRenderer(blockType)` for rendering (includes unknown-block fallback) or `registry.getRenderer(typeId)` for direct lookup. `setUnknownBlockRenderer()` registers a fallback renderer for `UnknownBlockType` blocks. `createEditorRegistry()` pre-registers all built-in types: `TodoBlockRenderer` for "todo", `TextBlockRenderer` for all other text-supporting types, and `UnknownBlockRenderer` as the unknown-block fallback. All text-editing renderers share the `TextBlockField` composable for text input, spans, and focus.

**BlockRenderer** — `Render(block, isSelected, isFocused, modifier, callbacks)`. Property `handlesSelectionVisual` (default `false`) opts out of the wrapper-level selection overlay; when `true` the renderer is fully responsible for its own selection chrome using `isSelected`.

**BlockCallbacks** — interface passed to renderers for interaction handling. `DefaultBlockCallbacks` wires `onEnter` → selection-mode no-op, nested empty list/todo outdent, root list exit, or split; `onBackspaceAtStart` → selection-mode no-op, nested supported-block outdent, root un-list, or merge; `onDeleteAtEnd` → forward-merge, `onDragStart` → drag initiation, `onSlashCommand` → open menu. Stubs: `onClick`, `onLongClick`.

## Conventions

- **Explicit API mode** — all public declarations need explicit `public`/`internal` visibility
- **`@Immutable` data classes** for state objects
- **`internal`** for implementation details, **`public`** for API surface
- **New actions** must be a data class/object extending `EditorAction` with a `reduce()` override
- **Renderers** access text via `LocalBlockTextStates.current`, never from `BlockContent` directly during editing
- **High-frequency updates** (drag position, scroll) use `mutableFloatStateOf` locally, NOT in `EditorState`
- **Performance**: prefer `graphicsLayer { }` lambdas over Modifier params for draw-phase-only changes (e.g., alpha, translationY)
- **Drag gesture** lives on the Box wrapper, NOT on LazyColumn items (survives recycling)
- **Auto-scroll** uses `dispatchRawDelta` to avoid MutatorMutex contention with gesture scroll
- **Tests** go in `editor/src/commonTest/`. Run: `./gradlew :editor:allTests`

## Implementation Status

| Feature | Status | Notes |
|---------|:------:|-------|
| Core architecture (Block, State, Actions) | Done | |
| Text editing (split, merge, cursor) | Done | |
| Focus management | Done | |
| Selection (single, multi, range) | Done | Actions done with focus/selection mutual exclusivity invariant; UI triggers partial (`onClick` is a stub); wrapper-level selection overlay with `handlesSelectionVisual` opt-out |
| Drag & drop (gesture, preview, indicator, auto-scroll) | Done | Subtree-aware payloads, depth-aware hover, lane-aligned indicator, preview indentation, and payload badge |
| Block registry & search | Done | |
| TextBlockRenderer | Done | All text-supporting types except todo |
| TextBlockField (shared) | Done | Extracted text editing composable used by all text renderers |
| Heading font sizes | Done | No bold weight yet |
| Slash commands (backend) | Done | Session state with query range, submenu nav, highlight; enriched reducer API; `BuiltInSlashCommandSpec` on descriptors with `ConvertInPlace`/`AlwaysInsert` behavior policies; `BuiltInSlashCommandFactory` generates `SlashCommandAction`s from descriptor metadata; `SlashCommandEditorHost` provides safe runtime/snapshot editing; `BlockTextStates.replaceVisibleRange()` + `BlockSpanStates.adjustForRangeReplacement()` primitives; `CascadeEditor` exposes public `slashRegistry` parameter for consumer custom commands |
| Slash commands (integration) | Done | `shouldInvalidateSlashSession()` closes session on drag, selection, or anchor deletion; reactive `LaunchedEffect` + `snapshotFlow` wiring in `CascadeEditor` |
| Slash commands (text observer) | Done | `SlashCommandTextObserver` detects `/`, tracks `queryRange`, dismisses on invalid state; wired in `TextBlockField` via combined text+selection `snapshotFlow` |
| Slash commands (UI) | Done | Popup overlay with grouped items, caret-relative positioning, keyboard nav (Up/Down/Enter/Escape), auto-highlight, submenu back-nav, `focusProperties { canFocus = false }` pattern |
| Todo checkbox UI | Done | `TodoBlockRenderer` with `Checkbox` + `TextBlockField`, `ToggleTodo` action |
| Bullet/numbered list prefixes | Done | `TextBlockRenderer` wraps list types in `Row` with non-editable prefix gutter (`•` / depth-formatted ordered prefixes) |
| List auto-detection | Done | `ListAutoDetectObserver` detects `- ` and `N. ` triggers, converts block type, removes prefix text |
| List enter/backspace behavior | Done | Empty nested list/todo Enter outdents, root empty lists exit to Paragraph, Backspace at nested supported blocks outdents before root un-list/merge behavior |
| Quote visual styling | Done | Left border (3dp) + background tint; `quoteBorder`/`quoteBackground` color slots |
| Divider renderer | Done | `DividerBlockRenderer` — horizontal line, 1dp, vertical padding |
| Rich text spans — domain model | Done | `TextSpan`, `SpanStyle`, `BlockContent.Text.spans` |
| Rich text spans — algorithms | Done | `SpanAlgorithms`: normalize, adjust, split/merge, apply/remove/toggle, query |
| Rich text spans — runtime holder | Done | `BlockSpanStates` + `LocalBlockSpanStates`, strict ingress normalization/clamping |
| Rich text spans — lifecycle wiring | Done | `BlockSpanStates` provided in `CascadeEditor`, per-block init in `TextBlockRenderer`, text-only cleanup guard |
| Rich text spans — rendering | Done | `OutputTransformation` path wired in `TextBlockRenderer` via `SpanMapper` |
| Rich text spans — edit maintenance | Done | Implemented via committed visible-text observer (`SpanMaintenanceTextObserver`) + `BlockSpanStates.adjustForUserEdit`/pending continuation style application |
| Rich text spans — programmatic split/merge/setText sync | Done | Programmatic commit signaling in `BlockTextStates`, observer consume/rebase path, deterministic `SplitBlock.newBlockId`, callback-side span transfer for split/merge |
| Rich text spans — public actions & snapshot sync | Done | `ApplySpanStyle`/`RemoveSpanStyle` actions, `SpanActionDispatcher`, `SplitBlock`/`MergeBlocks` reducers preserve spans, `UpdateBlockText` explicit reset policy |
| Text transformation panel | Not done | |
| Block anchor / action menu | Not done | |
| Serialization — rich text spans | Done | `RichTextSchema` encode/decode with version switch |
| Serialization — doc foundation types | Done | Enums, options, warnings, codecs, `UnknownBlockType` |
| Serialization — full document | Done | `DocumentSchema` encode/decode, `EditorStateHolder.toJson()`/`loadFromJson()` extensions |
| Undo / Redo | Done | Hybrid linear history is finalized: `BlockTextEntry` handles strict one-block text/span edits, `StructuralEntry` handles semantic or multi-block changes, public `canUndo`/`canRedo`/`undo()`/`redo()` API is live, and Cmd/Ctrl+Z plus Shift+Cmd/Ctrl+Z are implemented |
| Theming / styling API — data models | Done | `CascadeEditorTheme`, `CascadeEditorColors`, `CascadeEditorTypography`, `LocalCascadeTheme`; light/dark presets |
| Theming / styling API — color migration | Done | All UI colors read from `LocalCascadeTheme.current.colors` |
| Theming / styling API — typography migration | Done | All UI typography reads from `LocalCascadeTheme.current.typography` |
| Localization — data models | Done | `CascadeEditorStrings`, `CascadeEditorBlockStrings`, `BlockLocalizedStrings`, `LocalCascadeStrings`, `LocalCascadeBlockStrings` |
| Localization — UI string migration | Done | `SlashCommandPopup`, `UnknownBlockRenderer`, `RichTextToolbar` read from `LocalCascadeStrings` |
| Localization — slash command system | Done | `BuiltInSlashCommandFactory.generate()` accepts `CascadeEditorBlockStrings?` for localized titles/descriptions/keywords |
| Block nesting / indentation | Done | Core `BlockAttributes` metadata, outline-aware numbered renumbering, reducer actions, editing callbacks, serialization, public state/actions, toolbar buttons, rendering, and depth-aware drag UI are implemented |
| Multi-block drag | Done | Selection drags resolve selected roots, include full subtrees once, complete as one atomic reorder/reindent operation, and render a payload-count preview badge |
| Keyboard shortcuts — formatting | Done | Cmd+B/I/U (macOS) / Ctrl+B/I/U (other) via `onPreviewKeyEvent` in `TextBlockField` + `LocalFormattingActions` |
| Keyboard shortcuts — other | Partial | Undo/redo history shortcuts are implemented alongside formatting shortcuts; broader non-formatting shortcut coverage is still open |
| iOS keyboard dismiss | Done | `HideKeyboardToolbarButton` pinned to trailing toolbar edge, iOS-only via `isIos` expect/actual; dispatches `ClearFocus` |

## Known Gaps

| # | Area | Constraint |
|---|------|-----------|
| 1 | **History Capture Boundary** | Direct raw `EditorStateHolder.dispatch(...)` still bypasses history by contract. Built-in structural commands must route through the holder transaction helpers (or another history-aware boundary) if they need undo/redo coverage. |

## Testing

| Test File | Coverage |
|-----------|----------|
| `EditorStateTest.kt` | All action reducers incl. span actions, split/merge span transfer, snapshot stability (~87 tests) |
| `SlashCommandStateTest.kt` | Slash session reducers: open/update/navigate/highlight/close, submenu path, no-op guards |
| `SlashCommandRegistryTest.kt` | Registry: registration order, dedup, ranking tiers, path-based submenu search, menu discoverability, tie-breaking |
| `DragActionsTest.kt` | Drag state transitions, subtree payload resolution, depth-aware target clearing, self-drop rejection, depth rewrite, drag renumbering |
| `DragSelectionTest.kt` | `isDropAtOriginalPosition` boundary cases for long-press-to-select detection |
| `BlockSelectionIntegrationTest.kt` | Block selection workflows: enter/exit selection, multi-select, delete selected, insertion preserves selection, slash invalidation, full lifecycle (11 scenarios) |
| `AutoScrollTest.kt` | Hot zones, speed calculation |
| `DragUtilsTest.kt` | Drop target coordinate math, depth-aware hover clamps, invalid payload gaps, drop indicator geometry, drag preview badge text |
| `BuiltInSlashCommandFactoryTest.kt` | Factory filtering, ID stability, metadata copying, icon resolution, behavior preservation via recording executor, deterministic ordering, registry integration |
| `BlockTextStatesTest.kt` | Range replacement (middle/start/end/full), deletion, missing block, clamping, programmatic commit tracking, cursor positioning, `hasPendingProgrammaticCommit` peek semantics |
| `SlashCommandEditorHostTest.kt` | replaceQueryText (removal, replacement, span preservation, snapshot sync), updateAnchorText, replaceAnchorBlock (id preservation, focus), insertBlockAfterAnchor (ordering, focus), focusBlock, closeMenu, graceful no-ops for missing anchors |
| `SlashCommandTextObserverTest.kt` | Session opening (start/middle/empty, non-slash, deletion, replacement), updating (progressive, spaces), closing (slash deletion, cursor outside range, focus lost), programmatic changes (skip, preserve, remove), paste/multi-char excluded, notifySessionClosed, range shifting (insert/delete before slash), within-range edits, after-range cursor, identical no-op, successive open-after-close (~30 tests) |
| `BlockRegistryTest.kt` | Descriptor search, block creation, slash metadata exposure, behavior policies per built-in type |
| `BlockTest.kt` | Core block creation, `BlockAttributes` defaults/copying/range validation, indentation support matrix, `NumberedList` type validation |
| `ListUtilsTest.kt` | `renumberNumberedLists`: outline-aware depth/parent sequences, same-depth breaks, parent continuation across descendants, referential equality |
| `ListAutoDetectObserverTest.kt` | Bullet trigger (dash+space), numbered trigger (N.+space), no-trigger guards (mid-text, already-list, paste, programmatic, zero, deletion, replacement) |
| `ListIntegrationTest.kt` | Multi-step list scenarios: auto-detect→enter→sequential numbers, delete middle→renumber, empty-enter exit→paragraph+renumber, backspace un-list→run split, move blocks→both runs renumber, mid-text split with spans, full lifecycle |
| `IndentationEditingIntegrationTest.kt` | Depth-aware Enter/Backspace callback behavior: same-depth split continuation, nested empty list/todo outdent, root list exit, root todo continuation, selection-mode callback no-ops, indented paragraph split, backspace outdent without merge, renumbering after outdent |
| `UnknownBlockTypeTest.kt` | UnknownBlockType properties (supportsText, isConvertible, displayName, rawTypeJson), registry getRenderer unchanged |
| `DocumentSchemaEncodeTest.kt` | Document encode: envelope/version, block attributes, all built-in types, content kinds, custom data, codec hooks, UnknownBlockType re-emit |
| `DocumentSchemaDecodeTest.kt` | Document decode: round-trips, version guard, block attributes, heading/todo/numbered defaults, ID modes, malformed blocks, codecs, depth-aware renumbering, warnings |
| `DocumentSerializationExtTest.kt` | Editor integration: toJson runtime/snapshot resolution with attributes, loadFromJson state replacement with attributes, runtime clearing, codec pass-through |
| `RichTextSchemaTest.kt` | Span serialization round-trips, normalization, version handling |
| `SpanAlgorithmsTest.kt` | Normalize, edit adjust, split/merge, apply/remove/toggle, style queries (~62 tests) |
| `BlockSpanStatesTest.kt` | Lifecycle, edit adjustment, split/merge transfer, style ops, queries, pending styles, aliasing/invariant edge cases (~57 tests) |
| `SpanLifecycleIntegrationTest.kt` | Task 5 wiring behavior: text-id collection, non-text transition cleanup, same-id re-init |
| `SpanMapperTest.kt` | Style mapping (all variants, property isolation), OutputTransformation null/non-null contract, stability |
| `SpanMaintenanceTextObserverTest.kt` | Programmatic commit exact-skip and rebase behavior (observer-safe split/merge/setText path) |
| `SpanActionDispatcherTest.kt` | Runtime + snapshot coordination via UpdateBlockContent for apply/remove/toggle, no-op guards, multi-dispatch accumulation, collapsed-cursor pending style toggle |
| `VisibleSelectionTest.kt` | Sentinel offset adjustment for visibleSelection(): collapsed, ranged, reversed, edge cases |
| `EnterContinuationTest.kt` | New-block style continuation on Enter: pending transfer, end-of-block inheritance, mid-block no-transfer, empty block edge cases |
| `FormattingStateCalculatorTest.kt` | Pure calculator: canFormat conditions, collapsed caret pending/continuation, ranged selection query, reversed bounds, metadata |
| `DefaultFormattingActionsTest.kt` | Action adapter: ranged/collapsed toggle, apply/remove pass-through, no-op guards (no focus, block selection, drag, non-text), fresh selection resolution |
| `FormattingIntegrationTest.kt` | Full integration: focus/unfocus cycles, focus switch between styled blocks, pending styles for empty blocks, drag disables formatting, same-style cursor move structural equality, Enter continuation + calculator, toggle + calculator consistency, multi-block selection disable, config extensibility, backspace merge continuity, runtime/snapshot sync, collapsed-cursor pending toggle cycle |
| `StructuralHistoryIntegrationTest.kt` | Forced `StructuralEntry` capture at structural boundaries: split/merge, slash convert/insert, list auto-detect conversion, todo toggle, drag reorder, selected delete, typing-batch boundary |
| `HistoryRegressionIntegrationTest.kt` | Cross-cutting hybrid undo/redo regressions: alternating `BlockTextEntry`/`StructuralEntry` flows, exact focused selection and pending-style restoration through `type -> split -> undo split -> undo typing`, and history clearing after document replacement |
| `SlashPopupUtilsTest.kt` | Popup pure functions: estimatePopupHeightDp (compact/clamped), calculatePopupOffset (below/above/clamp), resolveNextHighlight (null/down/up/first/last/clamped/unknown) |
| `CascadeEditorSlashIntegrationTest.kt` | Slash integration: registry coexistence (built-in + custom), custom override, custom execution alongside built-ins, session invalidation pure function (no session, healthy, drag, selection, anchor missing, different block deleted), full scenarios (drag start, anchor deletion) |
| `CascadeEditorColorsTest.kt` | Light/dark presets: non-transparent slots, known values, light vs dark differ on key slots, copy/equality semantics |
| `CascadeEditorTypographyTest.kt` | Default preset: positive font sizes, monotonically decreasing headings, monospace code, medium-weight toolbar, copy/equality |
| `CascadeEditorStringsTest.kt` | Default preset: non-empty strings, unsupportedBlock interpolation, copy with custom values, known English defaults |
| `CascadeEditorBlockStringsTest.kt` | Default preset: all built-in typeIds present, non-empty displayName/description/keywords, forType null for unknown, BlockLocalizedStrings defaults |
| `BuiltInSlashCommandFactoryLocalizationTest.kt` | Localized slash generation: title/description override, keyword merging + dedup, null blockStrings fallback, missing typeId fallback, mixed localized/unlocalized, English keywords always present |
