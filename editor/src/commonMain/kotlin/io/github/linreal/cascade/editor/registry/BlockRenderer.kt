package io.github.linreal.cascade.editor.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.FocusNextBlock
import io.github.linreal.cascade.editor.action.FocusPreviousBlock
import io.github.linreal.cascade.editor.action.MergeBlocks
import io.github.linreal.cascade.editor.action.OpenSlashCommand
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.action.UpdateBlockText
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.loge
import io.github.linreal.cascade.editor.state.EditorState

/**
 * Callbacks for block interactions.
 * These are provided to renderers to handle user interactions.
 */
public interface BlockCallbacks {
    /**
     * Dispatches an action to the editor.
     */
    public fun dispatch(action: EditorAction)

    /**
     * Called when text content changes.
     */
    public fun onTextChange(blockId: BlockId, text: String)

    /**
     * Called when the block receives focus.
     */
    public fun onFocus(blockId: BlockId, cursorPosition: Int?)

    /**
     * Called when the block loses focus.
     */
    public fun onBlur(blockId: BlockId)

    /**
     * Called when the user presses Enter.
     * @param cursorPosition Position where Enter was pressed
     */
    public fun onEnter(blockId: BlockId, cursorPosition: Int)

    /**
     * Called when the user presses Backspace at the start of a block.
     */
    public fun onBackspaceAtStart(blockId: BlockId)

    /**
     * Called when the user presses Delete at the end of a block.
     */
    public fun onDeleteAtEnd(blockId: BlockId)

    /**
     * Called when the user clicks the block (for selection).
     * @param isMultiSelect True if Ctrl/Cmd is held
     * @param isRangeSelect True if Shift is held
     */
    public fun onClick(blockId: BlockId, isMultiSelect: Boolean, isRangeSelect: Boolean)

    /**
     * Called when a drag operation starts on this block.
     */
    public fun onDragStart(blockId: BlockId)

    /**
     * Called when the user types '/' to open slash commands.
     */
    public fun onSlashCommand(blockId: BlockId)
}

/**
 * Default implementation of [BlockCallbacks] that delegates to dispatch.
 *
 * @param dispatchFn Function to dispatch actions
 * @param stateProvider Optional provider for current editor state, enables merge/delete logic
 */
public open class DefaultBlockCallbacks(
    private val dispatchFn: (EditorAction) -> Unit,
    private val stateProvider: (() -> EditorState)? = null
) : BlockCallbacks {

    override fun dispatch(action: EditorAction) {
        dispatchFn(action)
    }

    override fun onTextChange(blockId: BlockId, text: String) {
        dispatch(UpdateBlockText(blockId, text))
    }

    override fun onFocus(blockId: BlockId, cursorPosition: Int?) {
        dispatch(FocusBlock(blockId, cursorPosition))
    }

    override fun onBlur(blockId: BlockId) {
        // Default: do nothing, focus is managed by incoming focus events
    }

    override fun onEnter(blockId: BlockId, cursorPosition: Int) {
        dispatch(SplitBlock(blockId, cursorPosition))
    }

    override fun onBackspaceAtStart(blockId: BlockId) {
        val state = stateProvider?.invoke()
        if (state != null) {
            val blockIndex = state.indexOfBlock(blockId)
            if (blockIndex > 0) {
                val previousBlock = state.blocks[blockIndex - 1]
                // Only merge if previous block supports text
                if (previousBlock.type.supportsText && previousBlock.content is BlockContent.Text) {
                    dispatch(MergeBlocks(sourceId = blockId, targetId = previousBlock.id))
                    return
                }
            }
        }
        // Fallback: just move focus
        dispatch(FocusPreviousBlock)
    }

    override fun onClick(blockId: BlockId, isMultiSelect: Boolean, isRangeSelect: Boolean) {
        // Implement based on modifier keys - subclasses can override
    }

    override fun onDeleteAtEnd(blockId: BlockId) {
        val state = stateProvider?.invoke()
        if (state != null) {
            val blockIndex = state.indexOfBlock(blockId)
            if (blockIndex < state.blocks.size - 1) {
                val nextBlock = state.blocks[blockIndex + 1]
                // Only merge if next block supports text
                if (nextBlock.type.supportsText && nextBlock.content is BlockContent.Text) {
                    dispatch(MergeBlocks(sourceId = nextBlock.id, targetId = blockId))
                    return
                }
            }
        }
        // Fallback: just move focus
        dispatch(FocusNextBlock)
    }

    override fun onDragStart(blockId: BlockId) {
        dispatch(StartDrag(setOf(blockId)))
    }

    override fun onSlashCommand(blockId: BlockId) {
        dispatch(OpenSlashCommand(blockId))
    }
}

/**
 * Interface for rendering a specific block type.
 *
 * Implement this interface to provide custom rendering for block types.
 * Renderers are registered with [BlockRegistry] and looked up by type ID.
 *
 * @param T The specific [BlockType] this renderer handles
 */
public interface BlockRenderer<T : BlockType> {
    /**
     * Renders the block content.
     *
     * @param block The block to render
     * @param isSelected Whether this block is selected
     * @param isFocused Whether this block has focus
     * @param modifier Modifier to apply to the root composable
     * @param callbacks Callbacks for handling user interactions
     */
    @Composable
    public fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    )
}

/**
 * A simple renderer that delegates to a composable lambda.
 * Useful for simple block types that don't need a full class.
 */
public class LambdaBlockRenderer<T : BlockType>(
    private val render: @Composable (
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    ) -> Unit
) : BlockRenderer<T> {

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    ) {
        render(block, isSelected, isFocused, modifier, callbacks)
    }
}

/**
 * Factory function for creating a lambda-based renderer.
 */
public fun <T : BlockType> blockRenderer(
    render: @Composable (
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    ) -> Unit
): BlockRenderer<T> = LambdaBlockRenderer(render)
