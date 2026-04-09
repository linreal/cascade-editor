package io.github.linreal.cascade.editor.state

/**
 * Captures the focused editing UI state from runtime holders in visible-text
 * coordinates.
 *
 * Only the currently focused text block participates in v1 history UI capture.
 * If there is no focused block, the block is non-text, or the runtime text state
 * does not exist yet, selection capture degrades to `null` instead of failing.
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
    )
}

/**
 * Restores focused selection and focused pending styles for history replay.
 *
 * The caller is responsible for restoring `EditorState.focusedBlockId` first.
 * This helper then applies the runtime-only editing context for that focused
 * block. Missing focus or missing blocks are treated as safe no-ops.
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
