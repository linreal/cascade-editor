# Rich Text Spans — Developer Guide

This document explains how inline rich text formatting (bold, italic, underline, etc.) works in CascadeEditor. It covers the domain model, algorithms, runtime state management, rendering, edit maintenance, serialization, and the toolbar/formatting API.

All file paths are relative to `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/`.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Domain Model](#domain-model)
3. [Span Algorithms](#span-algorithms)
4. [Runtime State — BlockSpanStates](#runtime-state--blockspanstates)
5. [Rendering Pipeline](#rendering-pipeline)
6. [Edit Maintenance](#edit-maintenance)
7. [Programmatic Edits (Split / Merge)](#programmatic-edits-split--merge)
8. [Formatting API — SpanActionDispatcher](#formatting-api--spanactiondispatcher)
9. [Toolbar and FormattingState](#toolbar-and-formattingstate)
10. [Serialization](#serialization)
11. [Coordinate System](#coordinate-system)
12. [Key Invariants](#key-invariants)

---

## Architecture Overview

```
┌───────────────────────────────────────────────────────────┐
│  Toolbar UI                                               │
│  FormattingState ← FormattingStateObserver                │
│  FormattingActions → SpanActionDispatcher                 │
├───────────────────────────────────────────────────────────┤
│  Rendering                                                │
│  SpanMapper → OutputTransformation → BasicTextField       │
├───────────────────────────────────────────────────────────┤
│  Edit Maintenance                                         │
│  SpanMaintenanceTextObserver → BlockSpanStates            │
├───────────────────────────────────────────────────────────┤
│  Runtime State                                            │
│  BlockSpanStates (per-block MutableState<List<TextSpan>>) │
├───────────────────────────────────────────────────────────┤
│  Algorithms                                               │
│  SpanAlgorithms (pure functions, no side effects)         │
├───────────────────────────────────────────────────────────┤
│  Domain Model                                             │
│  TextSpan, SpanStyle, BlockContent.Text.spans             │
├───────────────────────────────────────────────────────────┤
│  Persistence                                              │
│  RichTextSchema (JSON encode/decode)                      │
└───────────────────────────────────────────────────────────┘
```

There are two parallel representations of span data:

- **Snapshot state** — `BlockContent.Text.spans` inside `EditorState`. Immutable. Updated through action dispatch. Used for persistence and structural operations.
- **Runtime state** — `BlockSpanStates`. Mutable, Compose-reactive. The live source of truth during editing. Drives rendering and style queries.

Both are kept in sync: runtime updates are reflected to snapshot via `UpdateBlockContent` dispatch, and snapshot spans initialize runtime state when blocks are created.

---

## Domain Model

### TextSpan (`core/TextSpan.kt`)

```kotlin
@Immutable
data class TextSpan(
    val start: Int,  // inclusive
    val end: Int,    // exclusive
    val style: SpanStyle,
)
```

- Half-open interval: `[start, end)` in **visible-text coordinates** (excludes sentinel characters).
- Validates: `start >= 0` and `end >= start`.
- Multiple spans can overlap with different styles (cumulative application — e.g., bold + italic on the same range).

### SpanStyle (`core/SpanStyle.kt`)

```kotlin
sealed interface SpanStyle {
    data object Bold : SpanStyle
    data object Italic : SpanStyle
    data object Underline : SpanStyle
    data object StrikeThrough : SpanStyle
    data object InlineCode : SpanStyle
    data class Highlight(val colorArgb: Long) : SpanStyle
    data class Custom(val typeId: String, val payload: String? = null) : SpanStyle
}
```

- `Custom.payload` is an opaque JSON string. The core layer never parses it — serialization layer handles conversion.
- **Kind-based matching for Highlight:** `SpanStyle.kindMatches(a, b)` treats all `Highlight` instances as equivalent regardless of `colorArgb`. This is used throughout algorithms, formatting state, and toggle logic. Other styles use exact data-class equality.
- `Highlight.colorArgb` is retained for serialization backward compatibility but ignored at render time — the theme's `CascadeEditorColors.highlight` controls the visual color.
- **Link is not in V1.** Link span support is deferred to a future version.

### BlockContent.Text (`core/BlockContent.kt`)

```kotlin
data class Text(
    val text: String,
    val spans: List<TextSpan> = emptyList(),
) : BlockContent
```

The `spans` field defaults to empty, preserving backward compatibility with all existing block factory functions.

---

## Span Algorithms

**File:** `richtext/SpanAlgorithms.kt` — `internal object`, pure functions only.

All functions operate on a single block's span list. No whole-document scans.

### Core Operations

| Function | Purpose |
|----------|---------|
| `normalize(spans, textLength)` | Clamp to `[0, textLength]`, drop empty, merge same-style overlaps, sort |
| `adjustForEdit(spans, editStart, deletedLength, insertedLength)` | Shift span coordinates after a text edit |
| `splitAt(spans, position)` | Split spans into two lists at a position (for Enter/block split) |
| `mergeSpans(firstSpans, secondSpans, firstTextLength)` | Merge two span lists (for Backspace/block merge) |
| `applyStyle(spans, rangeStart, rangeEnd, style, textLength)` | Add a style to a range, auto-merge with existing same-style spans |
| `removeStyle(spans, rangeStart, rangeEnd, style)` | Remove a style from a range (clips/splits matching spans) |
| `toggleStyle(spans, rangeStart, rangeEnd, style, textLength)` | Remove if fully active, otherwise apply |
| `queryStyleStatus(spans, rangeStart, rangeEnd, style)` | Returns `FullyActive`, `Partial`, or `Absent` |
| `activeStylesAt(spans, position)` | Returns all styles active at a cursor position |

### Edit Adjustment Boundary Rules

When text is edited at `[editStart, editStart + deletedLength)` with `insertedLength` new characters:

- **Span start** uses "after" bias — insertions at span start push the span right (new character is NOT automatically styled).
- **Span end** uses "before" bias — insertions at span end do NOT extend the span.

This design means new characters are unstyled by default. The pending-style mechanism (see [Edit Maintenance](#edit-maintenance)) handles style continuation for typed characters.

### StyleStatus (`richtext/SpanAlgorithms.kt`)

```kotlin
enum class StyleStatus {
    FullyActive,  // Style covers the entire queried range
    Partial,      // Style covers part of the queried range
    Absent,       // Style is not present
}
```

For a collapsed cursor (`start == end`), only `FullyActive` or `Absent` is returned (checks containment).

---

## Runtime State — BlockSpanStates

**File:** `state/BlockSpanStates.kt`

`BlockSpanStates` is the **live source of truth** for spans during editing. It parallels `BlockTextStates` (which manages `TextFieldState` instances).

### Storage

- One `MutableState<List<TextSpan>>` per block — Compose snapshot-reactive, so composable readers (like `OutputTransformation`) recompose when spans change.
- A separate `mutableStateMapOf<BlockId, Set<SpanStyle>>` for **pending styles** (styles queued for the next insertion).

### Lifecycle

| Method | When |
|--------|------|
| `getOrCreate(blockId, initialSpans, textLength)` | Called in `TextBlockRenderer` when a block first renders. Normalizes and clamps initial spans. |
| `cleanup(existingBlockIds)` | Called in `CascadeEditor`'s `LaunchedEffect(state.blocks)` to remove stale entries when blocks are deleted. |
| `remove(blockId)` | Explicit removal for a single block. |

### Key APIs

**Edit adjustment:**
- `adjustForUserEdit(blockId, editStart, deletedLength, insertedLength)` — delegates to `SpanAlgorithms.adjustForEdit`

**Transfer (split/merge):**
- `split(sourceBlockId, newBlockId, position)` — splits spans at position, clears pending styles on both blocks
- `mergeInto(sourceId, targetId, targetTextLength)` — merges source spans into target, removes source entry, clears pending styles on both

**Style operations:**
- `applyStyle(blockId, rangeStart, rangeEnd, style, textLength)`
- `removeStyle(blockId, rangeStart, rangeEnd, style)`
- `toggleStyle(blockId, rangeStart, rangeEnd, style, textLength)`

**Queries:**
- `queryStyleStatus(blockId, rangeStart, rangeEnd, style) → StyleStatus`
- `activeStylesAt(blockId, position) → Set<SpanStyle>`

**Pending styles:**
- `getPendingStyles(blockId) → Set<SpanStyle>?` — null means "no override"
- `setPendingStyles(blockId, styles)` — queues styles for next insertion
- `clearPendingStyles(blockId)`
- `resolveStylesForInsertion(blockId, position) → Set<SpanStyle>` — consumes pending if set, otherwise inherits from `position - 1`

### Invariant Enforcement

`getOrCreate` and `set` normalize and clamp spans against current visible text length at API ingress. Defensive copies prevent aliasing.

### CompositionLocal

Provided via `LocalBlockSpanStates` (file: `ui/LocalBlockSpanStates.kt`), created and remembered in `CascadeEditor`, available in all renderers.

---

## Rendering Pipeline

### How Spans Become Visual

```
BlockSpanStates (runtime spans)
    ↓ read by
TextBlockRenderer (observes State<List<TextSpan>> per block)
    ↓ passes to
SpanMapper.applyStyles(spans)  — maps domain styles to Compose SpanStyle
    ↓ called within
OutputTransformation  — applied to BasicTextField
    ↓
Visual rendering (bold, italic, underline, etc.)
```

**File:** `richtext/SpanMapper.kt` — `internal object`

### SpanMapper Responsibilities

1. **Domain → Compose mapping** (`toComposeSpanStyle`):
   - `Bold` → `FontWeight.Bold`
   - `Italic` → `FontStyle.Italic`
   - `Underline` → `TextDecoration.Underline`
   - `StrikeThrough` → `TextDecoration.LineThrough`
   - `InlineCode` → `FontFamily.Monospace` + semi-transparent background
   - `Highlight` → theme `highlightBackground` color (span's `colorArgb` ignored at render time)
   - `Custom` → `null` (not rendered, but retained in state)

2. **Cumulative decoration overlay** — when `Underline` and `StrikeThrough` spans overlap, an overlay span with `TextDecoration.combine(Underline, LineThrough)` is generated for the intersection range so both decorations render.

3. **Sentinel offset** — visible-coordinate spans are shifted by +1 to account for the ZWSP sentinel character prepended to text field content.

4. **Defensive clamping** — every span is clamped to current visible text length on each transform pass. Invalid/empty ranges are skipped.

### Stable OutputTransformation

Each block gets a stable `OutputTransformation` instance that reads the latest span state on every transform pass (via `SpanMapper.applyStyles(...)`). This avoids transformation-identity churn during typing, which would cause IME instability.

---

## Edit Maintenance

**File:** `richtext/SpanMaintenanceTextObserver.kt`

When the user types, deletes, or pastes text, spans must be adjusted to remain coherent. This is handled by `SpanMaintenanceTextObserver`, **not** by `InputTransformation` (to avoid mutating external state during the input pipeline).

### Flow

```
User types/deletes/pastes
    ↓
TextFieldState commits new text
    ↓
snapshotFlow { textFieldState.visibleText() }  (in TextBlockRenderer)
    ↓
SpanMaintenanceTextObserver.onCommittedVisibleText(newText)
    ↓
1. Check for programmatic commit → skip if exact match
2. Diff previous vs current text → compute (start, deletedLength, insertedLength)
3. blockSpanStates.adjustForUserEdit(...)  → shift span coordinates
4. Apply pending/continuation styles to inserted range
```

### Pending Style Resolution for Insertions

When new characters are inserted:

1. If explicit pending styles are set (user toggled Bold before typing) → use those, then clear them.
2. Otherwise, inherit styles from `position - 1` (continuation semantics — typing at end of bold range continues bold).
3. If inherited styles differ from what `adjustForEdit` produced (e.g., the adjustment expanded a style the user doesn't want), corrections are applied via `applyStyle`/`removeStyle`.

### Programmatic Commit Guard

`BlockTextStates` tracks expected programmatic commits (from `setText` / `mergeInto`). The observer checks this before diffing:
- If committed text exactly matches the programmatic expectation → skip (no user edit happened).
- If committed text differs (coalesced user + programmatic edit) → rebase: use the programmatic text as the diff baseline so only the residual user edit is processed.

This prevents double-adjustment when programmatic operations (split, merge) change text.

---

## Programmatic Edits (Split / Merge)

**File:** `registry/BlockRenderer.kt` (`DefaultBlockCallbacks`)

### Enter (Block Split)

```
onEnter:
1. Generate newBlockId at callback boundary
2. Compute continuation styles (before split clears pending):
   - If pending styles exist → use those
   - Else if cursor at end of block → inherit from activeStylesAt(position - 1)
   - Else → no continuation
3. blockSpanStates.split(sourceBlockId, newBlockId, cursorPosition)
   → clips crossing spans, shifts second block to 0-based
4. If continuation styles non-empty → setPendingStyles(newBlockId, continuationStyles)
5. Dispatch SplitBlock action with newBlockId + runtime span payloads
```

### Backspace at Start (Block Merge)

```
onBackspaceAtStart:
1. Capture targetTextLength from blockTextStates.mergeInto(...)
2. blockSpanStates.mergeInto(sourceId, targetId, targetTextLength)
   → shifts source spans, merges, clears pending on both
3. Sync merged content to snapshot via UpdateBlockContent
4. Dispatch DeleteBlock
```

### Key Design Decisions

- **Deterministic split ID** — `newBlockId` is generated at the callback boundary and passed to both runtime span split and reducer dispatch, ensuring they target the same block.
- **Pending style clearing on merge** — prevents stale pending styles from bleeding across merge boundaries.
- **Snapshot sync before delete** — merged text+spans are written to snapshot via `UpdateBlockContent` before the source block is deleted, ensuring snapshot consistency.

---

## Formatting API — SpanActionDispatcher

**File:** `richtext/SpanActionDispatcher.kt`

The recommended entry point for all formatting operations (toolbar, keyboard shortcuts, programmatic).

```kotlin
class SpanActionDispatcher(
    dispatchFn: (EditorAction) -> Unit,
    blockTextStates: BlockTextStates,
    blockSpanStates: BlockSpanStates,
)
```

### Methods

| Method | Behavior |
|--------|----------|
| `applyStyle(blockId, rangeStart, rangeEnd, style)` | Updates runtime spans → syncs snapshot via `UpdateBlockContent` |
| `removeStyle(blockId, rangeStart, rangeEnd, style)` | Updates runtime spans → syncs snapshot via `UpdateBlockContent` |
| `toggleStyle(blockId, rangeStart, rangeEnd, style)` | **Collapsed cursor**: toggles pending style (no snapshot dispatch). **Ranged**: remove if fully active, else apply → syncs snapshot. |

### Why Not Dispatch ApplySpanStyle/RemoveSpanStyle Directly?

`ApplySpanStyle` and `RemoveSpanStyle` are snapshot-only actions. During active editing, the snapshot's text length may be stale (lagging behind the runtime `TextFieldState`). `SpanActionDispatcher` avoids this mismatch by:
1. Reading current visible text from `BlockTextStates`
2. Updating `BlockSpanStates` (immediate visual)
3. Syncing snapshot via `UpdateBlockContent` which carries current runtime text + spans

### CompositionLocal

Provided via `LocalSpanActionDispatcher` (`ui/LocalSpanActionDispatcher.kt`).

---

## Toolbar and FormattingState

### FormattingState (`richtext/FormattingState.kt`)

Immutable snapshot of the current formatting context:

```kotlin
data class FormattingState(
    val styles: Map<SpanStyle, StyleStatus>,  // status per tracked style
    val canFormat: Boolean,                    // false = toolbar disabled
    val focusedBlockId: BlockId?,
    val selectionCollapsed: Boolean,
)
```

`canFormat` is `false` when:
- No block is focused
- Focused block doesn't support text (Divider, Image)
- Focused block is `Code`
- Multi-block selection is active
- Drag-and-drop is in progress

### FormattingActions (`richtext/FormattingActions.kt`)

```kotlin
interface FormattingActions {
    fun toggleStyle(style: SpanStyle)
    fun applyStyle(style: SpanStyle)
    fun removeStyle(style: SpanStyle)
}
```

`DefaultFormattingActions` resolves the focused block and visible selection **at invocation time** and delegates to `SpanActionDispatcher`. No-op when `canFormat` conditions aren't met.

### FormattingStateCalculator (`richtext/FormattingStateCalculator.kt`)

Pure function that computes `FormattingState` from inputs. Rules:

- **Collapsed cursor with pending styles set** → pending styles are canonical (pending = `FullyActive`, others = `Absent`)
- **Collapsed cursor without pending** → continuation semantics from `activeStylesAt(position - 1)`
- **Ranged selection** → `queryStyleStatus` per tracked style (pending styles ignored)

### FormattingStateObserver (`richtext/FormattingStateObserver.kt`)

Composable bridge (`rememberFormattingState()`) that reads snapshot state and produces `State<FormattingState>`. Uses `derivedStateOf` chains so cursor movement within the same style region does NOT trigger recomposition.

### Toolbar UI (`ui/RichTextToolbar.kt`)

Config-driven (`RichTextToolbarConfig`) horizontal scrollable row of toggle buttons. V1 default buttons: Bold, Italic, Underline, StrikeThrough, InlineCode, Highlight. The Highlight button's color is injected from `CascadeEditorColors.highlight` at the `CascadeEditor` composable level.

All toolbar buttons use `focusProperties { canFocus = false }` to prevent stealing focus from the text field.

### CascadeEditor Integration

```kotlin
CascadeEditor(
    stateHolder = ...,
    toolbar = ToolbarSlot.Default(),              // or None, or Custom { state, actions -> ... }
    onFormattingStateChanged = { state -> ... },  // optional external callback
)
```

---

## Serialization

**File:** `serialization/RichTextSchema.kt`

JSON schema (version 1):

```json
{
  "version": 1,
  "text": "Hello bold world",
  "spans": [
    { "start": 6, "end": 10, "style": { "type": "bold" } }
  ]
}
```

### Style Type Mapping

| SpanStyle | JSON `type` | Extra Fields |
|-----------|------------|--------------|
| Bold | `"bold"` | — |
| Italic | `"italic"` | — |
| Underline | `"underline"` | — |
| StrikeThrough | `"strikethrough"` | — |
| InlineCode | `"inline_code"` | — |
| Highlight | `"highlight"` | `colorArgb: Long` |
| Custom | `"custom"` | `typeId: String`, `payload?: JsonElement` |

### Decode Normalization

- Span coordinates clamped to `[0, text.length]`
- Empty spans dropped
- Unknown style types dropped (not fatal)
- Malformed span entries (non-object) dropped
- Missing required fields cause individual span drop, not whole decode failure

### Custom Payload Canonicalization

- **Encode:** `Custom.payload` (String?) is parsed to `JsonElement` and embedded as structured JSON.
- **Decode:** Structured `JsonElement` is serialized back to canonical string.
- This normalizes formatting differences (`{"a":1}` vs `{ "a" : 1 }`).

### API

```kotlin
RichTextSchema.encode(content: BlockContent.Text): JsonObject
RichTextSchema.encodeToString(content: BlockContent.Text): String
RichTextSchema.decode(json: JsonObject): BlockContent.Text
RichTextSchema.decodeFromString(jsonString: String): BlockContent.Text
```

---

## Coordinate System

The editor uses a ZWSP (zero-width space) sentinel as the first character of every text field to enable backspace detection. This creates two coordinate spaces:

| Space | Description | Used By |
|-------|-------------|---------|
| **Buffer coordinates** | Include sentinel at index 0 | `TextFieldState.text`, `TextFieldState.selection` |
| **Visible coordinates** | Exclude sentinel (offset by -1) | `TextSpan`, `SpanAlgorithms`, `BlockSpanStates`, `SpanActionDispatcher` |

Conversion helpers in `ui/BackspaceAwareTextEdit.kt`:
- `TextFieldState.visibleText()` — buffer text minus sentinel
- `TextFieldState.visibleCursorPosition()` — cursor position minus 1, clamped to 0
- `TextFieldState.visibleSelection()` — selection range minus 1 on each bound, clamped to 0

`SpanMapper` handles the reverse shift (+1) when applying styles to `TextFieldBuffer` in `OutputTransformation`.

**Rule:** All span algorithms, `BlockSpanStates`, and `SpanActionDispatcher` work exclusively in visible coordinates. The sentinel offset conversion happens at the boundary (renderer → observer, and mapper → text field buffer).

---

## Key Invariants

1. **Span coordinates never include the sentinel** — all `TextSpan` ranges are in visible coordinates.

2. **Normalization at ingress** — `BlockSpanStates.getOrCreate()` and `set()` normalize and clamp spans against current text length. Invalid data cannot enter runtime state.

3. **No external state mutation during InputTransformation** — span maintenance runs post-commit via `snapshotFlow`, not during input transform. This prevents typing corruption and IME instability.

4. **Programmatic edits are observer-safe** — `BlockTextStates` tracks expected programmatic commits. The observer consumes/rebases these to avoid double-adjusting spans.

5. **Split ID determinism** — `newBlockId` is generated once at the callback boundary and used for both runtime span split and reducer dispatch.

6. **Pending style clearing on merge** — prevents style bleed across block boundaries.

7. **Snapshot sync via UpdateBlockContent** — `SpanActionDispatcher` carries current runtime text + spans when syncing to snapshot, avoiding stale-text-length mismatch.

8. **Stable OutputTransformation identity** — each block creates one `OutputTransformation` instance that reads latest spans per pass, avoiding recomposition/IME churn from identity changes.

9. **Reducers are pure** — `ApplySpanStyle`/`RemoveSpanStyle` actions only modify snapshot state. Runtime mutations happen at the integration layer (`SpanActionDispatcher`, `DefaultBlockCallbacks`).

---

## File Reference

| File | Purpose |
|------|---------|
| `core/TextSpan.kt` | `TextSpan` data class |
| `core/SpanStyle.kt` | `SpanStyle` sealed hierarchy |
| `core/BlockContent.kt` | `BlockContent.Text` with `spans` field |
| `richtext/SpanAlgorithms.kt` | Pure span manipulation functions + `StyleStatus` enum |
| `richtext/SpanMapper.kt` | Domain-to-Compose style mapping + `OutputTransformation` builder |
| `richtext/SpanMaintenanceTextObserver.kt` | User-edit span coordinate maintenance |
| `richtext/SpanActionDispatcher.kt` | Formatting entry point (runtime + snapshot sync) |
| `richtext/FormattingState.kt` | Immutable formatting state snapshot |
| `richtext/FormattingActions.kt` | Formatting action interface |
| `richtext/FormattingStateCalculator.kt` | Pure `FormattingState` computation |
| `richtext/FormattingStateObserver.kt` | Reactive Compose bridge for `FormattingState` |
| `richtext/DefaultFormattingActions.kt` | `FormattingActions` implementation |
| `state/BlockSpanStates.kt` | Per-block runtime span state holder |
| `ui/LocalBlockSpanStates.kt` | `CompositionLocal` for `BlockSpanStates` |
| `ui/LocalSpanActionDispatcher.kt` | `CompositionLocal` for `SpanActionDispatcher` |
| `ui/RichTextToolbar.kt` | Default toolbar composable |
| `ui/RichTextToolbarConfig.kt` | Toolbar button configuration |
| `ui/ToolbarSlot.kt` | Toolbar slot sealed interface (Default/None/Custom) |
| `serialization/RichTextSchema.kt` | JSON encode/decode for text + spans |
| `ui/renderers/TextBlockRenderer.kt` | Span lifecycle init, rendering, and edit observer wiring |
| `ui/CascadeEditor.kt` | Top-level wiring: creates `BlockSpanStates`, toolbar, cleanup |
| `registry/BlockRenderer.kt` | `DefaultBlockCallbacks` — span transfer on split/merge |
