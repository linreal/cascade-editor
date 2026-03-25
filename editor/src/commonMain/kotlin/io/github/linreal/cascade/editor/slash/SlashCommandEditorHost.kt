package io.github.linreal.cascade.editor.slash

import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.InsertBlockAfter
import io.github.linreal.cascade.editor.action.InsertBlockBefore
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
 * Anchor-bound edit operations are graceful no-ops when the anchor block no
 * longer exists or is no longer text-backed.
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
        getAnchorTextBlockOrNull() ?: return null
        return textStates.getVisibleText(anchorBlockId)
    }

    override fun replaceQueryText(replacement: String) {
        val anchorBlock = getAnchorTextBlockOrNull() ?: return
        val visibleText = textStates.getVisibleText(anchorBlock.id) ?: return

        val safeStart = queryRange.start.coerceIn(0, visibleText.length)
        val safeEnd = queryRange.endExclusive.coerceIn(safeStart, visibleText.length)

        // Update runtime spans first (before text changes)
        spanStates.adjustForRangeReplacement(
            blockId = anchorBlock.id,
            start = safeStart,
            endExclusive = safeEnd,
            replacementLength = replacement.length,
        )

        // Update runtime text
        val newText = textStates.replaceVisibleRange(
            blockId = anchorBlock.id,
            start = safeStart,
            endExclusive = safeEnd,
            replacement = replacement,
            cursorPositionAfter = safeStart + replacement.length,
        ) ?: return

        // Sync snapshot
        syncTextSnapshot(anchorBlock.id, newText)
    }

    override fun updateAnchorText(text: String, cursorPosition: Int?) {
        val anchorBlock = getAnchorTextBlockOrNull() ?: return

        // Full text replacement — reset spans
        textStates.setText(anchorBlock.id, text, cursorPosition)
        spanStates.set(anchorBlock.id, emptyList(), text.length)

        // Sync snapshot
        syncTextSnapshot(anchorBlock.id, text)
    }

    override fun replaceAnchorBlock(
        block: Block,
        preserveAnchorId: Boolean,
        requestFocus: Boolean,
        cursorPosition: Int?,
    ) {
        getAnchorTextBlockOrNull() ?: return

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
            upsertRuntimeTextAndSpans(
                blockId = effectiveBlock.id,
                textContent = textContent,
                cursorPosition = cursorPosition,
            )
        } else {
            textStates.remove(effectiveBlock.id)
            spanStates.remove(effectiveBlock.id)
        }

        if (!preserveAnchorId && effectiveBlock.id != anchorBlockId) {
            textStates.remove(anchorBlockId)
            spanStates.remove(anchorBlockId)
        }

        if (requestFocus) {
            stateHolder.dispatch(FocusBlock(effectiveBlock.id))
            if (textContent != null) {
                val targetCursor = cursorPosition
                    ?: (textStates.getVisibleText(effectiveBlock.id)?.length ?: textContent.text.length)
                textStates.setCursorPosition(effectiveBlock.id, targetCursor)
            }
        }
    }

    override fun insertBlockBeforeAnchor(block: Block) {
        getAnchorTextBlockOrNull() ?: return
        stateHolder.dispatch(InsertBlockBefore(block, anchorBlockId))
    }

    override fun insertBlockAfterAnchor(
        block: Block,
        requestFocus: Boolean,
        cursorPosition: Int?,
    ) {
        getAnchorTextBlockOrNull() ?: return

        // Insert in snapshot
        stateHolder.dispatch(InsertBlockAfter(block, anchorBlockId))

        // Set up runtime text state for inserted block
        val textContent = block.content as? BlockContent.Text
        if (textContent != null) {
            upsertRuntimeTextAndSpans(
                blockId = block.id,
                textContent = textContent,
                cursorPosition = cursorPosition,
            )
        }

        if (requestFocus) {
            stateHolder.dispatch(FocusBlock(block.id))
            if (textContent != null) {
                val targetCursor = cursorPosition
                    ?: (textStates.getVisibleText(block.id)?.length ?: textContent.text.length)
                textStates.setCursorPosition(block.id, targetCursor)
            }
        }
    }

    override fun focusBlock(blockId: BlockId, cursorPosition: Int?) {
        val block = stateHolder.state.getBlock(blockId) ?: return
        stateHolder.dispatch(FocusBlock(blockId))

        val textContent = block.content as? BlockContent.Text ?: return
        val targetCursor = cursorPosition
            ?: (textStates.getVisibleText(blockId)?.length ?: textContent.text.length)

        if (textStates.get(blockId) == null) {
            textStates.getOrCreate(blockId, textContent.text, targetCursor)
        } else {
            textStates.setCursorPosition(blockId, targetCursor)
        }
    }

    override fun closeMenu() {
        stateHolder.dispatch(CloseSlashCommand)
    }

    // -- Internal helpers --

    private fun getAnchorTextBlockOrNull(): Block? {
        val anchorBlock = stateHolder.state.getBlock(anchorBlockId) ?: return null
        return if (anchorBlock.content is BlockContent.Text) anchorBlock else null
    }

    private fun upsertRuntimeTextAndSpans(
        blockId: BlockId,
        textContent: BlockContent.Text,
        cursorPosition: Int?,
    ) {
        val targetCursor = cursorPosition ?: textContent.text.length

        if (textStates.get(blockId) == null) {
            textStates.getOrCreate(blockId, textContent.text, targetCursor)
        } else {
            textStates.setText(blockId, textContent.text, targetCursor)
        }

        if (spanStates.get(blockId) == null) {
            spanStates.getOrCreate(blockId, textContent.spans, textContent.text.length)
        } else {
            spanStates.set(blockId, textContent.spans, textContent.text.length)
        }
    }

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
