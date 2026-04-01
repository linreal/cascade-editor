# CascadeEditor

A block-based rich text editor for Compose Multiplatform — the Notion/Craft editing model, natively in Kotlin.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose](https://img.shields.io/badge/Compose_Multiplatform-1.10-4285F4?logo=jetpackcompose)](https://www.jetbrains.com/compose-multiplatform/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-minSdk_28-3DDC84?logo=android)](https://developer.android.com/)
[![iOS](https://img.shields.io/badge/iOS-arm64-000000?logo=apple)](https://developer.apple.com/)

<!-- TODO: Record demo GIF (docs/demo.gif) -->
![Demo](docs/demo.gif)

## The Problem

Building structured document editing in Compose means solving problems that `BasicTextField` was never designed for: splitting paragraphs, merging blocks, reordering sections, inline formatting with span preservation, slash commands, cross-platform serialization. Most teams either settle for a flat text area or bridge to a web-based editor, losing Compose's native rendering pipeline entirely.

Existing Compose rich-text libraries take the **single-field** approach — one state object wrapping one text buffer. That works for formatted text input, but it isn't document editing. Splitting a paragraph requires substring math and span offset recalculation. Reordering sections means cut-paste with careful re-stitching. Converting a paragraph to a heading means applying a style range to a character span rather than changing the block's type. The gap between "rich text field" and "structured document editor" is where most of the hard engineering lives.

## Block-Based Architecture

CascadeEditor treats a document as an ordered list of typed blocks, each an independent unit with its own type, content, and text state. Structural operations are first-class:

| Operation | Block-based (CascadeEditor) | Single-field approach |
|---|---|---|
| Split a paragraph at the cursor | `SplitBlock` — two blocks, cursor in the new one | Substring math + offset recalculation |
| Convert paragraph to heading | `ConvertBlockType` — type change, content preserved | Style range applied to characters |
| Reorder sections | `MoveBlocks` — drag-and-drop with full state preservation | Cut-paste substrings + re-stitch all spans |
| Delete a section | `DeleteBlock` — remove the block | Find range boundaries + delete + adjust offsets |

Each block owns its own `TextFieldState`. Editing one block never affects another's cursor or text state — no global offset recalculation, no cursor-jump bugs, no race conditions between adjacent edits.

### Design decisions

- **Unidirectional data flow** — sealed action hierarchy, pure reducer functions, immutable state snapshots. Every state change is deterministic and testable without UI infrastructure.
- **Kotlin Multiplatform** — single codebase, native Compose rendering on Android and iOS. No WebView bridging.
- **Rich text spans** — bold, italic, underline, strikethrough, inline code, highlight, and custom styles with O(n) normalization. Split/merge operations transfer spans with deterministic snapshot alignment.
- **Slash command system** — searchable popup with submenus, keyboard navigation, caret-relative positioning, and custom command registration.
- **Full serialization** — `toJson()` / `loadFromJson()` with versioned document schemas, codec hooks for custom types, configurable ID and duplicate handling.
- **Theming and localization** — colors, typography, and all UI strings configurable through data classes and `CompositionLocal` providers, with light/dark presets.

## Quick Start

> Maven Central publication is in progress. For now, clone the repository and use the `editor` module as a local dependency, or explore the included `sample/` app.

```kotlin
@Composable
fun MyEditor() {
    val stateHolder = rememberEditorState(
        initialBlocks = listOf(
            Block.heading(level = 1, text = "Hello"),
            Block.paragraph(text = "Start editing..."),
        )
    )

    CascadeEditor(stateHolder = stateHolder)
}
```

All state mutations flow through the sealed `EditorAction` hierarchy:

```kotlin
stateHolder.dispatch(EditorAction.InsertBlockAfter(
    afterBlockId = currentBlockId,
    newBlock = Block.paragraph()
))

val currentBlocks = stateHolder.state.blocks
val focusedId = stateHolder.state.focusedBlockId
```

## Block Types

| Type | Supports Text | Notes |
|------|:---:|-------|
| `Paragraph` | Yes | Default block type |
| `Heading(level)` | Yes | H1–H6 |
| `Todo(checked)` | Yes | Checkbox with toggle action |
| `BulletList` | Yes | Auto-detected from `- ` prefix |
| `NumberedList(number)` | Yes | Auto-renumbering on insert/delete/move |
| `Quote` | Yes | Left border stripe + background tint |
| `Divider` | No | Horizontal rule |

Extend with custom types via the `CustomBlockType` interface (see [Custom Block Types](#custom-block-types)).

## Slash Commands

Type `/` in any text block to open the slash command popup. Built-in commands for all standard block types are generated automatically from the block registry. Register custom commands:

```kotlin
val slashRegistry = remember { SlashCommandRegistry() }

slashRegistry.register(
    SlashCommandItem(
        id = SlashCommandId("my-callout"),
        title = "Callout",
        description = "Insert a callout block",
        keywords = listOf("callout", "note", "info"),
        action = SlashCommandAction.Custom { context ->
            context.replaceAnchorBlock(Block.paragraph(text = "Callout content"))
            SlashCommandResult.Handled
        }
    )
)

CascadeEditor(
    stateHolder = stateHolder,
    slashRegistry = slashRegistry,
)
```

## Theming & Localization

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    theme = CascadeEditorTheme.dark(),
    strings = CascadeEditorStrings.default().copy(bold = "Fett"),
)
```

`CascadeEditorTheme` provides `colors` and `typography` with `light()` and `dark()` presets — every color from cursor tint to slash popup background to quote border is configurable. `CascadeEditorStrings` localizes UI chrome; `CascadeEditorBlockStrings` localizes per-block-type display names, descriptions, and slash command keywords.

## Custom Block Types

```kotlin
public data object CalloutBlock : CustomBlockType {
    override val typeId: String = "callout"
    override val displayName: String = "Callout"
    override val supportsText: Boolean = true
}

public class CalloutBlockRenderer : BlockRenderer<CalloutBlock> {
    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks,
    ) {
        // Your composable UI
    }
}

val registry = createEditorRegistry()
registry.register(
    BlockDescriptor(typeId = "callout", displayName = "Callout"),
    CalloutBlockRenderer()
)
```

## Serialization

```kotlin
val json = stateHolder.toJson(textStates, spanStates)
val result = stateHolder.loadFromJson(json, textStates, spanStates)
```

Versioned `DocumentSchema` with `BlockTypeCodec` and `BlockContentCodec` hooks for custom types. Configurable `BlockIdMode` (preserve or regenerate), `DuplicateIdMode`, and `CustomDataMode`.

## Architecture

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
│  Core Layer (Block, BlockType, BlockContent, TextSpan)  │
└─────────────────────────────────────────────────────────┘
```

Six layers with strict dependency direction. The reducer pattern ensures every state transition is deterministic — testable in isolation without mocks or UI infrastructure. `BlockTextStates` owns one `TextFieldState` per block directly, avoiding the cursor-jump and race-condition issues that `LaunchedEffect`-based syncing causes.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full quick-reference table (90+ public symbols), layer interactions, data flow details, and conventions.

## Testing

**43 test files** in `editor/src/commonTest/` cover the full architecture:

- **Reducer coverage** (~87 tests) — every `EditorAction` including span transfer across split/merge
- **Span algorithms** (~62 tests) — normalize, edit adjust, split/merge, apply/remove/toggle, style queries
- **Span state management** (~57 tests) — lifecycle, runtime operations, pending styles, edge cases
- **Slash commands** (~30 tests) — session management, text observer, registry ranking, factory generation
- **Integration suites** — block selection workflows, list behavior chains (auto-detect, enter continuation, renumbering), formatting round-trips, serialization encode/decode, drag-and-drop, theming presets, localization

```bash
./gradlew :editor:allTests
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full test matrix.

## Platform Requirements

| | Version |
|---|---|
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.10.3 |
| Android minSdk | 28 |
| Android compileSdk | 36 |
| iOS targets | arm64, simulatorArm64 |
| JVM target | 11 |

## Roadmap

- [ ] Undo / redo (requires span state snapshot integration)
- [ ] Text transformation panel
- [ ] Block nesting / indentation
- [ ] Multi-block drag
- [ ] Block anchor / action menu
- [ ] Maven Central publication

## Contributing

```bash
git clone https://github.com/linreal/cascade-editor.git
cd cascade-editor
./gradlew :editor:allTests
```

The codebase enforces `explicitApi()` — all public declarations require explicit visibility modifiers. State objects use `@Immutable` data classes. New actions must extend the `EditorAction` sealed hierarchy with a `reduce()` override.

See [ARCHITECTURE.md](ARCHITECTURE.md) for conventions and the full quick-reference table.

## License

MIT
