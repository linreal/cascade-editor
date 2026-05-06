package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.slash.BuiltInBlockSlashBehavior
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandSpec
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.theme.BlockLocalizedStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltInSlashCommandFactoryLocalizationTest {

    private val factory = BuiltInSlashCommandFactory { _, _ -> SlashCommandResult.Done }

    // -- Localized title --

    @Test
    fun `uses localized title when blockStrings provides one`() {
        val blockStrings = blockStringsFor(
            "paragraph" to BlockLocalizedStrings("Paragraphe", "Texte brut"),
        )
        val items = factory.generate(listOf(paragraphDescriptor()), blockStrings)
        assertEquals("Paragraphe", items[0].title)
    }

    // -- Localized description --

    @Test
    fun `uses localized description when provided`() {
        val blockStrings = blockStringsFor(
            "paragraph" to BlockLocalizedStrings("Paragraphe", "Un paragraphe de texte"),
        )
        val items = factory.generate(listOf(paragraphDescriptor()), blockStrings)
        assertEquals("Un paragraphe de texte", items[0].description)
    }

    // -- Keyword merging --

    @Test
    fun `merges localized keywords with descriptor keywords`() {
        val blockStrings = blockStringsFor(
            "paragraph" to BlockLocalizedStrings(
                "Paragraphe",
                "Texte",
                keywords = listOf("texte", "paragraphe"),
            ),
        )
        val items = factory.generate(listOf(paragraphDescriptor()), blockStrings)
        // English keywords from descriptor: "text", "p"
        // Localized keywords: "texte", "paragraphe"
        assertTrue(items[0].keywords.contains("text"), "Should contain English keyword 'text'")
        assertTrue(items[0].keywords.contains("p"), "Should contain English keyword 'p'")
        assertTrue(items[0].keywords.contains("texte"), "Should contain localized keyword 'texte'")
        assertTrue(items[0].keywords.contains("paragraphe"), "Should contain localized keyword 'paragraphe'")
    }

    @Test
    fun `merged keywords are deduplicated`() {
        val blockStrings = blockStringsFor(
            "paragraph" to BlockLocalizedStrings(
                "Paragraph",
                "Text",
                keywords = listOf("text", "extra"), // "text" overlaps with descriptor
            ),
        )
        val items = factory.generate(listOf(paragraphDescriptor()), blockStrings)
        val textCount = items[0].keywords.count { it == "text" }
        assertEquals(1, textCount, "Duplicate keyword 'text' should be deduplicated")
    }

    @Test
    fun `localized keywords with empty list preserves descriptor keywords`() {
        val blockStrings = blockStringsFor(
            "paragraph" to BlockLocalizedStrings("Paragraphe", "Texte", keywords = emptyList()),
        )
        val items = factory.generate(listOf(paragraphDescriptor()), blockStrings)
        assertEquals(listOf("text", "p"), items[0].keywords)
    }

    // -- Fallback when blockStrings is null --

    @Test
    fun `falls back to descriptor values when blockStrings is null`() {
        val items = factory.generate(listOf(paragraphDescriptor()), blockStrings = null)
        assertEquals("Paragraph", items[0].title)
        assertEquals("Plain text paragraph", items[0].description)
        assertEquals(listOf("text", "p"), items[0].keywords)
    }

    // -- Fallback when typeId not in blockStrings --

    @Test
    fun `falls back to descriptor when typeId is missing from blockStrings`() {
        val blockStrings = blockStringsFor(
            "heading_1" to BlockLocalizedStrings("Titre 1", "Titre de niveau 1"),
        )
        // paragraph is NOT in blockStrings
        val items = factory.generate(listOf(paragraphDescriptor()), blockStrings)
        assertEquals("Paragraph", items[0].title)
        assertEquals("Plain text paragraph", items[0].description)
        assertEquals(listOf("text", "p"), items[0].keywords)
    }

    // -- Mixed: some localized, some not --

    @Test
    fun `mixed descriptors apply localization only to matching typeIds`() {
        val blockStrings = blockStringsFor(
            "heading_1" to BlockLocalizedStrings("Titre 1", "Titre de niveau 1"),
        )
        val items = factory.generate(
            listOf(paragraphDescriptor(), headingDescriptor()),
            blockStrings,
        )
        // paragraph — no localization
        assertEquals("Paragraph", items[0].title)
        // heading_1 — localized
        assertEquals("Titre 1", items[1].title)
        assertEquals("Titre de niveau 1", items[1].description)
    }

    // -- English keywords always present --

    @Test
    fun `English keywords always present even with full localization`() {
        val blockStrings = blockStringsFor(
            "heading_1" to BlockLocalizedStrings(
                "Titre 1",
                "Titre de niveau 1",
                keywords = listOf("titre"),
            ),
        )
        val items = factory.generate(listOf(headingDescriptor()), blockStrings)
        // Original English keywords must still be present
        assertTrue(items[0].keywords.contains("h1"))
        assertTrue(items[0].keywords.contains("heading"))
        assertTrue(items[0].keywords.contains("title"))
        // Plus localized
        assertTrue(items[0].keywords.contains("titre"))
    }

    // -- Code descriptor --

    @Test
    fun `code descriptor generates localized item with ConvertInPlace`() {
        val items = factory.generate(
            listOf(codeDescriptor()),
            CascadeEditorBlockStrings.default(),
        )

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("Code", item.title)
        assertEquals("Plain code block", item.description)
        assertTrue(item.keywords.contains("code"))
        assertTrue(item.keywords.contains("snippet"))
        assertTrue(item.keywords.contains("monospace"))
    }

    @Test
    fun `code descriptor without blockStrings falls back to descriptor metadata`() {
        val items = factory.generate(listOf(codeDescriptor()), blockStrings = null)
        assertEquals("Code", items[0].title)
        assertEquals("Plain code block", items[0].description)
        assertEquals(listOf("code", "snippet", "monospace"), items[0].keywords)
    }

    // -- Helpers --

    private fun blockStringsFor(vararg entries: Pair<String, BlockLocalizedStrings>) =
        CascadeEditorBlockStrings(blocks = mapOf(*entries))

    private fun codeDescriptor() = BlockDescriptor(
        typeId = "code",
        displayName = "Code",
        description = "Plain code block",
        keywords = listOf("code", "snippet", "monospace"),
        slash = BuiltInSlashCommandSpec(behavior = BuiltInBlockSlashBehavior.ConvertInPlace),
        factory = { id -> Block(id, BlockType.Code, BlockContent.Text("")) },
    )

    private fun paragraphDescriptor() = BlockDescriptor(
        typeId = "paragraph",
        displayName = "Paragraph",
        description = "Plain text paragraph",
        keywords = listOf("text", "p"),
        slash = BuiltInSlashCommandSpec(behavior = BuiltInBlockSlashBehavior.ConvertInPlace),
        factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) },
    )

    private fun headingDescriptor() = BlockDescriptor(
        typeId = "heading_1",
        displayName = "Heading 1",
        description = "Heading level 1",
        keywords = listOf("h1", "heading", "title"),
        slash = BuiltInSlashCommandSpec(behavior = BuiltInBlockSlashBehavior.ConvertInPlace),
        factory = { id -> Block(id, BlockType.Heading(1), BlockContent.Text("")) },
    )
}
