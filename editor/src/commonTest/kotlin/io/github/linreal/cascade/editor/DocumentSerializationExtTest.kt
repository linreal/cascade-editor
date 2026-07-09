package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
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
import io.github.linreal.cascade.editor.serialization.resolveCurrentBlocks
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
    fun `toJson runtime span override - captures runtime link spans`() {
        val blockId = BlockId("b1")
        val runtimeLink = TextSpan(0, 4, SpanStyle.Link("https://example.com"))
        val blocks = listOf(
            Block(blockId, BlockType.Paragraph, BlockContent.Text("link")),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        spanStates.getOrCreate(blockId, listOf(runtimeLink), textLength = 4)

        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)
        val text = assertIs<BlockContent.Text>(decoded[0].content)

        assertEquals(listOf(runtimeLink), text.spans)
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
    fun `resolveCurrentBlocks strips spans from non-spans block types`() {
        // A Code block (supportsSpans = false) should never persist spans, even
        // if a malformed snapshot or stale runtime state still carries some.
        val blockId = BlockId("c1")
        val staleSpans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val blocks = listOf(
            Block(blockId, BlockType.Code, BlockContent.Text("hello", staleSpans)),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        // Without runtime entries: snapshot spans must be stripped on resolve.
        val resolved = resolveCurrentBlocks(holder, textStates, spanStates)
        val resolvedContent = assertIs<BlockContent.Text>(resolved[0].content)
        assertEquals("hello", resolvedContent.text)
        assertTrue(resolvedContent.spans.isEmpty())
    }

    @Test
    fun `resolveCurrentBlocks strips runtime spans for non-spans block types`() {
        // Defensive: even if runtime span state is non-empty (e.g. a same-id
        // conversion left stale state behind), the resolved block has empty spans.
        val blockId = BlockId("c1")
        val blocks = listOf(
            Block(blockId, BlockType.Code, BlockContent.Text("hello")),
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "hello")
        // Bypass type-aware gating to seed defensive coverage.
        spanStates.getOrCreate(
            blockId,
            listOf(TextSpan(0, 5, SpanStyle.Italic)),
            textLength = 5,
        )

        val resolved = resolveCurrentBlocks(holder, textStates, spanStates)
        val resolvedContent = assertIs<BlockContent.Text>(resolved[0].content)
        assertEquals("hello", resolvedContent.text)
        assertTrue(resolvedContent.spans.isEmpty())
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
    fun `toJson preserves block attributes while resolving runtime text`() {
        val blockId = BlockId("b1")
        val parentId = BlockId("parent")
        val childId = BlockId("child")
        val blocks = listOf(
            Block(
                id = parentId,
                type = BlockType.Paragraph,
                content = BlockContent.Text("parent"),
            ),
            Block(
                id = childId,
                type = BlockType.BulletList,
                content = BlockContent.Text("child"),
                attributes = BlockAttributes(indentationLevel = 1),
            ),
            Block(
                id = blockId,
                type = BlockType.BulletList,
                content = BlockContent.Text("snapshot"),
                attributes = BlockAttributes(indentationLevel = 2),
            )
        )
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "snapshot")
        textStates.setText(blockId, "runtime")

        val json = holder.toJson(textStates, spanStates)
        val decoded = DocumentSchema.decodeFromString(json)

        assertEquals("runtime", (decoded[2].content as BlockContent.Text).text)
        assertEquals(2, decoded[2].attributes.indentationLevel)
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
    fun `loadFromJson applies decoded block attributes`() {
        val holder = EditorStateHolder()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val json = """{"version":2,"blocks":[
            {"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"Parent","spans":[]}},
            {"id":"b2","type":{"typeId":"paragraph"},"attributes":{"indentationLevel":1},"content":{"kind":"text","version":1,"text":"Nested","spans":[]}}
        ]}"""

        holder.loadFromJson(json, textStates, spanStates)

        assertEquals(1, holder.state.blocks[1].attributes.indentationLevel)
    }

    @Test
    fun `loadFromJson preserves free indentation on first block and after unsupported boundary`() {
        val holder = EditorStateHolder()
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val json = """{"version":2,"blocks":[
            {"id":"b1","type":{"typeId":"paragraph"},"attributes":{"indentationLevel":5},"content":{"kind":"text","version":1,"text":"Indented root","spans":[]}},
            {"id":"h1","type":{"typeId":"heading_1"},"content":{"kind":"text","version":1,"text":"Boundary","spans":[]}},
            {"id":"b2","type":{"typeId":"paragraph"},"attributes":{"indentationLevel":4},"content":{"kind":"text","version":1,"text":"Indented after boundary","spans":[]}}
        ]}"""

        holder.loadFromJson(json, textStates, spanStates)

        assertEquals(5, holder.state.blocks[0].attributes.indentationLevel)
        assertEquals(0, holder.state.blocks[1].attributes.indentationLevel)
        assertEquals(4, holder.state.blocks[2].attributes.indentationLevel)
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
    fun `loadFromJson hard replaces document and runtime state on parse failure`() {
        val keptId = BlockId("kept")
        val holder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(Block(keptId, BlockType.Paragraph, BlockContent.Text("Keep me"))),
            ),
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        textStates.getOrCreate(keptId, "Keep me edited")

        val result = holder.loadFromJson("{ not json", textStates, spanStates)

        assertTrue(result.warnings.any { it is DocumentDecodeWarning.DocumentParseFailed })
        assertEquals(1, holder.state.blocks.size)
        assertTrue(holder.state.blocks.single().content is BlockContent.Text)
        assertEquals("", (holder.state.blocks.single().content as BlockContent.Text).text)
        assertTrue(holder.state.blocks.none { it.id == keptId })
        assertNull(textStates.get(keptId))
        assertNull(spanStates.get(keptId))
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
