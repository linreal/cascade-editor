# Read-Only Mode

Read-only mode is a UI-scoped behavior option for `CascadeEditor`. It is intended for permission states where the current user may view a document but should not mutate it through the built-in editor surface.

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    config = CascadeEditorConfig(readOnly = true),
)
```

`CascadeEditorConfig` is for cross-cutting editor behavior options. It does not replace the existing ownership, styling, slot, registry, state, or callback parameters such as `stateHolder`, `textStates`, `spanStates`, `registry`, `slashRegistry`, `theme`, `strings`, `toolbar`, `linkPopup`, `onOpenLink`, or `onFormattingStateChanged`.

## Viewer Behavior

Read-only mode keeps these viewer interactions available:

- Vertical scrolling.
- Native text selection and copy inside text blocks.
- Opening existing links from unfocused text blocks.
- Text focus, caret movement, and selection-only `TextFieldState` changes when needed for selection/navigation.
- The default toolbar, unless the caller hides it with `ToolbarSlot.None`.

Read-only mode disables editor-owned mutations:

- Typing, paste, IME insertion, and text deletion.
- Structural Enter, Backspace, Delete, code-block newline insertion, split, merge, convert, insert, delete, indent, and outdent paths.
- Formatting shortcuts and formatting action surfaces.
- Editor-owned undo/redo shortcuts handled by the text-field key path.
- Slash observation, popup rendering, keyboard navigation/execution, and toolbar slash insertion.
- Link editing popup/actions while preserving link-opening behavior.
- Todo checkbox mutation.
- Block long-press selection, drag/drop/reorder, drop target updates, drag preview/drop indicator, and empty-space tap focus.

When `readOnly` changes from `false` to `true`, `CascadeEditor` closes active slash sessions, cancels active drags, clears block selection, and dismisses editor-owned link editing popup sessions. This cleanup does not change document blocks, text focus, caret, or text selection.

## Toolbar

Read-only mode does not add a separate toolbar visibility switch.

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    config = CascadeEditorConfig(readOnly = true),
)
```

The default toolbar remains visible with mutating controls disabled or no-op, including formatting buttons, indentation buttons, link editing, and slash insertion.

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    toolbar = ToolbarSlot.None,
    config = CascadeEditorConfig(readOnly = true),
)
```

`ToolbarSlot.None` hides the toolbar exactly as it does in editable mode. `ToolbarSlot.Custom` content remains visible; editor-provided state/action surfaces report disabled state and no-op actions when read-only disables their capability.

On iOS, the default hide-keyboard toolbar button remains visible and dispatches `ClearFocus`. Since the soft keyboard usually does not open for a read-only field, this is mostly a no-op. Consumers who want to hide it in read-only mode should provide custom toolbar content with `ToolbarSlot.Custom`.

## Custom Renderers And Chrome

Custom content can read the public config local:

```kotlin
val readOnly = LocalCascadeEditorConfig.current.readOnly
```

Use it to disable custom checkboxes, buttons, menus, gestures, and direct runtime writes that your code owns. Custom block-selection or drag affordances should also honor `LocalCascadeEditorConfig.current.blockSelectionEnabled` and `LocalCascadeEditorConfig.current.blockDraggingEnabled`. Built-in implementation code uses internal policy plumbing instead; those internal objects are not public API and should not be consumed as extension points.

Editor-provided custom chrome surfaces are already policy-aware:

- `FormattingState.canFormat` is `false` in read-only mode.
- `FormattingActions` no-op.
- `IndentationState.canIndentForward` and `canIndentBackward` are `false`.
- `IndentationActions` no-op.
- `LinkState.canLink` is `false`, while non-mutating metadata such as `focusedBlockId`, `target`, `targetText`, `existingUrl`, `existingLinkRange`, `existingLinkText`, `isInsideLink`, and `intersectsLink` remains available when the focused block and selection support it.
- `LinkActions` validate URLs for UI feedback but do not mutate spans or text when link editing is disabled.

Custom chrome that directly writes to app-owned objects must gate itself. For example, a custom toolbar button that calls `BlockTextStates.replaceVisibleRange(...)` or `TextFieldState.edit { ... }` should check `LocalCascadeEditorConfig.current.readOnly` before writing.

## Application Boundary

Read-only mode is not server authorization and not a complete application permission model. It protects editor-owned UI integration paths inside `CascadeEditor`.

These surfaces remain mutable and must be guarded by the application when needed:

- `EditorStateHolder.dispatch(...)`.
- `EditorStateHolder.setState(...)`.
- `EditorStateHolder.undo()` and `redo()`.
- `loadFromJson(...)` and `loadFromHtml(...)`.
- Serialization (`toJson(...)`, `toHtml(...)`) and autosave.
- Remote sync application.
- App-owned toolbar/header controls.
- Direct writes through `BlockTextStates`, `BlockSpanStates`, or captured `TextFieldState` references.

The sample editor screen demonstrates this by disabling app-owned undo/redo/reset/delete controls and persistence writes while the read-only toggle is active.

## Low-Level Text APIs

`BackspaceAwareTextField` also exposes a defaulted `readOnly: Boolean = false` parameter. When true, it forwards read-only mode to the underlying state-based `BasicTextField`, keeping focus and selection available while blocking text editing through that field. This is a lower-level text field API, not a replacement for `CascadeEditor(config = CascadeEditorConfig(readOnly = ...))`.

`TextFieldState.selectedVisibleText()` returns the selected visible text in document order while excluding the internal leading ZWSP sentinel used for backspace-at-start detection. Common tests cover this helper. Before release, maintainers must still run native clipboard QA on Android, iOS, desktop, and wasm/JS to confirm platform copy does not include the sentinel. If a platform copies the sentinel despite the common helper coverage, the read-only text rendering path should be changed to avoid sentinel leakage before release.

## Accessibility

Built-in renderers do not currently announce read-only state to screen readers. Consumers who need that announcement should wrap the editor with their own semantics, for example:

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    config = CascadeEditorConfig(readOnly = true),
    modifier = Modifier.semantics {
        contentDescription = "Read-only document"
    },
)
```

Choose the content description or role that fits your application context.
