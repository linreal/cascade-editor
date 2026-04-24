package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.IndentBackward
import io.github.linreal.cascade.editor.action.IndentForward
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.indentation.DefaultIndentationActions
import io.github.linreal.cascade.editor.indentation.IndentationState
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
}
