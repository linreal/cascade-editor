package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.serialization.BlockContentCodec
import io.github.linreal.cascade.editor.serialization.BlockIdMode
import io.github.linreal.cascade.editor.serialization.BlockTypeCodec
import io.github.linreal.cascade.editor.serialization.CustomDataMode
import io.github.linreal.cascade.editor.serialization.DocumentDecodeOptions
import io.github.linreal.cascade.editor.serialization.DocumentDecodeWarning
import io.github.linreal.cascade.editor.serialization.DocumentEncodeOptions
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.serialization.DuplicateIdMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentSchemaDecodeTest {

    private fun block(
        id: String = "b1",
        type: BlockType = BlockType.Paragraph,
        content: BlockContent = BlockContent.Text(""),
    ): Block = Block(id = BlockId(id), type = type, content = content)

    // Exhaustive built-in type round-trip
    // If you add a new BlockType variant, add it here. A missing entry means
    // encode works (compiler-enforced sealed when) but decode silently falls
    // back to UnknownBlockType — this test catches that drift.

    @Test
    fun `all built-in block types survive encode-decode round-trip`() {
        val allBuiltInTypes: List<Pair<BlockType, BlockContent>> = listOf(
            BlockType.Paragraph to BlockContent.Text("p"),
            BlockType.Heading(1) to BlockContent.Text("h1"),
            BlockType.Heading(6) to BlockContent.Text("h6"),
            BlockType.Todo(checked = false) to BlockContent.Text("todo"),
            BlockType.Todo(checked = true) to BlockContent.Text("done"),
            BlockType.BulletList to BlockContent.Text("bullet"),
            BlockType.NumberedList(1) to BlockContent.Text("num"),
            BlockType.Quote to BlockContent.Text("quote"),
            BlockType.Divider to BlockContent.Empty,
        )

        val blocks = allBuiltInTypes.mapIndexed { i, (type, content) ->
            Block(id = BlockId("b$i"), type = type, content = content)
        }
        val result = DocumentSchema.decodeWithReport(DocumentSchema.encode(blocks))

        // No unknown-type warnings means every typeId decoded to a built-in
        val unknownTypeWarnings = result.warnings.filterIsInstance<DocumentDecodeWarning.UnknownBlockTypePreserved>()
        assertTrue(
            unknownTypeWarnings.isEmpty(),
            "Built-in types decoded as UnknownBlockType: ${unknownTypeWarnings.map { it.typeId }}"
        )

        assertEquals(blocks.size, result.blocks.size)
        for ((original, decoded) in blocks.zip(result.blocks)) {
            assertEquals(
                original.type::class,
                decoded.type::class,
                "Type class mismatch for typeId '${original.type.typeId}'"
            )
            assertEquals(
                original.type.typeId,
                decoded.type.typeId,
                "typeId mismatch for ${original.type::class.simpleName}"
            )
        }
    }

    // Round-trip

    @Test
    fun `round-trip paragraph`() {
        val original = listOf(block(type = BlockType.Paragraph, content = BlockContent.Text("Hello")))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        assertEquals(1, decoded.size)
        assertIs<BlockType.Paragraph>(decoded[0].type)
        assertEquals("Hello", (decoded[0].content as BlockContent.Text).text)
    }

    @Test
    fun `round-trip heading`() {
        val original = listOf(block(type = BlockType.Heading(3), content = BlockContent.Text("Title")))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        val heading = assertIs<BlockType.Heading>(decoded[0].type)
        assertEquals(3, heading.level)
    }

    @Test
    fun `round-trip todo`() {
        val original = listOf(block(type = BlockType.Todo(checked = true), content = BlockContent.Text("Task")))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        val todo = assertIs<BlockType.Todo>(decoded[0].type)
        assertEquals(true, todo.checked)
    }

    @Test
    fun `round-trip bullet list`() {
        val original = listOf(block(type = BlockType.BulletList, content = BlockContent.Text("Item")))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        assertIs<BlockType.BulletList>(decoded[0].type)
    }

    @Test
    fun `round-trip numbered list`() {
        val original = listOf(block(type = BlockType.NumberedList(3), content = BlockContent.Text("Item")))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        val nl = assertIs<BlockType.NumberedList>(decoded[0].type)
        // renumberNumberedLists always starts runs from 1
        assertEquals(1, nl.number)
    }

    @Test
    fun `round-trip quote`() {
        val original = listOf(block(type = BlockType.Quote, content = BlockContent.Text("Quote")))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        assertIs<BlockType.Quote>(decoded[0].type)
    }

    @Test
    fun `round-trip divider`() {
        val original = listOf(block(type = BlockType.Divider, content = BlockContent.Empty))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        assertIs<BlockType.Divider>(decoded[0].type)
        assertIs<BlockContent.Empty>(decoded[0].content)
    }

    @Test
    fun `round-trip text with spans`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold), TextSpan(6, 11, SpanStyle.Italic))
        val original = listOf(block(content = BlockContent.Text("Hello World", spans)))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        val text = assertIs<BlockContent.Text>(decoded[0].content)
        assertEquals("Hello World", text.text)
        assertEquals(2, text.spans.size)
        assertEquals(SpanStyle.Bold, text.spans[0].style)
        assertEquals(SpanStyle.Italic, text.spans[1].style)
    }

    // Version

    @Test
    fun `missing version defaults to 1`() {
        val json = """{"blocks":[]}"""
        val result = DocumentSchema.decodeFromString(json)
        assertEquals(0, result.size)
    }

    @Test
    fun `version 2 throws`() {
        val json = """{"version":2,"blocks":[]}"""
        assertFailsWith<IllegalArgumentException> {
            DocumentSchema.decodeFromString(json)
        }
    }

    // Empty / missing blocks

    @Test
    fun `empty document`() {
        val json = """{"version":1,"blocks":[]}"""
        assertEquals(0, DocumentSchema.decodeFromString(json).size)
    }

    @Test
    fun `missing blocks field`() {
        val json = """{"version":1}"""
        assertEquals(0, DocumentSchema.decodeFromString(json).size)
    }

    // Heading level from suffix

    @Test
    fun `heading level from suffix`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"heading_3"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        val heading = assertIs<BlockType.Heading>(decoded[0].type)
        assertEquals(3, heading.level)
    }

    @Test
    fun `heading invalid suffix falls back to 1 with warning`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"heading_abc"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        val heading = assertIs<BlockType.Heading>(result.blocks[0].type)
        assertEquals(1, heading.level)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.InvalidBlockTypeParam })
    }

    @Test
    fun `heading level 0 falls back to 1 with warning`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"heading_0"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        val heading = assertIs<BlockType.Heading>(result.blocks[0].type)
        assertEquals(1, heading.level)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.InvalidBlockTypeParam })
    }

    // Todo defaults

    @Test
    fun `todo missing checked defaults to false`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"todo"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        val todo = assertIs<BlockType.Todo>(decoded[0].type)
        assertEquals(false, todo.checked)
    }

    // NumberedList defaults

    @Test
    fun `numbered list missing number defaults to 1`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"numbered_list"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        val nl = assertIs<BlockType.NumberedList>(decoded[0].type)
        assertEquals(1, nl.number)
    }

    @Test
    fun `numbered list number 0 falls back to 1 with warning`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"numbered_list","number":0},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        val nl = assertIs<BlockType.NumberedList>(result.blocks[0].type)
        assertEquals(1, nl.number)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.InvalidBlockTypeParam })
    }

    // Empty content

    @Test
    fun `empty content decoded`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"divider"},"content":{"kind":"empty"}}]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        assertIs<BlockContent.Empty>(decoded[0].content)
    }

    // Custom content

    @Test
    fun `custom content canonical shape`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"embed","data":{"url":"https://y.com"}}}]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        val custom = assertIs<BlockContent.Custom>(decoded[0].content)
        assertEquals("embed", custom.typeId)
        assertEquals("https://y.com", custom.data["url"])
    }

    @Test
    fun `custom content legacy shape - kind custom with typeId`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"custom","typeId":"embed","data":{"url":"https://y.com"}}}]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        val custom = assertIs<BlockContent.Custom>(decoded[0].content)
        assertEquals("embed", custom.typeId)
        assertEquals("https://y.com", custom.data["url"])
    }

    @Test
    fun `unknown content kind fallback with warning`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"future_widget","data":{"x":1}}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        val custom = assertIs<BlockContent.Custom>(result.blocks[0].content)
        assertEquals("future_widget", custom.typeId)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.UnknownContentKind })
    }

    // Unknown block type

    @Test
    fun `unknown block type produces UnknownBlockType with warning`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"callout","color":"blue"},"content":{"kind":"text","version":1,"text":"Note","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        val unknownType = assertIs<UnknownBlockType>(result.blocks[0].type)
        assertEquals("callout", unknownType.typeId)
        assertTrue(unknownType.rawTypeJson.contains("callout"))
        assertTrue(unknownType.rawTypeJson.contains("blue"))
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.UnknownBlockTypePreserved })
    }

    @Test
    fun `unknown block type round-trip preserves raw JSON`() {
        val rawTypeJson = """{"typeId":"callout","color":"blue","version":3}"""
        val unknownType = UnknownBlockType(typeId = "callout", rawTypeJson = rawTypeJson)
        val original = listOf(block(type = unknownType, content = BlockContent.Text("Note")))
        val encoded = DocumentSchema.encode(original)
        val result = DocumentSchema.decodeWithReport(encoded)
        val decoded = assertIs<UnknownBlockType>(result.blocks[0].type)
        // Verify all fields survived
        val reparsed = Json.parseToJsonElement(decoded.rawTypeJson).jsonObject
        assertEquals("callout", reparsed["typeId"]?.jsonPrimitive?.content)
        assertEquals("blue", reparsed["color"]?.jsonPrimitive?.content)
        assertEquals(3, reparsed["version"]?.jsonPrimitive?.intOrNull)
    }

    // ID modes

    @Test
    fun `preserve mode - IDs match input`() {
        val json = """{"version":1,"blocks":[{"id":"my-id","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val decoded = DocumentSchema.decodeFromString(json, DocumentDecodeOptions(idMode = BlockIdMode.Preserve))
        assertEquals("my-id", decoded[0].id.value)
    }

    @Test
    fun `regenerate mode - IDs differ from input`() {
        val json = """{"version":1,"blocks":[{"id":"my-id","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val decoded = DocumentSchema.decodeFromString(json, DocumentDecodeOptions(idMode = BlockIdMode.Regenerate))
        assertNotEquals("my-id", decoded[0].id.value)
    }

    @Test
    fun `duplicate ID regenerate - second gets new ID with warning`() {
        val json = """{"version":1,"blocks":[
            {"id":"dup","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"a","spans":[]}},
            {"id":"dup","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"b","spans":[]}}
        ]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(2, result.blocks.size)
        assertEquals("dup", result.blocks[0].id.value)
        assertNotEquals("dup", result.blocks[1].id.value)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.DuplicateIdRegenerated })
    }

    @Test
    fun `duplicate ID fail-fast throws`() {
        val json = """{"version":1,"blocks":[
            {"id":"dup","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"a","spans":[]}},
            {"id":"dup","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"b","spans":[]}}
        ]}"""
        assertFailsWith<IllegalArgumentException> {
            DocumentSchema.decodeFromString(json, DocumentDecodeOptions(duplicateIdMode = DuplicateIdMode.FailFast))
        }
    }

    @Test
    fun `missing ID generates new one with warning`() {
        val json = """{"version":1,"blocks":[{"id":"","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(1, result.blocks.size)
        assertTrue(result.blocks[0].id.value.isNotEmpty())
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.MissingIdRegenerated })
    }

    // Malformed blocks

    @Test
    fun `non-object in blocks array skipped with warning`() {
        val json = """{"version":1,"blocks":["not_an_object",{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"ok","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(1, result.blocks.size)
        assertEquals("ok", (result.blocks[0].content as BlockContent.Text).text)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.MalformedBlockSkipped })
    }

    @Test
    fun `missing typeId - block skipped with warning`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(0, result.blocks.size)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.MalformedBlockSkipped })
    }

    @Test
    fun `missing content field - defaults to Empty`() {
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"divider"}}]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        assertEquals(1, decoded.size)
        assertIs<BlockContent.Empty>(decoded[0].content)
    }

    // Custom data types round-trip

    @Test
    fun `custom data types round-trip - string, long, double, boolean, null, list, map`() {
        val data = mapOf<String, Any?>(
            "s" to "hello",
            "l" to 42L,
            "d" to 3.14,
            "b" to true,
            "n" to null,
            "list" to listOf("a", 1L),
            "map" to mapOf("nested" to "value"),
        )
        val original = listOf(block(content = BlockContent.Custom("test", data)))
        val decoded = DocumentSchema.decode(DocumentSchema.encode(original))
        val custom = assertIs<BlockContent.Custom>(decoded[0].content)
        assertEquals("hello", custom.data["s"])
        assertEquals(42L, custom.data["l"])
        assertEquals(true, custom.data["b"])
        assertNull(custom.data["n"])
        val list = assertIs<List<*>>(custom.data["list"])
        assertEquals("a", list[0])
        val map = assertIs<Map<*, *>>(custom.data["map"])
        assertEquals("value", map["nested"])
    }

    // Consumer codecs

    @Test
    fun `consumer type codec decodes custom type`() {
        val codec = object : BlockTypeCodec {
            override fun encodeType(type: BlockType): JsonObject? = null
            override fun decodeType(typeId: String, json: JsonObject): BlockType? {
                if (typeId == "callout") return BlockType.Quote // map callout → Quote for test
                return null
            }
        }
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"callout"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val decoded = DocumentSchema.decodeFromString(json, typeCodec = codec)
        assertIs<BlockType.Quote>(decoded[0].type)
    }

    @Test
    fun `consumer content codec decodes custom kind`() {
        val codec = object : BlockContentCodec {
            override fun encodeContent(content: BlockContent): JsonObject? = null
            override fun decodeContent(kind: String, json: JsonObject): BlockContent? {
                if (kind == "video") return BlockContent.Custom("video", mapOf("decoded" to true))
                return null
            }
        }
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"video","src":"clip.mp4"}}]}"""
        val decoded = DocumentSchema.decodeFromString(json, contentCodec = codec)
        val custom = assertIs<BlockContent.Custom>(decoded[0].content)
        assertEquals("video", custom.typeId)
        assertEquals(true, custom.data["decoded"])
    }

    @Test
    fun `codec returns null - falls through to fallback`() {
        val codec = object : BlockTypeCodec {
            override fun encodeType(type: BlockType): JsonObject? = null
            override fun decodeType(typeId: String, json: JsonObject): BlockType? = null
        }
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val decoded = DocumentSchema.decodeFromString(json, typeCodec = codec)
        assertIs<BlockType.Paragraph>(decoded[0].type)
    }

    // Numbered list renumbering

    @Test
    fun `numbered list renumbered after decode`() {
        val json = """{"version":1,"blocks":[
            {"id":"b1","type":{"typeId":"numbered_list","number":5},"content":{"kind":"text","version":1,"text":"a","spans":[]}},
            {"id":"b2","type":{"typeId":"numbered_list","number":9},"content":{"kind":"text","version":1,"text":"b","spans":[]}}
        ]}"""
        val decoded = DocumentSchema.decodeFromString(json)
        assertEquals(2, decoded.size)
        val nl1 = assertIs<BlockType.NumberedList>(decoded[0].type)
        val nl2 = assertIs<BlockType.NumberedList>(decoded[1].type)
        // renumberNumberedLists always starts runs from 1
        assertEquals(1, nl1.number)
        assertEquals(2, nl2.number)
    }

    // Large document

    @Test
    fun `large document 1000 blocks round-trip`() {
        val blocks = (1..1000).map { i ->
            block(id = "b$i", content = BlockContent.Text("Block $i"))
        }
        val decoded = DocumentSchema.decode(DocumentSchema.encode(blocks))
        assertEquals(1000, decoded.size)
        assertEquals("Block 500", (decoded[499].content as BlockContent.Text).text)
    }

    // Warning count

    @Test
    fun `warning count for document with multiple issues`() {
        val json = """{"version":1,"blocks":[
            "bad_entry",
            {"id":"b1","type":{"typeId":"heading_abc"},"content":{"kind":"text","version":1,"text":"","spans":[]}},
            {"id":"b1","type":{"typeId":"future_type"},"content":{"kind":"future_kind","data":{}}}
        ]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        // Expect: MalformedBlockSkipped (bad_entry) + InvalidBlockTypeParam (heading_abc)
        //       + DuplicateIdRegenerated (b1 dup) + UnknownBlockTypePreserved (future_type)
        //       + UnknownContentKind (future_kind)
        assertEquals(5, result.warnings.size)
    }

    // Skipped blocks don't pollute seenIds

    @Test
    fun `skipped block ID does not cause false duplicate on later valid block`() {
        // Block 1: missing typeId → skipped. Block 2: same id "b1" → should NOT be flagged as duplicate
        val json = """{"version":1,"blocks":[
            {"id":"b1","type":{},"content":{"kind":"empty"}},
            {"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"ok","spans":[]}}
        ]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(1, result.blocks.size)
        assertEquals("b1", result.blocks[0].id.value)
        // Only MalformedBlockSkipped for the first block, no DuplicateIdRegenerated
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.MalformedBlockSkipped })
        assertTrue(result.warnings.none { it is DocumentDecodeWarning.DuplicateIdRegenerated })
    }

    // Malformed field types don't crash decode

    @Test
    fun `non-primitive typeId does not crash`() {
        // typeId is an array instead of a string
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":["not","a","string"]},"content":{"kind":"empty"}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(0, result.blocks.size)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.MalformedBlockSkipped })
    }

    @Test
    fun `non-primitive id does not crash`() {
        val json = """{"version":1,"blocks":[{"id":{"nested":"object"},"type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        // ID is not a string primitive → treated as missing → regenerated
        assertEquals(1, result.blocks.size)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.MissingIdRegenerated })
    }

    @Test
    fun `non-primitive kind does not crash`() {
        // kind is an object, not a primitive → stringOrNull returns null → Empty
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":{"nested":"object"}}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(1, result.blocks.size)
        assertIs<BlockContent.Empty>(result.blocks[0].content)
    }

    @Test
    fun `numeric kind treated as unknown content kind`() {
        // kind=42 is a valid JsonPrimitive with content "42" → unknown kind fallback
        val json = """{"version":1,"blocks":[{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":42}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(1, result.blocks.size)
        val custom = assertIs<BlockContent.Custom>(result.blocks[0].content)
        assertEquals("42", custom.typeId)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.UnknownContentKind })
    }

    // Non-string map keys in custom data

    @Test
    fun `non-string map keys in strict mode throws`() {
        val data = mapOf<String, Any?>("nested" to mapOf(1 to "bad"))
        assertFailsWith<IllegalArgumentException> {
            DocumentSchema.encode(listOf(block(content = BlockContent.Custom("test", data))))
        }
    }

    @Test
    fun `non-string map keys in lenient mode skipped`() {
        val data = mapOf<String, Any?>("good" to "ok", "nested" to mapOf(1 to "bad"))
        val json = DocumentSchema.encode(
            blocks = listOf(block(content = BlockContent.Custom("test", data))),
            options = DocumentEncodeOptions(customDataMode = CustomDataMode.LenientSkipUnsupported),
        )
        val dataObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["data"]!!.jsonObject
        assertEquals("ok", dataObj["good"]?.jsonPrimitive?.content)
        assertFalse(dataObj.containsKey("nested"))
    }

    // Invalid JSON input

    @Test
    fun `decodeFromString with invalid JSON throws SerializationException`() {
        assertFailsWith<kotlinx.serialization.SerializationException> {
            DocumentSchema.decodeFromString("not valid json at all")
        }
    }

    @Test
    fun `decodeFromStringWithReport with invalid JSON throws SerializationException`() {
        assertFailsWith<kotlinx.serialization.SerializationException> {
            DocumentSchema.decodeFromStringWithReport("{{broken")
        }
    }
}
