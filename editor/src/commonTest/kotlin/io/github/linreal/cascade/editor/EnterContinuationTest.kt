package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.MergeBlocks
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.registry.PolicyAwareBlockCallbacks
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnterContinuationTest {

    private val blockId = BlockId("block-1")

    private class TestHarness(
        text: String,
        spans: List<TextSpan> = emptyList(),
        pendingStyles: Set<SpanStyle>? = null,
        blockType: BlockType = BlockType.Paragraph,
    ) {
        val dispatched = mutableListOf<EditorAction>()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val state: EditorState

        init {
            val block = Block(
                id = BlockId("block-1"),
                type = blockType,
                content = BlockContent.Text(text, spans),
            )
            state = EditorState.withBlocks(listOf(block))
            textStates.getOrCreate(BlockId("block-1"), text)
            spanStates.getOrCreate(BlockId("block-1"), spans, text.length)
            if (pendingStyles != null) {
                spanStates.setPendingStyles(BlockId("block-1"), pendingStyles)
            }
        }

        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { dispatched.add(it) },
            stateProvider = { state },
            textStates = textStates,
            spanStates = spanStates,
        )

        fun newBlockId(): BlockId? {
            val split = dispatched.filterIsInstance<SplitBlock>().firstOrNull()
            return split?.newBlockId
        }
    }

    /** Harness for multi-block scenarios (e.g., backspace merge regression). */
    private class MultiBlockHarness(blocks: List<Block>) {
        val dispatched = mutableListOf<EditorAction>()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val state = EditorState.withBlocks(blocks)

        init {
            for (block in blocks) {
                val text = (block.content as? BlockContent.Text)?.text.orEmpty()
                val spans = (block.content as? BlockContent.Text)?.spans.orEmpty()
                textStates.getOrCreate(block.id, text)
                spanStates.getOrCreate(block.id, spans, text.length)
            }
        }

        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { dispatched.add(it) },
            stateProvider = { state },
            textStates = textStates,
            spanStates = spanStates,
        )
    }

 // Pending styles transferred to new block

    @Test
    fun `read-only Enter does not split paragraph`() {
        val harness = TestHarness(text = "Hello")
        val readOnlyCallbacks = PolicyAwareBlockCallbacks(
            delegate = harness.callbacks,
            policy = EditorInteractionPolicy.ReadOnly,
        )

        readOnlyCallbacks.onEnter(blockId, cursorPosition = 5)

        assertEquals(emptyList(), harness.dispatched)
        assertEquals("Hello", harness.textStates.getVisibleText(blockId))
    }

    @Test
    fun `pending styles are transferred to new block on Enter`() {
        val harness = TestHarness(
            text = "Hello",
            pendingStyles = setOf(SpanStyle.Bold, SpanStyle.Italic),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.spanStates.getPendingStyles(newId)
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
        assertNull(harness.spanStates.getPendingStyles(blockId))
    }

 // End-of-block inheritance

    @Test
    fun `cursor at end of bold text inherits bold to new block`() {
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.spanStates.getPendingStyles(newId)
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
        val pendingOnNew = harness.spanStates.getPendingStyles(newId)
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
        val pendingOnNew = harness.spanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertTrue(SpanStyle.Bold in pendingOnNew)
    }

    @Test
    fun `cursor at end of unstyled text does not set pending on new block`() {
        val harness = TestHarness(text = "Hello")

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        assertNull(harness.spanStates.getPendingStyles(newId))
    }

 // Mid-block split: no continuation

    @Test
    fun `mid-block split does not set positional continuation`() {
        // Bold on [0,5), split at position 3 (mid-block, collapsed cursor)
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 3)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        // No positional continuation — new block gets its spans from the split algorithm
        assertNull(harness.spanStates.getPendingStyles(newId))
    }

    @Test
    fun `mid-block collapsed split with pending styles transfers them`() {
        // Collapsed cursor + pending styles = transfer per D2 policy
        val harness = TestHarness(
            text = "Hello",
            pendingStyles = setOf(SpanStyle.Bold),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 3)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        // Collapsed cursor with pending → continuation transfers
        val pendingOnNew = harness.spanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertEquals(setOf(SpanStyle.Bold), pendingOnNew)
    }

    @Test
    fun `ranged selection split does not transfer pending styles`() {
        val harness = TestHarness(
            text = "Hello",
            pendingStyles = setOf(SpanStyle.Bold),
        )
        // Set a ranged (non-collapsed) selection on the TextFieldState
        // Raw coords: +1 for sentinel, so visible [1,4) → raw [2,5)
        harness.textStates.get(blockId)?.edit {
            selection = TextRange(2, 5)
        }

        harness.callbacks.onEnter(blockId, cursorPosition = 1)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        // Ranged split → no continuation per D2 policy
        assertNull(harness.spanStates.getPendingStyles(newId))
    }

    @Test
    fun `ranged selection at end of styled text does not inherit`() {
        val harness = TestHarness(
            text = "Hello",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        // Ranged selection [3,5) → raw [4,6)
        harness.textStates.get(blockId)?.edit {
            selection = TextRange(4, 6)
        }

        harness.callbacks.onEnter(blockId, cursorPosition = 3)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        // Ranged → no continuation even though cursor is at styled position
        assertNull(harness.spanStates.getPendingStyles(newId))
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
        assertNull(harness.spanStates.getPendingStyles(newId))
    }

 // Empty block with pending styles

    @Test
    fun `empty block with pending styles transfers them to new block`() {
        val harness = TestHarness(
            text = "",
            pendingStyles = setOf(SpanStyle.Underline),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        val pendingOnNew = harness.spanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        assertEquals(setOf(SpanStyle.Underline), pendingOnNew)
    }

    @Test
    fun `empty block without pending styles does not set continuation`() {
        val harness = TestHarness(text = "")

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        val newId = harness.newBlockId()
        assertNotNull(newId)
        assertNull(harness.spanStates.getPendingStyles(newId))
    }

 // Pending styles take precedence over position inheritance

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
        val pendingOnNew = harness.spanStates.getPendingStyles(newId)
        assertNotNull(pendingOnNew)
        // Pending wins over position-based Bold
        assertEquals(setOf(SpanStyle.Italic), pendingOnNew)
    }

    @Test
    fun `split id handoff keeps runtime target and dispatched target identical`() {
        val harness = TestHarness(
            text = "HelloWorld",
            spans = listOf(TextSpan(0, 10, SpanStyle.Bold)),
        )

        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        val splitAction = harness.dispatched.filterIsInstance<SplitBlock>().single()
        val dispatchedNewId = splitAction.newBlockId
        assertNotNull(dispatchedNewId)

        val runtimeSpansOnDispatchedTarget = harness.spanStates.getSpans(dispatchedNewId)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), runtimeSpansOnDispatchedTarget)
        assertEquals(
            runtimeSpansOnDispatchedTarget,
            splitAction.newBlockSpans,
            "Runtime split and dispatched payload must reference the same split target ID",
        )
    }

    // --- Empty list item exit ---

    @Test
    fun `enter on empty BulletList dispatches ConvertBlockType to Paragraph`() {
        val harness = TestHarness(text = "", blockType = BlockType.BulletList)

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        val convert = harness.dispatched.filterIsInstance<ConvertBlockType>().singleOrNull()
        assertNotNull(convert, "Expected ConvertBlockType action")
        assertEquals(blockId, convert.blockId)
        assertEquals(BlockType.Paragraph, convert.newType)
        // No SplitBlock should be dispatched
        assertTrue(harness.dispatched.none { it is SplitBlock })
    }

    @Test
    fun `enter on empty NumberedList dispatches ConvertBlockType to Paragraph`() {
        val harness = TestHarness(text = "", blockType = BlockType.NumberedList(3))

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        val convert = harness.dispatched.filterIsInstance<ConvertBlockType>().singleOrNull()
        assertNotNull(convert, "Expected ConvertBlockType action")
        assertEquals(blockId, convert.blockId)
        assertEquals(BlockType.Paragraph, convert.newType)
        assertTrue(harness.dispatched.none { it is SplitBlock })
    }

    @Test
    fun `enter on empty Paragraph still splits normally`() {
        val harness = TestHarness(text = "", blockType = BlockType.Paragraph)

        harness.callbacks.onEnter(blockId, cursorPosition = 0)

        // Should dispatch SplitBlock, NOT ConvertBlockType
        assertTrue(harness.dispatched.any { it is SplitBlock })
        assertTrue(harness.dispatched.none { it is ConvertBlockType })
    }

    @Test
    fun `enter on non-empty BulletList splits normally`() {
        val harness = TestHarness(text = "Item text", blockType = BlockType.BulletList)

        harness.callbacks.onEnter(blockId, cursorPosition = 9)

        // Non-empty list item → split, not convert
        assertTrue(harness.dispatched.any { it is SplitBlock })
        assertTrue(harness.dispatched.none { it is ConvertBlockType })
    }

    @Test
    fun `enter on non-empty NumberedList splits normally`() {
        val harness = TestHarness(text = "Item text", blockType = BlockType.NumberedList(2))

        harness.callbacks.onEnter(blockId, cursorPosition = 9)

        assertTrue(harness.dispatched.any { it is SplitBlock })
        assertTrue(harness.dispatched.none { it is ConvertBlockType })
    }

    @Test
    fun `empty list exit renumbers remaining items via ConvertBlockType reducer`() {
        // Verify at reducer level: converting middle NumberedList to Paragraph splits the run.
        // renumberNumberedLists always starts each run from 1.
        val blocks = listOf(
            Block(BlockId("a"), BlockType.NumberedList(1), BlockContent.Text("First")),
            Block(BlockId("b"), BlockType.NumberedList(2), BlockContent.Text("")),
            Block(BlockId("c"), BlockType.NumberedList(3), BlockContent.Text("Third")),
            Block(BlockId("d"), BlockType.NumberedList(4), BlockContent.Text("Fourth")),
        )
        val state = EditorState.withBlocks(blocks)

        val newState = ConvertBlockType(BlockId("b"), BlockType.Paragraph).reduce(state)

        // Block b is now Paragraph
        assertEquals(BlockType.Paragraph, newState.blocks[1].type)
        // Block a stays 1 (single-item run)
        assertEquals(1, (newState.blocks[0].type as BlockType.NumberedList).number)
        // Block c starts a new run from 1
        assertIs<BlockType.NumberedList>(newState.blocks[2].type)
        assertEquals(1, (newState.blocks[2].type as BlockType.NumberedList).number)
        // Block d is renumbered sequentially: 2
        assertEquals(2, (newState.blocks[3].type as BlockType.NumberedList).number)
    }

    // --- Backspace at start of list item un-lists ---

    @Test
    fun `backspace at start of BulletList converts to Paragraph`() {
        val harness = TestHarness(text = "Some text", blockType = BlockType.BulletList)

        harness.callbacks.onBackspaceAtStart(blockId)

        val convert = harness.dispatched.filterIsInstance<ConvertBlockType>().singleOrNull()
        assertNotNull(convert, "Expected ConvertBlockType action")
        assertEquals(blockId, convert.blockId)
        assertEquals(BlockType.Paragraph, convert.newType)
        // No merge actions should be dispatched
        assertTrue(harness.dispatched.none { it is DeleteBlock })
        assertTrue(harness.dispatched.none { it is MergeBlocks })
    }

    @Test
    fun `backspace at start of NumberedList converts to Paragraph`() {
        val harness = TestHarness(text = "Item text", blockType = BlockType.NumberedList(5))

        harness.callbacks.onBackspaceAtStart(blockId)

        val convert = harness.dispatched.filterIsInstance<ConvertBlockType>().singleOrNull()
        assertNotNull(convert, "Expected ConvertBlockType action")
        assertEquals(blockId, convert.blockId)
        assertEquals(BlockType.Paragraph, convert.newType)
        assertTrue(harness.dispatched.none { it is DeleteBlock })
        assertTrue(harness.dispatched.none { it is MergeBlocks })
    }

    @Test
    fun `backspace at start of empty BulletList converts to Paragraph`() {
        val harness = TestHarness(text = "", blockType = BlockType.BulletList)

        harness.callbacks.onBackspaceAtStart(blockId)

        val convert = harness.dispatched.filterIsInstance<ConvertBlockType>().singleOrNull()
        assertNotNull(convert)
        assertEquals(BlockType.Paragraph, convert.newType)
    }

    @Test
    fun `backspace at start of Paragraph merges with previous block`() {
        val prev = Block(BlockId("prev"), BlockType.Paragraph, BlockContent.Text("Hello"))
        val current = Block(blockId, BlockType.Paragraph, BlockContent.Text("World"))
        val harness = MultiBlockHarness(listOf(prev, current))

        harness.callbacks.onBackspaceAtStart(blockId)

        // Should merge (DeleteBlock dispatched), NOT convert
        assertTrue(harness.dispatched.none { it is ConvertBlockType })
        assertTrue(harness.dispatched.any { it is DeleteBlock })
    }

    @Test
    fun `backspace at start of Heading does not convert to Paragraph`() {
        val prev = Block(BlockId("prev"), BlockType.Paragraph, BlockContent.Text("Hello"))
        val current = Block(blockId, BlockType.Heading(2), BlockContent.Text("Title"))
        val harness = MultiBlockHarness(listOf(prev, current))

        harness.callbacks.onBackspaceAtStart(blockId)

        // Should merge with previous, not convert
        assertTrue(harness.dispatched.none { it is ConvertBlockType })
        assertTrue(harness.dispatched.any { it is DeleteBlock })
    }

    @Test
    fun `backspace un-list preserves text via ConvertBlockType reducer`() {
        // Reducer-level: ConvertBlockType only changes type, text stays intact
        val block = Block(blockId, BlockType.BulletList, BlockContent.Text("Keep this text"))
        val state = EditorState.withBlocks(listOf(block))

        val newState = ConvertBlockType(blockId, BlockType.Paragraph).reduce(state)

        assertEquals(BlockType.Paragraph, newState.blocks[0].type)
        assertEquals("Keep this text", (newState.blocks[0].content as BlockContent.Text).text)
    }

    @Test
    fun `backspace un-list preserves spans via ConvertBlockType reducer`() {
        val spans = listOf(TextSpan(0, 4, SpanStyle.Bold))
        val block = Block(blockId, BlockType.NumberedList(1), BlockContent.Text("Bold", spans))
        val state = EditorState.withBlocks(listOf(block))

        val newState = ConvertBlockType(blockId, BlockType.Paragraph).reduce(state)

        assertEquals(BlockType.Paragraph, newState.blocks[0].type)
        assertEquals(spans, (newState.blocks[0].content as BlockContent.Text).spans)
    }
}
