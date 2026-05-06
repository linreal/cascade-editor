# Feature Context: Numbered and Bullet Lists

> HTML import/export emits a single `listOutline` `BlockGroupEncoder` that produces genuinely nested `<ul>` / `<ol>` for mixed bullet/numbered runs. Decoded numbered values are *not* trusted from input — `HtmlDecodeEngine` runs `renumberNumberedLists(...)` after every decode, and `HtmlProfileSupportSet.supportsDocument(...)` rejects documents whose numbering would change under that pass. See [`HtmlImportExportFeatureContext.md`](HtmlImportExport.md) for the outline-encoder contract and the flat-vs-nested tradeoff that dialect profiles such as `CustomHtmlProfile` make.

## 1. Feature Overview

CascadeEditor's block model already defined `BulletList` and `NumberedList` as block types, but they rendered as plain paragraphs with no visual distinction or list-specific editing behavior.

This feature adds:
- **Visual prefixes** — a non-editable `•` or ancestry-formatted ordered-list gutter to the left of the text field
- **Auto-detection** — typing `- ` or `1. ` in a paragraph automatically converts it to the corresponding list type
- **Enter continuation** — pressing Enter in a list item creates a new item of the same type; pressing Enter on an empty item exits the list
- **Backspace un-listing** — backspace at position 0 of a list item demotes it to a paragraph
- **Automatic renumbering** — sequential numbers in numbered lists are corrected after structural mutations and document decode, using indentation depth and derived parent scope
- **Nested ordered-list formats** — stored numbers remain decimal, but rendering cycles prefixes by numbered-list ancestry (`1.`, `a.`, `i.`, then `1.` again)

The implementation touches the core model, action reducers, rendering layer, and text observers. List numbering now integrates with block indentation through `BlockAttributes.indentationLevel`, but list content remains a flat `List<Block>` rather than a tree.

---

## 2. Architecture & Design Decisions

### New Files

| File | Purpose |
|------|---------|
| `core/ListUtils.kt` | `renumberNumberedLists()` — pure function that corrects sequential numbers across outline-aware sequences |
| `ui/observers/ListAutoDetectObserver.kt` | Text observer that detects `- ` and `N. ` trigger patterns and fires conversion callbacks |
| `ui/renderers/OrderedListPrefixFormatter.kt` | Presentation-only ordered prefix formatter for numbered-ancestry-specific decimal, alphabetic, and roman forms |

### Modified Files

| File | Change Summary |
|------|---------------|
| `core/BlockType.kt` | `NumberedList` changed from `data object` to `data class` with `number: Int` (validated ≥ 1) |
| `core/Block.kt` | `Block.numberedList(text, number)` factory method and `Block.attributes` participate in nested list depth |
| `action/EditorAction.kt` | `SplitBlock` propagates list type to new block; structural reducers and drag/indent actions call `renumberNumberedLists()` post-processing |
| `registry/BlockRenderer.kt` | `DefaultBlockCallbacks.onEnter` — empty-list-item exit; `onBackspaceAtStart` — un-list conversion |
| `registry/BlockRegistry.kt` | Factory updated: `NumberedList` → `NumberedList(number = 1)` |
| `ui/renderers/TextBlockRenderer.kt` | Added list prefix row and numbered-ancestry-aware ordered prefixes; branching render path for list vs. non-list types |
| `ui/renderers/TextBlockField.kt` | Wired `ListAutoDetectObserver` into the combined text observation `LaunchedEffect` |

### Key Design Choices

**`NumberedList` as `data class` with `number` field.** The display number is stored per-block rather than computed at render time. This makes the number available to reducers, tests, and serialization without needing positional context. The trade-off is that numbers must be explicitly renumbered after mutations — handled by the centralized `renumberNumberedLists()` utility.

**Centralized renumbering as post-processing.** Instead of each reducer computing the correct number inline, structural-mutation reducers call `renumberNumberedLists()` on the resulting block list. This includes insert/delete/replace/convert/split/merge/reorder paths, indentation actions, subtree drag completion, and document decode.

**Outline-aware sequence scope.** A numbered-list sequence is scoped by the block's indentation depth and its derived parent in the flat outline. Deeper descendants do not break an ancestor sequence. A same-depth non-numbered block resets only the sequence at that depth and derived parent.

**Text observer pattern for auto-detection.** `ListAutoDetectObserver` follows the same observer architecture as `SlashCommandTextObserver` and `SpanMaintenanceTextObserver` — a stateful class that receives committed text diffs and reacts. This avoids coupling detection logic to the composable or the action system.

**Non-editable prefix via `Row` layout.** The prefix (`•`, `1.`, `a.`, `i.`) is a separate `Text` composable in a `Row`, not part of the editable `TextFieldState`. This avoids cursor/span offset complications and keeps the content model clean.

**Ordered style follows numbered ancestry.** Absolute indentation no longer controls ordered-list marker style. A numbered list without a shallower numbered-list ancestor uses decimal. A numbered child of decimal uses lower alpha, a child of lower alpha uses lower roman, and a child of lower roman cycles back to decimal.

---

## 3. Data Flow

### Auto-Detection Flow

```
User types "- " in Paragraph block
  → TextFieldState commits new text
  → LaunchedEffect observes visible text change
  → ListAutoDetectObserver.onTextChanged() detects bullet trigger
  → onListDetected callback fires:
      1. BlockSpanStates.adjustForRangeReplacement(0..2 → 0 length)
      2. BlockTextStates.replaceVisibleRange(remove "- ")
      3. dispatch(UpdateBlockContent) — sync snapshot
      4. dispatch(ConvertBlockType → BulletList)
  → ConvertBlockType.reduce() converts block, preserves supported indentation, and renumbers
  → Recomposition: TextBlockRenderer renders ListPrefixRow with "•"
```

### Enter Continuation Flow

```
User presses Enter on non-empty numbered list item (number = 3)
  → DefaultBlockCallbacks.onEnter()
  → Checks: is list block + text non-empty? → proceed with split
  → SplitBlock.reduce():
      1. Splits text/spans at cursor position
      2. New block type = NumberedList(number = 4)
      3. New block keeps the source indentation level
      4. renumberNumberedLists() corrects the outline sequence
  → Recomposition: new list item appears with the correct ancestry-formatted prefix
```

### Enter on Empty Root List Item (Exit) Flow

```
User presses Enter on an empty root BulletList/NumberedList item
  → DefaultBlockCallbacks.onEnter()
  → Detects: block is list type + visible text is empty + indentation depth is 0
  → dispatch(ConvertBlockType → Paragraph)
  → ConvertBlockType.reduce() converts + renumbers remaining run
  → Recomposition: block renders as paragraph, no prefix
```

### Enter or Backspace on Nested List Item Flow

```
User presses Enter on an empty nested list/todo item
  OR presses Backspace at position 0 in any nested supported block
  → DefaultBlockCallbacks checks indentation depth > 0 before root un-list/merge handling
  → dispatch(IndentBackward)
  → IndentBackward.reduce() shifts the block subtree out by one level and renumbers
  → Recomposition: block remains the same type, but appears one depth shallower
```

### Root Backspace Un-List Flow

```
User presses Backspace at position 0 of a root list/todo item
  → DefaultBlockCallbacks.onBackspaceAtStart()
  → Detects: depth is 0 and block is BulletList, NumberedList, or Todo
  → dispatch(ConvertBlockType → Paragraph)
  → Short-circuits: does NOT merge with previous block
  → Recomposition: block renders as paragraph, text preserved
```

### Renumbering Flow (Structural Mutations)

```
Any reducer that modifies block list structure
  → Produces tentative new block list
  → Calls renumberNumberedLists(newBlocks)
      1. Quick scan: are any numbers wrong? If not, returns same list (no allocation)
      2. If fix needed: scans top-to-bottom, tracking ancestor IDs and per-depth numbering runs
      3. Numbered blocks increment the sequence for their own (depth, derived parent) scope
      4. Same-depth non-numbered blocks reset that scope; deeper descendants do not reset ancestors
      5. Blocks with correct numbers retain referential identity
  → Returns corrected list → state.copy(blocks = corrected)
```

---

## 4. Public API Surface

### Modified Types

**`BlockType.NumberedList`** — changed from `data object` to:
```kotlin
public data class NumberedList(val number: Int = 1) : BlockType
```
- `number` must be ≥ 1 (enforced by `init` block with `require`)
- `typeId`, `displayName`, `supportsText` unchanged

**`Block.numberedList()`** — new factory method:
```kotlin
public fun numberedList(text: String = "", number: Int = 1): Block
```

### Related Public API

- `BlockType.BulletList` — remains `data object`, no changes
- `Block.bulletList()` — already existed, no changes
- `BlockAttributes.indentationLevel` controls list nesting depth for supported block types.
- `EditorAction.IndentForward` / `IndentBackward` change indentation and then trigger list renumbering.
- `BlockRenderer`, `BlockCallbacks` — interfaces unchanged

All implementation helpers (`ListAutoDetectObserver`, `renumberNumberedLists`, `formatOrderedListPrefix`) are `internal`.

---

## 5. Integration Points

### Existing Systems Affected

| System | Integration |
|--------|-------------|
| **Action reducers** | Structural reducers call `renumberNumberedLists()` after block insert/delete/replace/convert/split/merge/reorder, indentation, and subtree drag completion |
| **`SplitBlock` reducer** | New block inherits list type: `BulletList` → `BulletList`, `NumberedList(n)` → `NumberedList(n+1)`, else → `Paragraph` |
| **`DefaultBlockCallbacks`** | `onEnter` gains empty-list-item exit check (before split path); `onBackspaceAtStart` gains un-list check (before merge path) |
| **`TextBlockRenderer`** | Now branches: list types get a prefix row, ordered prefixes are formatted by depth, others get direct `TextBlockField` |
| **`BlockIndentationModifier`** | Applies the visual leading inset for supported nested list blocks before list prefix rendering |
| **`TextBlockField`** | Wires `ListAutoDetectObserver` alongside existing `SlashCommandTextObserver` and `SpanMaintenanceTextObserver` |
| **`BlockRegistry`** | Factory for `numbered_list` updated to use `NumberedList(number = 1)` instead of `NumberedList` object |
| **Rich text spans** | Auto-detection adjusts spans via `BlockSpanStates.adjustForRangeReplacement()` before removing trigger prefix |
| **Slash commands** | No changes needed — existing `ConvertInPlace` behavior works with the updated `NumberedList` type |

### Dependencies

- `ListAutoDetectObserver` depends on `BlockTextStates`, `BlockSpanStates`, and `BlockCallbacks.dispatch` (all pre-existing)
- `renumberNumberedLists` depends only on `Block`, `BlockType`, and `BlockAttributes` (core layer, no UI dependencies)
- `ListPrefixRow` depends on `TextBlockField` and Material3 `Text` / `LocalContentColor`

---

## 7. Edge Cases & Known Constraints

### Auto-Detection Guards

- **Paste excluded**: Multi-character insertions (paste) do not trigger auto-detection. The observer checks that exactly 1 character was inserted and 0 were deleted.
- **Programmatic changes excluded**: The `isProgrammatic` flag (from `BlockTextStates` pending-commit tracking) prevents detection during split/merge/setText operations.
- **Already-list blocks excluded**: Typing `- ` inside an existing `BulletList` block does nothing special — the observer short-circuits via `isListBlock()`.
- **Zero-number guard**: `NumberedList` validates `number >= 1` in its `init` block. Typing `0. ` does not trigger conversion because the observer checks `number >= 1`.
- **Mid-text triggers excluded**: The trigger only fires when the pattern starts at position 0 of the block text. Typing `hello - ` in the middle of text is ignored.

### Renumbering

- **Sequences start at 1.** Outline-aware renumbering assigns `1` to the first `NumberedList` in each depth/parent scope. Custom start numbers are not preserved in the current implementation.
- **Same-depth non-numbered blocks break only their own scope.** A same-depth `Paragraph`, `Todo`, `BulletList`, `Heading`, `Quote`, `Divider`, or unknown/custom block resets the numbered sequence for that depth and derived parent. Deeper blocks do not break ancestor numbering.
- **Referential equality optimization**: `renumberNumberedLists()` performs a quick scan first. If no numbers need fixing, the original list is returned without allocation. Blocks whose numbers are already correct retain referential identity (important for Compose stability).

### Backspace Behavior

- **Root un-list overrides merge**: Backspace at position `0` of a root list/todo item converts to `Paragraph` and **returns immediately** — it does not fall through to the standard merge-with-previous-block path. Nested supported blocks outdent before this root-level un-list behavior. Text and spans are preserved.
- **Paragraph after list merges normally**: Backspace at position 0 of a Paragraph following a list item still triggers the standard merge behavior (text appended to previous block, which retains its list type).

### Enter Behavior

- **Nested empty-list Enter outdents first.** Pressing Enter on an empty nested `BulletList`, `NumberedList`, or `Todo` dispatches `IndentBackward` instead of splitting.
- **Root empty-list exit overrides split.** Pressing Enter on an empty root `BulletList` or `NumberedList` converts the block to `Paragraph`, never creates a new empty item. Empty root `Todo` keeps todo continuation behavior.
- **Span continuation**: When splitting a non-empty list item mid-text, span styles transfer to the new block via the existing `SplitBlock` span-split logic. The `EnterContinuationTest` suite covers pending style transfer across list-type splits.

### Rendering

- **`remember` key change**: `TextBlockRenderer` changed its `remember` key for `targetStyle` from `block.type` (full object) to `block.type.typeId` (string). This prevents unnecessary style recalculation when `NumberedList.number` changes, since `number` doesn't affect text styling.
- **Baseline alignment**: The prefix `Text` and `TextBlockField` use `alignByBaseline()` inside a `Row` to vertically align the prefix with the first line of text.
- **Depth-based prefix styles are render-only.** `NumberedList.number` always stores the decimal integer. `formatOrderedListPrefix()` turns that number into lower-alpha at depth `1`, lower-roman at depth `2`, upper-alpha at depth `3`, and decimal at depth `0`.

### Threading

- `ListAutoDetectObserver` is not thread-safe — it is created and called within a single `LaunchedEffect` coroutine on the main thread, matching the pattern of other text observers.
- `renumberNumberedLists()` is a pure function with no shared mutable state.

---

## 8. Glossary

| Term | Definition |
|------|------------|
| **Outline sequence** | A sequence of `NumberedList` blocks with the same indentation depth and derived parent, uninterrupted by same-depth non-numbered blocks. |
| **Derived parent** | The nearest preceding shallower outline block used to scope nested numbering in the flat block list. Depth `0` uses a shared root parent. |
| **Trigger pattern** | A text pattern at position 0 of a non-list block that, when completed with a Space keypress, converts the block to a list type. `- ` for bullet, `N. ` for numbered. |
| **Un-list** | Converting a root list/todo block back to a Paragraph. Triggered by Backspace at position `0` for root list/todo blocks, or Enter on an empty root bullet/numbered list. Text and spans are preserved. |
| **Prefix gutter** | The non-editable area to the left of the text field in a list item, displaying `•` or an ancestry-formatted ordered prefix. Implemented as a separate `Text` composable, not part of `TextFieldState`. |
| **Quick scan** | The optimization pass in `renumberNumberedLists()` that checks whether any numbers need fixing before allocating a new list. Returns the original list by reference when no changes are needed. |
| **`ListAutoDetectObserver`** | Internal text observer that monitors committed visible-text changes and detects list trigger patterns. Follows the same stateful-observer pattern as `SlashCommandTextObserver`. |
| **`renumberNumberedLists()`** | Internal pure function that scans a block list for outline-aware `NumberedList` sequences and corrects stored decimal numbers. Called after structural reducers and decode. |
