package io.github.linreal.cascade.editor.indentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.IndentationDirection
import io.github.linreal.cascade.editor.core.canShiftIndentation
import io.github.linreal.cascade.editor.core.resolveIndentationTargetRootIndices
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy

/**
 * Pure calculator for public indentation command state.
 */
internal object IndentationStateCalculator {

    /**
     * Computes the indentation capabilities for [state] using the same target
     * resolution and outline validation as the reducer actions.
     */
    internal fun compute(
        state: EditorState,
        policy: EditorInteractionPolicy = EditorInteractionPolicy.Editable,
    ): IndentationState {
        return compute(
            blocks = state.blocks,
            focusedBlockId = state.focusedBlockId,
            selectedBlockIds = state.selectedBlockIds,
            policy = policy,
        )
    }

    /**
     * Computes from only the editor fields that affect indentation capabilities.
     *
     * [policy] disables mutation-facing booleans after target resolution so
     * custom chrome can still inspect the current indentation target.
     */
    internal fun compute(
        blocks: List<Block>,
        focusedBlockId: BlockId?,
        selectedBlockIds: Set<BlockId>,
        policy: EditorInteractionPolicy = EditorInteractionPolicy.Editable,
    ): IndentationState {
        val rootIndices = resolveIndentationTargetRootIndices(
            blocks = blocks,
            focusedBlockId = focusedBlockId,
            selectedBlockIds = selectedBlockIds,
        )
        if (rootIndices.isEmpty()) return IndentationState.Empty

        val targetBlockIds = rootIndices.map { index -> blocks[index].id }
        if (!policy.canChangeBlockIndentation) {
            return IndentationState(
                canIndentForward = false,
                canIndentBackward = false,
                targetBlockIds = targetBlockIds,
            )
        }

        val canIndentForward = canShiftIndentation(
            blocks = blocks,
            targetRootIndices = rootIndices,
            direction = IndentationDirection.Forward,
        )
        val canIndentBackward = canShiftIndentation(
            blocks = blocks,
            targetRootIndices = rootIndices,
            direction = IndentationDirection.Backward,
        )

        return IndentationState(
            canIndentForward = canIndentForward,
            canIndentBackward = canIndentBackward,
            targetBlockIds = targetBlockIds,
        )
    }
}

/**
 * Remembers reactive indentation state derived from [stateHolder].
 */
@Composable
internal fun rememberIndentationState(
    stateHolder: EditorStateHolder,
    policy: EditorInteractionPolicy,
): State<IndentationState> {
    // Split high-churn EditorState into stable inputs so drag target updates do
    // not force outline validation work when indentation-relevant fields are unchanged.
    val blocks = remember(stateHolder) {
        derivedStateOf { stateHolder.state.blocks }
    }
    val focusedBlockId = remember(stateHolder) {
        derivedStateOf { stateHolder.state.focusedBlockId }
    }
    val selectedBlockIds = remember(stateHolder) {
        derivedStateOf { stateHolder.state.selectedBlockIds }
    }

    return remember(stateHolder, policy) {
        derivedStateOf {
            IndentationStateCalculator.compute(
                blocks = blocks.value,
                focusedBlockId = focusedBlockId.value,
                selectedBlockIds = selectedBlockIds.value,
                policy = policy,
            )
        }
    }
}
