package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.visibleSelection

/**
 * Remembers reactive link state derived from editor runtime state.
 *
 * The returned [State] is backed by [derivedStateOf], so link target resolution
 * runs only when a consumer reads [State.value] and one of the focused-block
 * inputs has changed.
 */
@Composable
internal fun rememberLinkState(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    policy: EditorInteractionPolicy,
): State<LinkState> {
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

    return remember(stateHolder, textStates, spanStates, policy) {
        derivedStateOf {
            val blockId = focusedBlockId.value
            val blockType = focusedBlockType.value

            if (blockId == null) {
                // Calculator returns LinkState.Empty for null blockId; short-circuit
                // here so we also skip reading hasBlockSelection / isDragging when no
                // block is focused, keeping derivedStateOf invalidations narrower.
                return@derivedStateOf LinkState.Empty
            }

            val textFieldState = textStates.get(blockId)
                ?: return@derivedStateOf LinkState.Empty.copy(focusedBlockId = blockId)
            val visibleText = textStates.getVisibleText(blockId).orEmpty()
            val selection = textFieldState.visibleSelection()
            val spans = spanStates.getSpans(blockId)

            LinkStateCalculator.compute(
                focusedBlockId = blockId,
                focusedBlockType = blockType,
                hasBlockSelection = hasBlockSelection.value,
                isDragging = isDragging.value,
                visibleText = visibleText,
                visibleSelectionStart = selection.start,
                visibleSelectionEnd = selection.end,
                spans = spans,
                policy = policy,
            )
        }
    }
}
