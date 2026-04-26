package io.github.linreal.cascade.editor.state

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId

/**
 * Builds the narrowest history payload that exactly represents the transition.
 *
 * Single-block text/span edits use [BlockTextEntry]; everything else falls back
 * to a full [StructuralEntry].
 */
internal fun buildHistoryEntryFromCheckpoints(
    before: EditorCheckpoint,
    after: EditorCheckpoint,
): HistoryEntry {
    before.requireNormalizedForHistory()
    after.requireNormalizedForHistory()
    return buildBlockTextEntryOrNull(before, after) ?: StructuralEntry(
        before = before,
        after = after,
    )
}

/**
 * Returns a [BlockTextEntry] only when the exact v1 promotion predicate holds.
 */
internal fun buildBlockTextEntryOrNull(
    before: EditorCheckpoint,
    after: EditorCheckpoint,
): BlockTextEntry? {
    before.requireNormalizedForHistory()
    after.requireNormalizedForHistory()

    val beforeBlocks = before.blocks
    val afterBlocks = after.blocks
    if (beforeBlocks.size != afterBlocks.size) return null

    var changedBlockId: BlockId? = null
    var beforeContent: BlockContent.Text? = null
    var afterContent: BlockContent.Text? = null

    beforeBlocks.indices.forEach { index ->
        val beforeBlock = beforeBlocks[index]
        val afterBlock = afterBlocks[index]

        if (beforeBlock.id != afterBlock.id) return null
        if (beforeBlock.type != afterBlock.type) return null
        if (beforeBlock.content == afterBlock.content) return@forEach

        if (changedBlockId != null) return null

        val beforeText = beforeBlock.content as? BlockContent.Text ?: return null
        val afterText = afterBlock.content as? BlockContent.Text ?: return null

        changedBlockId = beforeBlock.id
        beforeContent = beforeText
        afterContent = afterText
    }

    val blockId = changedBlockId ?: return null
    return BlockTextEntry(
        blockId = blockId,
        before = requireNotNull(beforeContent),
        after = requireNotNull(afterContent),
        uiBefore = before.ui,
        uiAfter = after.ui,
    )
}

/**
 * Applies a one-block text history entry without clearing unrelated runtime
 * block state.
 *
 * Callers are responsible for holding the history replay guard while invoking
 * this helper.
 */
internal fun EditorStateHolder.applyBlockTextEntry(
    blockId: BlockId,
    content: BlockContent.Text,
    ui: EditingUiState,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
) {
    val replayState = state.patchBlockTextReplayState(
        blockId = blockId,
        content = content,
        ui = ui,
    )
    replaceStateForReplay(replayState)

    val existingTextState = textStates.get(blockId)
    if (existingTextState == null) {
        textStates.getOrCreate(blockId, content.text)
    } else {
        textStates.setText(blockId, content.text)
    }

    val existingSpanState = spanStates.get(blockId)
    if (existingSpanState == null) {
        spanStates.getOrCreate(
            blockId = blockId,
            initialSpans = content.spans,
            textLength = content.text.length,
        )
    } else {
        spanStates.set(
            blockId = blockId,
            spans = content.spans,
            textLength = content.text.length,
        )
    }

    ensureFocusedTextStateForReplay(
        uiState = ui,
        state = replayState,
        textStates = textStates,
    )
    restoreFocusedEditingUiState(
        uiState = ui,
        state = replayState,
        textStates = textStates,
        spanStates = spanStates,
    )
}

private fun EditorState.patchBlockTextReplayState(
    blockId: BlockId,
    content: BlockContent.Text,
    ui: EditingUiState,
): EditorState {
    val replayBlocks = blocks.replaceTextBlockContent(
        blockId = blockId,
        content = content,
    )
    return copy(
        blocks = replayBlocks,
        focusedBlockId = ui.focusedBlockId,
        selectedBlockIds = ui.replaySelectedBlockIds(replayBlocks),
    )
}

private fun ensureFocusedTextStateForReplay(
    uiState: EditingUiState,
    state: EditorState,
    textStates: BlockTextStates,
) {
    val focusedBlockId = uiState.focusedBlockId ?: return
    if (textStates.get(focusedBlockId) != null) return

    val content = state.getBlock(focusedBlockId)?.content as? BlockContent.Text ?: return
    textStates.getOrCreate(
        blockId = focusedBlockId,
        initialText = content.text,
    )
}
