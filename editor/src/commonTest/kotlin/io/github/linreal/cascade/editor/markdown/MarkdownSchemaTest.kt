package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownSchemaTest {

    @Test
    fun `decode empty input yields non-null empty success`() {
        val result = MarkdownSchema.decodeWithReport("", MarkdownProfile.Default)
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.blocks)
        assertEquals(emptyList<Block>(), MarkdownSchema.decode("", MarkdownProfile.Default))
    }

    @Test
    fun `encode empty document yields non-null empty success`() {
        val result = MarkdownSchema.encodeWithReport(emptyList(), MarkdownProfile.Default)
        assertTrue(result.isSuccess)
        assertEquals("", result.markdown)
    }

    @Test
    fun `decode then encode of a heading is canonical`() {
        val blocks = MarkdownSchema.decode("## Title", MarkdownProfile.Default)
        assertNotNull(blocks)
        assertEquals(1, blocks.size)
        assertEquals(BlockType.Heading(2), blocks.single().type)
        assertEquals("## Title\n", MarkdownSchema.encode(blocks, MarkdownProfile.Default))
    }

    @Test
    fun `over-limit input aborts convenience null and report aborted`() {
        val limits = MarkdownCodecLimits(maxInputChars = 4)
        assertNull(MarkdownSchema.decode("longer than four", MarkdownProfile.Default, limits))
        val report = MarkdownSchema.decodeWithReport("longer than four", MarkdownProfile.Default, limits)
        assertTrue(report.isAborted)
        assertNull(report.blocks)
        assertTrue(report.warnings.any { it.impact == MarkdownFidelityImpact.Fatal })
    }

    @Test
    fun `over-limit output aborts encode`() {
        val blocks = listOf(Block.paragraph("a fairly long paragraph of text"))
        val limits = MarkdownCodecLimits(maxOutputChars = 4)
        assertNull(MarkdownSchema.encode(blocks, MarkdownProfile.Default, limits))
        val report = MarkdownSchema.encodeWithReport(blocks, MarkdownProfile.Default, limits)
        assertTrue(report.isAborted)
        assertNull(report.markdown)
    }

    @Test
    fun `crlf line ending option applies`() {
        val blocks = listOf(Block.paragraph("a"), Block.paragraph("b"))
        val out = MarkdownSchema.encode(blocks, MarkdownProfile.Default, lineEnding = MarkdownLineEnding.CrLf)
        assertEquals("a\r\n\r\nb\r\n", out)
    }

    @Test
    fun `throwing consumer encoder never crosses the public boundary`() {
        val profile = MarkdownProfile.Default.withMarkdownBlockEncoder<BlockType.Paragraph>(
            { _, _, _ -> throw IllegalStateException("intentional") },
        )
        val result = MarkdownSchema.encodeWithReport(listOf(Block.paragraph("x")), profile)
        // The encoder threw and degraded through the fallback; no crash.
        assertTrue(result.isSuccess)
        assertTrue(result.warnings.any { it is MarkdownEncodeWarning.EncoderException })
    }

    @Test
    fun `custom content is not unconditionally rejected by supportsBlock`() {
        // A custom support set may claim a custom block type.
        val block = Block(
            id = io.github.linreal.cascade.editor.core.BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Custom("widget"),
        )
        val claiming = MarkdownProfileSupportSet(
            supportsBlockPredicate = { it.content is BlockContent.Custom },
            supportsSpanPredicate = { false },
            supportsDocumentPredicate = { blocks -> blocks.all { it.content is BlockContent.Custom } },
        )
        assertTrue(claiming.supportsBlock(block))
        assertTrue(claiming.supportsDocument(listOf(block)))
    }
}
