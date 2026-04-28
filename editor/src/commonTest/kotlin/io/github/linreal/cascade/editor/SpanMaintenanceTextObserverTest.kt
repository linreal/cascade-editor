package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanMaintenanceTextObserver
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpanMaintenanceTextObserverTest {

    @Test
    fun `onCommittedVisibleText - skips maintenance for exact programmatic commit`() {
        val blockId = BlockId("block-1")
        val textStates = BlockTextStates()
        textStates.getOrCreate(blockId, "ab")

        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(
            blockId = blockId,
            initialSpans = listOf(TextSpan(0, 2, SpanStyle.Bold)),
            textLength = 2,
        )
        val observer = SpanMaintenanceTextObserver(
            blockId = blockId,
            textStates = textStates,
            spanStates = spanStates,
            initialVisibleText = "ab",
        )

        // Simulate callback-side programmatic merge transfer result.
        spanStates.set(
            blockId = blockId,
            spans = listOf(
                TextSpan(0, 2, SpanStyle.Bold),
                TextSpan(2, 4, SpanStyle.Italic),
            ),
            textLength = 4,
        )
        textStates.setText(blockId, "abcd")

        observer.onCommittedVisibleText("abcd")

        assertEquals(
            listOf(
                TextSpan(0, 2, SpanStyle.Bold),
                TextSpan(2, 4, SpanStyle.Italic),
            ),
            spanStates.getSpans(blockId),
        )
    }

    @Test
    fun `onCommittedVisibleText - rebases to programmatic baseline before applying user delta`() {
        val blockId = BlockId("block-1")
        val textStates = BlockTextStates()
        textStates.getOrCreate(blockId, "ab")

        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(
            blockId = blockId,
            initialSpans = listOf(TextSpan(0, 2, SpanStyle.Bold)),
            textLength = 2,
        )
        val observer = SpanMaintenanceTextObserver(
            blockId = blockId,
            textStates = textStates,
            spanStates = spanStates,
            initialVisibleText = "ab",
        )

        // Programmatic baseline ("ab" -> "abcd") was already handled by callback-side span merge.
        spanStates.set(
            blockId = blockId,
            spans = listOf(
                TextSpan(0, 2, SpanStyle.Bold),
                TextSpan(2, 4, SpanStyle.Italic),
            ),
            textLength = 4,
        )
        textStates.setText(blockId, "abcd")

        // Observer sees only final commit after user typed one more character.
        observer.onCommittedVisibleText("abcde")

        assertEquals(
            listOf(
                TextSpan(0, 2, SpanStyle.Bold),
                TextSpan(2, 5, SpanStyle.Italic),
            ),
            spanStates.getSpans(blockId),
        )
    }

    @Test
    fun `onCommittedVisibleText - insertion strictly inside link remains linked`() {
        val blockId = BlockId("block-1")
        val link = SpanStyle.Link("https://example.com")
        val textStates = BlockTextStates()
        textStates.getOrCreate(blockId, "Link")

        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(
            blockId = blockId,
            initialSpans = listOf(TextSpan(0, 4, link)),
            textLength = 4,
        )
        val observer = SpanMaintenanceTextObserver(
            blockId = blockId,
            textStates = textStates,
            spanStates = spanStates,
            initialVisibleText = "Link",
        )

        observer.onCommittedVisibleText("LiXnk")

        assertEquals(listOf(TextSpan(0, 5, link)), spanStates.getSpans(blockId))
    }

    @Test
    fun `onCommittedVisibleText - insertion at link end does not continue link`() {
        val blockId = BlockId("block-1")
        val link = SpanStyle.Link("https://example.com")
        val textStates = BlockTextStates()
        textStates.getOrCreate(blockId, "Link")

        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(
            blockId = blockId,
            initialSpans = listOf(TextSpan(0, 4, link)),
            textLength = 4,
        )
        val observer = SpanMaintenanceTextObserver(
            blockId = blockId,
            textStates = textStates,
            spanStates = spanStates,
            initialVisibleText = "Link",
        )

        observer.onCommittedVisibleText("LinkX")

        assertEquals(listOf(TextSpan(0, 4, link)), spanStates.getSpans(blockId))
    }

    @Test
    fun `onCommittedVisibleText - explicit pending link is not applied to inserted text`() {
        val blockId = BlockId("block-1")
        val link = SpanStyle.Link("https://example.com")
        val textStates = BlockTextStates()
        textStates.getOrCreate(blockId, "Hi")

        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(blockId = blockId, initialSpans = emptyList(), textLength = 2)
        spanStates.setPendingStyles(blockId, setOf(link, SpanStyle.Bold))
        val observer = SpanMaintenanceTextObserver(
            blockId = blockId,
            textStates = textStates,
            spanStates = spanStates,
            initialVisibleText = "Hi",
        )

        observer.onCommittedVisibleText("Hi!")

        assertEquals(listOf(TextSpan(2, 3, SpanStyle.Bold)), spanStates.getSpans(blockId))
        assertNull(spanStates.getPendingStyles(blockId))
    }
}
