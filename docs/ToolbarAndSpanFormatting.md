# Toolbar & Span Formatting — Technical Context

## 1. Feature Overview

The toolbar system provides inline editor controls at the bottom of the `CascadeEditor`. It lets users apply span styles (bold, italic, underline, strikethrough, inline code, highlight) to selected text or toggle pending styles at a collapsed cursor, and the built-in toolbar also exposes block indent/outdent controls. The feature is fully configurable: consumers can use the built-in config-driven toolbar, supply a completely custom composable toolbar, or hide it entirely. Keyboard shortcuts (Cmd/Ctrl+B/I/U) work independently of toolbar visibility. An external `onFormattingStateChanged` callback enables out-of-editor UI (e.g., a floating toolbar or app bar) to reflect formatting state reactively, while indentation state/actions are available through CompositionLocals for custom chrome.

---

## 2. Architecture & Design Decisions

### Introduced Types

| Type | File | Role |
|------|------|------|
| `ToolbarSlot` | `ui/ToolbarSlot.kt` | Sealed interface controlling which toolbar variant renders: `Default`, `None`, or `Custom`. |
| `RichTextToolbarConfig` | `ui/RichTextToolbarConfig.kt` | `@Immutable` data class listing `ToolbarButtonSpec` entries. Ships a `Default` companion preset with 6 built-in styles. |
| `ToolbarButtonSpec` | `ui/RichTextToolbarConfig.kt` | Pairs a `SpanStyle` with an accessibility `label`. |
| `RichTextToolbar` | `ui/RichTextToolbar.kt` | `internal` composable — the default config-driven toolbar UI. Iterates `config.buttons`, renders toggle buttons reflecting `FormattingState`. |
| `FormattingState` | `richtext/FormattingState.kt` | `@Immutable` snapshot: per-style `StyleStatus` map, `canFormat` flag, `focusedBlockId`, `selectionCollapsed`. |
| `FormattingActions` | `richtext/FormattingActions.kt` | `@Stable` interface with `toggleStyle`, `applyStyle`, `removeStyle`. |
| `DefaultFormattingActions` | `richtext/DefaultFormattingActions.kt` | Resolves focused block + selection at invocation time, delegates to `SpanActionDispatcher`. No-op when formatting is disallowed. |
| `FormattingStateCalculator` | `richtext/FormattingStateCalculator.kt` | Pure function: takes raw inputs (focus, selection, spans, pending styles) → `FormattingState`. |
| `rememberFormattingState()` | `richtext/FormattingStateObserver.kt` | Composable bridge producing `State<FormattingState>` via chained `derivedStateOf` layers. |
| `SpanActionDispatcher` | `richtext/SpanActionDispatcher.kt` | Coordinates runtime `BlockSpanStates` update + snapshot `UpdateBlockContent` dispatch in one call. |
| `TextBlockKeyHandler` | `ui/renderers/TextBlockKeyHandler.kt` | Handles Cmd/Ctrl+B/I/U shortcuts and slash popup keyboard navigation. |
| `LocalFormattingActions` | `ui/LocalFormattingActions.kt` | `CompositionLocal` providing `FormattingActions?` to the composition tree. |
| `LocalSpanActionDispatcher` | `ui/LocalSpanActionDispatcher.kt` | `CompositionLocal` providing `SpanActionDispatcher?` to the composition tree. |
| `IndentationState` | `indentation/IndentationState.kt` | `@Immutable` snapshot: `canIndentForward`, `canIndentBackward`, and document-ordered target root IDs. |
| `IndentationActions` | `indentation/IndentationActions.kt` | `@Stable` interface with `indentForward()` and `indentBackward()`. |
| `DefaultIndentationActions` | `indentation/DefaultIndentationActions.kt` | Resolves state at invocation time and dispatches structural indent actions only when enabled. |
| `LocalIndentationState` | `ui/LocalIndentationState.kt` | `CompositionLocal` providing `State<IndentationState>?` to custom editor chrome. |
| `LocalIndentationActions` | `ui/LocalIndentationActions.kt` | `CompositionLocal` providing `IndentationActions?` to custom editor chrome. |

### Key Design Decisions

**Three-variant `ToolbarSlot` sealed interface.** Rather than a boolean toggle + optional composable, the slot is a sealed type with explicit variants. This makes the toolbar contract exhaustive at the call site and in the `when` dispatch inside `CascadeEditor`.

**Config-driven default toolbar.** `RichTextToolbarConfig.Default` defines which buttons appear and in what order. Consumers can add/remove/reorder buttons without implementing a custom composable — just pass a modified `RichTextToolbarConfig`.

**Indentation controls are config-gated but API-always available.** `RichTextToolbarConfig.showIndentation` controls whether the default toolbar renders indent/outdent icon buttons. `CascadeEditor` still provides `LocalIndentationState` and `LocalIndentationActions` in `ToolbarSlot.Default`, `ToolbarSlot.Custom`, and `ToolbarSlot.None`, so hidden or custom toolbars can expose indentation without changing the `ToolbarSlot.Custom` lambda signature.

**Theme-injected highlight color.** When the consumer uses the exact `RichTextToolbarConfig.Default` sentinel, `CascadeEditor` replaces the placeholder `SpanStyle.Highlight` color with `theme.colors.highlight.toArgb()`. Custom configs are left untouched. This avoids a mismatch between the toolbar button's style and the rendered highlight color.

**`FormattingActions` always created — even without a toolbar.** Keyboard shortcuts (Cmd+B/I/U) consume `FormattingActions` via `LocalFormattingActions`. The actions are created unconditionally so shortcuts work regardless of `ToolbarSlot.None`.

**`FormattingState` lazily created.** The expensive `rememberFormattingState()` observer chain is only instantiated when `resolvedToolbar !is ToolbarSlot.None || onFormattingStateChanged != null`. This avoids unnecessary `derivedStateOf` overhead in headless/no-toolbar configurations.

**Two-layer `derivedStateOf` in the formatting observer.** Layer 1 extracts `focusedBlockId`, `focusedBlockType`, `hasBlockSelection`, `isDragging` — each as an independent `derivedStateOf` that only propagates when its specific output changes. Layer 2 reads Layer 1 outputs plus per-block runtime state (selection, spans, pending styles). This shields the final derivation from high-frequency state churn (drag position, text changes in other blocks).

**`SpanActionDispatcher` as the single coordination point.** Toolbar buttons, keyboard shortcuts, and any external code all route through `SpanActionDispatcher`, which performs: (1) runtime `BlockSpanStates` update (immediate visual), then (2) `UpdateBlockContent` dispatch (snapshot sync with current text + spans). Direct dispatch of `ApplySpanStyle`/`RemoveSpanStyle` actions is explicitly prohibited during active editing — these snapshot-only actions would use stale text lengths.

**Collapsed-cursor toggle produces pending styles, not zero-width spans.** When the cursor is collapsed, `toggleStyle` updates `BlockSpanStates.pendingStyles` for the block. Next inserted character inherits pending styles. No snapshot dispatch occurs — pending styles are runtime-only.

**Focus-stealing prevention.** Both `RichTextToolbar` and individual `ToolbarToggleButton` set `Modifier.focusProperties { canFocus = false }`, preventing the toolbar from stealing focus from the active text field on tap.

**Structural history boundary for indentation.** Default indentation actions dispatch `IndentForward` / `IndentBackward` through the same structural transaction wrapper used by built-in semantic document edits when runtime history holders are bound.

---

## 3. Data Flow

### Toolbar Button Tap

```
User taps toolbar button (e.g., Bold)
  └─ ToolbarToggleButton.onClick
       └─ FormattingActions.toggleStyle(SpanStyle.Bold)
            └─ DefaultFormattingActions.resolveContext()
                 ├─ Reads stateHolder.state → focusedBlockId, block type, selection/drag guards
                 └─ Reads textStates.get(blockId).visibleSelection() → (start, end)
            └─ SpanActionDispatcher.toggleStyle(blockId, start, end, Bold)
                 ├─ [Collapsed cursor] → spanStates.setPendingStyles(blockId, toggled set)
                 │                        (no snapshot dispatch; next insertion applies style)
                 └─ [Ranged selection]
                      ├─ spanStates.queryStyleStatus → FullyActive? remove : apply
                      ├─ spanStates.applyStyle / removeStyle (immediate visual)
                      └─ dispatchFn(UpdateBlockContent(blockId, Text(visibleText, spans)))
                           └─ EditorAction.reduce → new EditorState snapshot
```

### Keyboard Shortcut (Cmd+B)

```
User presses Cmd+B
  └─ TextBlockField.onPreviewKeyEvent
       └─ TextBlockKeyHandler.handleFormattingShortcut(keyEvent)
            ├─ isMetaPressed || isCtrlPressed → true
            ├─ formattingStyleForKey(Key.B) → SpanStyle.Bold
            ├─ Dedup guard: handledFormattingKey != Key.B → proceed
            └─ formattingActions.toggleStyle(SpanStyle.Bold)
                 └─ (same flow as toolbar tap above)
```

### Built-In Indentation Button Tap

```
User taps the indent-forward toolbar icon
  └─ RichTextToolbar reads indentationState.value.canIndentForward
       └─ If enabled: indentationActions.indentForward()
            └─ DefaultIndentationActions re-reads current IndentationState
                 └─ dispatchStructuralAction(IndentForward)
                      └─ reducer shifts supported target roots/subtrees and renumbers ordered lists
```

### Custom Toolbar Indentation Access

```
ToolbarSlot.Custom content
  └─ val indentationState = LocalIndentationState.current?.value
  └─ val indentationActions = LocalIndentationActions.current
  └─ Custom UI uses indentationState.canIndentForward / canIndentBackward
       and calls indentationActions.indentForward() / indentBackward()
```

### Formatting State Observation

```
(any state change: focus, cursor move, span edit, drag, block selection)
  └─ Layer 1 derivedStateOf
       ├─ focusedBlockId: derivedStateOf { stateHolder.state.focusedBlockId }
       ├─ focusedBlockType: derivedStateOf { ... block.type }
       ├─ hasBlockSelection: derivedStateOf { selectedBlockIds.isNotEmpty() }
       └─ isDragging: derivedStateOf { dragState != null }
  └─ Layer 2 derivedStateOf (re-evaluates only when Layer 1 outputs change)
       ├─ textFieldState.visibleSelection() → (start, end)
       ├─ spanStates.getSpans(blockId) → List<TextSpan>
       ├─ spanStates.getPendingStyles(blockId) → Set<SpanStyle>?
       └─ FormattingStateCalculator.compute(...) → FormattingState
            └─ ToolbarToggleButton reads state.styleStatusOf(style) → StyleStatus
            └─ onFormattingStateChanged callback (if set) fires via snapshotFlow
```

### External Callback

```
Consumer
  └─ CascadeEditor(onFormattingStateChanged = { state -> updateAppBar(state) })
       └─ LaunchedEffect(formattingState) {
              snapshotFlow { formattingState.value }
                  .collect { currentCallback(it) }  // structural equality dedup
          }
```

---

## 4. Public API Surface

### `CascadeEditor` Parameters

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `toolbar` | `ToolbarSlot` | `ToolbarSlot.Default()` | Controls toolbar variant |
| `onFormattingStateChanged` | `((FormattingState) -> Unit)?` | `null` | External formatting state listener |

### `ToolbarSlot` (sealed interface)

```kotlin
ToolbarSlot.Default(config: RichTextToolbarConfig = RichTextToolbarConfig.Default)
ToolbarSlot.None
ToolbarSlot.Custom(
    trackedStyles: List<SpanStyle> = ...,  // styles to include in FormattingState.styles
    content: @Composable (State<FormattingState>, FormattingActions) -> Unit,
)
```

### `RichTextToolbarConfig` / `ToolbarButtonSpec`

```kotlin
RichTextToolbarConfig(
    buttons: List<ToolbarButtonSpec>,
    showIndentation: Boolean = true,
)
ToolbarButtonSpec(style: SpanStyle, label: String)
```

### `FormattingState`

```kotlin
data class FormattingState(
    val styles: Map<SpanStyle, StyleStatus>,  // per-tracked-style status
    val canFormat: Boolean,                    // false when formatting is disallowed
    val focusedBlockId: BlockId?,
    val selectionCollapsed: Boolean,
)
fun styleStatusOf(style: SpanStyle): StyleStatus  // Absent if not tracked
```

### `FormattingActions`

```kotlin
interface FormattingActions {
    fun toggleStyle(style: SpanStyle)
    fun applyStyle(style: SpanStyle)
    fun removeStyle(style: SpanStyle)
}
```

### `IndentationState` / `IndentationActions`

```kotlin
data class IndentationState(
    val canIndentForward: Boolean,
    val canIndentBackward: Boolean,
    val targetBlockIds: List<BlockId>,
)

interface IndentationActions {
    fun indentForward()
    fun indentBackward()
}
```

### `SpanActionDispatcher`

```kotlin
class SpanActionDispatcher(dispatchFn, textStates, spanStates) {
    fun applyStyle(blockId, rangeStart, rangeEnd, style)
    fun removeStyle(blockId, rangeStart, rangeEnd, style)
    fun toggleStyle(blockId, rangeStart, rangeEnd, style)
}
```

### CompositionLocals

| Local | Type | Provided by |
|-------|------|-------------|
| `LocalFormattingActions` | `FormattingActions?` | `CascadeEditor` |
| `LocalSpanActionDispatcher` | `SpanActionDispatcher?` | `CascadeEditor` |
| `LocalIndentationState` | `State<IndentationState>?` | `CascadeEditor` |
| `LocalIndentationActions` | `IndentationActions?` | `CascadeEditor` |

---

## 5. Integration Points

### Consumed By

| Consumer | What it reads | How |
|----------|---------------|-----|
| `RichTextToolbar` | `State<FormattingState>`, `FormattingActions`, `RichTextToolbarConfig` | Direct parameters from `CascadeEditor` |
| `ToolbarSlot.Custom.content` | `State<FormattingState>`, `FormattingActions` | Composable lambda parameters |
| Custom toolbar indentation UI | `LocalIndentationState`, `LocalIndentationActions` | CompositionLocals inside `CascadeEditor` |
| `TextBlockKeyHandler` | `FormattingActions` | Constructor parameter (sourced from `LocalFormattingActions`) |
| External app code | `FormattingState` | `onFormattingStateChanged` callback |

### Depends On

| Dependency | Role |
|------------|------|
| `EditorStateHolder` | Source of `focusedBlockId`, block types, selection, drag state |
| `BlockTextStates` | Visible text + cursor selection for the focused block |
| `BlockSpanStates` | Runtime span state: spans, pending styles, style queries |
| `SpanAlgorithms` | Pure functions for `queryStyleStatus`, `activeStylesAt` |
| `CascadeEditorTheme` | Colors for toolbar backgrounds, icons, primary/onPrimary; highlight color injection |
| `CascadeEditorStrings` | Localized accessibility labels for built-in toolbar buttons |
| `CascadeEditorTypography` | `toolbarButton` text style for button labels |
| `IndentationStateCalculator` | Enablement for indent/outdent controls; mirrors reducer target resolution and outline validation |

### Placement in Editor Layout

```
Column(modifier) {
    Box(weight=1f) {           // Editor content area (drag gesture target)
        LazyColumn { ... }     // Block list
        AutoScrollDuringDrag
        DropIndicator
        DragPreview
        SlashCommandPopup
    }
    // --- Toolbar sits OUTSIDE the drag Box ---
    when (resolvedToolbar) {
        Default → RichTextToolbar(...)
        Custom  → resolvedToolbar.content(...)
        None    → { /* nothing */ }
    }
}
```

The toolbar is intentionally outside the drag gesture `Box` to prevent drag events from hitting toolbar children.

---

## 6. Edge Cases & Known Constraints

**iOS duplicate KeyDown events.** iOS delivers duplicate `KeyDown` events for the same key press. `TextBlockKeyHandler` uses a `handledFormattingKey` guard to deduplicate — the first `KeyDown` toggles the style, subsequent duplicates are consumed without action. The guard resets on `KeyUp` or when the modifier key is released.

**`canFormat` disablement conditions.** Formatting is disabled (toolbar buttons grayed out, actions no-op) when:
- No block is focused
- Focused block type does not support text
- Block selection is active (multi-select mode)
- Drag is in progress

**Indentation enablement is independent of formatting enablement.** Indent/outdent buttons use `IndentationState`, not `FormattingState.canFormat`. They can target focused supported blocks or selected supported root blocks and no-op when structural outline rules disallow movement.

**`showIndentation = false` hides only default toolbar buttons.** It does not disable indentation reducers or remove `LocalIndentationState` / `LocalIndentationActions` from custom toolbar content.

**Highlight color identity.** `SpanStyle.Highlight` uses `kindMatches` for toolbar status detection — any `Highlight` regardless of `colorArgb` is treated as the same "kind". This means the toolbar correctly reflects highlight status even when the theme color differs from the stored span color. However, applying highlight via the toolbar always uses the theme color, potentially creating spans with different `colorArgb` values in the same document.

**Collapsed-cursor pending styles are runtime-only.** Pending styles live in `BlockSpanStates` memory and are not part of `EditorState` snapshots. They survive within a composition session but are lost if `BlockSpanStates` is recreated. They are also cleared on block merge operations to prevent style bleed.

**Structural equality dedup for `onFormattingStateChanged`.** The callback fires via `snapshotFlow`, which uses structural equality on `FormattingState`. Cursor movement within the same style region does not fire redundant callbacks.

**Toolbar config sentinel check.** The highlight color injection in `CascadeEditor` uses referential equality (`===`) to detect the exact `RichTextToolbarConfig.Default` sentinel. A consumer who creates a `RichTextToolbarConfig` with identical content but different identity will not get automatic highlight color injection.

---

## 7. Glossary

| Term | Definition |
|------|------------|
| **ToolbarSlot** | Sealed type controlling which toolbar variant renders at the bottom of the editor. |
| **ToolbarButtonSpec** | Pairs a `SpanStyle` with a display/accessibility label for one toolbar button. |
| **FormattingState** | Immutable snapshot of which styles are active/partial/absent for the current selection, plus formatting-allowed metadata. |
| **FormattingActions** | Interface for triggering style toggle/apply/remove from UI (toolbar, shortcuts). |
| **SpanActionDispatcher** | Coordination layer ensuring runtime `BlockSpanStates` and snapshot `EditorState` stay consistent on style mutations. |
| **StyleStatus** | Enum: `FullyActive` (style covers entire selection), `Partial` (covers part), `Absent` (not present). |
| **Pending styles** | Runtime-only set of `SpanStyle`s that will be applied to the next character typed at a collapsed cursor. Not persisted in snapshot state. |
| **`kindMatches`** | `SpanStyle.Companion` function that compares styles by kind (ignoring parameters like `colorArgb`), used for toolbar status detection. |
| **Tracked styles** | The list of `SpanStyle`s the formatting observer monitors. Derived from `RichTextToolbarConfig.buttons` or `ToolbarSlot.Custom.trackedStyles`. |
