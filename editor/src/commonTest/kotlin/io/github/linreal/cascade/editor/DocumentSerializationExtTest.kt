package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.serialization.BlockContentCodec
import io.github.linreal.cascade.editor.serialization.BlockTypeCodec
import io.github.linreal.cascade.editor.serialization.DocumentDecodeWarning
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentSerializationExtTest {

    // toJson

    @Test
    fun `toJson basic - paragraphs round-trip`() {
        val blocks = listOf(
            Block(BlockId("b1"), BlockType.Paragraph, BlockContent.Text("Hello")),
            Block(BlockId("b2"), BlockType.Paragraph, BlockContent.Text("World")),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)

        assertEquals(2, decoded.size)
        assertEquals("Hello", (decoded[0].content as BlockContent.Text).text)
        assertEquals("World", (decoded[1].content as BlockContent.Text).text)
    }

    @Test
    fun `toJson runtime text override - captures updated text`() {
        val blockId = BlockId("b1")
        val blocks = listOf(
            Block(blockId, BlockType.Paragraph, BlockContent.Text("snapshot text")),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        // Simulate runtime: create entry then update text
        textStates.getOrCreate(blockId, "snapshot text")
        textStates.setText(blockId, "runtime text")

        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)

        assertEquals("runtime text", (decoded[0].content as BlockContent.Text).text)
    }

    @Test
    fun `toJson runtime span override - captures runtime spans`() {
        val blockId = BlockId("b1")
        val snapshotSpans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val runtimeSpans = listOf(TextSpan(0, 3, SpanStyle.Italic))
        val blocks = listOf(
            Block(blockId, BlockType.Paragraph, BlockContent.Text("abc", snapshotSpans)),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        // Create runtime span entry with different spans
        spanStates.getOrCreate(blockId, runtimeSpans, textLength = 3)

        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)
        val text = assertIs<BlockContent.Text>(decoded[0].content)
        assertEquals(1, text.spans.size)
        assertEquals(SpanStyle.Italic, text.spans[0].style)
    }

    @Test
    fun `toJson snapshot fallback - no runtime entry uses snapshot`() {
        val blockId = BlockId("b1")
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val blocks = listOf(
            Block(blockId, BlockType.Paragraph, BlockContent.Text("Hello", spans)),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        // No getOrCreate called — simulates off-screen block
        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)
        val text = assertIs<BlockContent.Text>(decoded[0].content)
        assertEquals("Hello", text.text)
        assertEquals(1, text.spans.size)
        assertEquals(SpanStyle.Bold, text.spans[0].style)
    }

    @Test
    fun `toJson mixed - some blocks have runtime and some use snapshot`() {
        val id1 = BlockId("b1")
        val id2 = BlockId("b2")
        val blocks = listOf(
            Block(id1, BlockType.Paragraph, BlockContent.Text("snapshot1")),
            Block(id2, BlockType.Paragraph, BlockContent.Text("snapshot2")),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        // Only b1 has runtime entry
        textStates.getOrCreate(id1, "snapshot1")
        textStates.setText(id1, "runtime1")

        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)

        assertEquals("runtime1", (decoded[0].content as BlockContent.Text).text)
        assertEquals("snapshot2", (decoded[1].content as BlockContent.Text).text)
    }

    @Test
    fun `toJson non-text blocks pass through unchanged`() {
        val blocks = listOf(
            Block(BlockId("b1"), BlockType.Divider, BlockContent.Empty),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)

        // 2 blocks: divider, auto-appended trailing paragraph
        assertEquals(2, decoded.size)
        assertIs<BlockContent.Empty>(decoded[0].content)
    }

    // loadFromJson

    @Test
    fun `loadFromJson basic - sets correct blocks`() {
        val holder = EditorStateHolder()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val json = DocumentSchema.encodeToString(
            listOf(
                Block(BlockId("b1"), BlockType.Paragraph, BlockContent.Text("Hello")),
                Block(BlockId("b2"), BlockType.Heading(2), BlockContent.Text("Title")),
            )
        )

        val result = holder.loadFromJson(json, textStates, spanStates)

        assertEquals(2, holder.state.blocks.size)
        assertEquals("Hello", (holder.state.blocks[0].content as BlockContent.Text).text)
        assertIs<BlockType.Heading>(holder.state.blocks[1].type)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `loadFromJson clears runtime state`() {
        val oldId = BlockId("old")
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        // Pre-populate runtime state
        textStates.getOrCreate(oldId, "old text")
        spanStates.getOrCreate(oldId, listOf(TextSpan(0, 3, SpanStyle.Bold)), textLength = 8)

        val holder = EditorStateHolder()
        val json = DocumentSchema.encodeToString(
            listOf(Block(BlockId("new"), BlockType.Paragraph, BlockContent.Text("new text")))
        )

        holder.loadFromJson(json, textStates, spanStates)

        // Old runtime entries should be gone
        assertNull(textStates.get(oldId))
        assertNull(spanStates.get(oldId))
    }

    @Test
    fun `loadFromJson returns warnings for duplicate IDs`() {
        val holder = EditorStateHolder()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val json = """{"version":1,"blocks":[
            {"id":"dup","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"a","spans":[]}},
            {"id":"dup","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"b","spans":[]}}
        ]}"""

        val result = holder.loadFromJson(json, textStates, spanStates)

        assertEquals(2, result.blocks.size)
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.DuplicateIdRegenerated })
    }

    @Test
    fun `loadFromJson passes through codec parameters`() {
        val holder = EditorStateHolder()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val typeCodec = object : BlockTypeCodec {
            override fun encodeType(type: BlockType): JsonObject? = null
            override fun decodeType(typeId: String, json: JsonObject): BlockType? {
                if (typeId == "callout") return BlockType.Quote
                return null
            }
        }
        val contentCodec = object : BlockContentCodec {
            override fun encodeContent(content: BlockContent): JsonObject? = null
            override fun decodeContent(kind: String, json: JsonObject): BlockContent? {
                if (kind == "video") return BlockContent.Custom("video", mapOf("decoded" to true))
                return null
            }
        }

        val json = """{"version":1,"blocks":[
            {"id":"b1","type":{"typeId":"callout"},"content":{"kind":"video","src":"clip.mp4"}}
        ]}"""

        val result = holder.loadFromJson(
            json, textStates, spanStates,
            typeCodec = typeCodec, contentCodec = contentCodec,
        )

        assertEquals(1, result.blocks.size)
        assertIs<BlockType.Quote>(result.blocks[0].type)
        val custom = assertIs<BlockContent.Custom>(result.blocks[0].content)
        assertEquals("video", custom.typeId)
        assertEquals(true, custom.data["decoded"])
    }
}
