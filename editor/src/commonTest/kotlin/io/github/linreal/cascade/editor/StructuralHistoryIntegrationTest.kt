package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.CompleteDrag
import io.github.linreal.cascade.editor.action.DeleteSelectedOrFocused
import io.github.linreal.cascade.editor.action.IndentForward
import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.action.ToggleTodo
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.createBuiltInSlashExecutor
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.PendingTextHistoryPush
import io.github.linreal.cascade.editor.state.SlashCommandState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.state.TextEditHistoryTracker
import io.github.linreal.cascade.editor.state.captureCheckpoint
import io.github.linreal.cascade.editor.ui.observers.ListAutoDetectObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuralHistoryIntegrationTest {

    @Test
    fun `split creates a structural boundary after typing`() {
        val blockId = BlockId("a")
        val harness = Harness(
            EditorState.withBlocks(listOf(paragraph(blockId, "ab"))).copy(
                focusedBlockId = blockId,
            )
        )
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates),
        )
        harness.stateHolder.registerTextHistoryTracker(blockId, tracker)

        try {
            harness.setRuntimeText(blockId, "abc", cursorPosition = 3)
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates)
                )
            )

            harness.callbacks.onEnter(blockId, cursorPosition = 3)

            assertEquals(listOf("abc", ""), harness.visibleTexts())
            assertTrue(harness.stateHolder.canUndo)

            harness.stateHolder.undo()
            assertEquals(listOf("abc"), harness.visibleTexts())
            assertTrue(harness.stateHolder.canUndo)
            assertTrue(harness.stateHolder.canRedo)

            harness.stateHolder.undo()
            assertEquals(listOf("ab"), harness.visibleTexts())
            assertFalse(harness.stateHolder.canUndo)
        } finally {
            harness.stateHolder.unregisterTextHistoryTracker(blockId, tracker)
        }
    }

    @Test
    fun `backspace merge is undoable through structural history`() {
        val firstId = BlockId("a")
        val secondId = BlockId("b")
        val harness = Harness(
            EditorState.withBlocks(
                listOf(
                    paragraph(firstId, "Hello "),
                    paragraph(secondId, "World"),
                )
            ).copy(focusedBlockId = secondId)
        )

        harness.callbacks.onBackspaceAtStart(secondId)

        assertEquals(listOf("Hello World"), harness.visibleTexts())
        assertEquals(firstId, harness.stateHolder.state.focusedBlockId)

        harness.stateHolder.undo()

        assertEquals(listOf("Hello ", "World"), harness.visibleTexts())
        assertEquals(secondId, harness.stateHolder.state.focusedBlockId)

        harness.stateHolder.redo()

        assertEquals(listOf("Hello World"), harness.visibleTexts())
        assertEquals(firstId, harness.stateHolder.state.focusedBlockId)
    }

    @Test
    fun `delete at end merge is undoable through structural history`() {
        val firstId = BlockId("a")
        val secondId = BlockId("b")
        val harness = Harness(
            EditorState.withBlocks(
                listOf(
                    paragraph(firstId, "Hello "),
                    paragraph(secondId, "World"),
                )
            ).copy(focusedBlockId = firstId)
        )

        harness.callbacks.onDeleteAtEnd(firstId)

        assertEquals(listOf("Hello World"), harness.visibleTexts())

        harness.stateHolder.undo()
        assertEquals(listOf("Hello ", "World"), harness.visibleTexts())

        harness.stateHolder.redo()
        assertEquals(listOf("Hello World"), harness.visibleTexts())
    }

    @Test
    fun `slash convert in place is undoable as a structural transaction`() = runTest {
        val anchorId = BlockId("anchor")
        val env = createSlashHarness(
            anchorId = anchorId,
            text = "Hello /h1 World",
            query = "h1",
            queryRange = SlashQueryRange(6, 9),
            blockType = BlockType.Paragraph,
            executionScope = this,
        )

        env.executor.executeNow(SlashCommandId("builtin.block.heading_1"))

        assertEquals(BlockType.Heading(1), env.anchorBlock().type)
        assertEquals("Hello  World", env.textStates.getVisibleText(anchorId))
        assertTrue(env.stateHolder.canUndo)

        env.stateHolder.undo()

        assertEquals(BlockType.Paragraph, env.anchorBlock().type)
        assertEquals("Hello /h1 World", env.textStates.getVisibleText(anchorId))

        env.stateHolder.redo()

        assertEquals(BlockType.Heading(1), env.anchorBlock().type)
        assertEquals("Hello  World", env.textStates.getVisibleText(anchorId))
    }

    @Test
    fun `slash insert before anchor is undoable as a structural transaction`() = runTest {
        val anchorId = BlockId("anchor")
        val env = createSlashHarness(
            anchorId = anchorId,
            text = "Text /div",
            query = "div",
            queryRange = SlashQueryRange(5, 9),
            blockType = BlockType.Paragraph,
            executionScope = this,
        )

        env.executor.executeNow(SlashCommandId("builtin.block.divider"))

        assertEquals(listOf(BlockType.Divider, BlockType.Paragraph), env.blockTypes())
        assertEquals("Text ", env.textStates.getVisibleText(anchorId))

        env.stateHolder.undo()

        assertEquals(listOf(BlockType.Paragraph), env.blockTypes())
        assertEquals("Text /div", env.textStates.getVisibleText(anchorId))
    }

    @Test
    fun `list auto detect conversion is undoable as structural history`() {
        val blockId = BlockId("a")
        val harness = Harness(
            EditorState.withBlocks(listOf(paragraph(blockId, "-"))).copy(
                focusedBlockId = blockId,
            )
        )
        val observer = ListAutoDetectObserver(
            isListBlock = { false },
            onListDetected = { newType, prefixLength ->
                harness.stateHolder.runStructuralHistoryTransaction(harness.textStates, harness.spanStates) {
                    harness.spanStates.adjustForRangeReplacement(
                        blockId = blockId,
                        start = 0,
                        endExclusive = prefixLength,
                        replacementLength = 0,
                    )
                    val newText = harness.textStates.replaceVisibleRange(
                        blockId = blockId,
                        start = 0,
                        endExclusive = prefixLength,
                        replacement = "",
                        cursorPositionAfter = 0,
                    )
                    if (newText != null) {
                        harness.stateHolder.dispatch(
                            io.github.linreal.cascade.editor.action.UpdateBlockContent(
                                blockId,
                                BlockContent.Text(newText, harness.spanStates.getSpans(blockId)),
                            )
                        )
                    }
                    harness.stateHolder.dispatch(
                        io.github.linreal.cascade.editor.action.ConvertBlockType(blockId, newType)
                    )
                }
            },
            initialVisibleText = "-",
        )

        harness.setRuntimeText(blockId, "- ", cursorPosition = 2)
        observer.onTextChanged("- ", isProgrammatic = false)

        assertEquals(BlockType.BulletList, harness.stateHolder.state.getBlock(blockId)?.type)
        assertEquals("", harness.textStates.getVisibleText(blockId))

        harness.stateHolder.undo()

        assertEquals(BlockType.Paragraph, harness.stateHolder.state.getBlock(blockId)?.type)
        assertEquals("- ", harness.textStates.getVisibleText(blockId))

        harness.stateHolder.redo()

        assertEquals(BlockType.BulletList, harness.stateHolder.state.getBlock(blockId)?.type)
        assertEquals("", harness.textStates.getVisibleText(blockId))
    }

    @Test
    fun `todo toggle is undoable through the callback structural boundary`() {
        val blockId = BlockId("todo")
        val harness = Harness(
            EditorState.withBlocks(
                listOf(
                    Block(
                        id = blockId,
                        type = BlockType.Todo(checked = false),
                        content = BlockContent.Text("Task"),
                    )
                )
            ).copy(focusedBlockId = blockId)
        )

        harness.callbacks.dispatch(ToggleTodo(blockId))

        assertEquals(BlockType.Todo(checked = true), harness.stateHolder.state.getBlock(blockId)?.type)

        harness.stateHolder.undo()
        assertEquals(BlockType.Todo(checked = false), harness.stateHolder.state.getBlock(blockId)?.type)
    }

    @Test
    fun `drag reorder moves the selected block set and is undoable`() {
        val firstId = BlockId("a")
        val secondId = BlockId("b")
        val thirdId = BlockId("c")
        val harness = Harness(
            EditorState.withBlocks(
                listOf(
                    paragraph(firstId, "A"),
                    paragraph(secondId, "B"),
                    paragraph(thirdId, "C"),
                )
            ).copy(
                selectedBlockIds = setOf(secondId, thirdId),
            )
        )

        harness.stateHolder.dispatch(StartDrag(secondId, touchOffsetY = 0f))
        assertEquals(setOf(secondId, thirdId), harness.stateHolder.state.dragState?.draggingBlockIds)

        harness.stateHolder.dispatch(UpdateDragTarget(targetIndex = 0))
        harness.callbacks.dispatch(CompleteDrag)

        assertEquals(listOf(secondId, thirdId, firstId), harness.blockIds())

        harness.stateHolder.undo()
        assertEquals(listOf(firstId, secondId, thirdId), harness.blockIds())
    }

    @Test
    fun `subtree drag restores order indentation numbering and selection through history`() {
        val firstId = BlockId("a")
        val parentId = BlockId("b")
        val childId = BlockId("c")
        val afterId = BlockId("d")
        val tailId = BlockId("e")
        val harness = Harness(
            EditorState.withBlocks(
                listOf(
                    numbered(firstId, "A", number = 1, indentationLevel = 0),
                    numbered(parentId, "B", number = 2, indentationLevel = 0),
                    numbered(childId, "C", number = 1, indentationLevel = 1),
                    paragraph(afterId, "D"),
                    paragraph(tailId, "E"),
                )
            ).copy(
                focusedBlockId = null,
                selectedBlockIds = setOf(parentId),
            )
        )

        harness.stateHolder.dispatch(StartDrag(parentId, touchOffsetY = 0f))
        assertEquals(listOf(parentId, childId), harness.stateHolder.state.dragState?.payloadBlockIds)

        harness.stateHolder.dispatch(UpdateDragTarget(targetIndex = 4, futureRootIndentationLevel = 1))
        harness.callbacks.dispatch(CompleteDrag)

        assertEquals(listOf(firstId, afterId, parentId, childId, tailId), harness.blockIds())
        assertEquals(listOf(0, 0, 1, 2, 0), harness.indentationLevels())
        assertEquals(null, harness.stateHolder.state.focusedBlockId)
        assertEquals(setOf(parentId), harness.stateHolder.state.selectedBlockIds)
        assertEquals(
            mapOf(
                firstId to 1,
                parentId to 1,
                childId to 1,
            ),
            harness.numberedValues(),
        )

        harness.stateHolder.undo()

        assertEquals(listOf(firstId, parentId, childId, afterId, tailId), harness.blockIds())
        assertEquals(listOf(0, 0, 1, 0, 0), harness.indentationLevels())
        assertEquals(null, harness.stateHolder.state.focusedBlockId)
        assertEquals(setOf(parentId), harness.stateHolder.state.selectedBlockIds)
        assertEquals(
            mapOf(
                firstId to 1,
                parentId to 2,
                childId to 1,
            ),
            harness.numberedValues(),
        )
    }

    @Test
    fun `selected block delete is undoable through the structural action wrapper`() {
        val firstId = BlockId("a")
        val secondId = BlockId("b")
        val thirdId = BlockId("c")
        val harness = Harness(
            EditorState.withBlocks(
                listOf(
                    paragraph(firstId, "A"),
                    paragraph(secondId, "B"),
                    paragraph(thirdId, "C"),
                )
            ).copy(
                selectedBlockIds = setOf(firstId, thirdId),
            )
        )

        harness.callbacks.dispatch(DeleteSelectedOrFocused)

        assertEquals(listOf(secondId), harness.blockIds())

        harness.stateHolder.undo()
        assertEquals(listOf(firstId, secondId, thirdId), harness.blockIds())
    }

    @Test
    fun `indent forward is undoable through the structural action wrapper`() {
        val firstId = BlockId("a")
        val secondId = BlockId("b")
        val harness = Harness(
            EditorState.withBlocks(
                listOf(
                    paragraph(firstId, "A"),
                    paragraph(secondId, "B"),
                )
            ).copy(focusedBlockId = secondId)
        )

        harness.callbacks.dispatch(IndentForward)

        assertEquals(listOf(0, 1), harness.indentationLevels())

        harness.stateHolder.undo()
        assertEquals(listOf(0, 0), harness.indentationLevels())

        harness.stateHolder.redo()
        assertEquals(listOf(0, 1), harness.indentationLevels())
    }

    @Test
    fun `no-op indent action through structural wrapper does not create undo step`() {
        val blockId = BlockId("a")
        val harness = Harness(
            EditorState.withBlocks(listOf(paragraph(blockId, "A"))).copy(
                focusedBlockId = blockId,
            )
        )

        harness.callbacks.dispatch(IndentForward)

        assertEquals(listOf(0), harness.indentationLevels())
        assertFalse(harness.stateHolder.canUndo)
    }

    private class Harness(initialState: EditorState) {
        val stateHolder = EditorStateHolder(initialState)
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )

        init {
            stateHolder.bindHistoryRuntime(textStates, spanStates)
            stateHolder.state.blocks.forEach { block ->
                val text = block.content as? BlockContent.Text ?: return@forEach
                textStates.getOrCreate(block.id, text.text)
                spanStates.getOrCreate(block.id, text.spans, text.text.length)
            }
        }

        fun visibleTexts(): List<String> {
            return stateHolder.state.blocks
                .mapNotNull { block ->
                    val text = block.content as? BlockContent.Text ?: return@mapNotNull null
                    textStates.getVisibleText(block.id) ?: text.text
                }
        }

        fun blockIds(): List<BlockId> {
            return stateHolder.state.blocks.map { it.id }
        }

        fun indentationLevels(): List<Int> {
            return stateHolder.state.blocks.map { it.attributes.indentationLevel }
        }

        fun numberedValues(): Map<BlockId, Int> {
            return stateHolder.state.blocks
                .mapNotNull { block ->
                    val type = block.type as? BlockType.NumberedList ?: return@mapNotNull null
                    block.id to type.number
                }
                .toMap()
        }

        fun setRuntimeText(
            blockId: BlockId,
            text: String,
            cursorPosition: Int,
        ) {
            textStates.setText(blockId, text, cursorPosition)
            textStates.consumeProgrammaticCommit(blockId)
        }
    }

    private data class SlashHarness(
        val anchorId: BlockId,
        val stateHolder: EditorStateHolder,
        val textStates: BlockTextStates,
        val executor: SlashCommandExecutor,
    ) {
        fun anchorBlock(): Block = requireNotNull(stateHolder.state.getBlock(anchorId))

        fun blockTypes(): List<BlockType> {
            return stateHolder.state.blocks.map { it.type }
        }
    }

    private fun createSlashHarness(
        anchorId: BlockId,
        text: String,
        query: String,
        queryRange: SlashQueryRange,
        blockType: BlockType,
        executionScope: kotlinx.coroutines.CoroutineScope,
    ): SlashHarness {
        val stateHolder = EditorStateHolder(
            EditorState(
                blocks = listOf(
                    Block(
                        id = anchorId,
                        type = blockType,
                        content = BlockContent.Text(text),
                    )
                ),
                focusedBlockId = anchorId,
                selectedBlockIds = emptySet(),
                dragState = null,
                slashCommandState = SlashCommandState(
                    anchorBlockId = anchorId,
                    query = query,
                    queryRange = queryRange,
                ),
            )
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        seedRuntimeStates(stateHolder.state.blocks, textStates, spanStates)
        stateHolder.bindHistoryRuntime(textStates, spanStates)

        val registry = SlashCommandRegistry()
        val blockRegistry = BlockRegistry.createDefault()
        val executor = SlashCommandExecutor(
            registry = registry,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            executionScope = executionScope,
            builtInExecutor = createBuiltInSlashExecutor(stateHolder, blockRegistry),
        )
        BuiltInSlashCommandFactory(executor.builtInExecutor)
            .generate(blockRegistry.getAllDescriptors())
            .forEach(registry::register)

        return SlashHarness(
            anchorId = anchorId,
            stateHolder = stateHolder,
            textStates = textStates,
            executor = executor,
        )
    }

    private fun EditorStateHolder.pushFrom(pending: PendingTextHistoryPush?) {
        if (pending == null) return
        pushHistoryEntry(pending.entry, pending.policy)
    }

    private fun seedRuntimeStates(
        blocks: List<Block>,
        textStates: BlockTextStates,
        spanStates: BlockSpanStates,
    ) {
        blocks.forEach { block ->
            val text = block.content as? BlockContent.Text ?: return@forEach
            textStates.getOrCreate(block.id, text.text)
            spanStates.getOrCreate(block.id, text.spans, text.text.length)
        }
    }

    private fun paragraph(id: BlockId, text: String): Block {
        return Block(
            id = id,
            type = BlockType.Paragraph,
            content = BlockContent.Text(text),
        )
    }

    private fun numbered(
        id: BlockId,
        text: String,
        number: Int,
        indentationLevel: Int,
    ): Block {
        return Block(
            id = id,
            type = BlockType.NumberedList(number),
            content = BlockContent.Text(text),
            attributes = BlockAttributes(indentationLevel = indentationLevel),
        )
    }
}
