# Empty Document Placeholder

Use this recipe to show a hint such as `Start here…` in a new or empty
document. The feature is opt-in and appears only when the editable document
contains one empty root `Paragraph` or `Heading`. Read-only editors suppress it.

## Compose Multiplatform

Enable the behavior through `CascadeEditorConfig`. The built-in string and
theme color are used unless you override them:

```kotlin
CascadeEditor(
    // existing state and registries
    config = CascadeEditorConfig(
        emptyDocumentPlaceholderEnabled = true,
    ),
    strings = CascadeEditorStrings.default().copy(
        emptyDocumentPlaceholder = "Add a title…",
    ),
    theme = CascadeEditorTheme.light().let { theme ->
        theme.copy(
            colors = theme.colors.copy(
                placeholderText = Color(0xFF74747B),
            ),
        )
    },
)
```

If the screen already owns a configuration, preserve its other settings with
`config.copy(emptyDocumentPlaceholderEnabled = true)`.

## Native iOS (Swift)

Pass `emptyDocumentPlaceholderEnabled: true` in the immutable native
configuration. Swift uses the complete primary initializer:

```swift
let configuration = CascadeEditorConfiguration(
    readOnly: false,
    toolbarMode: .builtIn,
    slashCommandsEnabled: true,
    blockSelectionEnabled: true,
    blockDraggingEnabled: true,
    isDark: false,
    crashPolicy: .containAndReport,
    blockIndentationEnabled: true,
    emptyDocumentPlaceholderEnabled: true,
    keyboardInsetsEnabled: false
)

let controller = CascadeEditorController(
    initialJson: nil,
    configuration: configuration
)

let strings = CascadeLocalizedStrings()
strings.emptyDocumentPlaceholder = "Add a title…"
controller.setLocalization(
    localization: CascadeEditorLocalization(strings: strings)
)

let colors = CascadeEditorColors(isDark: false)
colors.placeholderText = 0xFF74747B
controller.setColors(colors: colors)
```

The localization and color calls are optional; omit them to use the built-in
`Start here…` string and the preset placeholder color. For an existing
controller, pass the updated full configuration to
`controller.updateConfiguration(value:)`.
