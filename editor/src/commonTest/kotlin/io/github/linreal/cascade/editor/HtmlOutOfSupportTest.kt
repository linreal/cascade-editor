package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlOutOfSupportTest {

    @Test
    fun `default profile todo degrades through paragraph fallback`() {
        val block = Block.todo("Task", checked = true)

        assertFalse(HtmlProfile.Default.supportSet.supportsDocument(listOf(block)))

        val result = HtmlSchema.decodeWithReport(
            HtmlSchema.encode(listOf(block), HtmlProfile.Default),
            HtmlProfile.Default,
        )

        val decoded = result.blocks.single()
        assertEquals(BlockType.Paragraph, decoded.type)
        assertEquals("Task", assertTextContent(decoded).text)
        assertEquals(BlockAttributes.MIN_INDENTATION_LEVEL, decoded.attributes.indentationLevel)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `default profile custom block without encoder degrades through paragraph fallback`() {
        val block = Block(
            id = BlockId.generate(),
            type = OutOfSupportBlockType,
            content = BlockContent.Text("Custom"),
        )

        assertFalse(HtmlProfile.Default.supportSet.supportsDocument(listOf(block)))

        val result = HtmlSchema.decodeWithReport(
            HtmlSchema.encode(listOf(block), HtmlProfile.Default),
            HtmlProfile.Default,
        )

        val decoded = result.blocks.single()
        assertEquals(BlockType.Paragraph, decoded.type)
        assertEquals("Custom", assertTextContent(decoded).text)
        assertTrue(result.warnings.isEmpty())
    }

    private fun assertTextContent(block: Block): BlockContent.Text = assertIs<BlockContent.Text>(block.content)

    private object OutOfSupportBlockType : CustomBlockType {
        override val typeId: String = "test.out_of_support"
        override val displayName: String = "Out of support"
        override val supportsText: Boolean = true
    }
}
