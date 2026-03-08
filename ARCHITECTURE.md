# CascadeEditor Architecture

Block-based editor (Craft/Notion-like) for Compose Multiplatform. Unidirectional data flow: actions → reducers → state → recomposition.

## Quick Reference

| Concept | File | Key Symbol |
|---------|------|------------|
| Main composable | `ui/CascadeEditor.kt` | `CascadeEditor()` |
| Text input | `ui/BackspaceAwareTextEdit.kt` | `BackspaceAwareTextField()` |
| Text renderer | `ui/renderers/TextBlockRenderer.kt` | `TextBlockRenderer` |
| Editor registry setup | `ui/EditorRegistry.kt` | `createEditorRegistry()` |
| Drop indicator | `ui/DropIndicator.kt` | `DropIndicator()` |
| Drag preview | `ui/DragPreview.kt` | `DragPreview()` |
| Drag gesture | `ui/DragModifier.kt` | — |
| Auto-scroll | `ui/AutoScrollEffect.kt` | `AutoScrollDuringDrag()` |
| Drop target calc | `ui/utils/DragUtils.kt` | `calculateDropTargetIndex()` |
| Text state local | `ui/LocalBlockTextStates.kt` | `LocalBlockTextStates` |
| Span state local | `ui/LocalBlockSpanStates.kt` | `LocalBlockSpanStates` |
| State snapshot | `state/EditorState.kt` | `EditorState`, `DragState` |
| State holder | `state/EditorStateHolder.kt` | `EditorStateHolder`, `rememberEditorState()` |
| Text state manager | `state/BlockTextStates.kt` | `BlockTextStates` |
| Span state manager | `state/BlockSpanStates.kt` | `BlockSpanStates` |
| All actions | `action/EditorAction.kt` | `sealed class EditorAction` |
| Block model | `core/Block.kt` | `Block`, factory methods |
| Block types | `core/BlockType.kt` | `sealed interface BlockType` |
| Block content | `core/BlockContent.kt` | `sealed interface BlockContent` |
| Span style | `core/SpanStyle.kt` | `sealed interface SpanStyle` |
| Text span | `core/TextSpan.kt` | `TextSpan` |
| Block ID | `core/BlockId.kt` | `BlockId` |
| Registry | `registry/BlockRegistry.kt` | `BlockRegistry` |
| Descriptors | `registry/BlockDescriptor.kt` | `BlockDescriptor`, `BlockCategory` |
| Renderer interface | `registry/BlockRenderer.kt` | `BlockRenderer<T>`, `BlockCallbacks`, `DefaultBlockCallbacks` |
| Rich text serialization | `serialization/RichTextSchema.kt` | `RichTextSchema` |
| Span algorithms | `richtext/SpanAlgorithms.kt` | `SpanAlgorithms`, `StyleStatus` |
| Span mapper | `richtext/SpanMapper.kt` | `SpanMapper` |
| Span edit observer | `richtext/SpanMaintenanceTextObserver.kt` | `SpanMaintenanceTextObserver` |
| Span action dispatcher | `richtext/SpanActionDispatcher.kt` | `SpanActionDispatcher` |
| Span dispatcher local | `ui/LocalSpanActionDispatcher.kt` | `LocalSpanActionDispatcher` |
| Formatting state | `richtext/FormattingState.kt` | `FormattingState` |
| Formatting actions | `richtext/FormattingActions.kt` | `FormattingActions` |
| Toolbar slot | `ui/ToolbarSlot.kt` | `ToolbarSlot` |
| Toolbar config | `ui/RichTextToolbarConfig.kt` | `RichTextToolbarConfig`, `ToolbarButtonSpec` |
| Formatting calculator | `richtext/FormattingStateCalculator.kt` | `FormattingStateCalculator` |
| Formatting observer | `richtext/FormattingStateObserver.kt` | `rememberFormattingState()` |
| Formatting actions impl | `richtext/DefaultFormattingActions.kt` | `DefaultFormattingActions` |
| Default toolbar UI | `ui/RichTextToolbar.kt` | `RichTextToolbar()` |

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

**Block** = id (`BlockId`) + type (`BlockType`) + content (`BlockContent`). Factory methods: `Block.paragraph()`, `Block.heading()`, `Block.todo()`, etc.

**BlockType** — sealed interface:

| Type | Supports Text | Notes |
|------|:---:|-------|
| `Paragraph` | Yes | Default block type |
| `Heading(level)` | Yes | H1-H6 |
| `Todo(checked)` | Yes | Has `checked` boolean |
| `BulletList` | Yes | |
| `NumberedList` | Yes | |
| `Quote` | Yes | |
| `Code(language)` | Yes | Has `language` string |
| `Divider` | No | |
| `Image` | No | |

Custom blocks: implement `CustomBlockType` interface.

**BlockContent** — `Text(text, spans)` | `Image(uri, altText?)` | `Empty` | `Custom(typeId, data)`.

**TextSpan** — `TextSpan(start, end, style)` with half-open `[start, end)` visible coordinates. Validates `start >= 0` and `end >= start`.

**SpanStyle** — sealed interface: `Bold`, `Italic`, `Underline`, `StrikeThrough`, `InlineCode`, `Highlight(colorArgb)`, `Link(url)`, `Custom(typeId, payload?)`. `Custom.payload` is opaque `String?` (raw JSON); core layer must not parse it.

## State Management

**EditorState** — immutable snapshot: `blocks`, `focusedBlockId`, `selectedBlockIds`, `dragState`, `slashCommandState`. Cursor position is NOT in EditorState — it lives in `TextFieldState` managed by `BlockTextStates`.

**EditorStateHolder** — Compose-friendly mutable wrapper. Use `rememberEditorState(initialBlocks)` to create. Call `stateHolder.dispatch(action)` to modify state.

**BlockTextStates** — single source of truth for text content. One `TextFieldState` per block. Key methods: `getOrCreate()`, `getVisibleText()`, `mergeInto()`, `setText()`, `consumeProgrammaticCommit()`, `extractAllText()`, `cleanup()`. Programmatic text mutations (`mergeInto` / `setText`) register per-block expected committed text so `SpanMaintenanceTextObserver` can skip/rebase non-user commits and avoid duplicate span adjustment. Provided to renderers via `LocalBlockTextStates` CompositionLocal.

**BlockSpanStates** — single source of truth for rich text spans during editing. One `MutableState<List<TextSpan>>` per block plus snapshot-aware pending-style state. Key methods: `getOrCreate(..., textLength)`, `getSpans()`, `set(..., textLength)`, `adjustForUserEdit()`, `split()`, `mergeInto()`, `applyStyle()`, `removeStyle()`, `toggleStyle()`, `queryStyleStatus()`, `activeStylesAt()`, `resolveStylesForInsertion()`. Invariants are enforced at API ingress (`getOrCreate` / `set`) by normalizing and clamping spans with current visible text length. Created and remembered in `CascadeEditor`, cleaned up in `LaunchedEffect(state.blocks)` with text-only IDs (`collectTextBlockIds`) to prevent stale span state on non-text transitions, and provided to renderers via `LocalBlockSpanStates` CompositionLocal. Per-block span state is initialized in `TextBlockRenderer` from `BlockContent.Text.spans`. Rendering is applied through a stable per-block `BasicTextField` `outputTransformation` that reads latest spans at render time via `SpanMapper.applyStyles(...)` with defensive clamping in visible coordinates. User-edit span maintenance runs post-commit via `SpanMaintenanceTextObserver`, which consumes/rebases programmatic commit baselines from `BlockTextStates` before applying diff-based user edit maintenance. Programmatic split/merge runtime transfer is executed in `DefaultBlockCallbacks`, and `mergeInto(...)` clears pending styles on both source and target to avoid pending-style bleed after merge. External formatting operations (toolbar, keyboard shortcuts) should use `SpanActionDispatcher` (provided via `LocalSpanActionDispatcher`) which coordinates runtime `BlockSpanStates` update (immediate visual) with full snapshot sync via `UpdateBlockContent` (avoids stale-text-length mismatch). Collapsed-cursor `toggleStyle` toggles pending styles instead of applying zero-width spans. `ApplySpanStyle`/`RemoveSpanStyle` actions are snapshot-only and should not be dispatched directly during active editing. Snapshot span reducers use the same `SpanAlgorithms` normalization contract as runtime for canonical output. `SplitBlock` accepts `newBlockSpans` parameter for runtime-provided spans and always updates source block snapshot. `MergeBlocks` reducer merges snapshot spans alongside text. `DefaultBlockCallbacks` syncs merged text+spans to snapshot via `UpdateBlockContent` before `DeleteBlock` dispatch on merge paths. `UpdateBlockText` explicitly resets spans (callers needing span preservation use `UpdateBlockContent`).

> **Why not sync text via LaunchedEffect?** Causes cursor jumps, race conditions, and double-init. `BlockTextStates` avoids all of this by owning the `TextFieldState` directly.

## Action System

All state changes go through `EditorAction.reduce(state) → newState`.

**Block Manipulation:** `InsertBlock`, `InsertBlockAfter`, `DeleteBlocks`, `DeleteBlock`, `UpdateBlockContent`, `UpdateBlockText`, `ConvertBlockType`, `MoveBlocks`, `MergeBlocks`, `SplitBlock`, `ReplaceBlock`

**Span Styles:** `ApplySpanStyle`, `RemoveSpanStyle`

**Selection:** `SelectBlock`, `ToggleBlockSelection`, `SelectBlockRange`, `AddBlockRangeToSelection`, `ClearSelection`, `SelectAll`, `DeleteSelectedOrFocused`

**Focus:** `FocusBlock`, `FocusNextBlock`, `FocusPreviousBlock`, `ClearFocus`

**Drag & Drop:** `StartDrag`, `UpdateDragTarget`, `CompleteDrag`, `CancelDrag`

**Slash Commands:** `OpenSlashCommand`, `UpdateSlashCommandQuery`, `CloseSlashCommand`

## Data Flow

**Standard flow:** User Input → `BlockCallbacks` → `EditorAction` → `dispatch()` → `reduce()` → recomposition.

**Text operations (merge/split):** `BlockCallbacks` performs runtime transfer first (`BlockTextStates` + `BlockSpanStates`) and then dispatches block-structure actions. `onEnter` pre-generates `newBlockId`, passes runtime payload (`newBlockSpans`, `sourceBlockText`, `sourceBlockSpans`) into `SplitBlock` for deterministic runtime/snapshot alignment. Merge flows use captured pre-merge target length from `BlockTextStates.mergeInto(...)` to shift source spans exactly once, then sync merged content to snapshot via `UpdateBlockContent` before dispatching `DeleteBlock`. `SplitBlock` and `MergeBlocks` reducers split/merge snapshot spans using `SpanAlgorithms` for snapshot consistency.

**Style formatting:** External code uses `SpanActionDispatcher` (via `LocalSpanActionDispatcher`) which first updates runtime `BlockSpanStates` (immediate visual), then syncs snapshot via `UpdateBlockContent` (full text + spans). Collapsed-cursor toggle updates pending styles instead of dispatching actions.

## Registry System

**BlockRegistry** — maps `typeId` string to `BlockDescriptor` (metadata + factory) and `BlockRenderer` (UI). Use `registry.search(query)` for slash command filtering. Use `registry.getRenderer(typeId)` for rendering. `createEditorRegistry()` pre-registers all built-in types with `TextBlockRenderer`.

**BlockCallbacks** — interface passed to renderers for interaction handling. `DefaultBlockCallbacks` wires `onEnter` → split, `onBackspaceAtStart` → merge, `onDeleteAtEnd` → forward-merge, `onDragStart` → drag initiation, `onSlashCommand` → open menu. Stubs: `onClick`, `onLongClick`.

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
| Selection (single, multi, range) | Done | Actions done; UI triggers partial (`onClick` is a stub) |
| Drag & drop (gesture, preview, indicator, auto-scroll) | Done | Single-block drag only |
| Block registry & search | Done | |
| TextBlockRenderer | Done | All text-supporting types use this |
| Heading font sizes | Done | No bold weight yet |
| Code monospace font | Done | No syntax highlighting |
| Slash commands (backend) | Done | Actions + state + search |
| Slash commands (UI) | Not done | No popup, no "/" detection |
| Todo checkbox UI | Not done | Type exists, no checkbox rendering |
| Bullet/numbered list prefixes | Not done | Render as plain paragraphs |
| Quote visual styling | Not done | No left border / background |
| Divider renderer | Not done | Type exists, no UI |
| Image renderer | Not done | Type exists, no UI |
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
| Serialization — full document | Not done | `extractAllText()` helper exists |
| Undo / Redo | Not done | |
| Theming / styling API | Not done | Colors and sizes hardcoded |
| Block nesting / indentation | Not done | Flat list only |
| Multi-block drag | Not done | `DragState` supports it, UI doesn't |
| Keyboard shortcuts | Not done | Only Enter/Backspace handled |

## Known Gaps

| # | Area | Constraint |
|---|------|-----------|
| 1 | **Undo/Redo + Rich Text Spans** | `SpanActionDispatcher` syncs snapshots via `UpdateBlockContent`, but runtime `BlockSpanStates` is NOT part of the undo/redo snapshot chain. Future undo system must either (a) capture `BlockSpanStates` snapshots alongside `EditorState`, or (b) rebuild runtime span state from the undo'd snapshot. Until then, formatting actions cannot be undone. |

## Testing

| Test File | Coverage |
|-----------|----------|
| `EditorStateTest.kt` | All action reducers incl. span actions, split/merge span transfer, snapshot stability (~87 tests) |
| `DragActionsTest.kt` | Drag state transitions |
| `AutoScrollTest.kt` | Hot zones, speed calculation |
| `DragUtilsTest.kt` | Drop target coordinate math |
| `BlockRegistryTest.kt` | Descriptor search, block creation |
| `BlockTest.kt` | Core block creation |
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
| `DefaultFormattingActionsTest.kt` | Action adapter: ranged/collapsed toggle, apply/remove pass-through, no-op guards (no focus, Code, block selection, drag, non-text), fresh selection resolution |
| `FormattingIntegrationTest.kt` | Full integration: focus/unfocus cycles, focus switch between styled blocks, pending styles for empty blocks, drag disables formatting, same-style cursor move structural equality, Enter continuation + calculator, toggle + calculator consistency, multi-block selection disable, Code disable, config extensibility, backspace merge continuity, runtime/snapshot sync, collapsed-cursor pending toggle cycle |
