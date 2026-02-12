# Rich Text Spans V1 - Decomposed Implementation Tasks

This task list is derived from `cassist/RichTextSpans.md`.
Tasks are ordered by implementation sequence and are scoped for one-shot delivery.

## Task 1. Extend Domain Model for Spans (Compile-Safe Foundation)

`Objective`: Introduce rich text span types into the core model without changing runtime behavior yet.

`Primary files`:
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/core/BlockContent.kt`
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/core/TextSpan.kt`
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/core/SpanStyle.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/core/Block.kt`

`Implementation`:
- Add `TextSpan(start, end, style)` with visible-coordinate half-open range semantics.
- Add sealed `SpanStyle` with `Bold`, `Italic`, `Underline`, `StrikeThrough`, `InlineCode`, `Highlight(colorArgb)`, `Link(url)`, and `Custom(typeId, payload)`.
- Add `spans: List<TextSpan> = emptyList()` to `BlockContent.Text`.
- Keep all existing block factory functions source-compatible by relying on default `spans`.

`Restrictions and considerations`:
- Use `kotlinx.serialization.json.JsonElement?` for `Custom.payload`; do not use `Map<String, Any?>`.
- Preserve explicit API mode (`public`/`internal` declarations).
- Do not alter runtime editor behavior in this task.

`Done when`:
- Project compiles.
- Existing tests compile without behavior regressions.

## Task 2. Define Span Serialization Contract (JSON Canonical)

`Objective`: Lock persistence shape for future save/load and guarantee round-trip safety.

`Primary files`:
- New: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/serialization/RichTextSchema.kt`
- New tests: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/RichTextSchemaTest.kt`

`Implementation`:
- Define a schema with explicit version field.
- Add encode/decode helpers for `BlockContent.Text` with spans.
- Ensure unknown `Custom` payloads can round-trip unchanged.

`Restrictions and considerations`:
- JSON is canonical; do not add Markdown parser/export in this task.
- Keep schema forward-compatible via version switch.
- Keep API independent from UI/runtime holders.

`Done when`:
- Serialization tests verify lossless round-trip for built-in and custom styles.
- Invalid/out-of-bounds span data from input is rejected or normalized per defined rules.

## Task 3. Implement Core Span Algorithms (Pure, Tested Utilities)

`Objective`: Implement all range math in pure functions before UI/runtime integration.

`Primary files`:
- New: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanAlgorithms.kt`
- New tests: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/SpanAlgorithmsTest.kt`

`Implementation`:
- Add normalization (`sort`, `clamp`, `drop empty`, merge same-style overlaps).
- Add edit adjustment helpers for insert/delete/replace in visible coordinates.
- Add split and merge transfer helpers.
- Add style toggle/apply/remove helpers.
- Add query helper for style status: `FullyActive`, `Partial`, `Absent`.

`Restrictions and considerations`:
- All APIs use `TextRange` semantics (`[start, end)`).
- Keep algorithm complexity block-local; avoid whole-document scans.
- No sentinel-aware math here; this layer assumes visible coordinates only.

`Done when`:
- Unit tests cover edge cases: overlaps, clipping, exact-boundary edits, full-delete collapse, and partial-style ranges.

## Task 4. Add `BlockSpanStates` Runtime Holder

`Objective`: Create live mutable span state holder parallel to `BlockTextStates`.

`Primary files`:
- New: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockSpanStates.kt`
- New: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/LocalBlockSpanStates.kt`

`Implementation`:
- Implement per-block span storage and lifecycle APIs: `getOrCreate`, `get`, `set`, `remove`, `cleanup`.
- Implement transfer/update APIs: `mergeInto`, `split`, `applyStyle`, `removeStyle`, `adjustForUserEdit`.
- Implement pending continuation style APIs.
- Integrate pure algorithm utilities from Task 3.

`Restrictions and considerations`:
- Keep runtime source of truth for editing in this holder; do not store live mutable span state in `EditorState`.
- Maintain internal invariants after every mutation.
- Keep methods thread-safe with Compose snapshot expectations (same pattern as `BlockTextStates`).

`Done when`:
- Holder unit tests confirm invariant preservation and transfer correctness.

## Task 5. Wire Span Holder Lifecycle Into Editor Composition

`Objective`: Make `BlockSpanStates` available where text fields render and ensure cleanup policy matches blocks list lifecycle.

`Primary files`:
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/CascadeEditor.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/LocalBlockTextStates.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockRenderer.kt`

`Implementation`:
- Create and remember `BlockSpanStates` in `CascadeEditor`.
- Provide it through a new `CompositionLocal`.
- Initialize per-block spans from `BlockContent.Text.spans` when creating runtime state.
- Cleanup stale span states alongside text state cleanup.

`Restrictions and considerations`:
- Do not cause per-frame recompositions from lifecycle wiring.
- Do not add high-frequency selection/cursor data to `EditorState`.

`Done when`:
- Spans are available in renderers for all text-supporting blocks.
- No regressions in current text editing and drag/drop behavior.

## Task 6. Add Span Rendering Through `OutputTransformation`

`Objective`: Visually render spans without changing raw text storage.

`Primary files`:
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/BackspaceAwareTextEdit.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockRenderer.kt`
- New: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanMapper.kt`

`Implementation`:
- Extend `BackspaceAwareTextField` to accept optional `outputTransformation`.
- Build per-block `OutputTransformation` from runtime spans and map styles via `SpanMapper`.
- Clamp spans to current visible text length before calling `addStyle`.
- Skip style toolbar controls for `BlockType.Code` in future UI hooks, but keep rendering logic non-lossy.

`Restrictions and considerations`:
- Keep transformation creation stable and recompute only when relevant span data changes.
- Do not switch to `AnnotatedString` value-based field architecture.
- Handle invalid spans defensively (skip, never crash render path).

`Done when`:
- Hardcoded span data renders correctly in focused/unfocused text fields across text block types.

## Task 7. User-Edit Span Maintenance via `InputTransformation`

`Objective`: Keep spans coherent during typing, deletion, and plain-text paste.

`Primary files`:
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/BackspaceAwareTextEdit.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockRenderer.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockSpanStates.kt`

`Implementation`:
- Chain span-maintenance `InputTransformation` with sentinel guard using `then(...)`.
- Convert edit change ranges from text-field buffer coordinates to visible coordinates (exclude ZWSP).
- Route changes into `BlockSpanStates.adjustForUserEdit`.
- Apply pending styles for collapsed-cursor typing.
- Ensure plain-text paste inherits active/pending style policy from cursor context.

`Restrictions and considerations`:
- Never allow span coordinates to include sentinel index.
- Be composition-aware; avoid destructive normalization while IME composition is active.
- Keep per-edit work O(k) for current block.

`Done when`:
- Typing/delete/paste preserves expected style ranges with no cursor jumps or crashes.

## Task 8. Programmatic Edit Sync: Split/Merge/SetText Paths

`Objective`: Ensure span updates happen for non-user edits that bypass input transformations.

`Primary files`:
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/registry/BlockRenderer.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockTextStates.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockSpanStates.kt`

`Implementation`:
- Update `DefaultBlockCallbacks.onEnter` split flow to split spans in runtime holder.
- Update `onBackspaceAtStart` merge flow to merge span sets with proper shift.
- Update forward-delete merge flow similarly.
- Ensure any `setText`-style programmatic mutation path has explicit span sync behavior.

`Restrictions and considerations`:
- Reducers remain pure; runtime state mutations belong in callback/integration boundary.
- Preserve focus and cursor behavior already implemented for text.
- Keep block ID lifecycle consistent when source block is removed.

`Done when`:
- Split/merge operations transfer spans exactly per spec, including crossing-span clip behavior.

## Task 9. Add Public Span Actions and Snapshot Synchronization

`Objective`: Provide external API for formatting while keeping immutable state snapshots aligned.

`Primary files`:
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/action/EditorAction.kt`
- Optional helper: new `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanActionDispatcher.kt`

`Implementation`:
- Add `ApplySpanStyle` and `RemoveSpanStyle` actions with `TextRange`.
- Implement reducer-side updates to `BlockContent.Text.spans` as immutable snapshot sync only.
- Add integration path so runtime `BlockSpanStates` and snapshot state stay consistent after action dispatch.

`Restrictions and considerations`:
- No side effects inside reducers.
- Keep dispatch path deterministic and easy to test.
- Preserve compatibility with future undo/redo snapshot capture.

`Done when`:
- External code can call `dispatch(ApplySpanStyle/RemoveSpanStyle)` and see immediate UI + state snapshot consistency.

## Task 10. Implement Selection Status and Toolbar Contracts

`Objective`: Expose style state for internal/external formatting UIs without EditorState churn.

`Primary files`:
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/CascadeEditor.kt`
- New optional UI: `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/RichTextToolbar.kt`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockSpanStates.kt`

`Implementation`:
- Add `StyleStatus` query usage from focused block selection (`TextFieldState.selection`).
- Add optional `CascadeEditor` params:
- `enableRichTextToolbar: Boolean = false`
- `onActiveStylesChanged: ((Set<SpanStyle>) -> Unit)? = null`
- Implement internal toolbar as keyboard-adjacent panel (V1 default behavior).
- Disable formatting controls in UI when focused block type is `Code`.

`Restrictions and considerations`:
- Do not store high-frequency text selection in `EditorState`.
- Keep toolbar observation block-local and reactive.
- External callback should be stable and not spam redundant updates.

`Done when`:
- Internal toolbar toggles styles and reflects `FullyActive`/`Partial`/`Absent`.
- External callback provides usable active-style state for custom UIs.

## Task 11. Complete Test Matrix and Performance Validation

`Objective`: Lock correctness and prevent regressions for rich text behavior.

`Primary files`:
- New tests in `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/`

`Implementation`:
- Add algorithm unit tests: normalization, offsets, style status, continuation, split/merge transfer.
- Add integration tests for enter split, backspace merge, forward-delete merge, action dispatch flows.
- Add sentinel-specific tests for coordinate conversions.
- Add composition-sensitive tests where feasible.
- Add scale-oriented tests for many blocks/spans to catch obvious performance regressions.

`Restrictions and considerations`:
- Tests must verify visible coordinate invariants.
- Keep tests deterministic and platform-agnostic in `commonTest` where possible.
- Do not rely on UI screenshot assertions for core correctness.

`Done when`:
- `./gradlew :editor:allTests` passes with new coverage.
- No regressions in existing editor tests.

## Task 12. Final Hardening and Agent Readiness Pass

`Objective`: Ensure implementation matches spec constraints and is safe for downstream agent-driven work.

`Primary files`:
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/cassist/RichTextSpans.md`
- `/Users/sergeydrymchenko/Projects/Android/CascadeEditor/ARCHITECTURE.md`
- Code touched by Tasks 1-11

`Implementation`:
- Run full checklist from spec section "Agent Checklist Before Merging".
- Verify API docs/comments for new public types and actions.
- Update architecture docs to include rich text runtime layer and action contracts.
- Record deferred items as explicit TODOs: floating toolbar, rich clipboard import, undo/redo integration.

`Restrictions and considerations`:
- No undocumented behavioral deltas.
- No unresolved ambiguity in action or coordinate semantics.
- Keep follow-up backlog explicit to avoid accidental scope creep in later tasks.

`Done when`:
- Checklist is satisfied and documented.
- Another engineer/agent can implement or review from docs alone without rediscovery work.
