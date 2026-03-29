package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.visibleSelection

/**
 * Default implementation of [FormattingActions] that resolves the focused block
 * and visible selection from runtime state **at invocation time** and delegates
 * to [SpanActionDispatcher].
 *
 * No-op when formatting is disallowed (no focus, Code block, block selection
 * active, dragging).
 */
@Stable
internal class DefaultFormattingActions(
    private val stateHolder: EditorStateHolder,
    private val textStates: BlockTextStates,
    private val spanActionDispatcher: SpanActionDispatcher,
) : FormattingActions {

    override fun toggleStyle(style: SpanStyle) {
        val (blockId, start, end) = resolveContext() ?: return
        spanActionDispatcher.toggleStyle(blockId, start, end, style)
    }

    override fun applyStyle(style: SpanStyle) {
        val (blockId, start, end) = resolveContext() ?: return
        spanActionDispatcher.applyStyle(blockId, start, end, style)
    }

    override fun removeStyle(style: SpanStyle) {
        val (blockId, start, end) = resolveContext() ?: return
        spanActionDispatcher.removeStyle(blockId, start, end, style)
    }

    /**
     * Resolves current formatting context from runtime state.
     * Returns null if formatting is disallowed.
     */
    private fun resolveContext(): FormattingContext? {
        val state = stateHolder.state
        val blockId = state.focusedBlockId ?: return null
        val block = state.getBlock(blockId) ?: return null
        val blockType = block.type

        if (!blockType.supportsText) return null
        if (state.selectedBlockIds.isNotEmpty()) return null
        if (state.dragState != null) return null

        val textFieldState = textStates.get(blockId) ?: return null
        val sel = textFieldState.visibleSelection()

        return FormattingContext(
            blockId = blockId,
            selectionStart = sel.start,
            selectionEnd = sel.end,
        )
    }
}

private data class FormattingContext(
    val blockId: BlockId,
    val selectionStart: Int,
    val selectionEnd: Int,
)
