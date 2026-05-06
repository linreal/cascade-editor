package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.renumberNumberedLists
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.SupportSetBlockGenerator
import io.github.linreal.cascade.editor.htmlserialization.assertHtmlSemanticallyEquals
import io.github.linreal.cascade.editor.htmlserialization.assertSupportSetDocument
import kotlin.test.Test

class HtmlSchemaRoundTripTest {

    @Test
    fun `default profile round trips generated support-set documents`() {
        val generator = SupportSetBlockGenerator(
            supportSet = HtmlProfile.Default.supportSet,
            seed = 0xCACE,
        )

        repeat(200) {
            val blocks = generator.nextDocument()
            assertSupportSetDocument(HtmlProfile.Default.supportSet, blocks)

            val roundTripped = HtmlSchema.decode(
                HtmlSchema.encode(blocks, HtmlProfile.Default),
                HtmlProfile.Default,
            )

            assertHtmlSemanticallyEquals(blocks, roundTripped)
        }
    }

    @Test
    fun `default profile round trips every supported built-in block and span style`() {
        val blocks = renumberNumberedLists(
            listOf(
                Block.paragraph(
                    text = "bold italic underline strike code highlight link",
                    spans = listOf(
                        TextSpan(0, 4, SpanStyle.Bold),
                        TextSpan(5, 11, SpanStyle.Italic),
                        TextSpan(12, 21, SpanStyle.Underline),
                        TextSpan(22, 28, SpanStyle.StrikeThrough),
                        TextSpan(29, 33, SpanStyle.InlineCode),
                        TextSpan(34, 43, SpanStyle.Highlight(0xFF00FF00)),
                        TextSpan(44, 48, SpanStyle.Link("https://example.com")),
                    ),
                ).withAttributes(BlockAttributes(indentationLevel = 2)),
            ) +
                (1..6).map { level -> Block.heading(level = level, text = "Heading $level") } +
                listOf(
                    Block(
                        id = BlockId.generate(),
                        type = BlockType.Quote,
                        content = BlockContent.Text(
                            text = "Quote",
                            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
                        ),
                    ),
                    Block(
                        id = BlockId.generate(),
                        type = BlockType.Code,
                        content = BlockContent.Text("code\nline"),
                    ),
                    Block.bulletList("Bullet")
                        .withAttributes(BlockAttributes(indentationLevel = 1)),
                    Block.numberedList("Numbered", number = 1)
                        .withAttributes(BlockAttributes(indentationLevel = 2)),
                    Block.divider(),
                )
        )

        assertSupportSetDocument(HtmlProfile.Default.supportSet, blocks)

        val roundTripped = HtmlSchema.decode(
            HtmlSchema.encode(blocks, HtmlProfile.Default),
            HtmlProfile.Default,
        )

        assertHtmlSemanticallyEquals(blocks, roundTripped)
    }

    @Test
    fun `default profile round trips mixed list outlines with varying indentation`() {
        val blocks = renumberNumberedLists(
            listOf(
                Block.bulletList("root"),
                Block.numberedList("numbered child", number = 1)
                    .withAttributes(BlockAttributes(indentationLevel = 1)),
                Block.bulletList("free depth")
                    .withAttributes(BlockAttributes(indentationLevel = 4)),
                Block.numberedList("back to numbered root", number = 1),
                Block.bulletList("bullet child")
                    .withAttributes(BlockAttributes(indentationLevel = 2)),
            )
        )

        assertSupportSetDocument(HtmlProfile.Default.supportSet, blocks)

        val roundTripped = HtmlSchema.decode(
            HtmlSchema.encode(blocks, HtmlProfile.Default),
            HtmlProfile.Default,
        )

        assertHtmlSemanticallyEquals(blocks, roundTripped)
    }
}
