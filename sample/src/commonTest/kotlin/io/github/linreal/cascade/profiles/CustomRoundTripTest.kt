package io.github.linreal.cascade.profiles

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomRoundTripTest {

    @Test
    fun `Custom profile round trips deterministic support-set fixtures`() {
        for (blocks in supportSetFixtures()) {
            assertTrue(
                CustomHtmlProfile.Profile.supportSet.supportsDocument(blocks),
                "Fixture should stay inside the Custom support set: $blocks",
            )

            val roundTripped = HtmlSchema.decode(
                HtmlSchema.encode(blocks, CustomHtmlProfile.Profile),
                CustomHtmlProfile.Profile,
            )

            assertCustomSemanticallyEquals(blocks, roundTripped)
        }
    }

    private fun supportSetFixtures(): List<List<Block>> = listOf(
        listOf(
            textBlock(
                type = BlockType.Paragraph,
                text = "bold italic strike code link",
                spans = listOf(
                    TextSpan(0, 4, SpanStyle.Bold),
                    TextSpan(5, 11, SpanStyle.Italic),
                    TextSpan(12, 18, SpanStyle.StrikeThrough),
                    TextSpan(19, 23, SpanStyle.InlineCode),
                    TextSpan(24, 28, SpanStyle.Link("https://example.com/path?a=1")),
                ),
            ),
        ),
        listOf(
            textBlock(type = BlockType.Code, text = "Code block\nCode line"),
        ),
        listOf(
            textBlock(type = BlockType.BulletList, text = "Root"),
            textBlock(type = BlockType.BulletList, text = "Nested", indentation = 1),
            textBlock(type = BlockType.BulletList, text = "Sibling", indentation = 1),
            textBlock(type = BlockType.BulletList, text = "Back"),
        ),
        listOf(
            textBlock(type = BlockType.NumberedList(1), text = "One"),
            textBlock(type = BlockType.NumberedList(1), text = "Nested one", indentation = 1),
            textBlock(type = BlockType.NumberedList(2), text = "Nested two", indentation = 1),
            textBlock(type = BlockType.NumberedList(2), text = "Two"),
        ),
        listOf(
            textBlock(type = BlockType.Paragraph, text = "Intro"),
            textBlock(
                type = BlockType.BulletList,
                text = "Linked item",
                indentation = 1,
                spans = listOf(TextSpan(0, 6, SpanStyle.Link("https://example.com"))),
            ),
            textBlock(type = BlockType.Code, text = "x < y && z"),
        ),
    )

    private fun textBlock(
        type: BlockType,
        text: String,
        indentation: Int = 0,
        spans: List<TextSpan> = emptyList(),
    ): Block = Block(
        id = BlockId.generate(),
        type = type,
        content = BlockContent.Text(text = text, spans = spans),
        attributes = BlockAttributes(indentationLevel = indentation),
    )

    private fun assertCustomSemanticallyEquals(
        expected: List<Block>,
        actual: List<Block>,
    ) {
        assertEquals(expected.size, actual.size, "Block count should match")
        for (index in expected.indices) {
            assertEquals(expected[index].type, actual[index].type, "Block $index type")
            assertEquals(expected[index].attributes, actual[index].attributes, "Block $index attributes")
            assertEquals(
                expected[index].content.normalized(),
                actual[index].content.normalized(),
                "Block $index content",
            )
        }
    }

    private fun BlockContent.normalized(): BlockContent = when (this) {
        is BlockContent.Text -> copy(spans = spans.normalized(text.length))
        BlockContent.Empty,
        is BlockContent.Custom -> this
    }

    private fun List<TextSpan>.normalized(textLength: Int): List<TextSpan> =
        mapNotNull { span ->
            val start = span.start.coerceIn(0, textLength)
            val end = span.end.coerceIn(start, textLength)
            if (start < end) span.copy(start = start, end = end) else null
        }.sortedWith(
            compareBy<TextSpan> { it.start }
                .thenBy { it.end }
                .thenBy { it.style.sortKey() }
        )

    private fun SpanStyle.sortKey(): String = when (this) {
        SpanStyle.Bold -> "bold"
        SpanStyle.Italic -> "italic"
        SpanStyle.Underline -> "underline"
        SpanStyle.StrikeThrough -> "strike"
        SpanStyle.InlineCode -> "inline_code"
        is SpanStyle.Link -> "link:$url"
        is SpanStyle.Highlight -> "highlight:$colorArgb"
        is SpanStyle.Custom -> "custom:$typeId:$payload"
    }
}
