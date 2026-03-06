# Task 11 Test Matrix and Performance Validation

## Objective
Lock correctness for rich text spans and add scale-focused guards that surface regressions before release.

## Automated Test Matrix

| Area | Requirement | Coverage |
|---|---|---|
| Algorithms | Normalization, offsets, style status, continuation, split/merge transfer | `SpanAlgorithmsTest`, `BlockSpanStatesTest`, `FormattingStateCalculatorTest` |
| Overlapping styles | `[0,5) Bold` + `[3,8) Italic` remain cumulative | `SpanAlgorithmsTest` (`normalize - preserves different-style overlaps`), `FormattingIntegrationTest` (`overlapping Bold and Italic...`) |
| Integration (enter split) | Enter split correctness, continuation, runtime/snapshot sync | `EnterContinuationTest`, `FormattingIntegrationTest` |
| Integration (backspace merge) | Backspace merge span transfer + formatting state continuity | `FormattingIntegrationTest` (`backspace merge updates runtime spans...`) |
| Integration (forward-delete merge) | Forward-delete merge span transfer + dispatch ordering | `FormattingIntegrationTest` (`forward delete merge updates runtime and snapshot...`) |
| Action dispatch flows | Apply/remove/toggle dispatch and runtime/snapshot consistency | `SpanActionDispatcherTest`, `DefaultFormattingActionsTest`, `FormattingIntegrationTest` |
| Sentinel coordinate invariants | visible selection/cursor/text conversions | `VisibleSelectionTest` |
| Observer-origin guard | Programmatic commits ignored, user delta still applied | `SpanMaintenanceTextObserverTest` |
| Split ID handoff | Runtime split target ID matches dispatched `SplitBlock.newBlockId` | `EnterContinuationTest` (`split id handoff keeps runtime target...`) |
| Serialization hardening | Malformed span/style shapes dropped safely | `RichTextSchemaTest` (non-object span entries, non-object style entries, non-array spans field) |
| Rendering mapping | Underline + strikethrough overlap produces cumulative decoration | `SpanMapperTest` (`...combined decoration run`) |
| Scale regressions | Many spans/blocks operations preserve invariants and block-local behavior | `RichTextScaleRegressionTest` |

## Performance Validation Protocol

### 1. Test Execution (to run locally)
1. `./gradlew :editor:allTests`
2. `./gradlew :editor:testDebugUnitTest --tests "io.github.linreal.cascade.editor.RichTextScaleRegressionTest"`

### 2. Manual Scale Scenarios
1. Create or load a document with ~1000 text blocks.
2. Apply mixed formatting spans (Bold/Italic/Underline/StrikeThrough) across long selections in multiple blocks.
3. Repeatedly execute:
   - Enter split at middle/end of formatted blocks
   - Backspace merge at block start
   - Forward-delete merge at block end
   - Toggle styles at collapsed cursor near style boundaries
4. Paste large plain-text chunks into formatted regions and confirm continuation behavior.
5. Verify no dropped spans, no duplicated shifts, no cursor jumps, and no visible typing lag spikes.

### 3. Exit Criteria
1. All automated tests pass.
2. No crashes or style drift in manual scale scenarios.
3. No observable quadratic behavior in split/merge/edit operations at block-local scope.
