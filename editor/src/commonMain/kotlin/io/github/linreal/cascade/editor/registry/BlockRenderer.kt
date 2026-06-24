package io.github.linreal.cascade.editor.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.action.ClearFocus
import io.github.linreal.cascade.editor.action.CompleteDrag
import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.DeleteSelectedOrFocused
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.FocusNextBlock
import io.github.linreal.cascade.editor.action.FocusPreviousBlock
import io.github.linreal.cascade.editor.action.IndentBackward
import io.github.linreal.cascade.editor.action.IndentForward
import io.github.linreal.cascade.editor.action.MergeBlocks
import io.github.linreal.cascade.editor.action.OpenSlashCommand
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.action.ToggleTodo
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder

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
     *
     * @param blockId The anchor block where '/' was typed
     * @param queryRange Visible-text range covering the '/' and any query characters
     * @param initialQuery The query text after the leading '/' (empty at trigger time)
     */
    public fun onSlashCommand(blockId: BlockId, queryRange: SlashQueryRange, initialQuery: String = "")
}

/**
 * Default implementation of [BlockCallbacks] that delegates to dispatch.
 *
 * Uses [BlockTextStates] for text operations like merge and split, ensuring
 * the TextFieldState is the single source of truth for text content.
 *
 * @param dispatchFn Function to dispatch actions
 * @param stateProvider Optional provider for current editor state, enables merge/delete logic
 * @param textStates Manager for per-block TextFieldState instances
 * @param spanStates Manager for per-block runtime spans
 * @param stateHolder Optional history-aware holder used to wrap built-in
 *        structural edit sources into explicit structural transactions.
 */
public open class DefaultBlockCallbacks(
    private val dispatchFn: (EditorAction) -> Unit,
    private val stateProvider: (() -> EditorState)? = null,
    private val textStates: BlockTextStates? = null,
    private val spanStates: BlockSpanStates? = null,
    private val stateHolder: EditorStateHolder? = null,
) : BlockCallbacks {

    override fun dispatch(action: EditorAction) {
        if (action.shouldUseStructuralTransactionBoundary()) {
            stateHolder?.dispatchStructuralAction(action, textStates, spanStates) ?: dispatchFn(action)
            return
        }
        dispatchFn(action)
    }

    override fun onFocus(blockId: BlockId) {
        // In selection mode, focus must not be granted - it would clear the selection
        // via the focus/selection mutual exclusivity invariant.
        val state = stateProvider?.invoke()
        if (state != null && state.hasSelection) return
        dispatch(FocusBlock(blockId))
    }

    override fun onEnter(blockId: BlockId, cursorPosition: Int) {
        runStructuralMutation {
            val state = stateProvider?.invoke()
            if (state != null && state.hasSelection) return@runStructuralMutation

            val currentBlock = state?.getBlock(blockId)

            val blockType = currentBlock?.type

            // Code blocks intercept Enter: insert a literal newline at the cursor
            // (or replace a ranged selection) instead of splitting the block. The
            // exception is the "trailing-blank-line" gesture, which exits the code
            // block by inserting a fresh Paragraph below.
            if (currentBlock != null && blockType is BlockType.Code) {
                handleCodeBlockEnter(blockId, currentBlock, cursorPosition)
                return@runStructuralMutation
            }

            if (currentBlock != null) {
                val visibleText = currentBlock.visibleText(blockId)
                if (visibleText.isEmpty()) {
                    if (blockType.isEmptyEnterOutdentType() && currentBlock.isSupportedIndented()) {
                        dispatch(IndentBackward)
                        return@runStructuralMutation
                    }
                    if (blockType.isEmptyRootEnterExitType()) {
                        dispatch(ConvertBlockType(blockId, BlockType.Paragraph))
                        return@runStructuralMutation
                    }
                }
            }

            if (textStates != null) {
                val newBlockId = BlockId.generate()
                // Get current text from TextFieldState (source of truth)
                val fallbackText = (currentBlock?.content as? BlockContent.Text)?.text.orEmpty()
                val currentText = textStates.getVisibleText(blockId) ?: fallbackText
                val splitPosition = cursorPosition.coerceIn(0, currentText.length)

                // Split the text
                val beforeText = currentText.take(splitPosition)
                val afterText = currentText.drop(splitPosition)

                // Compute continuation styles BEFORE split (which clears pending on both blocks).
                // Only for collapsed-cursor splits (ranged split → no continuation).
                val selectionCollapsed = textStates.get(blockId)?.selection?.collapsed ?: true
                val continuationStyles = if (selectionCollapsed) {
                    spanStates?.let { spanStates ->
                        val pending = spanStates.getPendingStyles(blockId)
                        when {
                            pending != null -> pending
                            splitPosition == currentText.length && splitPosition > 0 ->
                                spanStates.activeStylesAt(blockId, splitPosition - 1)
                            else -> null
                        }
                    }
                } else {
                    null
                }

                // Runtime span split must happen before source text truncation.
                spanStates?.split(
                    sourceBlockId = blockId,
                    newBlockId = newBlockId,
                    position = splitPosition,
                )

                // Transfer continuation styles to the new block after split.
                if (!continuationStyles.isNullOrEmpty()) {
                    spanStates?.setPendingStyles(newBlockId, continuationStyles)
                }

                // Update current block's text to only have the "before" portion
                textStates.setText(blockId, beforeText, beforeText.length)
                val sourceRuntimeText = textStates.getVisibleText(blockId) ?: beforeText
                val sourceRuntimeSpans = spanStates?.getSpans(blockId)

                // Dispatch split action with the "after" text and runtime spans for the new block
                dispatch(
                    SplitBlock(
                        blockId = blockId,
                        atPosition = splitPosition,
                        newBlockText = afterText,
                        newBlockId = newBlockId,
                        newBlockSpans = spanStates?.getSpans(newBlockId),
                        sourceBlockText = sourceRuntimeText,
                        sourceBlockSpans = sourceRuntimeSpans,
                    )
                )
            } else {
                // Fallback to original behavior
                dispatch(SplitBlock(blockId, cursorPosition, null, BlockId.generate()))
            }
        }
    }

    override fun onBackspaceAtStart(blockId: BlockId) {
        runStructuralMutation {
            val state = stateProvider?.invoke()
            if (state != null && state.hasSelection) return@runStructuralMutation

            val currentBlock = state?.getBlock(blockId)

            if (currentBlock != null && currentBlock.isSupportedIndented()) {
                dispatch(IndentBackward)
                return@runStructuralMutation
            }

            // List item un-list: convert to Paragraph instead of merging with previous block.
            val blockType = currentBlock?.type
            if (blockType is BlockType.BulletList || blockType is BlockType.NumberedList || blockType is BlockType.Todo) {
                dispatch(ConvertBlockType(blockId, BlockType.Paragraph))
                return@runStructuralMutation
            }

            // Code: backspace at start converts to Paragraph in place rather than merging
            // upward. Multi-line text is preserved verbatim — the user can split it later.
            if (blockType is BlockType.Code) {
                dispatch(ConvertBlockType(blockId, BlockType.Paragraph))
                return@runStructuralMutation
            }

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
                            spanStates?.getOrCreate(blockId, sourceContent?.spans.orEmpty(), sourceContent?.text?.length ?: 0)
                            spanStates?.getOrCreate(previousBlock.id, targetContent.spans, targetContent.text.length)

                            val targetTextLength = textStates.mergeInto(
                                sourceId = blockId,
                                targetId = previousBlock.id,
                            )
                            if (targetTextLength != null) {
                                // Mirror the MergeBlocks reducer gate: when the target
                                // type opts out of spans (e.g. Code), the source's
                                // bold/link/etc spans must be discarded rather than
                                // carried into the target's canonical-empty span list.
                                val targetSupportsSpans = previousBlock.type.supportsSpans
                                if (targetSupportsSpans) {
                                    spanStates?.mergeInto(
                                        sourceId = blockId,
                                        targetId = previousBlock.id,
                                        targetTextLength = targetTextLength,
                                    )
                                } else {
                                    spanStates?.set(previousBlock.id, emptyList(), targetTextLength)
                                    spanStates?.clearPendingStyles(previousBlock.id)
                                }

                                // Sync snapshot with merged content before deleting source
                                val mergedText = textStates.getVisibleText(previousBlock.id)
                                if (mergedText != null) {
                                    val mergedSpans = if (targetSupportsSpans) {
                                        spanStates?.getSpans(previousBlock.id).orEmpty()
                                    } else {
                                        emptyList()
                                    }
                                    dispatch(UpdateBlockContent(previousBlock.id, BlockContent.Text(mergedText, mergedSpans)))
                                }

                                // Dispatch action to remove source block and update focus
                                dispatch(DeleteBlock(blockId))
                                dispatch(FocusBlock(previousBlock.id))
                                return@runStructuralMutation
                            }
                        }

                        // Fallback when runtime holders are unavailable.
                        dispatch(MergeBlocks(sourceId = blockId, targetId = previousBlock.id))
                        return@runStructuralMutation
                    }
                }
            }
            // Fallback: just move focus
            dispatch(FocusPreviousBlock)
        }
    }

    override fun onClick(blockId: BlockId) {
        // TODO could be deleted in future versions
    }

    override fun onLongClick(blockId: BlockId) {
        // TODO could be deleted in future versions
    }

    override fun onDeleteAtEnd(blockId: BlockId) {
        runStructuralMutation {
            val state = stateProvider?.invoke()

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
                            spanStates?.getOrCreate(blockId, targetContent?.spans.orEmpty(), targetContent?.text?.length ?: 0)
                            spanStates?.getOrCreate(nextBlock.id, sourceContent.spans, sourceContent.text.length)

                            // Get cursor position before merge (end of current text)
                            val cursorPos = textStates.getVisibleText(blockId)?.length

                            // Perform text merge in BlockTextStates (nextBlock into current)
                            val targetTextLength = textStates.mergeInto(
                                sourceId = nextBlock.id,
                                targetId = blockId,
                            )
                            if (targetTextLength != null) {
                                // Mirror the MergeBlocks reducer gate: when the target
                                // type opts out of spans (e.g. Code), the source's
                                // bold/link/etc spans must be discarded rather than
                                // carried into the target's canonical-empty span list.
                                val targetSupportsSpans = state.getBlock(blockId)?.type?.supportsSpans ?: true
                                if (targetSupportsSpans) {
                                    spanStates?.mergeInto(
                                        sourceId = nextBlock.id,
                                        targetId = blockId,
                                        targetTextLength = targetTextLength,
                                    )
                                } else {
                                    spanStates?.set(blockId, emptyList(), targetTextLength)
                                    spanStates?.clearPendingStyles(blockId)
                                }

                                // Sync snapshot with merged content before deleting source
                                val mergedText = textStates.getVisibleText(blockId)
                                if (mergedText != null) {
                                    val mergedSpans = if (targetSupportsSpans) {
                                        spanStates?.getSpans(blockId).orEmpty()
                                    } else {
                                        emptyList()
                                    }
                                    dispatch(UpdateBlockContent(blockId, BlockContent.Text(mergedText, mergedSpans)))
                                }

                                // Dispatch action to remove source block
                                dispatch(DeleteBlock(nextBlock.id))

                                // Restore cursor position if needed
                                if (cursorPos != null) {
                                    textStates.setCursorPosition(blockId, cursorPos)
                                }
                                return@runStructuralMutation
                            }
                        }

                        // Fallback when runtime holders are unavailable.
                        dispatch(MergeBlocks(sourceId = nextBlock.id, targetId = blockId))
                        return@runStructuralMutation
                    }
                }
            }
            // Fallback: just move focus
            dispatch(FocusNextBlock)
        }
    }

    override fun onDragStart(blockId: BlockId, touchOffsetY: Float) {
        dispatch(ClearFocus)
        dispatch(StartDrag(blockId, touchOffsetY))
    }

    override fun onSlashCommand(blockId: BlockId, queryRange: SlashQueryRange, initialQuery: String) {
        dispatch(OpenSlashCommand(anchorBlockId = blockId, queryRange = queryRange, initialQuery = initialQuery))
    }

    private fun runStructuralMutation(mutation: () -> Unit) {
        stateHolder?.runStructuralHistoryTransaction(textStates, spanStates, mutation) ?: mutation()
    }

    /**
     * Implements the Code-block Enter contract. The caller already enforced the
     * selection-mode and `block.type is BlockType.Code` guards.
     *
     * Branch order guarantees that ranged selections never trip
     * the trailing-blank-line exit even when the range touches the block end:
     *  1. Ranged selection - replace the selected visible range with a single `\n`.
     *  2. Empty visible text — convert the block to a Paragraph (mirrors the empty
     *     root-list exit).
     *  3. Trailing-blank-line exit - collapsed cursor at end AND the visible text
     *     ends with `\n`: drop the trailing newline and split off a fresh Paragraph.
     *  4. Otherwise — insert a `\n` at the collapsed cursor.
     *
     * All mutations are programmatic and run inside the structural history
     * transaction set up by the caller, so each Enter forms exactly one undo step.
     */
    private fun handleCodeBlockEnter(
        blockId: BlockId,
        block: Block,
        cursorPosition: Int,
    ) {
        val visibleText = block.visibleText(blockId)
        val rawSelection = textStates?.getSelection(blockId)
        val rawStart = rawSelection?.start ?: cursorPosition
        val rawEnd = rawSelection?.end ?: cursorPosition
        val rangeStart = minOf(rawStart, rawEnd).coerceIn(0, visibleText.length)
        val rangeEnd = maxOf(rawStart, rawEnd).coerceIn(0, visibleText.length)
        val hasRangeSelection = rangeStart != rangeEnd

        when {
            hasRangeSelection -> {
                // Ranged Enter never exits, even if the range touches the block end.
                replaceCodeRangeWithNewline(blockId, rangeStart, rangeEnd)
            }
            visibleText.isEmpty() -> {
                dispatch(ConvertBlockType(blockId, BlockType.Paragraph))
            }
            rangeStart == visibleText.length && visibleText.endsWith("\n") -> {
                exitCodeBlockOnTrailingBlankLine(blockId, visibleText)
            }
            else -> {
                replaceCodeRangeWithNewline(blockId, rangeStart, rangeStart)
            }
        }
    }

    /**
     * Replaces the visible-text range `[start, endExclusive)` of a code block with
     * a single `\n` and syncs the snapshot. The cursor lands immediately after the
     * inserted newline. Spans are forced to `emptyList()` because Code opts out of
     * spans (`supportsSpans = false`).
     *
     * Falls back to a snapshot-only `UpdateBlockContent` when no [textStates] is
     * available (test paths that exercise the reducer directly).
     */
    private fun replaceCodeRangeWithNewline(
        blockId: BlockId,
        start: Int,
        endExclusive: Int,
    ) {
        if (textStates != null) {
            val cursorAfter = start + 1
            val updatedText = textStates.replaceVisibleRange(
                blockId = blockId,
                start = start,
                endExclusive = endExclusive,
                replacement = "\n",
                cursorPositionAfter = cursorAfter,
            ) ?: return
            spanStates?.set(blockId, emptyList(), updatedText.length)
            spanStates?.clearPendingStyles(blockId)
            dispatch(UpdateBlockContent(blockId, BlockContent.Text(updatedText, emptyList())))
            return
        }

        val state = stateProvider?.invoke() ?: return
        val current = (state.getBlock(blockId)?.content as? BlockContent.Text)?.text ?: return
        val safeStart = start.coerceIn(0, current.length)
        val safeEnd = endExclusive.coerceIn(safeStart, current.length)
        val updatedText = current.take(safeStart) + "\n" + current.substring(safeEnd)
        dispatch(UpdateBlockContent(blockId, BlockContent.Text(updatedText, emptyList())))
    }

    /**
     * Trailing-blank-line exit: the cursor sits at the very end of a code block
     * whose visible text ends with `\n`. Drops that trailing newline, then splits
     * off a fresh empty Paragraph below.
     *
     * `SplitBlock` is preferable to a separate `UpdateBlockContent` + `InsertBlockAfter`
     * pair because `SplitBlock`'s reducer already (a) writes the trimmed source-block
     * snapshot via `sourceBlockText`, (b) inserts a `Paragraph` continuation
     * (`else -> BlockType.Paragraph` in `SplitBlock.reduce`), (c) resets indentation
     * for non-indentation source/target via `forSplitContinuation`, and (d) moves
     * focus to the new block — all in one reducer call inside one structural history
     * transaction.
     */
    private fun exitCodeBlockOnTrailingBlankLine(
        blockId: BlockId,
        visibleText: String,
    ) {
        val trimmedLength = visibleText.length - 1
        val trimmedText = visibleText.take(trimmedLength)
        val newBlockId = BlockId.generate()

        if (textStates != null) {
            textStates.replaceVisibleRange(
                blockId = blockId,
                start = trimmedLength,
                endExclusive = visibleText.length,
                replacement = "",
                cursorPositionAfter = trimmedLength,
            )
            spanStates?.set(blockId, emptyList(), trimmedLength)
            spanStates?.clearPendingStyles(blockId)
            // Seed runtime span state for the new paragraph so the upcoming
            // recomposition does not have to materialize it from snapshot.
            spanStates?.split(
                sourceBlockId = blockId,
                newBlockId = newBlockId,
                position = trimmedLength,
            )
        }

        dispatch(
            SplitBlock(
                blockId = blockId,
                atPosition = trimmedLength,
                newBlockText = "",
                newBlockId = newBlockId,
                newBlockSpans = emptyList(),
                sourceBlockText = trimmedText,
                sourceBlockSpans = emptyList(),
            )
        )
    }

    private fun Block.visibleText(blockId: BlockId): String {
        return textStates?.getVisibleText(blockId)
            ?: (content as? BlockContent.Text)?.text.orEmpty()
    }

    /**
     * True when a block participates in indentation semantics and can be outdented.
     */
    private fun Block.isSupportedIndented(): Boolean {
        return type.supportsIndentation &&
            attributes.indentationLevel > BlockAttributes.MIN_INDENTATION_LEVEL
    }

    /**
     * True for empty block types that should outdent instead of splitting when
     * they are already nested.
     */
    private fun BlockType?.isEmptyEnterOutdentType(): Boolean {
        return this is BlockType.BulletList ||
            this is BlockType.NumberedList ||
            this is BlockType.Todo
    }

    /**
     * True for empty root block types where Enter exits the structural type and
     * converts the current block to a paragraph.
     */
    private fun BlockType?.isEmptyRootEnterExitType(): Boolean {
        return this is BlockType.BulletList ||
            this is BlockType.NumberedList
    }
}

private fun EditorAction.shouldUseStructuralTransactionBoundary(): Boolean {
    return this is CompleteDrag ||
        this is DeleteSelectedOrFocused ||
        this is IndentBackward ||
        this is IndentForward ||
        this is ToggleTodo
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
     * Whether this renderer provides its own selection visual.
     *
     * When `true`, the wrapper-level default selection overlay is suppressed
     * and the renderer is fully responsible for indicating selection state
     * (using the `isSelected` parameter in [Render]).
     *
     * When `false` (default), a semi-transparent background overlay is applied
     * at the wrapper level automatically.
     */
    public val handlesSelectionVisual: Boolean get() = false

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
 * Renderer variant for custom blocks that need a public editor scope.
 *
 * Implementers only need to override the scoped overload. CascadeEditor detects
 * this interface and supplies a live [BlockRenderScope].
 */
public interface ScopedBlockRenderer<T : BlockType> : BlockRenderer<T> {
    @Composable
    public override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks,
    ) {
        error("ScopedBlockRenderer requires CascadeEditor to supply BlockRenderScope")
    }

    @Composable
    public fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks,
        scope: BlockRenderScope,
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
