package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCascadeMarkdownApi::class)
class MarkdownEscapingTest {

    private val lineStart = MarkdownEscapeContext.LineStart
    private val midLine = MarkdownEscapeContext.MidLine

    @Test
    fun `hash is escaped at line start and not mid-line`() {
        assertEquals("""\# heading""", Markdown.escapeInline("# heading", lineStart))
        assertEquals("a # b", Markdown.escapeInline("a # b", midLine))
        assertEquals("a # b", Markdown.escapeInline("a # b", lineStart))
    }

    @Test
    fun `blockquote marker is escaped at line start only`() {
        assertEquals("""\> quote""", Markdown.escapeInline("> quote", lineStart))
        assertEquals("a > b", Markdown.escapeInline("a > b", lineStart))
    }

    @Test
    fun `setext underline chars are escaped at line start`() {
        assertEquals("""\==""", Markdown.escapeInline("==", lineStart))
        assertEquals("""\=x""", Markdown.escapeInline("=x", lineStart))
        assertEquals("a = b", Markdown.escapeInline("a = b", lineStart))
    }

    @Test
    fun `bullet markers are escaped at line start when they would form list syntax`() {
        assertEquals("""\- item""", Markdown.escapeInline("- item", lineStart))
        assertEquals("""\+ item""", Markdown.escapeInline("+ item", lineStart))
        assertEquals("""\---""", Markdown.escapeInline("---", lineStart))
        // A dash inside a word at line start is not list/break syntax.
        assertEquals("-x", Markdown.escapeInline("-x", lineStart))
        assertEquals("well-known", Markdown.escapeInline("well-known", lineStart))
    }

    @Test
    fun `ordered list marker cannot re-parse as a list item`() {
        val escaped = Markdown.escapeInline("1. x", lineStart)
        assertEquals("""1\. x""", escaped)

        assertEquals("""12\) y""", Markdown.escapeInline("12) y", lineStart))
        // Mid-line numbers stay untouched.
        assertEquals("see 1. below", Markdown.escapeInline("see 1. below", midLine))
        assertEquals("see 1. below", Markdown.escapeInline("see 1. below", lineStart))
        // Not at a true line start: no escape.
        assertEquals("1. x", Markdown.escapeInline("1. x", midLine))
        // Ten or more digits cannot be a list marker.
        assertEquals("1234567890. x", Markdown.escapeInline("1234567890. x", lineStart))
    }

    @Test
    fun `multi-line text treats each physical line start as line start`() {
        val context = MarkdownEscapeContext(
            atLineStart = true,
            containerPrefix = "> ",
        )
        val escaped = Markdown.escapeInline("first\n# second\n- third\n1. fourth", context)

        assertEquals("first\n\\# second\n\\- third\n1\\. fourth", escaped)
    }

    @Test
    fun `mid-line context still arms line-start rules after an embedded newline`() {
        val escaped = Markdown.escapeInline("tail\n# heading", midLine)

        assertEquals("tail\n\\# heading", escaped)
    }

    @Test
    fun `backtick backslash ampersand and angle bracket are always escaped`() {
        assertEquals("""a\`b""", Markdown.escapeInline("a`b", midLine))
        assertEquals("""a\\b""", Markdown.escapeInline("a\\b", midLine))
        assertEquals("""a\&b""", Markdown.escapeInline("a&b", midLine))
        assertEquals("""a\<b""", Markdown.escapeInline("a<b", midLine))
    }

    @Test
    fun `brackets are always escaped`() {
        assertEquals("""\[text\](url)""", Markdown.escapeInline("[text](url)", midLine))
    }

    @Test
    fun `emphasis delimiters escape when they could form delimiter runs`() {
        assertEquals("""a\*b""", Markdown.escapeInline("a*b", midLine))
        assertEquals("""\*emphasis\*""", Markdown.escapeInline("*emphasis*", midLine))
        assertEquals("""\*\*strong\*\*""", Markdown.escapeInline("**strong**", midLine))
        assertEquals("""\~\~struck\~\~""", Markdown.escapeInline("~~struck~~", midLine))
        // Whitespace on both sides: the run can neither open nor close.
        assertEquals("5 * 3", Markdown.escapeInline("5 * 3", midLine))
        assertEquals("a ~ b", Markdown.escapeInline("a ~ b", midLine))
    }

    @Test
    fun `intraword underscores are not escaped`() {
        assertEquals("foo_bar_baz", Markdown.escapeInline("foo_bar_baz", midLine))
        assertEquals("""\_lead""", Markdown.escapeInline("_lead", midLine))
        assertEquals("""trail\_""", Markdown.escapeInline("trail_", midLine))
        assertEquals("""\_emph\_""", Markdown.escapeInline("_emph_", midLine))
    }

    @Test
    fun `line-start star bullet is escaped even when flanking rules would skip it`() {
        assertEquals("""\* item""", Markdown.escapeInline("* item", lineStart))
    }

    @Test
    fun `line-start tilde run is escaped so it cannot open a fence`() {
        val escaped = Markdown.escapeInline("~~~", lineStart)
        assertTrue(escaped.startsWith("""\~"""), "expected leading tilde escaped, got $escaped")
    }

    @Test
    fun `code span delimiters widen past internal backtick runs and pad`() {
        val delimiters = Markdown.codeSpanDelimiters("a`b")

        assertEquals("``", delimiters.fence)
        assertTrue(delimiters.padded)
        assertEquals("`` ", delimiters.open)
        assertEquals(" ``", delimiters.close)
    }

    @Test
    fun `code span delimiters for plain content are a single unpadded backtick`() {
        val delimiters = Markdown.codeSpanDelimiters("plain code")

        assertEquals("`", delimiters.fence)
        assertFalse(delimiters.padded)
        assertEquals("`", delimiters.open)
        assertEquals("`", delimiters.close)
    }

    @Test
    fun `code span content with edge spaces is padded but all-space content is not`() {
        assertTrue(Markdown.codeSpanDelimiters(" x ").padded)
        assertTrue(Markdown.codeSpanDelimiters(" x").padded)
        assertFalse(Markdown.codeSpanDelimiters("   ").padded)
        assertFalse(Markdown.codeSpanDelimiters("").padded)
    }

    @Test
    fun `code span fence is longer than the longest internal run`() {
        assertEquals("````", Markdown.codeSpanDelimiters("a ``` b").fence)
        assertEquals("``", Markdown.codeSpanDelimiters("`").fence)
    }

    @Test
    fun `link destination with parens or spaces uses the angle form`() {
        assertEquals("<http://x/(1)>", Markdown.escapeLinkDestination("http://x/(1)"))
        assertEquals("<a b>", Markdown.escapeLinkDestination("a b"))
        assertEquals("<>", Markdown.escapeLinkDestination(""))
    }

    @Test
    fun `plain link destination stays bare`() {
        assertEquals("../guide.md", Markdown.escapeLinkDestination("../guide.md"))
        assertEquals("https://example.com/path?q=1#frag", Markdown.escapeLinkDestination("https://example.com/path?q=1#frag"))
        assertEquals("mailto:user@example.com", Markdown.escapeLinkDestination("mailto:user@example.com"))
    }

    @Test
    fun `angle form escapes angle brackets and backslashes`() {
        assertEquals("""<a\<b\>c d>""", Markdown.escapeLinkDestination("a<b>c d"))
        assertEquals("""<a\\b c>""", Markdown.escapeLinkDestination("""a\b c"""))
    }

    @Test
    fun `link text escapes brackets and inline syntax`() {
        assertEquals("""\[x\]""", Markdown.escapeLinkText("[x]"))
        assertEquals("""a \`code\` b""", Markdown.escapeLinkText("a `code` b"))
        assertEquals("""\*em\*""", Markdown.escapeLinkText("*em*"))
    }

    @Test
    fun `escaped hostile fixtures contain no unescaped structural triggers at line starts`() {
        for (hostile in MarkdownHostileTextFixtures.strings) {
            val escaped = Markdown.escapeInline(hostile, lineStart)
            for (line in escaped.split('\n', '\r')) {
                // Line-start syntax sees through up to 3 leading spaces.
                val content = line.trimStart(' ')
                if (content.isEmpty() || line.length - content.length >= 4) continue
                val first = content.first()
                assertFalse(
                    first == '#' || first == '>' || first == '`' || first == '~' || first == '=',
                    "line-start trigger '$first' left unescaped in: $line (from: $hostile)",
                )
                assertFalse(
                    (first == '-' || first == '+' || first == '*') &&
                        (content.length == 1 || content[1] == ' ' || content[1] == '\t' || content[1] == first),
                    "unescaped list/break marker at line start in: $line (from: $hostile)",
                )
                val digits = content.takeWhile { it in '0'..'9' }
                assertFalse(
                    digits.length in 1..9 && content.length > digits.length &&
                        (content[digits.length] == '.' || content[digits.length] == ')') &&
                        (content.length == digits.length + 1 || content[digits.length + 1] == ' '),
                    "unescaped ordered-list marker at line start in: $line (from: $hostile)",
                )
                for ((i, c) in content.withIndex()) {
                    if (c != '|') continue
                    assertTrue(
                        i > 0 && content[i - 1] == '\\',
                        "unescaped pipe in: $line (from: $hostile)",
                    )
                }
            }
        }
    }

    @Test
    fun `escaping is idempotent-safe on empty input`() {
        assertEquals("", Markdown.escapeInline("", lineStart))
    }

    @Test
    fun `block markers behind one to three leading spaces are still escaped`() {
        assertEquals(" \\# b", Markdown.escapeInline(" # b", lineStart))
        assertEquals("   \\- x", Markdown.escapeInline("   - x", lineStart))
        assertEquals("  1\\. x", Markdown.escapeInline("  1. x", lineStart))
        assertEquals(" \\> q", Markdown.escapeInline(" > q", lineStart))
        assertEquals("a\n \\# b", Markdown.escapeInline("a\n # b", midLine))
    }

    @Test
    fun `markers behind four or more leading spaces are indented-code territory and stay untouched`() {
        assertEquals("    # b", Markdown.escapeInline("    # b", lineStart))
        assertEquals("    - x", Markdown.escapeInline("    - x", lineStart))
    }

    @Test
    fun `carriage-return line endings re-arm line-start rules`() {
        assertEquals("a\r\\# b", Markdown.escapeInline("a\r# b", midLine))
        assertEquals("a\r\n\\- x", Markdown.escapeInline("a\r\n- x", midLine))
        // '\r' also terminates a bullet/ordered marker.
        assertEquals("\\-\rx", Markdown.escapeInline("-\rx", lineStart))
        assertEquals("1\\.\rx", Markdown.escapeInline("1.\rx", lineStart))
    }

    @Test
    fun `pipe and dollar are always escaped to guard recognizer syntax`() {
        assertEquals("a\\|b", Markdown.escapeInline("a|b", midLine))
        assertEquals("\\| a \\| b \\|", Markdown.escapeInline("| a | b |", lineStart))
        assertEquals("\\$5 and \\$6", Markdown.escapeInline("$5 and $6", midLine))
        assertEquals("\\$\\$", Markdown.escapeInline("$$", lineStart))
    }

    @Test
    fun `code span delimiters flag unrepresentable content for fallback`() {
        assertTrue(Markdown.codeSpanDelimiters("").requiresFallback)
        assertTrue(Markdown.codeSpanDelimiters("a\nb").requiresFallback)
        assertTrue(Markdown.codeSpanDelimiters("a\rb").requiresFallback)
        assertFalse(Markdown.codeSpanDelimiters("a`b").requiresFallback)
        assertFalse(Markdown.codeSpanDelimiters("plain").requiresFallback)
    }
}
