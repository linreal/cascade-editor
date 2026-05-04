package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlSupportSetTest {

    private val supportSet get() = HtmlProfile.Default.supportSet

    // supportsBlock — built-in supported types.

    @Test
    fun `supportsBlock accepts a default Paragraph`() {
        val block = Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text(""))
        assertTrue(supportSet.supportsBlock(block))
    }

    @Test
    fun `supportsBlock accepts each Heading level 1 through 6 at depth zero`() {
        for (level in 1..6) {
            val block = Block(BlockId.generate(), BlockType.Heading(level), BlockContent.Text(""))
            assertTrue(supportSet.supportsBlock(block), "Heading $level should be supported")
        }
    }

    @Test
    fun `supportsBlock accepts BulletList at every supported indentation depth`() {
        for (depth in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) {
            val block = Block(
                id = BlockId.generate(),
                type = BlockType.BulletList,
                content = BlockContent.Text(""),
                attributes = BlockAttributes(indentationLevel = depth),
            )
            assertTrue(supportSet.supportsBlock(block), "BulletList at depth $depth should be supported")
        }
    }

    @Test
    fun `supportsBlock accepts NumberedList at every supported indentation depth`() {
        for (depth in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) {
            val block = Block(
                id = BlockId.generate(),
                type = BlockType.NumberedList(number = 1),
                content = BlockContent.Text(""),
                attributes = BlockAttributes(indentationLevel = depth),
            )
            assertTrue(supportSet.supportsBlock(block), "NumberedList at depth $depth should be supported")
        }
    }

    @Test
    fun `supportsBlock accepts Code Quote and Divider at depth zero`() {
        val code = Block(BlockId.generate(), BlockType.Code, BlockContent.Text(""))
        val quote = Block(BlockId.generate(), BlockType.Quote, BlockContent.Text(""))
        val divider = Block(BlockId.generate(), BlockType.Divider, BlockContent.Empty)
        assertTrue(supportSet.supportsBlock(code))
        assertTrue(supportSet.supportsBlock(quote))
        assertTrue(supportSet.supportsBlock(divider))
    }

    @Test
    fun `supportsBlock rejects non-list block types at non-zero indentation depth`() {
        val heading = Block(
            id = BlockId.generate(),
            type = BlockType.Heading(1),
            content = BlockContent.Text(""),
            attributes = BlockAttributes(indentationLevel = 1),
        )
        val quote = Block(
            id = BlockId.generate(),
            type = BlockType.Quote,
            content = BlockContent.Text(""),
            attributes = BlockAttributes(indentationLevel = 2),
        )
        val code = Block(
            id = BlockId.generate(),
            type = BlockType.Code,
            content = BlockContent.Text(""),
            attributes = BlockAttributes(indentationLevel = 1),
        )

        assertFalse(supportSet.supportsBlock(heading))
        assertFalse(supportSet.supportsBlock(quote))
        assertFalse(supportSet.supportsBlock(code))
    }

    // supportsBlock — explicitly excluded types.

    @Test
    fun `supportsBlock rejects Todo`() {
        val checked = Block(BlockId.generate(), BlockType.Todo(checked = true), BlockContent.Text(""))
        val unchecked = Block(BlockId.generate(), BlockType.Todo(checked = false), BlockContent.Text(""))
        assertFalse(supportSet.supportsBlock(checked))
        assertFalse(supportSet.supportsBlock(unchecked))
    }

    @Test
    fun `supportsBlock rejects a Custom BlockType`() {
        val customType = object : io.github.linreal.cascade.editor.core.CustomBlockType {
            override val typeId: String = "html.preserved"
            override val displayName: String = "Preserved"
        }
        val block = Block(BlockId.generate(), customType, BlockContent.Empty)
        assertFalse(supportSet.supportsBlock(block))
    }

    // supportsSpan — built-in styles.

    @Test
    fun `supportsSpan accepts every built-in span style`() {
        assertTrue(supportSet.supportsSpan(SpanStyle.Bold))
        assertTrue(supportSet.supportsSpan(SpanStyle.Italic))
        assertTrue(supportSet.supportsSpan(SpanStyle.Underline))
        assertTrue(supportSet.supportsSpan(SpanStyle.StrikeThrough))
        assertTrue(supportSet.supportsSpan(SpanStyle.InlineCode))
    }

    @Test
    fun `supportsSpan accepts Highlight with any color`() {
        assertTrue(supportSet.supportsSpan(SpanStyle.Highlight(0xFFFFFF00)))
        assertTrue(supportSet.supportsSpan(SpanStyle.Highlight(0xFF00FF00)))
        assertTrue(supportSet.supportsSpan(SpanStyle.Highlight(0xFFFF00FF)))
    }

    @Test
    fun `supportsSpan accepts Link with any url`() {
        assertTrue(supportSet.supportsSpan(SpanStyle.Link("https://example.com")))
        assertTrue(supportSet.supportsSpan(SpanStyle.Link("https://wrike.com/path?q=1")))
    }

    @Test
    fun `supportsSpan rejects Custom`() {
        assertFalse(supportSet.supportsSpan(SpanStyle.Custom(typeId = "wrike.mention")))
    }

    // supportsDocument — full-document validation including normalization checks.

    @Test
    fun `supportsDocument returns true for a normalized supported document`() {
        val blocks = listOf(
            Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text("hello")),
            Block(BlockId.generate(), BlockType.Heading(1), BlockContent.Text("title")),
            Block(BlockId.generate(), BlockType.BulletList, BlockContent.Text("a")),
            Block(BlockId.generate(), BlockType.NumberedList(1), BlockContent.Text("one")),
            Block(BlockId.generate(), BlockType.NumberedList(2), BlockContent.Text("two")),
        )
        assertTrue(supportSet.supportsDocument(blocks))
    }

    @Test
    fun `supportsDocument rejects a document containing an unsupported block type`() {
        val blocks = listOf(
            Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text("ok")),
            Block(BlockId.generate(), BlockType.Todo(), BlockContent.Text("nope")),
        )
        assertFalse(supportSet.supportsDocument(blocks))
    }

    @Test
    fun `supportsDocument rejects a document containing an unsupported span style`() {
        val customSpan = TextSpan(0, 1, SpanStyle.Custom(typeId = "wrike.mention"))
        val blocks = listOf(
            Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text("a", listOf(customSpan))),
        )
        assertFalse(supportSet.supportsDocument(blocks))
    }

    @Test
    fun `supportsDocument rejects spans on blocks that do not support spans`() {
        val blocks = listOf(
            Block(
                id = BlockId.generate(),
                type = BlockType.Code,
                content = BlockContent.Text("code", listOf(TextSpan(0, 4, SpanStyle.Bold))),
            ),
        )

        assertFalse(supportSet.supportsDocument(blocks))
    }

    @Test
    fun `supportsDocument rejects unsupported content shapes on supported text blocks`() {
        val blocks = listOf(
            Block(
                id = BlockId.generate(),
                type = BlockType.Paragraph,
                content = BlockContent.Custom(typeId = "custom", data = emptyMap()),
            ),
        )

        assertFalse(supportSet.supportsDocument(blocks))
    }

    @Test
    fun `supportsDocument rejects a document with stale numbered-list values`() {
        val blocks = listOf(
            Block(BlockId.generate(), BlockType.NumberedList(number = 1), BlockContent.Text("one")),
            Block(BlockId.generate(), BlockType.NumberedList(number = 99), BlockContent.Text("two")),
        )
        assertFalse(supportSet.supportsDocument(blocks))
    }

    @Test
    fun `supportsDocument accepts a document where renumberNumberedLists would be a no-op`() {
        val blocks = listOf(
            Block(BlockId.generate(), BlockType.NumberedList(number = 1), BlockContent.Text("one")),
            Block(BlockId.generate(), BlockType.NumberedList(number = 2), BlockContent.Text("two")),
            Block(BlockId.generate(), BlockType.NumberedList(number = 3), BlockContent.Text("three")),
        )
        assertTrue(supportSet.supportsDocument(blocks))
    }

    @Test
    fun `supportsDocument rejects an indentation outline that violates the parent-child invariant`() {
        // Divider is unsupported for indentation; persisting depth > 0 violates the
        // parent/child invariant enforced by post-decode normalization. The block
        // itself cannot be constructed with depth > 0 only when the type forbids it
        // — Divider's BlockAttributes are validated by the constructor, so we
        // simulate the violation via Quote (also unsupported indentation) which has
        // no constructor-side rejection but is enforced by isValidIndentationOutline.
        val invalidOutline = listOf(
            Block(
                id = BlockId.generate(),
                type = BlockType.Quote,
                content = BlockContent.Text("q"),
                attributes = BlockAttributes(indentationLevel = 2),
            ),
        )
        assertFalse(supportSet.supportsDocument(invalidOutline))
    }

    @Test
    fun `supportsDocument accepts an empty document`() {
        assertTrue(supportSet.supportsDocument(emptyList()))
    }
}
