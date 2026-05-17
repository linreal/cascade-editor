package io.github.linreal.cascade.screens.external_toolbar

import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Toggles the focused block between paragraph and the requested list type.
 *
 * The toolbar controller exposes generic indentation and text-formatting actions,
 * while list conversion is still a block-level command in the editor reducer. This
 * helper keeps that reducer-facing logic out of the toolbar composable.
 */
internal fun toggleFocusedBlockType(
    editorState: EditorStateHolder,
    callbacks: BlockCallbacks,
    requestedType: BlockType,
    isReadOnly: Boolean,
) {
    if (isReadOnly) return

    val state = editorState.state
    if (state.hasSelection || state.dragState != null) return

    val block = state.focusedBlock ?: return
    if (!block.type.isConvertible) return

    val targetType = if (block.type.matchesToolbarListType(requestedType)) {
        BlockType.Paragraph
    } else {
        requestedType
    }
    callbacks.dispatch(ConvertBlockType(block.id, targetType))
}

private fun BlockType.matchesToolbarListType(other: BlockType): Boolean =
    (this == BlockType.BulletList && other == BlockType.BulletList) ||
        (this is BlockType.NumberedList && other is BlockType.NumberedList)
