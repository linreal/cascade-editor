# CascadeEditor Architecture

Block-based editor (Craft/Notion-like) for Compose Multiplatform. Unidirectional data flow: actions → reducers → state → recomposition.

## Quick Reference

| Concept | File | Key Symbol |
|---------|------|------------|
| Main composable | `ui/CascadeEditor.kt` | `CascadeEditor(stateHolder, textStates, spanStates, registry, slashRegistry, slashCommand, ...)` |
| Editor behavior config | `ui/CascadeEditorConfig.kt`, `ui/LocalCascadeEditorConfig.kt` | `CascadeEditorConfig`, `LocalCascadeEditorConfig` |
| Editor interaction policy | `ui/EditorInteractionPolicy.kt`, `ui/LocalEditorInteractionPolicy.kt` | `EditorInteractionPolicy` (internal) |
| Text input | `ui/BackspaceAwareTextEdit.kt` | `BackspaceAwareTextField()`, `TextFieldState.selectedVisibleText()` |
| Shared text field | `ui/renderers/TextBlockField.kt` | `TextBlockField()` |
| Text renderer | `ui/renderers/TextBlockRenderer.kt` | `TextBlockRenderer` |
| Block indentation rendering helper | `ui/renderers/BlockIndentationModifier.kt` | `withBlockIndentation()` (internal) |
| Indentation animation tokens | `ui/IndentationAnimation.kt` | `IndentationAnimation` (internal) |
| Ordered-list prefix formatter | `ui/renderers/OrderedListPrefixFormatter.kt` | `formatOrderedListPrefix()` (internal) |
| Todo renderer | `ui/renderers/TodoBlockRenderer.kt` | `TodoBlockRenderer` |
| Divider renderer | `ui/renderers/DividerBlockRenderer.kt` | `DividerBlockRenderer` |
| Unknown block renderer | `ui/renderers/UnknownBlockRenderer.kt` | `UnknownBlockRenderer` (internal, via `BlockRegistry.setUnknownBlockRenderer`) |
| Editor registry setup | `ui/EditorRegistry.kt` | `createEditorRegistry()` |
| Drop indicator | `ui/DropIndicator.kt` | `DropIndicator()` |
| Drag preview | `ui/DragPreview.kt` | `DragPreview()` |
| Block gestures (tap, drag, selection) | `ui/BlockGestureModifier.kt` | — |
| Auto-scroll | `ui/AutoScrollEffect.kt` | `AutoScrollDuringDrag()` |
| Drag hover utils | `ui/utils/DragUtils.kt` | `calculateDropTargetIndex()`, `resolveDepthAwareDragHoverTarget()`, `recomputeDepthAwareDragHoverTarget()` |
| Text state local | `ui/LocalBlockTextStates.kt` | `LocalBlockTextStates` |
| Span state local | `ui/LocalBlockSpanStates.kt` | `LocalBlockSpanStates` |
| State snapshot | `state/EditorState.kt` | `EditorState`, `DragState`, `SlashCommandState`, `SlashQueryRange` |
| Slash command ID | `slash/SlashCommandId.kt` | `SlashCommandId` |
| Slash command model | `slash/SlashCommandModel.kt` | `SlashCommandItem`, `SlashCommandAction`, `SlashCommandMenu`, `SlashCommandIconKey`, `SlashQueryTextPolicy`, `SlashCommandResult` |
| Slash command context | `slash/SlashCommandContext.kt` | `SlashCommandContext`, `SlashCommandEditor` |
| Slash command registry | `slash/SlashCommandRegistry.kt` | `SlashCommandRegistry` |
| State holder | `state/EditorStateHolder.kt` | `EditorStateHolder`, `rememberEditorState()` |
| History model | `state/EditorHistory.kt` | `HistoryManager`, `StructuralEntry`, `BlockTextEntry`, `EditorCheckpoint`, `EditingUiState` |
| History UI helpers | `state/EditorHistoryUiState.kt` | `captureFocusedEditingUiState()`, `restoreFocusedEditingUiState()` |
| Text history capture | `state/EditorTextHistory.kt` | `TextEditHistoryTracker`, `TextEditCoalescer` |
| Block-text history helpers | `state/EditorHistoryBlockText.kt` | `buildHistoryEntryFromCheckpoints()`, `applyBlockTextEntry()` |
| History checkpoint helpers | `state/EditorHistoryCheckpoint.kt` | `captureCheckpoint()`, `applyCheckpoint()` |
| Text state manager | `state/BlockTextStates.kt` | `BlockTextStates` |
| Span state manager | `state/BlockSpanStates.kt` | `BlockSpanStates` |
| All actions | `action/EditorAction.kt` | `sealed class EditorAction` |
| Block model | `core/Block.kt` | `Block`, factory methods |
| Block attributes | `core/BlockAttributes.kt` | `BlockAttributes`, indentation depth constants |
| Block types | `core/BlockType.kt` | `sealed interface BlockType` |
| Block content | `core/BlockContent.kt` | `sealed interface BlockContent` |
| Span style | `core/SpanStyle.kt` | `sealed interface SpanStyle` |
| Text span | `core/TextSpan.kt` | `TextSpan` |
| Block ID | `core/BlockId.kt` | `BlockId` |
| List utilities | `core/ListUtils.kt` | `renumberNumberedLists()` (internal) |
| Outline utilities | `core/OutlineUtils.kt` | `shiftIndentation()` (internal), `resolveDragPayload()` (internal), `moveDragPayload()` (internal), `IndentationDirection` (internal) |
| Registry | `registry/BlockRegistry.kt` | `BlockRegistry` |
| Descriptors | `registry/BlockDescriptor.kt` | `BlockDescriptor` |
| Built-in slash spec | `slash/BuiltInSlashCommandSpec.kt` | `BuiltInSlashCommandSpec`, `BuiltInBlockSlashBehavior` |
| Built-in slash factory | `slash/BuiltInSlashCommandFactory.kt` | `BuiltInSlashCommandFactory` |
| Built-in slash executor | `slash/SlashCommandExecutor.kt` | `createBuiltInSlashExecutor()` (internal) |
| Slash editor host | `slash/SlashCommandEditorHost.kt` | `SlashCommandEditorHost` (internal) |
| List auto-detect observer | `ui/observers/ListAutoDetectObserver.kt` | `ListAutoDetectObserver` (internal) |
| Slash text observer | `slash/SlashCommandTextObserver.kt` | `SlashCommandTextObserver` (internal) |
| Renderer interface | `registry/BlockRenderer.kt` | `BlockRenderer<T>` (+ `handlesSelectionVisual`), `BlockCallbacks`, `DefaultBlockCallbacks` |
| Policy-aware renderer callbacks | `registry/PolicyAwareBlockCallbacks.kt` | `PolicyAwareBlockCallbacks` (internal) |
| Unknown block type | `core/UnknownBlockType.kt` | `UnknownBlockType` (implements `CustomBlockType`) |
| Document serialization | `serialization/DocumentSchema.kt` | `DocumentSchema` (encode/decode full document) |
| Rich text serialization | `serialization/RichTextSchema.kt` | `RichTextSchema` |
| Link URL policy | `richtext/LinkUrlPolicy.kt` | `LinkUrlPolicy`, `LinkValidationResult`, `LinkValidationError` |
| Link hit tester | `richtext/LinkHitTester.kt` | `LinkHitTester` (internal) |
| Link state | `richtext/LinkState.kt` | `LinkState`, `LinkTarget` |
| Link state calculator | `richtext/LinkStateCalculator.kt` | `LinkStateCalculator` (internal) |
| Link state observer | `richtext/LinkStateObserver.kt` | `rememberLinkState()` (internal) |
| Link actions | `richtext/LinkActions.kt`, `richtext/LinkChromeActions.kt` | `LinkActions`, `LinkChromeActions` |
| Link action dispatcher | `richtext/LinkActionDispatcher.kt` | `LinkActionDispatcher` |
| Link locals | `ui/LocalLinkState.kt`, `ui/LocalLinkActions.kt` | `LocalLinkState`, `LocalLinkActions` |
| Doc serialization types | `serialization/BlockIdMode.kt` | `BlockIdMode` |
| Doc serialization types | `serialization/DuplicateIdMode.kt` | `DuplicateIdMode` |
| Doc serialization types | `serialization/CustomDataMode.kt` | `CustomDataMode` |
| Doc encode options | `serialization/DocumentEncodeOptions.kt` | `DocumentEncodeOptions` |
| Doc decode options | `serialization/DocumentDecodeOptions.kt` | `DocumentDecodeOptions` |
| Doc decode warnings | `serialization/DocumentDecodeWarning.kt` | `DocumentDecodeWarning` (sealed class) |
| Doc decode result | `serialization/DocumentDecodeResult.kt` | `DocumentDecodeResult` |
| Block type codec | `serialization/BlockTypeCodec.kt` | `BlockTypeCodec` |
| Block content codec | `serialization/BlockContentCodec.kt` | `BlockContentCodec` |
| Editor serialization ext | `serialization/DocumentSerializationExt.kt` | `EditorStateHolder.toJson()`, `EditorStateHolder.loadFromJson()` |
| Editor HTML serialization ext | `htmlserialization/HtmlSerializationExt.kt` | `EditorStateHolder.toHtml()`, `EditorStateHolder.loadFromHtml()` |
| HTML schema entry point | `htmlserialization/HtmlSchema.kt` | `HtmlSchema` (`decode` and `encode` wired through profile-driven engines) |
| HTML parser internals | `htmlserialization/HtmlParser.kt`, `htmlserialization/HtmlPolicyApplier.kt`, `htmlserialization/HtmlTokenizer.kt`, `htmlserialization/HtmlTreeBuilder.kt`, `htmlserialization/HtmlNode.kt`, `htmlserialization/HtmlToken.kt` | `HtmlParser.parse()` (internal), `HtmlPolicyApplier`, `HtmlNode`, `HtmlToken` |
| HTML decode engine | `htmlserialization/HtmlDecodeEngine.kt`, `htmlserialization/TagDecodeContextImpl.kt`, `htmlserialization/HtmlNodeViewMapper.kt`, `htmlserialization/DefaultTagDecoders.kt`, `htmlserialization/PreservedHtmlBlockType.kt` | `HtmlDecodeEngine` (internal; normalizes indentation + numbered lists after decode), `TagDecodeContextImpl`, default tag decoders, `PreservedHtmlBlockType`; default list-item decoders honor `class="cascade-indent-N"` as the lossless default-profile list-depth escape hatch, while default `<ul>` / `<ol>` containers delegate to a profile-level custom `<li>` decoder when one is registered |
| HTML encode engine | `htmlserialization/HtmlEncodeEngine.kt`, `htmlserialization/HtmlEncodeContextImpl.kt`, `htmlserialization/DefaultBlockEncoders.kt`, `htmlserialization/DefaultSpanEncoders.kt`, `htmlserialization/DefaultListOutlineEncoder.kt`, `htmlserialization/DefaultEncoderFallbacks.kt` | `HtmlEncodeEngine` (internal), `HtmlEncodeContextImpl`, canonical default block/span encoders, one default `listOutline` encoder for mixed bullet/numbered runs, default block/span encode fallbacks |
| HTML profile config | `htmlserialization/HtmlProfile.kt` | `HtmlProfile`, `HtmlProfile.Default` (immutable builder-style with default tag decoders, canonical encode mappings, encode fallbacks, and `withSupportSet`) |
| HTML support set | `htmlserialization/HtmlProfileSupportSet.kt` | `HtmlProfileSupportSet` (public predicate-based support-set constructor; `supportsBlock`/`supportsSpan`/`supportsDocument`; document checks reject stale numbering, invalid indentation outlines, and non-round-trippable content shapes) |
| Custom reference HTML profile | `sample/src/commonMain/kotlin/io/github/linreal/cascade/profiles/CustomHtmlProfile.kt` | Sample-only `CustomHtmlProfile.Profile` composed from public HTML APIs; flat `ql-indent-N` lists and custom link attributes |
| HTML codec contracts | `htmlserialization/HtmlCodecContracts.kt` | `HtmlNodeView`, `TagDecoder`, `TagDecodeContext`, `TagDecodeResult`, `InlineFragment`, `BlockEncoder`, `SpanEncoder`, `BlockGroupEncoder`, `HtmlEncodeContext`, `HtmlEmit`, `HtmlTagPair` |
| HTML parser policies | `htmlserialization/HtmlPolicies.kt` | `BlockSeparator`, `InlineRoot`, `EntityDecode`, `UnknownTagPolicy` |
| HTML escaping helpers | `htmlserialization/HtmlEscaping.kt` | `Html.escapeText()`, `Html.escapeAttr()` |
| HTML decode/encode results | `htmlserialization/HtmlResults.kt`, `htmlserialization/HtmlWarnings.kt` | `HtmlDecodeResult`, `HtmlEncodeResult`, `HtmlDecodeWarning`, `HtmlEncodeWarning` |
| Span algorithms | `richtext/SpanAlgorithms.kt` | `SpanAlgorithms`, `StyleStatus` |
| Span mapper | `richtext/SpanMapper.kt` | `SpanMapper` |
| Span edit observer | `richtext/SpanMaintenanceTextObserver.kt` | `SpanMaintenanceTextObserver` |
| Span action dispatcher | `richtext/SpanActionDispatcher.kt` | `SpanActionDispatcher` |
| Span dispatcher local | `ui/LocalSpanActionDispatcher.kt` | `LocalSpanActionDispatcher` |
| Formatting actions local | `ui/LocalFormattingActions.kt` | `LocalFormattingActions` |
| Indentation state | `indentation/IndentationState.kt` | `IndentationState` |
| Indentation actions | `indentation/IndentationActions.kt` | `IndentationActions` |
| Indentation state calculator | `indentation/IndentationStateCalculator.kt` | `IndentationStateCalculator` (internal), `rememberIndentationState()` (internal) |
| Indentation actions impl | `indentation/DefaultIndentationActions.kt` | `DefaultIndentationActions` (internal) |
| Indentation locals | `ui/LocalIndentationState.kt`, `ui/LocalIndentationActions.kt` | `LocalIndentationState`, `LocalIndentationActions` |
| Keyboard handler | `ui/renderers/TextBlockKeyHandler.kt` | `TextBlockKeyHandler` |
| Formatting state | `richtext/FormattingState.kt` | `FormattingState` |
| Formatting actions | `richtext/FormattingActions.kt` | `FormattingActions` |
| Toolbar slot | `ui/ToolbarSlot.kt` | `ToolbarSlot` |
| Toolbar config | `ui/RichTextToolbarConfig.kt` | `RichTextToolbarConfig`, `ToolbarButtonSpec` |
| Link popup slot | `ui/LinkPopupSlot.kt` | `LinkPopupSlot` |
| Link popup contract | `ui/LinkPopupState.kt`, `ui/LinkPopupActions.kt` | `LinkPopupState`, `LinkPopupActions` |
| Link popup session/UI | `ui/LinkPopupSession.kt`, `ui/LinkPopup.kt` | `LinkPopupSession` (internal), `LinkPopup()` (internal), `LinkPopupDefaults.calculatePopupOffset(...)` (internal viewport-centered placement) |
| Link opener local | `ui/LinkOpener.kt` | `LocalLinkOpener`, `createLinkOpener(...)` (internal) |
| Default toolbar UI | `ui/RichTextToolbar.kt` | Formatting buttons plus indent/outdent/link buttons |
| Formatting calculator | `richtext/FormattingStateCalculator.kt` | `FormattingStateCalculator` |
| Formatting observer | `richtext/FormattingStateObserver.kt` | `rememberFormattingState()` |
| Formatting actions impl | `richtext/DefaultFormattingActions.kt` | `DefaultFormattingActions` |
| Hide keyboard button | `ui/HideKeyboardToolbarButton.kt` | `HideKeyboardToolbarButton()` (public, iOS-only in default toolbar) |
| Platform detection | `Platform.kt` | `internal expect val isIos: Boolean` |
| Slash popup defaults | `ui/SlashPopupDefaults.kt` | `SlashPopupDefaults` |
| Slash popup overlay | `ui/SlashCommandPopup.kt` | `SlashCommandPopup()` |
| Slash command row | `ui/SlashCommandRow.kt` | `SlashCommandRow()` |
| Slash caret rect local | `ui/LocalSlashCaretRect.kt` | `LocalSlashCaretRect`, `SlashCaretRectHolder` |
| Slash registry local | `ui/LocalSlashCommandRegistry.kt` | `LocalSlashCommandRegistry` |
| Slash popup items local | `ui/LocalSlashPopupItems.kt` | `LocalSlashPopupItems` |
| Slash command slot | `ui/SlashCommandSlot.kt` | `SlashCommandSlot` (`Default`, `None`) |
| Slash enabled local | `ui/LocalSlashCommandsEnabled.kt` | `LocalSlashCommandsEnabled` (internal) |
| Theme colors | `theme/CascadeEditorColors.kt` | `CascadeEditorColors` |
| Theme typography | `theme/CascadeEditorTypography.kt` | `CascadeEditorTypography` |
| Theme dimensions | `theme/CascadeEditorDimensions.kt` | `CascadeEditorDimensions` |
| Theme top-level | `theme/CascadeEditorTheme.kt` | `CascadeEditorTheme` |
| Theme local | `theme/LocalCascadeTheme.kt` | `LocalCascadeTheme` |
| UI strings | `theme/CascadeEditorStrings.kt` | `CascadeEditorStrings` |
| Block strings | `theme/CascadeEditorBlockStrings.kt` | `CascadeEditorBlockStrings`, `BlockLocalizedStrings` |
| Strings locals | `theme/LocalCascadeStrings.kt` | `LocalCascadeStrings`, `LocalCascadeBlockStrings` |

Unless a row uses an explicit module-root path such as `sample/src/...`, paths are relative to `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/`.

## Layer Diagram

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
│  Core Layer (Block, BlockType, BlockContent, TextSpan)   │
└─────────────────────────────────────────────────────────┘
```

## Core Concepts

**Block** = id (`BlockId`) + type (`BlockType`) + content (`BlockContent`) + attributes (`BlockAttributes`). Factory methods: `Block.paragraph()`, `Block.heading()`, `Block.todo()`, `Block.bulletList()`, `Block.numberedList()`, `Block.divider()`.

**BlockAttributes** — block-level document metadata that is independent of `BlockType`. V1 stores `indentationLevel` with default `0` and validates persisted depth against the module-internal indentation range (`0..3`). `Block.attributes` is a trailing constructor property with `BlockAttributes.Default`, so existing `Block(id, type, content)` call sites remain source-compatible. Use `Block.withAttributes(...)` or `copy(attributes = ...)` when changing attributes. Structural reducers preserve indentation when the resulting block type supports indentation and clear indentation to `0` when converting into unsupported block types.

**Document serialization** — `DocumentSchema.CURRENT_VERSION` is `2`. Version 2 can write a block-level `attributes.indentationLevel` field; default depth `0` is omitted on encode, and unsupported block types always encode as unindented. Version 1 and missing-version documents decode with default attributes when the field is absent. Malformed, non-integer, out-of-range, or structurally invalid indentation emits `DocumentDecodeWarning.InvalidBlockAttributeParam` and falls back to the normalized depth. Decode clears indentation on block types that do not support indentation, normalizes the flat outline, then runs depth-aware numbered-list renumbering after attributes are applied.

**BlockType** — sealed interface:

| Type | Supports Text | Supports Indentation | Supports Spans | Notes |
|------|:---:|:---:|:---:|-------|
| `Paragraph` | Yes | Yes | Yes | Default block type |
| `Heading(level)` | Yes | No | Yes | H1-H6 |
| `Todo(checked)` | Yes | Yes | Yes | Has `checked` boolean |
| `BulletList` | Yes | Yes | Yes | |
| `NumberedList(number)` | Yes | Yes | Yes | Has `number` int (>= 1, default 1) |
| `Quote` | Yes | No | Yes | |
| `Code` | Yes | No | No | Plain monospace block; multi-line text with verbatim `\n` |
| `Divider` | No | No | No | |

`supportsSpans` is an orthogonal capability flag on the sealed interface with default `get() = supportsText`. `BlockType.Code` is the only built-in that opts out while still supporting text — formatting toolbar, link button, Cmd/Ctrl+B/I/U, span reducers, span persistence, and runtime span observers all gate on `supportsSpans`. `ConvertBlockType` preserves `BlockContent.Text.text` across conversion and clears `spans` to `emptyList()` when the target type has `supportsSpans = false` (covers Paragraph/Heading/List/Todo/Quote → Code). Custom blocks: implement `CustomBlockType` interface; non-spans plain-text custom blocks can override `supportsSpans = false` and inherit the same gating for free.

**BlockContent** — `Text(text, spans)` | `Empty` | `Custom(typeId, data)`.

**Numbered list renumbering** — `renumberNumberedLists()` stores numbering in `BlockType.NumberedList(number)` and recomputes it after structural changes. The algorithm is outline-aware over the flat `List<Block>` model: sequences are scoped by indentation depth and derived parent, deeper descendants do not break ancestor-depth numbering, same-depth non-numbered supported blocks reset the sequence for their own derived parent, and unsupported blocks reset the outline segment entirely. Unchanged blocks preserve referential identity, and the original list instance is returned when no numbers need changing.

**Indent/outdent reducers** — `IndentForward` and `IndentBackward` are structural actions over the flat outline model. Selection takes precedence over focus; selected unsupported blocks are ignored; selected supported descendants of another selected supported root are shifted once through the ancestor subtree. Focus mode targets only the focused supported block. Unsupported blocks are hard outline boundaries: they do not own descendants, terminate preceding supported subtrees, and reset the nearest-supported-depth validation segment. Successful shifts update supported target roots plus supported descendants, validate first-block/root-gap/max-depth rules, preserve focus and selection, normalize structural reducers that can orphan descendants, and then renumber ordered lists.

**Subtree drag payloads** — `StartDrag` resolves a semantic payload from the flat outline before any blocks move. Single-block drags use the touched block as the root and include its full subtree. Selection drags use selected roots that are not descendants of another selected root, then include each root subtree once. `DragState.payloadBlockIds` is the ordered payload contract; `payloadBlockIdSet`, `payloadBlockIndices`, `payloadBlockIndexSet`, and `payloadIndexRanges` cache membership and original ranges for frame-rate hover resolution. `draggingBlockIds` remains a legacy containment set for existing UI checks. `DragState.primaryRootId` is the root that contains the block that started the gesture, even when another selected root appears earlier in document order. `resolveDepthAwareDragHoverTarget(...)` resolves both the visual gap and `futureRootIndentationLevel`; invalid gaps clear `targetIndex` so `CompleteDrag` cannot mutate the document. Unsupported primary roots keep future depth `0` regardless of horizontal drag movement.

**Subtree drag moves** — `MoveDragPayload` is the drag-specific structural reducer. It removes payload blocks, inserts them at the visual gap after removal, applies one primary-root depth delta to every payload block, validates the resulting outline, and renumbers ordered lists. Drops into a gap inside the payload are rejected by returning the original block list. `MoveBlocks` remains the flat reorder primitive for non-drag callers and does not perform drag-style depth rewriting, but structural normalization can still adjust invalid outlines after the move. `convertVisualGapToMoveBlocksIndex(...)` remains public as a deprecated compatibility shim for legacy flat drag integrations.

**Public indentation API** — custom chrome reads `LocalIndentationState` (`State<IndentationState>?`) and `LocalIndentationActions` (`IndentationActions?`) from inside `CascadeEditor`. `IndentationState.targetBlockIds` contains supported root targets in document order, not moved descendants. `IndentationStateCalculator` is internal and pure; it reuses reducer target resolution plus `shiftIndentation(...)` validation so enablement matches the actual `IndentForward`/`IndentBackward` reducers. Read-only policy disables the indentation booleans while keeping resolved targets available for inspection. `DefaultIndentationActions` evaluates state at invocation time, gates dispatch through `EditorInteractionPolicy.canEditBlockStructure`, and dispatches indentation through the structural history wrapper when runtime history holders are bound. `ToolbarSlot.Custom` stays source-compatible; indentation is intentionally exposed through locals rather than new lambda parameters.

**Theme dimensions and indentation animation** — `CascadeEditorTheme` combines colors, typography, and `CascadeEditorDimensions`. `CascadeEditorDimensions.indentUnit` is the shared visual depth unit used by supported block renderers, the drop indicator, and drag preview, while `blockHorizontalPadding` is the normal editor-list horizontal padding token. `IndentationAnimation` centralizes the 150 ms FastOutSlowIn tween used by normal indentation, drop indicator depth/Y movement, and drag preview badge indentation. Normal rendering animates supported block indentation by applying `indentationLevel * indentUnit` as a leading inset; unsupported block types ignore hidden indentation during rendering.

**TextSpan** — `TextSpan(start, end, style)` with half-open `[start, end)` visible coordinates. Validates `start >= 0` and `end >= start`.

**SpanStyle** — sealed interface: `Bold`, `Italic`, `Underline`, `StrikeThrough`, `InlineCode`, `Highlight(colorArgb)`, `Link(url)`, `Custom(typeId, payload?)`. `Link.url` stores only the normalized target URL; the visible link title remains the covered `TextSpan` range. Use `LinkUrlPolicy.validate(...)` before constructing link styles. `SpanStyle.kindKey(Link(url))` keeps URL identity so same-URL links can merge while different URLs remain distinct; URL-agnostic link remove/query behavior is implemented as an operation-matching rule inside `SpanAlgorithms`, not by changing the merge key. `Custom.payload` is opaque `String?` (raw JSON); core layer must not parse it.

**LinkUrlPolicy** — permissive Slack-style URL normalization for link creation. Anything non-blank is accepted: inputs that already carry a `<scheme>://` prefix are kept verbatim after trimming, everything else is normalized by prepending `https://`. The only `LinkValidationError` is `Blank`. Platform URL opening, link persistence, rendering, mutation actions, and allowlists are separate feature layers.

**Public link API** — custom chrome reads `LocalLinkState` (`State<LinkState>?`) and `LocalLinkActions` (`LinkChromeActions?`) from inside `CascadeEditor`. These locals are provided regardless of `ToolbarSlot.Default`, `ToolbarSlot.Custom`, or `ToolbarSlot.None` so link UI does not require a new custom-toolbar lambda parameter. `LocalLinkState` is backed by lazy derived state over focused editor/text/span inputs. Read-only policy sets `LinkState.canLink` to false while preserving focused-block target and existing-link inspection metadata when available. The link API is split: `LinkActions` is the minimal target-based mutation surface (`applyLink(target, ...)`, `removeLink(target)`) used by popup sessions and any caller that already captured a target; `LinkChromeActions extends LinkActions` adds `currentTarget()` plus `applyLinkAtCurrentTarget(...)` / `removeLinkAtCurrentTarget()` sugar for chrome that reads live `LinkState`. `LocalLinkActions` exposes a gated `LinkChromeActions` facade over `LinkActionDispatcher`: when current `LinkState.canLink` or `EditorInteractionPolicy.canEditLinks` is false, apply still returns URL validation feedback but does not mutate, and remove no-ops. The default toolbar has a special link entry point controlled by `RichTextToolbarConfig.showLink`; editor-owned popup presentation is selected through `CascadeEditor(linkPopup = LinkPopupSlot.Default/Custom/None)`, and `LinkPopupController.open()` also gates on `canEditLinks` before creating a popup session. `LinkPopupSlot.Default` uses the foundation-only default `LinkPopup`, `Custom` receives the editor-managed `LinkPopupState` / `LinkPopupActions`, and `None` suppresses editor-owned popup state/UI. Link opening is configured separately through `CascadeEditor(onOpenLink)`: consumer callbacks receive normalized URLs and may throw, while the default `LocalUriHandler` path swallows platform opening failures.

## State Management

**EditorState** — immutable snapshot: `blocks`, `focusedBlockId`, `selectedBlockIds`, `dragState`, `slashCommandState`. Cursor position is NOT in EditorState — it lives in `TextFieldState` managed by `BlockTextStates`. **Invariant:** `focusedBlockId` and `selectedBlockIds` are mutually exclusive — enforced by reducers, not UI code. Selection reducers clear focus; focus reducers (with non-null target) clear selection. `ClearFocus` and `ClearSelection` are orthogonal and do not enforce this on each other.

**Editor configuration and interaction policy** — `CascadeEditorConfig` is a public behavior config passed as the final defaulted `CascadeEditor` parameter and exposed to custom renderer/chrome content through `LocalCascadeEditorConfig`. It does not replace direct ownership, styling, registry, slot, state, or callback parameters. Built-in code converts it once with `CascadeEditorConfig.toInteractionPolicy()` inside `CascadeEditor` and provides the resulting internal `EditorInteractionPolicy` through `LocalEditorInteractionPolicy`; built-in policy checks should use named capabilities from that policy instead of reading `config.readOnly` directly. Formatting, indentation, and link calculators/actions receive the policy explicitly from `CascadeEditor` so custom chrome locals expose disabled state and gated action facades from the same policy source. Their action objects also read policy through invocation-time providers, so stale formatting, indentation, link, link-popup, and toolbar-slash callbacks captured before a runtime policy change cannot keep mutating after the current policy disables them. Text-block input reads the internal policy in `TextBlockField`, forwards `readOnly = !canEditText` into the shared state-based `BackspaceAwareTextField`, and gates side-effectful Enter, sentinel-backspace, paste-history, formatting-shortcut, editor-history-shortcut, and list auto-detect paths before they reach mutation callbacks or text-history batching. The slash subsystem is gated once in `CascadeEditor` from `SlashCommandSlot` plus `canUseSlashCommands`; observer construction, popup rendering, key handling, and executor wiring use that gate, while the default toolbar slash button also requires `canEditText` before it can write to the focused text field. Block-level gesture wiring receives the policy explicitly in `blockGestures(...)`; read-only mode omits the editor-owned pointer detector, gates drag/selection/drop-target dispatch, suppresses empty-space edit focus, and renders drag auto-scroll/drop/preview affordances only while `canDragBlocks` is true. `CascadeEditor` runs one transition cleanup when the active policy disables editor-owned mutation workflows: it closes slash sessions, cancels drags, clears block selection, and dismisses editor-owned link popup sessions without changing document blocks or text focus. `TodoBlockRenderer` reads `LocalEditorInteractionPolicy` and resolves policy/callbacks at checkbox invocation time so stale checkbox lambdas cannot toggle todo state after the policy changes. The read-only policy disables every current interaction capability, while reducers and app-owned mutation surfaces remain outside this UI policy boundary.

**Read-only boundary** — read-only mode is documented in [`docs/ReadOnlyMode.md`](docs/ReadOnlyMode.md). It is intentionally a `CascadeEditor` UI boundary, not reducer-level authorization. App-owned calls such as `EditorStateHolder.dispatch(...)`, `setState(...)`, `undo()`, `redo()`, `loadFromJson(...)`, `loadFromHtml(...)`, autosave, remote sync, and direct `BlockTextStates` / `BlockSpanStates` / `TextFieldState` writes remain mutable unless the application gates them. Custom renderer and chrome code should read `LocalCascadeEditorConfig.current.readOnly`; built-in code should continue to use the internal policy.

**EditorStateHolder** — Compose-friendly mutable wrapper. Use `rememberEditorState(initialBlocks)` to create. Call `stateHolder.dispatch(action)` to modify state. Undo/redo history is owned internally by the holder in v1 and exposed via `canUndo`, `canRedo`, `undo()`, and `redo()`. The finalized v1 model is hybrid and linear: `BlockTextEntry` is used only for strict one-block text/span deltas, while `StructuralEntry` is used for semantic or multi-block document changes and replays through full checkpoints. Direct external `dispatch(action)` calls still bypass history unless they are routed through a history-aware integration point. `setState(...)` and `loadFromJson(...)` are hard replacement paths and clear history. When rendered through `CascadeEditor`, the holder is internally bound to the live `BlockTextStates` / `BlockSpanStates` instances so replay can choose the correct scope per entry type: `StructuralEntry` clears runtime holders, replaces snapshot state, rebuilds runtime text/span state for current text blocks, restores block selection when there is no focused block, and then restores focused text selection plus focused pending styles; `BlockTextEntry` patches only the target block snapshot/runtime text/runtime spans plus the same replayable UI state. Structural checkpoints include block selection because structural commands such as subtree drag are selection-defined; they still exclude slash UI and active drag state. Live typing capture currently happens in `TextBlockField` through `TextEditHistoryTracker`, which promotes committed one-block text edits into history after span maintenance and coalesces only caret-preserving insert/backspace/delete-forward batches within `500ms`; focus, selection, paste-like, programmatic, replay, and externally triggered formatting commands reset that local batch state through holder-registered tracker hooks. Structural edit sources now route through holder-owned transaction helpers that always push a forced `StructuralEntry`, break all open typing batches before capture, and re-anchor surviving trackers to the post-transaction checkpoint so later typing compares against the new structural baseline. Custom runtime-holder instances should remain stable for the lifetime of the bound holder; the default remembered instances already satisfy that contract.

**BlockTextStates** — single source of truth for text content. One `TextFieldState` per block. Key methods: `getOrCreate()`, `getVisibleText()`, `getSelection()`, `mergeInto()`, `setText()`, `setSelection()`, `replaceVisibleRange()`, `consumeProgrammaticCommit()`, `extractAllText()`, `cleanup()`. Programmatic text mutations (`mergeInto` / `setText`) register per-block expected committed text so `SpanMaintenanceTextObserver` can skip/rebase non-user commits and avoid duplicate span adjustment. Programmatic selection restore uses visible-text coordinates through `getSelection()` / `setSelection()` so history replay does not need to reason about the internal ZWSP sentinel. Internal observers (like `SlashCommandTextObserver`) can also perform a non-destructive pending-commit peek when needed without consuming the authoritative span observer entry. Provided to renderers via `LocalBlockTextStates` CompositionLocal.

**BlockSpanStates** — single source of truth for rich text spans during editing. One `MutableState<List<TextSpan>>` per block plus snapshot-aware pending-style state. Key methods: `getOrCreate(..., textLength)`, `getSpans()`, `set(..., textLength)`, `adjustForUserEdit()`, `split()`, `mergeInto()`, `applyStyle()`, `removeStyle()`, `removeLinkSpans()`, `toggleStyle()`, `queryStyleStatus()`, `activeStylesAt()`, `resolveStylesForInsertion()`. Invariants are enforced at API ingress (`getOrCreate` / `set`) by normalizing and clamping spans with current visible text length. Created and remembered in `CascadeEditor`, cleaned up in `LaunchedEffect(state.blocks)` with text-only IDs (`collectTextBlockIds`) to prevent stale span state on non-text transitions, and provided to renderers via `LocalBlockSpanStates` CompositionLocal. Per-block span state is initialized in `TextBlockRenderer` from `BlockContent.Text.spans`. Rendering is applied through a stable per-block `BasicTextField` `outputTransformation` that reads latest spans at render time via `SpanMapper.applyStyles(...)` with defensive clamping in visible coordinates and theme-captured `inlineCodeBackground`, `highlight`, and `linkText` colors. Link spans render as `linkText` plus underline; click/open semantics are separate from rendering. User-edit span maintenance runs post-commit via `SpanMaintenanceTextObserver`, which consumes/rebases programmatic commit baselines from `BlockTextStates` before applying diff-based user edit maintenance. Programmatic split/merge runtime transfer is executed in `DefaultBlockCallbacks`, and `mergeInto(...)` clears pending styles on both source and target to avoid pending-style bleed after merge. External formatting operations (toolbar, keyboard shortcuts) should use `SpanActionDispatcher` (provided via `LocalSpanActionDispatcher`) which coordinates runtime `BlockSpanStates` update (immediate visual) with full snapshot sync via `UpdateBlockContent` (avoids stale-text-length mismatch). Link create/edit/remove operations use `LinkActionDispatcher`, exposed to custom chrome through gated `LocalLinkActions`; it validates URLs, mutates runtime text/spans against a captured `LinkTarget`, syncs snapshot through `UpdateBlockContent`, and captures one isolated history entry when an `EditorStateHolder` is supplied. Collapsed-cursor `toggleStyle` toggles pending styles instead of applying zero-width spans, but `resolveStylesForInsertion(...)` filters `SpanStyle.Link` from pending and inherited continuation styles so generic typing cannot create or extend links at boundaries. `ApplySpanStyle`/`RemoveSpanStyle` actions are snapshot-only and should not be dispatched directly during active editing. Snapshot span reducers use the same `SpanAlgorithms` normalization contract as runtime for canonical output. `SplitBlock` accepts `newBlockSpans` parameter for runtime-provided spans and always updates source block snapshot. `MergeBlocks` reducer merges snapshot spans alongside text. `DefaultBlockCallbacks` syncs merged text+spans to snapshot via `UpdateBlockContent` before `DeleteBlock` dispatch on merge paths. `UpdateBlockText` explicitly resets spans (callers needing span preservation use `UpdateBlockContent`).

> **Why not sync text via LaunchedEffect?** Causes cursor jumps, race conditions, and double-init. `BlockTextStates` avoids all of this by owning the `TextFieldState` directly.

## Action System

All state changes go through `EditorAction.reduce(state) → newState`.

**Block Manipulation:** `InsertBlock`, `InsertBlockAfter`, `DeleteBlocks`, `DeleteBlock`, `UpdateBlockContent`, `UpdateBlockText`, `ConvertBlockType`, `MoveBlocks`, `MoveDragPayload`, `IndentForward`, `IndentBackward`, `MergeBlocks`, `SplitBlock`, `ReplaceBlock`, `ToggleTodo`

**Span Styles:** `ApplySpanStyle`, `RemoveSpanStyle`

**Link Mutations:** `LinkActions` / `LinkActionDispatcher` are runtime integration APIs, not snapshot reducer actions. They update `BlockTextStates` and `BlockSpanStates` against a captured `LinkTarget`, then dispatch one `UpdateBlockContent` snapshot sync.

**Selection:** `SelectBlock`, `ToggleBlockSelection`, `SelectBlockRange`, `AddBlockRangeToSelection`, `ClearSelection`, `SelectAll`, `DeleteSelectedOrFocused`

**Focus:** `FocusBlock`, `FocusNextBlock`, `FocusPreviousBlock`, `ClearFocus`

**Drag & Drop:** `StartDrag`, `UpdateDragTarget`, `MoveDragPayload`, `CompleteDrag`, `CancelDrag`

**Slash Commands:** `OpenSlashCommand`, `UpdateSlashCommandSession`, `NavigateSlashSubmenu`, `NavigateSlashBack`, `HighlightSlashCommand`, `CloseSlashCommand`

## Data Flow

**Standard flow:** User Input → `BlockCallbacks` → `EditorAction` → `dispatch()` → `reduce()` → recomposition.

**Text operations (merge/split):** `BlockCallbacks` performs runtime transfer first (`BlockTextStates` + `BlockSpanStates`) and then dispatches block-structure actions. `onEnter` returns without mutation while block selection is active; `BlockType.Code` blocks intercept Enter before the empty-list / split branches and never split — ranged Enter replaces the selection with a single `\n`, empty Code converts to `Paragraph` (preserving `BlockId`), trailing-blank-line Enter (cursor at end + visible text ends with `\n`) drops the trailing newline and dispatches `SplitBlock` with `sourceBlockText = trimmedText` / `newBlockText = ""` to inject a fresh `Paragraph` below, otherwise Enter inserts `\n` at the collapsed cursor — all four branches dispatch through `runStructuralMutation` and form one `StructuralEntry`. For non-Code blocks `onEnter` routes empty `BulletList`, `NumberedList`, and `Todo` blocks at depth greater than `0` to `IndentBackward` before splitting. Empty root `BulletList` and `NumberedList` blocks exit to `Paragraph`; empty root `Todo` blocks keep the normal split-continuation path and create a new unchecked `Todo`. Split paths generate `newBlockId` only when splitting and pass runtime payload (`newBlockSpans`, `sourceBlockText`, `sourceBlockSpans`) into `SplitBlock` for deterministic runtime/snapshot alignment. `onBackspaceAtStart` returns without mutation while block selection is active, then routes any supported block at depth greater than `0` to `IndentBackward` before root-level un-list or merge handling. Merge flows use captured pre-merge target length from `BlockTextStates.mergeInto(...)` to shift source spans exactly once, then sync merged content to snapshot via `UpdateBlockContent` before dispatching `DeleteBlock`. `SplitBlock` and `MergeBlocks` reducers split/merge snapshot spans using `SpanAlgorithms` for snapshot consistency. `SplitBlock` carries supported source indentation to supported continuation blocks; merge keeps the target block's attributes authoritative.

**Live typing history:** `TextBlockField` observes committed `TextFieldState` snapshots directly. For user text edits, the pipeline is: slash observer -> span maintenance -> checkpoint-based history capture/coalescing -> list auto-detect. Programmatic commits bypass that capture path by re-anchoring the local tracker from a full checkpoint. Replay also bypasses capture, but block-local undo/redo now re-anchors the registered tracker directly from the replay payload while structural replay recreates trackers by clearing and rebuilding runtime holders, avoiding replay-time full-document checkpoint churn inside `TextBlockField`. Structural commands also terminate live typing explicitly through the holder transaction wrapper before they mutate document shape or block type, so sequences like `type -> split` stay in separate undo steps. In read-only mode the text field remains mounted and selectable, but editor-owned text input mutation paths do not construct slash observers, mark paste batches, break typing batches for Enter/backspace, execute editor undo/redo shortcuts, apply formatting shortcuts, insert code-block newlines, split/convert blocks from Enter, execute slash commands, or convert list triggers.

**Style formatting:** External code uses `SpanActionDispatcher` (via `LocalSpanActionDispatcher`) which first updates runtime `BlockSpanStates` (immediate visual), then syncs snapshot via `UpdateBlockContent` (full text + spans). `DefaultFormattingActions` is the policy-aware chrome facade over that dispatcher and blocks all formatting calls while `EditorInteractionPolicy.canFormatText` is false. When the dispatcher is constructed with `EditorStateHolder` it also captures isolated history for selected-range formatting via before/after checkpoints and resets the registered text-history tracker for the target block so formatting never merges into surrounding typing. Collapsed-cursor toggle still updates pending styles without a standalone history push, but it refreshes the block-local history baseline so later entries can replay the correct pending-style state.

**Link mutations:** Link state is derived by `LinkStateCalculator` from the focused text block, current visible selection, current spans, and interaction policy, then exposed to custom chrome via `LocalLinkState`. `isInsideLink` is strict collapsed-cursor state (`span.start < cursor < span.end`); ranged selections can still expose `existingUrl` without being inside a link. The default toolbar link button opens an internal `LinkPopupSession` for `LinkPopupSlot.Default` and `Custom`, freezing the target block/range and caret anchor at open time. Collapsed cursors inside an existing link mutate the full existing link range while remaining visually anchored to the original cursor. Link actions use `LinkActionDispatcher` rather than generic formatting actions because apply/edit/remove must validate URLs, optionally replace visible title text, remove all links URL-agnostically, and operate on a captured `LinkTarget`. The dispatcher reads latest runtime text/spans at Apply/Remove time, clamps the captured range, no-ops safely for missing blocks or invalid/stale ranges, updates runtime state first, syncs snapshot with `UpdateBlockContent`, and records one isolated undo/redo step when history runtime is bound. `LocalLinkActions` wraps the dispatcher and blocks mutations while `LinkState.canLink` or `EditorInteractionPolicy.canEditLinks` is false.

**Link opening:** `CascadeEditor(onOpenLink)` controls read/open behavior. `TextBlockField` only attempts link opening from its unfocused tap overlay; focused blocks keep normal editable text hit testing and never open links. Pointer positions are converted from raw `BasicTextField` offsets into visible-text coordinates, then resolved by `LinkHitTester`. Opening is gated off during active drag, block selection, and active text selection. A resolved URL is passed to `LocalLinkOpener` and no editor action is dispatched, so link opening does not create history entries. Long-press is unaffected because only tap handling calls the opener.

**Indentation commands:** External custom toolbar code uses `LocalIndentationActions.current?.indentForward()` / `indentBackward()` and `LocalIndentationState.current?.value` for enablement. The public actions no-op when their corresponding state capability is false. Link custom chrome follows the same local-first shape with `LocalLinkState` and `LocalLinkActions`. The built-in default toolbar renders indent/outdent icon buttons using the same state/actions and localized `CascadeEditorStrings.indentForward` / `indentBackward` accessibility labels. There is no `onIndentationStateChanged` callback in v1.

**Structural transactions:** Built-in semantic document edits now capture explicit structural transactions instead of relying on raw reducer dispatch. The current in-scope boundaries are split/merge in `DefaultBlockCallbacks`, slash command execution in `SlashCommandExecutor`, list auto-detect conversion in `TextBlockField`, and single-action structural dispatches such as `ToggleTodo`, `CompleteDrag`, `DeleteSelectedOrFocused`, `IndentForward`, and `IndentBackward`. Accepted structural entries replay through full-document checkpoints even when the before/after delta would otherwise fit the one-block text predicate; no-op entries are dropped by the history stack.

## Registry System

**BlockRegistry** — maps `typeId` string to `BlockDescriptor` (metadata + factory) and `BlockRenderer` (UI). Use `registry.search(query)` for slash command filtering. Use `registry.getRenderer(blockType)` for rendering (includes unknown-block fallback) or `registry.getRenderer(typeId)` for direct lookup. `setUnknownBlockRenderer()` registers a fallback renderer for `UnknownBlockType` blocks. `createEditorRegistry()` pre-registers all built-in types: `TodoBlockRenderer` for "todo", `TextBlockRenderer` for all other text-supporting types, and `UnknownBlockRenderer` as the unknown-block fallback. All text-editing renderers share the `TextBlockField` composable for text input, spans, and focus.

**BlockRenderer** — `Render(block, isSelected, isFocused, modifier, callbacks)`. Property `handlesSelectionVisual` (default `false`) opts out of the wrapper-level selection overlay; when `true` the renderer is fully responsible for its own selection chrome using `isSelected`.

**BlockCallbacks** — interface passed to renderers for interaction handling. `DefaultBlockCallbacks` wires `onEnter` → selection-mode no-op, Code-block in-place edit (insert/replace `\n`, empty→Paragraph, trailing-blank-line exit via `SplitBlock`), nested empty list/todo outdent, root list exit, or split; `onBackspaceAtStart` → selection-mode no-op, nested supported-block outdent, root un-list, Code→Paragraph in place (preserves multi-line text), or merge; `onDeleteAtEnd` → forward-merge, `onDragStart` → drag initiation, `onSlashCommand` → open menu. Stubs: `onClick`, `onLongClick`. `CascadeEditor` wraps the default callback delegate in internal `PolicyAwareBlockCallbacks` before passing it to built-in renderers and callback consumers. Editable policy forwards all behavior; read-only policy blocks mutating structural, drag, slash, selection, indentation, todo, span, and document actions while allowing focus and cleanup actions (`FocusBlock`, `ClearFocus`, `CloseSlashCommand`, `CancelDrag`, `ClearSelection`). This is a UI facade only: reducers, `EditorStateHolder.dispatch`, and replacement APIs remain mutable for app-owned code.

## Conventions

- **Explicit API mode** — all public declarations need explicit `public`/`internal` visibility
- **`@Immutable` data classes** for state objects
- **`internal`** for implementation details, **`public`** for API surface
- **New actions** must be a data class/object extending `EditorAction` with a `reduce()` override
- **Renderers** access text via `LocalBlockTextStates.current`, never from `BlockContent` directly during editing
- **High-frequency updates** (drag position, scroll) use `mutableFloatStateOf` locally, NOT in `EditorState`
- **Performance**: prefer `graphicsLayer { }` lambdas over Modifier params for draw-phase-only changes (e.g., alpha, translationY)
- **Drag gesture** lives on the Box wrapper, NOT on LazyColumn items (survives recycling)
- **Auto-scroll** uses `dispatchRawDelta` to avoid MutatorMutex contention with gesture scroll
- **Tests** go in `editor/src/commonTest/`. Run: `./gradlew :editor:allTests`

## Implementation Status

| Feature | Status | Notes |
|---------|:------:|-------|
| Core architecture (Block, State, Actions) | Done | |
| Text editing (split, merge, cursor) | Done | |
| Focus management | Done | |
| Selection (single, multi, range) | Done | Actions done with focus/selection mutual exclusivity invariant; UI triggers partial (`onClick` is a stub); wrapper-level selection overlay with `handlesSelectionVisual` opt-out |
| Drag & drop (gesture, preview, indicator, auto-scroll) | Done | Subtree-aware payloads, depth-aware hover, lane-aligned indicator, preview indentation, and payload badge |
| Block registry & search | Done | |
| TextBlockRenderer | Done | All text-supporting types except todo |
| TextBlockField (shared) | Done | Extracted text editing composable used by all text renderers |
| Heading font sizes | Done | No bold weight yet |
| Slash commands (backend) | Done | Session state with query range, submenu nav, highlight; enriched reducer API; `BuiltInSlashCommandSpec` on descriptors with `ConvertInPlace`/`AlwaysInsert` behavior policies; `BuiltInSlashCommandFactory` generates `SlashCommandAction`s from descriptor metadata; `SlashCommandEditorHost` provides safe runtime/snapshot editing; `BlockTextStates.replaceVisibleRange()` + `BlockSpanStates.adjustForRangeReplacement()` primitives; `CascadeEditor` exposes public `slashRegistry` parameter for consumer custom commands |
| Slash commands (integration) | Done | `shouldInvalidateSlashSession()` closes session on drag, selection, or anchor deletion; reactive `LaunchedEffect` + `snapshotFlow` wiring in `CascadeEditor` |
| Slash commands (text observer) | Done | `SlashCommandTextObserver` detects `/`, tracks `queryRange`, dismisses on invalid state; wired in `TextBlockField` via combined text+selection `snapshotFlow`; observer is `null` for `BlockType.Code` (call-site suppression keyed in `remember(...)` so same-id Paragraph ↔ Code conversion drops/recreates it) |
| Slash commands (UI) | Done | Popup overlay with grouped items, caret-relative positioning, keyboard nav (Up/Down/Enter/Escape), auto-highlight, submenu back-nav, `focusProperties { canFocus = false }` pattern |
| Slash commands (disable gate) | Done | `CascadeEditor` derives one `LocalSlashCommandsEnabled` gate from `SlashCommandSlot` plus `EditorInteractionPolicy.canUseSlashCommands`; the gate disables executor/registry wiring, observer construction, popup rendering, and keyboard slash handling. The default toolbar slash button stays visible but is enabled only when the slash gate and `canEditText` are both true |
| Todo checkbox UI | Done | `TodoBlockRenderer` with `Checkbox` + `TextBlockField`, `ToggleTodo` action |
| Bullet/numbered list prefixes | Done | `TextBlockRenderer` wraps list types in `Row` with non-editable prefix gutter (`•` / depth-formatted ordered prefixes) |
| List auto-detection | Done | `ListAutoDetectObserver` detects `- ` and `N. ` triggers, converts block type, removes prefix text; suppressed for `BlockType.Code` via the call-site `isListBlock` predicate (`isCurrentlyList || block.type is BlockType.Code`) keyed in `remember(...)` |
| List enter/backspace behavior | Done | Empty nested list/todo Enter outdents, root empty lists exit to Paragraph, Backspace at nested supported blocks outdents before root un-list/merge behavior |
| Quote visual styling | Done | Left border (3dp) + background tint; `quoteBorder`/`quoteBackground` color slots |
| Divider renderer | Done | `DividerBlockRenderer` — horizontal line, 1dp, vertical padding |
| Code block | Done | `BlockType.Code` (plain monospace, `supportsSpans = false`), `/code` slash command (`ConvertInPlace`), `CodeBlock` composable in `TextBlockRenderer` with `codeBlockBackground` tint, multi-line `\n` Enter with trailing-blank-line exit, Backspace-at-start convert-to-Paragraph, slash/list-auto-detect observer suppression keyed on `block.type` |
| Rich text spans — domain model | Done | `TextSpan`, `SpanStyle`, `BlockContent.Text.spans` |
| Link spans — model, URL policy, persistence, algorithms, rendering, state/actions, popup editing, unfocused opening | Done | `SpanStyle.Link`, `LinkUrlPolicy`, stable `LinkValidationError`, `RichTextSchema` link encode/decode, URL-exact merge identity, URL-agnostic link remove/query, non-overlapping link normalization, theme-colored underline rendering, `LinkState`, `LinkTarget`, `LinkActions`, `LinkActionDispatcher`, `LocalLinkState`, `LocalLinkActions`, default toolbar link entry point, `LinkPopupSlot`, `LinkPopupSession`, default/custom/none popup editing, `LinkHitTester`, `onOpenLink` |
| Rich text spans — algorithms | Done | `SpanAlgorithms`: normalize, adjust, split/merge, apply/remove/toggle, query, link range canonicalization |
| Rich text spans — runtime holder | Done | `BlockSpanStates` + `LocalBlockSpanStates`, strict ingress normalization/clamping |
| Rich text spans — lifecycle wiring | Done | `BlockSpanStates` provided in `CascadeEditor`, per-block init in `TextBlockRenderer`, text-only cleanup guard |
| Rich text spans — rendering | Done | `OutputTransformation` path wired in `TextBlockRenderer` via `SpanMapper`; links render with `CascadeEditorColors.linkText` plus underline |
| Rich text spans — edit maintenance | Done | Implemented via committed visible-text observer (`SpanMaintenanceTextObserver`) + `BlockSpanStates.adjustForUserEdit`/pending continuation style application |
| Rich text spans — programmatic split/merge/setText sync | Done | Programmatic commit signaling in `BlockTextStates`, observer consume/rebase path, deterministic `SplitBlock.newBlockId`, callback-side span transfer for split/merge |
| Rich text spans — public actions & snapshot sync | Done | `ApplySpanStyle`/`RemoveSpanStyle` actions, `SpanActionDispatcher`, `SplitBlock`/`MergeBlocks` reducers preserve spans, `UpdateBlockText` explicit reset policy |
| Text transformation panel | Not done | |
| Block anchor / action menu | Not done | |
| Serialization — rich text spans | Done | `RichTextSchema` encode/decode with version switch and link style persistence |
| Serialization — doc foundation types | Done | Enums, options, warnings, codecs, `UnknownBlockType` |
| Serialization — full document | Done | `DocumentSchema` encode/decode, `EditorStateHolder.toJson()`/`loadFromJson()` extensions |
| HTML import/export | Done | Profile-driven HTML codec under `htmlserialization/`: hand-written common-only parser, generic decode/encode engines, default HTML5-ish profile (canonical block/span/list-outline encoders, default tag decoders, encode fallbacks), `UnknownTagPolicy` (`Strip`/`WarnAndStrip`/`Preserve`/`Custom`), `HtmlProfileSupportSet` round-trip claim with `SupportSetBlockGenerator`, `EditorStateHolder.toHtml()` / `loadFromHtml()` integration, and sample-only `CustomHtmlProfile.Profile` reference dialect. As-shipped behavior documented in `docs/HtmlImportExportFeatureContext.md`; design history in `docs/HtmlImportExportSpec.md` |
| Undo / Redo | Done | Hybrid linear history is finalized: `BlockTextEntry` handles strict one-block text/span edits, `StructuralEntry` handles semantic or multi-block changes, public `canUndo`/`canRedo`/`undo()`/`redo()` API is live, and Cmd/Ctrl+Z plus Shift+Cmd/Ctrl+Z are implemented |
| Theming / styling API — data models | Done | `CascadeEditorTheme`, `CascadeEditorColors`, `CascadeEditorTypography`, `LocalCascadeTheme`; light/dark presets |
| Theming / styling API — color migration | Done | All UI colors read from `LocalCascadeTheme.current.colors` |
| Theming / styling API — typography migration | Done | All UI typography reads from `LocalCascadeTheme.current.typography` |
| Localization — data models | Done | `CascadeEditorStrings`, `CascadeEditorBlockStrings`, `BlockLocalizedStrings`, `LocalCascadeStrings`, `LocalCascadeBlockStrings` |
| Localization — UI string migration | Done | `SlashCommandPopup`, `UnknownBlockRenderer`, `RichTextToolbar` read from `LocalCascadeStrings`; link labels and validation-error labels live in `CascadeEditorStrings` |
| Localization — slash command system | Done | `BuiltInSlashCommandFactory.generate()` accepts `CascadeEditorBlockStrings?` for localized titles/descriptions/keywords |
| Block nesting / indentation | Done | Core `BlockAttributes` metadata, outline-aware numbered renumbering, reducer actions, editing callbacks, serialization, public state/actions, toolbar buttons, rendering, and depth-aware drag UI are implemented |
| Multi-block drag | Done | Selection drags resolve selected roots, include full subtrees once, complete as one atomic reorder/reindent operation, and render a payload-count preview badge |
| Keyboard shortcuts — formatting | Done | Cmd+B/I/U (macOS) / Ctrl+B/I/U (other) via `onPreviewKeyEvent` in `TextBlockField` + `LocalFormattingActions` |
| Keyboard shortcuts — other | Partial | Undo/redo history shortcuts are implemented alongside formatting shortcuts; broader non-formatting shortcut coverage is still open |
| iOS keyboard dismiss | Done | `HideKeyboardToolbarButton` pinned to trailing toolbar edge, iOS-only via `isIos` expect/actual; dispatches `ClearFocus` |

## Known Gaps

| # | Area | Constraint |
|---|------|-----------|
| 1 | **History Capture Boundary** | Direct raw `EditorStateHolder.dispatch(...)` still bypasses history by contract. Built-in structural commands must route through the holder transaction helpers (or another history-aware boundary) if they need undo/redo coverage. |

## Testing

| Test File | Coverage |
|-----------|----------|
| `EditorStateTest.kt` | All action reducers incl. span actions, split/merge span transfer, snapshot stability (~87 tests) |
| `SlashCommandStateTest.kt` | Slash session reducers: open/update/navigate/highlight/close, submenu path, no-op guards |
| `SlashCommandRegistryTest.kt` | Registry: registration order, dedup, ranking tiers, path-based submenu search, menu discoverability, tie-breaking |
| `DragActionsTest.kt` | Drag state transitions, subtree payload resolution, depth-aware target clearing, self-drop rejection, depth rewrite, drag renumbering |
| `DragSelectionTest.kt` | `isDropAtOriginalPosition` boundary cases for long-press-to-select detection |
| `BlockSelectionIntegrationTest.kt` | Block selection workflows: enter/exit selection, multi-select, delete selected, insertion preserves selection, slash invalidation, full lifecycle (11 scenarios) |
| `AutoScrollTest.kt` | Hot zones, speed calculation |
| `DragUtilsTest.kt` | Drop target coordinate math, depth-aware hover clamps, invalid payload gaps, drop indicator geometry, drag preview badge text |
| `BuiltInSlashCommandFactoryTest.kt` | Factory filtering, ID stability, metadata copying, icon resolution, behavior preservation via recording executor, deterministic ordering, registry integration |
| `BlockTextStatesTest.kt` | Range replacement (middle/start/end/full), deletion, missing block, clamping, programmatic commit tracking, cursor positioning, `hasPendingProgrammaticCommit` peek semantics |
| `SlashCommandEditorHostTest.kt` | replaceQueryText (removal, replacement, span preservation, snapshot sync), updateAnchorText, replaceAnchorBlock (id preservation, focus), insertBlockAfterAnchor (ordering, focus), focusBlock, closeMenu, graceful no-ops for missing anchors |
| `SlashCommandTextObserverTest.kt` | Session opening (start/middle/empty, non-slash, deletion, replacement), updating (progressive, spaces), closing (slash deletion, cursor outside range, focus lost), programmatic changes (skip, preserve, remove), paste/multi-char excluded, notifySessionClosed, range shifting (insert/delete before slash), within-range edits, after-range cursor, identical no-op, successive open-after-close (~30 tests) |
| `BlockRegistryTest.kt` | Descriptor search, block creation, slash metadata exposure, behavior policies per built-in type |
| `BlockTest.kt` | Core block creation, `BlockAttributes` defaults/copying/range validation, indentation support matrix, `NumberedList` type validation |
| `ListUtilsTest.kt` | `renumberNumberedLists`: outline-aware depth/parent sequences, same-depth breaks, parent continuation across descendants, referential equality |
| `ListAutoDetectObserverTest.kt` | Bullet trigger (dash+space), numbered trigger (N.+space), no-trigger guards (mid-text, already-list, paste, programmatic, zero, deletion, replacement) |
| `ListIntegrationTest.kt` | Multi-step list scenarios: auto-detect→enter→sequential numbers, delete middle→renumber, empty-enter exit→paragraph+renumber, backspace un-list→run split, move blocks→both runs renumber, mid-text split with spans, full lifecycle |
| `IndentationEditingIntegrationTest.kt` | Depth-aware Enter/Backspace callback behavior: same-depth split continuation, nested empty list/todo outdent, root list exit, root todo continuation, selection-mode callback no-ops, indented paragraph split, backspace outdent without merge, renumbering after outdent |
| `UnknownBlockTypeTest.kt` | UnknownBlockType properties (supportsText, isConvertible, displayName, rawTypeJson), registry getRenderer unchanged |
| `DocumentSchemaEncodeTest.kt` | Document encode: envelope/version, block attributes, all built-in types, content kinds, custom data, codec hooks, UnknownBlockType re-emit |
| `DocumentSchemaDecodeTest.kt` | Document decode: round-trips, version guard, block attributes, heading/todo/numbered defaults, ID modes, malformed blocks, codecs, depth-aware renumbering, warnings |
| `DocumentSerializationExtTest.kt` | Editor integration: toJson runtime/snapshot resolution with attributes, loadFromJson state replacement with attributes, runtime clearing, codec pass-through |
| `HtmlSerializationExtTest.kt` | HTML editor integration: toHtml runtime/snapshot resolution, non-spans runtime span stripping, loadFromHtml runtime clearing/state replacement/warning return, custom profile pass-through |
| `RichTextSchemaTest.kt` | Span serialization round-trips, normalization, version handling |
| `SpanAlgorithmsTest.kt` | Normalize, edit adjust, split/merge, apply/remove/toggle, style queries (~62 tests) |
| `BlockSpanStatesTest.kt` | Lifecycle, edit adjustment, split/merge transfer, style ops, queries, pending styles, aliasing/invariant edge cases (~57 tests) |
| `SpanLifecycleIntegrationTest.kt` | Task 5 wiring behavior: text-id collection, non-text transition cleanup, same-id re-init |
| `SpanMapperTest.kt` | Style mapping (all variants, property isolation), link theme color/underline rendering, link overlap combinations, OutputTransformation null/non-null contract, stability |
| `SpanMaintenanceTextObserverTest.kt` | Programmatic commit exact-skip and rebase behavior (observer-safe split/merge/setText path) |
| `SpanActionDispatcherTest.kt` | Runtime + snapshot coordination via UpdateBlockContent for apply/remove/toggle, no-op guards, multi-dispatch accumulation, collapsed-cursor pending style toggle |
| `VisibleSelectionTest.kt` | Sentinel offset adjustment for visibleSelection(): collapsed, ranged, reversed, edge cases |
| `SentinelGuardClassificationTest.kt` | Pure ZWSP-sentinel-guard classification: NoOp for unchanged/mid-text edits, DeletionAtStart for true Backspace-at-start (incl. selection across sentinel), RestoreSentinel for accidental insertion-before-sentinel — distinguishes real Backspace from typing at raw cursor 0 |
| `EnterContinuationTest.kt` | New-block style continuation on Enter: pending transfer, end-of-block inheritance, mid-block no-transfer, empty block edge cases |
| `FormattingStateCalculatorTest.kt` | Pure calculator: canFormat conditions, collapsed caret pending/continuation, ranged selection query, reversed bounds, metadata |
| `DefaultFormattingActionsTest.kt` | Action adapter: ranged/collapsed toggle, apply/remove pass-through, no-op guards (no focus, block selection, drag, non-text), fresh selection resolution |
| `FormattingIntegrationTest.kt` | Full integration: focus/unfocus cycles, focus switch between styled blocks, pending styles for empty blocks, drag disables formatting, same-style cursor move structural equality, Enter continuation + calculator, toggle + calculator consistency, multi-block selection disable, config extensibility, backspace merge continuity, runtime/snapshot sync, collapsed-cursor pending toggle cycle |
| `LinkStateCalculatorTest.kt` | Pure calculator: focused-block enablement, strict inside-link cursor resolution, boundary exclusion, one-URL selection resolution, mixed/intersecting state |
| `LinkActionDispatcherTest.kt` | Runtime + snapshot coordination for link apply/edit/remove, URL validation no-op, title replacement/insertion, stale target clamping, missing-block no-op |
| `LinkPopupSessionTest.kt` | Pure popup session behavior: initial fields, captured target, validation, apply/remove/dismiss delegation |
| `LinkHitTesterTest.kt` | Pure link opening hit tests: visible offsets, overlap with non-link styles, boundaries, focused/drag/selection gating |
| `LinkOpenerTest.kt` | Link opener policy: consumer exception propagation and default platform failure swallowing |
| `LinkHistoryIntegrationTest.kt` | Link apply, edit, and remove operations replay as one isolated undo/redo step |
| `StructuralHistoryIntegrationTest.kt` | Forced `StructuralEntry` capture at structural boundaries: split/merge, slash convert/insert, list auto-detect conversion, todo toggle, drag reorder, selected delete, typing-batch boundary |
| `HistoryRegressionIntegrationTest.kt` | Cross-cutting hybrid undo/redo regressions: alternating `BlockTextEntry`/`StructuralEntry` flows, exact focused selection and pending-style restoration through `type -> split -> undo split -> undo typing`, and history clearing after document replacement |
| `SlashPopupUtilsTest.kt` | Popup pure functions: estimatePopupHeightDp (compact/clamped), calculatePopupOffset (below/above/clamp), resolveNextHighlight (null/down/up/first/last/clamped/unknown) |
| `CascadeEditorSlashIntegrationTest.kt` | Slash integration: registry coexistence (built-in + custom), custom override, custom execution alongside built-ins, session invalidation pure function (no session, healthy, drag, selection, anchor missing, different block deleted), full scenarios (drag start, anchor deletion) |
| `CascadeEditorColorsTest.kt` | Light/dark presets: non-transparent slots including `linkText`, known values, light vs dark differ on key slots, copy/equality semantics |
| `CascadeEditorTypographyTest.kt` | Default preset: positive font sizes, monotonically decreasing headings, monospace code, medium-weight toolbar, copy/equality |
| `CascadeEditorStringsTest.kt` | Default preset: non-empty strings, unsupportedBlock interpolation, copy with custom values, known English defaults |
| `CascadeEditorBlockStringsTest.kt` | Default preset: all built-in typeIds present, non-empty displayName/description/keywords, forType null for unknown, BlockLocalizedStrings defaults |
| `BuiltInSlashCommandFactoryLocalizationTest.kt` | Localized slash generation: title/description override, keyword merging + dedup, null blockStrings fallback, missing typeId fallback, mixed localized/unlocalized, English keywords always present |
| `HtmlEscapingTest.kt` | `Html.escapeText` / `Html.escapeAttr`: ampersand-first escaping, escapable-set coverage, idempotence on plain ASCII, empty input, Unicode passthrough |
| `HtmlProfileTest.kt` | `HtmlProfile` registration surface: tag-decoder replacement / removal / case-insensitive lookup, profile immutability with built-in default decoders, parser policy overrides, `UnknownTagPolicy` Strip / WarnAndStrip / Preserve / Custom, built-in block + span encoder lookup by class (Heading / Highlight cross-instance sharing), custom block + span encoder lookup by typeId, block group encoder registration / replacement / removal, support-set replacement, `Default` policy defaults |
| `HtmlSupportSetTest.kt` | `HtmlProfile.Default.supportSet` predicates: per-built-in-type support, indentation range coverage, Heading levels 1–6, Todo / `SpanStyle.Custom` rejection, parameterized `Highlight` / `Link` value-based acceptance; `supportsDocument` rejects stale `NumberedList` numbering and outline-invariant violations, accepts empty + normalized documents |
| `HtmlSchemaDecodeTest.kt` | Default-profile block decode mappings, inline tag synonyms, nested mixed list flattening, numbered-list post-decode renumbering, and Todo-like HTML non-mapping |
| `HtmlPerDecoderWhitespaceTest.kt` | Default per-decoder whitespace: paragraph / heading / quote trim+collapse, list-item edge trimming and single trailing newline drop, pre/code whitespace preservation |
| `HtmlBrAndPreCodeTest.kt` | Default `<br>` handling, root `<br>` warning/drop behavior, `<pre><code>` plain code blocks, inline `<code>` spans |
| `HtmlLinkAttributesTest.kt` | Default `<a href>` URL normalization, missing/blank href warning/drop behavior, and `<mark>` highlight color decode |
| `HtmlIndentationEncodingTest.kt` | Decode-side default `cascade-indent-N` class support and post-decode indentation normalization for unsupported block types |
| `HtmlEncodeContextTest.kt` | Generic encode context helpers: inline escaping, registered span tags, newline policy for non-Code vs Code, text-only/attr/free-fragment helpers |
| `HtmlBlockGroupEncoderTest.kt` | Generic block group dispatch: profile-order group matching, contiguous run detection, unrelated-block splitting, null-key fallthrough |
| `EncoderFallbackTest.kt` | Encode fallback behavior: default text-preserving block fallback, `HtmlEmit.Skip`, consumer exception warnings, primary+fallback failure, custom block/span encoder lookup |
| `HtmlNoThrowGuaranteeTest.kt` | Decode no-throw coverage plus encode-side no-throw coverage for empty/large/custom inputs and throwing consumer block/span encoders |
| `sample/.../CustomProfileTest.kt` | Sample-only custom profile: sample decode layout, concrete/placeholder `ql-indent-N`, canonical span/link encode, flat list encode, dropped non-list indentation, flat mixed-outline output, support-set claims |
| `sample/.../CustomRoundTripTest.kt` | Sample-local deterministic fixtures constrained by `CustomHtmlProfile.Profile.supportSet` round-trip through `HtmlSchema.encode` / `decode` |
