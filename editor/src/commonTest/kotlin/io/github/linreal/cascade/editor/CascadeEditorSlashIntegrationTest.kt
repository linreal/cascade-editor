package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.slash.SlashQueryTextPolicy
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.DragState
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashCommandState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.createMergedSlashRegistry
import io.github.linreal.cascade.editor.ui.shouldInvalidateSlashSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CascadeEditorSlashIntegrationTest {

    @Test
    fun `createMergedSlashRegistry combines built-in and custom items`() {
        val builtIn = SlashCommandAction(
            id = SlashCommandId("builtin.block.heading_1"),
            title = "Heading 1",
            description = "Built-in heading",
            onExecute = { SlashCommandResult.Done },
        )
        val custom = SlashCommandAction(
            id = SlashCommandId("custom.callout"),
            title = "Callout",
            description = "Insert callout block",
            onExecute = { SlashCommandResult.Done },
        )

        val merged = createMergedSlashRegistry(
            builtInItems = listOf(builtIn),
            customItems = listOf(custom),
        )

        val ids = merged.search("").map { it.id.value }
        assertTrue("builtin.block.heading_1" in ids)
        assertTrue("custom.callout" in ids)
    }

    @Test
    fun `createMergedSlashRegistry keeps consumer override and does not mutate consumer registry`() {
        val builtIn = SlashCommandAction(
            id = SlashCommandId("builtin.block.heading_1"),
            title = "Heading 1",
            description = "Built-in heading",
            onExecute = { SlashCommandResult.Done },
        )
        val customOverride = SlashCommandAction(
            id = SlashCommandId("builtin.block.heading_1"),
            title = "Custom Heading",
            description = "Consumer override",
            onExecute = { SlashCommandResult.Done },
        )

        val consumerRegistry = SlashCommandRegistry().apply {
            register(customOverride)
        }
        val merged = createMergedSlashRegistry(
            builtInItems = listOf(builtIn),
            customItems = consumerRegistry.getRootItems(),
        )

        val mergedHeading = merged.search("")
            .firstOrNull { it.id == SlashCommandId("builtin.block.heading_1") }
        assertNotNull(mergedHeading)
        assertEquals("Custom Heading", mergedHeading.title)

        val consumerItems = consumerRegistry.search("")
        assertEquals(1, consumerItems.size)
        assertEquals("Custom Heading", consumerItems.first().title)
    }

    // -- Registry coexistence --

    @Test
    fun `built-in and custom items coexist in registry search`() {
        val registry = SlashCommandRegistry()
        val blockRegistry = BlockRegistry.createDefault()

        // Register built-in items
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val anchorId = BlockId.generate()
        val stateHolder = createStateHolder(anchorId)
        textStates.getOrCreate(anchorId, "/")

        val executor = SlashCommandExecutor(
            registry = registry,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            blockRegistry = blockRegistry,
            executionScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )

        val factory = BuiltInSlashCommandFactory(executor.builtInExecutor)
        factory.generate(blockRegistry.getAllDescriptors()).forEach(registry::register)

        // Register a custom item
        val customAction = SlashCommandAction(
            id = SlashCommandId("custom.callout"),
            title = "Callout",
            description = "Insert a callout box",
            onExecute = { SlashCommandResult.Done },
        )
        registry.register(customAction)

        // Both built-in and custom should be discoverable
        val allItems = registry.search("")
        val builtInIds = allItems.filter { it.id.value.startsWith("builtin.") }
        val customIds = allItems.filter { it.id.value.startsWith("custom.") }

        assertTrue(builtInIds.isNotEmpty(), "Expected built-in items")
        assertTrue(customIds.isNotEmpty(), "Expected custom items")
        assertNotNull(allItems.find { it.id == SlashCommandId("custom.callout") })
    }

    @Test
    fun `custom item replaces built-in with same id`() {
        val registry = SlashCommandRegistry()
        val blockRegistry = BlockRegistry.createDefault()

        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val anchorId = BlockId.generate()
        val stateHolder = createStateHolder(anchorId)
        textStates.getOrCreate(anchorId, "/")

        val executor = SlashCommandExecutor(
            registry = registry,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            blockRegistry = blockRegistry,
            executionScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )

        val factory = BuiltInSlashCommandFactory(executor.builtInExecutor)
        factory.generate(blockRegistry.getAllDescriptors()).forEach(registry::register)

        // Register a custom item with the same ID as a built-in
        val overrideAction = SlashCommandAction(
            id = SlashCommandId("builtin.block.heading_1"),
            title = "Custom Heading",
            description = "Custom heading override",
            onExecute = { SlashCommandResult.Done },
        )
        registry.register(overrideAction)

        val items = registry.search("")
        val heading = items.find { it.id == SlashCommandId("builtin.block.heading_1") }
        assertNotNull(heading)
        assertEquals("Custom Heading", heading.title)
    }

    @Test
    fun `custom item is executable via executor alongside built-ins`() = runTest {
        val registry = SlashCommandRegistry()
        val blockRegistry = BlockRegistry.createDefault()
        val anchorId = BlockId.generate()

        val stateHolder = EditorStateHolder(
            EditorState(
                blocks = listOf(Block(anchorId, BlockType.Paragraph, BlockContent.Text("Hello /cal"))),
                focusedBlockId = anchorId,
                selectedBlockIds = emptySet(),
                dragState = null,
                slashCommandState = SlashCommandState(
                    anchorBlockId = anchorId,
                    query = "cal",
                    queryRange = SlashQueryRange(6, 10),
                ),
            )
        )

        val textStates = BlockTextStates()
        textStates.getOrCreate(anchorId, "Hello /cal")
        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(anchorId, emptyList(), 10)

        val executor = SlashCommandExecutor(
            registry = registry,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            blockRegistry = blockRegistry,
            executionScope = this,
        )

        // Register built-ins
        val factory = BuiltInSlashCommandFactory(executor.builtInExecutor)
        factory.generate(blockRegistry.getAllDescriptors()).forEach(registry::register)

        // Register custom action
        val executed = mutableListOf<String>()
        val customAction = SlashCommandAction(
            id = SlashCommandId("custom.callout"),
            title = "Callout",
            description = "Insert callout",
            queryTextPolicy = SlashQueryTextPolicy.KeepText,
            onExecute = {
                executed.add("callout")
                SlashCommandResult.Done
            },
        )
        registry.register(customAction)

        // Execute the custom command
        executor.executeNow(SlashCommandId("custom.callout"))

        assertEquals(listOf("callout"), executed)
        assertNull(stateHolder.state.slashCommandState, "Menu should be closed after Done")
    }

    // -- Session invalidation (pure function tests) --

    @Test
    fun `returns false when no session exists`() {
        val state = EditorState(
            blocks = listOf(Block.paragraph("Hello")),
            focusedBlockId = null,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = null,
        )
        assertFalse(shouldInvalidateSlashSession(state))
    }

    @Test
    fun `returns false for healthy session`() {
        val anchorId = BlockId.generate()
        val state = EditorState(
            blocks = listOf(Block(anchorId, BlockType.Paragraph, BlockContent.Text("Hello /"))),
            focusedBlockId = anchorId,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = SlashCommandState(
                anchorBlockId = anchorId,
                query = "",
                queryRange = SlashQueryRange(6, 7),
            ),
        )
        assertFalse(shouldInvalidateSlashSession(state))
    }

    @Test
    fun `returns true when drag is active`() {
        val anchorId = BlockId.generate()
        val otherId = BlockId.generate()
        val state = EditorState(
            blocks = listOf(
                Block(anchorId, BlockType.Paragraph, BlockContent.Text("Hello /")),
                Block(otherId, BlockType.Paragraph, BlockContent.Text("World")),
            ),
            focusedBlockId = anchorId,
            selectedBlockIds = emptySet(),
            dragState = DragState(
                draggingBlockIds = setOf(otherId),
                targetIndex = null,
            ),
            slashCommandState = SlashCommandState(
                anchorBlockId = anchorId,
                query = "",
                queryRange = SlashQueryRange(6, 7),
            ),
        )
        assertTrue(shouldInvalidateSlashSession(state))
    }

    @Test
    fun `returns true when selection is non-empty`() {
        val anchorId = BlockId.generate()
        val otherId = BlockId.generate()
        val state = EditorState(
            blocks = listOf(
                Block(anchorId, BlockType.Paragraph, BlockContent.Text("Hello /")),
                Block(otherId, BlockType.Paragraph, BlockContent.Text("World")),
            ),
            focusedBlockId = anchorId,
            selectedBlockIds = setOf(otherId),
            dragState = null,
            slashCommandState = SlashCommandState(
                anchorBlockId = anchorId,
                query = "",
                queryRange = SlashQueryRange(6, 7),
            ),
        )
        assertTrue(shouldInvalidateSlashSession(state))
    }

    @Test
    fun `returns true when anchor block is missing`() {
        val anchorId = BlockId.generate()
        val otherId = BlockId.generate()
        val state = EditorState(
            blocks = listOf(
                Block(otherId, BlockType.Paragraph, BlockContent.Text("World")),
            ),
            focusedBlockId = null,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = SlashCommandState(
                anchorBlockId = anchorId,
                query = "",
                queryRange = SlashQueryRange(0, 1),
            ),
        )
        assertTrue(shouldInvalidateSlashSession(state))
    }

    @Test
    fun `returns false when a different block is deleted`() {
        val anchorId = BlockId.generate()
        val state = EditorState(
            blocks = listOf(
                Block(anchorId, BlockType.Paragraph, BlockContent.Text("Hello /")),
            ),
            focusedBlockId = anchorId,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = SlashCommandState(
                anchorBlockId = anchorId,
                query = "",
                queryRange = SlashQueryRange(6, 7),
            ),
        )
        // Anchor is still present — other block was deleted
        assertFalse(shouldInvalidateSlashSession(state))
    }

    // -- Full scenario tests --

    @Test
    fun `drag start with active session triggers invalidation`() {
        val anchorId = BlockId.generate()
        val otherId = BlockId.generate()

        // Start with healthy session
        val healthyState = EditorState(
            blocks = listOf(
                Block(anchorId, BlockType.Paragraph, BlockContent.Text("Hello /")),
                Block(otherId, BlockType.Paragraph, BlockContent.Text("World")),
            ),
            focusedBlockId = anchorId,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = SlashCommandState(
                anchorBlockId = anchorId,
                query = "",
                queryRange = SlashQueryRange(6, 7),
            ),
        )
        assertFalse(shouldInvalidateSlashSession(healthyState))

        // Simulate drag start
        val dragState = healthyState.copy(
            dragState = DragState(
                draggingBlockIds = setOf(otherId),
                targetIndex = null,
            )
        )
        assertTrue(shouldInvalidateSlashSession(dragState))
    }

    @Test
    fun `anchor block deletion detected after state change`() {
        val anchorId = BlockId.generate()
        val otherId = BlockId.generate()

        // Start with anchor present
        val beforeDeletion = EditorState(
            blocks = listOf(
                Block(anchorId, BlockType.Paragraph, BlockContent.Text("Hello /")),
                Block(otherId, BlockType.Paragraph, BlockContent.Text("World")),
            ),
            focusedBlockId = anchorId,
            selectedBlockIds = emptySet(),
            dragState = null,
            slashCommandState = SlashCommandState(
                anchorBlockId = anchorId,
                query = "",
                queryRange = SlashQueryRange(6, 7),
            ),
        )
        assertFalse(shouldInvalidateSlashSession(beforeDeletion))

        // Simulate anchor block deletion (block removed from list but session still references it)
        val afterDeletion = beforeDeletion.copy(
            blocks = listOf(Block(otherId, BlockType.Paragraph, BlockContent.Text("World"))),
            focusedBlockId = null,
        )
        assertTrue(shouldInvalidateSlashSession(afterDeletion))
    }

    // -- Helpers --

    private fun createStateHolder(
        anchorId: BlockId,
        text: String = "/",
    ): EditorStateHolder {
        return EditorStateHolder(
            EditorState(
                blocks = listOf(Block(anchorId, BlockType.Paragraph, BlockContent.Text(text))),
                focusedBlockId = anchorId,
                selectedBlockIds = emptySet(),
                dragState = null,
                slashCommandState = SlashCommandState(
                    anchorBlockId = anchorId,
                    query = "",
                    queryRange = SlashQueryRange(0, 1),
                ),
            )
        )
    }
}
