package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanMaintenanceTextObserver
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import kotlin.test.Test
import kotlin.test.assertEquals

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
            blockTextStates = textStates,
            blockSpanStates = spanStates,
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
            blockTextStates = textStates,
            blockSpanStates = spanStates,
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
}
