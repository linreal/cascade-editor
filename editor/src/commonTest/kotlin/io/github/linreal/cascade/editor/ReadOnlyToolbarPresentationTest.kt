package io.github.linreal.cascade.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.createToolbarSlashInsertAction
import io.github.linreal.cascade.editor.ui.isToolbarSlashInsertEnabled
import io.github.linreal.cascade.editor.ui.visibleSelection
import io.github.linreal.cascade.editor.ui.visibleText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadOnlyToolbarPresentationTest {

    @Test
    fun `toolbar slash button is disabled when slash subsystem is disabled`() {
        assertFalse(
            isToolbarSlashInsertEnabled(
                slashEnabled = false,
                policy = EditorInteractionPolicy.Editable,
            )
        )
    }

    @Test
    fun `toolbar slash button is disabled when text editing is unavailable`() {
        assertFalse(
            isToolbarSlashInsertEnabled(
                slashEnabled = true,
                policy = EditorInteractionPolicy.ReadOnly,
            )
        )
    }

    @Test
    fun `toolbar slash button is enabled when slash and text editing are available`() {
        assertTrue(
            isToolbarSlashInsertEnabled(
                slashEnabled = true,
                policy = EditorInteractionPolicy.Editable,
            )
        )
    }

    @Test
    fun `disabled toolbar slash action does not edit focused text field`() {
        val blockId = BlockId("b1")
        val textStates = BlockTextStates()
        val textFieldState = textStates.getOrCreate(blockId, "abc", initialCursorPosition = 1)
        val action = createToolbarSlashInsertAction(
            slashEnabledProvider = { false },
            formattingState = mutableStateOf(formattingState(blockId, canFormat = true)),
            textStates = textStates,
        )

        action()

        assertEquals("abc", textFieldState.visibleText())
        assertEquals(TextRange(1), textFieldState.visibleSelection())
    }

    @Test
    fun `enabled toolbar slash action inserts slash at visible selection`() {
        val blockId = BlockId("b1")
        val textStates = BlockTextStates()
        val textFieldState = textStates.getOrCreate(blockId, "abc", initialCursorPosition = 1)
        val action = createToolbarSlashInsertAction(
            slashEnabledProvider = { true },
            formattingState = mutableStateOf(formattingState(blockId, canFormat = false)),
            textStates = textStates,
        )

        action()

        assertEquals("a/bc", textFieldState.visibleText())
        assertEquals(TextRange(2), textFieldState.visibleSelection())
    }

    private fun formattingState(
        blockId: BlockId,
        canFormat: Boolean,
    ): FormattingState {
        return FormattingState(
            styles = emptyMap(),
            canFormat = canFormat,
            focusedBlockId = blockId,
            selectionCollapsed = true,
        )
    }
}
