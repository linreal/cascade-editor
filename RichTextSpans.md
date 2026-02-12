# Rich Text Spans V1

This document is the canonical implementation plan for rich text spans in CascadeEditor.

## 1. Scope and Goals

### Goals
- Add inline rich text styles (bold/italic/underline/strike/highlight/link/inline code) to text blocks.
- Preserve editor responsiveness and low recomposition overhead.
- Keep APIs and code paths explicit for maintainability and testability.
- Keep behavior deterministic across Android and iOS Compose targets.

### Non-goals (V1)
- Full rich clipboard import/export (HTML/RTF).
- Interactive link handling UX (tap-to-open/editor UI) beyond storing/rendering style.
- Undo/redo implementation (but V1 must remain undo-ready).
- Syntax highlighting for code blocks.

## 2. Architecture Constraints (Must Follow)

These are existing project realities that the implementation must respect:

- Live text source of truth is `BlockTextStates`, not `EditorState`.
- Reducers are pure and synchronous (`EditorAction.reduce` only transforms immutable `EditorState`).
- High-frequency text selection/cursor movement must not be pushed into `EditorState`.
- Text fields use a ZWSP sentinel at index `0`; all business logic must use visible coordinates.

References:
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockTextStates.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/action/EditorAction.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/BackspaceAwareTextEdit.kt`

## 3. Core Decisions (Resolved Open Questions)

1. State placement:
- Use `BlockSpanStates` as live editing source of truth (parallel to `BlockTextStates`).
- Keep `BlockContent.Text.spans` for initialization and persistence snapshots.

2. Rendering approach:
- Use `OutputTransformation` on `BasicTextField(state = TextFieldState)` for span visualization.
- This is supported in Compose Foundation `1.10.0` and avoids replacing editing infrastructure.

3. Span style type system:
- Use sealed `SpanStyle` plus `Custom` escape hatch.
- Keep exhaustive handling in core code and extensibility via `Custom`.

4. Code block behavior:
- Exclude code blocks from formatting controls in V1.
- Preserve span data when converting between block types (non-lossy model).

5. Internal toolbar:
- V1 default is panel above keyboard (simpler, lower risk).
- Floating selection toolbar is a later enhancement.

6. Serialization:
- JSON is the canonical format from day one.
- Markdown/HTML export is optional adapter layer, not primary persistence.

## 4. Data Model

```kotlin
public data class TextSpan(
    val start: Int, // inclusive, visible-text coordinate
    val end: Int,   // exclusive, visible-text coordinate
    val style: SpanStyle
)

public sealed interface SpanStyle {
    public data object Bold : SpanStyle
    public data object Italic : SpanStyle
    public data object Underline : SpanStyle
    public data object StrikeThrough : SpanStyle
    public data object InlineCode : SpanStyle
    public data class Highlight(val colorArgb: Long) : SpanStyle
    public data class Link(val url: String) : SpanStyle
    public data class Custom(
        val typeId: String,
        val payload: kotlinx.serialization.json.JsonElement? = null
    ) : SpanStyle
}

public data class Text(
    val text: String,
    val spans: List<TextSpan> = emptyList()
) : BlockContent
```

### Why payload is `JsonElement` instead of `Map<String, Any?>`
- Stable for cross-platform serialization.
- Avoids runtime-cast ambiguity and non-serializable values.

## 5. Coordinate and Invariant Rules

### Coordinate system
- All span coordinates are in visible text coordinates.
- ZWSP sentinel is never represented in span ranges.
- Valid range invariant: `0 <= start < end <= visibleText.length`.

### Span list invariants
- Sorted by `(start, end, styleKey)`.
- No zero-length spans.
- No overlapping spans of identical style after normalization.
- Out-of-bounds spans are clamped or removed during normalization.

### IME composition safety
- Avoid aggressive normalization that disrupts composition range behavior during active IME composition.
- Defer expensive normalization steps until composition commit when needed.

## 6. Runtime State: `BlockSpanStates`

Add a new runtime holder similar to `BlockTextStates`.

Responsibilities:
- Store per-block mutable span list.
- Keep pending continuation styles for collapsed selections.
- Provide split/merge/programmatic update helpers.
- Provide query APIs for toolbar state (`FullyActive`, `Partial`, `Absent`).

Required API surface (V1):
- `getOrCreate(blockId, initialSpans)`
- `get(blockId)`
- `set(blockId, spans)`
- `remove(blockId)`
- `cleanup(existingBlockIds)`
- `mergeInto(sourceId, targetId, targetTextLength)`
- `split(blockId, splitAt): Pair<List<TextSpan>, List<TextSpan>>`
- `applyStyle(blockId, range: TextRange, style, toggle)`
- `removeStyle(blockId, range: TextRange, style)`
- `adjustForUserEdit(blockId, changes)`
- `setPendingStyles(blockId, styles)`
- `getPendingStyles(blockId): Set<SpanStyle>`
- `getStyleStatus(blockId, range: TextRange, style): StyleStatus`

## 7. Rendering

### `OutputTransformation` usage
- Build transformation from current block spans and call `addStyle(...)`.
- Clamp every span to current visible text length before adding.
- Skip invalid/clipped-empty spans.

### Mapper
- Keep domain-to-Compose mapping as a pure function:
  `fun SpanStyle.toComposeSpanStyle(): androidx.compose.ui.text.SpanStyle`

### Performance rules
- Remember/recompute transformation only when that block's span state changes.
- Do not allocate large temporary collections per recomposition if unchanged.
- Keep operations O(k) per block where `k = span count for that block`.

## 8. Editing Pipeline and Correct Mutation Boundaries

### Critical correction
Do not mutate `BlockSpanStates` inside reducers.
Reducers remain pure. Runtime holder mutations happen in UI/callback boundary, same as text behavior today.

### User edits (typing/delete/paste)
- Chain span-maintenance `InputTransformation` with existing sentinel guard.
- Use transformation change list to adjust offsets.
- Update `BlockSpanStates` immediately after edit.

### Programmatic edits (must also update spans)
Input transformations only run for user-originated edits. Therefore span adjustments must also run for:
- `onEnter` split flow
- `onBackspaceAtStart` merge flow
- forward-delete merge flow
- any direct `setText(...)` path introduced later

## 9. Split and Merge Rules

### Split at `P`
- Left-only spans `[start, end)` where `end <= P` stay on source.
- Right-only spans where `start >= P` move to new block with shift `-P`.
- Crossing spans are split into two clipped spans.

### Merge `source -> target`
- Shift source spans by `targetVisibleTextLength`.
- Append and normalize.

### Implementation location
- Runtime transfer in `BlockSpanStates` is required.
- Reducer updates to `BlockContent.Text.spans` are snapshot synchronization only.
- Do not rely on reducer-only split/merge for live editing correctness.

## 10. Actions and API

Use `TextRange`, not `IntRange` (avoid end-inclusive bugs).

```kotlin
public data class ApplySpanStyle(
    val blockId: BlockId,
    val range: TextRange, // [start, end), visible coordinates
    val style: SpanStyle,
    val toggle: Boolean = true
) : EditorAction

public data class RemoveSpanStyle(
    val blockId: BlockId,
    val range: TextRange,
    val style: SpanStyle
) : EditorAction
```

Reducer responsibility:
- Update immutable `EditorState` snapshot model only.

Runtime responsibility:
- Update `BlockSpanStates` at dispatch/callback boundary for immediate UX correctness.

## 11. Toolbar and Selection

### Selection source
- Read selection from `TextFieldState.selection` on demand.
- Do not add active text selection into `EditorState`.

### Mixed-style status
Required enum:

```kotlin
public enum class StyleStatus { FullyActive, Partial, Absent }
```

### UI strategy
- Internal default toolbar: panel above keyboard (V1).
- External toolbar: use public actions and optional callback for active-style state.

## 12. Serialization

Canonical format: JSON.

Requirements:
- Lossless round-trip for all built-in and custom span styles.
- Explicit version field in serialized schema for forward compatibility.
- Preserve unknown custom styles where possible.

Optional:
- Markdown export/import adapter (best-effort, lossy for some styles).

## 13. Test Matrix (Mandatory)

### Unit tests: span algorithms
- Insert/delete/replace offset adjustments.
- Overlap normalization.
- Toggle behavior on fully covered/partially covered selections.
- Bounds clamping and invalid span pruning.
- Split and merge transfer correctness.
- Continuation styles behavior.

### Integration tests: editor flows
- Enter split with spans before/at/after cursor.
- Backspace merge transfers spans and keeps cursor stable.
- Forward delete merge path.
- Programmatic text updates keep spans coherent.
- Selection mixed-state queries.

### Edge-case tests
- Sentinel index offset correctness.
- IME composition scenarios (where feasible).
- Large document with many spans (performance regression guard).

## 14. Performance Budgets and Guardrails

- No per-keystroke full-document span scan.
- Only mutate and normalize spans for the focused/edited block.
- Keep dispatch/reducer path free of heavy allocations.
- No high-frequency selection updates in `EditorState`.
- Avoid unnecessary recomposition by keeping span-derived UI state block-local.

## 15. Phased Delivery Plan

1. Data model and serialization schema scaffolding.
2. `BlockSpanStates` holder and normalization algorithms with unit tests.
3. `OutputTransformation` rendering in text field path.
4. Input transformation for user-edit offset maintenance.
5. Split/merge/programmatic edit integration with runtime span transfers.
6. Actions + snapshot synchronization into `EditorState`.
7. Toolbar (internal panel) + style status queries.
8. Performance validation and regression tests.

## 16. Agent Checklist Before Merging

- Confirm reducer purity is preserved.
- Confirm `TextRange` is used for span actions and APIs.
- Confirm no span includes sentinel coordinates.
- Confirm programmatic split/merge paths update `BlockSpanStates`.
- Confirm code blocks do not expose formatting controls in V1.
- Confirm all mandatory tests are present and passing.
- Confirm serialization round-trip tests for custom payloads.

## 17. Risks and Mitigations

- Risk: off-by-one bugs from coordinate conversions.
  - Mitigation: central conversion helpers + exhaustive tests.

- Risk: IME composition regressions.
  - Mitigation: composition-aware normalization policy.

- Risk: divergence between runtime spans and snapshot spans.
  - Mitigation: explicit sync points and invariant checks in debug builds.

- Risk: performance degradation under dense span usage.
  - Mitigation: block-local operations, capped normalization complexity, perf tests.
