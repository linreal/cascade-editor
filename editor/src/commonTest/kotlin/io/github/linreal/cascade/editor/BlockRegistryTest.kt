package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.slash.BuiltInBlockSlashBehavior
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockRegistryTest {

    @Test
    fun `default registry has built-in types`() {
        val registry = BlockRegistry.createDefault()

        assertNotNull(registry.getDescriptor("paragraph"))
        assertNotNull(registry.getDescriptor("heading_1"))
        assertNotNull(registry.getDescriptor("heading_6"))
        assertNotNull(registry.getDescriptor("todo"))
        assertNotNull(registry.getDescriptor("bullet_list"))
        assertNotNull(registry.getDescriptor("divider"))
    }

    @Test
    fun `search finds matching descriptors`() {
        val registry = BlockRegistry.createDefault()

        val results = registry.search("head")

        assertEquals(6, results.size) // heading_1 through heading_6
        assertTrue(results.all { it.typeId.startsWith("heading_") })
    }

    @Test
    fun `search by keyword`() {
        val registry = BlockRegistry.createDefault()

        val results = registry.search("checkbox")

        assertEquals(1, results.size)
        assertEquals("todo", results[0].typeId)
    }

    @Test
    fun `search returns empty for no match`() {
        val registry = BlockRegistry.createDefault()

        val results = registry.search("xyznonexistent")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `create block from registry`() {
        val registry = BlockRegistry.createDefault()

        val block = registry.createBlock("paragraph")

        assertNotNull(block)
        assertEquals(BlockType.Paragraph, block.type)
        assertEquals(BlockContent.Text(""), block.content)
    }

    @Test
    fun `create block returns null for unknown type`() {
        val registry = BlockRegistry.createDefault()

        val block = registry.createBlock("unknown_type")

        assertNull(block)
    }

    @Test
    fun `register custom block type`() {
        val registry = BlockRegistry.create()

        val customDescriptor = BlockDescriptor(
            typeId = "custom:callout",
            displayName = "Callout",
            description = "A callout box for important information",
            keywords = listOf("alert", "tip", "note"),
            factory = { id ->
                Block(id, BlockType.Quote, BlockContent.Text(""))
            }
        )

        registry.registerDescriptor(customDescriptor)

        assertNotNull(registry.getDescriptor("custom:callout"))
        assertEquals("Callout", registry.getDescriptor("custom:callout")?.displayName)
    }

    @Test
    fun `descriptor relevance scoring`() {
        val descriptor = BlockDescriptor(
            typeId = "test",
            displayName = "Paragraph",
            description = "A text paragraph",
            keywords = listOf("text", "body"),
            factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) }
        )

        // Exact match should have highest score
        assertTrue(descriptor.relevanceScore("paragraph") > descriptor.relevanceScore("para"))

        // Starts with should be higher than contains
        assertTrue(descriptor.relevanceScore("para") > descriptor.relevanceScore("graph"))

        // Keyword match
        assertTrue(descriptor.relevanceScore("text") > descriptor.relevanceScore("xyz"))
    }

    @Test
    fun `empty search returns all descriptors`() {
        val registry = BlockRegistry.createDefault()
        val allDescriptors = registry.getAllDescriptors()

        val searchResults = registry.search("")

        assertEquals(allDescriptors.size, searchResults.size)
    }

    // -- Slash metadata tests --

    @Test
    fun `descriptors expose slash metadata when configured`() {
        val registry = BlockRegistry.createDefault()

        val paragraph = registry.getDescriptor("paragraph")!!
        assertNotNull(paragraph.slash)
        assertEquals(BuiltInBlockSlashBehavior.ConvertInPlace, paragraph.slash!!.behavior)
    }

    @Test
    fun `descriptors without slash metadata remain valid registry entries`() {
        val registry = BlockRegistry.create()
        val noSlash = BlockDescriptor(
            typeId = "custom:plain",
            displayName = "Plain",
            description = "No slash",
            factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) }
        )
        registry.registerDescriptor(noSlash)

        val descriptor = registry.getDescriptor("custom:plain")
        assertNotNull(descriptor)
        assertNull(descriptor.slash)
        // Still searchable in BlockRegistry
        assertEquals(1, registry.search("plain").size)
    }

    @Test
    fun `all built-in descriptors have slash metadata`() {
        val registry = BlockRegistry.createDefault()
        val allDescriptors = registry.getAllDescriptors()

        assertTrue(allDescriptors.isNotEmpty())
        for (descriptor in allDescriptors) {
            assertNotNull(
                descriptor.slash,
                "Built-in descriptor '${descriptor.typeId}' should have slash metadata"
            )
        }
    }

    @Test
    fun `text-capable convertible blocks use ConvertInPlace`() {
        val registry = BlockRegistry.createDefault()
        val convertTypes = listOf(
            "paragraph", "heading_1", "heading_2", "heading_3",
            "heading_4", "heading_5", "heading_6",
            "todo", "bullet_list", "numbered_list", "quote"
        )

        for (typeId in convertTypes) {
            val descriptor = registry.getDescriptor(typeId)!!
            assertEquals(
                BuiltInBlockSlashBehavior.ConvertInPlace,
                descriptor.slash!!.behavior,
                "Expected ConvertInPlace for '$typeId'"
            )
        }
    }

    @Test
    fun `code divider and image use AlwaysInsert`() {
        val registry = BlockRegistry.createDefault()
        val insertTypes = listOf("code", "divider", "image")

        for (typeId in insertTypes) {
            val descriptor = registry.getDescriptor(typeId)!!
            assertEquals(
                BuiltInBlockSlashBehavior.AlwaysInsert,
                descriptor.slash!!.behavior,
                "Expected AlwaysInsert for '$typeId'"
            )
        }
    }


    @Test
    fun `custom descriptor with slash spec is preserved`() {
        val registry = BlockRegistry.create()
        val customSpec = BuiltInSlashCommandSpec(
            behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
        )
        val descriptor = BlockDescriptor(
            typeId = "custom:widget",
            displayName = "Widget",
            description = "A custom widget",
            slash = customSpec,
            factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) }
        )
        registry.registerDescriptor(descriptor)

        val retrieved = registry.getDescriptor("custom:widget")!!
        assertEquals(customSpec, retrieved.slash)
        assertEquals(BuiltInBlockSlashBehavior.AlwaysInsert, retrieved.slash!!.behavior)
    }
}
