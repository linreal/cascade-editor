package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandMenu
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.slash.SlashQueryTextPolicy
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashCommandState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlashCommandExecutorTest {

    // -- Submenu navigation --

    @Test
    fun `submenu selection updates navigation path without executing`() = runTest {
        val env = createExecutorEnv(
            text = "Hello /",
            queryRange = SlashQueryRange(6, 7),
        )

        val childExecuted = mutableListOf<String>()
        val submenu = SlashCommandMenu(
            id = SlashCommandId("menu.test"),
            title = "Test Menu",
            description = "A submenu",
            children = listOf(
                SlashCommandAction(
                    id = SlashCommandId("menu.test.child"),
                    title = "Child",
                    description = "Child action",
                    onExecute = {
                        childExecuted.add("executed")
                        SlashCommandResult.Done
                    },
                ),
            ),
        )
        env.registry.register(submenu)

        env.executor.executeNow(SlashCommandId("menu.test"))

        val session = env.stateHolder.state.slashCommandState
        assertNotNull(session)
        assertEquals(listOf(SlashCommandId("menu.test")), session.navigationPath)
        assertTrue(childExecuted.isEmpty(), "Child action should not have been executed")
    }

    // -- Query text policy --

    @Test
    fun `RemoveBeforeExecute removes the range before command logic runs`() = runTest {
        val recordedTexts = mutableListOf<String?>()
        val env = createExecutorEnv(
            text = "Hello /cmd World",
            queryRange = SlashQueryRange(6, 10), // "/cmd"
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.action"),
            title = "Test",
            description = "Test action",
            queryTextPolicy = SlashQueryTextPolicy.RemoveBeforeExecute,
            onExecute = {
                recordedTexts.add(editor.getAnchorVisibleText())
                SlashCommandResult.Done
            },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.action"))

        assertEquals(1, recordedTexts.size)
        assertEquals("Hello  World", recordedTexts[0])
    }

    @Test
    fun `KeepText does not remove the range`() = runTest {
        val recordedTexts = mutableListOf<String?>()
        val env = createExecutorEnv(
            text = "Hello /cmd World",
            queryRange = SlashQueryRange(6, 10),
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.keep"),
            title = "Keep",
            description = "Keep text action",
            queryTextPolicy = SlashQueryTextPolicy.KeepText,
            onExecute = {
                recordedTexts.add(editor.getAnchorVisibleText())
                SlashCommandResult.Done
            },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.keep"))

        assertEquals(1, recordedTexts.size)
        assertEquals("Hello /cmd World", recordedTexts[0])
    }

    // -- Built-in ConvertInPlace --

    @Test
    fun `ConvertInPlace converts the anchor block type preserving remaining text`() = runTest {
        val env = createExecutorEnvWithBuiltIns(
            text = "Hello /h1 World",
            queryRange = SlashQueryRange(6, 9), // "/h1"
            query = "h1",
        )

        env.executor.executeNow(SlashCommandId("builtin.block.heading_1"))

        val block = env.stateHolder.state.getBlock(env.anchorId)
        assertNotNull(block)
        assertEquals(BlockType.Heading(1), block.type)
        assertEquals("Hello  World", env.textStates.getVisibleText(env.anchorId))
        assertNull(env.stateHolder.state.slashCommandState)
    }

    @Test
    fun `ConvertInPlace on blank anchor produces empty target block with correct type`() = runTest {
        val env = createExecutorEnvWithBuiltIns(
            text = "/todo",
            queryRange = SlashQueryRange(0, 5),
            query = "todo",
        )

        env.executor.executeNow(SlashCommandId("builtin.block.todo"))

        val block = env.stateHolder.state.getBlock(env.anchorId)
        assertNotNull(block)
        assertEquals(BlockType.Todo(checked = false), block.type)
        assertEquals("", env.textStates.getVisibleText(env.anchorId))
    }

    @Test
    fun `ConvertInPlace preserves spans outside query range`() = runTest {
        val anchorId = BlockId.generate()
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),       // "Hello"
            TextSpan(10, 15, SpanStyle.Italic),    // "World" — in "Hello /h1 World"
        )

        val env = createExecutorEnvWithBuiltIns(
            anchorId = anchorId,
            text = "Hello /h1 World",
            queryRange = SlashQueryRange(6, 9), // "/h1"
            query = "h1",
            spans = spans,
        )

        env.executor.executeNow(SlashCommandId("builtin.block.heading_1"))

        val block = env.stateHolder.state.getBlock(anchorId)
        assertNotNull(block)
        assertEquals(BlockType.Heading(1), block.type)
        assertEquals("Hello  World", env.textStates.getVisibleText(anchorId))

        // Bold span on "Hello" should be preserved
        val currentSpans = env.spanStates.getSpans(anchorId)
        assertTrue(currentSpans.any { it.style == SpanStyle.Bold && it.start == 0 && it.end == 5 })
    }

    // -- Built-in AlwaysInsert --

    @Test
    fun `AlwaysInsert inserts below and never converts anchor type`() = runTest {
        val env = createExecutorEnvWithBuiltIns(
            text = "Some text /div",
            queryRange = SlashQueryRange(10, 14), // "/div"
            query = "div",
        )

        val originalType = env.stateHolder.state.getBlock(env.anchorId)?.type

        env.executor.executeNow(SlashCommandId("builtin.block.divider"))

        // Anchor block type unchanged
        val anchorBlock = env.stateHolder.state.getBlock(env.anchorId)
        assertNotNull(anchorBlock)
        assertEquals(originalType, anchorBlock.type)

        // Anchor text has query removed
        assertEquals("Some text ", env.textStates.getVisibleText(env.anchorId))

        // New block inserted after anchor
        val blocks = env.stateHolder.state.blocks
        val anchorIndex = blocks.indexOfFirst { it.id == env.anchorId }
        assertTrue(blocks.size >= anchorIndex + 2, "Expected block after anchor")
        val insertedBlock = blocks[anchorIndex + 1]
        assertEquals(BlockType.Divider, insertedBlock.type)
    }

    @Test
    fun `AlwaysInsert for code block inserts new code block below`() = runTest {
        val env = createExecutorEnvWithBuiltIns(
            text = "/code",
            queryRange = SlashQueryRange(0, 5),
            query = "code",
        )

        env.executor.executeNow(SlashCommandId("builtin.block.code"))

        val blocks = env.stateHolder.state.blocks
        assertEquals(2, blocks.size)
        assertEquals(BlockType.Paragraph, blocks[0].type) // anchor unchanged
        assertEquals("", env.textStates.getVisibleText(env.anchorId)) // query removed

        val codeBlock = blocks[1]
        assertTrue(codeBlock.type is BlockType.Code)
    }

    // -- Exception handling --

    @Test
    fun `thrown exception becomes Failure and does not crash the executor`() = runTest {
        val env = createExecutorEnv(
            text = "Hello /crash",
            queryRange = SlashQueryRange(6, 12),
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.crash"),
            title = "Crash",
            description = "Throws exception",
            onExecute = { throw IllegalStateException("boom") },
        )
        env.registry.register(action)

        // Should NOT throw
        env.executor.executeNow(SlashCommandId("test.crash"))

        // Menu should be closed (Failure result)
        assertNull(env.stateHolder.state.slashCommandState)
    }

    @Test
    fun `thrown RuntimeException becomes Failure and closes menu`() = runTest {
        val env = createExecutorEnv(
            text = "/fail",
            queryRange = SlashQueryRange(0, 5),
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.runtime"),
            title = "Runtime",
            description = "Throws RuntimeException",
            queryTextPolicy = SlashQueryTextPolicy.KeepText,
            onExecute = { throw RuntimeException("unexpected") },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.runtime"))

        assertNull(env.stateHolder.state.slashCommandState)
    }

    // -- Missing anchor --

    @Test
    fun `missing anchor block is handled gracefully`() = runTest {
        val anchorId = BlockId.generate()
        val otherBlock = Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text("Other"))

        val stateHolder = EditorStateHolder(
            EditorState(
                blocks = listOf(otherBlock), // anchor NOT in blocks
                focusedBlockId = null,
                selectedBlockIds = emptySet(),
                dragState = null,
                slashCommandState = SlashCommandState(
                    anchorBlockId = anchorId,
                    query = "cmd",
                    queryRange = SlashQueryRange(0, 4),
                ),
            )
        )

        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val registry = SlashCommandRegistry()
        val blockRegistry = BlockRegistry.createDefault()

        val executor = SlashCommandExecutor(
            registry = registry,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            blockRegistry = blockRegistry,
            executionScope = this,
        )

        val executionRecorder = mutableListOf<String>()
        val action = SlashCommandAction(
            id = SlashCommandId("test.action"),
            title = "Test",
            description = "Test",
            onExecute = {
                executionRecorder.add("executed")
                val anchor = editor.getAnchorBlock()
                if (anchor == null) {
                    SlashCommandResult.Failure("No anchor")
                } else {
                    SlashCommandResult.Done
                }
            },
        )
        registry.register(action)

        // Should NOT throw
        executor.executeNow(SlashCommandId("test.action"))

        // Action was still invoked
        assertEquals(1, executionRecorder.size)
        // Menu closed (Failure result)
        assertNull(stateHolder.state.slashCommandState)
    }

    // -- Result handling --

    @Test
    fun `Done result closes the menu`() = runTest {
        val env = createExecutorEnv(
            text = "/cmd",
            queryRange = SlashQueryRange(0, 4),
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.done"),
            title = "Done",
            description = "Returns Done",
            onExecute = { SlashCommandResult.Done },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.done"))

        assertNull(env.stateHolder.state.slashCommandState)
    }

    @Test
    fun `KeepOpen result leaves the menu open`() = runTest {
        val env = createExecutorEnv(
            text = "/cmd",
            queryRange = SlashQueryRange(0, 4),
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.keepopen"),
            title = "Keep",
            description = "Returns KeepOpen",
            queryTextPolicy = SlashQueryTextPolicy.KeepText,
            onExecute = { SlashCommandResult.KeepOpen },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.keepopen"))

        // Menu should still be open
        assertNotNull(env.stateHolder.state.slashCommandState)
    }

    @Test
    fun `KeepOpen with RemoveBeforeExecute closes menu to keep session valid`() = runTest {
        val env = createExecutorEnv(
            text = "/cmd",
            queryRange = SlashQueryRange(0, 4),
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.keepopen.remove-before"),
            title = "KeepOpen RemoveBefore",
            description = "Returns KeepOpen with RemoveBeforeExecute",
            queryTextPolicy = SlashQueryTextPolicy.RemoveBeforeExecute,
            onExecute = { SlashCommandResult.KeepOpen },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.keepopen.remove-before"))

        assertNull(env.stateHolder.state.slashCommandState)
    }

    @Test
    fun `Failure result closes the menu`() = runTest {
        val env = createExecutorEnv(
            text = "/cmd",
            queryRange = SlashQueryRange(0, 4),
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.fail"),
            title = "Fail",
            description = "Returns Failure",
            queryTextPolicy = SlashQueryTextPolicy.KeepText,
            onExecute = { SlashCommandResult.Failure("intentional") },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.fail"))

        assertNull(env.stateHolder.state.slashCommandState)
    }

    // -- Edge cases --

    @Test
    fun `no-op when no active slash session`() = runTest {
        val stateHolder = EditorStateHolder(
            EditorState(
                blocks = listOf(Block.paragraph("Hello")),
                focusedBlockId = null,
                selectedBlockIds = emptySet(),
                dragState = null,
                slashCommandState = null,
            )
        )

        val executor = SlashCommandExecutor(
            registry = SlashCommandRegistry(),
            stateHolder = stateHolder,
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
            blockRegistry = BlockRegistry.createDefault(),
            executionScope = this,
        )

        // Should complete without error
        executor.executeNow(SlashCommandId("nonexistent"))
        // State unchanged
        assertNull(stateHolder.state.slashCommandState)
    }

    @Test
    fun `no-op when item cannot be resolved`() = runTest {
        val env = createExecutorEnv(
            text = "/cmd",
            queryRange = SlashQueryRange(0, 4),
        )

        // Don't register any items
        env.executor.executeNow(SlashCommandId("nonexistent"))

        // Session should remain unchanged (not closed)
        assertNotNull(env.stateHolder.state.slashCommandState)
    }

    @Test
    fun `context carries correct session snapshot`() = runTest {
        val recordedSnapshots = mutableListOf<Triple<BlockId, String, SlashQueryRange>>()
        val anchorId = BlockId.generate()
        val env = createExecutorEnv(
            anchorId = anchorId,
            text = "prefix /query suffix",
            queryRange = SlashQueryRange(7, 13), // "/query"
            query = "query",
        )

        val action = SlashCommandAction(
            id = SlashCommandId("test.snapshot"),
            title = "Snapshot",
            description = "Records context",
            queryTextPolicy = SlashQueryTextPolicy.KeepText,
            onExecute = {
                recordedSnapshots.add(Triple(anchorBlockId, query, queryRange))
                SlashCommandResult.Done
            },
        )
        env.registry.register(action)

        env.executor.executeNow(SlashCommandId("test.snapshot"))

        assertEquals(1, recordedSnapshots.size)
        val (capturedBlockId, capturedQuery, capturedRange) = recordedSnapshots[0]
        assertEquals(anchorId, capturedBlockId)
        assertEquals("query", capturedQuery)
        assertEquals(SlashQueryRange(7, 13), capturedRange)
    }

    // -- Helpers --

    private data class ExecutorEnv(
        val anchorId: BlockId,
        val stateHolder: EditorStateHolder,
        val textStates: BlockTextStates,
        val spanStates: BlockSpanStates,
        val registry: SlashCommandRegistry,
        val blockRegistry: BlockRegistry,
        val executor: SlashCommandExecutor,
    )

    private fun createExecutorEnv(
        anchorId: BlockId = BlockId.generate(),
        text: String,
        queryRange: SlashQueryRange,
        query: String = text.substring(
            (queryRange.start + 1).coerceAtMost(queryRange.endExclusive),
            queryRange.endExclusive,
        ),
        spans: List<TextSpan> = emptyList(),
        blockType: BlockType = BlockType.Paragraph,
        executionScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ): ExecutorEnv {
        val anchorBlock = Block(anchorId, blockType, BlockContent.Text(text, spans))

        val stateHolder = EditorStateHolder(
            EditorState(
                blocks = listOf(anchorBlock),
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
        textStates.getOrCreate(anchorId, text)

        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(anchorId, spans, text.length)

        val registry = SlashCommandRegistry()
        val blockRegistry = BlockRegistry.createDefault()

        val executor = SlashCommandExecutor(
            registry = registry,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            blockRegistry = blockRegistry,
            executionScope = executionScope,
        )

        return ExecutorEnv(
            anchorId = anchorId,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            registry = registry,
            blockRegistry = blockRegistry,
            executor = executor,
        )
    }

    private fun createExecutorEnvWithBuiltIns(
        anchorId: BlockId = BlockId.generate(),
        text: String,
        queryRange: SlashQueryRange,
        query: String,
        spans: List<TextSpan> = emptyList(),
    ): ExecutorEnv {
        val env = createExecutorEnv(
            anchorId = anchorId,
            text = text,
            queryRange = queryRange,
            query = query,
            spans = spans,
        )

        // Generate built-in items and register them
        val factory = BuiltInSlashCommandFactory(env.executor.builtInExecutor)
        val builtInItems = factory.generate(env.blockRegistry.getAllDescriptors())
        builtInItems.forEach { env.registry.register(it) }

        return env
    }
}
