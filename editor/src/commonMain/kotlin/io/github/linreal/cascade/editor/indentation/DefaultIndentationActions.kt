package io.github.linreal.cascade.editor.indentation

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.IndentBackward
import io.github.linreal.cascade.editor.action.IndentForward
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy

/**
 * Default [IndentationActions] implementation used by [io.github.linreal.cascade.editor.ui.CascadeEditor].
 *
 * The state provider is evaluated at invocation time so toolbar buttons and
 * keyboard/custom chrome cannot dispatch stale indentation commands after focus,
 * selection, or document structure changes.
 */
@Stable
internal class DefaultIndentationActions(
    private val stateProvider: () -> IndentationState,
    private val dispatchAction: (EditorAction) -> Unit,
    private val policyProvider: () -> EditorInteractionPolicy,
) : IndentationActions {

    constructor(
        stateProvider: () -> IndentationState,
        dispatchAction: (EditorAction) -> Unit,
        policy: EditorInteractionPolicy,
    ) : this(
        stateProvider = stateProvider,
        dispatchAction = dispatchAction,
        policyProvider = { policy },
    )

    override fun indentForward() {
        val policy = policyProvider()
        if (!policy.canEditBlockStructure) return
        if (!stateProvider().canIndentForward) return
        dispatchAction(IndentForward)
    }

    override fun indentBackward() {
        val policy = policyProvider()
        if (!policy.canEditBlockStructure) return
        if (!stateProvider().canIndentBackward) return
        dispatchAction(IndentBackward)
    }
}
