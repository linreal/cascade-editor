package io.github.linreal.cascade.editor.action

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.loge
import io.github.linreal.cascade.editor.state.DragState
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.SlashCommandState
import kotlin.math.max
import kotlin.math.min

/**
 * Sealed interface for all editor actions.
 * Actions are the only way to modify editor state, ensuring unidirectional data flow.
 */
public sealed interface EditorAction {
    /**
     * Reduces the current state to a new state based on this action.
     */
    public fun reduce(state: EditorState): EditorState
}

// =============================================================================
// Block Manipulation Actions
// =============================================================================

/**
 * Inserts a block at the specified index.
 * If [atIndex] is null, appends to the end.
 */
public data class InsertBlock(
    val block: Block,
    val atIndex: Int? = null
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val index = atIndex?.coerceIn(0, state.blocks.size) ?: state.blocks.size
        val newBlocks = state.blocks.toMutableList().apply {
            add(index, block)
        }
        return state.copy(blocks = newBlocks)
    }
}

/**
 * Inserts a block after the specified block ID.
 * If [afterBlockId] is null, prepends to the beginning.
 */
public data class InsertBlockAfter(
    val block: Block,
    val afterBlockId: BlockId?
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val index = if (afterBlockId == null) {
            0
        } else {
            val afterIndex = state.indexOfBlock(afterBlockId)
            if (afterIndex == -1) state.blocks.size else afterIndex + 1
        }
        val newBlocks = state.blocks.toMutableList().apply {
            add(index, block)
        }
        return state.copy(blocks = newBlocks)
    }
}

/**
 * Deletes blocks with the specified IDs.
 */
public data class DeleteBlocks(
    val blockIds: Set<BlockId>
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val newBlocks = state.blocks.filterNot { it.id in blockIds }
        val newFocusedId = if (state.focusedBlockId in blockIds) null else state.focusedBlockId
        val newSelectedIds = state.selectedBlockIds - blockIds
        return state.copy(
            blocks = newBlocks,
            focusedBlockId = newFocusedId,
            selectedBlockIds = newSelectedIds
        )
    }
}

/**
 * Deletes a single block by ID.
 */
public data class DeleteBlock(
    val blockId: BlockId
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return DeleteBlocks(setOf(blockId)).reduce(state)
    }
}

/**
 * Updates the content of a block.
 */
public data class UpdateBlockContent(
    val blockId: BlockId,
    val content: BlockContent
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val newBlocks = state.blocks.map { block ->
            if (block.id == blockId) block.withContent(content) else block
        }
        return state.copy(blocks = newBlocks)
    }
}

/**
 * Updates the text content of a block.
 * Convenience action for text-based blocks.
 */
public data class UpdateBlockText(
    val blockId: BlockId,
    val text: String
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return UpdateBlockContent(blockId, BlockContent.Text(text)).reduce(state)
    }
}

/**
 * Converts a block to a different type, preserving its content if compatible.
 */
public data class ConvertBlockType(
    val blockId: BlockId,
    val newType: BlockType
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val newBlocks = state.blocks.map { block ->
            if (block.id == blockId) block.withType(newType) else block
        }
        return state.copy(blocks = newBlocks)
    }
}

/**
 * Moves blocks to a new index position.
 */
public data class MoveBlocks(
    val blockIds: Set<BlockId>,
    val toIndex: Int
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val blocksToMove = state.blocks.filter { it.id in blockIds }
        val remainingBlocks = state.blocks.filterNot { it.id in blockIds }
        val targetIndex = toIndex.coerceIn(0, remainingBlocks.size)
        val newBlocks = remainingBlocks.toMutableList().apply {
            addAll(targetIndex, blocksToMove)
        }
        return state.copy(blocks = newBlocks)
    }
}

/**
 * Merges the content of source block into target block, then deletes source.
 * Only works for text-supporting blocks.
 */
public data class MergeBlocks(
    val sourceId: BlockId,
    val targetId: BlockId
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val sourceBlock = state.getBlock(sourceId) ?: return state
        val targetBlock = state.getBlock(targetId) ?: return state

        // Only merge text content
        val sourceText = (sourceBlock.content as? BlockContent.Text)?.text ?: return state
        val targetText = (targetBlock.content as? BlockContent.Text)?.text ?: return state
        loge(
            tag = "DefaultBlockCallbacks",
            message = "sourceText: $sourceText targetText: $targetText"
        )

        val mergedText = targetText + sourceText
        val cursorPosition = targetText.length

        // Update target with merged content
        val newBlocks = state.blocks
            .map { block ->
                if (block.id == targetId) {
                    block.withContent(BlockContent.Text(mergedText))
                } else {
                    block
                }
            }
            .filterNot { it.id == sourceId }
        newBlocks.forEach {
            loge(
                tag = "DefaultBlockCallbacks",
                message = "newBlocks: ${it.content}"
            )
        }
        return state.copy(
            blocks = newBlocks,
            focusedBlockId = targetId,
            cursorPosition = cursorPosition,
            selectedBlockIds = state.selectedBlockIds - sourceId
        )
    }
}

/**
 * Replaces a block with a new block.
 */
public data class ReplaceBlock(
    val blockId: BlockId,
    val newBlock: Block
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val newBlocks = state.blocks.map { block ->
            if (block.id == blockId) newBlock else block
        }
        val newFocusedId = if (state.focusedBlockId == blockId) newBlock.id else state.focusedBlockId
        val newSelectedIds = if (blockId in state.selectedBlockIds) {
            state.selectedBlockIds - blockId + newBlock.id
        } else {
            state.selectedBlockIds
        }
        return state.copy(
            blocks = newBlocks,
            focusedBlockId = newFocusedId,
            selectedBlockIds = newSelectedIds
        )
    }
}

// =============================================================================
// Selection Actions
// =============================================================================

/**
 * Selects a single block, clearing any previous selection.
 */
public data class SelectBlock(
    val blockId: BlockId
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(selectedBlockIds = setOf(blockId))
    }
}

/**
 * Toggles selection of a block (for multi-select with Ctrl/Cmd).
 */
public data class ToggleBlockSelection(
    val blockId: BlockId
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val newSelected = if (blockId in state.selectedBlockIds) {
            state.selectedBlockIds - blockId
        } else {
            state.selectedBlockIds + blockId
        }
        return state.copy(selectedBlockIds = newSelected)
    }
}

/**
 * Selects a range of blocks (for Shift+click).
 */
public data class SelectBlockRange(
    val fromId: BlockId,
    val toId: BlockId
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val fromIndex = state.indexOfBlock(fromId)
        val toIndex = state.indexOfBlock(toId)
        if (fromIndex == -1 || toIndex == -1) return state

        val startIndex = min(fromIndex, toIndex)
        val endIndex = max(fromIndex, toIndex)
        val rangeIds = state.blocks
            .subList(startIndex, endIndex + 1)
            .map { it.id }
            .toSet()

        return state.copy(selectedBlockIds = rangeIds)
    }
}

/**
 * Adds a range of blocks to the current selection.
 */
public data class AddBlockRangeToSelection(
    val fromId: BlockId,
    val toId: BlockId
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val fromIndex = state.indexOfBlock(fromId)
        val toIndex = state.indexOfBlock(toId)
        if (fromIndex == -1 || toIndex == -1) return state

        val startIndex = min(fromIndex, toIndex)
        val endIndex = max(fromIndex, toIndex)
        val rangeIds = state.blocks
            .subList(startIndex, endIndex + 1)
            .map { it.id }
            .toSet()

        return state.copy(selectedBlockIds = state.selectedBlockIds + rangeIds)
    }
}

/**
 * Clears all block selections.
 */
public data object ClearSelection : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(selectedBlockIds = emptySet())
    }
}

/**
 * Selects all blocks.
 */
public data object SelectAll : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(selectedBlockIds = state.blocks.map { it.id }.toSet())
    }
}

// =============================================================================
// Focus Actions
// =============================================================================

/**
 * Focuses a block with optional cursor position.
 */
public data class FocusBlock(
    val blockId: BlockId?,
    val cursorPosition: Int? = null
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(
            focusedBlockId = blockId,
            cursorPosition = cursorPosition
        )
    }
}

/**
 * Moves focus to the next block.
 */
public data object FocusNextBlock : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val currentIndex = state.focusedBlockId?.let { state.indexOfBlock(it) } ?: -1
        val nextIndex = (currentIndex + 1).coerceAtMost(state.blocks.size - 1)
        if (nextIndex < 0 || state.blocks.isEmpty()) return state
        return state.copy(
            focusedBlockId = state.blocks[nextIndex].id,
            cursorPosition = 0
        )
    }
}

/**
 * Moves focus to the previous block.
 */
public data object FocusPreviousBlock : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val currentIndex = state.focusedBlockId?.let { state.indexOfBlock(it) } ?: state.blocks.size
        val prevIndex = (currentIndex - 1).coerceAtLeast(0)
        if (prevIndex < 0 || state.blocks.isEmpty()) return state
        val prevBlock = state.blocks[prevIndex]
        val cursorPos = (prevBlock.content as? BlockContent.Text)?.text?.length
        return state.copy(
            focusedBlockId = prevBlock.id,
            cursorPosition = cursorPos
        )
    }
}

/**
 * Clears focus.
 */
public data object ClearFocus : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(focusedBlockId = null, cursorPosition = null)
    }
}

// =============================================================================
// Drag & Drop Actions
// =============================================================================

/**
 * Starts dragging the specified blocks.
 */
public data class StartDrag(
    val blockIds: Set<BlockId>
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(
            dragState = DragState(draggingBlockIds = blockIds, targetIndex = null)
        )
    }
}

/**
 * Updates the drop target during a drag operation.
 */
public data class UpdateDragTarget(
    val targetIndex: Int?
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val currentDrag = state.dragState ?: return state
        return state.copy(
            dragState = currentDrag.copy(targetIndex = targetIndex)
        )
    }
}

/**
 * Completes a drag operation, moving blocks to the target.
 */
public data object CompleteDrag : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val dragState = state.dragState ?: return state
        val targetIndex = dragState.targetIndex ?: return state.copy(dragState = null)

        return MoveBlocks(dragState.draggingBlockIds, targetIndex)
            .reduce(state)
            .copy(dragState = null)
    }
}

/**
 * Cancels a drag operation.
 */
public data object CancelDrag : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(dragState = null)
    }
}

// =============================================================================
// Slash Command Actions
// =============================================================================

/**
 * Opens the slash command menu.
 */
public data class OpenSlashCommand(
    val anchorBlockId: BlockId,
    val initialQuery: String = ""
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(
            slashCommandState = SlashCommandState(
                query = initialQuery,
                anchorBlockId = anchorBlockId
            )
        )
    }
}

/**
 * Updates the slash command search query.
 */
public data class UpdateSlashCommandQuery(
    val query: String
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val current = state.slashCommandState ?: return state
        return state.copy(
            slashCommandState = current.copy(query = query)
        )
    }
}

/**
 * Closes the slash command menu.
 */
public data object CloseSlashCommand : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        return state.copy(slashCommandState = null)
    }
}

// =============================================================================
// Compound Actions
// =============================================================================

/**
 * Splits a block at the cursor position, creating a new block below.
 */
public data class SplitBlock(
    val blockId: BlockId,
    val atPosition: Int
) : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val block = state.getBlock(blockId) ?: return state
        val textContent = block.content as? BlockContent.Text ?: return state
        val text = textContent.text

        val beforeText = text.take(atPosition)
        val afterText = text.drop(atPosition)

        val newBlock = Block(
            id = BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Text(afterText)
        )

        val blockIndex = state.indexOfBlock(blockId)
        val newBlocks = state.blocks.toMutableList().apply {
            this[blockIndex] = block.withContent(BlockContent.Text(beforeText))
            add(blockIndex + 1, newBlock)
        }

        return state.copy(
            blocks = newBlocks,
            focusedBlockId = newBlock.id,
            cursorPosition = 0
        )
    }
}

/**
 * Deletes selected blocks, or the focused block if nothing selected.
 */
public data object DeleteSelectedOrFocused : EditorAction {
    override fun reduce(state: EditorState): EditorState {
        val toDelete = if (state.selectedBlockIds.isNotEmpty()) {
            state.selectedBlockIds
        } else {
            state.focusedBlockId?.let { setOf(it) } ?: return state
        }
        return DeleteBlocks(toDelete).reduce(state)
    }
}
