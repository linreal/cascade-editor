package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.renumberNumberedLists
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic generator for documents that must satisfy an [HtmlProfileSupportSet].
 *
 * This lives in editor test sources so default-profile round-trip tests can share
 * one generation policy instead of drifting as new block/span support is added.
 */
public class SupportSetBlockGenerator(
    private val supportSet: HtmlProfileSupportSet,
    seed: Int,
) {
    private val random: Random = Random(seed)

    /** Returns an editor-normalized document accepted by the configured support set. */
    public fun nextDocument(maxBlocks: Int = 10): List<Block> {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val blocks = renumberNumberedLists(buildCandidateDocument(maxBlocks))
            if (supportSet.supportsDocument(blocks)) return blocks
        }
        error("Unable to generate a support-set document after $MAX_GENERATION_ATTEMPTS attempts")
    }

    private fun buildCandidateDocument(maxBlocks: Int): List<Block> {
        val blockCount = random.nextInt(from = 1, until = maxBlocks + 1)
        return List(blockCount) { index -> candidateBlock(index) }
    }

    private fun candidateBlock(index: Int): Block {
        val text = "text $index <&>"
        return when (random.nextInt(7)) {
            0 -> textBlock(
                type = BlockType.Paragraph,
                text = text,
                indentation = randomIndentation(),
                spans = candidateSpans(text.length),
            )

            1 -> textBlock(
                type = BlockType.Heading(random.nextInt(from = 1, until = 7)),
                text = text,
                spans = candidateSpans(text.length),
            )

            2 -> textBlock(
                type = BlockType.Quote,
                text = text,
                spans = candidateSpans(text.length),
            )

            3 -> textBlock(type = BlockType.Code, text = "code $index\nline")
            4 -> Block.divider()
            5 -> textBlock(
                type = BlockType.BulletList,
                text = text,
                indentation = randomIndentation(),
                spans = candidateSpans(text.length),
            )

            else -> textBlock(
                type = BlockType.NumberedList(number = 1),
                text = text,
                indentation = randomIndentation(),
                spans = candidateSpans(text.length),
            )
        }
    }

    private fun textBlock(
        type: BlockType,
        text: String,
        indentation: Int = BlockAttributes.MIN_INDENTATION_LEVEL,
        spans: List<TextSpan> = emptyList(),
    ): Block = Block(
        id = BlockId.generate(),
        type = type,
        content = BlockContent.Text(text = text, spans = spans),
        attributes = BlockAttributes(indentationLevel = indentation),
    )

    private fun candidateSpans(textLength: Int): List<TextSpan> {
        if (textLength < 2) return emptyList()

        val availableStyles = BUILT_IN_SPAN_STYLES.filter(supportSet::supportsSpan)
        if (availableStyles.isEmpty()) return emptyList()

        val spanCount = random.nextInt(from = 0, until = minOf(4, availableStyles.size + 1))
        val selectedStyles = availableStyles.shuffled(random).take(spanCount)
        val spans = List(spanCount) { index ->
            val start = random.nextInt(from = 0, until = textLength - 1)
            val end = random.nextInt(from = start + 1, until = textLength + 1)
            TextSpan(start = start, end = end, style = selectedStyles[index])
        }
        return SpanAlgorithms.normalize(spans, textLength)
    }

    private fun randomIndentation(): Int =
        random.nextInt(
            from = BlockAttributes.MIN_INDENTATION_LEVEL,
            until = BlockAttributes.MAX_INDENTATION_LEVEL + 1,
        )

    private companion object {
        private const val MAX_GENERATION_ATTEMPTS: Int = 1_000

        private val BUILT_IN_SPAN_STYLES: List<SpanStyle> = listOf(
            SpanStyle.Bold,
            SpanStyle.Italic,
            SpanStyle.Underline,
            SpanStyle.StrikeThrough,
            SpanStyle.InlineCode,
            SpanStyle.Highlight(HtmlProfile.DEFAULT_HIGHLIGHT_COLOR_ARGB),
            SpanStyle.Highlight(0x0000_0000L),
            SpanStyle.Highlight(0xFF00_FF00L),
            SpanStyle.Highlight(0xFFFF_FFFFL),
            SpanStyle.Link("https://example.com/path?a=1"),
            SpanStyle.Link("https://example.com/path?q=a&b=<c>"),
        )
    }
}

/**
 * Compare HTML round-trip output while ignoring regenerated [BlockId] values and
 * stable-only span ordering differences.
 */
public fun assertHtmlSemanticallyEquals(
    expected: List<Block>,
    actual: List<Block>,
) {
    assertEquals(expected.size, actual.size, "Block count should match")
    for (index in expected.indices) {
        assertHtmlSemanticallyEquals(expected[index], actual[index], index)
    }
}

/** Assert that a generated fixture did not escape the target profile's support set. */
public fun assertSupportSetDocument(
    supportSet: HtmlProfileSupportSet,
    blocks: List<Block>,
) {
    assertTrue(
        supportSet.supportsDocument(blocks),
        "Generated document should be inside the target support set: $blocks",
    )
}

private fun assertHtmlSemanticallyEquals(
    expected: Block,
    actual: Block,
    index: Int,
) {
    assertEquals(expected.type, actual.type, "Block $index type should match")
    assertEquals(expected.attributes, actual.attributes, "Block $index attributes should match")
    assertEquals(
        expected.content.normalizedForHtmlSemanticComparison(),
        actual.content.normalizedForHtmlSemanticComparison(),
        "Block $index content should match",
    )
}

private fun BlockContent.normalizedForHtmlSemanticComparison(): BlockContent = when (this) {
    is BlockContent.Text -> copy(
        spans = spans.normalizedForHtmlSemanticComparison(text.length)
    )

    BlockContent.Empty,
    is BlockContent.Custom -> this
}

private fun List<TextSpan>.normalizedForHtmlSemanticComparison(textLength: Int): List<TextSpan> {
    val clamped = mapNotNull { span ->
        val start = span.start.coerceIn(0, textLength)
        val end = span.end.coerceIn(start, textLength)
        if (start < end) TextSpan(start, end, span.style) else null
    }
    if (clamped.isEmpty()) return emptyList()

    val mergedByStyle = mutableListOf<TextSpan>()
    val byStyleThenRange = compareBy<TextSpan> { it.style.semanticSortKey() }
        .thenBy { it.start }
        .thenBy { it.end }
    for (span in clamped.sortedWith(byStyleThenRange)) {
        val previous = mergedByStyle.lastOrNull()
        if (previous != null && previous.style == span.style && span.start <= previous.end) {
            mergedByStyle[mergedByStyle.lastIndex] = previous.copy(end = maxOf(previous.end, span.end))
        } else {
            mergedByStyle += span
        }
    }

    return mergedByStyle.sortedWith(
        compareBy<TextSpan> { it.start }
            .thenBy { it.end }
            .thenBy { it.style.semanticSortKey() }
    )
}

private fun SpanStyle.semanticSortKey(): String = when (this) {
    SpanStyle.Bold -> "bold"
    SpanStyle.Italic -> "italic"
    SpanStyle.Underline -> "underline"
    SpanStyle.StrikeThrough -> "strike_through"
    SpanStyle.InlineCode -> "inline_code"
    is SpanStyle.Highlight -> "highlight:$colorArgb"
    is SpanStyle.Link -> "link:$url"
    is SpanStyle.Custom -> "custom:$typeId:$payload"
}
