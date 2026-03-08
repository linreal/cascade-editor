# Slash Command System — Feature Specification

## Overview

Inline command palette triggered by typing `/` inside any text-capable block. The system supports:

- built-in block commands generated from registered block descriptors
- consumer-defined actions such as camera import or template insertion
- exact inline query removal when `/` is typed in the middle of text
- async command execution without breaking editor state
- nested command menus without a future public API break

This design is aligned with the current editor architecture:

- structural changes still flow through `EditorAction`
- live text and cursor state still live in `BlockTextStates`
- live span state still lives in `BlockSpanStates`
- slash command execution uses safe editor operations instead of exposing raw reducer dispatch

## Goals

- Keep slash commands correct for inline `/` usage, not only empty blocks.
- Preserve text, spans, and cursor/focus behavior through command execution.
- Keep search behavior consistent across built-in and custom commands.
- Support async commands from day one.
- Support nested menus from day one, even if built-in commands initially use only one level.
- Avoid over-coupling slash command discoverability to block registration.

## Trigger

- Slash command opens only when the user types a new `/` character inside a text-capable block.
- Pasted `/` text does not open the menu.
- Undo/redo replay does not open the menu.
- `/` may appear at the start, middle, or end of a block.
- Slash command does not open while drag mode or multi-block selection is active.
- Opening the menu captures the exact slash query range in visible-text coordinates.

## Slash Session State

The editor must track the active slash query as a range, not only as a block ID plus free-form query string.

```kotlin
@Immutable
public data class SlashQueryRange(
    val start: Int,
    val endExclusive: Int
)

@Immutable
public data class SlashCommandState(
    val anchorBlockId: BlockId,
    val query: String,
    val queryRange: SlashQueryRange,
    val navigationPath: List<SlashCommandId> = emptyList(),
    val highlightedCommandId: SlashCommandId? = null
)
```

Rules:

- `queryRange` always includes the leading `/`.
- Coordinates are in visible-text space, not `TextFieldState` internal sentinel coordinates.
- `query` is derived from `queryRange` and excludes the leading `/`.
- `navigationPath` represents the currently open submenu path.
- The menu closes if the anchor block disappears or the cursor leaves `queryRange`.

## Popup Behavior

### Appearance

- Popup is anchored to the current cursor rect.
- Default placement is below the cursor.
- If there is insufficient viewport space below, the popup flips above.
- The popup shows the items visible at the current `navigationPath`.
- Each row may show icon, title, description, and group label.

### Live Filtering

- The user continues typing after `/` as normal.
- All text after `/` inside `queryRange` is the active query, including spaces.
- Search is centralized in the slash registry and is identical for built-in and custom commands.
- Search runs against title, description, and keywords.
- Results update on every keystroke.
- V1 uses relevance ranking, not registration order.

Minimum relevance policy:

1. exact title match
2. title prefix match
3. title substring match
4. exact keyword match
5. keyword prefix match
6. keyword substring match
7. description substring match

### Nested Menus

- The public model supports nested menus in V1.
- Selecting a menu node updates `navigationPath` and replaces the visible list in the same popup.
- A back action pops the last entry from `navigationPath`.
- Built-in commands do not need to use nested menus initially, but the public API must support them now.

### Dismissal

The popup closes and the typed text remains ordinary block text when any of these occur:

- the active path has no matching items
- the cursor leaves `queryRange`
- focus moves to another block
- the `/` character is deleted
- drag mode or multi-selection becomes active

## Command Execution

### Execution Pipeline

When the user activates an item:

1. If the item is a submenu, update `navigationPath` and keep the menu open.
2. If the item is an action, snapshot the current slash session.
3. Apply the action's `queryTextPolicy`.
4. Execute the action in the editor-owned coroutine scope.
5. Apply the returned `SlashCommandResult`.
6. Close or keep the menu according to the result.

Execution must tolerate the anchor block being deleted or changed while an async command is in flight. Host operations must fail gracefully instead of corrupting editor state.

### Query Text Policy

Most commands should remove the slash query before execution.

```kotlin
public enum class SlashQueryTextPolicy {
    RemoveBeforeExecute,
    KeepText
}
```

Default: `RemoveBeforeExecute`.

`RemoveBeforeExecute` must remove exactly `queryRange` from runtime text state and sync the corresponding snapshot text and spans without disturbing text outside the range.

## Built-in Block Command Semantics

Built-in block commands are generated from block descriptors that explicitly opt in to slash-menu exposure.

### Blank-vs-Non-Blank Resolution

For built-in block commands:

1. Remove `queryRange` from the anchor block's visible text.
2. Let `remainingText` be the resulting anchor text.
3. If `remainingText.isBlank()` and the block command behavior is `ReplaceAnchorWhenBlank`, replace the anchor block with the target block type.
4. Otherwise insert a new target block below the anchor block.

Rules:

- Replace-on-blank uses an empty text payload for text-capable targets.
- Insert-below preserves `remainingText` in the anchor block verbatim.
- Non-text blocks and custom-content blocks should default to `AlwaysInsert`.
- Replace-on-blank is an explicit descriptor choice, not an implicit rule applied to every registered block.

```kotlin
public enum class BuiltInBlockSlashBehavior {
    ReplaceAnchorWhenBlank,
    AlwaysInsert
}
```

This avoids invalid states such as converting a text block to a non-text block while leaving incompatible content attached.

## Public API Design

### Why Not a Raw `SlashCommand` Interface

A single `SlashCommand` interface with `matches(query)` and `execute(context)` is too weak and too unsafe:

- it cannot represent submenu nodes cleanly
- it cannot model async execution without a breaking change
- it pushes search policy into arbitrary implementations
- it encourages raw `dispatch` access instead of safe editor operations
- it does not provide stable identity for focus, analytics, and testing

The public API should separate metadata, search, and execution.

### Stable IDs and Presentation Types

```kotlin
@JvmInline
public value class SlashCommandId(public val value: String)

@JvmInline
public value class SlashCommandIconKey(public val value: String)

@Immutable
public data class SlashCommandGroup(
    val id: String,
    val label: String,
    val order: Int = 0
)
```

### Slash Command Items

```kotlin
public sealed interface SlashCommandItem {
    public val id: SlashCommandId
    public val title: String
    public val description: String
    public val keywords: List<String>
    public val icon: SlashCommandIconKey?
    public val group: SlashCommandGroup?
}

public data class SlashCommandAction(
    override val id: SlashCommandId,
    override val title: String,
    override val description: String,
    override val keywords: List<String> = emptyList(),
    override val icon: SlashCommandIconKey? = null,
    override val group: SlashCommandGroup? = null,
    val queryTextPolicy: SlashQueryTextPolicy = SlashQueryTextPolicy.RemoveBeforeExecute,
    val onExecute: suspend SlashCommandContext.() -> SlashCommandResult
) : SlashCommandItem

public data class SlashCommandMenu(
    override val id: SlashCommandId,
    override val title: String,
    override val description: String,
    override val keywords: List<String> = emptyList(),
    override val icon: SlashCommandIconKey? = null,
    override val group: SlashCommandGroup? = null,
    val children: List<SlashCommandItem>
) : SlashCommandItem
```

### Execution Context

Commands receive the original query plus a safe editor host. Raw reducer dispatch is not part of the public slash API.

```kotlin
public data class SlashCommandContext(
    val anchorBlockId: BlockId,
    val query: String,
    val queryRange: SlashQueryRange,
    val editor: SlashCommandEditor
)

public interface SlashCommandEditor {
    public fun getAnchorBlock(): Block?
    public fun getAnchorVisibleText(): String?
    public fun replaceQueryText(replacement: String = "")
    public fun updateAnchorText(text: String, cursorPosition: Int? = null)
    public fun replaceAnchorBlock(
        block: Block,
        preserveAnchorId: Boolean = true,
        requestFocus: Boolean = true,
        cursorPosition: Int? = null
    )
    public fun insertBlockAfterAnchor(block: Block, requestFocus: Boolean = true, cursorPosition: Int? = null)
    public fun focusBlock(blockId: BlockId, cursorPosition: Int? = null)
    public fun closeMenu()
}
```

`SlashCommandEditor` is responsible for:

- updating `BlockTextStates`
- updating `BlockSpanStates`
- dispatching the necessary snapshot actions
- preserving cursor and focus invariants
- no-op or safe failure when the anchor block no longer exists

### Execution Result

```kotlin
public sealed interface SlashCommandResult {
    public data object CloseMenu : SlashCommandResult
    public data object KeepMenuOpen : SlashCommandResult
    public data class Failure(val message: String? = null) : SlashCommandResult
}
```

Rules:

- uncaught exceptions are treated as `Failure`
- `Failure` does not restore previously removed slash text
- focus is controlled through `SlashCommandEditor` operations, not through the result object

## Registry Design

Slash commands should not be bolted directly onto `BlockRegistry`. Block registration and slash-menu discoverability are related, but they are not the same concern.

```kotlin
public class SlashCommandRegistry {
    public fun register(item: SlashCommandItem)
    public fun getRootItems(): List<SlashCommandItem>
    public fun search(query: String, path: List<SlashCommandId> = emptyList()): List<SlashCommandItem>
}
```

Rules:

- search behavior is centralized in `SlashCommandRegistry`
- custom items and built-in items use the same search algorithm
- ranking is deterministic
- items are de-duplicated by `SlashCommandId`

## Built-in Command Generation from Block Descriptors

Block descriptors may opt into a built-in slash command.

```kotlin
@Immutable
public data class BuiltInSlashCommandSpec(
    val group: SlashCommandGroup,
    val behavior: BuiltInBlockSlashBehavior,
    val icon: SlashCommandIconKey? = null
)

public data class BlockDescriptor(
    val typeId: String,
    val displayName: String,
    val description: String,
    val keywords: List<String> = emptyList(),
    val slash: BuiltInSlashCommandSpec? = null,
    val factory: (BlockId) -> Block
)
```

Rules:

- `slash == null` means the block is not shown in the slash menu
- built-in block slash commands inherit `displayName`, `description`, and `keywords` from the descriptor
- `slash.behavior` decides whether blank-anchor replacement is allowed
- block descriptors remain the source of block creation, not the source of arbitrary custom actions

## Example: Built-in Todo Command

```kotlin
val todoDescriptor = BlockDescriptor(
    typeId = "todo",
    displayName = "To-do",
    description = "Task with checkbox",
    keywords = listOf("checkbox", "task", "check", "todo"),
    slash = BuiltInSlashCommandSpec(
        group = SlashCommandGroup("lists", "Lists", order = 20),
        behavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
        icon = SlashCommandIconKey("check_box")
    ),
    factory = { id -> Block(id, BlockType.Todo(false), BlockContent.Text("")) }
)
```

## Example: Async Camera Command

```kotlin
val cameraCommand = SlashCommandAction(
    id = SlashCommandId("media.camera.capture"),
    title = "Camera",
    description = "Take a photo and insert below",
    keywords = listOf("camera", "photo", "take"),
    group = SlashCommandGroup("media", "Media", order = 30),
    icon = SlashCommandIconKey("camera"),
) {
    val imageUri = launchCameraAndAwaitResult()
    if (imageUri == null) {
        SlashCommandResult.CloseMenu
    } else {
        editor.insertBlockAfterAnchor(
            block = Block(
                id = BlockId.generate(),
                type = BlockType.Image,
                content = BlockContent.Image(imageUri, null)
            ),
            requestFocus = true
        )

        SlashCommandResult.CloseMenu
    }
}
```

## Implementation Notes

The current codebase already has useful pieces, but slash command implementation must be extended rather than wired on top of the existing minimal state:

- existing `OpenSlashCommand`, `UpdateSlashCommandQuery`, and `CloseSlashCommand` actions should evolve to carry `queryRange`, `navigationPath`, and highlight state
- query extraction and removal must operate through `BlockTextStates`, not only through snapshot reducers
- slash-range edits must preserve spans outside the removed range
- the popup should read from `SlashCommandRegistry`, not directly from `BlockRegistry.search()`
- built-in block slash items should be generated only from descriptors with `slash != null`

## What Needs to Be Built

| Component | Description |
|-----------|-------------|
| `SlashCommandState` enrichment | Add `queryRange`, `navigationPath`, and highlighted item state |
| `SlashCommandRegistry` | Centralized registry and search for root items plus nested paths |
| Public slash item model | `SlashCommandId`, `SlashCommandGroup`, `SlashCommandAction`, `SlashCommandMenu` |
| Safe execution host | Internal implementation of `SlashCommandEditor` that updates runtime and snapshot state together |
| Built-in descriptor adapter | Generate built-in block actions only from `BlockDescriptor.slash` |
| Slash text observer | Detect newly typed `/`, keep `queryRange` updated, dismiss when cursor leaves range |
| Query-range editor ops | Remove or replace `queryRange` while preserving spans and cursor invariants |
| Popup composable | Anchored popup with grouping, keyboard selection, and submenu navigation |
| Async command executor | Run `SlashCommandAction.onExecute` in editor-owned coroutine scope |
| Failure handling | Convert thrown exceptions to `SlashCommandResult.Failure` and keep editor state consistent |
