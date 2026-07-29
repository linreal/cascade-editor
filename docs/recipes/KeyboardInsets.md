# Keyboard Insets

Use this recipe to keep editor content and the built-in formatting toolbar above
the software keyboard without resizing the editor's measured bounds on every
keyboard-animation frame. The behavior is opt-in; enable it only when Cascade
is the sole owner of the keyboard inset.

## Integration contract

- Keep the editor host at its normal full height.
- Enable `keyboardInsetsEnabled` on the editor configuration.
- Remove any parent `imePadding`, keyboard-layout-guide constraint, or other
  keyboard avoidance that also affects the editor. Two owners apply the inset
  twice.
- Leave the flag `false` when an existing host intentionally resizes or pads the
  editor for the keyboard.

Cascade applies the platform IME inset to the root that contains both the
document viewport and built-in toolbar. Safe-area, system-bar, and surrounding
screen layout remain host responsibilities.

## Compose Multiplatform

Preserve existing behavior flags with `copy`:

```kotlin
val keyboardAwareConfig = editorConfig.copy(
    keyboardInsetsEnabled = true,
)

CascadeEditor(
    // existing state, registries, theme, and slots
    modifier = Modifier.fillMaxSize(),
    config = keyboardAwareConfig,
)
```

Do not also apply `Modifier.imePadding()` to an ancestor that bounds the
editor. On Android, inspect the activity and scaffold integration before opting
in: `adjustResize`, edge-to-edge handling, or a parent inset modifier may
already move or resize the editor. Desktop currently shares the configuration
surface; the platform-reported IME inset determines whether any padding is
applied.

## Native iOS (Swift)

Pass the flag in the immutable configuration and retain one controller and one
returned view controller for the editor lifetime:

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
    keyboardInsetsEnabled: true
)

let controller = CascadeEditorController(
    initialJson: documentJson,
    configuration: configuration
)
let viewController = controller.makeViewController()
```

Constrain the returned view controller to the editor container's full bounds,
including its normal bottom edge. Do not constrain that bottom edge to
`view.keyboardLayoutGuide.topAnchor`; doing so resizes the Compose canvas while
Cascade is also applying the keyboard inset.

To change ownership after mounting, construct the complete updated
`CascadeEditorConfiguration` and pass it to
`controller.updateConfiguration(value:)`. Update the host constraints and
configuration in the same transition so there is never a frame with two inset
owners or no inset owner.

## Verification

- Focus a text block and confirm the caret and toolbar rise together while the
  editor host's outer bounds remain unchanged.
- Interactively dismiss the keyboard and confirm there is no gap below the
  toolbar.
- Exercise focus with the keyboard already visible and confirm no double inset.
- On Android, repeat the check in the app's actual window-inset mode.
- Run `./gradlew :editor:allTests :editor-ios-sdk:iosSimulatorArm64Test` for
  configuration defaults and native mapping coverage.

Reference implementations and deeper context:

- Native iOS configuration:
  `editor-ios-sdk/src/iosMain/kotlin/io/github/linreal/cascade/ios/controller/CascadeEditorConfiguration.kt`
- Native iOS sample:
  `iosNativeSample/iosNativeSample/Editor/EditorScreenModel.swift`
- Native host lifecycle and configuration:
  `docs/iOsNativeSdk.md`
