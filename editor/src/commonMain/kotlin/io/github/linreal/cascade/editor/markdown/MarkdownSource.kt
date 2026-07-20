package io.github.linreal.cascade.editor.markdown

/**
 * Line-ending pattern observed in a Markdown source string.
 *
 * Recorded during the single source scan so encode-side comparisons
 * (`analyze.wouldRewriteSource`) and canonical line-ending selection can reason
 * about the original input without re-scanning it.
 */
internal enum class MarkdownLineEndings {
    /** Input contains no line terminators at all. */
    None,

    /** Every terminator is `\n`. */
    Lf,

    /** Every terminator is `\r\n`. */
    CrLf,

    /** Every terminator is a lone `\r`. */
    Cr,

    /** More than one terminator kind appears. */
    Mixed,
}

/**
 * Source model for one Markdown decode: logical line map over the original
 * `String` plus input-trivia facts (BOM, line-ending pattern, final newline).
 *
 * Parsing operates on normalized logical lines ([lineContent] excludes
 * terminators and the leading BOM), while every line and every range maps back
 * to original UTF-16 offsets so warnings and verbatim preservation slices are
 * character-exact. The node/range map built on top of
 * this stays internal; only [MarkdownSourceRange] and [MarkdownSourceLocator]
 * are public.
 *
 * Line splitting recognizes `\n`, `\r\n`, and lone `\r` in one pass — no regex.
 */
internal class MarkdownSource private constructor(
    /** The original, unmodified input string. */
    val text: String,
    /** True when the input starts with a U+FEFF byte-order mark. */
    val hasBom: Boolean,
    /** Observed line-ending pattern. */
    val lineEndings: MarkdownLineEndings,
    /** True when the last logical line is terminated by a line ending. */
    val endsWithNewline: Boolean,
    /** Offset where each logical line's content starts (after BOM/terminators). */
    private val contentStarts: IntArray,
    /** Offset where each logical line's content ends (terminator excluded). */
    private val contentEnds: IntArray,
    /** Offset where each logical line ends including its terminator. */
    private val lineEnds: IntArray,
) {

    /** Number of logical lines. Empty input has zero lines. */
    val lineCount: Int get() = contentStarts.size

    /** Location lookup helper shared with public results. */
    val locator: MarkdownSourceLocator = MarkdownSourceLocator(
        lineContentStarts = contentStarts,
        lastContentEnd = contentEnds.lastOrNull() ?: 0,
    )

    /** Normalized content of line [index] (0-based), excluding the terminator. */
    fun lineContent(index: Int): String =
        text.substring(contentStarts[index], contentEnds[index])

    /** Source range of line [index]'s content, excluding the terminator. */
    fun lineContentRange(index: Int): MarkdownSourceRange =
        MarkdownSourceRange(start = contentStarts[index], endExclusive = contentEnds[index])

    /** Source range of line [index] including its terminator (if any). */
    fun lineRange(index: Int): MarkdownSourceRange =
        MarkdownSourceRange(start = contentStarts[index], endExclusive = lineEnds[index])

    /**
     * Character-exact slice of the original string. The basis for verbatim
     * preservation: the returned string equals the original substring
     * character-for-character, terminators and all.
     */
    fun slice(range: MarkdownSourceRange): String {
        val start = range.start.coerceIn(0, text.length)
        val end = range.endExclusive.coerceIn(start, text.length)
        return text.substring(start, end)
    }

    /** Resolve an original UTF-16 offset to a 1-based line/column location. */
    fun locationOf(offset: Int): MarkdownSourceLocation = locator.locate(offset)

    companion object {

        /** Scan [input] once and build the source model. Never throws on any input. */
        fun of(input: String): MarkdownSource {
            val hasBom = input.isNotEmpty() && input[0] == '\uFEFF'
            val contentStarts = ArrayList<Int>(16)
            val contentEnds = ArrayList<Int>(16)
            val lineEnds = ArrayList<Int>(16)

            var lfCount = 0
            var crLfCount = 0
            var crCount = 0
            var endsWithNewline = false

            var index = if (hasBom) 1 else 0
            var lineStart = index
            val length = input.length
            while (index < length) {
                val ch = input[index]
                if (ch != '\n' && ch != '\r') {
                    index++
                    continue
                }

                val contentEnd = index
                val terminatorEnd: Int
                if (ch == '\r') {
                    if (index + 1 < length && input[index + 1] == '\n') {
                        crLfCount++
                        terminatorEnd = index + 2
                    } else {
                        crCount++
                        terminatorEnd = index + 1
                    }
                } else {
                    lfCount++
                    terminatorEnd = index + 1
                }

                contentStarts.add(lineStart)
                contentEnds.add(contentEnd)
                lineEnds.add(terminatorEnd)
                index = terminatorEnd
                lineStart = terminatorEnd
            }

            if (lineStart < length) {
                // Trailing line without a terminator.
                contentStarts.add(lineStart)
                contentEnds.add(length)
                lineEnds.add(length)
            } else {
                endsWithNewline = contentStarts.isNotEmpty()
            }

            val kinds = (if (lfCount > 0) 1 else 0) + (if (crLfCount > 0) 1 else 0) + (if (crCount > 0) 1 else 0)
            val lineEndings = when {
                kinds == 0 -> MarkdownLineEndings.None
                kinds > 1 -> MarkdownLineEndings.Mixed
                lfCount > 0 -> MarkdownLineEndings.Lf
                crLfCount > 0 -> MarkdownLineEndings.CrLf
                else -> MarkdownLineEndings.Cr
            }

            return MarkdownSource(
                text = input,
                hasBom = hasBom,
                lineEndings = lineEndings,
                endsWithNewline = endsWithNewline,
                contentStarts = contentStarts.toIntArray(),
                contentEnds = contentEnds.toIntArray(),
                lineEnds = lineEnds.toIntArray(),
            )
        }
    }
}
