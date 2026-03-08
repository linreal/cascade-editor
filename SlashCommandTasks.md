# Slash Command System — Technical Task Breakdown

This document decomposes [SlashCommandSpec.md](SlashCommandSpec.md) into an ordered set of implementation tasks.

Recommended package for new slash-specific code:

- `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/slash/`
- `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/`

## Task 1 — Enrich Slash Session State and Reducer API ✅

### Goal

Replace the current minimal slash state with a session model that can represent inline query ranges, submenu navigation, and selection state.

### Scope

- update `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/state/EditorState.kt`
- update `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/action/EditorAction.kt`
- update any call sites that construct slash actions, currently `DefaultBlockCallbacks.onSlashCommand(...)`

### Technical Work

- Introduce `SlashQueryRange(start, endExclusive)` in visible-text coordinates.
- Expand `SlashCommandState` to include:
  - `anchorBlockId`
  - `query`
  - `queryRange`
  - `navigationPath: List<SlashCommandId>`
  - `highlightedCommandId: SlashCommandId?`
- Replace the current `OpenSlashCommand(anchorBlockId, initialQuery)` action with one that also accepts `queryRange`.
- Replace `UpdateSlashCommandQuery(query)` with an action that can update both `query` and `queryRange` together. Recommended name: `UpdateSlashCommandSession`.
- Add reducer actions for:
  - submenu navigation
  - highlighted-item changes
  - close/reset
- Keep reducers pure. They should not attempt text mutation or command execution.

### Notes

- This task is state-only. Do not implement command execution here.
- Avoid storing cursor position in `EditorState`; that remains in `BlockTextStates`.

### Tests

Add reducer coverage in `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/EditorStateTest.kt` or a dedicated `SlashCommandStateTest.kt`:

- opening a slash session stores range and query
- updating the session changes both query and range atomically
- navigating into a submenu updates `navigationPath`
- closing clears the entire session
- highlight changes do not disturb query/range state

### Definition of Done

- `EditorState` can fully represent an active slash session
- reducer API matches the new spec
- all slash reducer tests pass
- no remaining code constructs the old `OpenSlashCommand` / `UpdateSlashCommandQuery` shapes

## Task 2 — Add Public Slash Command Model and Registry

### Goal

Create the stable public API for slash items and centralize search behavior in a dedicated registry.

### Scope

- add new files under `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/slash/`
- do not yet wire UI or execution

### Technical Work

- Add public model types:
  - `SlashCommandId`
  - `SlashCommandIconKey`
  - `SlashCommandGroup`
  - `SlashCommandItem`
  - `SlashCommandAction`
  - `SlashCommandMenu`
  - `SlashQueryTextPolicy`
  - `SlashCommandResult`
- Add execution context types required by `SlashCommandAction.onExecute`:
  - `SlashCommandContext` — data class holding `anchorBlockId`, `query`, `queryRange`, and `editor: SlashCommandEditor`
  - `SlashCommandEditor` — public interface declaring safe editor operations (`getAnchorBlock`, `getAnchorVisibleText`, `replaceQueryText`, `updateAnchorText`, `replaceAnchorBlock`, `insertBlockAfterAnchor`, `focusBlock`, `closeMenu`)
  - Only the interface is defined here. The internal implementation (`SlashCommandEditorHost`) is deferred to Task 5.
- Implement `SlashCommandRegistry` with:
  - `register(item)`
  - `getRootItems()`
  - `search(query, path)`
- Search requirements:
  - resolve the visible node set from `path`
  - rank matches deterministically
  - search `title`, `description`, and `keywords`
  - deduplicate by `SlashCommandId`
- Decide and document duplicate-registration behavior. Recommended: latest registration wins by id.
- Keep menu nodes searchable the same way as actions.
- Guard internal collections with synchronization if `register()` may be called from different coroutines.

### Notes

- Do not reuse `BlockRegistry.search()` as the primary API. Slash search rules now belong here.
- Keep the ranking algorithm in one place so built-in and custom commands remain consistent.
- `SlashCommandContext` and `SlashCommandEditor` must live in this task because `SlashCommandAction.onExecute` has receiver type `SlashCommandContext.() -> SlashCommandResult`. Without them, `SlashCommandAction` does not compile.

### Tests

Add `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/SlashCommandRegistryTest.kt`:

- root registration returns registered items in deterministic order
- duplicate ids replace previous items
- exact-title match ranks above prefix and substring matches
- keyword matches participate in ranking
- path-based search only searches the addressed submenu level
- menu items are discoverable in the same search path as actions

### Definition of Done

- public slash types compile in common code
- registry behavior is deterministic and unit-tested
- search/ranking logic no longer depends on `BlockDescriptor.matches()`

## Task 3 — Add Built-In Slash Metadata to Block Descriptors

### Goal

Make built-in slash entries an explicit opt-in on `BlockDescriptor` rather than auto-generating entries for every descriptor.

### Scope

- update `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/registry/BlockDescriptor.kt`
- update `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/registry/BlockRegistry.kt`
- update default built-in descriptors

### Technical Work

- Add `BuiltInSlashCommandSpec` to the new slash package.
- Add `BuiltInBlockSlashBehavior` to the new slash package.
- Add `slash: BuiltInSlashCommandSpec? = null` to `BlockDescriptor`.
- Remove stale KDoc drift around `category`; the source of grouping is now `slash.group`.
- The existing `BlockDescriptor.icon: String?` field remains as-is for general registry use. `slash.icon: SlashCommandIconKey?` is the slash-menu-specific icon override. When `slash.icon` is null, the built-in command factory (Task 4) should fall back to `BlockDescriptor.icon` wrapped in `SlashCommandIconKey`.
- Populate `slash` for built-in block types that should appear in the slash menu.
- Use `BuiltInBlockSlashBehavior` to encode replace-vs-insert policy.
- Leave descriptors without `slash` hidden from the slash menu.
- Keep `BlockRegistry.search()` intact for now if other callers still rely on it, but stop treating it as the slash system API.

### Notes

- `Divider` and `Image` should use `AlwaysInsert`.
- Text-capable defaults such as paragraph, heading, todo, list, quote may use `ReplaceAnchorWhenBlank`.
- `Code` should use `AlwaysInsert`. Although `Code` is text-capable, `BlockType.Code.isConvertible` is false, meaning in-place type conversion is not supported. Inserting a new code block below is consistent with this constraint.

### Tests

Extend `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/BlockRegistryTest.kt`:

- descriptors expose slash metadata when configured
- descriptors without slash metadata remain valid registry entries
- built-in defaults have the expected slash behavior per type

### Definition of Done

- block slash discoverability is explicit in descriptor metadata
- default descriptors compile with the new field
- tests cover representative built-in policies

## Task 4 — Generate Built-In Slash Items from Descriptor Metadata

### Goal

Bridge `BlockDescriptor.slash` into concrete `SlashCommandAction` instances without coupling arbitrary custom commands to block registration.

### Scope

- add a built-in adapter under the new slash package
- keep it separate from UI and execution host logic

### Technical Work

- Implement a generator, for example `BuiltInSlashCommandFactory`, that:
  - consumes registered `BlockDescriptor`s
  - filters descriptors with `slash != null`
  - emits `SlashCommandAction`s with stable ids
- Recommended id format: `builtin.block.<typeId>`
- Built-in action payload should carry enough information to later resolve:
  - target descriptor or `typeId`
  - `BuiltInBlockSlashBehavior`
- Keep display metadata sourced from the descriptor:
  - `displayName` -> `title`
  - `description`
  - `keywords`
  - `slash.group`
  - `slash.icon` (fall back to `BlockDescriptor.icon` wrapped in `SlashCommandIconKey` when null)
- The `onExecute` closure for built-in actions requires a `SlashCommandEditor` at call time, but the factory does not know the editor host at generation time. Recommended approach: the factory accepts a `builtInExecutor: suspend SlashCommandContext.(typeId: String, behavior: BuiltInBlockSlashBehavior) -> SlashCommandResult` lambda parameter that encapsulates built-in block semantics (blank-vs-non-blank resolution, replace-vs-insert). Each generated action's `onExecute` delegates to this lambda with its captured `typeId` and `behavior`. The lambda implementation is provided during wiring in Task 8, and can be unit-tested with a fake in Task 4.
- Keep generation pure and deterministic.

### Notes

- This task should not mutate editor state.
- It is acceptable to add an internal marker type or internal metadata object for built-in block actions if execution needs a typed payload later.
- The `builtInExecutor` lambda approach keeps the factory testable in isolation: tests can pass a recording lambda that captures `typeId`/`behavior` without needing a real editor host.

### Tests

Add `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/BuiltInSlashCommandFactoryTest.kt`:

- only descriptors with `slash != null` become slash items
- generated ids are stable
- metadata is copied correctly from the descriptor
- behavior is preserved for later execution

### Definition of Done

- built-in slash items can be generated from descriptors without UI involvement
- generation has direct unit coverage
- no code path assumes every descriptor automatically becomes a slash entry

## Task 5 — Add Query-Range Editing Primitives and Safe Slash Editor Host

### Goal

Provide the internal execution host that can safely mutate runtime text/spans and sync snapshot state without exposing raw reducer dispatch to slash commands.

### Scope

- extend `BlockTextStates`
- extend span-maintenance support if needed
- add internal `SlashCommandEditorHost` implementation of the `SlashCommandEditor` interface (defined in Task 2) under the slash package
- likely add helper functions near text/span utilities

### Technical Work

- Add visible-text range replacement primitives to `BlockTextStates`. Recommended API:
  - `replaceVisibleRange(blockId, start, endExclusive, replacement, cursorPositionAfter)`
  - return the new visible text or null if the block does not exist
- Ensure `pendingProgrammaticCommits` is updated for programmatic slash edits.
- Add the corresponding span update primitive. Options:
  - extend `BlockSpanStates` with a range-replacement helper
  - or add a dedicated internal utility that computes the edit and calls `adjustForUserEdit`
- Implement internal `SlashCommandEditorHost : SlashCommandEditor` with safe operations:
  - `getAnchorBlock`
  - `getAnchorVisibleText`
  - `replaceQueryText`
  - `updateAnchorText`
  - `replaceAnchorBlock`
  - `insertBlockAfterAnchor`
  - `focusBlock`
  - `closeMenu`
- Snapshot sync requirements:
  - text and spans must stay aligned after query removal
  - `replaceAnchorBlock(preserveAnchorId = true)` should use the current anchor id unless explicitly overridden
  - insertion/replacement should request focus through snapshot action plus runtime cursor update where applicable
- Add graceful no-op behavior if the anchor block no longer exists.

### Notes

- This task is the core correctness layer for slash execution.
- Do not let built-in or custom slash commands call `EditorAction` directly after this host exists.

### Tests

Add targeted tests such as:

- `BlockTextStatesTest.kt`
  - replacing a middle visible range keeps text outside the range intact
  - programmatic commit tracking works for range replacement
- `SlashCommandEditorHostTest.kt`
  - `replaceQueryText()` removes exactly the captured range
  - span ranges outside the removed query remain intact
  - missing anchor block results in safe no-op
  - replacing anchor block with preserved id keeps focus/selection coherent

### Definition of Done

- slash host operations can update runtime and snapshot state together
- query text can be removed from the middle of a block without corrupting spans
- all host/range helper tests pass

## Task 6 — Add Slash Text Observer and Session Tracking

### Goal

Detect freshly typed `/`, maintain `query` plus `queryRange` as the user edits, and dismiss the session when the range becomes invalid.

### Scope

- update `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/renderers/TextBlockField.kt`
- likely add `SlashCommandTextObserver.kt` under the slash package
- update `DefaultBlockCallbacks.onSlashCommand(...)` if it remains useful, or remove it if the text observer supersedes it

### Technical Work

- Implement a committed-text observer similar in shape to `SpanMaintenanceTextObserver`, but for slash session tracking.
- Observer responsibilities:
  - detect a newly inserted `/` in visible text
  - open a slash session with exact `queryRange`
  - update `query` and `queryRange` as text is inserted/deleted inside the active range
  - close the session if:
    - the leading `/` is deleted
    - focus moves away
    - the cursor leaves `queryRange`
    - the anchor block changes
- Distinguish user-typed `/` from pasted text containing `/`. The spec explicitly excludes pasted `/` from triggering the menu. Recommended heuristic: only open the menu when a single-character insertion of `/` is detected. Multi-character insertions (paste, autocomplete) that happen to include `/` should not trigger.
- Use the existing `pendingProgrammaticCommits` mechanism in `BlockTextStates` to skip programmatic insertions (e.g., `setText`, `mergeInto`). Future undo/redo replay will also go through programmatic commit paths, so this naturally satisfies the spec's undo/redo exclusion rule.
- Track selection from the active `TextFieldState`.
- Keep all coordinates in visible-text space, never sentinel coordinates.
- Decide whether spaces remain part of the query. Spec says yes.

### Notes

- The old `onSlashCommand(blockId)` callback is too weak because it cannot provide a range.
- If retained for backwards compatibility, it should become an implementation detail, not the primary trigger path.

### Tests

Add `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/SlashCommandTextObserverTest.kt`:

- typing `/` opens a session with the right initial range
- typing more characters extends the range and updates the query
- deleting characters shrinks the range
- deleting the slash closes the session
- cursor move outside the range closes the session
- slash inserted by programmatic text change does not open the menu
- pasted text containing `/` does not open the menu
- multi-character insertion containing `/` does not open the menu

### Definition of Done

- slash session lifecycle is driven from live text edits
- inline range tracking works for middle-of-line slash commands
- observer behavior has direct unit coverage

## Task 7 — Implement Command Execution Engine

### Goal

Execute slash items, support async actions, support submenu navigation, and enforce query-text policy.

### Scope

- add execution coordinator classes under the slash package
- update `CascadeEditor.kt` to create and own the execution coordinator

### Technical Work

- Add an internal coordinator, for example `SlashCommandExecutor`, that:
  - reads the current `SlashCommandState`
  - resolves the selected `SlashCommandItem`
  - handles submenu navigation by updating `navigationPath`
  - applies `queryTextPolicy`
  - launches `SlashCommandAction.onExecute` in an editor-owned coroutine scope
  - converts uncaught exceptions to `SlashCommandResult.Failure`
  - closes or keeps the menu open based on the result
- Execution order for actions:
  1. capture current session snapshot
  2. remove query text when policy is `RemoveBeforeExecute`
  3. invoke action
  4. apply result
- Built-in block command execution:
  - resolve the target descriptor
  - compute `remainingText` after query removal
  - if behavior is `ReplaceAnchorWhenBlank` and `remainingText.isBlank()`, replace anchor block with empty target block
  - otherwise insert a new target block below anchor
- Explicitly protect against stale anchor blocks during async execution.

### Notes

- The executor should not know about popup layout.
- Failure handling should keep editor state consistent even if command code throws.

### Tests

Add `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/SlashCommandExecutorTest.kt`:

- submenu selection updates navigation path without executing
- `RemoveBeforeExecute` removes the range before command logic runs
- `KeepText` does not remove the range
- built-in blank-anchor command replaces the block
- built-in non-blank command inserts below
- `AlwaysInsert` never replaces
- thrown exception becomes `Failure` and does not crash the executor
- missing anchor block is handled gracefully

### Definition of Done

- action and menu items both execute correctly
- async command flow is deterministic and unit-tested
- built-in block semantics match the spec

## Task 8 — Integrate Slash Registry and Host Into `CascadeEditor`

### Goal

Make the editor own slash infrastructure and expose a clean integration point for consumers to register custom slash items.

### Scope

- update `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/CascadeEditor.kt`
- update editor construction APIs if needed

### Technical Work

- Decide the public integration shape. Recommended:
  - `CascadeEditor(..., slashRegistry: SlashCommandRegistry = remember { SlashCommandRegistry() })`
  - or consumer registration via `BlockRegistry` plus an explicit slash registry param
- In `CascadeEditor`, create and remember:
  - slash registry or registry adapter
  - built-in descriptor-to-command adapter
  - slash execution coordinator
  - slash editor host bound to `stateHolder`, `BlockTextStates`, and `BlockSpanStates`
- Ensure slash session closes automatically when:
  - drag starts
  - multi-selection becomes active
  - anchor block is deleted
- Keep lifecycle cleanup aligned with existing `LaunchedEffect(state.blocks)` patterns.

### Notes

- This is where built-in items and consumer items become one logical menu source.
- Do not mix search logic back into `BlockRegistry`.

### Tests

Add integration-oriented unit tests where possible:

- `CascadeEditorSlashIntegrationTest.kt`
  - built-in and custom items are both available to the executor
  - session closes when anchor block is removed
  - session closes when selection/drag invalidates slash usage

### Definition of Done

- `CascadeEditor` owns all non-UI slash infrastructure
- consumers have a defined way to provide custom slash commands
- integration tests cover invalidation rules

## Task 9 — Implement Slash Popup UI and Keyboard Navigation

### Goal

Render the slash popup, anchor it near the caret, support grouped results, submenu navigation, and keyboard selection.

### Scope

- add new UI components under `editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/ui/`
- update `CascadeEditor.kt` to render the popup overlay

### Technical Work

- Add a popup composable, for example:
  - `SlashCommandPopup`
  - `SlashCommandList`
  - `SlashCommandRow`
- UI requirements:
  - anchor to the current caret rect
  - default below, flip above on insufficient space
  - group visible items by `SlashCommandGroup.order` then label
  - highlight the active row
  - support back navigation for submenu paths
  - support keyboard Enter on highlighted item
  - support Up/Down arrow navigation for highlight movement
- Keyboard interception strategy: install a `Modifier.onPreviewKeyEvent` on the popup's parent (or on the text field wrapper) that intercepts Up, Down, Enter, and Escape **only while a slash session is active**. Intercepted keys dispatch highlight/execute/close actions and return `true` to consume the event. All other keys pass through to the text field normally. This keeps text input focus in the field at all times.
- Collect cursor rect information from `TextBlockField` / `TextLayoutResult` for the focused anchor block.
- Avoid moving popup state into `EditorState`; keep geometry local to UI.

### Notes

- Popup placement fidelity is mostly a UI integration concern; unit tests are low value here.
- Focus and text input must remain in the underlying text field while the popup is visible. The popup must NOT request focus itself.

### Tests

Prefer lightweight logic tests for view-model style code if extracted, for example:

- visible grouped items for a given path and query
- highlight movement clamps to list bounds
- back navigation updates the visible path correctly

Manual verification is required for:

- caret anchoring
- below/above flipping
- pointer selection
- keyboard interaction across desktop/mobile targets

### Definition of Done

- popup appears for active slash sessions
- filtered/grouped items are rendered correctly
- submenu navigation and action activation work from the popup
- manual verification confirms placement and interaction on supported targets

## Task 10 — Add End-to-End Regression Coverage and API Documentation

### Goal

Harden the feature with cross-cutting tests and document the new public API.

### Scope

- add or extend test files under `editor/src/commonTest/kotlin/io/github/linreal/cascade/editor/`
- update `ARCHITECTURE.md` if needed after implementation stabilizes
- document public slash extension points in KDoc

### Technical Work

- Add end-to-end scenario coverage for the highest-risk flows:
  - inline slash typed in the middle of styled text
  - built-in replace-on-blank command
  - built-in always-insert command
  - custom async command inserting a new block
  - submenu navigation followed by action execution
  - session invalidation during drag or selection change
- Review new public types for KDoc completeness.
- Update architecture docs so slash commands are described with the new registry/host split rather than the old minimal reducer-only model.

### Tests

Add focused regression tests using fake registries, fake slash editor hosts, or real state holders where practical:

- no text outside `queryRange` is changed by command execution
- span state remains aligned after query removal
- built-in and custom items share one search pipeline
- async failure does not leave the session in a corrupt state

### Definition of Done

- the highest-risk slash flows have regression coverage
- slash public API is documented
- architecture docs no longer describe the old slash design as the target implementation
