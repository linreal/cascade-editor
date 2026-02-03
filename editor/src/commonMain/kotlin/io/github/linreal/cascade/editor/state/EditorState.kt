package io.github.linreal.cascade.editor.state

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId

/**
 * State for slash command menu.
 */
@Immutable
public data class SlashCommandState(
    val query: String,
    val anchorBlockId: BlockId
)

/**
 * State for drag-and-drop operations.
 */
@Immutable
public data class DragState(
    val draggingBlockIds: Set<BlockId>,
    val targetIndex: Int?
)

/**
 * Immutable snapshot of the editor state.
 *
 * Selection state is stored here (not in Block) to support:
 * - Single source of truth for multi-selection
 * - O(1) select all / clear operations
 * - Block recomposition independent of selection changes
 */
@Immutable
public data class EditorState(
    val blocks: List<Block>,
    val focusedBlockId: BlockId?,
    val selectedBlockIds: Set<BlockId>,
    val dragState: DragState?,
    val slashCommandState: SlashCommandState?
) {
    /**
     * Returns the currently focused block, if any.
     */
    public val focusedBlock: Block?
        get() = focusedBlockId?.let { id -> blocks.find { it.id == id } }

    /**
     * Returns all selected blocks in their list order.
     */
    public val selectedBlocks: List<Block>
        get() = blocks.filter { it.id in selectedBlockIds }

    /**
     * Returns true if any blocks are selected.
     */
    public val hasSelection: Boolean
        get() = selectedBlockIds.isNotEmpty()

    /**
     * Returns true if a single block is selected.
     */
    public val hasSingleSelection: Boolean
        get() = selectedBlockIds.size == 1

    /**
     * Returns the index of a block by its ID, or -1 if not found.
     */
    public fun indexOfBlock(blockId: BlockId): Int = blocks.indexOfFirst { it.id == blockId }

    /**
     * Returns a block by its ID, or null if not found.
     */
    public fun getBlock(blockId: BlockId): Block? = blocks.find { it.id == blockId }

    public companion object {
        /**
         * Empty editor state with no blocks.
         */
        public val Empty: EditorState = EditorState(
            blocks = emptyList(),
            focusedBlockId = null,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = null
        )

        /**
         * Creates an editor state with the given blocks.
         */
        public fun withBlocks(blocks: List<Block>): EditorState = EditorState(
            blocks = blocks,
            focusedBlockId = null,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = null
        )
    }
}
