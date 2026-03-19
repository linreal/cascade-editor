package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.theme.BlockLocalizedStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CascadeEditorBlockStringsTest {

    private val blockStrings = CascadeEditorBlockStrings.default()

    // All built-in typeIds that should be present
    private val builtInTypeIds = listOf(
        "paragraph", "heading_1", "heading_2", "heading_3",
        "heading_4", "heading_5", "heading_6", "todo",
        "bullet_list", "numbered_list", "quote", "code",
        "divider", "image",
    )

    // -- default() contains entries for all built-in typeIds --

    @Test
    fun `default contains all built-in typeIds`() {
        builtInTypeIds.forEach { typeId ->
            assertNotNull(
                blockStrings.forType(typeId),
                "Missing entry for built-in typeId: $typeId"
            )
        }
    }

    // -- Each entry has non-empty displayName and description --

    @Test
    fun `all entries have non-empty displayName`() {
        blockStrings.blocks.forEach { (typeId, entry) ->
            assertTrue(
                entry.displayName.isNotEmpty(),
                "$typeId should have non-empty displayName"
            )
        }
    }

    @Test
    fun `all entries have non-empty description`() {
        blockStrings.blocks.forEach { (typeId, entry) ->
            assertTrue(
                entry.description.isNotEmpty(),
                "$typeId should have non-empty description"
            )
        }
    }

    // -- Keyword lists are non-empty --

    @Test
    fun `all entries have non-empty keywords`() {
        blockStrings.blocks.forEach { (typeId, entry) ->
            assertTrue(
                entry.keywords.isNotEmpty(),
                "$typeId should have non-empty keywords list"
            )
        }
    }

    // -- forType() returns null for unknown typeIds --

    @Test
    fun `forType returns null for unknown typeId`() {
        assertNull(blockStrings.forType("custom:nonexistent"))
    }

    @Test
    fun `forType returns null for empty typeId`() {
        assertNull(blockStrings.forType(""))
    }

    // -- forType() returns correct entry --

    @Test
    fun `forType returns correct entry for paragraph`() {
        val entry = blockStrings.forType("paragraph")
        assertNotNull(entry)
        assertEquals("Paragraph", entry.displayName)
    }

    @Test
    fun `forType returns correct entry for heading_1`() {
        val entry = blockStrings.forType("heading_1")
        assertNotNull(entry)
        assertEquals("Heading 1", entry.displayName)
    }

    // -- Default stability --

    @Test
    fun `default called twice produces equal instances`() {
        assertEquals(CascadeEditorBlockStrings.default(), CascadeEditorBlockStrings.default())
    }

    // -- BlockLocalizedStrings defaults --

    @Test
    fun `BlockLocalizedStrings keywords default to empty list`() {
        val entry = BlockLocalizedStrings(displayName = "Test", description = "Test desc")
        assertTrue(entry.keywords.isEmpty())
    }
}
