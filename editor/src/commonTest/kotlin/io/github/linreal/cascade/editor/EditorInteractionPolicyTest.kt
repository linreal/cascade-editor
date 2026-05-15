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
