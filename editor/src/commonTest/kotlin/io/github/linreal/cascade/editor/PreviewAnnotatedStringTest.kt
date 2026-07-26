package io.github.linreal.cascade.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle as ComposeSpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanMapper
import io.github.linreal.cascade.editor.richtext.buildPreviewAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreviewAnnotatedStringTest {

    private val inlineCodeBackground = Color(0x14000000)
    private val highlightBackground = Color(0xFFFFEB3B)
    private val linkText = Color(0xFF1A73E8)

    @Test
    fun `bold uses the canonical font weight`() {
        val result = build(
            text = "bold",
            spans = listOf(TextSpan(0, 4, SpanStyle.Bold)),
        )

        val run = result.singleRun()
        assertEquals(0, run.start)
        assertEquals(4, run.end)
        assertEquals(FontWeight.Bold, run.item.fontWeight)
    }

    @Test
    fun `italic uses the canonical font style`() {
        val result = build(
            text = "italic",
            spans = listOf(TextSpan(0, 6, SpanStyle.Italic)),
        )

        assertEquals(FontStyle.Italic, result.singleRun().item.fontStyle)
    }

    @Test
    fun `underline uses the canonical decoration`() {
        val result = build(
            text = "underlined",
            spans = listOf(TextSpan(0, 10, SpanStyle.Underline)),
        )

        assertEquals(TextDecoration.Underline, result.singleRun().item.textDecoration)
    }

    @Test
    fun `strikethrough uses the canonical decoration`() {
        val result = build(
            text = "struck",
            spans = listOf(TextSpan(0, 6, SpanStyle.StrikeThrough)),
        )

        assertEquals(TextDecoration.LineThrough, result.singleRun().item.textDecoration)
    }

    @Test
    fun `inline code uses the canonical monospace font and themed background`() {
        val result = build(
            text = "code",
            spans = listOf(TextSpan(0, 4, SpanStyle.InlineCode)),
        )

        val style = result.singleRun().item
        assertEquals(FontFamily.Monospace, style.fontFamily)
        assertEquals(inlineCodeBackground, style.background)
    }

    @Test
    fun `highlight uses the editor theme color instead of the stored color`() {
        val result = build(
            text = "marked",
            spans = listOf(TextSpan(0, 6, SpanStyle.Highlight(0xFFFF0000L))),
        )

        assertEquals(highlightBackground, result.singleRun().item.background)
    }

    @Test
    fun `link uses the canonical theme color and underline`() {
        val result = build(
            text = "linked",
            spans = listOf(TextSpan(0, 6, SpanStyle.Link("https://example.com"))),
        )

        val style = result.singleRun().item
        assertEquals(linkText, style.color)
        assertEquals(TextDecoration.Underline, style.textDecoration)
    }

    @Test
    fun `link annotation delegates the validated stored target exactly once`() {
        val opened = mutableListOf<String>()
        val result = buildPreviewAnnotatedString(
            text = "open guide",
            spans = listOf(
                TextSpan(5, 10, SpanStyle.Link("  ../guide.md  ")),
            ),
            inlineCodeBackground = inlineCodeBackground,
            highlightBackground = highlightBackground,
            linkText = linkText,
            onOpenLink = opened::add,
        )

        val annotation = result
            .getLinkAnnotations(start = 0, end = result.length)
            .single()
            .item as LinkAnnotation.Clickable
        annotation.linkInteractionListener?.onClick(annotation)

        assertEquals(listOf("../guide.md"), opened)
    }

    @Test
    fun `missing link handler keeps visual styling without clickable annotation`() {
        val result = build(
            text = "linked",
            spans = listOf(TextSpan(0, 6, SpanStyle.Link("https://example.com"))),
        )

        assertTrue(result.getLinkAnnotations(0, result.length).isEmpty())
        assertEquals(linkText, result.singleRun().item.color)
    }

    @Test
    fun `link annotation clamps its range to visible text`() {
        val result = buildPreviewAnnotatedString(
            text = "short",
            spans = listOf(
                TextSpan(2, Int.MAX_VALUE, SpanStyle.Link("https://example.com")),
                TextSpan(5, 20, SpanStyle.Link("https://hidden.example")),
            ),
            inlineCodeBackground = inlineCodeBackground,
            highlightBackground = highlightBackground,
            linkText = linkText,
            onOpenLink = {},
        )

        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals(2, link.start)
        assertEquals(5, link.end)
    }

    @Test
    fun `oversized and empty ranges are clamped or dropped`() {
        val result = build(
            text = "short",
            spans = listOf(
                TextSpan(2, Int.MAX_VALUE, SpanStyle.Bold),
                TextSpan(5, 20, SpanStyle.Italic),
                TextSpan(1, 1, SpanStyle.Underline),
            ),
        )

        val run = result.singleRun()
        assertEquals(2, run.start)
        assertEquals(5, run.end)
        assertEquals(FontWeight.Bold, run.item.fontWeight)
    }

    @Test
    fun `negative and reversed ranges are rejected by the document model`() {
        assertFailsWith<IllegalArgumentException> {
            TextSpan(-1, 2, SpanStyle.Bold)
        }
        assertFailsWith<IllegalArgumentException> {
            TextSpan(2, 1, SpanStyle.Bold)
        }
    }

    @Test
    fun `shared range adapter safely clamps malformed mapped runs`() {
        val bold = ComposeSpanStyle(fontWeight = FontWeight.Bold)
        val malformed = listOf(
            SpanMapper.RenderSpan(-4, 2, bold),
            SpanMapper.RenderSpan(4, 2, bold),
            SpanMapper.RenderSpan(2, 20, bold),
            SpanMapper.RenderSpan(3, 3, bold),
        )
        val clamped = malformed.mapNotNull {
            SpanMapper.clampRenderableSpan(
                span = it,
                visibleTextLength = 5,
            )
        }

        assertEquals(
            listOf(
                SpanMapper.RenderSpan(0, 2, bold),
                SpanMapper.RenderSpan(2, 5, bold),
            ),
            clamped,
        )
    }

    @Test
    fun `bold and italic overlaps remain independent compatible runs`() {
        val result = build(
            text = "overlap",
            spans = listOf(
                TextSpan(0, 5, SpanStyle.Bold),
                TextSpan(2, 7, SpanStyle.Italic),
            ),
        )

        assertTrue(
            result.spanStyles.any {
                it.start == 0 && it.end == 5 && it.item.fontWeight == FontWeight.Bold
            },
        )
        assertTrue(
            result.spanStyles.any {
                it.start == 2 && it.end == 7 && it.item.fontStyle == FontStyle.Italic
            },
        )
    }

    @Test
    fun `underline and strikethrough overlap emits their combined decoration`() {
        val result = build(
            text = "decorated",
            spans = listOf(
                TextSpan(0, 5, SpanStyle.Underline),
                TextSpan(3, 8, SpanStyle.StrikeThrough),
            ),
        )
        val combined = TextDecoration.combine(
            listOf(TextDecoration.Underline, TextDecoration.LineThrough),
        )

        assertTrue(
            result.spanStyles.any {
                it.start == 3 && it.end == 5 && it.item.textDecoration == combined
            },
        )
    }

    @Test
    fun `explicit underline is clipped around a link-owned underline`() {
        val result = build(
            text = "linkwrap",
            spans = listOf(
                TextSpan(0, 8, SpanStyle.Underline),
                TextSpan(2, 6, SpanStyle.Link("https://example.com")),
            ),
        )

        val plainUnderlinesInLinkRange = result.spanStyles.count {
            it.start < 6 &&
                it.end > 2 &&
                it.item.textDecoration == TextDecoration.Underline &&
                it.item.color == Color.Unspecified
        }
        assertEquals(0, plainUnderlinesInLinkRange)
        assertTrue(
            result.spanStyles.any {
                it.start == 0 &&
                    it.end == 2 &&
                    it.item.textDecoration == TextDecoration.Underline
            },
        )
        assertTrue(
            result.spanStyles.any {
                it.start == 2 &&
                    it.end == 6 &&
                    it.item.color == linkText &&
                    it.item.textDecoration == TextDecoration.Underline
            },
        )
        assertTrue(
            result.spanStyles.any {
                it.start == 6 &&
                    it.end == 8 &&
                    it.item.textDecoration == TextDecoration.Underline
            },
        )
    }

    @Test
    fun `base todo strikethrough is composed with run decorations`() {
        val result = build(
            text = "todo",
            spans = listOf(TextSpan(0, 4, SpanStyle.Underline)),
            baseDecoration = TextDecoration.LineThrough,
        )
        val combined = TextDecoration.combine(
            listOf(TextDecoration.LineThrough, TextDecoration.Underline),
        )

        assertEquals(combined, result.singleRun().item.textDecoration)
    }

    @Test
    fun `unsupported custom style is ignored without hiding supported styles`() {
        val result = build(
            text = "mixed",
            spans = listOf(
                TextSpan(0, 5, SpanStyle.Custom("consumer.emphasis", """{"level":2}""")),
                TextSpan(1, 4, SpanStyle.Bold),
            ),
        )

        val run = result.singleRun()
        assertEquals(1, run.start)
        assertEquals(4, run.end)
        assertEquals(FontWeight.Bold, run.item.fontWeight)
    }

    @Test
    fun `empty inputs produce a valid empty annotated string`() {
        val result = build(text = "", spans = emptyList())

        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `mapping leaves the input span list and its elements unchanged`() {
        val bold = TextSpan(0, 4, SpanStyle.Bold)
        val oversized = TextSpan(2, 40, SpanStyle.Italic)
        val spans = mutableListOf(bold, oversized)
        val snapshot = spans.toList()

        build(text = "input", spans = spans)

        assertEquals(snapshot, spans)
        assertSame(bold, spans[0])
        assertSame(oversized, spans[1])
    }

    @Test
    fun `output contains visible text exactly and introduces no sentinel`() {
        val text = "Visible text"

        val result = build(
            text = text,
            spans = listOf(TextSpan(0, text.length, SpanStyle.Bold)),
        )

        assertEquals(text, result.text)
        assertFalse('\u200B' in result.text)
    }

    private fun build(
        text: String,
        spans: List<TextSpan>,
        baseDecoration: TextDecoration? = null,
    ): AnnotatedString = buildPreviewAnnotatedString(
        text = text,
        spans = spans,
        inlineCodeBackground = inlineCodeBackground,
        highlightBackground = highlightBackground,
        linkText = linkText,
        baseDecoration = baseDecoration,
    )

    private fun AnnotatedString.singleRun(): AnnotatedString.Range<ComposeSpanStyle> {
        assertEquals(1, spanStyles.size)
        return spanStyles.single()
    }
}
