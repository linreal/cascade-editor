package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCascadeMarkdownApi::class)
class MarkdownSourceTest {

    @Test
    fun `projection normalizes bom crlf and lone cr with original boundaries`() {
        val source = "\uFEFFa\r\nb\rc"
        val input = MarkdownParseInput.of(source)

        assertEquals("a\nb\nc", input.text)
        assertEquals(1, input.originalOffset(0))
        assertEquals(source.indexOf('b'), input.originalOffset(2))
        assertEquals(source.length, input.originalOffset(input.text.length))
    }

    @Test
    fun `slice remains character exact across normalized line endings`() {
        val source = "one\r\ntwo\nthree"
        val input = MarkdownParseInput.of(source)
        val range = MarkdownSourceRange(2, 9)

        assertEquals(source.substring(2, 9), input.slice(range))
        assertEquals("e\r\ntwo\n", input.slice(range))
    }

    @Test
    fun `locator covers bom empty lines and past end`() {
        val input = MarkdownParseInput.of("\uFEFFab\r\n\r\ncd")

        assertEquals(MarkdownSourceLocation(1, 1), input.locator.locate(0))
        assertEquals(MarkdownSourceLocation(1, 1), input.locator.locate(1))
        assertEquals(MarkdownSourceLocation(1, 3), input.locator.locate(3))
        assertEquals(MarkdownSourceLocation(2, 1), input.locator.locate(5))
        assertEquals(MarkdownSourceLocation(3, 3), input.locator.locate(100))
    }

    @Test
    fun `locator preserves trailing empty line semantics`() {
        val lf = MarkdownParseInput.of("a\n")
        val crLf = MarkdownParseInput.of("a\r\n")
        val trailingEmpty = MarkdownParseInput.of("a\n\n")

        assertEquals(MarkdownSourceLocation(1, 2), lf.locator.locate(100))
        assertEquals(MarkdownSourceLocation(1, 2), crLf.locator.locate(100))
        assertEquals(MarkdownSourceLocation(2, 1), trailingEmpty.locator.locate(100))
    }

    @Test
    fun `empty projection has a usable locator`() {
        val input = MarkdownParseInput.of("")

        assertEquals("", input.text)
        assertEquals(MarkdownSourceLocation(1, 1), input.locator.locate(0))
    }
}
