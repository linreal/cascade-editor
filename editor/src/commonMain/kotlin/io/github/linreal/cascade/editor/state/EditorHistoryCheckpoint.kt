package io.github.linreal.cascade.editor.state

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.serialization.resolveCurrentBlocks

/**
 * Captures a full-document checkpoint from the authoritative mixed
 * runtime/snapshot editor state.
 */
internal fun EditorStateHolder.captureCheckpoint(
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
): EditorCheckpoint {
    return EditorCheckpoint(
        blocks = resolveCurrentBlocks(this, textStates, spanStates),
        ui = captureFocusedEditingUiState(state, textStates, spanStates),
    ).requireNormalizedForHistory()
}

/**
 * Applies a structural checkpoint by restoring snapshot state and
 * incrementally updating runtime text/span holders.
 *
 * Reuses existing [TextFieldState] instances where possible so that
 * [BasicTextField] does not reconnect the IME and reopen the keyboard.
 *
 * Callers are responsible for holding the history replay guard while invoking
 * this helper.
 */
internal fun EditorStateHolder.applyCheckpoint(
    checkpoint: EditorCheckpoint,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
) {
    val replayState = checkpoint.toReplayState()

    val checkpointBlockIds = checkpoint.blocks.mapTo(mutableSetOf()) { it.id }
    textStates.cleanup(checkpointBlockIds)
    spanStates.cleanup(checkpointBlockIds)

    checkpoint.blocks.forEach { block ->
        val content = block.content as? BlockContent.Text ?: return@forEach
        if (textStates.get(block.id) != null) {
            if (textStates.getVisibleText(block.id) != content.text) {
                textStates.setText(block.id, content.text)
            }
        } else {
            textStates.getOrCreate(block.id, content.text)
        }
        if (spanStates.get(block.id) != null) {
            if (spanStates.getSpans(block.id) != content.spans) {
                spanStates.set(block.id, content.spans, content.text.length)
            }
        } else {
            spanStates.getOrCreate(block.id, content.spans, content.text.length)
        }
    }

    replaceStateForReplay(replayState)

    restoreFocusedEditingUiState(
        uiState = checkpoint.ui,
        state = state,
        textStates = textStates,
        spanStates = spanStates,
    )
}

internal fun EditorCheckpoint.toReplayState(): EditorState {
    requireNormalizedForHistory()
    return EditorState.withBlocks(blocks).copy(
        focusedBlockId = ui.focusedBlockId,
    )
}

internal fun EditorCheckpoint.requireNormalizedForHistory(): EditorCheckpoint {
    require(blocks.endsWithTextSupportingBlock()) {
        "History checkpoints must be normalized and end with a text-supporting block"
    }
    return this
}

private fun List<Block>.endsWithTextSupportingBlock(): Boolean {
    return lastOrNull()?.type?.supportsText == true
}
