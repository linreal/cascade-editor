package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.toInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorInteractionPolicyTest {

    @Test
    fun `editable config enables every policy capability`() {
        val policy = CascadeEditorConfig(readOnly = false).toInteractionPolicy()

        assertEquals(EditorInteractionPolicy.Editable, policy)
        assertAllCapabilities(policy, expected = true)
    }

    @Test
    fun `read only config disables every policy capability`() {
        val policy = CascadeEditorConfig(readOnly = true).toInteractionPolicy()

        assertEquals(EditorInteractionPolicy.ReadOnly, policy)
        assertAllCapabilities(policy, expected = false)
        assertFalse(policy.canSelectBlocks)
        assertFalse(policy.canDragBlocks)
        assertFalse(policy.canChangeBlockIndentation)
    }

    @Test
    fun `editable config can disable block selection only`() {
        val policy = CascadeEditorConfig(
            readOnly = false,
            blockSelectionEnabled = false,
            blockDraggingEnabled = true,
        ).toInteractionPolicy()

        assertTrue(policy.canEditText)
        assertTrue(policy.canEditBlockStructure)
        assertTrue(policy.canEditBlockControls)
        assertTrue(policy.canFormatText)
        assertTrue(policy.canEditLinks)
        assertTrue(policy.canUseEditorHistoryShortcuts)
        assertTrue(policy.canUseSlashCommands)
        assertFalse(policy.canSelectBlocks)
        assertTrue(policy.canDragBlocks)
    }

    @Test
    fun `editable config can disable block dragging only`() {
        val policy = CascadeEditorConfig(
            readOnly = false,
            blockSelectionEnabled = true,
            blockDraggingEnabled = false,
        ).toInteractionPolicy()

        assertTrue(policy.canEditText)
        assertTrue(policy.canEditBlockStructure)
        assertTrue(policy.canEditBlockControls)
        assertTrue(policy.canFormatText)
        assertTrue(policy.canEditLinks)
        assertTrue(policy.canUseEditorHistoryShortcuts)
        assertTrue(policy.canUseSlashCommands)
        assertTrue(policy.canSelectBlocks)
        assertFalse(policy.canDragBlocks)
    }

    @Test
    fun `read only config overrides block selection and dragging flags`() {
        val policy = CascadeEditorConfig(
            readOnly = true,
            blockSelectionEnabled = true,
            blockDraggingEnabled = true,
        ).toInteractionPolicy()

        assertEquals(EditorInteractionPolicy.ReadOnly, policy)
        assertFalse(policy.canSelectBlocks)
        assertFalse(policy.canDragBlocks)
    }

    @Test
    fun `editable config can disable block indentation without disabling dragging`() {
        val policy = CascadeEditorConfig(
            readOnly = false,
            blockDraggingEnabled = true,
            blockIndentationEnabled = false,
        ).toInteractionPolicy()

        assertTrue(policy.canEditBlockStructure)
        assertTrue(policy.canDragBlocks)
        assertFalse(policy.canChangeBlockIndentation)
    }

    private fun assertAllCapabilities(policy: EditorInteractionPolicy, expected: Boolean) {
        val assertions = listOf(
            policy.canEditText,
            policy.canEditBlockStructure,
            policy.canEditBlockControls,
            policy.canFormatText,
            policy.canEditLinks,
            policy.canUseEditorHistoryShortcuts,
            policy.canUseSlashCommands,
            policy.canSelectBlocks,
            policy.canDragBlocks,
            policy.canChangeBlockIndentation,
        )

        assertions.forEach { actual ->
            if (expected) {
                assertTrue(actual)
            } else {
                assertFalse(actual)
            }
        }
    }
}
