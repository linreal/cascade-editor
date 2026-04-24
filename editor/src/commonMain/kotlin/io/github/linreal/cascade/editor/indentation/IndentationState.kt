package io.github.linreal.cascade.editor.indentation

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.core.BlockId

/**
 * Immutable indentation command state for the current focus or block selection.
 *
 * [targetBlockIds] is ordered for consumers that mirror the command target in
 * document chrome. It contains only supported root targets, not descendants that
 * would move as part of those roots' flat-outline subtrees.
 */
@Immutable
public data class IndentationState(
    val canIndentForward: Boolean,
    val canIndentBackward: Boolean,
    val targetBlockIds: List<BlockId>,
) {
    public companion object {
        /**
         * State used when no supported indentation target is available.
         */
        public val Empty: IndentationState = IndentationState(
            canIndentForward = false,
            canIndentBackward = false,
            targetBlockIds = emptyList(),
        )
    }
}
