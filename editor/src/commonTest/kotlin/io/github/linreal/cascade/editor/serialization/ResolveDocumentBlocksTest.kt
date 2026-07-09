package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class ResolveDocumentBlocksTest {

    @Test
    fun resolveDocumentBlocks_preservesSnapshotIdentity_whenNoRuntimeEdits() {
        val holder = EditorStateHolder(EditorState.withBlocks(listOf(Block.paragraph("Hello"))))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val resolved = holder.resolveDocumentBlocks(textStates, spanStates)

        // Blocks with no live runtime edits keep their original object identity, so
        // structural equality over consecutive results stays cheap.
        assertSame(holder.state.blocks.first(), resolved.first())
    }

    @Test
    fun resolveDocumentBlocks_reflectsRuntimeTextEdits() {
        val holder = EditorStateHolder(EditorState.withBlocks(listOf(Block.paragraph("Hello"))))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val blockId = holder.state.blocks.first().id
        textStates.getOrCreate(blockId, "Hello")

        val before = holder.resolveDocumentBlocks(textStates, spanStates)
        textStates.setText(blockId, "Hello world")
        val after = holder.resolveDocumentBlocks(textStates, spanStates)

        assertEquals("Hello", (before.first().content as BlockContent.Text).text)
        assertEquals("Hello world", (after.first().content as BlockContent.Text).text)
        assertNotEquals(before, after)
    }
}
