package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.ui.createEditorRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnknownBlockTypeTest {

    // UnknownBlockType properties

    @Test
    fun `supportsText is false`() {
        val type = UnknownBlockType(typeId = "future_widget", rawTypeJson = """{"typeId":"future_widget"}""")
        assertFalse(type.supportsText)
    }

    @Test
    fun `isConvertible is false`() {
        val type = UnknownBlockType(typeId = "future_widget", rawTypeJson = """{"typeId":"future_widget"}""")
        assertFalse(type.isConvertible)
    }

    @Test
    fun `displayName contains typeId`() {
        val type = UnknownBlockType(typeId = "callout_v2", rawTypeJson = """{"typeId":"callout_v2"}""")
        assertTrue(type.displayName.contains("callout_v2"))
    }

    @Test
    fun `rawTypeJson stores faithfully`() {
        val raw = """{"typeId":"future_widget","flavor":"spicy","version":2}"""
        val type = UnknownBlockType(typeId = "future_widget", rawTypeJson = raw)
        assertEquals(raw, type.rawTypeJson)
    }

    // Registry — string-based lookup unchanged

    @Test
    fun `getRenderer by string returns null for nonexistent type`() {
        val registry = createEditorRegistry()
        assertNull(registry.getRenderer("nonexistent_type"))
    }

    @Test
    fun `getRenderer by string returns renderer for paragraph`() {
        val registry = createEditorRegistry()
        assertNotNull(registry.getRenderer("paragraph"))
    }

    // Registry — BlockType-based lookup with unknown fallback

    @Test
    fun `getRenderer by BlockType returns renderer for built-in type`() {
        val registry = createEditorRegistry()
        assertNotNull(registry.getRenderer(BlockType.Paragraph))
    }

    @Test
    fun `getRenderer by BlockType returns null for unregistered custom type`() {
        val registry = createEditorRegistry()
        val customType = object : io.github.linreal.cascade.editor.core.CustomBlockType {
            override val typeId = "my_custom_type"
            override val displayName = "Custom"
        }
        assertNull(registry.getRenderer(customType))
    }

    @Test
    fun `getRenderer by BlockType returns fallback for UnknownBlockType`() {
        val registry = createEditorRegistry()
        val unknownType = UnknownBlockType(typeId = "future_widget", rawTypeJson = """{"typeId":"future_widget"}""")
        assertNotNull(registry.getRenderer(unknownType))
    }

    @Test
    fun `getRenderer by BlockType returns null for UnknownBlockType without fallback set`() {
        // Raw registry without registerBuiltInRenderers — no fallback
        val registry = io.github.linreal.cascade.editor.registry.BlockRegistry.create()
        val unknownType = UnknownBlockType(typeId = "future_widget", rawTypeJson = """{"typeId":"future_widget"}""")
        assertNull(registry.getRenderer(unknownType))
    }
}
