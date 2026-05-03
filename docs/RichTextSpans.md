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
9. [Link State and Mutation API](#link-state-and-mutation-api)
10. [Toolbar and FormattingState](#toolbar-and-formattingstate)
11. [Serialization](#serialization)
12. [Coordinate System](#coordinate-system)
13. [Key Invariants](#key-invariants)

---

## Architecture Overview

```
┌───────────────────────────────────────────────────────────┐
│  Toolbar UI                                               │
│  FormattingState ← FormattingStateObserver                │
│  FormattingActions → SpanActionDispatcher                 │
│  LinkState ← LinkStateCalculator                          │
│  LinkActions → LinkActionDispatcher                       │
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
    data class Link(val url: String) : SpanStyle
    data class Custom(val typeId: String, val payload: String? = null) : SpanStyle
}
```

- `Custom.payload` is an opaque JSON string. The core layer never parses it — serialization layer handles conversion.
- **Kind-based matching for Highlight:** `SpanStyle.kindMatches(a, b)` treats all `Highlight` instances as equivalent regardless of `colorArgb`. This is used throughout algorithms, formatting state, and toggle logic. Link merge identity still uses exact `Link(url)` equality so same-URL links can merge and different URLs remain distinct.
- `Highlight.colorArgb` is retained for serialization backward compatibility but ignored at render time — the theme's `CascadeEditorColors.highlight` controls the visual color.
- `Link.url` stores only the normalized URL. The visible link title remains the covered `TextSpan` range, so title text cannot drift from duplicated span metadata.
- `LinkUrlPolicy.validate(...)` is the shared URL policy for producing normalized URLs and stable `LinkValidationError` values. The policy is permissive (Slack-style): anything non-blank is accepted. Inputs that already contain `://` are kept verbatim after trimming; everything else is normalized by prepending `https://`. The only `LinkValidationError` is `Blank`. Blocking unsafe schemes, allowlists, and similar policy decisions are deferred to consumer-side opening callbacks (`onOpenLink`).
- `SpanStyle.kindKey(Link(url))` uses the full link style, so same-URL links share merge identity and different URLs stay distinct. Link-wide operation matching, such as removing or querying all links regardless of URL, is handled inside `SpanAlgorithms` rather than by changing this merge key.

> **Source compatibility note.** `SpanStyle` is a public sealed interface; adding `Link(url)` introduces a new exhaustive `when` case. Consumers who match `SpanStyle` exhaustively need to add a `Link` branch (or an `else`) to compile against this version. Custom `SpanStyle.Custom` consumers are unaffected.

### BlockContent.Text (`core/BlockContent.kt`)

```kotlin
data class Text(
    val text: String,
    val spans: List<TextSpan> = emptyList(),
) : BlockContent
```

The `spans` field defaults to empty, preserving backward compatibility with all existing block factory functions.

### Capability Gating — `supportsSpans`

`BlockType` declares an orthogonal `supportsSpans: Boolean` capability with default `get() = supportsText`. Every existing built-in inherits the default and behaves as before. `BlockType.Code` overrides to `false`, making it the first text-supporting block that opts out of rich-text spans. `CustomBlockType` consumers may override `supportsSpans = false` to ship plain-text custom blocks and inherit the same gating for free.

When `block.type.supportsSpans == false`:

- **FormattingState** — `FormattingStateCalculator` returns `canFormat = false`, so the toolbar disables Bold/Italic/Underline/Strikethrough/InlineCode/Highlight buttons.
- **Formatting actions** — `DefaultFormattingActions.resolveContext()` returns `null`, so toolbar `toggleStyle/applyStyle/removeStyle` and Cmd/Ctrl+B/I/U keyboard shortcuts (which route through `TextBlockKeyHandler` → `formattingActions.toggleStyle`) no-op.
- **LinkState** — `LinkStateCalculator` returns `canLink = false`; the link button is inert.
- **Link mutations** — `LinkActionDispatcher.applyLink/removeLink` no-op against non-spans target blocks. URL validation in `applyLink` still runs so callers receive a `LinkValidationResult`, but no runtime/snapshot mutation and no history entry is captured.
- **Reducers** — `ApplySpanStyle` and `RemoveSpanStyle` are no-ops. `ConvertBlockType` to a non-spans target preserves text and emits `spans = emptyList()`. `MergeBlocks` into a non-spans target produces empty spans.
- **Runtime state** — `TextBlockField` initializes `BlockSpanStates.getOrCreate(...)` with `emptyList()`, sets `outputTransformation = null` (no `SpanMapper` decoration), and constructs no `SpanMaintenanceTextObserver`. The unfocused link-hit overlay passes `emptyList()` to `LinkHitTester.linkUrlForTap(...)`.
- **Persistence** — `DocumentSchema` encode/decode and `DocumentSerializationExt.resolveCurrentBlocks(...)` defensively strip spans from text content for non-spans block types. Malformed JSON arriving with non-empty spans on a code block decodes to `BlockContent.Text(text, emptyList())` without producing a `DocumentDecodeWarning`.

All gates key on `block.type.supportsSpans` (or `focusedBlockType.supportsSpans`) and never pattern-match on `BlockType.Code` directly. `TextBlockField` includes `block.type.typeId` (or the derived predicate) in the `remember(...)` keys for span-affected observers and the `outputTransformation`, so same-id Paragraph ↔ Code conversion drops the prior runtime constructs entirely. The same observer-key strategy applies to `SlashCommandTextObserver` (suppressed when `block.type is BlockType.Code`) and `ListAutoDetectObserver` (suppressed via the call-site `isCurrentlyList || block.type is BlockType.Code` predicate).

> **Known programmatic-commit consume gap (Code Enter).** Code Enter mutations in `DefaultBlockCallbacks.onEnter` register a programmatic commit via `BlockTextStates.replaceVisibleRange(...)`. For non-Code blocks, `SpanMaintenanceTextObserver` consumes that commit. Code blocks have neither `SpanMaintenanceTextObserver` (suppressed by `supportsSpans`) nor — after Task 9 — a slash observer that would naturally drain it, so a pending commit can linger. The "snapshot identity changed without text change" branch in `TextBlockField` already calls `consumeProgrammaticCommit` defensively. If post-Enter typing in Code is later observed to misclassify the first user character as programmatic, the targeted fix is one extra `consumeProgrammaticCommit(block.id)` next to the `spanTextObserver?.onCommittedVisibleText(...)` call.

---

## Span Algorithms

**File:** `richtext/SpanAlgorithms.kt` — `internal object`, pure functions only.

All functions operate on a single block's span list. No whole-document scans.

### Core Operations

| Function | Purpose |
|----------|---------|
| `normalize(spans, textLength)` | Clamp to `[0, textLength]`, drop empty, canonicalize links, merge same-style overlaps, sort |
| `adjustForEdit(spans, editStart, deletedLength, insertedLength)` | Shift span coordinates after a text edit |
| `splitAt(spans, position)` | Split spans into two lists at a position (for Enter/block split) |
| `mergeSpans(firstSpans, secondSpans, firstTextLength)` | Merge two span lists (for Backspace/block merge) |
| `applyStyle(spans, rangeStart, rangeEnd, style, textLength)` | Add a style to a range, auto-merge with existing same-style spans |
| `removeStyle(spans, rangeStart, rangeEnd, style)` | Remove a style from a range (clips/splits matching spans) |
| `removeLinks(spans, rangeStart, rangeEnd)` | Remove link spans from a range regardless of URL |
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

### Link Range Semantics

Links have two identities:

- **Merge identity:** exact `SpanStyle.Link(url)` equality. Adjacent or overlapping links merge only when the URL matches.
- **Operation identity:** any `SpanStyle.Link` matches any existing link span. Removing or querying a link style is URL-agnostic.

Normalization guarantees that different-URL link spans never overlap in canonical output. When raw input contains overlapping different-URL links, spans are processed in list order: the later link owns its range, and earlier links are clipped around the overlap. Same-URL links are merged after clipping.

Applying a link first removes link coverage in the target range, preserving any before/after portions of existing links, then applies the new `SpanStyle.Link(normalizedUrl)`. Non-link spans are left untouched and may overlap links normally.

Split and merge use the same span transfer rules as other styles. Splitting inside a link creates the same URL on both sides. Merging adjacent same-URL links at a block boundary may consolidate them; adjacent different-URL links remain separate.

Insertion behavior is intentionally asymmetric:

- Insertion strictly inside a link remains linked because `adjustForEdit` expands the existing span.
- Insertion at the start boundary pushes the link right, so inserted text is not linked.
- Insertion at the end boundary does not extend the link.
- `BlockSpanStates.resolveStylesForInsertion(...)` excludes `SpanStyle.Link`, so pending-style continuation never creates a link. Link geometry is maintained by span adjustment or explicit link actions only.

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
- `removeLinkSpans(blockId, rangeStart, rangeEnd)` — removes every link URL while preserving non-link spans
- `toggleStyle(blockId, rangeStart, rangeEnd, style, textLength)`

**Queries:**
- `queryStyleStatus(blockId, rangeStart, rangeEnd, style) → StyleStatus`
- `activeStylesAt(blockId, position) → Set<SpanStyle>`

**Pending styles:**
- `getPendingStyles(blockId) → Set<SpanStyle>?` — null means "no override"
- `setPendingStyles(blockId, styles)` — queues styles for next insertion
- `clearPendingStyles(blockId)`
- `resolveStylesForInsertion(blockId, position) → Set<SpanStyle>` — consumes pending if set, otherwise inherits from `position - 1`; link styles are excluded from insertion continuation

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
SpanMapper.applyStyles(spans)  — maps domain styles to Compose SpanStyle using theme colors
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
   - `Link` → theme `linkText` color + underline
   - `Custom` → `null` (not rendered, but retained in state)

2. **Cumulative decoration overlay** — when underline-producing spans (`Underline` or `Link`) and `StrikeThrough` spans overlap, an overlay span with `TextDecoration.combine(Underline, LineThrough)` is generated for the intersection range so both decorations render.

3. **Link/underline de-duplication** — explicit `Underline` runs are split around link ranges so link-provided underline owns the shared range. This avoids duplicate underline-only runs while preserving explicit underline before and after the link.

4. **Sentinel offset** — visible-coordinate spans are shifted by +1 to account for the ZWSP sentinel character prepended to text field content.

5. **Defensive clamping** — every span is clamped to current visible text length on each transform pass. Invalid/empty ranges are skipped.

### Stable OutputTransformation

Each block gets a stable `OutputTransformation` instance that reads the latest span state on every transform pass (via `SpanMapper.applyStyles(...)`). The transformation captures `CascadeEditorColors.inlineCodeBackground`, `highlight`, and `linkText` at composition time and includes those values in the `remember` key. This avoids transformation-identity churn during typing while still reacting to theme changes.

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

## Link State and Mutation API

Links use a dedicated state/action layer instead of `FormattingActions` because they need URL validation, captured target ranges, optional visible-title replacement, and URL-agnostic remove behavior.

### LinkTarget and LinkState

**Files:** `richtext/LinkState.kt`, `richtext/LinkStateCalculator.kt`

`LinkTarget(blockId, rangeStart, rangeEnd)` captures a text-block range in visible-text coordinates. Popup/session code should keep this value when opening link UI and pass it back to mutation actions, even if focus or cursor position changes before Apply/Remove.

`LinkState` exposes:

| Property | Meaning |
|----------|---------|
| `canLink` | True only for a focused text block with no block selection and no active drag |
| `target` | Current cursor/selection as a `LinkTarget` |
| `targetText` | Visible text inside the current non-collapsed target |
| `existingUrl` | Existing URL only when the target is covered by exactly one link URL |
| `existingLinkRange` / `existingLinkText` | Full link span range/text for the resolved existing URL |
| `isInsideLink` | True only for a collapsed cursor strictly inside a link span |
| `intersectsLink` | True when the target touches any link span, including mixed linked/unlinked selections |

Collapsed cursors resolve existing links only when strictly inside the range (`span.start < cursor < span.end`). Exact start/end boundaries are outside links. Ranged selections expose `existingUrl` only when the whole selected range is covered by one URL; mixed linked/unlinked or multiple-URL selections keep `existingUrl == null` while still setting `intersectsLink`. `selectionCollapsed` and `existingLinkText` are cached convenience values derived from the same visible-text snapshot used to compute the target.

### LinkActions, LinkChromeActions, and LinkActionDispatcher

**Files:** `richtext/LinkActions.kt`, `richtext/LinkChromeActions.kt`, `richtext/LinkActionDispatcher.kt`

```kotlin
// Minimal target-based mutation surface — for popup sessions and any caller
// that already captured a target. Implemented by LinkActionDispatcher.
public interface LinkActions {
    public fun applyLink(
        target: LinkTarget,
        url: String,
        title: String? = null,
    ): LinkValidationResult

    public fun removeLink(target: LinkTarget)
}

// Chrome surface adds current-target sugar with concrete defaults that forward
// to applyLink / removeLink. Exposed via LocalLinkActions.
public interface LinkChromeActions : LinkActions {
    public fun currentTarget(): LinkTarget?

    public fun applyLinkAtCurrentTarget(
        url: String,
        title: String? = null,
    ): LinkValidationResult? = currentTarget()?.let { applyLink(it, url, title) }

    public fun removeLinkAtCurrentTarget() {
        currentTarget()?.let { removeLink(it) }
    }
}
```

`LinkActionDispatcher` normalizes URLs through `LinkUrlPolicy` before mutating state. Blank URLs return `LinkValidationResult.Invalid(LinkValidationError.Blank)` and do not touch runtime or snapshot state. `LinkValidationResult.Valid` reports only that URL normalization produced a non-blank target; stale or missing captured targets can still no-op safely. The current-target helpers live on `LinkChromeActions` only and route through the same `applyLink` / `removeLink` paths after consulting the live `LinkState.target`; `null` is returned by `applyLinkAtCurrentTarget` only when no target is currently available.

Mutation flow:

1. Read latest runtime visible text and spans for `target.blockId`.
2. Clamp the captured `LinkTarget` to current text length.
3. For apply/edit, optionally replace visible title text via `BlockTextStates.replaceVisibleRange(...)`.
4. Update runtime spans through `BlockSpanStates` (`applyStyle` for links, `removeStyle` with URL-agnostic link matching for removal).
5. Sync snapshot once with `UpdateBlockContent(currentText, currentSpans)`.
6. When an `EditorStateHolder` is supplied, capture one isolated history entry and re-anchor the block-local text-history tracker.

Blank/null titles preserve selected text. At a collapsed cursor, a blank/null title inserts the normalized URL as visible text and links it. Non-blank titles are inserted exactly as provided.

Stale targets are safe: missing runtime blocks no-op, ranges clamp to current text, and a stale non-collapsed target that collapses without an explicit replacement title no-ops rather than inserting unexpected text. Actions never re-resolve to a different link just because the cursor moved.

---

## Link Opening

**Files:** `richtext/LinkHitTester.kt`, `ui/LinkOpener.kt`, `ui/renderers/TextBlockField.kt`

Opening links is deliberately separate from link editing. `CascadeEditor(onOpenLink = ...)` lets apps route normalized URLs to analytics, confirmation UI, in-app web views, or platform openers. When `onOpenLink` is null, the editor uses Compose's `LocalUriHandler.openUri(...)` and swallows platform opening failures.

`TextBlockField` only attempts link opening from the unfocused tap overlay. Focused blocks keep ordinary editable text behavior and do not open links. Non-link taps in unfocused blocks keep the existing focus/cursor behavior.

`LinkHitTester` resolves visible-text offsets against half-open link spans:

- Start offsets are included.
- End offsets are excluded.
- Non-link spans do not affect hit testing.
- Stored link URLs are revalidated through `LinkUrlPolicy` before opening.
- Active drag, block selection, and active text selection suppress opening.

Opening a link never dispatches an editor action, never writes runtime text/span state, and never records history. Long-press does not open links because the opener is only called from tap handling.

### Out of V1

- **No popup "Open" action.** The link popup (`LinkPopupSlot.Default` and the `LinkPopupState`/`LinkPopupActions` surface) does not include an open-link button. Opening is exclusively the unfocused-block tap path described above. This keeps the popup focused on editing and avoids bypassing the focused-block editing semantics.
- **No automatic linkification.** Typing a URL-shaped string is not converted to a link. The user must explicitly invoke the link UI (toolbar button, custom chrome) to create a link span.
- **No paste-to-link conversion.** Pasting a URL while text is selected does not convert the selection into a link, and pasting a URL at a cursor position does not auto-link the inserted text. Apps that want this behavior can layer it on top of `LinkActions` themselves.

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
| Link | `"link"` | `url: String` |
| Custom | `"custom"` | `typeId: String`, `payload?: JsonElement` |

Link URLs are normalized through `LinkUrlPolicy` at the persistence boundary. Bare domain-shaped values decode to `https://...`; values that already contain `://` are kept verbatim. Only blank URLs and link entries whose `url` field is missing, non-string (e.g. a numeric primitive), or `null` cause the affected link span to be dropped — surrounding text and other spans decode normally.

### Decode Normalization

- Span coordinates clamped to `[0, text.length]`
- Empty spans dropped
- Unknown style types dropped (not fatal)
- Malformed span entries (non-object) dropped
- Missing required fields cause individual span drop, not whole decode failure
- Invalid link URLs cause individual link span drop, not whole decode failure

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

10. **Link URL policy is centralized** — link creation should use `LinkUrlPolicy.validate(...)` and store only the returned normalized URL in `SpanStyle.Link`.

11. **Link actions use captured targets** — link popup/session code passes a `LinkTarget` back to `LinkActionDispatcher`; the dispatcher clamps that target against current runtime text and never retargets based on later cursor movement.

12. **Link opening is read-only** — unfocused link taps route through `LinkHitTester` and `LocalLinkOpener` without dispatching editor actions or touching history.

---

## File Reference

| File | Purpose |
|------|---------|
| `core/TextSpan.kt` | `TextSpan` data class |
| `core/SpanStyle.kt` | `SpanStyle` sealed hierarchy |
| `core/BlockContent.kt` | `BlockContent.Text` with `spans` field |
| `richtext/LinkUrlPolicy.kt` | Shared link URL validation and normalization |
| `richtext/LinkHitTester.kt` | Pure visible-offset link opening resolver |
| `richtext/SpanAlgorithms.kt` | Pure span manipulation functions + `StyleStatus` enum |
| `richtext/SpanMapper.kt` | Domain-to-Compose style mapping + `OutputTransformation` builder |
| `richtext/SpanMaintenanceTextObserver.kt` | User-edit span coordinate maintenance |
| `richtext/SpanActionDispatcher.kt` | Formatting entry point (runtime + snapshot sync) |
| `richtext/LinkState.kt` | `LinkTarget` and immutable `LinkState` snapshot |
| `richtext/LinkStateCalculator.kt` | Pure focused-block link state computation |
| `richtext/LinkActions.kt` | Link mutation action interface |
| `richtext/LinkActionDispatcher.kt` | Link apply/edit/remove entry point (runtime + snapshot + history sync) |
| `ui/LinkOpener.kt` | Internal opener local and default/custom URL opening policy |
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
