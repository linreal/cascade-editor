package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.IndentBackward
import io.github.linreal.cascade.editor.action.IndentForward
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.indentation.DefaultIndentationActions
import io.github.linreal.cascade.editor.indentation.IndentationState
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultIndentationActionsTest {

    private val targetId = BlockId("target")

    @Test
    fun `indentForward dispatches one structural indent action for legal target`() {
        val dispatchedActions = mutableListOf<EditorAction>()
        val actions = DefaultIndentationActions(
            stateProvider = {
                IndentationState(
                    canIndentForward = true,
                    canIndentBackward = false,
                    targetBlockIds = listOf(targetId),
                )
            },
            dispatchAction = dispatchedActions::add,
            policy = EditorInteractionPolicy.Editable,
        )

        actions.indentForward()

        assertEquals<List<EditorAction>>(listOf(IndentForward), dispatchedActions)
    }

    @Test
    fun `indentBackward dispatches one structural indent action for legal target`() {
        val dispatchedActions = mutableListOf<EditorAction>()
        val actions = DefaultIndentationActions(
            stateProvider = {
                IndentationState(
                    canIndentForward = false,
                    canIndentBackward = true,
                    targetBlockIds = listOf(targetId),
                )
            },
            dispatchAction = dispatchedActions::add,
            policy = EditorInteractionPolicy.Editable,
        )

        actions.indentBackward()

        assertEquals<List<EditorAction>>(listOf(IndentBackward), dispatchedActions)
    }

    @Test
    fun `indentForward no-ops when current state disallows it`() {
        val dispatchedActions = mutableListOf<EditorAction>()
        val actions = DefaultIndentationActions(
            stateProvider = { IndentationState.Empty },
            dispatchAction = dispatchedActions::add,
            policy = EditorInteractionPolicy.Editable,
        )

        actions.indentForward()

        assertTrue(dispatchedActions.isEmpty())
    }

    @Test
    fun `indentBackward no-ops when current state disallows it`() {
        val dispatchedActions = mutableListOf<EditorAction>()
        val actions = DefaultIndentationActions(
            stateProvider = { IndentationState.Empty },
            dispatchAction = dispatchedActions::add,
            policy = EditorInteractionPolicy.Editable,
        )

        actions.indentBackward()

        assertTrue(dispatchedActions.isEmpty())
    }

    @Test
    fun `actions resolve state at invocation time`() {
        val dispatchedActions = mutableListOf<EditorAction>()
        var currentState = IndentationState.Empty
        val actions = DefaultIndentationActions(
            stateProvider = { currentState },
            dispatchAction = dispatchedActions::add,
            policy = EditorInteractionPolicy.Editable,
        )

        actions.indentForward()
        currentState = IndentationState(
            canIndentForward = true,
            canIndentBackward = false,
            targetBlockIds = listOf(targetId),
        )
        actions.indentForward()

        assertEquals<List<EditorAction>>(listOf(IndentForward), dispatchedActions)
    }

    @Test
    fun `read-only policy blocks indentation dispatch even when state is enabled`() {
        val dispatchedActions = mutableListOf<EditorAction>()
        val enabledState = IndentationState(
            canIndentForward = true,
            canIndentBackward = true,
            targetBlockIds = listOf(targetId),
        )
        val actions = DefaultIndentationActions(
            stateProvider = { enabledState },
            dispatchAction = dispatchedActions::add,
            policy = EditorInteractionPolicy.ReadOnly,
        )

        actions.indentForward()
        actions.indentBackward()

        assertTrue(dispatchedActions.isEmpty())
    }
}
