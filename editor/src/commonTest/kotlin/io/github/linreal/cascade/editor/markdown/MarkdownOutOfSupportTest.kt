package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Directed out-of-support behavior: documents outside the support
 * set never throw, emit honest warnings, and degrade deterministically.
 */
class MarkdownOutOfSupportTest {

    @Test
    fun `empty paragraph encodes deterministically with a warning`() {
        val doc = listOf(Block.paragraph("keep"), Block.paragraph(""))
        val a = MarkdownSchema.encodeWithReport(doc, MarkdownProfile.Default)
        val b = MarkdownSchema.encodeWithReport(doc, MarkdownProfile.Default)
        assertTrue(a.isSuccess)
        assertEquals(a.markdown, b.markdown)
        assertFalse(MarkdownProfile.Default.supportSet.supportsDocument(doc))
    }

    @Test
    fun `unsupported custom span keeps text and warns`() {
        val doc = listOf(
            Block.paragraph("spoiler here", spans = listOf(TextSpan(0, 7, SpanStyle.Custom("spoiler")))),
        )
        val result = MarkdownSchema.encodeWithReport(doc, MarkdownProfile.Default)
        assertTrue(result.isSuccess)
        assertNotNull(result.markdown)
        assertTrue(result.markdown!!.contains("spoiler"))
        assertTrue(result.warnings.any { it is MarkdownEncodeWarning.UnsupportedSpan })
    }

    @Test
    fun `pipe table preserved opaquely under default Preserve policy`() {
        val table = "| a | b |\n| - | - |\n| 1 | 2 |"
        val result = MarkdownSchema.decodeWithReport(table, MarkdownProfile.Default)
        assertTrue(result.isSuccess)
        val block = result.blocks!!.single()
        val content = block.content
        assertTrue(content is BlockContent.Custom && content.typeId == MARKDOWN_PRESERVED_TYPE_ID)
        assertTrue(result.warnings.any { it is MarkdownDecodeWarning.PreservedSyntax })
    }

    @Test
    fun `out-of-support document is not claimed but still round-trips its preserved slice`() {
        val table = "| a | b |\n| - | - |"
        val decoded = MarkdownSchema.decode(table, MarkdownProfile.Default)!!
        assertFalse(MarkdownProfile.Default.supportSet.supportsDocument(decoded))
        val reencoded = MarkdownSchema.encode(decoded, MarkdownProfile.Default)
        assertNotNull(reencoded)
        assertTrue(reencoded!!.contains("| a | b |"))
    }
}
