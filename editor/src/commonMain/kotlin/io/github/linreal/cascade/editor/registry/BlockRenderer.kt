package io.github.linreal.cascade.editor.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.action.ClearFocus
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.FocusNextBlock
import io.github.linreal.cascade.editor.action.FocusPreviousBlock
import io.github.linreal.cascade.editor.action.MergeBlocks
import io.github.linreal.cascade.editor.action.OpenSlashCommand
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.loge
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
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
     * Called when the block receives focus.
     */
    public fun onFocus(blockId: BlockId)

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
     */
    public fun onClick(blockId: BlockId)

    /**
     * Called when the user clicks the block (for drag and drop, usually).
     *
     */
    public fun onLongClick(blockId: BlockId)


    /**
     * Called when a drag operation starts on this block.
     *
     * @param blockId The ID of the block being dragged
     * @param touchOffsetY Y offset from the top of the block where touch occurred
     */
    public fun onDragStart(blockId: BlockId, touchOffsetY: Float)

    /**
     * Called when the user types '/' to open slash commands.
     */
    public fun onSlashCommand(blockId: BlockId)
}

/**
 * Default implementation of [BlockCallbacks] that delegates to dispatch.
 *
 * Uses [BlockTextStates] for text operations like merge and split, ensuring
 * the TextFieldState is the single source of truth for text content.
 *
 * @param dispatchFn Function to dispatch actions
 * @param stateProvider Optional provider for current editor state, enables merge/delete logic
 * @param blockTextStates Manager for per-block TextFieldState instances
 * @param blockSpanStates Manager for per-block runtime spans
 */
public open class DefaultBlockCallbacks(
    private val dispatchFn: (EditorAction) -> Unit,
    private val stateProvider: (() -> EditorState)? = null,
    private val blockTextStates: BlockTextStates? = null,
    private val blockSpanStates: BlockSpanStates? = null,
) : BlockCallbacks {

    override fun dispatch(action: EditorAction) {
        dispatchFn(action)
    }

    override fun onFocus(blockId: BlockId) {
        dispatch(FocusBlock(blockId))
    }

    override fun onEnter(blockId: BlockId, cursorPosition: Int) {
        val textStates = blockTextStates
        val newBlockId = BlockId.generate()

        if (textStates != null) {
            // Get current text from TextFieldState (source of truth)
            val fallbackText = (stateProvider?.invoke()?.getBlock(blockId)?.content as? BlockContent.Text)?.text.orEmpty()
            val currentText = textStates.getVisibleText(blockId) ?: fallbackText
            val splitPosition = cursorPosition.coerceIn(0, currentText.length)

            // Split the text
            val beforeText = currentText.take(splitPosition)
            val afterText = currentText.drop(splitPosition)

            // Runtime span split must happen before source text truncation.
            blockSpanStates?.split(
                sourceBlockId = blockId,
                newBlockId = newBlockId,
                position = splitPosition,
            )

            // Update current block's text to only have the "before" portion
            textStates.setText(blockId, beforeText, beforeText.length)

            // Dispatch split action with the "after" text for the new block
            dispatch(
                SplitBlock(
                    blockId = blockId,
                    atPosition = splitPosition,
                    newBlockText = afterText,
                    newBlockId = newBlockId,
                )
            )
        } else {
            // Fallback to original behavior
            dispatch(SplitBlock(blockId, cursorPosition, null, newBlockId))
        }
    }

    override fun onBackspaceAtStart(blockId: BlockId) {
        val state = stateProvider?.invoke()
        val textStates = blockTextStates

        if (state != null) {
            val blockIndex = state.indexOfBlock(blockId)
            if (blockIndex > 0) {
                val previousBlock = state.blocks[blockIndex - 1]
                // Only merge if previous block supports text
                if (previousBlock.type.supportsText && previousBlock.content is BlockContent.Text) {
                    if (textStates != null) {
                        val sourceContent =
                            state.getBlock(blockId)?.content as? BlockContent.Text
                        val targetContent = previousBlock.content
                        textStates.getOrCreate(blockId, sourceContent?.text.orEmpty())
                        textStates.getOrCreate(previousBlock.id, targetContent.text)

                        // Ensure span states exist before merge (block may not have been rendered yet).
                        blockSpanStates?.getOrCreate(blockId, sourceContent?.spans.orEmpty(), sourceContent?.text?.length ?: 0)
                        blockSpanStates?.getOrCreate(previousBlock.id, targetContent.spans, targetContent.text.length)

                        val targetTextLength = textStates.mergeInto(
                            sourceId = blockId,
                            targetId = previousBlock.id,
                        )
                        if (targetTextLength != null) {
                            blockSpanStates?.mergeInto(
                                sourceId = blockId,
                                targetId = previousBlock.id,
                                targetTextLength = targetTextLength,
                            )

                            // Dispatch action to remove source block and update focus
                            dispatch(DeleteBlock(blockId))
                            dispatch(FocusBlock(previousBlock.id))
                            return
                        }
                    }

                    // Fallback when runtime holders are unavailable.
                    dispatch(MergeBlocks(sourceId = blockId, targetId = previousBlock.id))
                    return
                }
            }
        }
        // Fallback: just move focus
        dispatch(FocusPreviousBlock)
    }

    override fun onClick(blockId: BlockId) {
        // TODO
    }

    override fun onLongClick(blockId: BlockId) {
        loge(message = "Long click on block $blockId")
    }

    override fun onDeleteAtEnd(blockId: BlockId) {
        val state = stateProvider?.invoke()
        val textStates = blockTextStates

        if (state != null) {
            val blockIndex = state.indexOfBlock(blockId)
            if (blockIndex < state.blocks.size - 1) {
                val nextBlock = state.blocks[blockIndex + 1]
                // Only merge if next block supports text
                if (nextBlock.type.supportsText && nextBlock.content is BlockContent.Text) {
                    if (textStates != null) {
                        val targetContent =
                            state.getBlock(blockId)?.content as? BlockContent.Text
                        val sourceContent = nextBlock.content
                        textStates.getOrCreate(blockId, targetContent?.text.orEmpty())
                        textStates.getOrCreate(nextBlock.id, sourceContent.text)

                        // Ensure span states exist before merge (block may not have been rendered yet).
                        blockSpanStates?.getOrCreate(blockId, targetContent?.spans.orEmpty(), targetContent?.text?.length ?: 0)
                        blockSpanStates?.getOrCreate(nextBlock.id, sourceContent.spans, sourceContent.text.length)

                        // Get cursor position before merge (end of current text)
                        val cursorPos = textStates.getVisibleText(blockId)?.length

                        // Perform text merge in BlockTextStates (nextBlock into current)
                        val targetTextLength = textStates.mergeInto(
                            sourceId = nextBlock.id,
                            targetId = blockId,
                        )
                        if (targetTextLength != null) {
                            blockSpanStates?.mergeInto(
                                sourceId = nextBlock.id,
                                targetId = blockId,
                                targetTextLength = targetTextLength,
                            )

                            // Dispatch action to remove source block
                            dispatch(DeleteBlock(nextBlock.id))

                            // Restore cursor position if needed
                            if (cursorPos != null) {
                                textStates.setCursorPosition(blockId, cursorPos)
                            }
                            return
                        }
                    }

                    // Fallback when runtime holders are unavailable.
                    dispatch(MergeBlocks(sourceId = nextBlock.id, targetId = blockId))
                    return
                }
            }
        }
        // Fallback: just move focus
        dispatch(FocusNextBlock)
    }

    override fun onDragStart(blockId: BlockId, touchOffsetY: Float) {
        dispatch(ClearFocus)
        dispatch(StartDrag(blockId, touchOffsetY))
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
