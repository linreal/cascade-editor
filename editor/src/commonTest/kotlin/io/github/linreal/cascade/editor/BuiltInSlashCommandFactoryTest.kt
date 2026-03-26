package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.slash.BuiltInBlockSlashBehavior
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandSpec
import io.github.linreal.cascade.editor.slash.SlashCommandIconKey
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.slash.SlashQueryTextPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuiltInSlashCommandFactoryTest {

    private val recordingExecutor =
        mutableListOf<Pair<String, BuiltInBlockSlashBehavior>>()

    private val factory = BuiltInSlashCommandFactory { typeId, behavior ->
        recordingExecutor.add(typeId to behavior)
        SlashCommandResult.Done
    }

    // -- Filtering --

    @Test
    fun `only descriptors with slash metadata become slash items`() {
        val withSlash = paragraphDescriptor()
        val withoutSlash = BlockDescriptor(
            typeId = "custom:no_slash",
            displayName = "No Slash",
            description = "No slash metadata",
            factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) }
        )

        val items = factory.generate(listOf(withSlash, withoutSlash))

        assertEquals(1, items.size)
        assertEquals("builtin.block.paragraph", items[0].id.value)
    }

    @Test
    fun `empty descriptor list produces empty output`() {
        val items = factory.generate(emptyList())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `all null slash descriptors produce empty output`() {
        val noSlash = BlockDescriptor(
            typeId = "plain",
            displayName = "Plain",
            description = "No slash",
            factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) }
        )
        val items = factory.generate(listOf(noSlash, noSlash.copy(typeId = "plain2")))
        assertTrue(items.isEmpty())
    }

    // -- ID stability --

    @Test
    fun `generated ids follow builtin block prefix format`() {
        val items = factory.generate(listOf(paragraphDescriptor(), headingDescriptor()))

        assertEquals("builtin.block.paragraph", items[0].id.value)
        assertEquals("builtin.block.heading_1", items[1].id.value)
    }

    @Test
    fun `ids are stable across repeated generation`() {
        val descriptors = listOf(paragraphDescriptor(), headingDescriptor())
        val first = factory.generate(descriptors)
        val second = factory.generate(descriptors)

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    // -- Metadata copying --

    @Test
    fun `title is copied from displayName`() {
        val items = factory.generate(listOf(paragraphDescriptor()))
        assertEquals("Paragraph", items[0].title)
    }

    @Test
    fun `description is copied from descriptor`() {
        val items = factory.generate(listOf(paragraphDescriptor()))
        assertEquals("Plain text paragraph", items[0].description)
    }

    @Test
    fun `keywords are copied from descriptor`() {
        val items = factory.generate(listOf(paragraphDescriptor()))
        assertEquals(listOf("text", "p"), items[0].keywords)
    }

    @Test
    fun `slash icon is used when present`() {
        val descriptor = paragraphDescriptor().copy(
            icon = "fallback_icon",
            slash = BuiltInSlashCommandSpec(
                behavior = BuiltInBlockSlashBehavior.ConvertInPlace,
                icon = SlashCommandIconKey("slash_icon"),
            )
        )

        val items = factory.generate(listOf(descriptor))
        assertEquals(SlashCommandIconKey("slash_icon"), items[0].icon)
    }

    @Test
    fun `falls back to descriptor icon when slash icon is null`() {
        val descriptor = paragraphDescriptor().copy(
            icon = "descriptor_icon",
            slash = BuiltInSlashCommandSpec(
                behavior = BuiltInBlockSlashBehavior.ConvertInPlace,
                icon = null,
            )
        )

        val items = factory.generate(listOf(descriptor))
        assertEquals(SlashCommandIconKey("descriptor_icon"), items[0].icon)
    }

    @Test
    fun `icon is null when both slash icon and descriptor icon are null`() {
        val descriptor = paragraphDescriptor().copy(
            icon = null,
            slash = BuiltInSlashCommandSpec(
                behavior = BuiltInBlockSlashBehavior.ConvertInPlace,
                icon = null,
            )
        )

        val items = factory.generate(listOf(descriptor))
        assertNull(items[0].icon)
    }

    @Test
    fun `all actions use RemoveBeforeExecute policy`() {
        val items = factory.generate(listOf(paragraphDescriptor(), dividerDescriptor()))
        assertTrue(items.all { it.queryTextPolicy == SlashQueryTextPolicy.RemoveBeforeExecute })
    }

    // -- Behavior preservation --

    @Test
    fun `ConvertInPlace behavior is captured for execution`() = runTest {
        val items = factory.generate(listOf(paragraphDescriptor()))
        items[0].onExecute(fakeContext())

        assertEquals(1, recordingExecutor.size)
        assertEquals("paragraph", recordingExecutor[0].first)
        assertEquals(BuiltInBlockSlashBehavior.ConvertInPlace, recordingExecutor[0].second)
    }

    @Test
    fun `AlwaysInsert behavior is captured for execution`() = runTest {
        val items = factory.generate(listOf(dividerDescriptor()))
        items[0].onExecute(fakeContext())

        assertEquals(1, recordingExecutor.size)
        assertEquals("divider", recordingExecutor[0].first)
        assertEquals(BuiltInBlockSlashBehavior.AlwaysInsert, recordingExecutor[0].second)
    }

    @Test
    fun `executor receives correct typeId per action`() = runTest {
        val descriptors = listOf(paragraphDescriptor(), headingDescriptor(), dividerDescriptor())
        val items = factory.generate(descriptors)

        for (item in items) {
            item.onExecute(fakeContext())
        }

        assertEquals(3, recordingExecutor.size)
        assertEquals("paragraph", recordingExecutor[0].first)
        assertEquals("heading_1", recordingExecutor[1].first)
        assertEquals("divider", recordingExecutor[2].first)
    }

    // -- Order / determinism --

    @Test
    fun `output order matches input order after filtering`() {
        val noSlash = BlockDescriptor(
            typeId = "no_slash",
            displayName = "X",
            description = "X",
            factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) }
        )
        val descriptors = listOf(
            dividerDescriptor(),
            noSlash,
            paragraphDescriptor(),
            headingDescriptor(),
        )

        val ids = factory.generate(descriptors).map { it.id.value }
        assertEquals(
            listOf("builtin.block.divider", "builtin.block.paragraph", "builtin.block.heading_1"),
            ids
        )
    }

    // -- Integration with default registry --

    @Test
    fun `generates items for all built-in descriptors from default registry`() {
        val registry = BlockRegistry.createDefault()
        val items = factory.generate(registry.getAllDescriptors())

        // All built-in descriptors have slash metadata, so count should match
        assertEquals(registry.getAllDescriptors().size, items.size)
        assertTrue(items.all { it.id.value.startsWith("builtin.block.") })
    }

    // -- Helpers --

    private fun paragraphDescriptor() = BlockDescriptor(
        typeId = "paragraph",
        displayName = "Paragraph",
        description = "Plain text paragraph",
        keywords = listOf("text", "p"),
        slash = BuiltInSlashCommandSpec(
            behavior = BuiltInBlockSlashBehavior.ConvertInPlace,
        ),
        factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) }
    )

    private fun headingDescriptor() = BlockDescriptor(
        typeId = "heading_1",
        displayName = "Heading 1",
        description = "Heading level 1",
        keywords = listOf("h1", "heading", "title"),
        slash = BuiltInSlashCommandSpec(
            behavior = BuiltInBlockSlashBehavior.ConvertInPlace,
        ),
        factory = { id -> Block(id, BlockType.Heading(1), BlockContent.Text("")) }
    )

    private fun dividerDescriptor() = BlockDescriptor(
        typeId = "divider",
        displayName = "Divider",
        description = "Horizontal line separator",
        keywords = listOf("hr", "line", "separator"),
        slash = BuiltInSlashCommandSpec(
            behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
        ),
        factory = { id -> Block(id, BlockType.Divider, BlockContent.Empty) }
    )

    private fun fakeContext() = io.github.linreal.cascade.editor.slash.SlashCommandContext(
        anchorBlockId = BlockId.generate(),
        query = "",
        queryRange = io.github.linreal.cascade.editor.state.SlashQueryRange(0, 1),
        editor = object : io.github.linreal.cascade.editor.slash.SlashCommandEditor {
            override fun getAnchorBlock() = null
            override fun getAnchorVisibleText() = null
            override fun replaceQueryText(replacement: String) {}
            override fun updateAnchorText(text: String, cursorPosition: Int?) {}
            override fun replaceAnchorBlock(
                block: Block,
                preserveAnchorId: Boolean,
                requestFocus: Boolean,
                cursorPosition: Int?,
            ) {}
            override fun insertBlockAfterAnchor(
                block: Block,
                requestFocus: Boolean,
                cursorPosition: Int?,
            ) {}

            override fun insertBlockBeforeAnchor(block: Block) {}

            override fun focusBlock(
                blockId: BlockId,
                cursorPosition: Int?,
            ) {}
            override fun closeMenu() {}
        }
    )
}
