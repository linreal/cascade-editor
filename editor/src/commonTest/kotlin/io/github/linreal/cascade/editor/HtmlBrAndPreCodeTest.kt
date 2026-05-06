package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlBrAndPreCodeTest {

    @Test
    fun `br inside text content decodes as newline`() {
        val block = HtmlSchema.decode("<p>a<br>b</p>", HtmlProfile.Default).single()

        assertEquals("a\nb", assertTextContent(block).text)
    }

    @Test
    fun `bare root br is dropped with content warning`() {
        val result = HtmlSchema.decodeWithReport("<br>", HtmlProfile.Default)

        assertTrue(result.blocks.isEmpty())
        assertIs<HtmlDecodeWarning.DroppedContent>(result.warnings.single())
    }

    @Test
    fun `pre code decodes as one plain code block without inline code span`() {
        val block = HtmlSchema.decode("<pre><code>line 1\nline 2\n</code></pre>", HtmlProfile.Default).single()
        val content = assertTextContent(block)

        assertEquals(BlockType.Code, block.type)
        assertEquals("line 1\nline 2", content.text)
        assertTrue(content.spans.isEmpty())
    }

    @Test
    fun `code outside pre decodes as inline code span`() {
        val block = HtmlSchema.decode("<p>a<code>b</code>c</p>", HtmlProfile.Default).single()
        val content = assertTextContent(block)

        assertEquals("abc", content.text)
        assertEquals(listOf(TextSpan(1, 2, SpanStyle.InlineCode)), content.spans)
    }

    private fun assertTextContent(block: io.github.linreal.cascade.editor.core.Block): BlockContent.Text =
        assertIs<BlockContent.Text>(block.content)
}
