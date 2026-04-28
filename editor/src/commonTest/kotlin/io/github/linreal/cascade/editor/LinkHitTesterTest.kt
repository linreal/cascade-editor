package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.LinkHitTester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkHitTesterTest {

    @Test
    fun `offset inside link returns normalized URL`() {
        val spans = listOf(
            TextSpan(0, 7, SpanStyle.Link("https://example.com")),
        )

        assertEquals(
            "https://example.com",
            LinkHitTester.linkUrlAtVisibleOffset(spans, 3),
        )
    }

    @Test
    fun `offset outside link returns null`() {
        val spans = listOf(
            TextSpan(2, 6, SpanStyle.Link("https://example.com")),
        )

        assertNull(LinkHitTester.linkUrlAtVisibleOffset(spans, 1))
        assertNull(LinkHitTester.linkUrlAtVisibleOffset(spans, 6))
    }

    @Test
    fun `overlapping non-link spans do not affect link hit testing`() {
        val spans = listOf(
            TextSpan(0, 8, SpanStyle.Bold),
            TextSpan(2, 6, SpanStyle.Link("https://example.com")),
            TextSpan(3, 5, SpanStyle.Italic),
        )

        assertNull(LinkHitTester.linkUrlAtVisibleOffset(spans, 1))
        assertEquals(
            "https://example.com",
            LinkHitTester.linkUrlAtVisibleOffset(spans, 4),
        )
        assertNull(LinkHitTester.linkUrlAtVisibleOffset(spans, 6))
    }

    @Test
    fun `end boundary does not hit link`() {
        val spans = listOf(
            TextSpan(2, 6, SpanStyle.Link("https://example.com")),
        )

        assertNull(LinkHitTester.linkUrlAtVisibleOffset(spans, 6))
    }

    @Test
    fun `tap URL resolves only for unfocused idle editor state`() {
        val spans = listOf(
            TextSpan(2, 6, SpanStyle.Link("https://example.com")),
        )

        assertEquals(
            "https://example.com",
            LinkHitTester.linkUrlForTap(
                isFocused = false,
                isDragging = false,
                hasBlockSelection = false,
                visibleOffset = 3,
                spans = spans,
            ),
        )
        assertNull(
            LinkHitTester.linkUrlForTap(
                isFocused = true,
                isDragging = false,
                hasBlockSelection = false,
                visibleOffset = 3,
                spans = spans,
            ),
        )
        assertNull(
            LinkHitTester.linkUrlForTap(
                isFocused = false,
                isDragging = true,
                hasBlockSelection = false,
                visibleOffset = 3,
                spans = spans,
            ),
        )
        assertNull(
            LinkHitTester.linkUrlForTap(
                isFocused = false,
                isDragging = false,
                hasBlockSelection = true,
                visibleOffset = 3,
                spans = spans,
            ),
        )
        assertNull(
            LinkHitTester.linkUrlForTap(
                isFocused = false,
                isDragging = false,
                hasBlockSelection = false,
                hasTextSelection = true,
                visibleOffset = 3,
                spans = spans,
            ),
        )
    }

    @Test
    fun `non-http schemes resolve verbatim under permissive policy`() {
        val spans = listOf(
            TextSpan(0, 4, SpanStyle.Link("ftp://example.com")),
        )

        assertEquals("ftp://example.com", LinkHitTester.linkUrlAtVisibleOffset(spans, 2))
    }
}
