package io.github.linreal.cascade.editor.ui.renderers

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmptyDocumentPlaceholderTest {

    private val paragraph = Block.paragraph().copy(id = BlockId("paragraph"))

    @Test
    fun `editable enabled root paragraph is eligible`() {
        assertTrue(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = true,
                blocks = listOf(paragraph),
                blockId = paragraph.id,
            )
        )
    }

    @Test
    fun `editable enabled root heading is eligible`() {
        val heading = Block.heading(level = 1).copy(id = BlockId("heading"))

        assertTrue(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = true,
                blocks = listOf(heading),
                blockId = heading.id,
            )
        )
    }

    @Test
    fun `disabled or read-only editor is not eligible`() {
        assertFalse(
            isEmptyDocumentPlaceholderCandidate(
                enabled = false,
                canEditText = true,
                blocks = listOf(paragraph),
                blockId = paragraph.id,
            )
        )
        assertFalse(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = false,
                blocks = listOf(paragraph),
                blockId = paragraph.id,
            )
        )
    }

    @Test
    fun `non-empty document structure is not eligible`() {
        assertFalse(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = true,
                blocks = listOf(paragraph, Block.paragraph()),
                blockId = paragraph.id,
            )
        )
        assertFalse(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = true,
                blocks = listOf(Block.divider(), paragraph),
                blockId = paragraph.id,
            )
        )
    }

    @Test
    fun `unsupported text block or indented first block is not eligible`() {
        val todo = Block.todo().copy(id = paragraph.id)
        val indented = paragraph.copy(
            attributes = BlockAttributes(indentationLevel = 1),
        )

        assertFalse(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = true,
                blocks = listOf(todo),
                blockId = todo.id,
            )
        )
        assertFalse(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = true,
                blocks = listOf(indented),
                blockId = indented.id,
            )
        )
    }

    @Test
    fun `candidate must be the only block id`() {
        assertFalse(
            isEmptyDocumentPlaceholderCandidate(
                enabled = true,
                canEditText = true,
                blocks = listOf(paragraph),
                blockId = BlockId("other"),
            )
        )
    }
}
