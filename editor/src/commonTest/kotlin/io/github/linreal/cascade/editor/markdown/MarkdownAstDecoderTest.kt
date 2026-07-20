package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarkdownAstDecoderTest {
    @Test
    fun `parser projection handles bom crlf and lone cr`() {
        val blocks = MarkdownSchema.decode("\uFEFF# first\r\n\rsecond")!!

        assertEquals(listOf(BlockType.Heading(1), BlockType.Paragraph), blocks.map { it.type })
        assertEquals("first", (blocks[0].content as BlockContent.Text).text)
        assertEquals("second", (blocks[1].content as BlockContent.Text).text)
    }

    @Test
    fun `preserved slices retain original crlf bytes and omit bom`() {
        val markdown = "\uFEFF---\r\nname: cascade\r\n---\r\n"
        val result = MarkdownSchema.decodeWithReport(markdown)
        val content = assertIs<BlockContent.Custom>(result.blocks!!.single().content)

        assertEquals("---\r\nname: cascade\r\n---", content.data["rawMarkdown"])
        val warning = assertIs<MarkdownDecodeWarning.PreservedSyntax>(result.warnings.single())
        assertEquals(1, warning.range.start)
        assertEquals(markdown.length - 2, warning.range.endExclusive)
    }

    @Test
    fun `unsupported range maps through crlf to original utf16 offsets`() {
        val markdown = "line\r\n\$x\$"
        val profile = MarkdownProfile.Default.withUnsupportedSyntax(UnsupportedSyntax.WarnAndDegrade)
        val warning = MarkdownSchema.decodeWithReport(markdown, profile).warnings
            .filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
            .single()

        assertEquals(markdown.indexOf('$'), warning.range.start)
        assertEquals(markdown.lastIndexOf('$') + 1, warning.range.endExclusive)
    }
}
