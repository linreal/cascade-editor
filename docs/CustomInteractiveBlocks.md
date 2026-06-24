# Interactive Custom Blocks — Technical Context


---

## 1. Feature Overview

Consumers of the Cascade editor can register custom block types, custom content payloads, custom renderers, and slash commands. The capability that this seam adds is **history-aware mutation from custom renderer code**: an interactive custom block can draw itself *and* change document state with the same undo/redo semantics as built-in structural edits. The plain public path, `EditorStateHolder.dispatch(...)`, bypasses history by contract, and the history transaction helpers are `internal`.

The `BlockRenderScope` plus `ScopedBlockRenderer<T>` renderer variant form a small public extension seam that closes that gap. A scoped renderer receives a live, capability-gated scope through which it can read editor state and apply block mutations that each produce exactly one structural undo entry. The seam is deliberately generic (no table-specific concepts) so the same surface supports future image, chart, embed, and diagram blocks. A sample interactive table block proves the seam end-to-end using public APIs only.

---

## 2. Architecture & Design Decisions

### New abstractions (editor module)

| Type | File | Visibility | Role |
|------|------|------------|------|
| `BlockRenderScope` | `registry/BlockRenderScope.kt` | `public interface` | The extension seam — state inspection + capability flags + gated mutators handed to scoped renderers. |
| `ScopedBlockRenderer<T : BlockType>` | `registry/BlockRenderer.kt` | `public interface` | A `BlockRenderer<T>` subtype whose `Render(...)` overload additionally receives a `BlockRenderScope`. |
| `DefaultBlockRenderScope` | `ui/DefaultBlockRenderScope.kt` | `internal class` | The only production implementation of `BlockRenderScope`; bridges the scope to `EditorStateHolder` and the interaction policy. |
| `RenderRegisteredBlock(...)` | `ui/CascadeEditor.kt` | `internal @Composable` | Dispatch helper that picks the scoped vs. legacy `Render` overload for a given renderer. |

### Patterns used and why

- **Interface segregation via a sub-interface, not a breaking change.** `ScopedBlockRenderer<T>` extends `BlockRenderer<T>` and provides a default body for the legacy 5-arg `Render(...)` that throws (`"ScopedBlockRenderer requires CascadeEditor to supply BlockRenderScope"`). This keeps every existing `BlockRenderer` source-compatible — old renderers compile and run untouched — while new renderers opt into the scope by implementing the 6-arg overload. The throwing default is a guardrail: a scoped renderer should never be invoked through the legacy path.

- **Capability gate over exposed internals.** Rather than handing renderers the `EditorStateHolder` or the interaction policy, `BlockRenderScope` exposes coarse boolean flags (`readOnly`, `canUpdateBlock`, `canEditBlockStructure`, `canSelectBlocks`, `canDragBlocks`) plus a fixed mutator vocabulary. Consumers disable their own controls from the flags; they never touch internal policy types.

- **Defense in depth on mutation.** Capability flags are advisory (used to disable UI), but every mutator in `DefaultBlockRenderScope` re-checks `canUpdateBlock` and re-reads the latest block before acting. A stale lambda captured before a runtime read-only flip therefore cannot mutate after the flip — the gate is enforced at call time, not capture time.

- **Live-by-getter, not snapshot-by-value.** `state`, `config`, `readOnly`, and the capability flags are computed `get()` properties backed by `rememberUpdatedState` providers wired in `CascadeEditor`. A long-lived renderer always reads current values even though the scope instance itself is `remember`ed across recompositions (it is keyed only on `stateHolder`).

- **`@Stable` annotations.** Both `BlockRenderScope` and `DefaultBlockRenderScope` are `@Stable`, signalling to Compose that the instance identity is stable and its observable reads go through snapshot state — so passing the scope as a parameter does not defeat recomposition skipping.

- **One structural transaction per committed operation.** All mutators route through `EditorStateHolder.dispatchStructuralAction(...)`, which wraps the reducer dispatch in `runStructuralHistoryTransaction { ... }` — capturing a before/after checkpoint and pushing a single `StructuralEntry`. This reuses the exact mechanism built-in structural edits already use.

---

## 3. Data Flow

### Wiring (composition setup, `CascadeEditor.kt`)

```
config ──rememberUpdatedState──▶ currentConfig ─┐
interactionPolicy ──rememberUpdatedState──▶ currentInteractionPolicy ─┐
                                                                       ▼
stateHolder ──remember(stateHolder)──▶ DefaultBlockRenderScope(stateHolder, {currentConfig}, {currentInteractionPolicy})
```

The single scope instance is created once per `stateHolder` and reused for every block.

### Render path

```
CascadeEditor (per block)
  └─ RenderRegisteredBlock(renderer, …, scope)
       ├─ renderer is ScopedBlockRenderer?  ── yes ─▶ renderer.Render(block, …, callbacks, scope)
       └─                                     no  ─▶ renderer.Render(block, …, callbacks)   // legacy 5-arg
```

The same helper is used by `DragPreview` so dragged scoped blocks still receive a scope (callbacks are non-interactive during drag).

### Mutation path (e.g. a cell commit)

```
user edits cell ─▶ renderer local draft state (mutableStateMapOf, NOT in EditorState)
   │ blur / Enter submit
   ▼
scope.updateBlock(blockId) { current -> current.withContent(newModel.toContent()) }
   ▼
DefaultBlockRenderScope.mutateExistingBlock
   ├─ if (!canUpdateBlock) return                       // policy gate
   ├─ current = stateHolder.state.getBlock(blockId) ?: return   // missing → no-op
   ├─ replacement = transform(current).copy(id = current.id)    // identity preserved
   ├─ if (replacement == current) return                // equal → no-op
   └─ stateHolder.dispatchStructuralAction(ReplaceBlock(blockId, replacement))
        ▼
   runStructuralHistoryTransaction { dispatch(ReplaceBlock) }
        ├─ captureCheckpoint(before)
        ├─ reducer applies ReplaceBlock → new EditorState
        ├─ captureCheckpoint(after)
        └─ pushHistoryEntry(StructuralEntry(before, after))   // exactly one undo step
        ▼
   recomposition renders the updated block
```

`focusBlock(...)` is the exception: it routes through `stateHolder.dispatch(FocusBlock(...))` directly and does **not** open a structural transaction, so focus changes never pollute undo history.

---

## 4. Public API Surface

### `BlockRenderScope` (`registry/BlockRenderScope.kt`)

```kotlin
@Stable
public interface BlockRenderScope {
    public val state: EditorState
    public val config: CascadeEditorConfig

    public val readOnly: Boolean
    public val canUpdateBlock: Boolean
    public val canEditBlockStructure: Boolean
    public val canSelectBlocks: Boolean
    public val canDragBlocks: Boolean

    public fun getBlock(blockId: BlockId): Block?
    public fun updateBlock(blockId: BlockId, transform: (Block) -> Block)
    public fun replaceBlock(blockId: BlockId, block: Block)
    public fun insertBlockBefore(blockId: BlockId, block: Block)
    public fun insertBlockAfter(blockId: BlockId, block: Block)
    public fun deleteBlock(blockId: BlockId)
    public fun focusBlock(blockId: BlockId?)
}
```

| Member | Behaviour / contract |
|--------|----------------------|
| `state` | Live editor snapshot read through the scope (`stateHolder.state`). |
| `config` / `readOnly` | Current `CascadeEditorConfig` and its `readOnly` flag; read at interaction time. |
| `canUpdateBlock` | Whether content/structure edits are currently allowed. In this version maps to the policy's `canEditBlockStructure`; named for the consumer operation, not the internal policy. |
| `canEditBlockStructure` / `canSelectBlocks` / `canDragBlocks` | Direct passthroughs of the editor interaction policy. |
| `getBlock(id)` | Latest block for `id`, or `null`. |
| `updateBlock(id, transform)` | Reads latest block, applies `transform`, **forces original id back** (`.copy(id = current.id)`), preserves position. No-op if missing / disabled / transform result equals current. One structural history entry. |
| `replaceBlock(id, block)` | Like `updateBlock` but installs the supplied block verbatim (caller may change identity). Same no-op + history rules. |
| `insertBlockBefore` / `insertBlockAfter` | Insert relative to an existing block via `InsertBlockBefore` / `InsertBlockAfter`. No-op if anchor missing or disabled. One history entry. |
| `deleteBlock(id)` | Delete via `DeleteBlock`. No-op if missing or disabled. One history entry. |
| `focusBlock(id?)` | Set/clear focus. No-op if target id is non-null but missing. **No** history entry. |


### `ScopedBlockRenderer<T>` (`registry/BlockRenderer.kt`)

```kotlin
public interface ScopedBlockRenderer<T : BlockType> : BlockRenderer<T> {
    @Composable public override fun Render(   // legacy 5-arg overload: throws
        block: Block, isSelected: Boolean, isFocused: Boolean,
        modifier: Modifier, callbacks: BlockCallbacks,
    ) { error("ScopedBlockRenderer requires CascadeEditor to supply BlockRenderScope") }

    @Composable public fun Render(            // implement THIS
        block: Block, isSelected: Boolean, isFocused: Boolean,
        modifier: Modifier, callbacks: BlockCallbacks, scope: BlockRenderScope,
    )
}
```

Implementers override only the 6-arg overload. `handlesSelectionVisual` (inherited from `BlockRenderer`) is still relevant — interactive blocks typically set it `true` to draw their own selection chrome instead of fighting the wrapper's overlay against inner field focus.

Usage stays identical to other custom blocks — register descriptor + renderer:

```kotlin
val registry = remember {
    createEditorRegistry().apply {
        register(createTableDescriptor(), createTableRenderer())
    }
}
```

---

## 5. Integration Points

- **`CascadeEditor` composable** — creates the single `DefaultBlockRenderScope` and routes every block through `RenderRegisteredBlock(...)`. The previous inline `renderer?.Render(...)` call site was replaced by an `if (renderer != null) RenderRegisteredBlock(...)` block; the modifier chain (crash guard, selection overlay, padding, drag-alpha `graphicsLayer`) is unchanged.
- **`EditorStateHolder`** — the scope depends on the existing (still `internal`) `dispatchStructuralAction(...)` / `runStructuralHistoryTransaction(...)`. The seam exposes these capabilities publicly *through the scope* without making the holder methods public.
- **Interaction policy** (`EditorInteractionPolicy` via `config.toInteractionPolicy()`) — source of the capability flags; read through `rememberUpdatedState` providers so runtime read-only flips are observed.
- **`DragPreview`** — now takes a `scope` parameter and renders via `RenderRegisteredBlock`, so scoped custom blocks render correctly as drag ghosts.
- **Reducers / actions** — mutators reuse existing `ReplaceBlock`, `InsertBlockBefore`, `InsertBlockAfter`, `DeleteBlock`, `FocusBlock`. No new actions or reducers were introduced for the seam.
- **Serialization** — unchanged. Custom payloads use `BlockContent.Custom` (a `typeId` + `Map`), which the existing JSON path already round-trips; no per-block codec is needed.
- **Sample app** — `CustomBlocksScreen` registers `createTableDescriptor()` / `createTableRenderer()` alongside existing metric/palette samples and seeds a demo table via `sampleTableBlock()`.

---

## 6. The Sample Table Block (example only)

The sample app ships a full interactive table block built entirely on the public seam above. It is **not** part of the editor — use it as a copy-from reference for your own custom blocks. It lives in `sample/src/commonMain/kotlin/io/github/linreal/cascade/screens/customblocks/`.

Main classes and functions:

| Symbol | File | Role |
|--------|------|------|
| `TableBlockType` | `SampleTableBlock.kt` | Sample `CustomBlockType` (`typeId = "table"`). |
| `SampleTableModel` | `SampleTableBlock.kt` | Immutable data model + parse/normalize/edit helpers (`fromBlock` / `fromContent`, `editCell`, `addRow` / `deleteRow`, `addColumn` / `deleteColumn`, `withHeaderRow`, `toContent`, `toBlock`, `default`). |
| `sampleTableBlock(id)` | `SampleTableBlock.kt` | Builds a default table `Block` for seeding/insertion. |
| `createTableDescriptor()` | `SampleTableBlockRenderer.kt` | `BlockDescriptor` with slash-command spec and factory. |
| `createTableRenderer()` | `SampleTableBlockRenderer.kt` | Returns the `ScopedBlockRenderer<TableBlockType>` implementation. |
| `commitCellValue(...)` / `updateTable(...)` | `SampleTableBlockRenderer.kt` | Private helpers showing how to commit edits through `scope.updateBlock(...)`. |

Registered in `CustomBlocksScreen.kt` via `register(createTableDescriptor(), createTableRenderer())`.

---

## 7. Edge Cases & Known Constraints

- **No-op safety is the core contract.** Every mutator silently no-ops on: missing target block, disabled/read-only policy, or an unchanged result (`replacement == current`). Renderers can fire mutators from stale lambdas without throwing.
- **Stale read-only flip.** Because `canUpdateBlock` is re-checked inside the mutator at call time, a mutation lambda captured while editable cannot mutate after a runtime transition to read-only. (Covered by editor tests.)
- **Identity preservation in `updateBlock`.** `transform`'s return has its id forcibly reset to the original (`.copy(id = current.id)`). To intentionally change a block's identity you **must** use `replaceBlock`.
- **Focus is not undoable and not persisted.** `focusBlock` skips history; inner-widget focus (e.g. the table's `focusedCell`) is renderer-local and **not** restored by undo/redo. Undo restores *content*, not which cell was focused.
- **Cell typing is not coalesced.** Unlike built-in text blocks (which batch keystrokes into one history entry), sample cell typing is local until a single blur/submit commit — that commit is one structural entry. This is an intentional difference, not a bug.
- **`@Stable` discipline.** Mutators are documented as "for event handlers and effects — do not call during composition." Calling a mutator during composition would dispatch state changes mid-frame.
- **Transaction fallback.** `runStructuralHistoryTransaction` only forms a real history entry when bound `textStates`/`spanStates` are available and no transaction/replay is already in progress; otherwise it runs the mutation bare. In normal `CascadeEditor` use these are bound, so scope mutations are history-aware.

---

## 8. Glossary

| Term | Meaning |
|------|---------|
| **`BlockRenderScope`** | Public, capability-gated interface giving a renderer controlled read + mutate access to editor state without exposing internals. |
| **`ScopedBlockRenderer<T>`** | `BlockRenderer<T>` variant whose `Render(...)` additionally receives a `BlockRenderScope`. The seam's renderer entry point. |
| **`DefaultBlockRenderScope`** | Internal implementation of `BlockRenderScope` bridging to `EditorStateHolder` and interaction policy. |
| **Structural history transaction** | A before/after checkpoint pair pushed as one `StructuralEntry`, making a mutation a single undo/redo step. |
| **`StructuralEntry`** | History record holding before/after document checkpoints (vs. `BlockTextEntry` for coalesced text edits). |
| **Capability flag** | Boolean on the scope (`canUpdateBlock`, etc.) advertising whether an operation is currently permitted under the interaction policy / read-only state. |
| **`BlockContent.Custom`** | Generic custom payload — a `typeId` string plus a primitive/list/map `data` bag — serialized by the default JSON path. |
| **`CustomBlockType`** | Consumer-defined `BlockType` (e.g. sample `TableBlockType`) identified by a `typeId`. |
| **Draft (sample)** | Renderer-local uncommitted edit state for a table cell; committed to `EditorState` only on blur/submit. |
| **`handlesSelectionVisual`** | Renderer flag; when `true` the editor suppresses its default selection overlay and the renderer draws its own selection chrome. |
| **No-op mutation** | A scope mutator call that intentionally does nothing (missing target, disabled policy, or unchanged result) instead of throwing. |
