package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpanActionDispatcherTest {

    private val blockTextStates = BlockTextStates()
    private val blockSpanStates = BlockSpanStates()
    private val dispatchedActions = mutableListOf<EditorAction>()

    private val dispatcher = SpanActionDispatcher(
        dispatchFn = { dispatchedActions.add(it) },
        blockTextStates = blockTextStates,
        blockSpanStates = blockSpanStates,
    )

    private val blockId = BlockId("test")

    private fun setupBlock(text: String, spans: List<TextSpan> = emptyList()) {
        blockTextStates.getOrCreate(blockId, text)
        blockSpanStates.getOrCreate(blockId, spans, text.length)
    }

 // applyStyle

    @Test
    fun `applyStyle updates runtime and syncs snapshot via UpdateBlockContent`() {
        setupBlock("Hello World")

        dispatcher.applyStyle(blockId, 0, 5, SpanStyle.Bold)

        // Runtime updated
        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), runtimeSpans[0])

        // Snapshot synced via UpdateBlockContent (carries runtime text + spans)
        assertEquals(1, dispatchedActions.size)
        val action = dispatchedActions[0] as UpdateBlockContent
        assertEquals(blockId, action.blockId)
        val content = action.content as BlockContent.Text
        assertEquals("Hello World", content.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), content.spans)
    }

    @Test
    fun `applyStyle no-op for unknown block`() {
        dispatcher.applyStyle(blockId, 0, 5, SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
    }

 // removeStyle

    @Test
    fun `removeStyle updates runtime and syncs snapshot via UpdateBlockContent`() {
        setupBlock("Hello World", listOf(TextSpan(0, 11, SpanStyle.Bold)))

        dispatcher.removeStyle(blockId, 0, 5, SpanStyle.Bold)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(5, 11, SpanStyle.Bold), runtimeSpans[0])

        assertEquals(1, dispatchedActions.size)
        val action = dispatchedActions[0] as UpdateBlockContent
        val content = action.content as BlockContent.Text
        assertEquals("Hello World", content.text)
        assertEquals(listOf(TextSpan(5, 11, SpanStyle.Bold)), content.spans)
    }

    @Test
    fun `removeStyle no-op for unknown block`() {
        dispatcher.removeStyle(blockId, 0, 5, SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
    }

 // toggleStyle (non-collapsed)

    @Test
    fun `toggleStyle applies when absent`() {
        setupBlock("Hello World")

        dispatcher.toggleStyle(blockId, 0, 5, SpanStyle.Bold)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), runtimeSpans[0])

        assertTrue(dispatchedActions[0] is UpdateBlockContent)
    }

    @Test
    fun `toggleStyle removes when fully active`() {
        setupBlock("Hello World", listOf(TextSpan(0, 5, SpanStyle.Bold)))

        dispatcher.toggleStyle(blockId, 0, 5, SpanStyle.Bold)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertTrue(runtimeSpans.isEmpty())

        assertTrue(dispatchedActions[0] is UpdateBlockContent)
    }

    @Test
    fun `toggleStyle applies when partial`() {
        setupBlock("Hello World", listOf(TextSpan(0, 3, SpanStyle.Bold)))

        dispatcher.toggleStyle(blockId, 0, 5, SpanStyle.Bold)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), runtimeSpans[0])

        assertTrue(dispatchedActions[0] is UpdateBlockContent)
    }

    @Test
    fun `toggleStyle no-op for unknown block`() {
        dispatcher.toggleStyle(blockId, 0, 5, SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
    }

 // toggleStyle (collapsed cursor — pending styles)

    @Test
    fun `toggleStyle collapsed cursor adds pending style when absent`() {
        setupBlock("Hello World")

        dispatcher.toggleStyle(blockId, 3, 3, SpanStyle.Bold)

        // No snapshot dispatch for pending styles
        assertTrue(dispatchedActions.isEmpty())

        // Pending style set
        val pending = blockSpanStates.getPendingStyles(blockId)
        assertTrue(pending != null && SpanStyle.Bold in pending)
    }

    @Test
    fun `toggleStyle collapsed cursor removes pending style when active from spans`() {
        setupBlock("Hello World", listOf(TextSpan(0, 11, SpanStyle.Bold)))

        dispatcher.toggleStyle(blockId, 3, 3, SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())

        // Bold was active at cursor from spans, toggling removes it from pending
        val pending = blockSpanStates.getPendingStyles(blockId)
        assertTrue(pending != null && SpanStyle.Bold !in pending)
    }

    @Test
    fun `toggleStyle collapsed cursor at span end removes continuation style`() {
        setupBlock("Hello World", listOf(TextSpan(0, 5, SpanStyle.Bold)))

        // Cursor at end of span: insertion continuation checks position-1.
        dispatcher.toggleStyle(blockId, 5, 5, SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
        val pending = blockSpanStates.getPendingStyles(blockId)
        assertTrue(pending != null && SpanStyle.Bold !in pending)
    }

    @Test
    fun `toggleStyle collapsed cursor toggles within existing pending styles`() {
        setupBlock("Hello World")
        // Pre-set pending styles with Italic
        blockSpanStates.setPendingStyles(blockId, setOf(SpanStyle.Italic))

        // Toggle Bold on — should add to existing pending
        dispatcher.toggleStyle(blockId, 3, 3, SpanStyle.Bold)

        val pending = blockSpanStates.getPendingStyles(blockId)
        assertTrue(pending != null)
        assertTrue(SpanStyle.Bold in pending!!)
        assertTrue(SpanStyle.Italic in pending)

        // Toggle Bold off — should remove from pending
        dispatcher.toggleStyle(blockId, 3, 3, SpanStyle.Bold)

        val pendingAfter = blockSpanStates.getPendingStyles(blockId)
        assertTrue(pendingAfter != null)
        assertTrue(SpanStyle.Bold !in pendingAfter!!)
        assertTrue(SpanStyle.Italic in pendingAfter)
    }

    @Test
    fun `toggleStyle collapsed cursor no-op for unknown block`() {
        dispatcher.toggleStyle(blockId, 3, 3, SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
        assertNull(blockSpanStates.getPendingStyles(blockId))
    }

 // Coordinated consistency

    @Test
    fun `applyStyle snapshot carries runtime text and updated spans`() {
        setupBlock("Hello World")

        dispatcher.applyStyle(blockId, 2, 8, SpanStyle.Italic)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(listOf(TextSpan(2, 8, SpanStyle.Italic)), runtimeSpans)

        val action = dispatchedActions[0] as UpdateBlockContent
        val content = action.content as BlockContent.Text
        assertEquals("Hello World", content.text)
        assertEquals(listOf(TextSpan(2, 8, SpanStyle.Italic)), content.spans)
    }

    @Test
    fun `multiple dispatches accumulate correctly in runtime`() {
        setupBlock("Hello World")

        dispatcher.applyStyle(blockId, 0, 5, SpanStyle.Bold)
        dispatcher.applyStyle(blockId, 6, 11, SpanStyle.Italic)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(2, runtimeSpans.size)
        assertEquals(2, dispatchedActions.size)
    }
}
