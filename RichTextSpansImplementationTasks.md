# Rich Text Spans V1 - Decomposed Implementation Tasks

This task list is derived from `cassist/RichTextSpans.md`, read it for better understanding.
Tasks are ordered by implementation sequence and are scoped for one-shot delivery.

## Task 1. Extend Domain Model for Spans (Compile-Safe Foundation) — DONE

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
- ~~Use `kotlinx.serialization.json.JsonElement?` for `Custom.payload`; do not use `Map<String, Any?>`.~~
  **Decision**: Used `String?` (opaque JSON) instead to keep core layer free of serialization dependencies. Core must not parse/inspect the payload. Serialization layer (Task 2) owns `String` <-> `JsonElement` conversion and must canonicalize (parse then re-encode to normalized JSON) at the persistence boundary to avoid equality/toggle bugs from formatting differences.
- Preserve explicit API mode (`public`/`internal` declarations).
- Do not alter runtime editor behavior in this task.

`Done when`:
- Project compiles.
- Existing tests compile without behavior regressions.

`Completed`: All files created/modified. `TextSpan` validates `start >= 0` and `end >= start`. All existing call sites use default `spans = emptyList()`. No runtime behavior changes. Pending build verification.

## Task 2. Define Span Serialization Contract (JSON Canonical) — DONE

`Objective`: Lock persistence shape for future save/load and guarantee round-trip safety.

`Primary files`:
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/serialization/RichTextSchema.kt`
- New tests: `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/RichTextSchemaTest.kt`

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

`Completed`: Added `kotlinx-serialization-json` dependency (runtime library only, no compiler plugin). `RichTextSchema` uses manual `buildJsonObject`/`buildJsonArray` API — core domain types stay annotation-free. Schema version 1 with forward-compatible version switch. Decode normalizes: clamps out-of-bounds coordinates, drops empty spans, drops unknown style types gracefully. Custom payload canonicalization: embedded as structured JSON on encode, serialized to canonical string on decode. 27 test cases covering round-trips for all style types, normalization edge cases, version handling, and malformed data resilience. Pending build verification.

## Task 3. Implement Core Span Algorithms (Pure, Tested Utilities) — DONE

`Objective`: Implement all range math in pure functions before UI/runtime integration.

`Primary files`:
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanAlgorithms.kt`
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/StyleStatus.kt` (enum in same file as algorithms)
- New tests: `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/SpanAlgorithmsTest.kt`

`Implementation`:
- Add normalization (`sort`, `clamp`, `drop empty`, merge same-style overlaps).
- Ensure different-style overlaps (e.g. Bold + Italic) are preserved (cumulative application).
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

`Completed`: `SpanAlgorithms` internal object with 9 pure functions:
- **normalize**: clamp, filter empty, merge same-style overlaps/adjacents, sort output.
- **adjustForEdit**: models any edit as `replace [editStart, editStart+deletedLen) with insertedLen chars`. Start uses "after" bias (insertions at span start push right → new char NOT styled). End uses "before" bias (insertions at span end don't extend). Collapsed spans dropped.
- **splitAt**: clips crossing spans, shifts second block to zero-based coordinates.
- **mergeSpans**: shifts second block spans, then merges adjacent same-style at boundary.
- **applyStyle**: adds span + normalizes (auto-merges with existing same-style).
- **removeStyle**: clips/splits matching spans around removal range; other styles untouched.
- **toggleStyle**: FullyActive → remove, else → apply.
- **queryStyleStatus**: collapsed cursor checks containment; ranged selection computes union coverage.
- **activeStylesAt**: returns all styles at a position.
`StyleStatus` is a `public` enum (needed by Task 10 toolbar UI). Algorithms object is `internal`.
62 test cases covering normalization, insertions, deletions, replacements, split/merge, apply/remove/toggle, style queries (collapsed + ranged), and composite integration scenarios. Pending build verification.

## Task 4. Add `BlockSpanStates` Runtime Holder — DONE

`Objective`: Create live mutable span state holder parallel to `BlockTextStates`.

`Primary files`:
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockSpanStates.kt`
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/LocalBlockSpanStates.kt`

`Implementation`:
- Implement per-block span storage and lifecycle APIs: `getOrCreate(..., textLength)`, `get`, `set(..., textLength)`, `remove`, `cleanup`.
- Implement transfer/update APIs: `mergeInto`, `split`, `applyStyle`, `removeStyle`, `adjustForUserEdit`.
- Implement pending continuation style APIs.
- Integrate pure algorithm utilities from Task 3.

`Restrictions and considerations`:
- Keep runtime source of truth for editing in this holder; do not store live mutable span state in `EditorState`.
- Maintain internal invariants after every mutation.
- Keep methods thread-safe with Compose snapshot expectations (same pattern as `BlockTextStates`).

`Done when`:
- Holder unit tests confirm invariant preservation and transfer correctness.

`Completed`: `@Stable` class using `MutableState<List<TextSpan>>` per block for Compose snapshot reactivity. Full API surface: lifecycle (`getOrCreate(..., textLength)` / `get` / `getSpans` / `set(..., textLength)` / `remove` / `cleanup` / `clear`), edit adjustment (`adjustForUserEdit`), transfer (`split`/`mergeInto`), style ops (`applyStyle`/`removeStyle`/`toggleStyle`), queries (`queryStyleStatus`/`activeStylesAt`), and pending styles (`get`/`set`/`clear`/`resolveStylesForInsertion`). Invariant enforcement is strict at API ingress: `getOrCreate` and `set` now normalize+clamp spans against current visible `textLength`. Defensive copies are applied for incoming span/pending-style collections. Pending styles use snapshot-aware state map semantics, and `cleanup` prunes pending-only stale IDs (not just IDs with span state). `split` reuses existing target state instance instead of replacing map entry identity. `resolveStylesForInsertion` uses `position - 1` fallback for natural style continuation. `LocalBlockSpanStates` CompositionLocal follows `LocalBlockTextStates` pattern. Tests expanded to cover aliasing defenses, strict normalization/clamping, stale pending cleanup, and split target-state identity preservation. Pending build verification.

## Task 5. Wire Span Holder Lifecycle Into Editor Composition — DONE

`Objective`: Make `BlockSpanStates` available where text fields render and ensure cleanup policy matches blocks list lifecycle.

`Primary files`:
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/CascadeEditor.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/LocalBlockTextStates.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockRenderer.kt`

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

`Completed`: `BlockSpanStates` is now created and remembered in `CascadeEditor`, cleaned up in the existing `LaunchedEffect(state.blocks)` alongside `BlockTextStates`, and provided via `CompositionLocalProvider` with `LocalBlockSpanStates`. Cleanup is now scoped to active text blocks only (`collectTextBlockIds`), so stale span runtime state is dropped when a block transitions to non-text while keeping the same `blockId`. `TextBlockRenderer` initializes per-block span state via `blockSpanStates.getOrCreate(block.id, textContent.spans, textContent.text.length)`. Lifecycle integration coverage added in `SpanLifecycleIntegrationTest` (text-id collection, non-text transition cleanup, same-id re-initialization from snapshot spans). All changes are recomposition-neutral — no high-frequency snapshot reads and no new effects. Pending build verification.

## Task 6. Add Span Rendering Through `OutputTransformation` — DONE

`Objective`: Visually render spans without changing raw text storage.

`Primary files`:
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/BackspaceAwareTextEdit.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockRenderer.kt`
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanMapper.kt`

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

`Completed`: Added `SpanMapper` (`editor/.../richtext/SpanMapper.kt`) as the domain-to-Compose styling bridge plus `OutputTransformation` builder. `BackspaceAwareTextField` now accepts optional `outputTransformation` and forwards it to `BasicTextField` while preserving existing sentinel guard behavior. `TextBlockRenderer` now observes per-block span `State<List<TextSpan>>`, builds a memoized `OutputTransformation` from current runtime spans via `SpanMapper`, and passes it into `BackspaceAwareTextField`. Rendering path defensively clamps every span to current visible text length on every transform pass and skips invalid/empty ranges (and non-renderable custom styles), so bad data cannot crash draw. Runtime model remains non-lossy: unsupported/custom span payloads are retained in state/snapshots even if not visually decorated. Pending build verification.

## Task 7. User-Edit Span Maintenance (Committed-Text Observer) — DONE

`Objective`: Keep spans coherent during typing, deletion, and plain-text paste.

`Primary files`:
- New: `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanMaintenanceTextObserver.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockRenderer.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockSpanStates.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanMapper.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/BackspaceAwareTextEdit.kt`

`Implementation`:
- Keep `sentinelGuard` as the only `InputTransformation` (no external-state mutation during input transform).
- Observe committed visible text with `snapshotFlow { textFieldState.visibleText() }`.
- Diff previous/current visible text per block to compute one replace edit (`start`, `deletedLength`, `insertedLength`).
- Route diff results into `BlockSpanStates.adjustForUserEdit`.
- Apply pending/continuation style policy for inserted ranges (including override via pending styles).
- Ensure plain-text paste inherits active/pending style policy from cursor context.
- Keep `OutputTransformation` instance stable per block while reading latest runtime spans each pass (to avoid composition/IME instability from transformation identity churn).
- **Verification**: Verify if internal clipboard operations (Rich Text -> Copy -> Paste) preserve styles automatically via serialization.

`Restrictions and considerations`:
- Never allow span coordinates to include sentinel index.
- Be composition-aware; avoid destructive normalization while IME composition is active.
- Keep per-edit work O(k) for current block.

`Done when`:
- Typing/delete/paste preserves expected style ranges with no cursor jumps or crashes.

`Completed`: Implemented `SpanMaintenanceTextObserver` (new `richtext` utility) as the Task 7 integration point. `TextBlockRenderer` now feeds committed visible text changes into this observer via `snapshotFlow`, and the observer performs block-local diffing, calls `BlockSpanStates.adjustForUserEdit`, and applies pending/continuation style updates for inserted ranges. `BackspaceAwareTextField` remains sentinel-only on input transformation path (no chained span-maintenance transform), avoiding the runtime typing corruption seen when external mutable state was touched during input transformation. Additionally, span rendering was stabilized by using a stable per-block `OutputTransformation` instance that reads current span state on each transform pass through `SpanMapper.applyStyles(...)`; this removed transformation-identity churn during typing. Pending build verification.

## Task 8. Programmatic Edit Sync: Split/Merge/SetText Paths — DONE

`Objective`: Ensure span updates happen for non-user edits and are **not** double-processed by the committed-text observer.

`Primary files`:
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/registry/BlockRenderer.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockTextStates.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/BlockSpanStates.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/CascadeEditor.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockRenderer.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanMaintenanceTextObserver.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/action/EditorAction.kt`

`Implementation`:
- Introduce explicit programmatic-edit origin signaling (per block) so observer path can consume and ignore programmatic commits instead of running user-edit span maintenance.
- Define deterministic split ID handoff for runtime transfer; preferred approach is generating `newBlockId` at callback boundary and passing it into `SplitBlock` so text + span split target IDs are identical.
- If split ID cannot be passed explicitly, define a post-dispatch retrieval contract for the new block ID and cover it with tests.
- Update `DefaultBlockCallbacks.onEnter` to compute split text as today, split runtime spans with known `newBlockId` at `cursorPosition` before source truncation, then apply source `setText` and dispatch split using the same target ID contract.
- Update `onBackspaceAtStart` and forward-delete merge flows to capture `targetTextLength` before text merge, merge runtime span sets exactly once (`source -> target`) with proper shift, and suppress observer handling for the programmatic merge commit.
- Ensure any direct `setText(...)` programmatic mutation path has explicit policy (explicit runtime span transform or explicit reset to empty spans).
- Never rely on user-edit observer heuristics for programmatic edits.

`Restrictions and considerations`:
- Reducers remain pure; runtime state mutations belong in callback/integration boundary.
- Preserve focus and cursor behavior already implemented for text.
- Keep block ID lifecycle consistent when source block is removed.
- Prevent double-adjust/double-shift bugs caused by observer + callback both mutating spans.
- Prevent pending-style bleed into merged text.

`Done when`:
- Split/merge operations transfer spans exactly per spec, including crossing-span clip behavior.
- Programmatic split/merge/setText paths are observer-safe (no duplicate span transforms).
- No style loss or style over-application in split/merge manual scenarios.

`Completed`: Added explicit programmatic-edit signaling to `BlockTextStates` via per-block expected commit text tracking (`consumeProgrammaticCommit`), automatically populated by `setText(...)` and `mergeInto(...)`. `SpanMaintenanceTextObserver` now consumes this signal before diffing committed text: exact programmatic commits are ignored, while coalesced commits are rebased to the programmatic baseline so only residual user edits are applied. `DefaultBlockCallbacks` now performs runtime span transfer explicitly for non-user edits: `onEnter` generates `newBlockId` at callback boundary, splits runtime spans with that ID before source truncation, then dispatches `SplitBlock` with the same `newBlockId`; backspace/delete merges use pre-merge target length from `BlockTextStates.mergeInto(...)` and call `BlockSpanStates.mergeInto(...)` exactly once. `SplitBlock` reducer now accepts optional `newBlockId` for deterministic runtime/reducer ID handoff. `BlockSpanStates.mergeInto(...)` clears pending styles on both source and target to prevent pending-style bleed across merge boundaries. Added tests: observer programmatic exact-skip + rebase (`SpanMaintenanceTextObserverTest`), deterministic split ID reducer behavior (`EditorStateTest`), and target pending-style cleanup on merge (`BlockSpanStatesTest`). Pending build verification.

## Task 9. Add Public Span Actions and Snapshot Synchronization — DONE

`Objective`: Provide external API for formatting while keeping immutable state snapshots aligned.

`Primary files`:
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/action/EditorAction.kt`
- Optional helper: new `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanActionDispatcher.kt`
- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/richtext/SpanAlgorithms.kt`

`Implementation`:
- Add `ApplySpanStyle` and `RemoveSpanStyle` actions with `TextRange`.
- Implement reducer-side updates to `BlockContent.Text.spans` as immutable snapshot sync only.
- Add integration path so runtime `BlockSpanStates` and snapshot state stay consistent after action dispatch.
- Ensure reducer-side span snapshots are canonicalized with the same normalization contract as runtime (`clamp`, drop empty, merge same-style overlaps, deterministic ordering).
- Update existing text mutation actions (`SplitBlock`, `UpdateBlockText`, and any replacement actions) so snapshot spans are preserved or transformed intentionally, never silently dropped.
- **Critical**: Ensure snapshot synchronization happens **before** any potential Undo stack capture to prevent state drift.

`Restrictions and considerations`:
- No side effects inside reducers.
- Keep dispatch path deterministic and easy to test.
- Preserve compatibility with future undo/redo snapshot capture.
- Avoid runtime/snapshot drift by defining one canonical conversion direction per mutation path.

`Done when`:
- External code can call `SpanActionDispatcher.applyStyle/removeStyle/toggleStyle` and see immediate UI + snapshot consistency.
- Snapshot span state remains canonical and stable across repeated action/reducer cycles.

`Completed`: Added `ApplySpanStyle` and `RemoveSpanStyle` as public `EditorAction` data classes with pure reducers that operate on `BlockContent.Text.spans` via `SpanAlgorithms`. `ApplySpanStyle` reducer delegates to `SpanAlgorithms.applyStyle` (output is already canonical — normalized, clamped, merged). `RemoveSpanStyle` reducer delegates to `SpanAlgorithms.removeStyle` followed by explicit `SpanAlgorithms.normalize` to ensure canonical output. Both actions carry "snapshot-only" KDoc warnings directing interactive use to `SpanActionDispatcher`. Fixed existing text mutation reducers: `SplitBlock` now splits snapshot spans at `clampedPosition` via `SpanAlgorithms.splitAt`, always updates source block snapshot, and accepts runtime payload overrides (`newBlockSpans`, `sourceBlockText`, `sourceBlockSpans`) for deterministic runtime/snapshot alignment. `MergeBlocks` now merges snapshot spans via `SpanAlgorithms.mergeSpans` with correct `firstTextLength` shift. `UpdateBlockText` documented with explicit "span policy: reset". `DefaultBlockCallbacks` updated: `onEnter` passes runtime split payload to `SplitBlock`; `onBackspaceAtStart`/`onDeleteAtEnd` sync merged text+spans to snapshot via `UpdateBlockContent` before `DeleteBlock` dispatch — fixes dead-code `MergeBlocks` snapshot path. `SpanActionDispatcher` syncs snapshot via `UpdateBlockContent` (carries runtime text+spans) instead of `ApplySpanStyle`/`RemoveSpanStyle` — avoids stale-text-length mismatch. Collapsed-cursor `toggleStyle` now toggles pending styles using insertion-continuation semantics (`position - 1`) instead of being a no-op/boundary-inconsistent. Tests: 24 tests in `EditorStateTest` (added runtime source payload split test), 17 tests in `SpanActionDispatcherTest` (updated for `UpdateBlockContent` dispatch, added collapsed-cursor pending style coverage including span-end boundary). Pending build verification.

## Task 10. Implement Selection Status and Toolbar Contracts

`Objective`: Expose style state for internal/external formatting UIs without EditorState churn.
Decomposition is in separate file: Task10_ToolbarAndSelectionStatus.md

## Task 11. Complete Test Matrix and Performance Validation

`Objective`: Lock correctness and prevent regressions for rich text behavior.

`Primary files`:
- New tests in `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/`

`Implementation`:
- Add algorithm unit tests: normalization, offsets, style status, continuation, split/merge transfer.
- Add specific test case for overlapping different styles (e.g. `[0,5) Bold` + `[3,8) Italic`) to ensure they apply cumulatively.
- Add integration tests for enter split, backspace merge, forward-delete merge, action dispatch flows.
- Add sentinel-specific tests for coordinate conversions.
- Add composition-sensitive tests where feasible.
- Add scale-oriented tests for many blocks/spans to catch obvious performance regressions.
- Add observer-origin tests verifying programmatic commits marked as programmatic are ignored by user-edit observer path while user commits still apply normal diff + continuation behavior.
- Add split-ID handoff regression tests to ensure runtime span split and reducer split target the same block ID.
- Add serialization hardening tests for malformed schema shapes where non-object span entries and non-object `style` entries are dropped (not fatal).
- Add rendering mapping tests for overlapping underline + strikethrough so cumulative decoration behavior is verified.

`Restrictions and considerations`:
- Tests must verify visible coordinate invariants.
- Keep tests deterministic and platform-agnostic in `commonTest` where possible.
- Do not rely on UI screenshot assertions for core correctness.

`Done when`:
- `./gradlew :editor:allTests` passes with new coverage.
- No regressions in existing editor tests.
- New regression tests explicitly cover programmatic edit sync and malformed decode resilience.

`Completed`: Extended the matrix with targeted regression coverage and scale guards. Added forward-delete merge integration test (`FormattingIntegrationTest`) to complement existing enter-split and backspace-merge paths. Added split-ID handoff assertion at callback/runtime boundary (`EnterContinuationTest`) to guarantee runtime split target and dispatched `SplitBlock.newBlockId` stay aligned. Added sentinel conversion tests for `visibleText()` and `visibleCursorPosition()` (`VisibleSelectionTest`). Added scale-oriented regression suite (`RichTextScaleRegressionTest`) covering large-span normalization invariants, large-block cleanup correctness, and block-local style mutation behavior. Hardened serialization decode path to safely drop malformed-but-parseable entries (non-object span items, non-object `style`, non-array `spans`) and added explicit tests in `RichTextSchemaTest`. Implemented cumulative decoration overlay in `SpanMapper` for underline+strikethrough intersections and added mapping tests in `SpanMapperTest` to verify combined decoration behavior. Added task-level validation report/checklist in `Task11_TestMatrixAndPerformanceValidation.md`. Pending user-run verification (`:editor:allTests`).

## Task 12. Final Hardening

`Objective`: Ensure implementation matches spec constraints and closes known correctness gaps.

`Primary files`:
- `cassist/RichTextSpans.md`
- `ARCHITECTURE.md`
- Code touched by Tasks 1-11

`Implementation`:
- Resolve known rendering limitation for cumulative text decorations so overlapping `Underline` + `StrikeThrough` can render together (not last-writer wins).
- Harden `RichTextSchema.decode` against malformed but parseable JSON so invalid span/style entries are dropped safely without aborting whole decode.
- Audit deterministic span canonicalization in both runtime and snapshot paths; sorting must be stable and deterministic for equal ranges across style combinations.
- Audit all text mutation actions/callbacks so span policy is explicit: preserve, transform, or reset (never implicit drop).
- Add short architecture notes for observer-origin guard and split ID handoff to prevent future regressions.

`Restrictions and considerations`:
- Preserve reducer purity and editor responsiveness.
- Do not introduce whole-document scans for per-block mutations.
- Keep backward compatibility for serialized version 1 documents.

`Done when`:
- Spec checklist in `cassist/RichTextSpans.md` is satisfied with no unresolved TODOs for V1 scope.
- No known rich-text correctness gaps remain in split/merge/programmatic edit flows.
- Final full test run is green (to be executed by user) and manual smoke scenarios show no style loss/drift.
