package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.normalizeIndentationOutline
import io.github.linreal.cascade.editor.core.renumberNumberedLists
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic generator for documents that must satisfy a
 * [MarkdownProfileSupportSet].
 *
 * The generator is **self-adapting**: it probes each candidate span style
 * through the profile's document claim once, so it only emits styles that
 * currently round-trip (e.g. `Underline` / `Highlight` become available once
 * the HTML span encoders are registered on the default profile). Every
 * generated document is verified against [supportSet] before it is returned, so
 * a generation escape fails loudly rather than producing a false round-trip
 * assertion.
 *
 * ### Reproducibility
 *
 * Seeded [Random]; the seed used by the property test is printed on failure so
 * any counter-example is replayable by re-running with the same seed.
 */
internal class MarkdownSupportSetBlockGenerator(
    private val profile: MarkdownProfile,
    private val supportSet: MarkdownProfileSupportSet,
    seed: Long,
    private val hardBreak: Boolean = false,
) {
    private val random: Random = Random(seed)

    /** Span styles that currently pass the document claim, probed once. */
    private val encodableStyles: List<SpanStyle> by lazy {
        CANDIDATE_SPAN_STYLES.filter { style ->
            val probe = listOf(
                Block(
                    id = BlockId.generate(),
                    type = BlockType.Paragraph,
                    content = BlockContent.Text("alpha beta", listOf(TextSpan(0, 5, style))),
                ),
            )
            supportSet.supportsDocument(probe)
        }
    }

    fun nextDocument(maxBlocks: Int = 8): List<Block> {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val blocks = normalizeIndentationOutline(buildCandidateDocument(maxBlocks))
                .let(::renumberNumberedLists)
            if (supportSet.supportsDocument(blocks)) return blocks
        }
        error("Unable to generate a support-set document after $MAX_GENERATION_ATTEMPTS attempts")
    }

    private fun buildCandidateDocument(maxBlocks: Int): List<Block> {
        // No claim-specific shaping: candidate documents are drawn freely and
        // the support set is the round-trip boundary — merge-prone or
        // front-matter-ambiguous HardBreak shapes are rejected by the claim and
        // regenerated, so the property test exercises that boundary.
        val count = random.nextInt(from = 1, until = maxBlocks + 1)
        return List(count) { index -> candidateBlock(index) }
    }

    private fun paragraphText(index: Int): String {
        // Occasionally draw an in-claim hostile fragment so the generator can
        // catch escaping bugs, not only "wordN alpha beta" text.
        if (usableHostileTexts.isNotEmpty() && random.nextInt(3) == 0) {
            return usableHostileTexts[random.nextInt(usableHostileTexts.size)]
        }
        return "word$index alpha beta"
    }

    private fun candidateBlock(index: Int): Block {
        val text = "word$index alpha beta"
        if (hardBreak) {
            // HardBreak's round-trippable block types are leaf blocks plus
            // explicit empty paragraphs; containers are excluded by the claim.
            return when (random.nextInt(5)) {
                0 -> {
                    val t = paragraphText(index)
                    textBlock(BlockType.Paragraph, t, spans = candidateSpans(t.length))
                }
                1 -> textBlock(BlockType.Heading(random.nextInt(1, 7)), text, spans = candidateSpans(text.length))
                2 -> textBlock(BlockType.Code, "code$index\nline")
                3 -> Block.divider()
                else -> emptyParagraph()
            }
        }
        return when (random.nextInt(7)) {
            0 -> {
                val t = paragraphText(index)
                textBlock(BlockType.Paragraph, t, spans = candidateSpans(t.length))
            }
            1 -> textBlock(BlockType.Heading(random.nextInt(1, 7)), text, spans = candidateSpans(text.length))
            2 -> textBlock(BlockType.Quote, text, spans = candidateSpans(text.length))
            3 -> textBlock(BlockType.Code, "code$index\nline")
            4 -> Block.divider()
            5 -> textBlock(
                BlockType.BulletList,
                text,
                indentation = randomIndentation(),
                spans = candidateSpans(text.length),
            )
            6 -> textBlock(
                BlockType.NumberedList(1),
                text,
                indentation = randomIndentation(),
                spans = candidateSpans(text.length),
            )
            else -> textBlock(
                BlockType.Todo(random.nextBoolean()),
                text,
                indentation = randomIndentation(),
                spans = candidateSpans(text.length),
            )
        }
    }

    /** Hostile fixture strings that survive the support set as a lone paragraph. */
    private val usableHostileTexts: List<String> by lazy {
        MarkdownHostileTextFixtures.strings.filter { fragment ->
            fragment.isNotEmpty() && supportSet.supportsDocument(
                listOf(Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text(fragment))),
            )
        }
    }

    private fun textBlock(
        type: BlockType,
        text: String,
        indentation: Int = 0,
        spans: List<TextSpan> = emptyList(),
    ): Block = Block(
        id = BlockId.generate(),
        type = type,
        content = BlockContent.Text(text, spans),
        attributes = BlockAttributes(indentationLevel = indentation),
    )

    private fun emptyParagraph(): Block =
        Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text(""))

    /** Non-overlapping, word-aligned spans so they always round-trip. */
    private fun candidateSpans(textLength: Int): List<TextSpan> {
        if (textLength < 3 || encodableStyles.isEmpty()) return emptyList()
        val spanCount = random.nextInt(0, 3)
        if (spanCount == 0) return emptyList()

        val spans = ArrayList<TextSpan>()
        var cursor = 0
        repeat(spanCount) {
            if (cursor >= textLength - 1) return@repeat
            val gap = random.nextInt(0, 2)
            val start = (cursor + gap).coerceAtMost(textLength - 1)
            val end = random.nextInt(start + 1, textLength + 1)
            spans += TextSpan(start, end, encodableStyles[random.nextInt(encodableStyles.size)])
            cursor = end + 1
        }
        return SpanAlgorithms.normalize(spans, textLength)
    }

    private fun randomIndentation(): Int =
        random.nextInt(BlockAttributes.MIN_INDENTATION_LEVEL, BlockAttributes.MAX_INDENTATION_LEVEL + 1)

    companion object {
        const val MAX_GENERATION_ATTEMPTS: Int = 500

        private val CANDIDATE_SPAN_STYLES: List<SpanStyle> = listOf(
            SpanStyle.Bold,
            SpanStyle.Italic,
            SpanStyle.StrikeThrough,
            SpanStyle.InlineCode,
            SpanStyle.Underline,
            SpanStyle.Highlight(0xFFFFFF00L),
            SpanStyle.Link("https://example.com/path"),
            SpanStyle.Link("../relative/guide.md"),
            SpanStyle.Link("#section"),
            SpanStyle.Link("mailto:user@example.com"),
        )
    }
}

/** Compare Markdown round-trip output ignoring regenerated [BlockId]s. */
internal fun assertMarkdownSemanticallyEquals(expected: List<Block>, actual: List<Block>) {
    assertEquals(expected.size, actual.size, "block count")
    for (index in expected.indices) {
        val e = expected[index]
        val a = actual[index]
        assertEquals(e.type, a.type, "block $index type")
        assertEquals(e.attributes, a.attributes, "block $index attributes")
        assertEquals(
            e.content.normalizedForComparison(),
            a.content.normalizedForComparison(),
            "block $index content",
        )
    }
}

internal fun assertMarkdownSupportSetDocument(supportSet: MarkdownProfileSupportSet, blocks: List<Block>) {
    assertTrue(
        supportSet.supportsDocument(blocks),
        "generated document escaped the support set: $blocks",
    )
}

private fun BlockContent.normalizedForComparison(): BlockContent = when (this) {
    is BlockContent.Text -> BlockContent.Text(text, SpanAlgorithms.normalize(spans, text.length))
    BlockContent.Empty, is BlockContent.Custom -> this
}
