package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.InsertBlockAfter
import io.github.linreal.cascade.editor.action.InsertBlockBefore
import io.github.linreal.cascade.editor.action.ReplaceBlock
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockRenderScope
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder

@Stable
internal class DefaultBlockRenderScope(
    private val stateHolder: EditorStateHolder,
    private val configProvider: () -> CascadeEditorConfig,
    private val policyProvider: () -> EditorInteractionPolicy,
) : BlockRenderScope {
    override val state: EditorState
        get() = stateHolder.state

    override val config: CascadeEditorConfig
        get() = configProvider()

    override val readOnly: Boolean
        get() = configProvider().readOnly

    override val canUpdateBlock: Boolean
        get() = policyProvider().canEditBlockStructure

    override val canEditBlockStructure: Boolean
        get() = policyProvider().canEditBlockStructure

    override val canSelectBlocks: Boolean
        get() = policyProvider().canSelectBlocks

    override val canDragBlocks: Boolean
        get() = policyProvider().canDragBlocks

    override fun getBlock(blockId: BlockId): Block? {
        return stateHolder.state.getBlock(blockId)
    }

    override fun updateBlock(blockId: BlockId, transform: (Block) -> Block) {
        mutateExistingBlock(blockId) { current ->
            transform(current).copy(id = current.id)
        }
    }

    override fun replaceBlock(blockId: BlockId, block: Block) {
        mutateExistingBlock(blockId) { block }
    }

    override fun insertBlockBefore(blockId: BlockId, block: Block) {
        if (!canUpdateBlock) return
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatchStructuralAction(InsertBlockBefore(block, blockId))
    }

    override fun insertBlockAfter(blockId: BlockId, block: Block) {
        if (!canUpdateBlock) return
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatchStructuralAction(InsertBlockAfter(block, blockId))
    }

    override fun deleteBlock(blockId: BlockId) {
        if (!canUpdateBlock) return
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatchStructuralAction(DeleteBlock(blockId))
    }

    override fun focusBlock(blockId: BlockId?) {
        if (blockId != null && stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatch(FocusBlock(blockId))
    }

    private fun mutateExistingBlock(
        blockId: BlockId,
        replacementFor: (Block) -> Block,
    ) {
        if (!canUpdateBlock) return
        val current = stateHolder.state.getBlock(blockId) ?: return
        val replacement = replacementFor(current)
        if (replacement == current) return
        stateHolder.dispatchStructuralAction(ReplaceBlock(blockId, replacement))
    }
}
