package io.github.linreal.cascade.ios.model

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CascadeDocumentBuilderTest {
    @Test
    fun buildsBuiltInBlocks() {
        val json = CascadeEditorDocumentBuilder()
            .heading(1, "Title")
            .paragraph("Body")
            .todo("Ship", checked = true)
            .divider()
            .buildJson()

        val blocks = DocumentSchema.decodeFromString(json)

        assertIs<BlockType.Heading>(blocks[0].type)
        assertEquals("Title", (blocks[0].content as BlockContent.Text).text)
        assertIs<BlockType.Paragraph>(blocks[1].type)
        assertEquals("Body", (blocks[1].content as BlockContent.Text).text)
        val todo = assertIs<BlockType.Todo>(blocks[2].type)
        assertEquals(true, todo.checked)
        assertEquals("Ship", (blocks[2].content as BlockContent.Text).text)
        assertIs<BlockType.Divider>(blocks[3].type)
    }

    @Test
    fun buildsCustomBlockFromPayloadJsonObject() {
        val json = CascadeEditorDocumentBuilder()
            .customBlock("metric", """{"value":"99.9%","label":"Uptime","trend":"up"}""")
            .buildJson()

        val block = DocumentSchema.decodeFromString(json).single()
        val content = block.content as BlockContent.Custom

        assertEquals("metric", block.type.typeId)
        assertEquals("metric", content.typeId)
        assertEquals("99.9%", content.data["value"])
        assertEquals("Uptime", content.data["label"])
    }

    @Test
    fun customBlockPreservesNonFiniteJsonNumberAsString() {
        val json = CascadeEditorDocumentBuilder()
            .customBlock("metric", """{"value":1e999999}""")
            .buildJson()

        val block = DocumentSchema.decodeFromString(json).single()
        val content = block.content as BlockContent.Custom
        val value = assertIs<String>(content.data["value"])

        assertEquals("1e999999", value)
    }

    @Test
    fun clampsInvalidHeadingLevelAndRecordsError() {
        val builder = CascadeEditorDocumentBuilder()

        val result = runCatching {
            builder.heading(99, "Oversized")
            builder.buildJson()
        }

        assertTrue(result.isSuccess)
        assertEquals("Heading level must be between 1 and 6", builder.lastErrorMessage)
        val block = DocumentSchema.decodeFromString(result.getOrThrow()).single()
        val heading = assertIs<BlockType.Heading>(block.type)
        assertEquals(6, heading.level)
        assertEquals("Oversized", (block.content as BlockContent.Text).text)
    }

    @Test
    fun clampsInvalidNumberedListNumberAndRecordsError() {
        val builder = CascadeEditorDocumentBuilder()

        val result = runCatching {
            builder.numberedList("First", number = 0)
            builder.buildJson()
        }

        assertTrue(result.isSuccess)
        assertEquals("Numbered list number must be >= 1", builder.lastErrorMessage)
        val block = DocumentSchema.decodeFromString(result.getOrThrow()).single()
        val numberedList = assertIs<BlockType.NumberedList>(block.type)
        assertEquals(1, numberedList.number)
        assertEquals("First", (block.content as BlockContent.Text).text)
    }

    @Test
    fun customBlockUsesEmptyPayloadForNonObjectJsonAndRecordsError() {
        val builder = CascadeEditorDocumentBuilder()

        val result = runCatching {
            builder.customBlock("metric", """["not-an-object"]""")
            builder.buildJson()
        }

        assertTrue(result.isSuccess)
        assertEquals("Custom block payload must be a JSON object", builder.lastErrorMessage)
        val content = DocumentSchema.decodeFromString(result.getOrThrow()).single().content as BlockContent.Custom
        assertEquals("metric", content.typeId)
        assertEquals(emptyMap(), content.data)
    }

    @Test
    fun customBlockUsesEmptyPayloadForMalformedJsonAndRecordsParseError() {
        val builder = CascadeEditorDocumentBuilder()

        val result = runCatching {
            builder.customBlock("metric", """{"value":""")
            builder.buildJson()
        }

        assertTrue(result.isSuccess)
        assertTrue(builder.lastErrorMessage?.startsWith("Invalid custom block payload JSON: ") == true)
        val content = DocumentSchema.decodeFromString(result.getOrThrow()).single().content as BlockContent.Custom
        assertEquals("metric", content.typeId)
        assertEquals(emptyMap(), content.data)
    }

    @Test
    fun clearLastErrorResetsBuilderErrorMessage() {
        val builder = CascadeEditorDocumentBuilder()
            .heading(0, "Clamped")

        assertEquals("Heading level must be between 1 and 6", builder.lastErrorMessage)
        assertTrue(builder.clearLastError() === builder)
        assertNull(builder.lastErrorMessage)
    }
}
