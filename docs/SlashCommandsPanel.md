# Slash Command Palette — Technical Context

## 1. Feature Overview

The Slash Command Palette provides a Notion-style `/` menu that lets users convert block types and insert new blocks inline. When a user types `/` in any text block, a popup appears with a searchable list of commands (headings, lists, code, divider, image, etc.). The user can filter by typing, navigate with arrow keys, and execute with Enter or tap.

The feature is fully extensible: consumers register custom `SlashCommandItem`s alongside auto-generated built-in commands derived from `BlockDescriptor` metadata. The palette supports submenu navigation, grouped display, and keyboard-driven workflows without stealing focus from the text field.

## 2. Architecture & Design Decisions

### New Abstractions

| Layer | Class / Interface | Role |
|-------|-------------------|------|
| **Model** | `SlashCommandItem` (sealed), `SlashCommandAction`, `SlashCommandMenu` | Polymorphic menu items — leaf actions vs. expandable submenus |
| **Identity** | `SlashCommandId` (value class) | Type-safe command identifier |
| **Metadata** | `SlashCommandGroup`, `SlashCommandIconKey` | Grouping/sorting and icon indirection |
| **Registry** | `SlashCommandRegistry` | Thread-safe registration + ranked fuzzy search |
| **Observer** | `SlashCommandTextObserver` | Detects `/` insertion, tracks query range, fires open/update/close callbacks |
| **Execution** | `SlashCommandExecutor` | Resolves items, routes menus vs. actions, manages query-text removal and result lifecycle |
| **Editor Host** | `SlashCommandEditorHost` (implements `SlashCommandEditor`) | Safe mutation facade: replaces query text, swaps blocks, inserts blocks, syncs runtime + snapshot state |
| **Context** | `SlashCommandContext` | Execution-time receiver providing `anchorBlockId`, `query`, `queryRange`, and `editor` handle |
| **Factory** | `BuiltInSlashCommandFactory` | Generates `SlashCommandAction`s from `BlockDescriptor`s with `BuiltInSlashCommandSpec` metadata |
| **Spec** | `BuiltInSlashCommandSpec`, `BuiltInBlockSlashBehavior` | Per-descriptor slash config: group + execution semantics (`ConvertInPlace` vs. `AlwaysInsert`) |
| **State** | `SlashCommandState`, `SlashQueryRange` | Immutable snapshot of the active session (anchor, query, navigation path, highlighted item) |
| **UI** | `SlashCommandPopup`, `SlashCommandRow`, `SlashPopupDefaults` | Compose overlay, row rendering, layout math |
| **Locals** | `LocalSlashCaretRect`, `LocalSlashCommandExecutor`, `LocalSlashHighlightedCommandId`, `LocalSlashPopupItems`, `LocalSlashSessionAnchorBlockId` | CompositionLocals bridging CascadeEditor to TextBlockField and popup |

### Patterns

- **Sealed hierarchy for items**: `SlashCommandItem` → `SlashCommandAction` | `SlashCommandMenu`. Enables pattern matching in the executor without runtime type checks elsewhere.
- **Action/Reducer for session state**: Seven new `EditorAction` subtypes (`OpenSlashCommand`, `UpdateSlashCommandSession`, `NavigateSlashSubmenu`, `NavigateSlashBack`, `HighlightSlashCommand`, `CloseSlashCommand`) integrate into the existing unidirectional data flow.
- **Observer pattern (not Flow)**: `SlashCommandTextObserver` uses imperative callbacks (`onOpen`/`onUpdate`/`onClose`) rather than `StateFlow` because it tracks fine-grained character-level edits inside a `snapshotFlow` collector that already runs on the main dispatcher. This avoids an extra flow layer.
- **Facade for safe mutations**: `SlashCommandEditor` interface prevents commands from dispatching raw `EditorAction`s. `SlashCommandEditorHost` coordinates runtime text (`BlockTextStates`), runtime spans (`BlockSpanStates`), and snapshot state (`EditorStateHolder`) so they never diverge.
- **Factory + executor lambda**: `BuiltInSlashCommandFactory` is pure — it generates items at composition time. Actual execution is deferred via a `builtInExecutor` lambda injected from `SlashCommandExecutor`, keeping the factory testable without editor dependencies.
- **CompositionLocal threading**: Five new locals carry slash state from `CascadeEditor` down to `TextBlockField` without prop-drilling, while keeping recomposition scoped (e.g., `LocalSlashSessionAnchorBlockId` carries only the block ID, not the full query).

### Why These Choices

- `SlashCommandRegistry` uses `synchronized(lock)` rather than `Mutex` because registration happens on the main thread during composition; a lock is simpler and avoids suspend overhead.
- The popup is rendered as a sibling overlay inside the existing editor `Box` (same pattern as `DropIndicator`/`DragPreview`) rather than a `Popup` composable, avoiding focus-stealing and z-order issues on Android.
- `focusProperties { canFocus = false }` is applied to every popup element to ensure the text field retains focus during keyboard navigation.

## 3. Data Flow

### Session Lifecycle

```
User types '/' in TextBlockField
       │
       ▼
SlashCommandTextObserver.onTextChanged()
  ├─ Detects single-char '/' insertion
  └─ Calls onOpen(blockId, queryRange, "")
       │
       ▼
BlockCallbacks.onSlashCommand()
  └─ Dispatches OpenSlashCommand action
       │
       ▼
EditorState.slashCommandState is set
       │
       ▼
CascadeEditor recomposes:
  ├─ Builds effectiveSlashRegistry (built-in + custom)
  ├─ Runs search(query, navigationPath) → slashPopupItems
  ├─ Provides locals (anchor ID, items, executor, caret rect)
  └─ Renders SlashCommandPopup overlay
```

### Query Update

```
User types more characters after '/'
       │
       ▼
SlashCommandTextObserver.onTextChanged()
  ├─ Adjusts tracked range (slashStart, rangeEnd)
  ├─ Extracts query substring
  └─ Calls onUpdate(query, queryRange)
       │
       ▼
Dispatches UpdateSlashCommandSession
       │
       ▼
CascadeEditor re-runs search → popup re-renders with filtered items
```

### Command Execution

```
User presses Enter (or taps a row)
       │
       ▼
SlashCommandExecutor.execute(item)
  └─ Launches in executionScope
       │
       ▼
For SlashCommandMenu:
  └─ Dispatches NavigateSlashSubmenu (push to navigationPath)

For SlashCommandAction:
  ├─ Creates SlashCommandEditorHost
  ├─ If RemoveBeforeExecute → host.replaceQueryText("")
  │    ├─ Adjusts runtime spans
  │    ├─ Replaces visible text range
  │    └─ Syncs snapshot via UpdateBlockContent
  ├─ Creates SlashCommandContext
  ├─ Invokes action.onExecute(context)
  └─ Based on result:
       ├─ Done → host.closeMenu()
       ├─ KeepOpen → keeps menu (or closes if query text was removed)
       └─ Failure → host.closeMenu()
```

### Built-in Command Execution

```
SlashCommandContext.executeBuiltInCommand(typeId, behavior)
       │
       ▼
ConvertInPlace:
  └─ Dispatches ConvertBlockType(anchorBlockId, newType)
       (text and spans are preserved since query was already removed)

AlwaysInsert:
  └─ Creates new block from descriptor factory
  └─ Calls editor.insertBlockAfterAnchor(newBlock)
       ├─ Dispatches InsertBlockAfter
       ├─ Sets up runtime text/span state
       └─ Optionally focuses new block
```

### Session Dismissal

The session closes when any of these occur:
- `SlashCommandTextObserver` detects the `/` was deleted, cursor moved outside range, or focus lost
- Empty search results (auto-close in `LaunchedEffect`)
- Escape key pressed
- Drag or block selection starts (`shouldInvalidateSlashSession`)
- Anchor block deleted from document
- Command execution completes with `Done` or `Failure`

## 4. Public API Surface

### Consumer-Facing (public)

```kotlin
// CascadeEditor parameter
fun CascadeEditor(
    slashRegistry: SlashCommandRegistry = remember { SlashCommandRegistry() },
    // ... other params
)

// Registration
class SlashCommandRegistry {
    fun register(item: SlashCommandItem)
    fun getRootItems(): List<SlashCommandItem>
    fun search(query: String, path: List<SlashCommandId>): List<SlashCommandItem>
}

// Model
sealed interface SlashCommandItem { id, title, description, keywords, icon, group }
data class SlashCommandAction(..., onExecute: suspend SlashCommandContext.() -> SlashCommandResult)
data class SlashCommandMenu(..., children: List<SlashCommandItem>)

// Execution context
data class SlashCommandContext(anchorBlockId, query, queryRange, editor: SlashCommandEditor)
interface SlashCommandEditor {
    fun getAnchorBlock(): Block?
    fun getAnchorVisibleText(): String?
    fun replaceQueryText(replacement: String = "")
    fun updateAnchorText(text: String, cursorPosition: Int? = null)
    fun replaceAnchorBlock(block, preserveAnchorId, requestFocus, cursorPosition)
    fun insertBlockAfterAnchor(block, requestFocus, cursorPosition)
    fun focusBlock(blockId, cursorPosition)
    fun closeMenu()
}

// Results & policies
sealed interface SlashCommandResult { Done, KeepOpen, Failure(message) }
enum class SlashQueryTextPolicy { RemoveBeforeExecute, KeepText }

// Supporting types
value class SlashCommandId(val value: String)
value class SlashCommandIconKey(val value: String)
data class SlashCommandGroup(id, label, order)
data class BuiltInSlashCommandSpec(group, behavior, icon?)
sealed interface BuiltInBlockSlashBehavior { ConvertInPlace, AlwaysInsert }

// State (read-only for consumers)
data class SlashCommandState(anchorBlockId, query, queryRange, navigationPath, highlightedCommandId)
data class SlashQueryRange(start, endExclusive)

// BlockDescriptor gained a new field
data class BlockDescriptor(..., slash: BuiltInSlashCommandSpec? = null, ...)
```

### Usage Example

```kotlin
val slashRegistry = remember { SlashCommandRegistry() }
LaunchedEffect(Unit) {
    slashRegistry.register(
        SlashCommandAction(
            id = SlashCommandId("custom.timestamp"),
            title = "Timestamp",
            description = "Insert current date/time",
            onExecute = {
                editor.replaceQueryText(Clock.System.now().toString())
                SlashCommandResult.Done
            }
        )
    )
}
CascadeEditor(stateHolder = stateHolder, slashRegistry = slashRegistry)
```

## 5. Integration Points

### Existing Systems Affected

| System | Integration |
|--------|-------------|
| **EditorState** | New `slashCommandState: SlashCommandState?` field |
| **EditorAction** | Seven new action types for session lifecycle |
| **BlockDescriptor** | New optional `slash: BuiltInSlashCommandSpec?` field |
| **BlockRegistry** | `registerBuiltInDescriptors()` now populates `slash` metadata for all built-in types |
| **TextBlockField** | Hosts `SlashCommandTextObserver`, keyboard interception for arrow/Enter/Escape, caret rect reporting |
| **CascadeEditor** | Wires registry, executor, factory; provides five CompositionLocals; renders popup overlay; disables scroll during slash session |
| **BlockCallbacks** | New `onSlashCommand(blockId, queryRange, query)` callback |

### Module Dependencies

```
CascadeEditor (composable)
  ├── SlashCommandRegistry (public API)
  ├── SlashCommandExecutor (internal coordinator)
  │     ├── SlashCommandEditorHost (internal, implements SlashCommandEditor)
  │     │     ├── EditorStateHolder
  │     │     ├── BlockTextStates
  │     │     └── BlockSpanStates
  │     └── BuiltInSlashCommandFactory
  │           └── BlockDescriptor.slash metadata
  ├── SlashCommandPopup / SlashCommandRow (internal UI)
  └── TextBlockField
        └── SlashCommandTextObserver (internal)
```

## 7. Edge Cases & Known Constraints

- **Single-character insertion only**: `SlashCommandTextObserver` only triggers on single-char `/` insertions. Pasting `/heading` does NOT open the palette. This is intentional to avoid false triggers.

- **Programmatic text changes**: When `isProgrammatic` is true (e.g., merge, setText), the observer checks if the `/` still exists at `slashStart` and closes the session if it doesn't, but never opens a new session.

- **ZWSP sentinel**: All text coordinates are "visible text" (after stripping the `\u200B` prefix used for backspace detection). The observer and editor host operate in visible-text space; the `BlockTextStates` layer handles the ZWSP offset internally.

- **Scroll lock during slash session**: `LazyColumn.userScrollEnabled` is set to `false` when `slashCommandState != null`. This prevents the list from scrolling while the popup is visible. The popup itself uses a separate `LazyColumn` for its item list.

- **KeepOpen + RemoveBeforeExecute conflict**: If a command returns `KeepOpen` but its `queryTextPolicy` was `RemoveBeforeExecute`, the menu is force-closed anyway because the tracked `/` token no longer exists in the text.

- **Thread safety**: `SlashCommandRegistry` guards internal collections with `synchronized(lock)`. All other slash components (`SlashCommandTextObserver`, `SlashCommandExecutor`, `SlashCommandEditorHost`) are designed for main-thread-only access within Compose's snapshot system.

- **Empty results auto-close**: A `LaunchedEffect` in `CascadeEditor` dispatches `CloseSlashCommand` when `slashPopupItems` becomes empty. This handles the case where the user's query filters out all items.

- **Focus preservation**: Every popup composable (`SlashCommandPopup`, `SlashCommandRow`, inner `LazyColumn`) uses `focusProperties { canFocus = false }` to prevent focus theft from the active `TextBlockField`.

- **Edit-before-range handling**: When the user types before the `/` character (e.g., inserting text at the beginning of the line), the observer shifts both `slashStart` and `rangeEnd` by the edit delta. A dedicated `editBeforeRange` flag skips cursor validation in this case because the cursor is naturally at the edit site, not inside the slash range.

- **Debug logging**: `SlashPopupDefaults.calculatePopupOffset` contains `loge(...)` calls that should be removed before release.

- **Duplicate ID policy**: In both `SlashCommandRegistry` and the merged registry, the last registration wins. Custom items override built-ins on ID collision. Built-in IDs follow the format `builtin.block.<typeId>`.

## 8. Glossary

| Term | Definition |
|------|------------|
| **Anchor block** | The text block where the user typed `/` to initiate the slash session |
| **Query range** | Half-open `[start, endExclusive)` interval in visible-text coordinates covering the `/` character and any typed query text |
| **Visible text** | Text content without the ZWSP (`\u200B`) sentinel character that `BlockTextStates` prepends for backspace detection |
| **Navigation path** | Stack of `SlashCommandId`s representing the submenu drill-down. Empty = root menu level |
| **ConvertInPlace** | Built-in behavior: changes the anchor block's type without inserting a new block. Text/spans survive after query removal |
| **AlwaysInsert** | Built-in behavior: creates a new block from the descriptor factory and inserts it below the anchor |
| **Programmatic commit** | A text change initiated by code (merge, setText, replaceVisibleRange) rather than user keystroke. Tracked via `pendingProgrammaticCommits` to suppress false slash triggers |
| **Highlighted command** | The item currently selected via keyboard navigation (Up/Down arrows). Enter executes it |
| **Effective registry** | A merged `SlashCommandRegistry` containing built-in items (from `BlockDescriptor` metadata) plus consumer-registered custom items, rebuilt on each composition |
