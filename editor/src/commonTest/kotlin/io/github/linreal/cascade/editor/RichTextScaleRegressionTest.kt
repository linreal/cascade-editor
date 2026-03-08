package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import io.github.linreal.cascade.editor.state.BlockSpanStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichTextScaleRegressionTest {

    @Test
    fun `normalize handles large mixed span set and preserves invariants`() {
        val textLength = 50_000
        val input = buildList {
            for (index in 0 until 6_000) {
                val start = (index * 7) % (textLength - 32)
                val end = (start + 8 + (index % 24)).coerceAtMost(textLength)
                val style = if (index % 2 == 0) SpanStyle.Bold else SpanStyle.Italic
                add(TextSpan(start, end, style))

                if (index % 3 == 0) {
                    val wideEnd = (end + 6).coerceAtMost(textLength)
                    add(TextSpan(start, wideEnd, SpanStyle.Underline))
                }
            }
        }

        val normalized = SpanAlgorithms.normalize(input, textLength)
        assertTrue(normalized.isNotEmpty())
        assertTrue(normalized.all { it.start >= 0 && it.end <= textLength && it.start < it.end })
        assertTrue(
            normalized.zipWithNext().all { (first, second) ->
                first.start < second.start || (first.start == second.start && first.end <= second.end)
            },
            "Output must stay sorted by (start, end)",
        )

        assertStyleRunsAreMerged(normalized, SpanStyle.Bold)
        assertStyleRunsAreMerged(normalized, SpanStyle.Italic)
        assertStyleRunsAreMerged(normalized, SpanStyle.Underline)
    }

    @Test
    fun `cleanup prunes stale states and pending styles across many blocks`() {
        val holder = BlockSpanStates()
        val totalBlocks = 1_500

        for (index in 0 until totalBlocks) {
            val blockId = BlockId("block-$index")
            holder.getOrCreate(
                blockId = blockId,
                initialSpans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
                textLength = 5,
            )
            holder.setPendingStyles(blockId, setOf(SpanStyle.Italic))
        }

        val retained = (0 until totalBlocks step 3).map { BlockId("block-$it") }.toSet()
        holder.cleanup(retained)

        for (index in 0 until totalBlocks) {
            val blockId = BlockId("block-$index")
            if (blockId in retained) {
                assertTrue(holder.get(blockId) != null)
                assertEquals(setOf(SpanStyle.Italic), holder.getPendingStyles(blockId))
            } else {
                assertNull(holder.get(blockId))
                assertNull(holder.getPendingStyles(blockId))
            }
        }
    }

    @Test
    fun `applyStyle remains block-local under large block count`() {
        val holder = BlockSpanStates()
        val totalBlocks = 2_000
        val target = BlockId("block-1337")

        for (index in 0 until totalBlocks) {
            holder.getOrCreate(
                blockId = BlockId("block-$index"),
                initialSpans = emptyList(),
                textLength = 200,
            )
        }

        holder.applyStyle(
            blockId = target,
            rangeStart = 12,
            rangeEnd = 48,
            style = SpanStyle.Bold,
            textLength = 200,
        )

        assertEquals(listOf(TextSpan(12, 48, SpanStyle.Bold)), holder.getSpans(target))
        assertTrue(holder.getSpans(BlockId("block-0")).isEmpty())
        assertTrue(holder.getSpans(BlockId("block-1999")).isEmpty())
    }

    private fun assertStyleRunsAreMerged(spans: List<TextSpan>, style: SpanStyle) {
        val styleSpans = spans
            .filter { it.style == style }
            .sortedWith(compareBy({ it.start }, { it.end }))

        assertTrue(
            styleSpans.zipWithNext().all { (first, second) -> second.start > first.end },
            "Same-style runs should be merged (no overlap/adjacency) for $style",
        )
    }
}
