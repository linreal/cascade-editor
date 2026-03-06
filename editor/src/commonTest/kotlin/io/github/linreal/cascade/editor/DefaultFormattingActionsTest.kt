package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.DefaultFormattingActions
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultFormattingActionsTest {

    private val blockTextStates = BlockTextStates()
    private val blockSpanStates = BlockSpanStates()
    private val dispatchedActions = mutableListOf<EditorAction>()
    private val stateHolder = EditorStateHolder()

    private val spanActionDispatcher = SpanActionDispatcher(
        dispatchFn = { dispatchedActions.add(it) },
        blockTextStates = blockTextStates,
        blockSpanStates = blockSpanStates,
    )

    private val actions = DefaultFormattingActions(
        stateHolder = stateHolder,
        blockTextStates = blockTextStates,
        spanActionDispatcher = spanActionDispatcher,
    )

    private val blockId = BlockId("test")

    /**
     * Sets up a focused text block with the given text, spans, and selection.
     * Selection coordinates are in visible text space (sentinel offset handled internally).
     */
    private fun setupFocusedBlock(
        text: String,
        spans: List<TextSpan> = emptyList(),
        blockType: BlockType = BlockType.Paragraph,
        selectionStart: Int = 0,
        selectionEnd: Int = selectionStart,
    ) {
        val block = Block(blockId, blockType, BlockContent.Text(text, spans))
        stateHolder.setState(
            EditorState.withBlocks(listOf(block)).copy(focusedBlockId = blockId)
        )
        val tfs = blockTextStates.getOrCreate(blockId, text)
        // Set selection in raw coordinates (+1 for ZWSP sentinel)
        tfs.edit { selection = TextRange(selectionStart + 1, selectionEnd + 1) }
        blockSpanStates.getOrCreate(blockId, spans, text.length)
    }

    // ── Toggle on ranged selection ──────────────────────────────────────

    @Test
    fun `toggleStyle on ranged selection applies style via dispatcher`() {
        setupFocusedBlock("Hello World", selectionStart = 0, selectionEnd = 5)

        actions.toggleStyle(SpanStyle.Bold)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), runtimeSpans[0])
        assertEquals(1, dispatchedActions.size)
        assertTrue(dispatchedActions[0] is UpdateBlockContent)
    }

    @Test
    fun `toggleStyle on ranged selection removes fully active style`() {
        setupFocusedBlock(
            "Hello World",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
            selectionStart = 0,
            selectionEnd = 5,
        )

        actions.toggleStyle(SpanStyle.Bold)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertTrue(runtimeSpans.isEmpty())
        assertEquals(1, dispatchedActions.size)
    }

    // ── Toggle on collapsed cursor ──────────────────────────────────────

    @Test
    fun `toggleStyle on collapsed cursor toggles pending style`() {
        setupFocusedBlock("Hello World", selectionStart = 3, selectionEnd = 3)

        actions.toggleStyle(SpanStyle.Bold)

        // No snapshot dispatch for pending styles
        assertTrue(dispatchedActions.isEmpty())
        val pending = blockSpanStates.getPendingStyles(blockId)
        assertNotNull(pending)
        assertTrue(SpanStyle.Bold in pending)
    }

    @Test
    fun `toggleStyle collapsed cursor removes active continuation style`() {
        setupFocusedBlock(
            "Hello World",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
            selectionStart = 3,
            selectionEnd = 3,
        )

        actions.toggleStyle(SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
        val pending = blockSpanStates.getPendingStyles(blockId)
        assertNotNull(pending)
        assertTrue(SpanStyle.Bold !in pending)
    }

    // ── No-op: no focus ─────────────────────────────────────────────────

    @Test
    fun `toggleStyle no-op when no block is focused`() {
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello"))
        stateHolder.setState(
            EditorState.withBlocks(listOf(block)) // no focusedBlockId
        )
        blockTextStates.getOrCreate(blockId, "Hello")
        blockSpanStates.getOrCreate(blockId, emptyList(), 5)

        actions.toggleStyle(SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
        assertTrue(blockSpanStates.getSpans(blockId).isEmpty())
    }

    // ── No-op: Code block ───────────────────────────────────────────────

    @Test
    fun `toggleStyle no-op when focused block is Code`() {
        setupFocusedBlock(
            "Hello World",
            blockType = BlockType.Code(),
            selectionStart = 0,
            selectionEnd = 5,
        )

        actions.toggleStyle(SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
    }

    // ── No-op: block selection active ───────────────────────────────────

    @Test
    fun `toggleStyle no-op when block selection is active`() {
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello"))
        stateHolder.setState(
            EditorState.withBlocks(listOf(block)).copy(
                focusedBlockId = blockId,
                selectedBlockIds = setOf(blockId),
            )
        )
        val tfs = blockTextStates.getOrCreate(blockId, "Hello")
        tfs.edit { selection = TextRange(1, 6) } // full visible selection
        blockSpanStates.getOrCreate(blockId, emptyList(), 5)

        actions.toggleStyle(SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
    }

    // ── No-op: dragging ─────────────────────────────────────────────────

    @Test
    fun `toggleStyle no-op when dragging`() {
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello"))
        stateHolder.setState(
            EditorState.withBlocks(listOf(block)).copy(
                focusedBlockId = blockId,
                dragState = io.github.linreal.cascade.editor.state.DragState(
                    draggingBlockIds = setOf(blockId),
                    targetIndex = null,
                ),
            )
        )
        val tfs = blockTextStates.getOrCreate(blockId, "Hello")
        tfs.edit { selection = TextRange(1, 6) }
        blockSpanStates.getOrCreate(blockId, emptyList(), 5)

        actions.toggleStyle(SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
    }

    // ── No-op: non-text block ───────────────────────────────────────────

    @Test
    fun `toggleStyle no-op when focused block is non-text type`() {
        val block = Block(blockId, BlockType.Divider, BlockContent.Empty)
        stateHolder.setState(
            EditorState.withBlocks(listOf(block)).copy(focusedBlockId = blockId)
        )

        actions.toggleStyle(SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
    }

    // ── Fresh selection resolution ──────────────────────────────────────

    @Test
    fun `action resolves fresh selection at invocation time`() {
        setupFocusedBlock("Hello World", selectionStart = 0, selectionEnd = 5)

        // First toggle applies Bold to [0, 5)
        actions.toggleStyle(SpanStyle.Bold)
        assertEquals(1, dispatchedActions.size)
        val spans1 = blockSpanStates.getSpans(blockId)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), spans1)

        // Change selection to [6, 11) — actions must pick up the new selection
        val tfs = blockTextStates.get(blockId)!!
        tfs.edit { selection = TextRange(7, 12) } // raw coords: +1 for sentinel

        dispatchedActions.clear()
        actions.toggleStyle(SpanStyle.Italic)

        assertEquals(1, dispatchedActions.size)
        val spans2 = blockSpanStates.getSpans(blockId)
        assertEquals(2, spans2.size)
        // Bold on [0,5) + Italic on [6,11)
        assertTrue(spans2.any { it.style == SpanStyle.Bold && it.start == 0 && it.end == 5 })
        assertTrue(spans2.any { it.style == SpanStyle.Italic && it.start == 6 && it.end == 11 })
    }

    // ── applyStyle and removeStyle pass through ─────────────────────────

    @Test
    fun `applyStyle delegates to dispatcher on ranged selection`() {
        setupFocusedBlock("Hello World", selectionStart = 0, selectionEnd = 5)

        actions.applyStyle(SpanStyle.Italic)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Italic), runtimeSpans[0])
        assertEquals(1, dispatchedActions.size)
    }

    @Test
    fun `removeStyle delegates to dispatcher on ranged selection`() {
        setupFocusedBlock(
            "Hello World",
            spans = listOf(TextSpan(0, 11, SpanStyle.Bold)),
            selectionStart = 0,
            selectionEnd = 5,
        )

        actions.removeStyle(SpanStyle.Bold)

        val runtimeSpans = blockSpanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(5, 11, SpanStyle.Bold), runtimeSpans[0])
    }

    @Test
    fun `applyStyle no-op when no focus`() {
        actions.applyStyle(SpanStyle.Bold)
        assertTrue(dispatchedActions.isEmpty())
    }

    @Test
    fun `removeStyle no-op when no focus`() {
        actions.removeStyle(SpanStyle.Bold)
        assertTrue(dispatchedActions.isEmpty())
    }
}
