package io.github.linreal.cascade.screens.preview

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreviewDocumentLibraryTest {

    @Test
    fun `every seeded document publishes blocks decoded from its own json`() {
        val library = PreviewDocumentLibrary(documentCount = 12)

        assertEquals(12, library.documents.size)
        for (document in library.documents) {
            assertTrue(document.json.isNotEmpty(), "${document.id} has no json")
            assertEquals(
                DocumentSchema.decodeFromString(
                    jsonString = document.json,
                    typeCodec = PreviewSampleTypeCodec,
                ),
                document.blocks,
                "${document.id} publishes blocks that do not match its json",
            )
        }
    }

    @Test
    fun `custom sample block type survives the seed round trip`() {
        // Index 5 is the custom/unknown fixture; without PreviewSampleTypeCodec the
        // metric block would decode as UnknownBlockType and lose its preview renderer.
        val library = PreviewDocumentLibrary(documentCount = 6)
        val document = assertNotNull(library.document("preview-note-5"))

        assertTrue(
            document.blocks.any { it.type == PreviewMetricBlockType },
            "custom block type did not survive: ${document.blocks.map { it.type.typeId }}",
        )
    }

    @Test
    fun `saving republishes one document and leaves the other entries identical`() {
        val library = PreviewDocumentLibrary(documentCount = 4)
        val before = library.documents
        val target = before[1]
        val editedJson = DocumentSchema.encodeToString(
            blocks = target.blocks.dropLast(1),
            typeCodec = PreviewSampleTypeCodec,
        )

        assertTrue(library.save(target.id, editedJson))

        val after = library.documents
        assertEquals(editedJson, after[1].json)
        assertEquals(target.blocks.size - 1, after[1].blocks.size)
        assertEquals("", library.lastErrorMessage)
        // Keyed grid cards must not churn for documents that did not change.
        assertSame(before[0], after[0])
        assertSame(before[2], after[2])
        assertSame(before[3], after[3])
    }

    @Test
    fun `a malformed export keeps the last valid snapshot and reports the failure`() {
        val library = PreviewDocumentLibrary(documentCount = 2)
        val target = library.documents[0]

        assertFalse(library.save(target.id, "{ not json"))

        assertSame(target, library.documents[0])
        assertTrue(library.lastErrorMessage.isNotEmpty())

        library.clearError()
        assertEquals("", library.lastErrorMessage)
    }

    @Test
    fun `saving an unknown document id is reported and changes nothing`() {
        val library = PreviewDocumentLibrary(documentCount = 2)
        val before = library.documents

        assertFalse(library.save("missing", """{"version":2,"blocks":[]}"""))

        assertSame(before, library.documents)
        assertTrue(library.lastErrorMessage.isNotEmpty())
    }

    @Test
    fun `an empty document decodes as a successful save`() {
        val library = PreviewDocumentLibrary(documentCount = 1)
        val target = library.documents[0]

        assertTrue(library.save(target.id, """{"version":2,"blocks":[]}"""))

        assertEquals(emptyList(), library.documents[0].blocks)
    }

    @Test
    fun `text blocks with no content key decode instead of being dropped`() {
        // Mirrors the preview renderers' BlockContent.Empty contract: a minimal
        // document still yields one block per entry.
        val library = PreviewDocumentLibrary(documentCount = 1)
        val target = library.documents[0]
        val minimalJson = """
            {"version":2,"blocks":[
              {"id":"a","type":{"typeId":"paragraph"}},
              {"id":"b","type":{"typeId":"todo","checked":true}}
            ]}
        """.trimIndent()

        assertTrue(library.save(target.id, minimalJson))

        val blocks = library.documents[0].blocks
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.content == BlockContent.Empty })
    }
}
