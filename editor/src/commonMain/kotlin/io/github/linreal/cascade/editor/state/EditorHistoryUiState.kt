package io.github.linreal.cascade.editor.state

/**
 * Captures the replayable editor UI state that belongs to document history.
 *
 * Structural checkpoints intentionally restore more than just block content:
 * a user expects undoing a structural command such as subtree drag, indent, or
 * selection delete to bring back the same block-selection context they had when
 * the command ran. Earlier history versions only carried focused text editing
 * state and therefore replayed structural checkpoints with an empty block
 * selection. Subtree drag makes that lossy because selection defines the drag
 * roots and remains visible after the drop.
 *
 * The replayable UI scope is still deliberately narrow:
 * - [EditorState.focusedBlockId], plus focused visible-text selection and pending
 *   styles when the focused block is text-editable.
 * - [EditorState.selectedBlockIds], filtered only by normal state replay later.
 *
 * Transient surfaces such as slash menus and active drag state are still excluded
 * from checkpoints. They represent in-flight interactions, not durable editor
 * position.
 *
 * If there is no focused text block, or the runtime text holder has not been
 * created yet, focused text selection capture degrades to `null` instead of
 * failing. Block selection is snapshot state, so it can always be captured.
 */
internal fun captureFocusedEditingUiState(
    state: EditorState,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
): EditingUiState {
    val focusedBlockId = state.focusedBlockId
    val focusedTextBlockId = focusedBlockId?.takeIf { blockId ->
        state.getBlock(blockId)?.type?.supportsText == true
    }

    return EditingUiState(
        focusedBlockId = focusedBlockId,
        focusedTextSelection = focusedTextBlockId?.let(textStates::getSelection),
        focusedPendingStyles = focusedTextBlockId?.let(spanStates::getPendingStyles).orEmpty(),
        selectedBlockIds = if (focusedBlockId == null) state.selectedBlockIds else emptySet(),
    )
}

/**
 * Restores runtime-only focused text affordances after snapshot replay.
 *
 * Block selection is restored as part of rebuilding [EditorState] from the
 * checkpoint, not here. This function only touches runtime holders that are not
 * stored in [EditorState]: visible-text cursor/range selection and pending
 * continuation styles for the focused text block.
 *
 * The caller is responsible for restoring `EditorState.focusedBlockId` and
 * `EditorState.selectedBlockIds` first. Missing focus or missing blocks are
 * treated as safe no-ops because history replay may target documents where a
 * focused block was deleted by the opposite side of the entry.
 */
internal fun restoreFocusedEditingUiState(
    uiState: EditingUiState,
    state: EditorState,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
) {
    val focusedBlockId = uiState.focusedBlockId ?: return
    val block = state.getBlock(focusedBlockId) ?: return
    if (!block.type.supportsText) return

    uiState.focusedTextSelection?.let { selection ->
        textStates.setSelection(focusedBlockId, selection)
    }

    if (uiState.focusedPendingStyles.isEmpty()) {
        spanStates.clearPendingStyles(focusedBlockId)
    } else {
        spanStates.setPendingStyles(focusedBlockId, uiState.focusedPendingStyles)
    }
}
