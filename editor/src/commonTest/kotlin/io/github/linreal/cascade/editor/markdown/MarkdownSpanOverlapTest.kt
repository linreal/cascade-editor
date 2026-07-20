package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Overlap classification and the honesty contract — the encoder
 * never produces output that silently decodes to different spans. Every
 * generated layout must either round-trip exactly or carry a DataLoss-impact
 * warning explaining what was dropped.
 */
class MarkdownSpanOverlapTest {

    private fun paragraph(text: String, spans: List<TextSpan>): Block = Block(
        id = BlockId.generate(),
        type = BlockType.Paragraph,
        content = BlockContent.Text(text, spans),
    )

    private fun roundTrip(block: Block): Pair<MarkdownEncodeResult, MarkdownDecodeResult?> {
        val encoded = MarkdownEncodeEngine.encodeWithReport(listOf(block), MarkdownProfile.Default)
        val markdown = encoded.markdown ?: return encoded to null
        return encoded to MarkdownDecodeEngine.decode(markdown, MarkdownProfile.Default)
    }

    // Directed cases

    @Test
    fun partialOverlapCloseReopenRoundTripsExactly() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.Italic),
        )
        val (encoded, decoded) = roundTrip(paragraph("abcdefgh", spans))
        val content = decoded!!.blocks!!.single().content as BlockContent.Text
        if (encoded.warnings.none { it.impact == MarkdownFidelityImpact.DataLoss }) {
            assertEquals("abcdefgh", content.text)
            assertEquals(spans.toSet(), content.spans.toSet())
        } else {
            // Drop path is allowed only with an AmbiguousEmphasis warning.
            assertTrue(encoded.warnings.any { it is MarkdownEncodeWarning.AmbiguousEmphasis })
        }
    }

    @Test
    fun codeCrossingSplitsAndRemergesThroughNormalization() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.InlineCode),
        )
        val (encoded, decoded) = roundTrip(paragraph("abcdefgh", spans))
        val content = decoded!!.blocks!!.single().content as BlockContent.Text
        if (encoded.warnings.none { it.impact == MarkdownFidelityImpact.DataLoss }) {
            assertEquals(spans.toSet(), content.spans.toSet())
        } else {
            assertTrue(
                encoded.warnings.any {
                    it is MarkdownEncodeWarning.DroppedSpanOverlap ||
                        it is MarkdownEncodeWarning.AmbiguousEmphasis
                },
            )
        }
    }

    // Generated overlap/code-content matrix

    @Test
    fun generatedOverlapMatrixIsNeverSilentlyDifferent() {
        val text = "abcdefgh"
        val styles = listOf(
            SpanStyle.Bold,
            SpanStyle.Italic,
            SpanStyle.StrikeThrough,
            SpanStyle.InlineCode,
            SpanStyle.Link("/u"),
        )
        val ranges = listOf(
            0 to 3, 0 to 5, 0 to 8, 2 to 6, 3 to 5, 3 to 8, 5 to 8, 1 to 7,
        )
        val failures = StringBuilder()
        var checked = 0
        for (styleA in styles) {
            for (styleB in styles) {
                for ((startA, endA) in ranges) {
                    for ((startB, endB) in ranges) {
                        val spans = listOf(
                            TextSpan(startA, endA, styleA),
                            TextSpan(startB, endB, styleB),
                        )
                        checked++
                        val block = paragraph(text, spans)
                        val encoded = MarkdownEncodeEngine.encodeWithReport(
                            listOf(block),
                            MarkdownProfile.Default,
                        )
                        val markdown = encoded.markdown
                        if (markdown == null) {
                            failures.appendLine("$spans: encode aborted ${encoded.warnings}")
                            continue
                        }
                        val decoded = MarkdownDecodeEngine.decode(markdown, MarkdownProfile.Default)
                        val decodedBlocks = decoded.blocks
                        if (decodedBlocks == null) {
                            failures.appendLine("$spans: decode aborted ${decoded.warnings}")
                            continue
                        }
                        val hasLoss = encoded.warnings.any {
                            it.impact == MarkdownFidelityImpact.DataLoss
                        }
                        if (hasLoss) continue // honest loss, explicitly warned
                        val content = decodedBlocks.singleOrNull()?.content as? BlockContent.Text
                        if (content == null) {
                            failures.appendLine("$spans → $markdown: shape changed on decode")
                            continue
                        }
                        val expected = normalizeForComparison(spans, text.length)
                        if (content.text != text || content.spans.toSet() != expected.toSet()) {
                            failures.appendLine(
                                "$spans → ${markdown.trimEnd()}: decoded " +
                                    "${content.spans} / ${content.text}",
                            )
                        }
                    }
                }
            }
        }
        assertTrue(checked > 300, "matrix too small: $checked")
        if (failures.isNotEmpty()) fail("silent differences:\n$failures")
    }

    /**
     * Decode returns normalized spans, so expectations run through the same
     * normalization contract (same-style merge, link overlap resolution).
     */
    private fun normalizeForComparison(spans: List<TextSpan>, length: Int): List<TextSpan> =
        SpanAlgorithms.normalize(spans, length)

    // decode(encode(x)) == x smoke suite for span-bearing documents

    @Test
    fun spanBearingDocumentSmokeRoundTrip() {
        val docs = listOf(
            listOf(
                paragraph(
                    "intro with bold and italic",
                    listOf(
                        TextSpan(11, 15, SpanStyle.Bold),
                        TextSpan(20, 26, SpanStyle.Italic),
                    ),
                ),
                Block(
                    id = BlockId.generate(),
                    type = BlockType.Heading(2),
                    content = BlockContent.Text(
                        "heading code",
                        listOf(TextSpan(8, 12, SpanStyle.InlineCode)),
                    ),
                ),
                Block(
                    id = BlockId.generate(),
                    type = BlockType.Quote,
                    content = BlockContent.Text(
                        "quoted link",
                        listOf(TextSpan(7, 11, SpanStyle.Link("../rel.md"))),
                    ),
                ),
                Block(
                    id = BlockId.generate(),
                    type = BlockType.Todo(checked = true),
                    content = BlockContent.Text(
                        "task struck",
                        listOf(TextSpan(5, 11, SpanStyle.StrikeThrough)),
                    ),
                ),
                Block(
                    id = BlockId.generate(),
                    type = BlockType.BulletList,
                    content = BlockContent.Text(
                        "item bold",
                        listOf(TextSpan(5, 9, SpanStyle.Bold)),
                    ),
                ),
            ),
            listOf(
                paragraph(
                    "nested bold italic",
                    listOf(
                        TextSpan(0, 18, SpanStyle.Bold),
                        TextSpan(7, 11, SpanStyle.Italic),
                    ),
                ),
                paragraph(
                    "hard\nbreak bold",
                    listOf(TextSpan(0, 10, SpanStyle.Bold)),
                ),
            ),
        )
        for (doc in docs) {
            val encoded = MarkdownEncodeEngine.encodeWithReport(doc, MarkdownProfile.Default)
            val markdown = encoded.markdown ?: fail("encode aborted: ${encoded.warnings}")
            assertTrue(
                encoded.warnings.none { it.impact == MarkdownFidelityImpact.DataLoss },
                "unexpected loss: ${encoded.warnings}",
            )
            val decoded = MarkdownDecodeEngine.decode(markdown, MarkdownProfile.Default)
            val blocks = decoded.blocks ?: fail("decode aborted: ${decoded.warnings}")
            assertEquals(doc.size, blocks.size, "block count for:\n$markdown")
            for (index in doc.indices) {
                val original = doc[index]
                val reloaded = blocks[index]
                assertEquals(original.type, reloaded.type, "type at $index:\n$markdown")
                val originalContent = original.content as BlockContent.Text
                val reloadedContent = reloaded.content as BlockContent.Text
                assertEquals(originalContent.text, reloadedContent.text, "text at $index:\n$markdown")
                assertEquals(
                    normalizeForComparison(originalContent.spans, originalContent.text.length).toSet(),
                    reloadedContent.spans.toSet(),
                    "spans at $index:\n$markdown",
                )
            }
        }
    }
}
