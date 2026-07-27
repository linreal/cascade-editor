# Theme Customization

Use this recipe when an app owns named theme families such as `Forest` and each
family has light and dark variants.

## Implementation contract

- Keep the user-selected family and the current light/dark appearance as
  separate state.
- Resolve `(family, isDark)` to one complete editor palette.
- Start from the matching built-in preset, then override the app's colors.
- Changing `primary`, `popupBackground`, or `inlineCodeBackground` does not
  implicitly change `linkText`, `toolbarBackground`, or
  `codeBlockBackground`; override those slots explicitly when required.
- CascadeEditor does not choose a custom family. The host resolves and applies
  the variant.

## Compose Multiplatform

1. Model the family and resolve both inputs to a `CascadeEditorTheme`:

```kotlin
enum class ThemeFamily { Violet, Forest }

fun ThemeFamily.editorTheme(isDark: Boolean): CascadeEditorTheme {
    val base = if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()
    val colors = when (this) {
        ThemeFamily.Violet -> violetColors(isDark)
        ThemeFamily.Forest -> forestColors(isDark)
    }
    return base.copy(colors = colors)
}

private fun forestColors(isDark: Boolean): CascadeEditorColors =
    (if (isDark) CascadeEditorColors.dark() else CascadeEditorColors.light()).copy(
        primary = if (isDark) Color(0xFF67D4AF) else Color(0xFF147D64),
        onPrimary = if (isDark) Color(0xFF07231A) else Color.White,
        text = if (isDark) Color(0xFFE5F5EF) else Color(0xFF102A23),
        popupBackground = if (isDark) Color(0xFF102820) else Color.White,
        toolbarBackground = if (isDark) Color(0xFF163229) else Color.White,
        linkText = if (isDark) Color(0xFF8FE0C4) else Color(0xFF147D64),
        cursor = if (isDark) Color(0xFF67D4AF) else Color(0xFF147D64),
    )
```

Define `violetColors` the same way. Add every app-specific slot from the
checklist below.

2. Resolve and pass the theme at the editor call site:

```kotlin
@Composable
fun EditorScreen(family: ThemeFamily) {
    val isDark = isSystemInDarkTheme()
    val editorTheme = remember(family, isDark) { family.editorTheme(isDark) }

    CascadeEditor(
        // existing state and registries
        theme = editorTheme,
    )
}
```

`isSystemInDarkTheme()` invalidates composition when system appearance changes,
so an open editor automatically moves from, for example, Forest Light to Forest
Dark. If the app has its own appearance override, pass that Boolean instead.

## Native iOS (Swift/SwiftUI)

The native SDK accepts colors as `0xAARRGGBB` values in a mutable
`CascadeEditorColors` bag. Seed the bag first so it always contains all 25
slots:

```swift
enum ThemeFamily {
    case violet
    case forest

    func colors(isDark: Bool) -> CascadeEditorColors {
        switch (self, isDark) {
        case (.forest, false): forestLightColors()
        case (.forest, true): forestDarkColors()
        case (.violet, false): violetLightColors()
        case (.violet, true): violetDarkColors()
        }
    }
}

private func forestDarkColors() -> CascadeEditorColors {
    let colors = CascadeEditorColors(isDark: true)
    colors.primary = 0xFF67D4AF
    colors.onPrimary = 0xFF07231A
    colors.text = 0xFFE5F5EF
    colors.popupBackground = 0xFF102820
    colors.toolbarBackground = 0xFF163229
    colors.linkText = 0xFF8FE0C4
    colors.cursor = 0xFF67D4AF
    return colors
}
```

Create one factory per family variant and set every app-specific slot from the
checklist below.

Observe iOS appearance in every screen that owns an active editor:

```swift
struct CascadeEditorHost: UIViewControllerRepresentable {
    let controller: CascadeEditorController

    func makeUIViewController(context: Context) -> UIViewController {
        controller.makeViewController()
    }

    func updateUIViewController(
        _ viewController: UIViewController,
        context: Context
    ) {}
}

struct EditorScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var family: ThemeFamily = .forest
    let controller: CascadeEditorController

    private var isDark: Bool { colorScheme == .dark }

    var body: some View {
        CascadeEditorHost(controller: controller)
            .onAppear { applyTheme() }
            .onChange(of: family) { _, _ in applyTheme() }
            .onChange(of: colorScheme) { _, _ in applyTheme() }
    }

    private func applyTheme() {
        controller.setDarkMode(value: isDark)
        controller.setColors(colors: family.colors(isDark: isDark))
    }
}
```

Always call both methods. `setDarkMode` updates semantic state exposed to native
custom blocks; `setColors` replaces the rendered palette. The SDK does not
observe `UITraitCollection` or SwiftUI environment changes itself. `setColors`
snapshots the bag, so mutate it first and then call `setColors` again for every
variant change.

## Color-slot checklist

Both APIs expose the same 25 semantic slots:

`primary`, `onPrimary`, `text`, `popupBackground`,
`unknownBlockBackground`, `toolbarIcon`, `toolbarIconDisabled`,
`slashItemTitle`, `slashChevron`, `unknownBlockText`, `uiDivider`,
`contentDivider`, `slashSelectedItem`, `inlineCodeBackground`, `highlight`,
`cursor`, `textSelectionBackground`, `quoteBorder`, `quoteBackground`,
`selectionOverlay`, `linkText`, `error`, `codeBlockBackground`, and
`toolbarBackground`, and `placeholderText`.

Reference implementations:

- Multiplatform: `sample/src/commonMain/kotlin/io/github/linreal/cascade/theme/SampleEditorTheme.kt`
- Native iOS: `iosNativeSample/iosNativeSample/App/AppTheme.swift` and
  `iosNativeSample/iosNativeSample/Screens/EditorDemoScreen.swift`
