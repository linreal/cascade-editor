# CascadeEditor Architecture

This document provides an overview of the block editor architecture for new developers.

## Overview

CascadeEditor is a block-based editor for Compose Multiplatform. It follows a unidirectional data flow pattern where state changes only through dispatched actions.

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (CascadeEditor, BlockItem, renderers)         │
├─────────────────────────────────────────────────────────┤
│  Text State Layer (BlockTextStates, TextFieldState)     │
├─────────────────────────────────────────────────────────┤
│  State Layer (EditorState, EditorStateHolder)           │
├─────────────────────────────────────────────────────────┤
│  Action Layer (EditorAction sealed hierarchy)           │
├─────────────────────────────────────────────────────────┤
│  Registry Layer (BlockRegistry, BlockDescriptor)        │
├─────────────────────────────────────────────────────────┤
│  Core Layer (Block, BlockType, BlockContent, BlockId)   │
└─────────────────────────────────────────────────────────┘
```

## Project Structure

```
editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/
├── core/           # Core data types
├── state/          # State management
│   ├── EditorState.kt        # Immutable editor state snapshot
│   ├── EditorStateHolder.kt  # Compose-friendly state holder
│   └── BlockTextStates.kt    # Per-block TextFieldState manager
├── action/         # All editor actions
├── registry/       # Block type registry and renderers
└── ui/             # UI components
    ├── CascadeEditor.kt           # Main editor composable
    ├── BackspaceAwareTextEdit.kt  # Text field with backspace detection
    ├── LocalBlockTextStates.kt    # CompositionLocal for text states
    ├── EditorRegistry.kt          # Registry with built-in renderers
    └── renderers/
        └── TextBlockRenderer.kt   # Renderer for text-supporting blocks
```

## Core Concepts

### Block

A block is the fundamental unit of content. Each block has:

- **id** (`BlockId`) - Unique identifier
- **type** (`BlockType`) - Determines behavior and rendering (paragraph, heading, todo, etc.)
- **content** (`BlockContent`) - The actual data (text, image, etc.)

```kotlin
// Example: Creating blocks
val paragraph = Block.paragraph("Hello world")
val heading = Block.heading(1, "Title")
val todo = Block.todo("Task item", checked = false)
```

Location: `core/Block.kt`

### BlockType

Sealed interface defining what kind of block it is. Built-in types:

| Type | Description | Supports Text |
|------|-------------|---------------|
| `Paragraph` | Plain text | Yes |
| `Heading(level)` | H1-H6 headings | Yes |
| `Todo(checked)` | Checkbox item | Yes |
| `BulletList` | Unordered list item | Yes |
| `NumberedList` | Ordered list item | Yes |
| `Quote` | Blockquote | Yes |
| `Code(language)` | Code block | Yes |
| `Divider` | Horizontal line | No |
| `Image` | Embedded image | No |

Custom blocks implement `CustomBlockType`.

Location: `core/BlockType.kt`

### BlockContent

Sealed interface for block data:

- `Text(text: String)` - Text content
- `Image(uri: String, altText: String?)` - Image content
- `Empty` - No content (dividers)
- `Custom(typeId: String, data: Map)` - Extension point

Location: `core/BlockContent.kt`

## State Management

### EditorState

Immutable snapshot of the entire editor state:

```kotlin
data class EditorState(
    val blocks: List<Block>,           // All blocks in order
    val focusedBlockId: BlockId?,      // Currently focused block
    val selectedBlockIds: Set<BlockId>, // Multi-selection
    val dragState: DragState?,         // Active drag operation
    val slashCommandState: SlashCommandState?  // Slash menu state
)
```

Note: Cursor position is managed by `BlockTextStates` via `TextFieldState`, not stored in `EditorState`. This avoids dual-state synchronization issues.

Location: `state/EditorState.kt`

### EditorStateHolder

Compose-friendly mutable wrapper that triggers recomposition:

```kotlin
@Composable
fun MyEditor() {
    val stateHolder = rememberEditorState(initialBlocks)

    // Read state
    val state = stateHolder.state

    // Modify state via actions
    stateHolder.dispatch(InsertBlock(newBlock))
}
```

Location: `state/EditorStateHolder.kt`

### BlockTextStates

Manages `TextFieldState` instances for text-capable blocks. This is the **single source of truth** for text content during editing.

```kotlin
class BlockTextStates {
    // Get or create TextFieldState for a block (cursor defaults to position 0)
    fun getOrCreate(blockId: BlockId, initialText: String, initialCursorPosition: Int = 0): TextFieldState

    // Get current text (for persistence)
    fun getVisibleText(blockId: BlockId): String?

    // Merge text from source into target block
    fun mergeInto(sourceId: BlockId, targetId: BlockId): Int?

    // Programmatic text updates
    fun setText(blockId: BlockId, text: String, cursorPosition: Int?)

    // Extract all text for persistence
    fun extractAllText(): Map<BlockId, String>

    // Cleanup states for deleted blocks
    fun cleanup(existingBlockIds: Set<BlockId>)
}
```

**Why BlockTextStates?**

Compose's `TextFieldState` is designed to be the source of truth for text input. Syncing external state into it via `LaunchedEffect` causes issues:
- Cursor position loss on external updates
- Race conditions between user input and sync
- Double-initialization on first render

`BlockTextStates` solves this by:
- Creating one `TextFieldState` per block
- Allowing direct manipulation for operations like merge/split
- Providing the state to renderers via `LocalBlockTextStates`

Location: `state/BlockTextStates.kt`

### LocalBlockTextStates

CompositionLocal that provides `BlockTextStates` to renderers:

```kotlin
@Composable
fun TextBlockRenderer.Render(...) {
    val blockTextStates = LocalBlockTextStates.current
    val textFieldState = blockTextStates.getOrCreate(block.id, initialText)

    BackspaceAwareTextField(
        state = textFieldState,
        ...
    )
}
```

Location: `ui/LocalBlockTextStates.kt`

## Action System

All state changes go through actions. This ensures:
- Predictable state mutations
- Easy debugging (log all actions)
- Potential for undo/redo

### Action Categories

**Block Manipulation**
- `InsertBlock(block, atIndex?)` - Add block
- `InsertBlockAfter(block, afterBlockId?)` - Add after specific block
- `DeleteBlocks(blockIds)` - Remove blocks
- `DeleteBlock(blockId)` - Remove single block
- `UpdateBlockContent(blockId, content)` - Change content
- `UpdateBlockText(blockId, text)` - Convenience for text (for programmatic use)
- `ConvertBlockType(blockId, newType)` - Change type (e.g., paragraph to heading)
- `MoveBlocks(blockIds, toIndex)` - Reorder blocks
- `MergeBlocks(sourceId, targetId)` - Combine two blocks (for programmatic use)
- `SplitBlock(blockId, atPosition, newBlockText?)` - Split at cursor (Enter key)
- `ReplaceBlock(blockId, newBlock)` - Swap block

**Selection**
- `SelectBlock(blockId)` - Select single (clears others)
- `ToggleBlockSelection(blockId)` - Add/remove from selection (Ctrl+click)
- `SelectBlockRange(fromId, toId)` - Range select (Shift+click)
- `ClearSelection` - Deselect all
- `SelectAll` - Select all blocks

**Focus**
- `FocusBlock(blockId?)` - Focus a block
- `FocusNextBlock` - Move to next block
- `FocusPreviousBlock` - Move to previous block
- `ClearFocus` - Remove focus

**Drag & Drop**
- `StartDrag(blockIds)` - Begin drag
- `UpdateDragTarget(targetIndex?)` - Update drop position
- `CompleteDrag` - Execute move
- `CancelDrag` - Abort

**Slash Commands**
- `OpenSlashCommand(anchorBlockId)` - Show menu
- `UpdateSlashCommandQuery(query)` - Filter
- `CloseSlashCommand` - Hide menu

Location: `action/EditorAction.kt`

## Registry System

The registry enables extensibility by decoupling block types from their UI.

### BlockDescriptor

Metadata for a block type:

```kotlin
data class BlockDescriptor(
    val typeId: String,          // Matches BlockType.typeId
    val displayName: String,     // "Heading 1"
    val description: String,     // For slash command menu
    val keywords: List<String>,  // Search terms
    val category: BlockCategory, // BASIC, MEDIA, ADVANCED, CUSTOM
    val factory: (BlockId) -> Block  // Creates new instance
)
```

Location: `registry/BlockDescriptor.kt`

### BlockRenderer

Interface for rendering a block type:

```kotlin
interface BlockRenderer<T : BlockType> {
    @Composable
    fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    )
}
```

Location: `registry/BlockRenderer.kt`

### BlockRegistry

Central registration point:

```kotlin
// Create with built-in types
val registry = BlockRegistry.createDefault()

// Search for slash commands
val results = registry.search("head") // Returns heading_1 through heading_6

// Create block by type
val block = registry.createBlock("paragraph")

// Register custom type
registry.register(customDescriptor, customRenderer)
```

Location: `registry/BlockRegistry.kt`

### BlockCallbacks

Interface for handling user interactions within a block:

```kotlin
interface BlockCallbacks {
    fun dispatch(action: EditorAction)
    fun onFocus(blockId: BlockId)
    fun onBlur(blockId: BlockId)
    fun onEnter(blockId: BlockId, cursorPosition: Int)
    fun onBackspaceAtStart(blockId: BlockId)
    fun onDeleteAtEnd(blockId: BlockId)
    fun onClick(blockId: BlockId, isMultiSelect: Boolean, isRangeSelect: Boolean)
    fun onDragStart(blockId: BlockId)
    fun onSlashCommand(blockId: BlockId)
}
```

`DefaultBlockCallbacks` provides standard implementations. It accepts:
- `stateProvider` - Access to current state for merge/delete logic
- `blockTextStates` - For text operations (merge, split)

Location: `registry/BlockRenderer.kt`

## Data Flow

### Standard Flow (Focus, Selection, etc.)

```
User Input
    │
    ▼
BlockCallbacks (in renderer)
    │
    ▼
EditorAction created
    │
    ▼
stateHolder.dispatch(action)
    │
    ▼
action.reduce(currentState) → newState
    │
    ▼
Compose recomposes affected UI
```

### Text Operations Flow (Merge, Split)

```
User Input (Backspace at start / Enter)
    │
    ▼
BlockCallbacks.onBackspaceAtStart / onEnter
    │
    ├──────────────────────────────┐
    ▼                              ▼
BlockTextStates                EditorAction
(text manipulation)            (block structure)
    │                              │
    ▼                              ▼
TextFieldState updated         EditorState updated
    │                              │
    └──────────────────────────────┘
                    │
                    ▼
        Compose recomposes UI
```

## Using CascadeEditor

The main entry point is the `CascadeEditor` composable:

```kotlin
@Composable
fun MyScreen() {
    val initialBlocks = remember {
        listOf(
            Block.paragraph("Hello world"),
            Block.paragraph("Start typing...")
        )
    }
    val stateHolder = rememberEditorState(initialBlocks)

    CascadeEditor(
        stateHolder = stateHolder,
        modifier = Modifier.fillMaxSize()
    )
}
```

### Key Features

- **Enter key**: Splits the current block at cursor position
- **Backspace at start**: Merges with the previous block (cursor at merge point)
- **Focus management**: Automatic focus transitions between blocks

### Custom Registry

To use custom block types, create a registry with your renderers:

```kotlin
val registry = createEditorRegistry().apply {
    register(myCustomDescriptor, MyCustomRenderer())
}

CascadeEditor(
    stateHolder = stateHolder,
    registry = registry
)
```

Location: `ui/CascadeEditor.kt`

## Adding a Custom Block Type

1. **Define the type** (optional if using existing type):
```kotlin
data class CalloutBlock(val variant: Variant) : CustomBlockType {
    override val typeId = "custom:callout"
    override val displayName = "Callout"
    override val supportsText = true

    enum class Variant { INFO, WARNING, ERROR }
}
```

2. **Create the renderer**:
```kotlin
class CalloutRenderer : BlockRenderer<CalloutBlock> {
    @Composable
    override fun Render(block, isSelected, isFocused, modifier, callbacks) {
        val blockTextStates = LocalBlockTextStates.current
        val textContent = block.content as? BlockContent.Text ?: return
        val textFieldState = blockTextStates.getOrCreate(block.id, textContent.text)

        // Your composable UI here using textFieldState
    }
}
```

3. **Register**:
```kotlin
registry.register(
    BlockDescriptor(
        typeId = "custom:callout",
        displayName = "Callout",
        description = "Highlighted information box",
        keywords = listOf("alert", "tip", "note"),
        category = BlockCategory.CUSTOM,
        factory = { id ->
            Block(id, CalloutBlock(INFO), BlockContent.Text(""))
        }
    ),
    CalloutRenderer()
)
```

## Testing

Tests are in `editor/src/commonTest/`:

- `BlockTest.kt` - Core type tests
- `EditorStateTest.kt` - Action/state tests
- `BlockRegistryTest.kt` - Registry tests

Run tests:
```bash
./gradlew :editor:allTests
```

## Future Additions (Not Yet Implemented)

- **HistoryManager** - Undo/redo stack
- **EditorSerializer** - JSON import/export
- **Rich text spans** - Bold, italic, links within text
- **DividerRenderer** - Renderer for horizontal line blocks
- **ImageRenderer** - Renderer for image blocks
