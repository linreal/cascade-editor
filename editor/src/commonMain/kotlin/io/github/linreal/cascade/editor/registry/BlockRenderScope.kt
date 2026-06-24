package io.github.linreal.cascade.editor.registry

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig

/**
 * Public interaction scope passed to renderers that need to mutate or inspect
 * editor state without depending on editor internals.
 *
 * [state] is a live editor snapshot read through this scope. [config],
 * [readOnly], and the capability flags should be read at interaction time
 * because editor configuration and policy may change while a renderer remains
 * composed.
 *
 * Once wired to `CascadeEditor`, mutation methods are history-aware. Mutations
 * are safe no-ops when the target block is missing, the current policy or
 * read-only state disables the requested operation, or the requested
 * replacement is equal to the current block.
 *
 * [updateBlock] updates the existing block while preserving the original block
 * id and position. Use [replaceBlock] when a renderer intentionally needs to
 * replace the block identity.
 *
 * Mutators are intended for event handlers and effects. Do not call them
 * directly during composition.
 */
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
