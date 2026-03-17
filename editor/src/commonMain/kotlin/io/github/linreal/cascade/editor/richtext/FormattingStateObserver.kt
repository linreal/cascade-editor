package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.visibleSelection

/**
 * Composable bridge that produces a reactive [State]<[FormattingState]> from
 * editor runtime state. Uses chained [derivedStateOf] to minimize recomputation:
 *
 * **Layer 1** — EditorState-derived values (focusedBlockId, block type, block
 * selection, drag state). Each re-evaluates on every [EditorStateHolder] change
 * but only propagates when its specific output changes. This shields the final
 * derivation from irrelevant state churn (drag position updates, block
 * reordering, etc.).
 *
 * **Layer 2** — Final [FormattingState]. Only re-evaluates when Layer 1 outputs
 * change OR per-block snapshot state (selection, spans, pending styles) changes.
 * Output uses structural equality so cursor movement within the same style
 * region does NOT cause downstream recomposition.
 */
@Composable
internal fun rememberFormattingState(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    trackedStyles: List<SpanStyle>,
): State<FormattingState> {

 // Layer 1: EditorState-derived values
    // Each re-evaluates on every EditorState dispatch but only propagates
    // when its specific output changes. Prevents the final derivation from
    // running on drag-position updates, block text changes, etc.

    val focusedBlockId = remember(stateHolder) {
        derivedStateOf { stateHolder.state.focusedBlockId }
    }

    val focusedBlockType = remember(stateHolder) {
        derivedStateOf {
            val state = stateHolder.state
            state.focusedBlockId?.let { id -> state.getBlock(id)?.type }
        }
    }

    val hasBlockSelection = remember(stateHolder) {
        derivedStateOf { stateHolder.state.selectedBlockIds.isNotEmpty() }
    }

    val isDragging = remember(stateHolder) {
        derivedStateOf { stateHolder.state.dragState != null }
    }

 // Layer 2: Final formatting state
    // Reads Layer 1 outputs + per-block snapshot state (selection, spans,
    // pending styles). Only the focused block's TextFieldState and span
    // state are read, so cursor/span changes in non-focused blocks are
    // invisible to this derivation.

    return remember(stateHolder, textStates, spanStates, trackedStyles) {
        derivedStateOf {
            val blockId = focusedBlockId.value
            val blockType = focusedBlockType.value
            val blockSelection = hasBlockSelection.value
            val dragging = isDragging.value

            // Per-block reads — only executed when a block is focused.
            val textFieldState = blockId?.let { textStates.get(it) }
            val sel = textFieldState?.visibleSelection() ?: TextRange(0, 0)

            val spans = blockId?.let { spanStates.getSpans(it) } ?: emptyList()
            val pendingStyles = blockId?.let { spanStates.getPendingStyles(it) }

            FormattingStateCalculator.compute(
                focusedBlockId = blockId,
                focusedBlockType = blockType,
                hasBlockSelection = blockSelection,
                isDragging = dragging,
                visibleSelectionStart = sel.start,
                visibleSelectionEnd = sel.end,
                spans = spans,
                pendingStyles = pendingStyles,
                trackedStyles = trackedStyles,
            )
        }
    }
}
