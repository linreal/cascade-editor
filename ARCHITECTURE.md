# CascadeEditor Architecture

This document provides an overview of the block editor architecture for new developers.

## Overview

CascadeEditor is a block-based editor for Compose Multiplatform. It follows a unidirectional data flow pattern where state changes only through dispatched actions.

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (CascadeEditor, BlockItem, renderers)         │
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
├── action/         # All editor actions
├── registry/       # Block type registry and renderers
└── ui/             # UI components
    ├── CascadeEditor.kt           # Main editor composable
    ├── BackspaceAwareTextEdit.kt  # Text field with backspace detection
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
    val cursorPosition: Int?,          // Cursor within focused block
    val dragState: DragState?,         // Active drag operation
    val slashCommandState: SlashCommandState?  // Slash menu state
)
```


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
- `UpdateBlockContent(blockId, content)` - Change content
- `UpdateBlockText(blockId, text)` - Convenience for text
- `ConvertBlockType(blockId, newType)` - Change type (e.g., paragraph to heading)
- `MoveBlocks(blockIds, toIndex)` - Reorder blocks
- `MergeBlocks(sourceId, targetId)` - Combine two blocks
- `SplitBlock(blockId, atPosition)` - Split at cursor (Enter key)
- `ReplaceBlock(blockId, newBlock)` - Swap block

**Selection**
- `SelectBlock(blockId)` - Select single (clears others)
- `ToggleBlockSelection(blockId)` - Add/remove from selection (Ctrl+click)
- `SelectBlockRange(fromId, toId)` - Range select (Shift+click)
- `ClearSelection` - Deselect all
- `SelectAll` - Select all blocks

**Focus**
- `FocusBlock(blockId?, cursorPosition?)` - Focus with cursor
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
    fun onTextChange(blockId: BlockId, text: String)
    fun onFocus(blockId: BlockId, cursorPosition: Int?)
    fun onEnter(blockId: BlockId, cursorPosition: Int)
    fun onBackspaceAtStart(blockId: BlockId)
    fun onDeleteAtEnd(blockId: BlockId)
    fun onClick(blockId: BlockId, isMultiSelect: Boolean, isRangeSelect: Boolean)
    fun onDragStart(blockId: BlockId)
    fun onSlashCommand(blockId: BlockId)
}
```

`DefaultBlockCallbacks` provides standard implementations. It accepts an optional `stateProvider` to enable proper merge/delete logic that requires access to the current state.

## Data Flow

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
- **Backspace at start**: Merges with the previous block
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
        // Your composable UI here
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
- **State sync after merge** - TextFieldState update after blocks merge
