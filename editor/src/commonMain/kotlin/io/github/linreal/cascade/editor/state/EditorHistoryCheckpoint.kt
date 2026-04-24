package io.github.linreal.cascade.editor.state

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.serialization.resolveCurrentBlocks

/**
 * Captures a full-document checkpoint from the authoritative mixed
 * runtime/snapshot editor state.
 *
 * A checkpoint is the unit structural history replays. It must include:
 * - resolved block payloads, with active text/span runtime state folded back
 *   into immutable [Block] snapshots;
 * - replayable editor UI context from [captureFocusedEditingUiState].
 *
 * Drag state, slash state, and other transient interaction state are
 * intentionally omitted. Block selection is not transient in this sense: it is
 * a stable editor context that affects structural commands, so it travels in the
 * checkpoint UI payload.
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
 *
 * Replay restores [EditorState] first, including focus and block selection, then
 * restores runtime-only focused text details. Keeping those two phases separate
 * avoids storing IME/cursor internals in the immutable document snapshot while
 * still making structural undo/redo round-trip visible editor context.
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
        selectedBlockIds = ui.replaySelectedBlockIds(blocks),
    )
}

/**
 * Returns the block selection that can be safely restored for a replay target.
 *
 * Focus and block selection are mutually exclusive in [EditorState]. A checkpoint
 * with focus wins over selection to preserve that invariant. For selection
 * checkpoints, stale IDs are filtered defensively so replaying hand-built or old
 * entries cannot resurrect selections for blocks absent from [blocks].
 */
internal fun EditingUiState.replaySelectedBlockIds(blocks: List<Block>): Set<BlockId> {
    if (focusedBlockId != null) return emptySet()
    val blockIds = blocks.mapTo(mutableSetOf()) { it.id }
    return selectedBlockIds.filterTo(linkedSetOf()) { it in blockIds }
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
