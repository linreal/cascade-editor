package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.serialization.RichTextSchema
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RichTextSchemaTest {

    // -- Round-trip: plain text (no spans) --

    @Test
    fun `round-trip plain text without spans`() {
        val original = BlockContent.Text("Hello, World!")
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original.text, decoded.text)
        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `round-trip empty text`() {
        val original = BlockContent.Text("")
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals("", decoded.text)
        assertTrue(decoded.spans.isEmpty())
    }

    // -- Round-trip: built-in singleton styles --

    @Test
    fun `round-trip bold span`() {
        val original = BlockContent.Text(
            text = "Hello bold",
            spans = listOf(TextSpan(6, 10, SpanStyle.Bold))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `round-trip italic span`() {
        val original = BlockContent.Text(
            text = "Hello italic",
            spans = listOf(TextSpan(6, 12, SpanStyle.Italic))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `round-trip underline span`() {
        val original = BlockContent.Text(
            text = "Hello underline",
            spans = listOf(TextSpan(6, 15, SpanStyle.Underline))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `round-trip strikethrough span`() {
        val original = BlockContent.Text(
            text = "Hello strike",
            spans = listOf(TextSpan(6, 12, SpanStyle.StrikeThrough))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `round-trip multiple span`() {
        val original = BlockContent.Text(
            text = "Hello multiple span with long long text!!!!",
            spans = listOf(TextSpan(6, 12, SpanStyle.StrikeThrough), TextSpan(6, 13, SpanStyle.Bold))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `round-trip inline code span`() {
        val original = BlockContent.Text(
            text = "Hello code",
            spans = listOf(TextSpan(6, 10, SpanStyle.InlineCode))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    // -- Round-trip: parameterized styles --

    @Test
    fun `round-trip highlight span`() {
        val original = BlockContent.Text(
            text = "Highlighted text",
            spans = listOf(TextSpan(0, 11, SpanStyle.Highlight(0xFFFFFF00)))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    // -- Round-trip: links --

    @Test
    fun `encode link span writes link type and normalized url`() {
        val content = BlockContent.Text(
            text = "Open example",
            spans = listOf(TextSpan(5, 12, SpanStyle.Link("https://example.com/path")))
        )

        val encoded = RichTextSchema.encode(content)
        val style = encoded["spans"]!!
            .jsonArray[0]
            .jsonObject["style"]!!
            .jsonObject

        assertEquals("link", style["type"]?.jsonPrimitive?.content)
        assertEquals("https://example.com/path", style["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun `decode link span preserves bare domain target exactly`() {
        val json = """{"version":1,"text":"Open example","spans":[{"start":5,"end":12,"style":{"type":"link","url":"example.com/path"}}]}"""

        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(listOf(TextSpan(5, 12, SpanStyle.Link("example.com/path"))), decoded.spans)
    }

    @Test
    fun `relative link target survives a save load cycle byte-identically`() {
        val original = BlockContent.Text(
            text = "Read the guide",
            spans = listOf(TextSpan(9, 14, SpanStyle.Link("../guide.md")))
        )

        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals("../guide.md", (decoded.spans.single().style as SpanStyle.Link).url)
        assertEquals(original, decoded)
    }

    @Test
    fun `mailto tel and custom-scheme link targets round-trip unchanged`() {
        val targets = listOf(
            "mailto:user@example.com",
            "tel:+123",
            "note://custom",
            "/docs",
            "#heading",
        )
        for (target in targets) {
            val original = BlockContent.Text(
                text = "Open target",
                spans = listOf(TextSpan(0, 4, SpanStyle.Link(target)))
            )

            val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

            assertEquals(original, decoded, "target $target must survive persistence unchanged")
        }
    }

    @Test
    fun `link span with missing url is dropped`() {
        val json = """
            {"version":1,"text":"Hello","spans":[
                {"start":0,"end":5,"style":{"type":"bold"}},
                {"start":0,"end":3,"style":{"type":"link"}}
            ]}
        """.trimIndent()

        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), decoded.spans)
    }

    @Test
    fun `blank link urls are dropped`() {
        val json = """
            {"version":1,"text":"Hello World","spans":[
                {"start":0,"end":5,"style":{"type":"bold"}},
                {"start":0,"end":5,"style":{"type":"link","url":"   "}}
            ]}
        """.trimIndent()

        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), decoded.spans)
    }

    @Test
    fun `round-trip bold and link spans together`() {
        val original = BlockContent.Text(
            text = "Open example",
            spans = listOf(
                TextSpan(0, 4, SpanStyle.Bold),
                TextSpan(5, 12, SpanStyle.Link("https://example.com/path")),
            )
        )

        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `sample default document link span decodes as link`() {
        val json = """
            {
                "version": 1,
                "text": "Mix bold, italic, underline, strikethrough, code, and highlights in any block.",
                "spans": [
                    {"start":4,"end":8,"style":{"type":"bold"}},
                    {"start":66,"end":71,"style":{"type":"link","url":"https://github.com/linreal/cascade-editor"}}
                ]
            }
        """.trimIndent()

        val decoded = RichTextSchema.decodeFromString(json)
        val linkStyle = assertIs<SpanStyle.Link>(decoded.spans.single { it.style is SpanStyle.Link }.style)

        assertEquals("https://github.com/linreal/cascade-editor", linkStyle.url)
    }

    // -- Round-trip: custom styles --

    @Test
    fun `round-trip custom span with structured payload`() {
        val original = BlockContent.Text(
            text = "Custom styled",
            spans = listOf(
                TextSpan(0, 6, SpanStyle.Custom("my-style", """{"key":"value","num":42}"""))
            )
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `round-trip custom span with null payload`() {
        val original = BlockContent.Text(
            text = "Custom styled",
            spans = listOf(TextSpan(0, 6, SpanStyle.Custom("marker-style")))
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `custom payload is canonicalized on round-trip`() {
        val original = BlockContent.Text(
            text = "Custom styled",
            spans = listOf(
                TextSpan(0, 6, SpanStyle.Custom("my-style", """{ "a" : 1 ,  "b" : 2 }"""))
            )
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        val customStyle = decoded.spans.single().style as SpanStyle.Custom
        assertEquals("""{"a":1,"b":2}""", customStyle.payload)
    }

    // -- Round-trip: multiple spans --

    @Test
    fun `round-trip multiple overlapping spans`() {
        val original = BlockContent.Text(
            text = "Bold and italic text",
            spans = listOf(
                TextSpan(0, 4, SpanStyle.Bold),
                TextSpan(2, 14, SpanStyle.Italic),
                TextSpan(9, 20, SpanStyle.Underline)
            )
        )
        val decoded = RichTextSchema.decodeFromString(RichTextSchema.encodeToString(original))

        assertEquals(original, decoded)
    }

    // -- Normalization: out-of-bounds --

    @Test
    fun `span exceeding text length is clamped`() {
        val json = """{"version":1,"text":"Hi","spans":[{"start":0,"end":100,"style":{"type":"bold"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(1, decoded.spans.size)
        assertEquals(0, decoded.spans[0].start)
        assertEquals(2, decoded.spans[0].end) // clamped to text.length
    }

    @Test
    fun `negative start is clamped to zero`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":-5,"end":3,"style":{"type":"bold"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(1, decoded.spans.size)
        assertEquals(0, decoded.spans[0].start)
        assertEquals(3, decoded.spans[0].end)
    }

    @Test
    fun `span fully beyond text length is dropped`() {
        val json = """{"version":1,"text":"Hi","spans":[{"start":10,"end":20,"style":{"type":"bold"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty()) // both clamped to 2, empty → dropped
    }

    @Test
    fun `empty span is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":3,"end":3,"style":{"type":"bold"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `inverted range is normalized to empty and dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":4,"end":2,"style":{"type":"bold"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty()) // end clamped to max(start, ...) = 4, but 2 < 4 → coerceIn(4, 5) = 4, empty
    }

    // -- Normalization: malformed data --

    @Test
    fun `span with missing start is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"end":3,"style":{"type":"bold"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `span with missing style is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":0,"end":3}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `unknown style type is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":0,"end":3,"style":{"type":"rainbow"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `highlight span with missing colorArgb is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":0,"end":3,"style":{"type":"highlight"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `link style with missing url is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":0,"end":3,"style":{"type":"link"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `link style with non-string url primitive is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":0,"end":3,"style":{"type":"link","url":42}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `custom span with missing typeId is dropped`() {
        val json = """{"version":1,"text":"Hello","spans":[{"start":0,"end":3,"style":{"type":"custom"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `non-object span entry is dropped without failing decode`() {
        val json = """
            {"version":1,"text":"Hello","spans":[
                {"start":0,"end":5,"style":{"type":"bold"}},
                123,
                "bad",
                true
            ]}
        """.trimIndent()
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(1, decoded.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), decoded.spans[0])
    }

    @Test
    fun `span with non-object style is dropped without failing decode`() {
        val json = """
            {"version":1,"text":"Hello","spans":[
                {"start":0,"end":5,"style":{"type":"bold"}},
                {"start":0,"end":5,"style":"bold"},
                {"start":0,"end":5,"style":42}
            ]}
        """.trimIndent()
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(1, decoded.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), decoded.spans[0])
    }

    // -- Normalization: valid spans preserved alongside invalid --

    @Test
    fun `valid spans survive alongside invalid ones`() {
        val json = """
            {"version":1,"text":"Hello World","spans":[
                {"start":0,"end":5,"style":{"type":"bold"}},
                {"start":0,"end":3,"style":{"type":"rainbow"}},
                {"start":6,"end":11,"style":{"type":"italic"}}
            ]}
        """.trimIndent()
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals(2, decoded.spans.size)
        assertEquals(SpanStyle.Bold, decoded.spans[0].style)
        assertEquals(SpanStyle.Italic, decoded.spans[1].style)
    }

    // -- Version handling --

    @Test
    fun `missing version defaults to 1`() {
        val json = """{"text":"Hello","spans":[{"start":0,"end":5,"style":{"type":"bold"}}]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals("Hello", decoded.text)
        assertEquals(1, decoded.spans.size)
    }

    @Test
    fun `unsupported future version throws`() {
        val json = """{"version":999,"text":"Hello","spans":[]}"""
        assertFailsWith<IllegalArgumentException> {
            RichTextSchema.decodeFromString(json)
        }
    }

    @Test
    fun `encoded output contains current version`() {
        val encoded = RichTextSchema.encode(BlockContent.Text("Hi"))
        val versionStr = encoded["version"].toString()
        assertEquals("${RichTextSchema.CURRENT_VERSION}", versionStr)
    }

    // -- Missing spans array --

    @Test
    fun `missing spans array decodes as empty list`() {
        val json = """{"version":1,"text":"Hello"}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals("Hello", decoded.text)
        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `non-array spans field decodes as empty list`() {
        val json = """{"version":1,"text":"Hello","spans":{"start":0}}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals("Hello", decoded.text)
        assertTrue(decoded.spans.isEmpty())
    }

    // -- Missing text field --

    @Test
    fun `missing text field decodes as empty string`() {
        val json = """{"version":1,"spans":[]}"""
        val decoded = RichTextSchema.decodeFromString(json)

        assertEquals("", decoded.text)
    }
}
