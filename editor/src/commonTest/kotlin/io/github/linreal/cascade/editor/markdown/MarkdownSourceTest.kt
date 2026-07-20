package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCascadeMarkdownApi::class)
class MarkdownSourceTest {

    @Test
    fun `crlf input yields logical lines without terminators and ranges including them`() {
        val source = MarkdownSource.of("alpha\r\nbeta\r\n")

        assertEquals(2, source.lineCount)
        assertEquals("alpha", source.lineContent(0))
        assertEquals("beta", source.lineContent(1))
        assertEquals(MarkdownSourceRange(0, 5), source.lineContentRange(0))
        assertEquals(MarkdownSourceRange(0, 7), source.lineRange(0))
        assertEquals(MarkdownSourceRange(7, 11), source.lineContentRange(1))
        assertEquals(MarkdownSourceRange(7, 13), source.lineRange(1))
        assertEquals(MarkdownLineEndings.CrLf, source.lineEndings)
        assertTrue(source.endsWithNewline)
    }

    @Test
    fun `mixed line endings are reported as mixed`() {
        val source = MarkdownSource.of("a\nb\r\nc\rd")

        assertEquals(4, source.lineCount)
        assertEquals(listOf("a", "b", "c", "d"), (0 until source.lineCount).map(source::lineContent))
        assertEquals(MarkdownLineEndings.Mixed, source.lineEndings)
        assertFalse(source.endsWithNewline)
    }

    @Test
    fun `lone cr endings split lines`() {
        val source = MarkdownSource.of("a\rb\r")

        assertEquals(2, source.lineCount)
        assertEquals("a", source.lineContent(0))
        assertEquals("b", source.lineContent(1))
        assertEquals(MarkdownLineEndings.Cr, source.lineEndings)
        assertTrue(source.endsWithNewline)
    }

    @Test
    fun `leading bom is recorded and line 1 column 1 resolves past it`() {
        val source = MarkdownSource.of("\uFEFF# title\n")

        assertTrue(source.hasBom)
        assertEquals(1, source.lineCount)
        assertEquals("# title", source.lineContent(0))
        assertEquals(MarkdownSourceRange(1, 8), source.lineContentRange(0))
        // Line 1 column 1 is the first character after the BOM.
        assertEquals(MarkdownSourceLocation(1, 1), source.locationOf(1))
        // The BOM offset itself clamps to line 1 column 1.
        assertEquals(MarkdownSourceLocation(1, 1), source.locationOf(0))
    }

    @Test
    fun `empty input produces zero lines without exception`() {
        val source = MarkdownSource.of("")

        assertEquals(0, source.lineCount)
        assertFalse(source.hasBom)
        assertFalse(source.endsWithNewline)
        assertEquals(MarkdownLineEndings.None, source.lineEndings)
        assertEquals(MarkdownSourceLocation(1, 1), source.locationOf(0))
    }

    @Test
    fun `slice across a line boundary is character-exact`() {
        val input = "one\r\ntwo\nthree"
        val source = MarkdownSource.of(input)

        val range = MarkdownSourceRange(2, 10)
        assertEquals(input.substring(2, 10), source.slice(range))
        assertEquals("e\r\ntwo\n", source.slice(MarkdownSourceRange(2, 9)))
    }

    @Test
    fun `single line without terminator has no final newline`() {
        val source = MarkdownSource.of("plain")

        assertEquals(1, source.lineCount)
        assertEquals("plain", source.lineContent(0))
        assertFalse(source.endsWithNewline)
        assertEquals(MarkdownLineEndings.None, source.lineEndings)
        assertEquals(MarkdownSourceRange(0, 5), source.lineRange(0))
    }

    @Test
    fun `lone newline is one empty line with final newline`() {
        val source = MarkdownSource.of("\n")

        assertEquals(1, source.lineCount)
        assertEquals("", source.lineContent(0))
        assertTrue(source.endsWithNewline)
        assertEquals(MarkdownSourceRange(0, 0), source.lineContentRange(0))
        assertEquals(MarkdownSourceRange(0, 1), source.lineRange(0))
    }

    @Test
    fun `location lookup covers first last and empty lines`() {
        val source = MarkdownSource.of("ab\n\ncd")

        // First line.
        assertEquals(MarkdownSourceLocation(1, 1), source.locationOf(0))
        assertEquals(MarkdownSourceLocation(1, 2), source.locationOf(1))
        // Terminator offset belongs to its line.
        assertEquals(MarkdownSourceLocation(1, 3), source.locationOf(2))
        // Empty middle line.
        assertEquals(MarkdownSourceLocation(2, 1), source.locationOf(3))
        // Last line.
        assertEquals(MarkdownSourceLocation(3, 1), source.locationOf(4))
        assertEquals(MarkdownSourceLocation(3, 2), source.locationOf(5))
        // Past-end offsets clamp to just past the last line's content.
        assertEquals(MarkdownSourceLocation(3, 3), source.locationOf(6))
        assertEquals(MarkdownSourceLocation(3, 3), source.locationOf(100))
        // Negative offsets clamp to the document start.
        assertEquals(MarkdownSourceLocation(1, 1), source.locationOf(-5))
    }

    @Test
    fun `past-end offsets clamp to just past the last content even with a trailing terminator`() {
        // "a\n": last line's content ends at offset 1; the terminator is not content.
        val lf = MarkdownSource.of("a\n")
        assertEquals(MarkdownSourceLocation(1, 2), lf.locationOf(2))
        assertEquals(MarkdownSourceLocation(1, 2), lf.locationOf(100))

        val crLf = MarkdownSource.of("a\r\n")
        assertEquals(MarkdownSourceLocation(1, 2), crLf.locationOf(100))

        // Trailing empty line ("a\n\n" has lines "a" and ""): clamps to line 2 column 1.
        val trailingEmpty = MarkdownSource.of("a\n\n")
        assertEquals(MarkdownSourceLocation(2, 1), trailingEmpty.locationOf(100))
    }

    @Test
    fun `locator resolves range starts`() {
        val source = MarkdownSource.of("ab\ncd\n")

        assertEquals(
            MarkdownSourceLocation(2, 2),
            source.locator.locate(MarkdownSourceRange(4, 5)),
        )
    }

    @Test
    fun `crlf line content range maps back to original offsets including the cr`() {
        val input = "a\r\nb"
        val source = MarkdownSource.of(input)

        val lineWithTerminator = source.lineRange(0)
        assertEquals("a\r\n", source.slice(lineWithTerminator))
        val content = source.lineContentRange(0)
        assertEquals("a", source.slice(content))
        // The terminator (including the \r) is inside the full line range.
        assertEquals(MarkdownSourceRange(0, 3), lineWithTerminator)
    }
}
