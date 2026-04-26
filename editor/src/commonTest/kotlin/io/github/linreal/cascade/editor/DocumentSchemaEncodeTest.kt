package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.serialization.BlockContentCodec
import io.github.linreal.cascade.editor.serialization.BlockTypeCodec
import io.github.linreal.cascade.editor.serialization.CustomDataMode
import io.github.linreal.cascade.editor.serialization.DocumentEncodeOptions
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocumentSchemaEncodeTest {

    private fun block(
        id: String = "b1",
        type: BlockType = BlockType.Paragraph,
        content: BlockContent = BlockContent.Text(""),
        attributes: BlockAttributes = BlockAttributes.Default,
    ): Block = Block(id = BlockId(id), type = type, content = content, attributes = attributes)

    // Envelope

    @Test
    fun `encode empty document`() {
        val json = DocumentSchema.encode(emptyList())
        assertEquals(2, json["version"]?.jsonPrimitive?.int)
        assertEquals(0, json["blocks"]?.jsonArray?.size)
    }

    @Test
    fun `version field is current schema version`() {
        val json = DocumentSchema.encode(listOf(block()))
        assertEquals(2, json["version"]?.jsonPrimitive?.int)
    }

    @Test
    fun `encodeToString returns valid JSON`() {
        val str = DocumentSchema.encodeToString(listOf(block()))
        val reparsed = Json.parseToJsonElement(str).jsonObject
        assertEquals(2, reparsed["version"]?.jsonPrimitive?.int)
        assertEquals(1, reparsed["blocks"]?.jsonArray?.size)
    }

    // Block type encoding

    @Test
    fun `encode paragraph type`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Paragraph)))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("paragraph", typeObj["typeId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `encode heading type - level encoded in suffix`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Heading(2))))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("heading_2", typeObj["typeId"]?.jsonPrimitive?.content)
        // No separate "level" field
        assertFalse(typeObj.containsKey("level"))
    }

    @Test
    fun `encode todo checked true`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Todo(checked = true))))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("todo", typeObj["typeId"]?.jsonPrimitive?.content)
        assertEquals(true, typeObj["checked"]?.jsonPrimitive?.content?.toBooleanStrict())
    }

    @Test
    fun `encode todo checked false`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Todo(checked = false))))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("todo", typeObj["typeId"]?.jsonPrimitive?.content)
        assertEquals(false, typeObj["checked"]?.jsonPrimitive?.content?.toBooleanStrict())
    }

    @Test
    fun `encode bullet list type`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.BulletList)))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("bullet_list", typeObj["typeId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `encode numbered list with number`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.NumberedList(number = 3))))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("numbered_list", typeObj["typeId"]?.jsonPrimitive?.content)
        assertEquals(3, typeObj["number"]?.jsonPrimitive?.int)
    }

    @Test
    fun `encode block attributes with indentation level`() {
        val json = DocumentSchema.encode(
            listOf(
                block(
                    type = BlockType.NumberedList(number = 1),
                    attributes = BlockAttributes(indentationLevel = 1),
                )
            )
        )
        val blockObj = json["blocks"]!!.jsonArray[0].jsonObject
        val attributesObj = blockObj["attributes"]!!.jsonObject
        assertEquals(1, attributesObj["indentationLevel"]?.jsonPrimitive?.int)
    }

    @Test
    fun `encode omits default block attributes`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Paragraph)))

        val blockObj = json["blocks"]!!.jsonArray[0].jsonObject

        assertFalse(blockObj.containsKey("attributes"))
    }

    @Test
    fun `encode omits unsupported block indentation attributes`() {
        val json = DocumentSchema.encode(
            listOf(
                block(
                    type = BlockType.Heading(1),
                    attributes = BlockAttributes(indentationLevel = 2),
                )
            )
        )

        val blockObj = json["blocks"]!!.jsonArray[0].jsonObject

        assertFalse(blockObj.containsKey("attributes"))
    }

    @Test
    fun `encode quote type`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Quote)))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("quote", typeObj["typeId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `encode divider type`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Divider, content = BlockContent.Empty)))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("divider", typeObj["typeId"]?.jsonPrimitive?.content)
    }

    // Block content encoding

    @Test
    fun `encode text content with spans - delegates to RichTextSchema`() {
        val textContent = BlockContent.Text(
            text = "Hello bold",
            spans = listOf(TextSpan(6, 10, SpanStyle.Bold)),
        )
        val json = DocumentSchema.encode(listOf(block(content = textContent)))
        val contentObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject

        assertEquals("text", contentObj["kind"]?.jsonPrimitive?.content)
        assertEquals("Hello bold", contentObj["text"]?.jsonPrimitive?.content)
        // Inner RichTextSchema version preserved
        assertNotNull(contentObj["version"])
        assertEquals(1, contentObj["version"]?.jsonPrimitive?.int)
        // Spans present
        val spans = contentObj["spans"]?.jsonArray
        assertNotNull(spans)
        assertEquals(1, spans.size)
    }

    @Test
    fun `text content includes inner RichTextSchema version alongside kind`() {
        val json = DocumentSchema.encode(listOf(block(content = BlockContent.Text("hi"))))
        val contentObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject
        assertTrue(contentObj.containsKey("kind"))
        assertTrue(contentObj.containsKey("version"))
        assertTrue(contentObj.containsKey("text"))
        assertTrue(contentObj.containsKey("spans"))
    }

    @Test
    fun `encode empty content`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Divider, content = BlockContent.Empty)))
        val contentObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject
        assertEquals("empty", contentObj["kind"]?.jsonPrimitive?.content)
    }

    @Test
    fun `encode custom content`() {
        val customContent = BlockContent.Custom(
            typeId = "embed",
            data = mapOf("url" to "https://youtube.com/watch?v=123", "width" to 640),
        )
        val json = DocumentSchema.encode(listOf(block(content = customContent)))
        val contentObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject

        assertEquals("embed", contentObj["kind"]?.jsonPrimitive?.content)
        val dataObj = contentObj["data"]?.jsonObject
        assertNotNull(dataObj)
        assertEquals("https://youtube.com/watch?v=123", dataObj["url"]?.jsonPrimitive?.content)
        assertEquals(640, dataObj["width"]?.jsonPrimitive?.int)
    }

    // Custom data map serialization

    @Test
    fun `custom data - string int long double boolean null`() {
        val data = mapOf<String, Any?>(
            "s" to "hello",
            "i" to 42,
            "l" to 999999999999L,
            "d" to 3.14,
            "b" to true,
            "n" to null,
        )
        val json = DocumentSchema.encode(listOf(block(content = BlockContent.Custom("test", data))))
        val dataObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["data"]!!.jsonObject

        assertEquals("hello", dataObj["s"]?.jsonPrimitive?.content)
        assertEquals("42", dataObj["i"]?.jsonPrimitive?.content)
        assertEquals("999999999999", dataObj["l"]?.jsonPrimitive?.content)
        assertEquals(true, dataObj["b"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertTrue(dataObj["n"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `custom data - nested list`() {
        val data = mapOf<String, Any?>("items" to listOf("a", "b", 3))
        val json = DocumentSchema.encode(listOf(block(content = BlockContent.Custom("test", data))))
        val dataObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["data"]!!.jsonObject
        val items = dataObj["items"]?.jsonArray
        assertNotNull(items)
        assertEquals(3, items.size)
        assertEquals("a", items[0].jsonPrimitive.content)
    }

    @Test
    fun `custom data - nested map`() {
        val data = mapOf<String, Any?>("meta" to mapOf("key" to "value"))
        val json = DocumentSchema.encode(listOf(block(content = BlockContent.Custom("test", data))))
        val dataObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["data"]!!.jsonObject
        val meta = dataObj["meta"]?.jsonObject
        assertNotNull(meta)
        assertEquals("value", meta["key"]?.jsonPrimitive?.content)
    }

    @Test
    fun `strict mode - unsupported type in custom data throws`() {
        val data = mapOf<String, Any?>("bad" to object {})
        assertFailsWith<IllegalArgumentException> {
            DocumentSchema.encode(
                blocks = listOf(block(content = BlockContent.Custom("test", data))),
                options = DocumentEncodeOptions(customDataMode = CustomDataMode.Strict),
            )
        }
    }

    @Test
    fun `lenient mode - unsupported type in custom data key skipped`() {
        val data = mapOf<String, Any?>("good" to "ok", "bad" to object {})
        val json = DocumentSchema.encode(
            blocks = listOf(block(content = BlockContent.Custom("test", data))),
            options = DocumentEncodeOptions(customDataMode = CustomDataMode.LenientSkipUnsupported),
        )
        val dataObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["data"]!!.jsonObject
        assertEquals("ok", dataObj["good"]?.jsonPrimitive?.content)
        assertFalse(dataObj.containsKey("bad"))
    }

    // Codec hooks

    @Test
    fun `consumer BlockTypeCodec encodes custom type`() {
        val customType = object : io.github.linreal.cascade.editor.core.CustomBlockType {
            override val typeId = "callout"
            override val displayName = "Callout"
        }
        val codec = object : BlockTypeCodec {
            override fun encodeType(type: BlockType): JsonObject? {
                if (type.typeId == "callout") {
                    return buildJsonObject {
                        put("typeId", JsonPrimitive("callout"))
                        put("color", JsonPrimitive("yellow"))
                    }
                }
                return null
            }
            override fun decodeType(typeId: String, json: JsonObject): BlockType? = null
        }
        val json = DocumentSchema.encode(
            blocks = listOf(block(type = customType)),
            typeCodec = codec,
        )
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("callout", typeObj["typeId"]?.jsonPrimitive?.content)
        assertEquals("yellow", typeObj["color"]?.jsonPrimitive?.content)
    }

    @Test
    fun `consumer BlockContentCodec encodes custom content`() {
        val codec = object : BlockContentCodec {
            override fun encodeContent(content: BlockContent): JsonObject? {
                if (content is BlockContent.Custom && content.typeId == "video") {
                    return buildJsonObject {
                        put("kind", JsonPrimitive("video"))
                        put("src", JsonPrimitive(content.data["src"] as String))
                    }
                }
                return null
            }
            override fun decodeContent(kind: String, json: JsonObject): BlockContent? = null
        }
        val json = DocumentSchema.encode(
            blocks = listOf(block(content = BlockContent.Custom("video", mapOf("src" to "clip.mp4")))),
            contentCodec = codec,
        )
        val contentObj = json["blocks"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject
        assertEquals("video", contentObj["kind"]?.jsonPrimitive?.content)
        assertEquals("clip.mp4", contentObj["src"]?.jsonPrimitive?.content)
    }

    @Test
    fun `codec returns null - falls through to default encoding`() {
        val codec = object : BlockTypeCodec {
            override fun encodeType(type: BlockType): JsonObject? = null
            override fun decodeType(typeId: String, json: JsonObject): BlockType? = null
        }
        val json = DocumentSchema.encode(
            blocks = listOf(block(type = BlockType.Paragraph)),
            typeCodec = codec,
        )
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("paragraph", typeObj["typeId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `no codec provided - built-in types encode normally`() {
        val json = DocumentSchema.encode(listOf(block(type = BlockType.Quote)))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject
        assertEquals("quote", typeObj["typeId"]?.jsonPrimitive?.content)
    }

    // UnknownBlockType

    @Test
    fun `UnknownBlockType re-emits rawTypeJson as structured JSON`() {
        val rawJson = """{"typeId":"future_widget","flavor":"spicy","version":2}"""
        val unknownType = UnknownBlockType(typeId = "future_widget", rawTypeJson = rawJson)
        val json = DocumentSchema.encode(listOf(block(type = unknownType)))
        val typeObj = json["blocks"]!!.jsonArray[0].jsonObject["type"]!!.jsonObject

        assertEquals("future_widget", typeObj["typeId"]?.jsonPrimitive?.content)
        assertEquals("spicy", typeObj["flavor"]?.jsonPrimitive?.content)
        assertEquals(2, typeObj["version"]?.jsonPrimitive?.int)
    }

    // Block ID

    @Test
    fun `block id is encoded`() {
        val json = DocumentSchema.encode(listOf(block(id = "my-block-id")))
        val blockObj = json["blocks"]!!.jsonArray[0].jsonObject
        assertEquals("my-block-id", blockObj["id"]?.jsonPrimitive?.content)
    }
}
