# Cascade Editor

The first native block-based editor for Compose Multiplatform. Notion/Craft-style editing - blocks, drag-and-drop, slash commands, all without WebView, natively in Kotlin.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose](https://img.shields.io/badge/Compose_Multiplatform-1.10-4285F4?logo=jetpackcompose)](https://www.jetbrains.com/compose-multiplatform/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-minSdk_28-3DDC84?logo=android)](https://developer.android.com/)
[![iOS](https://img.shields.io/badge/iOS-16+-000000?logo=apple)](https://developer.apple.com/)

![Demo](assets/demo.gif)

## Why CascadeEditor?

Every Compose rich text library wraps a single `BasicTextField` with formatting spans. That works for styled text - but not for structured documents with draggable blocks, slash command palettes, and custom block types.

CascadeEditor is architecturally different: each paragraph, heading, list item, and divider is an independent block with its own state, renderer, and lifecycle. This is the same model Notion, Craft, and BlockNote use — now available natively for Android and iOS.

| | CascadeEditor | Single-field editors |
|---|:---:|:---:|
| Block-based architecture | Yes | No |
| Drag-and-drop reorder | Yes | No |
| Slash command system | Yes | No |
| Custom block types | Yes | No |
| Rich text spans | Yes | Yes |
| Compose Multiplatform | Yes | Yes |

## Features

- **Block-based editing** — paragraphs, headings (H1-H6), todo lists, bullet lists, numbered lists, quotes, and dividers, each as an independent block you can split, merge, reorder, and convert freely
- **Rich text formatting** — bold, italic, underline, strikethrough, inline code, highlight, and custom styles with full span preservation across block operations
- **Drag & drop** — reorder blocks with native drag gestures and full state preservation
- **Slash commands** — type `/` to open a searchable command popup with keyboard navigation and custom command registration
- **Custom block types** — extend the editor with your own block types and renderers via `CustomBlockType` and `BlockRenderer`
- **Serialization** — `toJson()` / `loadFromJson()` with versioned document schemas and codec hooks for custom types
- **Theming & localization** — fully configurable colors, typography, and UI strings with light/dark presets
- **Kotlin Multiplatform** — single codebase, native Compose rendering on Android and iOS, no WebView bridging

## Quick Start

```groovy
implementation("io.github.linreal:cascade-editor:1.0.0")
```

```kotlin
@Composable
fun MyEditor() {
    val stateHolder = rememberEditorState(
        initialBlocks = listOf(
            Block.heading(level = 1, text = "Hello"),
            Block.paragraph(text = "Start editing..."),
        )
    )

    CascadeEditor(
        modifier = Modifier.fillMaxSize(),
        stateHolder = stateHolder
    )
}
```

## Theming

Built-in light and dark presets, or full control over every visual detail:

```kotlin
// Use a preset
CascadeEditor(
    stateHolder = stateHolder,
    theme = CascadeEditorTheme.dark(),
)

// Or customize individual slots
CascadeEditor(
    stateHolder = stateHolder,
    theme = CascadeEditorTheme.light().copy(
        colors = CascadeEditorColors.light().copy(
            primary = Color(0xFF6750A4),
            cursor = Color(0xFF6750A4),
            quoteBorder = Color(0xFF6750A4),
        ),
    ),
)
```
![Demo](assets/CascadeThemes.png)

`CascadeEditorColors` exposes 20+ slots — cursor, selection, toolbar icons, slash popup, quote borders, inline code background, highlight, and more. `CascadeEditorTypography` controls font size, weight, and family for every text element from body to headings to code blocks.

All UI strings are localizable via `CascadeEditorStrings` and `CascadeEditorBlockStrings`:

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    strings = CascadeEditorStrings.default().copy(bold = "Fett"),
)
```

## Slash Commands

Type `/` in any text block to open a Notion-style command palette — fuzzy search, keyboard navigation, submenus — all without stealing focus from the text field.

Built-in commands for all block types are generated automatically. Add your own:

```kotlin
val slashRegistry = remember { SlashCommandRegistry() }

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

CascadeEditor(
    stateHolder = stateHolder,
    slashRegistry = slashRegistry,
)
```

Custom commands get the full `SlashCommandContext` — replace text, swap blocks, insert new ones, or control focus. You can also organize commands into nested submenus with `SlashCommandMenu`.

## Toolbar

A built-in formatting toolbar ships with bold, italic, underline, strikethrough, inline code, and highlight — plus keyboard shortcuts (Cmd/Ctrl+B/I/U) that work even with the toolbar hidden.

Customize which buttons appear and in what order:

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    toolbar = ToolbarSlot.Default(
        config = RichTextToolbarConfig(
            buttons = listOf(
                ToolbarButtonSpec(SpanStyle.Bold, "Bold"),
                ToolbarButtonSpec(SpanStyle.Italic, "Italic"),
                ToolbarButtonSpec(SpanStyle.InlineCode, "Code"),
            )
        )
    ),
)
```

Or replace it entirely with your own composable — you get full access to `FormattingState` and `FormattingActions`:

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    toolbar = ToolbarSlot.Custom { formattingState, formattingActions ->
        MyCustomToolbar(formattingState, formattingActions)
    },
)
```

Need to sync formatting state with an external UI (like an app bar)? Use the `onFormattingStateChanged` callback:

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    toolbar = ToolbarSlot.None,
    onFormattingStateChanged = { state -> updateAppBar(state) },
)
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

## Save & Load

Save a document to JSON and restore it later — two lines:

```kotlin
// Save
val json = stateHolder.toJson(textStates, spanStates)

// Load
val result = stateHolder.loadFromJson(json, textStates, spanStates)
```

All block types, text content, and rich text formatting (bold, italic, etc.) are preserved through the round-trip. Unknown block types from newer editor versions are kept as-is — no silent data loss on re-save.

For custom block types, plug in `BlockTypeCodec` and `BlockContentCodec` to control how your types are serialized.

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

300+ tests across 43 test files — reducers, span algorithms, slash commands, serialization, drag-and-drop, and integration workflows. See [ARCHITECTURE.md](ARCHITECTURE.md) for the full test matrix.

```bash
./gradlew :editor:allTests
```

## Platform Requirements

| | Version |
|---|---|
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.10.3 |
| Android minSdk | 28 |
| Android compileSdk | 36 |
| iOS min version | 16.0 |
| iOS targets | arm64, simulatorArm64 |
| JVM target | 11 |


## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for dev setup, code conventions, and PR guidelines.

## License

MIT
