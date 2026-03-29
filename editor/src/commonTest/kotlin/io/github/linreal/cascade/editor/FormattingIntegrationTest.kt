package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.richtext.DefaultFormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.FormattingStateCalculator
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.DragState
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.RichTextToolbarConfig
import io.github.linreal.cascade.editor.ui.ToolbarButtonSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for Task 10 — Selection Status, Toolbar Contracts, and Formatting UI.
 *
 * These tests exercise the full non-Compose integration paths:
 * FormattingStateCalculator + DefaultFormattingActions + BlockSpanStates +
 * DefaultBlockCallbacks + SpanActionDispatcher working together.
 */
class FormattingIntegrationTest {

    private val defaultTracked = RichTextToolbarConfig.Default.buttons.map { it.style }

 // Helpers

    /**
     * Full integration harness with stateHolder, text/span states, actions, and callbacks.
     */
    private class Harness(
        blocks: List<Block>,
        focusedBlockId: BlockId? = null,
        selectedBlockIds: Set<BlockId> = emptySet(),
        dragState: DragState? = null,
    ) {
        val dispatched = mutableListOf<EditorAction>()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val stateHolder = EditorStateHolder(
            EditorState(
                blocks = blocks,
                focusedBlockId = focusedBlockId,
                selectedBlockIds = selectedBlockIds,
                dragState = dragState,
                slashCommandState = null,
            )
        )

        val spanActionDispatcher = SpanActionDispatcher(
            dispatchFn = { action ->
                dispatched.add(action)
                stateHolder.dispatch(action)
            },
            textStates = textStates,
            spanStates = spanStates,
        )

        val formattingActions = DefaultFormattingActions(
            stateHolder = stateHolder,
            textStates = textStates,
            spanActionDispatcher = spanActionDispatcher,
        )

        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { action ->
                dispatched.add(action)
                stateHolder.dispatch(action)
            },
            stateProvider = { stateHolder.state },
            textStates = textStates,
            spanStates = spanStates,
        )

        /**
         * Initialize runtime text + span state for a block.
         * Selection coordinates are in visible text space (sentinel offset applied internally).
         */
        fun initBlock(
            blockId: BlockId,
            text: String,
            spans: List<TextSpan> = emptyList(),
            selectionStart: Int = 0,
            selectionEnd: Int = selectionStart,
        ) {
            textStates.getOrCreate(blockId, text)
            spanStates.getOrCreate(blockId, spans, text.length)
            val tfs = textStates.get(blockId)!!
            tfs.edit { selection = TextRange(selectionStart + 1, selectionEnd + 1) }
        }

        /**
         * Compute FormattingState for current stateHolder snapshot + runtime state.
         */
        fun computeFormattingState(
            trackedStyles: List<SpanStyle> = RichTextToolbarConfig.Default.buttons.map { it.style },
        ): FormattingState {
            val state = stateHolder.state
            val blockId = state.focusedBlockId
            val blockType = blockId?.let { state.getBlock(it)?.type }
            val hasBlockSelection = state.selectedBlockIds.isNotEmpty()
            val isDragging = state.dragState != null

            val tfs = blockId?.let { textStates.get(it) }
            // Simulate visibleSelection: raw selection - 1, clamped to 0
            val rawSel = tfs?.selection ?: TextRange(0, 0)
            val selStart = (rawSel.start - 1).coerceAtLeast(0)
            val selEnd = (rawSel.end - 1).coerceAtLeast(0)

            val spans = blockId?.let { spanStates.getSpans(it) } ?: emptyList()
            val pendingStyles = blockId?.let { spanStates.getPendingStyles(it) }

            return FormattingStateCalculator.compute(
                focusedBlockId = blockId,
                focusedBlockType = blockType,
                hasBlockSelection = hasBlockSelection,
                isDragging = isDragging,
                visibleSelectionStart = selStart,
                visibleSelectionEnd = selEnd,
                spans = spans,
                pendingStyles = pendingStyles,
                trackedStyles = trackedStyles,
            )
        }
    }

 // 1. Focus/unfocus cycle produces correct state

    @Test
    fun `focus on styled block shows active styles then unfocus clears`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello", listOf(TextSpan(0, 5, SpanStyle.Bold))))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello", listOf(TextSpan(0, 5, SpanStyle.Bold)), selectionStart = 3)

        val focused = harness.computeFormattingState()
        assertTrue(focused.canFormat)
        assertEquals(StyleStatus.FullyActive, focused.styleStatusOf(SpanStyle.Bold))

        // Unfocus
        harness.stateHolder.setState(harness.stateHolder.state.copy(focusedBlockId = null))
        val unfocused = harness.computeFormattingState()
        assertFalse(unfocused.canFormat)
        assertTrue(unfocused.styles.isEmpty())
    }

    @Test
    fun `focus switch between blocks with different styles`() {
        val b1 = BlockId("b1")
        val b2 = BlockId("b2")
        val block1 = Block(b1, BlockType.Paragraph, BlockContent.Text("Bold", listOf(TextSpan(0, 4, SpanStyle.Bold))))
        val block2 = Block(b2, BlockType.Paragraph, BlockContent.Text("Italic", listOf(TextSpan(0, 6, SpanStyle.Italic))))
        val harness = Harness(blocks = listOf(block1, block2), focusedBlockId = b1)
        harness.initBlock(b1, "Bold", listOf(TextSpan(0, 4, SpanStyle.Bold)), selectionStart = 2)
        harness.initBlock(b2, "Italic", listOf(TextSpan(0, 6, SpanStyle.Italic)), selectionStart = 3)

        val state1 = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, state1.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.Absent, state1.styleStatusOf(SpanStyle.Italic))

        // Switch focus to block 2
        harness.stateHolder.setState(harness.stateHolder.state.copy(focusedBlockId = b2))
        val state2 = harness.computeFormattingState()
        assertEquals(StyleStatus.Absent, state2.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.FullyActive, state2.styleStatusOf(SpanStyle.Italic))
    }

 // 2. Pending style state for empty block

    @Test
    fun `empty block with pending Bold shows Bold FullyActive in formatting state`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text(""))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "", selectionStart = 0)
        harness.spanStates.setPendingStyles(blockId, setOf(SpanStyle.Bold))

        val state = harness.computeFormattingState()
        assertTrue(state.canFormat)
        assertEquals(StyleStatus.FullyActive, state.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.Absent, state.styleStatusOf(SpanStyle.Italic))
    }

    @Test
    fun `empty block tap Bold toggles pending then shows Bold active`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text(""))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "", selectionStart = 0)

        // Before toggle: no pending, position 0 → all Absent
        val before = harness.computeFormattingState()
        assertEquals(StyleStatus.Absent, before.styleStatusOf(SpanStyle.Bold))

        // Toggle Bold via formatting actions (collapsed cursor → sets pending)
        harness.formattingActions.toggleStyle(SpanStyle.Bold)

        val after = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, after.styleStatusOf(SpanStyle.Bold))
    }

 // 3. Toolbar disabled during drag

    @Test
    fun `formatting state shows canFormat false when dragging`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello"))
        val harness = Harness(
            blocks = listOf(block),
            focusedBlockId = blockId,
            dragState = DragState(draggingBlockIds = setOf(blockId), targetIndex = null),
        )
        harness.initBlock(blockId, "Hello", selectionStart = 3)

        val state = harness.computeFormattingState()
        assertFalse(state.canFormat)
    }

    @Test
    fun `formatting actions are no-op during drag`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello"))
        val harness = Harness(
            blocks = listOf(block),
            focusedBlockId = blockId,
            dragState = DragState(draggingBlockIds = setOf(blockId), targetIndex = null),
        )
        harness.initBlock(blockId, "Hello", selectionStart = 0, selectionEnd = 5)

        harness.formattingActions.toggleStyle(SpanStyle.Bold)
        assertTrue(harness.dispatched.isEmpty())
        assertTrue(harness.spanStates.getSpans(blockId).isEmpty())
    }

 // 4. Same-style cursor move produces structurally equal state

    @Test
    fun `cursor movement within same styled region produces equal FormattingState`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello World", listOf(TextSpan(0, 11, SpanStyle.Bold))))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello World", listOf(TextSpan(0, 11, SpanStyle.Bold)), selectionStart = 2)

        val state1 = harness.computeFormattingState()

        // Move cursor to different position within the same bold range
        harness.textStates.get(blockId)!!.edit { selection = TextRange(6, 6) } // visible pos 5
        val state2 = harness.computeFormattingState()

        // Structural equality: no redundant observer/callback notifications
        assertEquals(state1, state2)
    }

    @Test
    fun `cursor move from styled to unstyled region produces different FormattingState`() {
        val blockId = BlockId("b1")
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello World", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello World", spans, selectionStart = 3) // inside bold

        val styled = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, styled.styleStatusOf(SpanStyle.Bold))

        // Move cursor outside bold range (position 8, position-1=7, outside [0,5))
        harness.textStates.get(blockId)!!.edit { selection = TextRange(9, 9) } // visible pos 8
        val unstyled = harness.computeFormattingState()
        assertEquals(StyleStatus.Absent, unstyled.styleStatusOf(SpanStyle.Bold))

        // States are NOT equal → callback would fire
        assertTrue(styled != unstyled)
    }

 // 5. Enter at end of bold → new block toolbar shows Bold active

    @Test
    fun `Enter at end of bold text then new block shows Bold active in formatting state`() {
        val blockId = BlockId("b1")
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello", spans, selectionStart = 5)

        // Perform Enter split at end of text
        harness.callbacks.onEnter(blockId, cursorPosition = 5)

        // Find new block ID from the dispatched SplitBlock
        val splitAction = harness.dispatched.filterIsInstance<SplitBlock>().first()
        val newBlockId = splitAction.newBlockId
        assertNotNull(newBlockId)

        // New block's pending styles should carry Bold continuation
        val pendingOnNew = harness.spanStates.getPendingStyles(newBlockId)
        assertNotNull(pendingOnNew)
        assertTrue(SpanStyle.Bold in pendingOnNew)

        // Simulate focus moving to new block (as SplitBlock reducer does)
        // Initialize text state for new block (empty text, cursor at 0)
        harness.textStates.getOrCreate(newBlockId, "")
        harness.textStates.get(newBlockId)!!.edit { selection = TextRange(1, 1) } // raw 1 = visible 0

        // Compute formatting state for new block
        val state = FormattingStateCalculator.compute(
            focusedBlockId = newBlockId,
            focusedBlockType = BlockType.Paragraph,
            hasBlockSelection = false,
            isDragging = false,
            visibleSelectionStart = 0,
            visibleSelectionEnd = 0,
            spans = harness.spanStates.getSpans(newBlockId),
            pendingStyles = harness.spanStates.getPendingStyles(newBlockId),
            trackedStyles = defaultTracked,
        )

        assertTrue(state.canFormat)
        assertEquals(StyleStatus.FullyActive, state.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.Absent, state.styleStatusOf(SpanStyle.Italic))
    }

 // 6. Toggle style + calculator consistency

    @Test
    fun `toggle Bold on ranged selection then calculator shows Bold FullyActive`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello World"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello World", selectionStart = 0, selectionEnd = 5)

        // Before toggle
        val before = harness.computeFormattingState()
        assertEquals(StyleStatus.Absent, before.styleStatusOf(SpanStyle.Bold))

        // Toggle Bold
        harness.formattingActions.toggleStyle(SpanStyle.Bold)

        // After toggle: ranged selection [0,5) is now Bold
        val after = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, after.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `toggle Bold twice removes it and calculator shows Absent`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello World"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello World", selectionStart = 0, selectionEnd = 5)

        harness.formattingActions.toggleStyle(SpanStyle.Bold)
        val afterFirst = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, afterFirst.styleStatusOf(SpanStyle.Bold))

        harness.formattingActions.toggleStyle(SpanStyle.Bold)
        val afterSecond = harness.computeFormattingState()
        assertEquals(StyleStatus.Absent, afterSecond.styleStatusOf(SpanStyle.Bold))
    }

 // 7. Multi-block selection disables formatting

    @Test
    fun `multi-block selection makes canFormat false and actions no-op`() {
        val b1 = BlockId("b1")
        val b2 = BlockId("b2")
        val block1 = Block(b1, BlockType.Paragraph, BlockContent.Text("Hello"))
        val block2 = Block(b2, BlockType.Paragraph, BlockContent.Text("World"))
        val harness = Harness(
            blocks = listOf(block1, block2),
            selectedBlockIds = setOf(b1, b2),
        )
        harness.initBlock(b1, "Hello", selectionStart = 0, selectionEnd = 5)
        harness.initBlock(b2, "World")

        val state = harness.computeFormattingState()
        assertFalse(state.canFormat)

        // Actions should be no-op
        harness.formattingActions.toggleStyle(SpanStyle.Bold)
        assertTrue(harness.dispatched.isEmpty())
    }

 // 9. Default toolbar config extensibility

    @Test
    fun `default toolbar config has expected V1 buttons`() {
        val config = RichTextToolbarConfig.Default
        assertEquals(6, config.buttons.size)
        assertEquals(SpanStyle.Bold, config.buttons[0].style)
        assertEquals(SpanStyle.Italic, config.buttons[1].style)
        assertEquals(SpanStyle.Underline, config.buttons[2].style)
        assertEquals(SpanStyle.StrikeThrough, config.buttons[3].style)
        assertEquals(SpanStyle.InlineCode, config.buttons[4].style)
        assertTrue(config.buttons[5].style is SpanStyle.Highlight)
    }

    @Test
    fun `adding custom button to config is data-only change`() {
        val custom = RichTextToolbarConfig(
            buttons = RichTextToolbarConfig.Default.buttons + ToolbarButtonSpec(
                style = SpanStyle.Link("https://example.com"),
                label = "Link",
            )
        )
        assertEquals(7, custom.buttons.size)
        assertTrue(custom.buttons.last().style is SpanStyle.Link)
    }

    @Test
    fun `custom config tracked styles flow into calculator`() {
        val blockId = BlockId("b1")
        val linkUrl = "https://example.com"
        val spans = listOf(TextSpan(0, 5, SpanStyle.Link(linkUrl)))
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello", spans, selectionStart = 0, selectionEnd = 5)

        // Default tracked styles don't include Link
        val defaultState = harness.computeFormattingState(trackedStyles = defaultTracked)
        assertEquals(StyleStatus.Absent, defaultState.styleStatusOf(SpanStyle.Link(linkUrl)))

        // Custom tracked styles include Link
        val customTracked = defaultTracked + SpanStyle.Link(linkUrl)
        val customState = harness.computeFormattingState(trackedStyles = customTracked)
        assertEquals(StyleStatus.FullyActive, customState.styleStatusOf(SpanStyle.Link(linkUrl)))
    }

 // 10. Cursor at end of bold + type scenario

    @Test
    fun `cursor at end of bold text shows Bold active via continuation semantics`() {
        val blockId = BlockId("b1")
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello", spans, selectionStart = 5) // cursor at end

        val state = harness.computeFormattingState()
        assertTrue(state.canFormat)
        assertTrue(state.selectionCollapsed)
        assertEquals(StyleStatus.FullyActive, state.styleStatusOf(SpanStyle.Bold))
    }

 // 11. Multiple styles overlap correctly in status

    @Test
    fun `overlapping Bold and Italic both show FullyActive for covered range`() {
        val blockId = BlockId("b1")
        val spans = listOf(
            TextSpan(0, 10, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.Italic),
        )
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello Wrld", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello Wrld", spans, selectionStart = 3, selectionEnd = 8)

        val state = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, state.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.FullyActive, state.styleStatusOf(SpanStyle.Italic))
    }

    @Test
    fun `selection wider than Italic span shows Partial for Italic`() {
        val blockId = BlockId("b1")
        val spans = listOf(
            TextSpan(0, 10, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.Italic),
        )
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello Wrld", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello Wrld", spans, selectionStart = 0, selectionEnd = 10)

        val state = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, state.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.Partial, state.styleStatusOf(SpanStyle.Italic))
    }

 // 12. FormattingState.Empty companion

    @Test
    fun `FormattingState Empty has correct defaults`() {
        val empty = FormattingState.Empty
        assertFalse(empty.canFormat)
        assertTrue(empty.styles.isEmpty())
        assertEquals(null, empty.focusedBlockId)
        assertTrue(empty.selectionCollapsed)
        assertEquals(StyleStatus.Absent, empty.styleStatusOf(SpanStyle.Bold))
    }

 // 13. Backspace merge preserves formatting state continuity

    @Test
    fun `backspace merge updates runtime spans then calculator reflects merged state`() {
        val b1 = BlockId("b1")
        val b2 = BlockId("b2")
        val spans1 = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val spans2 = listOf(TextSpan(0, 5, SpanStyle.Italic))
        val block1 = Block(b1, BlockType.Paragraph, BlockContent.Text("Hello", spans1))
        val block2 = Block(b2, BlockType.Paragraph, BlockContent.Text("World", spans2))
        val harness = Harness(blocks = listOf(block1, block2), focusedBlockId = b2)
        harness.initBlock(b1, "Hello", spans1)
        harness.initBlock(b2, "World", spans2, selectionStart = 0)

        harness.callbacks.onBackspaceAtStart(b2)

        // After merge: b1 should have "HelloWorld" with Bold[0,5) + Italic[5,10)
        val mergedSpans = harness.spanStates.getSpans(b1)
        assertEquals(2, mergedSpans.size)
        assertTrue(mergedSpans.any { it.style == SpanStyle.Bold && it.start == 0 && it.end == 5 })
        assertTrue(mergedSpans.any { it.style == SpanStyle.Italic && it.start == 5 && it.end == 10 })

        // Calculator for merged block: cursor at merge point (5), position-1=4 → Bold
        val state = FormattingStateCalculator.compute(
            focusedBlockId = b1,
            focusedBlockType = BlockType.Paragraph,
            hasBlockSelection = false,
            isDragging = false,
            visibleSelectionStart = 5,
            visibleSelectionEnd = 5,
            spans = mergedSpans,
            pendingStyles = harness.spanStates.getPendingStyles(b1),
            trackedStyles = defaultTracked,
        )
        assertEquals(StyleStatus.FullyActive, state.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.Absent, state.styleStatusOf(SpanStyle.Italic))
    }

    @Test
    fun `forward delete merge updates runtime and snapshot with shifted spans`() {
        val b1 = BlockId("b1")
        val b2 = BlockId("b2")
        val spans1 = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val spans2 = listOf(TextSpan(0, 5, SpanStyle.Italic))
        val block1 = Block(b1, BlockType.Paragraph, BlockContent.Text("Hello", spans1))
        val block2 = Block(b2, BlockType.Paragraph, BlockContent.Text("World", spans2))
        val harness = Harness(blocks = listOf(block1, block2), focusedBlockId = b1)
        harness.initBlock(b1, "Hello", spans1, selectionStart = 5)
        harness.initBlock(b2, "World", spans2, selectionStart = 0)

        harness.callbacks.onDeleteAtEnd(b1)

        val mergedSpans = harness.spanStates.getSpans(b1)
        assertEquals(2, mergedSpans.size)
        assertTrue(mergedSpans.any { it.style == SpanStyle.Bold && it.start == 0 && it.end == 5 })
        assertTrue(mergedSpans.any { it.style == SpanStyle.Italic && it.start == 5 && it.end == 10 })
        assertEquals(null, harness.spanStates.get(b2))

        val snapshotMerged = harness.stateHolder.state.getBlock(b1)?.content as? BlockContent.Text
        assertNotNull(snapshotMerged)
        assertEquals("HelloWorld", snapshotMerged.text)
        assertEquals(mergedSpans, snapshotMerged.spans)

        val updateIndex = harness.dispatched.indexOfFirst { action ->
            action is UpdateBlockContent && action.blockId == b1
        }
        val deleteIndex = harness.dispatched.indexOfFirst { action ->
            action is DeleteBlock && action.blockId == b2
        }
        assertTrue(updateIndex >= 0 && deleteIndex > updateIndex)
    }

 // 14. Apply + remove via actions maintain snapshot consistency

    @Test
    fun `apply style updates both runtime and snapshot`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello World"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello World", selectionStart = 0, selectionEnd = 5)

        harness.formattingActions.applyStyle(SpanStyle.Bold)

        // Runtime
        val runtimeSpans = harness.spanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), runtimeSpans[0])

        // Snapshot (updated via UpdateBlockContent dispatch)
        val snapshotContent = harness.stateHolder.state.getBlock(blockId)?.content as? BlockContent.Text
        assertNotNull(snapshotContent)
        assertEquals(1, snapshotContent.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), snapshotContent.spans[0])
    }

    @Test
    fun `remove style updates both runtime and snapshot`() {
        val blockId = BlockId("b1")
        val spans = listOf(TextSpan(0, 11, SpanStyle.Bold))
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello World", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello World", spans, selectionStart = 0, selectionEnd = 5)

        harness.formattingActions.removeStyle(SpanStyle.Bold)

        // Runtime: Bold removed from [0,5), remaining [5,11)
        val runtimeSpans = harness.spanStates.getSpans(blockId)
        assertEquals(1, runtimeSpans.size)
        assertEquals(TextSpan(5, 11, SpanStyle.Bold), runtimeSpans[0])

        // Snapshot matches runtime
        val snapshotContent = harness.stateHolder.state.getBlock(blockId)?.content as? BlockContent.Text
        assertNotNull(snapshotContent)
        assertEquals(runtimeSpans, snapshotContent.spans)
    }

 // 15. Collapsed cursor toggle pending + continuation combined

    @Test
    fun `collapsed cursor toggle sets pending then second toggle removes it`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello", selectionStart = 3)

        // First toggle: adds Bold to pending
        harness.formattingActions.toggleStyle(SpanStyle.Bold)
        val state1 = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, state1.styleStatusOf(SpanStyle.Bold))

        // Second toggle: removes Bold from pending
        harness.formattingActions.toggleStyle(SpanStyle.Bold)
        val state2 = harness.computeFormattingState()
        assertEquals(StyleStatus.Absent, state2.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `collapsed cursor in bold range toggle removes bold from pending`() {
        val blockId = BlockId("b1")
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val block = Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello", spans))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Hello", spans, selectionStart = 3) // inside bold

        // Before toggle: continuation shows Bold active
        val before = harness.computeFormattingState()
        assertEquals(StyleStatus.FullyActive, before.styleStatusOf(SpanStyle.Bold))

        // Toggle: removes Bold from pending (since it was active via continuation)
        harness.formattingActions.toggleStyle(SpanStyle.Bold)
        val after = harness.computeFormattingState()
        assertEquals(StyleStatus.Absent, after.styleStatusOf(SpanStyle.Bold))
    }

 // 16. Non-text block type

    @Test
    fun `Divider block returns canFormat false`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Divider, BlockContent.Empty)
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)

        val state = harness.computeFormattingState()
        assertFalse(state.canFormat)
    }

 // 17. All text-supporting block types allow formatting

    @Test
    fun `Heading block allows formatting`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Heading(2), BlockContent.Text("Title"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Title", selectionStart = 3)

        assertTrue(harness.computeFormattingState().canFormat)
    }

    @Test
    fun `Todo block allows formatting`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Todo(), BlockContent.Text("Task"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Task", selectionStart = 2)

        assertTrue(harness.computeFormattingState().canFormat)
    }

    @Test
    fun `BulletList block allows formatting`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.BulletList, BlockContent.Text("Item"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Item", selectionStart = 2)

        assertTrue(harness.computeFormattingState().canFormat)
    }

    @Test
    fun `Quote block allows formatting`() {
        val blockId = BlockId("b1")
        val block = Block(blockId, BlockType.Quote, BlockContent.Text("Quote"))
        val harness = Harness(blocks = listOf(block), focusedBlockId = blockId)
        harness.initBlock(blockId, "Quote", selectionStart = 3)

        assertTrue(harness.computeFormattingState().canFormat)
    }
}
