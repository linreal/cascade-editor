package io.github.linreal.cascade.screens.external_toolbar

import io.github.linreal.cascade.editor.core.BlockContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExternalToolbarScreenModelTest {

    @Test
    fun `draft snapshot restores live typed text after screen recreation`() {
        val model = ExternalToolbarScreenModel()
        val editedBlock = model.editorState.state.blocks.first {
            it.content is BlockContent.Text
        }
        val originalText = assertIs<BlockContent.Text>(editedBlock.content).text

        model.textStates.getOrCreate(editedBlock.id, originalText)
        model.textStates.setText(editedBlock.id, "Edited text survives recreation")

        val draftJson = model.toDraftJson()
        val recreated = ExternalToolbarScreenModel(initialDraftJson = draftJson)
        val restoredBlock = recreated.editorState.state.getBlock(editedBlock.id)
        val restoredText = assertIs<BlockContent.Text>(restoredBlock?.content).text

        assertEquals("Edited text survives recreation", restoredText)
    }
}
