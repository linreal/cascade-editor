# Toolbar & Span Formatting — Technical Context

## 1. Feature Overview

The toolbar system provides inline editor controls at the bottom of the `CascadeEditor`. It lets users apply span styles (bold, italic, underline, strikethrough, inline code, highlight) to selected text or toggle pending styles at a collapsed cursor, and the built-in toolbar also exposes block indent/outdent controls. The feature is fully configurable: consumers can use the built-in config-driven toolbar, supply a completely custom composable toolbar, hide it entirely, or render a caller-owned external toolbar with `rememberCascadeEditorToolbarController(...)`. Keyboard shortcuts (Cmd/Ctrl+B/I/U) work independently of toolbar visibility, unless read-only mode disables editor-owned formatting mutations. `onFormattingStateChanged` remains available for observation-only external formatting state, while the controller exposes formatting, indentation, and link state/actions from explicit editor runtime holders. CompositionLocals remain available for custom chrome rendered inside `CascadeEditor`.

---

## 2. Architecture & Design Decisions

### Introduced Types

| Type | File | Role |
|------|------|------|
| `ToolbarSlot` | `ui/ToolbarSlot.kt` | Sealed interface controlling which toolbar variant renders: `Default`, `None`, or `Custom`. |
| `CascadeEditorToolbarController` | `ui/CascadeEditorToolbarController.kt` | Public state/action facade for app-owned toolbars rendered outside `CascadeEditor`. |
| `RichTextToolbarConfig` | `ui/RichTextToolbarConfig.kt` | `@Immutable` data class listing `ToolbarButtonSpec` entries plus default-toolbar feature flags. Ships a `Default` companion preset with 6 built-in styles and link enabled. |
| `ToolbarButtonSpec` | `ui/RichTextToolbarConfig.kt` | Pairs a non-link `SpanStyle` with an accessibility `label`. Link editing uses `showLink` instead. |
| `RichTextToolbar` | `ui/RichTextToolbar.kt` | `internal` composable — the default config-driven toolbar UI. Iterates `config.buttons`, renders toggle buttons reflecting `FormattingState`. |
| `FormattingState` | `richtext/FormattingState.kt` | `@Immutable` snapshot: per-style `StyleStatus` map, `canFormat` flag, `focusedBlockId`, `selectionCollapsed`. |
| `FormattingActions` | `richtext/FormattingActions.kt` | `@Stable` interface with `toggleStyle`, `applyStyle`, `removeStyle`. |
| `DefaultFormattingActions` | `richtext/DefaultFormattingActions.kt` | Resolves focused block + selection at invocation time, delegates to `SpanActionDispatcher`. No-op when formatting is disallowed. |
| `FormattingStateCalculator` | `richtext/FormattingStateCalculator.kt` | Pure function: takes raw inputs (focus, selection, spans, pending styles) → `FormattingState`. |
| `rememberFormattingState()` | `richtext/FormattingStateObserver.kt` | Composable bridge producing `State<FormattingState>` via chained `derivedStateOf` layers. |
| `SpanActionDispatcher` | `richtext/SpanActionDispatcher.kt` | Coordinates runtime `BlockSpanStates` update + snapshot `UpdateBlockContent` dispatch in one call. |
| `TextBlockKeyHandler` | `ui/renderers/TextBlockKeyHandler.kt` | Handles Cmd/Ctrl+B/I/U shortcuts and slash popup keyboard navigation. |
| `LocalFormattingActions` | `ui/LocalFormattingActions.kt` | `CompositionLocal` providing `FormattingActions?` to the composition tree. |
| `rememberCascadeEditorToolbarController()` | `ui/CascadeEditorToolbarController.kt` | Public Compose factory that assembles formatting, indentation, and link state/actions from explicit editor runtime holders. |
| `LocalSpanActionDispatcher` | `ui/LocalSpanActionDispatcher.kt` | `CompositionLocal` providing `SpanActionDispatcher?` to the composition tree. |
| `IndentationState` | `indentation/IndentationState.kt` | `@Immutable` snapshot: `canIndentForward`, `canIndentBackward`, and document-ordered target root IDs. |
| `IndentationActions` | `indentation/IndentationActions.kt` | `@Stable` interface with `indentForward()` and `indentBackward()`. |
| `DefaultIndentationActions` | `indentation/DefaultIndentationActions.kt` | Resolves state at invocation time and dispatches structural indent actions only when enabled. |
| `LocalIndentationState` | `ui/LocalIndentationState.kt` | `CompositionLocal` providing `State<IndentationState>?` to custom editor chrome. |
| `LocalIndentationActions` | `ui/LocalIndentationActions.kt` | `CompositionLocal` providing `IndentationActions?` to custom editor chrome. |
| `LinkState` | `richtext/LinkState.kt` | `@Immutable` snapshot of focused link target, existing URL, and mixed-link intersection state. |
| `LinkActions` | `richtext/LinkActions.kt` | `@Stable` interface for apply/edit/remove link mutations against captured `LinkTarget`s or the latest current target. |
| `rememberLinkState()` | `richtext/LinkStateObserver.kt` | `internal` composable bridge producing lazy `State<LinkState>` from editor runtime state. |
| `LocalLinkState` | `ui/LocalLinkState.kt` | `CompositionLocal` providing `State<LinkState>?` to custom editor chrome. |
| `LocalLinkActions` | `ui/LocalLinkActions.kt` | `CompositionLocal` providing gated `LinkChromeActions?` to custom editor chrome. |
| `LinkPopupSlot` | `ui/LinkPopupSlot.kt` | Public slot API for built-in, custom, or disabled editor-owned link popup UI. |
| `LinkPopupState` | `ui/LinkPopupState.kt` | Public UI-ready state shape for custom popup content. |
| `LinkPopupActions` | `ui/LinkPopupActions.kt` | Public action shape for custom popup content. |
| `LinkPopupSession` | `ui/LinkPopupSession.kt` | Internal editor-owned popup session that freezes the target, owns fields, validates URL input, and delegates apply/remove. |
| `LinkPopup` | `ui/LinkPopup.kt` | Internal foundation-only default popup UI, viewport-centered placement helper, and outside-tap dismiss scrim. |

### Key Design Decisions

**Three-variant `ToolbarSlot` sealed interface.** Rather than a boolean toggle + optional composable, the slot is a sealed type with explicit variants. This makes the toolbar contract exhaustive at the call site and in the `when` dispatch inside `CascadeEditor`.

**Config-driven default toolbar.** `RichTextToolbarConfig.Default` defines which buttons appear and in what order. Consumers can add/remove/reorder buttons without implementing a custom composable — just pass a modified `RichTextToolbarConfig`.

**Indentation controls are config-gated but API-always available.** `RichTextToolbarConfig.showIndentation` controls whether the default toolbar renders indent/outdent icon buttons. `CascadeEditor` still provides `LocalIndentationState` and `LocalIndentationActions` in `ToolbarSlot.Default`, `ToolbarSlot.Custom`, and `ToolbarSlot.None`, so hidden or custom toolbars can expose indentation without changing the `ToolbarSlot.Custom` lambda signature.

**Link APIs are local-provided and toolbar-agnostic.** `CascadeEditor` provides `LocalLinkState` and `LocalLinkActions` in `ToolbarSlot.Default`, `ToolbarSlot.Custom`, and `ToolbarSlot.None`. Custom toolbar and surrounding editor chrome can add link UI without changing `ToolbarSlot.Custom` parameters.

**External toolbar controller is caller-owned.** Apps that need a toolbar outside the `CascadeEditor` viewport use `rememberCascadeEditorToolbarController(...)` with the same `EditorStateHolder`, `BlockTextStates`, and `BlockSpanStates` they pass to `CascadeEditor`. The controller exposes formatting, indentation, and link state/actions without rendering UI. Consumers normally pair it with `ToolbarSlot.None`; no `ToolbarSlot.External` variant is needed.

**Default link button is special, not a `ToolbarButtonSpec`.** `RichTextToolbarConfig.showLink` controls the built-in link entry point separately from generic span-style buttons because link editing depends on URL state, mixed-link selection state, and a popup slot rather than a simple style toggle.

**Theme-injected highlight color.** When the consumer uses the exact `RichTextToolbarConfig.Default` sentinel, `CascadeEditor` replaces the placeholder `SpanStyle.Highlight` color with `theme.colors.highlight.toArgb()`. Custom configs are left untouched. This avoids a mismatch between the toolbar button's style and the rendered highlight color.

**`FormattingActions` always created — even without a toolbar.** Keyboard shortcuts (Cmd+B/I/U) consume `FormattingActions` via `LocalFormattingActions`. The actions are created unconditionally so shortcuts work regardless of `ToolbarSlot.None`.

**`FormattingState` lazily created inside `CascadeEditor`.** The editor-owned `rememberFormattingState()` observer chain is only instantiated when `resolvedToolbar !is ToolbarSlot.None || onFormattingStateChanged != null`. This avoids unnecessary `derivedStateOf` overhead in headless/no-toolbar editor configurations. `rememberCascadeEditorToolbarController(...)` creates its own formatting observer because external toolbar state is the requested output of that factory.

**`LinkState` uses a lazy `State` wrapper.** The link local is always provided, but its target resolution is inside `derivedStateOf`; it only computes when a consumer reads `LocalLinkState.current?.value`.

**Two-layer `derivedStateOf` in the formatting observer.** Layer 1 extracts `focusedBlockId`, `focusedBlockType`, `hasBlockSelection`, `isDragging` — each as an independent `derivedStateOf` that only propagates when its specific output changes. Layer 2 reads Layer 1 outputs plus per-block runtime state (selection, spans, pending styles). This shields the final derivation from high-frequency state churn (drag position, text changes in other blocks).

**`SpanActionDispatcher` as the single coordination point.** Toolbar buttons, keyboard shortcuts, and any external code all route through `SpanActionDispatcher`, which performs: (1) runtime `BlockSpanStates` update (immediate visual), then (2) `UpdateBlockContent` dispatch (snapshot sync with current text + spans). Direct dispatch of `ApplySpanStyle`/`RemoveSpanStyle` actions is explicitly prohibited during active editing — these snapshot-only actions would use stale text lengths.

**Collapsed-cursor toggle produces pending styles, not zero-width spans.** When the cursor is collapsed, `toggleStyle` updates `BlockSpanStates.pendingStyles` for the block. Next inserted character inherits pending styles. No snapshot dispatch occurs — pending styles are runtime-only.

**Focus-stealing prevention.** Both `RichTextToolbar` and individual `ToolbarToggleButton` set `Modifier.focusProperties { canFocus = false }`, preventing the toolbar from stealing focus from the active text field on tap.

**Structural history boundary for indentation.** Default indentation actions dispatch `IndentForward` / `IndentBackward` through the same structural transaction wrapper used by built-in semantic document edits when runtime history holders are bound.

**Link actions are gated by link availability.** `LocalLinkActions` exposes a `LinkChromeActions` facade that delegates to `LinkActionDispatcher` only while `LinkState.canLink` is true. If linking is unavailable, `applyLink` still validates the URL for UI feedback but does not mutate, and `removeLink` no-ops. The chrome surface adds `currentTarget()`, `applyLinkAtCurrentTarget(...)`, and `removeLinkAtCurrentTarget()` for callers without a captured target; the latter two are inherited defaults that forward to `applyLink`/`removeLink` after consulting `currentTarget()`. The minimal target-based `LinkActions` interface is the right type for popup sessions and any code path that has already captured a `LinkTarget`.

**Link popup sessions are editor-owned and slot-rendered.** Pressing the default toolbar link button creates a `LinkPopupSession` for `LinkPopupSlot.Default` and `Custom`. The session captures the current `LinkTarget`, owns title/URL field state, validates through `LinkUrlPolicy`, and routes valid apply/remove operations through `LinkActions`. `Default` renders the foundation-only popup, `Custom` receives the same state/actions for consumer UI, and `None` suppresses editor-owned popup state/UI.

**Read-only keeps toolbar visibility separate from edit permission.** `CascadeEditorConfig(readOnly = true)` leaves the default toolbar visible but makes mutating controls disabled or no-op: formatting, indentation, link editing, and toolbar slash insertion. Toolbar visibility is still controlled only by `ToolbarSlot`; use `ToolbarSlot.None` to hide it. `ToolbarSlot.Custom` content remains visible, and editor-provided state/actions are policy-aware, but custom direct writes still need to check `LocalCascadeEditorConfig.current.readOnly`.

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

### Custom Toolbar Link Access

```
ToolbarSlot.Custom content
  └─ val linkState = LocalLinkState.current?.value
  └─ val linkActions = LocalLinkActions.current
  └─ Custom UI uses linkState.canLink / existingUrl / intersectsLink
       and calls linkActions.applyLinkAtCurrentTarget(url, title)
       or captures linkState.target for a popup session and later calls
       linkActions.applyLink(capturedTarget, url, title)
       or linkActions.removeLink(capturedTarget)
```

### Default Toolbar Link Popup

```
User taps default link button
  └─ RichTextToolbar.onLinkClick
       └─ CascadeEditor creates LinkPopupSession from current LinkState
            ├─ Captures LinkTarget for apply/remove
            ├─ Prefills title/URL from existing link, selection text, or selected URL text
            └─ Provides LinkPopupState + LinkPopupActions to Default or Custom slot

Default popup placement
  └─ LinkPopup wraps content in a transparent scrim that fills the editor viewport
       ├─ LinkPopupDefaults.calculatePopupOffset centers popup horizontally and vertically
       │    in the viewport, regardless of cursor position
       ├─ Tap on scrim (outside popup body) → LinkPopupActions.dismiss() (Cancel-equivalent,
       │    no document mutation, focus/selection restored)
       └─ Tap inside popup body is consumed locally so it does not bubble to the scrim

User taps Apply
  └─ LinkPopupSession.apply()
       ├─ Invalid URL → no LinkActions call, popup stays open
       └─ Valid URL → LinkActions.applyLink(capturedTarget, rawUrl, titleOrNull)
            └─ LinkActionDispatcher handles runtime text/spans, snapshot sync, and history

User taps Remove
  └─ LinkPopupSession.remove()
       └─ LinkActions.removeLink(capturedTarget)
            └─ Removes link spans while preserving visible text and non-link spans
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
| `linkPopup` | `LinkPopupSlot` | `LinkPopupSlot.Default` | Controls editor-owned link popup rendering |
| `onOpenLink` | `((String) -> Unit)?` | `null` | App-controlled link opener for unfocused-block taps. When null, the editor falls back to `LocalUriHandler` and swallows platform open failures. |
| `onFormattingStateChanged` | `((FormattingState) -> Unit)?` | `null` | External formatting state listener |
| `config` | `CascadeEditorConfig` | `CascadeEditorConfig.Default` | Cross-cutting behavior config, including read-only mode |

### `ToolbarSlot` (sealed interface)

```kotlin
ToolbarSlot.Default(config: RichTextToolbarConfig = RichTextToolbarConfig.Default)
ToolbarSlot.None
ToolbarSlot.Custom(
    trackedStyles: List<SpanStyle> = ...,  // styles to include in FormattingState.styles
    content: @Composable (State<FormattingState>, FormattingActions) -> Unit,
)
```

### `CascadeEditorToolbarController`

```kotlin
@Stable
interface CascadeEditorToolbarController {
    val formattingState: State<FormattingState>
    val formattingActions: FormattingActions

    val indentationState: State<IndentationState>
    val indentationActions: IndentationActions

    val linkState: State<LinkState>
    val linkActions: LinkChromeActions
}

@Composable
fun rememberCascadeEditorToolbarController(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    trackedStyles: List<SpanStyle> =
        RichTextToolbarConfig.Default.buttons.map { it.style },
    config: CascadeEditorConfig = CascadeEditorConfig.Default,
): CascadeEditorToolbarController
```

Use this controller when toolbar UI must live outside `CascadeEditor`. The
controller does not render UI, does not own link popup placement, and does not
include slash insertion or platform keyboard dismissal helpers.

### `RichTextToolbarConfig` / `ToolbarButtonSpec`

```kotlin
RichTextToolbarConfig(
    buttons: List<ToolbarButtonSpec>,
    showIndentation: Boolean = true,
    showLink: Boolean = true,
)
ToolbarButtonSpec(style: SpanStyle, label: String)
```

`ToolbarButtonSpec` rejects `SpanStyle.Link`. Links require URL validation, captured targets, and popup state, so they are configured through `RichTextToolbarConfig.showLink` and `LinkPopupSlot`.

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

### `LinkState` / `LinkActions`

```kotlin
data class LinkState(
    val canLink: Boolean,
    val focusedBlockId: BlockId?,
    val target: LinkTarget?,
    val targetText: String,
    val selectionCollapsed: Boolean,
    val existingUrl: String?,
    val existingLinkRange: LinkTarget?,
    val existingLinkText: String?,
    val isInsideLink: Boolean,
    val intersectsLink: Boolean,
)

interface LinkActions {
    fun applyLink(target: LinkTarget, url: String, title: String? = null): LinkValidationResult
    fun removeLink(target: LinkTarget)
}

interface LinkChromeActions : LinkActions {
    fun currentTarget(): LinkTarget?
    fun applyLinkAtCurrentTarget(url: String, title: String? = null): LinkValidationResult? =
        currentTarget()?.let { applyLink(it, url, title) }
    fun removeLinkAtCurrentTarget() { currentTarget()?.let { removeLink(it) } }
}
```

`isInsideLink` is true only for a collapsed cursor strictly inside a link span. A ranged selection can expose `existingUrl` without being inside a link. `selectionCollapsed` and `existingLinkText` are cached convenience values derived from the same visible-text snapshot.

### `LinkPopupSlot`

```kotlin
LinkPopupSlot.Default
LinkPopupSlot.None
LinkPopupSlot.Custom(
    content: @Composable (LinkPopupState, LinkPopupActions) -> Unit,
)
```

`Default` selects CascadeEditor's built-in popup, `Custom` keeps the editor-managed session but lets the app render the UI, and `None` disables editor-owned popup UI.

`LinkPopupState` contains the session title, raw URL field value, normalized URL when valid, stable validation error when invalid, existing URL when one was resolved at open time, and `canApply` / `canRemove` flags. `LinkPopupActions` mutates the current session (`updateTitle`, `updateUrl`) or delegates mutations (`apply`, `remove`) against the captured target; `dismiss` closes without document mutation. The default popup also routes outside-tap-dismiss through `dismiss`, so tapping anywhere in the editor viewport outside the popup body is equivalent to pressing Cancel. Custom popup slots may implement their own dismiss UX, but `dismiss` is the canonical no-mutation close.

For collapsed cursors strictly inside an existing link, the popup mutates the full existing link range. For non-collapsed selections, the captured target remains the selected range even if the selection later changes. The default popup is always rendered centered in the editor content viewport rather than anchored to the cursor.

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
| `LocalLinkState` | `State<LinkState>?` | `CascadeEditor` |
| `LocalLinkActions` | `LinkChromeActions?` | `CascadeEditor` |

---

## 5. Integration Points

### Consumed By

| Consumer | What it reads | How |
|----------|---------------|-----|
| `RichTextToolbar` | `State<FormattingState>`, `FormattingActions`, `RichTextToolbarConfig` | Direct parameters from `CascadeEditor` |
| Default toolbar link button | `State<LinkState>`, `RichTextToolbarConfig.showLink`, `LinkPopupSlot` | Direct parameters from `CascadeEditor` |
| `ToolbarSlot.Custom.content` | `State<FormattingState>`, `FormattingActions` | Composable lambda parameters |
| Custom toolbar indentation UI | `LocalIndentationState`, `LocalIndentationActions` | CompositionLocals inside `CascadeEditor` |
| Custom toolbar link UI | `LocalLinkState`, `LocalLinkActions` | CompositionLocals inside `CascadeEditor` |
| `TextBlockKeyHandler` | `FormattingActions` | Constructor parameter (sourced from `LocalFormattingActions`) |
| External observation-only app code | `FormattingState` | `onFormattingStateChanged` callback |
| External app-owned toolbar | Formatting, indentation, and link state/actions | `rememberCascadeEditorToolbarController(...)` with the same runtime holders passed to `CascadeEditor` |

### Depends On

| Dependency | Role |
|------------|------|
| `EditorStateHolder` | Source of `focusedBlockId`, block types, selection, drag state |
| `BlockTextStates` | Visible text + cursor selection for the focused block |
| `BlockSpanStates` | Runtime span state: spans, pending styles, style queries |
| `SpanAlgorithms` | Pure functions for `queryStyleStatus`, `activeStylesAt` |
| `LinkStateCalculator` | Focused-block link target, existing URL, and intersection state |
| `LinkActionDispatcher` | Runtime/snapshot/history coordination for link mutations |
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
        LinkPopupSlot.Default/Custom content
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
- The editor is read-only

**`canLink` disablement conditions.** Link state is disabled under the same editor-level guards as formatting: no focused text block, block selection active, drag in progress, or read-only mode. Read-only link state still preserves non-mutating metadata for custom chrome when available. `LocalLinkActions` also checks this state at invocation time before mutating.

**Indentation enablement is independent of formatting enablement.** Indent/outdent buttons use `IndentationState`, not `FormattingState.canFormat`. They can target focused supported blocks or selected supported root blocks and no-op when structural outline rules, selection/focus state, or read-only mode disallow movement.

**`showIndentation = false` hides only default toolbar buttons.** It does not disable indentation reducers or remove `LocalIndentationState` / `LocalIndentationActions` from custom toolbar content.

**Toolbar visibility does not affect link locals.** `LocalLinkState` and `LocalLinkActions` are provided in default, custom, and no-toolbar modes. `ToolbarSlot.None` hides editor-owned toolbar UI only.

**Read-only does not hide the iOS hide-keyboard button.** The iOS default toolbar button remains visible and dispatches `ClearFocus`; read-only fields usually do not open the soft keyboard, so this is mostly a no-op. Use `ToolbarSlot.Custom` if the app wants to hide it.

**`showLink = false` hides only the default toolbar link button.** It does not remove `LocalLinkState`, `LocalLinkActions`, or the `linkPopup` parameter. Apps that own all link chrome should usually combine `ToolbarSlot.Custom` or `RichTextToolbarConfig(showLink = false)` with `LinkPopupSlot.None`.

**Highlight color identity.** `SpanStyle.Highlight` uses `kindMatches` for toolbar status detection — any `Highlight` regardless of `colorArgb` is treated as the same "kind". This means the toolbar correctly reflects highlight status even when the theme color differs from the stored span color. However, applying highlight via the toolbar always uses the theme color, potentially creating spans with different `colorArgb` values in the same document.

**Collapsed-cursor pending styles are runtime-only.** Pending styles live in `BlockSpanStates` memory and are not part of `EditorState` snapshots. They survive within a composition session but are lost if `BlockSpanStates` is recreated. They are also cleared on block merge operations to prevent style bleed.

**Structural equality dedup for `onFormattingStateChanged`.** The callback fires via `snapshotFlow`, which uses structural equality on `FormattingState`. Cursor movement within the same style region does not fire redundant callbacks.

**Toolbar config sentinel check.** The highlight color injection in `CascadeEditor` uses referential equality (`===`) to detect the exact `RichTextToolbarConfig.Default` sentinel. A consumer who creates a `RichTextToolbarConfig` with identical content but different identity will not get automatic highlight color injection.

---

## 7. Glossary

| Term | Definition |
|------|------------|
| **ToolbarSlot** | Sealed type controlling which toolbar variant renders at the bottom of the editor. |
| **ToolbarButtonSpec** | Pairs a non-link `SpanStyle` with a display/accessibility label for one toolbar button. |
| **FormattingState** | Immutable snapshot of which styles are active/partial/absent for the current selection, plus formatting-allowed metadata. |
| **FormattingActions** | Interface for triggering style toggle/apply/remove from UI (toolbar, shortcuts). |
| **SpanActionDispatcher** | Coordination layer ensuring runtime `BlockSpanStates` and snapshot `EditorState` stay consistent on style mutations. |
| **LinkState** | Immutable snapshot of the current focused link target and existing-link metadata. |
| **LinkActions** | Interface for applying, editing, and removing links at captured or current `LinkTarget`s. |
| **StyleStatus** | Enum: `FullyActive` (style covers entire selection), `Partial` (covers part), `Absent` (not present). |
| **Pending styles** | Runtime-only set of `SpanStyle`s that will be applied to the next character typed at a collapsed cursor. Not persisted in snapshot state. |
| **`kindMatches`** | `SpanStyle.Companion` function that compares styles by kind (ignoring parameters like `colorArgb`), used for toolbar status detection. |
| **Tracked styles** | The list of `SpanStyle`s the formatting observer monitors. Derived from `RichTextToolbarConfig.buttons` or `ToolbarSlot.Custom.trackedStyles`. |
