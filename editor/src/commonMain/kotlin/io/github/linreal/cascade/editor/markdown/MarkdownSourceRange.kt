package io.github.linreal.cascade.editor.markdown

/**
 * Half-open range of UTF-16 code units in the original Markdown source `String`.
 *
 * `start` is inclusive, [endExclusive] is exclusive. Ranges always address the
 * original string handed to decode — never a normalized copy — so
 * `source.substring(range.start, range.endExclusive)` reproduces the covered
 * source characters exactly (including line terminators and a leading BOM when
 * the range spans them).
 *
 * Byte offsets, file encodings, and filesystem metadata are outside the codec:
 * "verbatim" always means UTF-16 source-slice equality.
 */
@ExperimentalCascadeMarkdownApi
public data class MarkdownSourceRange(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "start must be >= 0, was $start" }
        require(endExclusive >= start) {
            "endExclusive ($endExclusive) must be >= start ($start)"
        }
    }

    /** Number of UTF-16 code units covered by this range. */
    public val length: Int get() = endExclusive - start

    /** True when the range covers no characters. */
    public val isEmpty: Boolean get() = start == endExclusive
}

/**
 * 1-based line/column position in the original Markdown source.
 *
 * Columns count UTF-16 code units from the first content character of the
 * logical line. A leading U+FEFF BOM is not part of line 1's content: line 1
 * column 1 addresses the first character after the BOM.
 */
@ExperimentalCascadeMarkdownApi
public data class MarkdownSourceLocation(
    val line: Int,
    val column: Int,
) {
    init {
        require(line >= 1) { "line must be >= 1, was $line" }
        require(column >= 1) { "column must be >= 1, was $column" }
    }
}

/**
 * Resolves UTF-16 offsets and [MarkdownSourceRange]s in the original source to
 * 1-based [MarkdownSourceLocation]s.
 *
 * Instances are produced by the codec (attached to decode results) — consumers
 * never construct one directly. Offsets are clamped defensively: negative
 * offsets resolve to line 1 column 1, offsets at or past end of input resolve
 * to the position just past the last line's content.
 */
@ExperimentalCascadeMarkdownApi
public class MarkdownSourceLocator internal constructor(
    /** Offset where each logical line's content starts (terminators excluded). */
    private val lineContentStarts: IntArray,
    /** Offset where the last logical line's content ends (terminator excluded). */
    private val lastContentEnd: Int,
) {

    /** Resolve a single UTF-16 offset in the original source. */
    public fun locate(offset: Int): MarkdownSourceLocation {
        if (lineContentStarts.isEmpty()) return MarkdownSourceLocation(line = 1, column = 1)

        val clamped = offset.coerceIn(0, lastContentEnd)
        val lineIndex = lineIndexFor(clamped)
        val column = (clamped - lineContentStarts[lineIndex] + 1).coerceAtLeast(1)
        return MarkdownSourceLocation(line = lineIndex + 1, column = column)
    }

    /** Resolve the start position of [range] in the original source. */
    public fun locate(range: MarkdownSourceRange): MarkdownSourceLocation = locate(range.start)

    private fun lineIndexFor(offset: Int): Int {
        // Largest index whose content start is <= offset. Offsets before the first
        // content start (a leading BOM) belong to line 1.
        var low = 0
        var high = lineContentStarts.size - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lineContentStarts[mid] <= offset) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
