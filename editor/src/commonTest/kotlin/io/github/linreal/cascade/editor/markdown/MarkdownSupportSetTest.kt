package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.UnknownBlockType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownSupportSetTest {

    private val support = MarkdownProfile.Default.supportSet

    @Test
    fun `supports plain built-in blocks and spans`() {
        assertTrue(support.supportsBlock(Block.paragraph("a")))
        assertTrue(support.supportsBlock(Block.heading(2, "a")))
        assertTrue(support.supportsBlock(Block.todo("a")))
        assertTrue(support.supportsSpan(SpanStyle.Bold))
        assertTrue(support.supportsSpan(SpanStyle.InlineCode))
    }

    @Test
    fun `rejects empty text block in CommonMark mode`() {
        assertFalse(support.supportsDocument(listOf(Block.paragraph(""))))
    }

    @Test
    fun `rejects preserved block`() {
        val preserved = Block(
            id = BlockId.generate(),
            type = UnknownBlockType(MARKDOWN_PRESERVED_TYPE_ID, "{\"typeId\":\"md.preserved\"}"),
            content = BlockContent.Custom(MARKDOWN_PRESERVED_TYPE_ID, mapOf("rawMarkdown" to "| a |")),
        )
        assertFalse(support.supportsBlock(preserved))
        assertFalse(support.supportsDocument(listOf(preserved)))
    }

    @Test
    fun `highlight claimed only within color range`() {
        assertTrue(support.supportsSpan(SpanStyle.Highlight(0x00000000L)))
        assertTrue(support.supportsSpan(SpanStyle.Highlight(0xFFFFFFFFL)))
        assertFalse(support.supportsSpan(SpanStyle.Highlight(-1L)))
        assertFalse(support.supportsSpan(SpanStyle.Highlight(0x1_0000_0000L)))
    }

    @Test
    fun `custom span excluded`() {
        assertFalse(support.supportsSpan(SpanStyle.Custom("spoiler")))
    }

    @Test
    fun `rejects stale numbering`() {
        val stale = listOf(
            Block.numberedList("a", number = 5),
            Block.numberedList("b", number = 6),
        )
        assertFalse(support.supportsDocument(stale))
    }

    @Test
    fun `rejects invalid indentation outline`() {
        val invalid = listOf(
            Block.paragraph("root"),
            Block.bulletList("too deep").withAttributes(BlockAttributes(indentationLevel = 3)),
        )
        assertFalse(support.supportsDocument(invalid))
    }

    @Test
    fun `rejects non-normalized spans`() {
        val block = Block.paragraph(
            "abcdef",
            spans = listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(2, 5, SpanStyle.Bold), // overlapping same-style: not normalized
            ),
        )
        assertFalse(support.supportsDocument(listOf(block)))
    }

    @Test
    fun `rejects nested links`() {
        val block = Block.paragraph(
            "linked text",
            spans = listOf(
                TextSpan(0, 11, SpanStyle.Link("https://a.example")),
                TextSpan(2, 6, SpanStyle.Link("https://b.example")),
            ),
        )
        assertFalse(support.supportsDocument(listOf(block)))
    }

    @Test
    fun `rejects value-based exclusions per section 5_12`() {
        // Trailing-space run: the inline phase trims it, so it never round-trips.
        assertFalse(support.supportsDocument(listOf(Block.paragraph("trailing   "))))
        // Leading spaces are stripped by the block phase.
        assertFalse(support.supportsDocument(listOf(Block.paragraph("   leading"))))
        // A tab at a syntax-sensitive (line-start) position expands to code indent.
        assertFalse(support.supportsDocument(listOf(Block.paragraph("\ttabbed"))))
    }

    @Test
    fun `rejects carriage returns in text`() {
        assertFalse(support.supportsDocument(listOf(Block.paragraph("a\rb"))))
        assertFalse(
            support.supportsDocument(
                listOf(Block(BlockId.generate(), BlockType.Code, BlockContent.Text("a\r\nb"))),
            ),
        )
    }

    @Test
    fun `hardbreak claim rejects merge-prone and front-matter shapes`() {
        val hb = MarkdownProfile.Default.withNewlineSemantics(NewlineSemantics.HardBreak).supportSet
        // Adjacent non-empty paragraphs merge on decode.
        assertFalse(hb.supportsDocument(listOf(Block.paragraph("a"), Block.paragraph("b"))))
        // ...but an empty paragraph between them is claimable.
        assertTrue(hb.supportsDocument(listOf(Block.paragraph("a"), Block.paragraph(""), Block.paragraph("b"))))
        // Leading divider + a later divider is captured by front matter.
        assertFalse(hb.supportsDocument(listOf(Block.divider(), Block.paragraph("x"), Block.divider())))
        // Containers are not claimed in HardBreak.
        assertFalse(hb.supportsDocument(listOf(Block.bulletList("item"))))
    }

    @Test
    fun `hardbreak claim allows empty text blocks`() {
        val hb = MarkdownProfile.Default.withNewlineSemantics(NewlineSemantics.HardBreak).supportSet
        assertTrue(hb.supportsDocument(listOf(Block.paragraph("a"), Block.paragraph(""), Block.paragraph("b"))))
    }

    @Test
    fun `accepts a plain supported document`() {
        val doc = listOf(
            Block.heading(1, "Title"),
            Block.paragraph("Body text"),
            Block.bulletList("Item"),
        )
        assertTrue(support.supportsDocument(doc))
    }
}
