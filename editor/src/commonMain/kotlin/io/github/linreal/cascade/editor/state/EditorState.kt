package io.github.linreal.cascade.editor.state

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.normalizeIndentationOutline
import io.github.linreal.cascade.editor.slash.SlashCommandId

/**
 * Visible-text coordinate range for the slash query (including the leading `/`).
 *
 * Half-open interval: the character at [start] is included, the character at [endExclusive] is not.
 */
@Immutable
public data class SlashQueryRange(
    val start: Int,
    val endExclusive: Int,
)

/**
 * State for an active slash command session.
 *
 * @property anchorBlockId The block in which the `/` was typed.
 * @property query The current search query (text after the leading `/`).
 * @property queryRange Visible-text range covering the `/` and the query characters.
 * @property navigationPath Stack of submenu IDs the user has navigated into. Empty = root menu.
 * @property highlightedCommandId The currently highlighted item in the menu (for keyboard nav).
 */
@Immutable
public data class SlashCommandState(
    val anchorBlockId: BlockId,
    val query: String,
    val queryRange: SlashQueryRange,
    val navigationPath: List<SlashCommandId> = emptyList(),
    val highlightedCommandId: SlashCommandId? = null,
)

/**
 * State for drag-and-drop operations.
 *
 * @property draggingBlockIds Legacy containment set for blocks currently being dragged.
 *           Use [payloadBlockIds] when document order matters.
 * @property targetIndex The calculated drop position index (null if not over a valid target).
 *           This is the visual gap position. Depth-aware hover resolution clears it for
 *           gaps that cannot accept the payload without corrupting outline structure.
 * @property initialTouchOffsetY Y offset from the top of the primary block where touch started.
 *           Used to prevent the preview from "jumping" - the preview top position is
 *           calculated as (currentDragY - initialTouchOffsetY). Set once at drag start.
 * @property primaryBlockOriginalIndex Original list index of the primary dragged block
 *           (the block the user actually touched to initiate drag). Used for index
 *           compatibility with existing drag gesture semantics.
 * @property dragRootIds Payload root IDs in document order. For a selection drag this is
 *           the selected root set after descendant de-duplication.
 * @property payloadBlockIds Full payload IDs in document order, including every root subtree.
 * @property payloadBlockIdSet O(1) membership companion for [payloadBlockIds].
 * @property payloadBlockIndices Original document indices for [payloadBlockIds].
 * @property payloadBlockIndexSet O(1) membership companion for [payloadBlockIndices].
 * @property payloadIndexRanges Contiguous original index ranges occupied by the payload.
 * @property primaryRootId The root that drives preview/depth intent. This may differ from
 *           the first document-ordered root when a later selected root started the gesture.
 * @property primaryRootOriginalIndex Original document index for [primaryRootId].
 * @property primaryRootSupportsIndentation Whether [primaryRootId] can change indentation.
 * @property originalRootIndentationLevels Original indentation level for every root.
 * @property payloadRelativeDepthOffsets Relative indentation offset for each payload block
 *           from the root subtree it belongs to.
 * @property payloadRootIdsByBlockId Root ID for each payload block. Hover resolution uses
 *           this with [payloadRelativeDepthOffsets] to compute multi-root depth bounds
 *           without re-deriving ownership from the block list on every pointer frame.
 * @property futureRootIndentationLevel Current candidate indentation level for [primaryRootId].
 *           It changes only when hover resolution crosses a semantic indentation lane.
 */
@Immutable
public data class DragState(
    val draggingBlockIds: Set<BlockId>,
    val targetIndex: Int?,
    val initialTouchOffsetY: Float = 0f,
    val primaryBlockOriginalIndex: Int = -1,
    val dragRootIds: List<BlockId> = draggingBlockIds.toList(),
    val payloadBlockIds: List<BlockId> = draggingBlockIds.toList(),
    val payloadBlockIdSet: Set<BlockId> = payloadBlockIds.toSet(),
    val payloadBlockIndices: List<Int> = emptyList(),
    val payloadBlockIndexSet: Set<Int> = payloadBlockIndices.toSet(),
    val payloadIndexRanges: List<IntRange> = emptyList(),
    val primaryRootId: BlockId? = dragRootIds.firstOrNull(),
    val primaryRootOriginalIndex: Int = primaryBlockOriginalIndex,
    val primaryRootSupportsIndentation: Boolean? = null,
    val originalRootIndentationLevels: Map<BlockId, Int> = emptyMap(),
    val payloadRelativeDepthOffsets: Map<BlockId, Int> = emptyMap(),
    val payloadRootIdsByBlockId: Map<BlockId, BlockId> = emptyMap(),
    val futureRootIndentationLevel: Int = primaryRootId
        ?.let { originalRootIndentationLevels[it] }
        ?: 0,
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

    /**
     * Ensures the block list ends with a text-supporting block.
     * If empty or the last block doesn't support text, appends an empty paragraph.
     */
    internal fun ensureTrailingTextBlock(): EditorState {
        val normalizedBlocks = normalizeIndentationOutline(blocks)
        val lastBlock = normalizedBlocks.lastOrNull()
        if (lastBlock == null || !lastBlock.type.supportsText) {
            return copy(blocks = normalizedBlocks + Block.paragraph())
        }
        return if (normalizedBlocks === blocks) this else copy(blocks = normalizedBlocks)
    }

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
