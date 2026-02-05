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
 *
 * @property draggingBlockIds The IDs of blocks currently being dragged
 * @property targetIndex The calculated drop position index (null if not over a valid target)
 * @property dragOffsetY Current Y position of the drag gesture relative to the editor
 * @property initialTouchOffsetY Y offset from the top of the primary block where touch started.
 *           Used to prevent the preview from "jumping" - the preview top position is
 *           calculated as (dragOffsetY - initialTouchOffsetY).
 * @property primaryBlockOriginalIndex Original list index of the primary dragged block
 *           (the block the user actually touched to initiate drag). Used for visual
 *           calculations like placeholder rendering.
 */
@Immutable
public data class DragState(
    val draggingBlockIds: Set<BlockId>,
    val targetIndex: Int?,
    val dragOffsetY: Float = 0f,
    val initialTouchOffsetY: Float = 0f,
    val primaryBlockOriginalIndex: Int = -1
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
