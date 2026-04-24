package io.github.linreal.cascade.editor

import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.theme.CascadeEditorDimensions
import io.github.linreal.cascade.editor.ui.renderers.blockIndentationInset
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockIndentationModifierTest {

    private val dimensions = CascadeEditorDimensions(
        indentUnit = 24.dp,
        blockHorizontalPadding = 16.dp,
    )

    @Test
    fun `paragraph depth uses depth multiplied by indent unit`() {
        val block = Block.paragraph("Nested").withAttributes(BlockAttributes(indentationLevel = 2))

        assertEquals(48.dp, blockIndentationInset(block, dimensions))
    }

    @Test
    fun `todo depth uses the same indent unit`() {
        val block = Block.todo("Nested").withAttributes(BlockAttributes(indentationLevel = 1))

        assertEquals(24.dp, blockIndentationInset(block, dimensions))
    }

    @Test
    fun `unsupported blocks ignore hidden indentation`() {
        val block = Block.heading(level = 1, text = "Heading")
            .withAttributes(BlockAttributes(indentationLevel = 2))

        assertEquals(0.dp, blockIndentationInset(block, dimensions))
    }
}
