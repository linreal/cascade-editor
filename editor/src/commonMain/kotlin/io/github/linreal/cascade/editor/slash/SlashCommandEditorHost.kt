package io.github.linreal.cascade.editor.slash

import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.InsertBlockAfter
import io.github.linreal.cascade.editor.action.ReplaceBlock
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashQueryRange

/**
 * Internal implementation of [SlashCommandEditor] that safely coordinates
 * runtime text/span mutations with snapshot state.
 *
 * All operations are graceful no-ops when the anchor block no longer exists.
 *
 * @property anchorBlockId The block that owns the `/` trigger.
 * @property queryRange Captured visible-text range of the `/…` token at execution time.
 * @property stateHolder Snapshot state holder for dispatch.
 * @property textStates Runtime text state manager.
 * @property spanStates Runtime span state manager.
 */
internal class SlashCommandEditorHost(
    private val anchorBlockId: BlockId,
    private val queryRange: SlashQueryRange,
    private val stateHolder: EditorStateHolder,
    private val textStates: BlockTextStates,
    private val spanStates: BlockSpanStates,
) : SlashCommandEditor {

    override fun getAnchorBlock(): Block? {
        return stateHolder.state.getBlock(anchorBlockId)
    }

    override fun getAnchorVisibleText(): String? {
        return textStates.getVisibleText(anchorBlockId)
    }

    override fun replaceQueryText(replacement: String) {
        val visibleText = textStates.getVisibleText(anchorBlockId) ?: return

        val safeStart = queryRange.start.coerceIn(0, visibleText.length)
        val safeEnd = queryRange.endExclusive.coerceIn(safeStart, visibleText.length)

        // Update runtime spans first (before text changes)
        spanStates.adjustForRangeReplacement(
            blockId = anchorBlockId,
            start = safeStart,
            endExclusive = safeEnd,
            replacementLength = replacement.length,
        )

        // Update runtime text
        val newText = textStates.replaceVisibleRange(
            blockId = anchorBlockId,
            start = safeStart,
            endExclusive = safeEnd,
            replacement = replacement,
            cursorPositionAfter = safeStart + replacement.length,
        ) ?: return

        // Sync snapshot
        syncTextSnapshot(anchorBlockId, newText)
    }

    override fun updateAnchorText(text: String, cursorPosition: Int?) {
        if (stateHolder.state.getBlock(anchorBlockId) == null) return

        // Full text replacement — reset spans
        textStates.setText(anchorBlockId, text, cursorPosition)
        spanStates.set(anchorBlockId, emptyList(), text.length)

        // Sync snapshot
        syncTextSnapshot(anchorBlockId, text)
    }

    override fun replaceAnchorBlock(
        block: Block,
        preserveAnchorId: Boolean,
        requestFocus: Boolean,
        cursorPosition: Int?,
    ) {
        if (stateHolder.state.getBlock(anchorBlockId) == null) return

        val effectiveBlock = if (preserveAnchorId) {
            Block(anchorBlockId, block.type, block.content)
        } else {
            block
        }

        // Update snapshot
        stateHolder.dispatch(ReplaceBlock(anchorBlockId, effectiveBlock))

        // Set up runtime text state for the replacement block
        val textContent = effectiveBlock.content as? BlockContent.Text
        if (textContent != null) {
            textStates.setText(
                effectiveBlock.id,
                textContent.text,
                cursorPosition,
            )
            spanStates.set(effectiveBlock.id, textContent.spans, textContent.text.length)
        }

        if (requestFocus) {
            stateHolder.dispatch(FocusBlock(effectiveBlock.id))
            if (cursorPosition != null) {
                textStates.setCursorPosition(effectiveBlock.id, cursorPosition)
            }
        }
    }

    override fun insertBlockAfterAnchor(
        block: Block,
        requestFocus: Boolean,
        cursorPosition: Int?,
    ) {
        if (stateHolder.state.getBlock(anchorBlockId) == null) return

        // Insert in snapshot
        stateHolder.dispatch(InsertBlockAfter(block, anchorBlockId))

        // Set up runtime text state for inserted block
        val textContent = block.content as? BlockContent.Text
        if (textContent != null) {
            textStates.getOrCreate(block.id, textContent.text, cursorPosition ?: 0)
            spanStates.getOrCreate(block.id, textContent.spans, textContent.text.length)
        }

        if (requestFocus) {
            stateHolder.dispatch(FocusBlock(block.id))
            if (cursorPosition != null) {
                textStates.setCursorPosition(block.id, cursorPosition)
            }
        }
    }

    override fun focusBlock(blockId: BlockId, cursorPosition: Int?) {
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatch(FocusBlock(blockId))
        if (cursorPosition != null) {
            textStates.setCursorPosition(blockId, cursorPosition)
        }
    }

    override fun closeMenu() {
        stateHolder.dispatch(CloseSlashCommand)
    }

    // -- Internal helpers --

    /**
     * Syncs runtime text + current runtime spans into the snapshot via [UpdateBlockContent].
     */
    private fun syncTextSnapshot(blockId: BlockId, text: String) {
        val block = stateHolder.state.getBlock(blockId) ?: return
        if (block.content !is BlockContent.Text) return
        val spans = spanStates.getSpans(blockId)
        stateHolder.dispatch(
            UpdateBlockContent(blockId, BlockContent.Text(text, spans))
        )
    }
}
