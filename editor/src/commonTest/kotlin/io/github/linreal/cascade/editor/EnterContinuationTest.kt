package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnterContinuationTest {

    private val blockId = BlockId("block-1")

    private class TestHarness(
        text: String,
        spans: List<TextSpan> = emptyList(),
        pendingStyles: Set<SpanStyle>? = null,
    ) {
        val dispatched = mutableListOf<EditorAction>()
        val blockTextStates = BlockTextStates()
        val blockSpanStates = BlockSpanStates()
        val state: EditorState

        init {
            val block = Block(
                id = BlockId("block-1"),
                type = BlockType.Paragraph,
                content = BlockContent.Text(text, spans),
            )
            state = EditorState.withBlocks(listOf(block))
            blockTextStates.getOrCreate(BlockId("block-1"), text)
            blockSpanStates.getOrCreate(BlockId("block-1"), spans, text.length)
            if (pendingStyles != null) {
                blockSpanStates.setPendingStyles(BlockId("block-1"), pendingStyles)
            }
        }

        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { dispatched.add(it) },
            stateProvider = { state },
            blockTextStates = blockTextStates,
            blockSpanStates = blockSpanStates,
        )

        fun newBlockId(): BlockId? {
            val split = dispatched.filterIsInstance<SplitBlock>().firstOrNull()
            return split?.newBlockId
        }
    }

    // ── Pending styles transferred to new block ──────────────────────────

    @Test
    fun `pending styles are transferred to new block on Enter`() {
        val harness = TestHarness(
            text = "Hello",
            pendingStyles = setOf(SpanStyle.Bold, SpanStyle.Italic),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.blockSpanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic), pendingOnNew)
    }

    @Test
    fun `pending styles on source are cleared after Enter`() {
        val harness = TestHarness(
            text = "Hello",
            pendingStyles = setOf(SpanStyle.Bold),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        // split() clears pending on source
        assertNull(harness.blockSpanStates.getPendingStyles(blockId))
    }

    // ── End-of-block inheritance ─────────────────────────────────────────

    @Test
    fun `cursor at end of bold text inherits bold to new block`() {
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.blockSpanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertTrue(SpanStyle.Bold in pendingOnNew)
    }

    @Test
    fun `cursor at end of multi-styled text inherits all styles`() {
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(
                TextSpan(0, 5, SpanStyle.Bold),
                TextSpan(0, 5, SpanStyle.Italic),
            ),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.blockSpanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic), pendingOnNew)
    }

    @Test
    fun `cursor at end of partially styled text inherits style at last position`() {
        // "Hello" where only [3,5) is bold — cursor at end inherits bold
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(3, 5, SpanStyle.Bold)),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.blockSpanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertTrue(SpanStyle.Bold in pendingOnNew)
    }

    @Test
    fun `cursor at end of unstyled text does not set pending on new block`() {
        val harness = TestHarness(text = "Hello")

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        assertNull(harness.blockSpanStates.getPendingStyles(newId))
    }

    // ── Mid-block split: no continuation ─────────────────────────────────

    @Test
    fun `mid-block split does not transfer pending styles`() {
        // Bold on [0,5), split at position 3 (mid-block)
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 3)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        // No pending styles — new block gets its spans from the split algorithm
        assertNull(harness.blockSpanStates.getPendingStyles(newId))
    }

    @Test
    fun `split at position 0 does not set continuation`() {
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        assertNull(harness.blockSpanStates.getPendingStyles(newId))
    }

    // ── Empty block with pending styles ──────────────────────────────────

    @Test
    fun `empty block with pending styles transfers them to new block`() {
        val harness = TestHarness(
            text = "",
            pendingStyles = setOf(SpanStyle.Underline),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.blockSpanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertEquals(setOf(SpanStyle.Underline), pendingOnNew)
    }

    @Test
    fun `empty block without pending styles does not set continuation`() {
        val harness = TestHarness(text = "")

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        assertNull(harness.blockSpanStates.getPendingStyles(newId))
    }

    // ── Pending styles take precedence over position inheritance ─────────

    @Test
    fun `pending styles override position-based inheritance`() {
        // Bold spans on [0,5), but pending is Italic-only
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
            pendingStyles = setOf(SpanStyle.Italic),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.blockSpanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        // Pending wins over position-based Bold
        assertEquals(setOf(SpanStyle.Italic), pendingOnNew)
    }
}
