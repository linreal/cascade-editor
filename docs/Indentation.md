# Indentation — Technical Context

> HTML import/export round-trips indentation through nested `<ul>` / `<ol>` for list outlines and `class="cascade-indent-N"` for free/skipped depths and indented non-list blocks. See [`HtmlImportExportFeatureContext.md`](HtmlImportExport.md) (`DefaultListOutlineEncoder`, `openTagWithCascadeIndentation`) for the encoding strategy and the `HtmlProfileSupportSet`-scoped round-trip claim.

## 1. Feature Overview

Indentation adds flat-outline nesting to CascadeEditor without replacing the document model with a tree. Supported blocks store a bounded `BlockAttributes.indentationLevel`, reducers shift target roots and their descendants as structural operations, and rendering turns depth into a visual leading inset. Numbered lists, serialization, toolbar state, keyboard editing behavior, and drag-and-drop all read the same block attributes, so nested documents remain consistent across editing, save/load, and reorder flows.

The supported indentation block types are `Paragraph`, `Todo`, `BulletList`, and `NumberedList`. `Heading`, `Quote`, `Divider`, unknown block types, and custom block types that do not opt into indentation remain at depth `0`.

---

## 2. Architecture & Design Decisions

### Introduced Types and Files

| File | Symbol | Role |
|------|--------|------|
| `core/BlockAttributes.kt` | `BlockAttributes` | Public block-level metadata with validated `indentationLevel` and default depth `0`. |
| `core/OutlineUtils.kt` | `shiftIndentation()`, `moveDragPayload()`, `resolveDragPayload()`, `normalizeIndentationOutline()` | Internal flat-outline utilities for indentation target resolution, subtree moves, outline validation, and structural normalization. |
| `indentation/IndentationState.kt` | `IndentationState` | Public immutable command state for current focus or block selection. |
| `indentation/IndentationActions.kt` | `IndentationActions` | Public action interface for custom toolbar/chrome indentation commands. |
| `indentation/DefaultIndentationActions.kt` | `DefaultIndentationActions` | Internal adapter that gates commands by fresh `IndentationState` and dispatches structural actions. |
| `indentation/IndentationStateCalculator.kt` | `IndentationStateCalculator` | Internal pure calculator that mirrors reducer target resolution and validation. |
| `ui/LocalIndentationState.kt` | `LocalIndentationState` | CompositionLocal exposing indentation state inside `CascadeEditor`. |
| `ui/LocalIndentationActions.kt` | `LocalIndentationActions` | CompositionLocal exposing indentation actions inside `CascadeEditor`. |
| `ui/IndentationAnimation.kt` | `IndentationAnimation` | Internal shared 150 ms FastOutSlowIn animation tokens for indentation-related transitions. |
| `ui/renderers/BlockIndentationModifier.kt` | `withBlockIndentation()` | Internal renderer helper that animates supported block leading inset. |
| `ui/renderers/OrderedListPrefixFormatter.kt` | `resolveOrderedListPrefixStyles()`, `formatOrderedListPrefix()` | Internal single-pass prefix-style cache and formatter for ancestry-derived ordered-list prefixes. |

### Key Design Decisions

**Flat outline, not tree storage.** The document remains `List<Block>`. Parent/child relationships are derived from block order and `indentationLevel`: a supported subtree starts at a supported root block and includes following supported blocks with deeper indentation until a same-or-shallower block or unsupported block appears.

**Attributes separate from block type.** `BlockAttributes` belongs to `Block`, not `BlockType`, so layout metadata does not become part of every semantic block type. `Block.attributes` has a trailing default parameter to preserve existing `Block(id, type, content)` call sites.

**Bounded free depth.** `indentationLevel` is validated in the range `0..5`. Supported blocks may use any level in that range, including the first block in a document and the first supported block after an unsupported boundary. Reducers no-op instead of clamping when an indent command or drag move would create an invalid outline.

**Supported-type contract.** `BlockType.supportsIndentation` is true only for `Paragraph`, `Todo`, `BulletList`, and `NumberedList`. Unsupported blocks do not receive hidden indentation through reducers, rendering, or serialization. They are hard outline boundaries: they do not own children, they terminate preceding supported subtrees, and later supported blocks start a new outline segment that can begin at any supported indentation level.

**Ordered-list style follows numbered ancestry.** Numbered-list marker style is derived from the nearest shallower numbered-list ancestor, not from absolute indentation level. The visible style cycles `decimal -> lower alpha -> lower roman -> decimal`, while the model still stores only decimal `BlockType.NumberedList(number)` values. `CascadeEditor` precomputes the marker-style map in a single pass for each block snapshot; list prefix rows use O(1) lookup and do not receive the full block list.

**Reducer and public state share validation.** `IndentationStateCalculator` uses the same target resolution and outline checks as the `IndentForward` / `IndentBackward` reducers, so toolbar enablement matches the actual command result.

**Read-only gates editor-owned indentation surfaces.** In read-only mode, `IndentationState` keeps the resolved target IDs available for inspection but reports `canIndentForward = false` and `canIndentBackward = false`. `DefaultIndentationActions` no-op while the internal editor policy disallows block-structure edits. Reducers remain mutable for app-owned dispatch.

**Drag moves are atomic.** `MoveDragPayload` handles payload removal, insertion, depth rewrite, outline validation, and numbered-list renumbering in one reducer step. Invalid drops return the original block list.

**Shared indentation animation timing.** Normal block indentation, drop indicator Y/depth movement, and drag preview badge indentation use `IndentationAnimation` so related lane changes feel consistent.

---

## 3. Data Flow

### Indent/Outdent Command

```
Custom toolbar or default toolbar
  -> IndentationActions.indentForward() / indentBackward()
  -> DefaultIndentationActions reads current IndentationState
  -> dispatchStructuralAction(IndentForward / IndentBackward)
  -> shiftIndentation():
       1. Selection mode: selected supported roots win over focus
       2. Focus mode: focused supported block is the only root
       3. Selected supported descendants of another selected supported root are skipped
       4. Root subtrees shift by one level when the full resulting outline is valid
  -> renumberNumberedLists()
  -> EditorState.blocks updates and recomposes
```

### Enter and Backspace

```
Enter on non-empty supported block
  -> SplitBlock
  -> continuation block keeps source indentation when target type supports indentation

Enter on empty nested BulletList / NumberedList / Todo
  -> IndentBackward

Enter on empty root BulletList / NumberedList
  -> ConvertBlockType(Paragraph)

Backspace at start of nested supported block
  -> IndentBackward before root-level un-list or merge

Backspace at start of root BulletList / NumberedList / Todo
  -> ConvertBlockType(Paragraph)
```

### Rendering

```
Block renderer receives Block(attributes.indentationLevel = N)
  -> withBlockIndentation(block)
       -> unsupported block types use 0
       -> supported block inset = N * CascadeEditorTheme.dimensions.indentUnit
       -> inset animates horizontally
  -> NumberedList prefix is formatted by numbered-list ancestry:
       no numbered-list ancestor: 1.
       parent style decimal: a.
       parent style lower alpha: i.
       parent style lower roman: 1.
```

### Drag-and-Drop

```
StartDrag(blockId)
  -> resolveDragPayload():
       -> single block drag includes touched block subtree
       -> selected drag includes selected roots plus each root subtree once
  -> DragState stores payload IDs, payload index metadata, root IDs, original root depths, and future root depth

Pointer hover
  -> Y position resolves visual gap
  -> cached DragHoverOutlineIndex resolves block IDs and non-payload neighbors
  -> X movement resolves future root depth in whole indent-unit steps
  -> unsupported primary roots keep future root depth 0
  -> invalid payload/self/adoption gaps clear targetIndex

CompleteDrag
  -> MoveDragPayload removes payload, inserts at gap, applies root-depth delta,
     validates outline, and renumbers ordered lists
```

---

## 4. Public API Surface

### Block Model

```kotlin
public data class Block(
    val id: BlockId,
    val type: BlockType,
    val content: BlockContent,
    val attributes: BlockAttributes = BlockAttributes.Default,
)

public data class BlockAttributes(
    val indentationLevel: Int = 0,
)
```

`Block.withAttributes(newAttributes)` creates a copy with new metadata.

### Supported Type Contract

```kotlin
public val BlockType.supportsIndentation: Boolean
```

The property defaults to `false`; built-in `Paragraph`, `Todo`, `BulletList`, and `NumberedList` override it to `true`.

### Public Reducer Actions

```kotlin
public data object IndentForward : EditorAction
public data object IndentBackward : EditorAction
```

`MoveBlocks` remains available as a flat reorder primitive and does not perform drag-style depth rewriting. Like other structural reducers, it still normalizes any invalid outline that would otherwise orphan descendants. Drag completion uses the internal `MoveDragPayload` reducer path to move and reindent the semantic payload atomically. `convertVisualGapToMoveBlocksIndex(...)` remains public as a deprecated compatibility shim for legacy flat drag integrations.

### Custom Toolbar API

```kotlin
public data class IndentationState(
    val canIndentForward: Boolean,
    val canIndentBackward: Boolean,
    val targetBlockIds: List<BlockId>,
)

public interface IndentationActions {
    public fun indentForward()
    public fun indentBackward()
}
```

Inside `CascadeEditor`, custom chrome can read:

```kotlin
val indentationState = LocalIndentationState.current?.value
val indentationActions = LocalIndentationActions.current
```

---

## 5. Integration Points

| System | Integration |
|--------|-------------|
| Core model | `Block.attributes` stores persistent indentation metadata; `BlockType.supportsIndentation` declares supported semantics. |
| Reducers | `IndentForward`, `IndentBackward`, conversion, split, merge, replace, insert, delete, move, and drag completion preserve or clear attributes according to type support. |
| Numbered lists | `renumberNumberedLists()` scopes sequences by depth and derived parent within a supported outline segment, then stores decimal numbers in `BlockType.NumberedList(number)`. Unsupported blocks reset numbering scope and outline ancestry. |
| Rendering | `withBlockIndentation()` applies animated leading inset through `IndentationAnimation`; ordered-list prefix styles are precomputed from numbered-list ancestry and formatted with an O(1) per-block lookup at render time. |
| Serialization | `DocumentSchema.CURRENT_VERSION = 2`; supported non-zero indentation is encoded under `attributes.indentationLevel`; out-of-range or unsupported-block indentation values warn and fall back to normalized depths. |
| Toolbar | Default toolbar shows indent/outdent buttons when `RichTextToolbarConfig.showIndentation` is true; custom toolbars use indentation locals. |
| History | Built-in indentation commands and drag completion are structural transactions when routed through the history-aware holder boundary. |
| Drag | Drag state stores roots, full payload IDs, payload index metadata, original depths, and future root depth; hover resolution uses a cached block index, prevents invalid drops before completion, and pins unsupported primary roots to depth `0`. |

---

## 7. Edge Cases & Known Constraints

- Supported blocks can use any indentation level in `0..5`, including skipped levels such as `0, 1, 2, 2, 4`.
- The first block in a document and the first supported block after an unsupported boundary may be indented.
- Unsupported block types must remain at depth `0`; conversion into unsupported types clears indentation and normalizes former descendants into a new supported outline segment.
- `IndentForward` is all-or-no-op. It does not clamp to an alternate legal depth when the exact one-level shift would be invalid.
- `IndentBackward` leaves root targets already at depth `0` unchanged; other selected roots in the same command can still move.
- Selection takes precedence over focus for indentation commands. Unsupported selected blocks are ignored for indentation targeting.
- Drag selection is different from indentation selection: unsupported selected blocks can still move as drag roots, but an unsupported primary root cannot be horizontally indented and keeps future depth `0`.
- Invalid drag hover targets hide the drop indicator and prevent `CompleteDrag` from mutating blocks.
- Read-only mode disables built-in indent/outdent UI, indentation actions exposed through editor locals, structural Enter/Backspace indentation behavior, and drag reindentation. External reducer dispatch such as `stateHolder.dispatch(IndentForward)` is still app-owned.
- Tab and Shift+Tab keyboard shortcuts are intentionally not part of V1. Indentation is available through reducer actions, public `IndentationActions`, Enter/Backspace list behavior, toolbar buttons, and drag depth changes.
- The document model is still flat. Consumers that need a tree must derive it from order and `indentationLevel`.

---

## 8. Glossary

| Term | Definition |
|------|------------|
| **Indentation level** | Persistent integer depth stored in `BlockAttributes.indentationLevel`. Supported range is `0..5`. |
| **Supported block** | A block whose `BlockType.supportsIndentation` is `true`: paragraph, todo, bullet list, or numbered list. |
| **Flat outline** | A hierarchy represented by block order plus depth metadata, not by nested child arrays. |
| **Subtree** | A supported root block plus following supported blocks whose indentation depth is greater than the root depth, stopping at a same-or-shallower supported block or any unsupported block. |
| **Derived parent** | The nearest preceding shallower supported block in the same outline segment used to scope nested numbered-list sequences. |
| **Numbered-list ancestry** | The nearest preceding shallower numbered-list ancestors in the same outline segment, used to choose ordered-list marker style. |
| **Unsupported boundary** | A block that does not support indentation. It stays at depth `0`, cannot own descendants, and resets outline validation, subtree discovery, drag payloads, and numbering ancestry. |
| **Target root** | A focused or selected supported block that receives an indent/outdent command. Descendants move with it. |
| **Future root depth** | The resolved indentation level a dragged primary root would have if dropped at the current hover target. |
| **Indent unit** | `CascadeEditorDimensions.indentUnit`, the visual spacing multiplied by `indentationLevel`. |
