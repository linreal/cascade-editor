# Feature Context: Numbered and Bullet Lists

## 1. Feature Overview

CascadeEditor's block model already defined `BulletList` and `NumberedList` as block types, but they rendered as plain paragraphs with no visual distinction or list-specific editing behavior.

This feature adds:
- **Visual prefixes** — a non-editable `•` or `N.` gutter to the left of the text field
- **Auto-detection** — typing `- ` or `1. ` in a paragraph automatically converts it to the corresponding list type
- **Enter continuation** — pressing Enter in a list item creates a new item of the same type; pressing Enter on an empty item exits the list
- **Backspace un-listing** — backspace at position 0 of a list item demotes it to a paragraph
- **Automatic renumbering** — sequential numbers in numbered lists are corrected after every structural mutation (insert, delete, move, merge, convert)

The implementation touches the core model, action reducers, rendering layer, and a new text observer — but no new public API types were introduced beyond the `number` parameter on `NumberedList`.

---

## 2. Architecture & Design Decisions

### New Files

| File | Purpose |
|------|---------|
| `core/ListUtils.kt` | `renumberNumberedLists()` — pure function that corrects sequential numbers across all consecutive runs |
| `ui/observers/ListAutoDetectObserver.kt` | Text observer that detects `- ` and `N. ` trigger patterns and fires conversion callbacks |

### Modified Files

| File | Change Summary |
|------|---------------|
| `core/BlockType.kt` | `NumberedList` changed from `data object` to `data class` with `number: Int` (validated ≥ 1) |
| `core/Block.kt` | New `Block.numberedList(text, number)` factory method |
| `action/EditorAction.kt` | `SplitBlock` propagates list type to new block; 8 reducers wired with `renumberNumberedLists()` post-processing |
| `registry/BlockRenderer.kt` | `DefaultBlockCallbacks.onEnter` — empty-list-item exit; `onBackspaceAtStart` — un-list conversion |
| `registry/BlockRegistry.kt` | Factory updated: `NumberedList` → `NumberedList(number = 1)` |
| `ui/renderers/TextBlockRenderer.kt` | Added `ListPrefixRow` composable; branching render path for list vs. non-list types |
| `ui/renderers/TextBlockField.kt` | Wired `ListAutoDetectObserver` into the combined text observation `LaunchedEffect` |

### Key Design Choices

**`NumberedList` as `data class` with `number` field.** The display number is stored per-block rather than computed at render time. This makes the number available to reducers, tests, and serialization without needing positional context. The trade-off is that numbers must be explicitly renumbered after mutations — handled by the centralized `renumberNumberedLists()` utility.

**Centralized renumbering as post-processing.** Instead of each reducer computing the correct number inline, all structural-mutation reducers (`InsertBlock`, `InsertBlockAfter`, `DeleteBlocks`, `ConvertBlockType`, `MoveBlocks`, `MergeBlocks`, `ReplaceBlock`, `SplitBlock`) call `renumberNumberedLists()` on the resulting block list. This keeps renumbering logic in one place and makes it easy to audit which actions trigger it.

**Text observer pattern for auto-detection.** `ListAutoDetectObserver` follows the same observer architecture as `SlashCommandTextObserver` and `SpanMaintenanceTextObserver` — a stateful class that receives committed text diffs and reacts. This avoids coupling detection logic to the composable or the action system.

**Non-editable prefix via `Row` layout.** The prefix (`•` / `N.`) is a separate `Text` composable in a `Row`, not part of the editable `TextFieldState`. This avoids cursor/span offset complications and keeps the content model clean.

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
  → ConvertBlockType.reduce() converts block + renumbers
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
      3. renumberNumberedLists() corrects downstream items
  → Recomposition: new list item appears with correct number
```

### Enter on Empty List Item (Exit) Flow

```
User presses Enter on empty list item
  → DefaultBlockCallbacks.onEnter()
  → Detects: block is list type + visible text is empty
  → dispatch(ConvertBlockType → Paragraph)
  → ConvertBlockType.reduce() converts + renumbers remaining run
  → Recomposition: block renders as paragraph, no prefix
```

### Backspace Un-List Flow

```
User presses Backspace at position 0 of list item
  → DefaultBlockCallbacks.onBackspaceAtStart()
  → Detects: block is BulletList or NumberedList
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
      2. If fix needed: iterates blocks, tracks consecutive NumberedList runs
      3. First block in run defines base number; subsequent = base + offset
      4. Blocks with correct numbers retain referential identity
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

### Unchanged Public API

- `BlockType.BulletList` — remains `data object`, no changes
- `Block.bulletList()` — already existed, no changes
- `EditorAction` sealed hierarchy — no new public action types
- `BlockRenderer`, `BlockCallbacks` — interfaces unchanged

All new implementation classes (`ListAutoDetectObserver`, `renumberNumberedLists`) are `internal`.

---

## 5. Integration Points

### Existing Systems Affected

| System | Integration |
|--------|-------------|
| **Action reducers** | 8 reducers gain `renumberNumberedLists()` post-processing: `InsertBlock`, `InsertBlockAfter`, `DeleteBlocks`, `ConvertBlockType`, `MoveBlocks`, `MergeBlocks`, `ReplaceBlock`, `SplitBlock` |
| **`SplitBlock` reducer** | New block inherits list type: `BulletList` → `BulletList`, `NumberedList(n)` → `NumberedList(n+1)`, else → `Paragraph` |
| **`DefaultBlockCallbacks`** | `onEnter` gains empty-list-item exit check (before split path); `onBackspaceAtStart` gains un-list check (before merge path) |
| **`TextBlockRenderer`** | Now branches: list types get `ListPrefixRow`, others get direct `TextBlockField` |
| **`TextBlockField`** | Wires `ListAutoDetectObserver` alongside existing `SlashCommandTextObserver` and `SpanMaintenanceTextObserver` |
| **`BlockRegistry`** | Factory for `numbered_list` updated to use `NumberedList(number = 1)` instead of `NumberedList` object |
| **Rich text spans** | Auto-detection adjusts spans via `BlockSpanStates.adjustForRangeReplacement()` before removing trigger prefix |
| **Slash commands** | No changes needed — existing `ConvertInPlace` behavior works with the updated `NumberedList` type |

### Dependencies

- `ListAutoDetectObserver` depends on `BlockTextStates`, `BlockSpanStates`, and `BlockCallbacks.dispatch` (all pre-existing)
- `renumberNumberedLists` depends only on `Block` and `BlockType` (core layer, no UI dependencies)
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

- **First-in-run base preservation**: Renumbering preserves the first block's number as-is. If a user manually starts a run at `3.`, subsequent items become `4.`, `5.`, etc. This is intentional for future support of custom start numbers.
- **Non-NumberedList breaks runs**: A `BulletList` block between two `NumberedList` blocks creates two independent runs, each renumbered independently.
- **Referential equality optimization**: `renumberNumberedLists()` performs a quick scan first. If no numbers need fixing, the original list is returned without allocation. Blocks whose numbers are already correct retain referential identity (important for Compose stability).

### Backspace Behavior

- **Un-list overrides merge**: Backspace at position 0 of a list item converts to Paragraph and **returns immediately** — it does not fall through to the standard merge-with-previous-block path. Text and spans are preserved.
- **Paragraph after list merges normally**: Backspace at position 0 of a Paragraph following a list item still triggers the standard merge behavior (text appended to previous block, which retains its list type).

### Enter Behavior

- **Empty-list exit overrides split**: The empty-text check runs before the split path in `onEnter`. This means pressing Enter on an empty list item always exits to Paragraph, never creates a new empty item.
- **Span continuation**: When splitting a non-empty list item mid-text, span styles transfer to the new block via the existing `SplitBlock` span-split logic. The `EnterContinuationTest` suite covers pending style transfer across list-type splits.

### Rendering

- **`remember` key change**: `TextBlockRenderer` changed its `remember` key for `targetStyle` from `block.type` (full object) to `block.type.typeId` (string). This prevents unnecessary style recalculation when `NumberedList.number` changes, since `number` doesn't affect text styling.
- **Baseline alignment**: The prefix `Text` and `TextBlockField` use `alignByBaseline()` inside a `Row` to vertically align the prefix with the first line of text.
- **Min gutter width**: `ListPrefixMinWidth = 24.dp` accommodates at least 2-digit numbers without layout shift. The prefix text uses `TextAlign.End` within this gutter.

### Threading

- `ListAutoDetectObserver` is not thread-safe — it is created and called within a single `LaunchedEffect` coroutine on the main thread, matching the pattern of other text observers.
- `renumberNumberedLists()` is a pure function with no shared mutable state.

---

## 8. Glossary

| Term | Definition |
|------|------------|
| **Consecutive run** | A maximal sequence of adjacent blocks in the document where every block has type `NumberedList`. Non-`NumberedList` blocks (including `BulletList`) break the run. Each run is renumbered independently. |
| **Run base** | The `number` value of the first `NumberedList` block in a consecutive run. Subsequent blocks get `base + 1`, `base + 2`, etc. |
| **Trigger pattern** | A text pattern at position 0 of a non-list block that, when completed with a Space keypress, converts the block to a list type. `- ` for bullet, `N. ` for numbered. |
| **Un-list** | Converting a list block back to a Paragraph. Triggered by backspace at position 0 or Enter on an empty list item. Text and spans are preserved. |
| **Prefix gutter** | The non-editable area to the left of the text field in a list item, displaying `•` or `N.`. Implemented as a separate `Text` composable, not part of `TextFieldState`. |
| **Quick scan** | The optimization pass in `renumberNumberedLists()` that checks whether any numbers need fixing before allocating a new list. Returns the original list by reference when no changes are needed. |
| **`ListAutoDetectObserver`** | Internal text observer that monitors committed visible-text changes and detects list trigger patterns. Follows the same stateful-observer pattern as `SlashCommandTextObserver`. |
| **`renumberNumberedLists()`** | Internal pure function that scans a block list for consecutive `NumberedList` runs and corrects sequential numbers. Called as post-processing in 8 action reducers. |
