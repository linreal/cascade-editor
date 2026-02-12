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

**BlockTextStates** — single source of truth for text content. One `TextFieldState` per block. Key methods: `getOrCreate()`, `getVisibleText()`, `mergeInto()`, `setText()`, `extractAllText()`, `cleanup()`. Provided to renderers via `LocalBlockTextStates` CompositionLocal.

**BlockSpanStates** — single source of truth for rich text spans during editing. One `MutableState<List<TextSpan>>` per block plus snapshot-aware pending-style state. Key methods: `getOrCreate(..., textLength)`, `getSpans()`, `set(..., textLength)`, `adjustForUserEdit()`, `split()`, `mergeInto()`, `applyStyle()`, `removeStyle()`, `toggleStyle()`, `queryStyleStatus()`, `activeStylesAt()`, `resolveStylesForInsertion()`. Invariants are enforced at API ingress (`getOrCreate` / `set`) by normalizing and clamping spans with current visible text length. Created and remembered in `CascadeEditor`, cleaned up in `LaunchedEffect(state.blocks)` with text-only IDs (`collectTextBlockIds`) to prevent stale span state on non-text transitions, and provided to renderers via `LocalBlockSpanStates` CompositionLocal. Per-block span state is initialized in `TextBlockRenderer` from `BlockContent.Text.spans`.

> **Why not sync text via LaunchedEffect?** Causes cursor jumps, race conditions, and double-init. `BlockTextStates` avoids all of this by owning the `TextFieldState` directly.

## Action System

All state changes go through `EditorAction.reduce(state) → newState`.

**Block Manipulation:** `InsertBlock`, `InsertBlockAfter`, `DeleteBlocks`, `DeleteBlock`, `UpdateBlockContent`, `UpdateBlockText`, `ConvertBlockType`, `MoveBlocks`, `MergeBlocks`, `SplitBlock`, `ReplaceBlock`

**Selection:** `SelectBlock`, `ToggleBlockSelection`, `SelectBlockRange`, `AddBlockRangeToSelection`, `ClearSelection`, `SelectAll`, `DeleteSelectedOrFocused`

**Focus:** `FocusBlock`, `FocusNextBlock`, `FocusPreviousBlock`, `ClearFocus`

**Drag & Drop:** `StartDrag`, `UpdateDragTarget`, `CompleteDrag`, `CancelDrag`

**Slash Commands:** `OpenSlashCommand`, `UpdateSlashCommandQuery`, `CloseSlashCommand`

## Data Flow

**Standard flow:** User Input → `BlockCallbacks` → `EditorAction` → `dispatch()` → `reduce()` → recomposition.

**Text operations (merge/split):** `BlockCallbacks` performs dual update — manipulates `BlockTextStates` directly (text content) AND dispatches action (block structure). Both converge in recomposition.

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
| Rich text spans — rendering/editing | Not done | No AnnotatedString usage yet |
| Text transformation panel | Not done | |
| Block anchor / action menu | Not done | |
| Serialization — rich text spans | Done | `RichTextSchema` encode/decode with version switch |
| Serialization — full document | Not done | `extractAllText()` helper exists |
| Undo / Redo | Not done | |
| Theming / styling API | Not done | Colors and sizes hardcoded |
| Block nesting / indentation | Not done | Flat list only |
| Multi-block drag | Not done | `DragState` supports it, UI doesn't |
| Keyboard shortcuts | Not done | Only Enter/Backspace handled |

## Testing

| Test File | Coverage |
|-----------|----------|
| `EditorStateTest.kt` | All action reducers (~65 tests) |
| `DragActionsTest.kt` | Drag state transitions |
| `AutoScrollTest.kt` | Hot zones, speed calculation |
| `DragUtilsTest.kt` | Drop target coordinate math |
| `BlockRegistryTest.kt` | Descriptor search, block creation |
| `BlockTest.kt` | Core block creation |
| `RichTextSchemaTest.kt` | Span serialization round-trips, normalization, version handling |
| `SpanAlgorithmsTest.kt` | Normalize, edit adjust, split/merge, apply/remove/toggle, style queries (~62 tests) |
| `BlockSpanStatesTest.kt` | Lifecycle, edit adjustment, split/merge transfer, style ops, queries, pending styles, aliasing/invariant edge cases (~57 tests) |
| `SpanLifecycleIntegrationTest.kt` | Task 5 wiring behavior: text-id collection, non-text transition cleanup, same-id re-init |
