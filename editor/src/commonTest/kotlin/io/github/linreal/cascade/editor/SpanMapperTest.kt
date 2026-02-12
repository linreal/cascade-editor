package io.github.linreal.cascade.editor

import androidx.compose.ui.graphics.Color
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpanMapperTest {

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  toComposeSpanStyle — style mapping                             ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `bold maps to FontWeight Bold`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Bold)
        assertNotNull(result)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, result.fontWeight)
    }

    @Test
    fun `italic maps to FontStyle Italic`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Italic)
        assertNotNull(result)
        assertEquals(androidx.compose.ui.text.font.FontStyle.Italic, result.fontStyle)
    }

    @Test
    fun `underline maps to TextDecoration Underline`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Underline)
        assertNotNull(result)
        assertEquals(
            androidx.compose.ui.text.style.TextDecoration.Underline,
            result.textDecoration,
        )
    }

    @Test
    fun `strikethrough maps to TextDecoration LineThrough`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.StrikeThrough)
        assertNotNull(result)
        assertEquals(
            androidx.compose.ui.text.style.TextDecoration.LineThrough,
            result.textDecoration,
        )
    }

    @Test
    fun `inline code maps to Monospace font and background`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.InlineCode)
        assertNotNull(result)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, result.fontFamily)
        assertNotEquals(
            Color.Unspecified,
            result.background,
            "InlineCode should have a background color",
        )
    }

    @Test
    fun `highlight maps to background color`() {
        val argb = 0xFFFF0000 // red
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Highlight(argb))
        assertNotNull(result)
        assertNotEquals(
            Color.Unspecified,
            result.background,
            "Highlight should set background color",
        )
    }

    @Test
    fun `link maps to colored underline`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Link("https://example.com"))
        assertNotNull(result)
        assertEquals(
            androidx.compose.ui.text.style.TextDecoration.Underline,
            result.textDecoration,
        )
        assertNotEquals(
            Color.Unspecified,
            result.color,
            "Link should set text color",
        )
    }

    @Test
    fun `custom returns null`() {
        assertNull(SpanMapper.toComposeSpanStyle(SpanStyle.Custom("myType")))
    }

    @Test
    fun `custom with payload returns null`() {
        assertNull(SpanMapper.toComposeSpanStyle(SpanStyle.Custom("myType", """{"key":"val"}""")))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  toComposeSpanStyle — property isolation                        ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `bold does not set textDecoration or fontStyle`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Bold)!!
        assertNull(result.textDecoration)
        assertNull(result.fontStyle)
    }

    @Test
    fun `italic does not set fontWeight or textDecoration`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Italic)!!
        assertNull(result.fontWeight)
        assertNull(result.textDecoration)
    }

    @Test
    fun `underline does not set fontWeight or fontStyle`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Underline)!!
        assertNull(result.fontWeight)
        assertNull(result.fontStyle)
    }

    @Test
    fun `strikethrough does not set fontWeight or fontStyle`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.StrikeThrough)!!
        assertNull(result.fontWeight)
        assertNull(result.fontStyle)
    }

    @Test
    fun `highlight only sets background`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Highlight(0xFFFF0000))!!
        assertNull(result.fontWeight)
        assertNull(result.fontStyle)
        assertNull(result.textDecoration)
        assertEquals(Color.Unspecified, result.color)
    }

    @Test
    fun `link does not set fontWeight or background`() {
        val result = SpanMapper.toComposeSpanStyle(SpanStyle.Link("https://example.com"))!!
        assertNull(result.fontWeight)
        assertNull(result.fontStyle)
        assertEquals(Color.Unspecified, result.background)
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  toComposeSpanStyle — exhaustive coverage                       ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `all renderable styles produce non-null result`() {
        val renderableStyles = listOf(
            SpanStyle.Bold,
            SpanStyle.Italic,
            SpanStyle.Underline,
            SpanStyle.StrikeThrough,
            SpanStyle.InlineCode,
            SpanStyle.Highlight(0xFFFFFF00),
            SpanStyle.Link("https://example.com"),
        )
        for (style in renderableStyles) {
            assertNotNull(
                SpanMapper.toComposeSpanStyle(style),
                "Expected non-null ComposeSpanStyle for ${style::class.simpleName}",
            )
        }
    }

    @Test
    fun `different highlight colors produce different backgrounds`() {
        val red = SpanMapper.toComposeSpanStyle(SpanStyle.Highlight(0xFFFF0000L))!!
        val blue = SpanMapper.toComposeSpanStyle(SpanStyle.Highlight(0xFF0000FFL))!!
        assertNotEquals(red.background, blue.background)
    }

    @Test
    fun `different link urls produce same style`() {
        val a = SpanMapper.toComposeSpanStyle(SpanStyle.Link("https://a.com"))
        val b = SpanMapper.toComposeSpanStyle(SpanStyle.Link("https://b.com"))
        assertEquals(a, b, "Link style should not depend on URL content")
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  toOutputTransformation — null / non-null contract              ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `empty span list returns null`() {
        assertNull(SpanMapper.toOutputTransformation(emptyList()))
    }

    @Test
    fun `all custom spans returns null`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Custom("a")),
            TextSpan(5, 10, SpanStyle.Custom("b", "payload")),
        )
        assertNull(SpanMapper.toOutputTransformation(spans))
    }

    @Test
    fun `single renderable span returns non-null`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        assertNotNull(SpanMapper.toOutputTransformation(spans))
    }

    @Test
    fun `mixed renderable and custom spans returns non-null`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Custom("x")),
            TextSpan(3, 6, SpanStyle.Italic),
        )
        assertNotNull(SpanMapper.toOutputTransformation(spans))
    }

    @Test
    fun `multiple renderable spans returns non-null`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(3, 6, SpanStyle.Underline),
            TextSpan(6, 10, SpanStyle.Link("https://example.com")),
        )
        assertNotNull(SpanMapper.toOutputTransformation(spans))
    }

    @Test
    fun `each renderable style type produces non-null transformation`() {
        val styles = listOf(
            SpanStyle.Bold,
            SpanStyle.Italic,
            SpanStyle.Underline,
            SpanStyle.StrikeThrough,
            SpanStyle.InlineCode,
            SpanStyle.Highlight(0xFFFFFF00),
            SpanStyle.Link("https://example.com"),
        )
        for (style in styles) {
            val transformation = SpanMapper.toOutputTransformation(
                listOf(TextSpan(0, 5, style))
            )
            assertNotNull(
                transformation,
                "Expected non-null OutputTransformation for ${style::class.simpleName}",
            )
        }
    }

    @Test
    fun `zero-length span is not filtered at build time`() {
        // start == end is allowed by TextSpan. The mapped list includes it
        // (Bold is renderable), so toOutputTransformation returns non-null.
        // The lambda will skip the empty range at render time via clamping.
        val spans = listOf(TextSpan(3, 3, SpanStyle.Bold))
        assertNotNull(SpanMapper.toOutputTransformation(spans))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  toOutputTransformation — stability                             ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `same input produces equal-behavior transformations`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val t1 = SpanMapper.toOutputTransformation(spans)
        val t2 = SpanMapper.toOutputTransformation(spans)
        // Both should be non-null (same input)
        assertNotNull(t1)
        assertNotNull(t2)
        // They are distinct instances (new lambda each call),
        // which is expected — remember(spans) handles dedup at call site.
        assertTrue(t1 !== t2, "Each call should produce a new instance")
    }
}
