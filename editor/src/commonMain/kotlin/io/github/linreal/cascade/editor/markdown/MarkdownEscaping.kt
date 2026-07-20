package io.github.linreal.cascade.editor.markdown

/**
 * Surrounding-syntax context for [Markdown.escapeInline].
 *
 * Markdown escaping is position-dependent, so a lone `atLineStart` Boolean is
 * insufficient for multi-line text or text nested in list/quote prefixes:
 *
 * - [atLineStart] describes only the *first* character of the text being
 *   escaped. After every `\n` inside the text a new physical output line
 *   begins, and line-start rules apply again to its first character — block
 *   constructs can start after container prefixes (`> `, list continuation
 *   indentation), so a container prefix does not neutralize line-start syntax.
 * - [containerPrefix] is the prefix the encoder prepends to every subsequent
 *   physical output line (e.g. `"> "` inside a quote, `"  "` for list-item
 *   continuations). [Markdown.escapeInline] does not insert it — prefixing is
 *   the emission engine's job — but it is carried so escaping decisions stay
 *   reviewable alongside the emission context and future prefix-sensitive
 *   rules have the data they need.
 * - [kind] selects the destination/label/plain-text context.
 *
 * [containerPrefix] and [kind] are currently informational: no escaping rule
 * consults them (brackets are escaped unconditionally, so [kind] adds nothing
 * yet). They are part of the contract so prefix- or kind-sensitive rules can
 * be added without a signature change; do not rely on kind-specific behavior.
 */
@ExperimentalCascadeMarkdownApi
public data class MarkdownEscapeContext(
    /** Whether the first character of the text lands at a physical line start. */
    val atLineStart: Boolean,
    /** Prefix the encoder applies to every following physical output line. */
    val containerPrefix: String = "",
    /** Text position kind: plain inline text, link text, or link label. */
    val kind: MarkdownEscapeTextKind = MarkdownEscapeTextKind.Plain,
) {
    public companion object {
        /** Plain text starting at a physical line start with no container prefix. */
        public val LineStart: MarkdownEscapeContext = MarkdownEscapeContext(atLineStart = true)

        /** Plain text continuing mid-line (e.g. after an emphasis opener). */
        public val MidLine: MarkdownEscapeContext = MarkdownEscapeContext(atLineStart = false)
    }
}

/** Position kind carried by [MarkdownEscapeContext]. */
@ExperimentalCascadeMarkdownApi
public enum class MarkdownEscapeTextKind {
    /** Ordinary inline text. */
    Plain,

    /** Text between `[` and `]` of a link. */
    LinkText,

    /** A reference-link label. */
    LinkLabel,
}

/**
 * Delimiter decision for encoding one inline code span, computed by
 * [Markdown.codeSpanDelimiters].
 *
 * The [fence] is a backtick run strictly longer than every backtick run inside
 * the content. When [padded] is true the content must be emitted with one
 * space of padding inside each fence (`open` and `close` include it), so that
 * edge backticks cannot merge with the fence and CommonMark's
 * strip-one-space rule reproduces the original content on decode.
 */
@ExperimentalCascadeMarkdownApi
public data class MarkdownCodeSpanDelimiters(
    val fence: String,
    val padded: Boolean,
    /**
     * True when the content cannot round-trip as an inline code span at all:
     * empty content (an empty code span is unrepresentable in CommonMark) or
     * content containing line terminators (CommonMark normalizes internal line
     * endings to a space on decode). Callers must route such content through
     * the HTML bridge or a drop-with-warning path instead of emitting these
     * delimiters blindly.
     */
    val requiresFallback: Boolean = false,
) {
    /** Opening delimiter including padding. */
    public val open: String get() = if (padded) "$fence " else fence

    /** Closing delimiter including padding. */
    public val close: String get() = if (padded) " $fence" else fence
}

/**
 * Positional Markdown escaping helpers — the analog of `Html.escapeText` /
 * `Html.escapeAttr`, except Markdown escaping depends on *position*: `#` is
 * only special at line start, `-`/`1.` only where they would form list or
 * break syntax, `*`/`_` only where they would form delimiter runs, `` ` ``
 * always, `[`/`]` around link syntax.
 *
 * Default encoders and consumer-authored encoders must go through these
 * helpers rather than hand-rolling `replace(...)` chains. The helpers may
 * over-escape (every escaped character re-decodes to itself); they never
 * under-escape — the round-trip property suite is the honesty check.
 *
 * All functions are pure, single-pass, and regex-free.
 */
@ExperimentalCascadeMarkdownApi
public object Markdown {

    /**
     * Escape [text] for emission as inline Markdown content in [context].
     *
     * Line-start rules apply to the first character when
     * [MarkdownEscapeContext.atLineStart] is true and to the first character
     * after every line terminator in [text] (`\n`, `\r\n`, and lone `\r` all
     * begin a new physical output line, matching the decoder's line map;
     * container prefixes do not neutralize line-start syntax). CommonMark
     * allows up to 3 leading spaces before block syntax, so line-start rules
     * see through 1–3 spaces of indentation; 4+ spaces is indented-code
     * territory and outside the escaper's job (a value-based support-set
     * exclusion).
     */
    public fun escapeInline(text: String, context: MarkdownEscapeContext): String {
        if (text.isEmpty()) return text

        val out = StringBuilder(text.length + 8)
        // Index in text where the current physical line begins.
        var lineStart = 0
        // Whether the current physical line's head is a true line start. The
        // first line inherits context.atLineStart; every line after a
        // terminator is.
        var lineHeadIsLineStart = context.atLineStart
        // First non-space index of the current line, looking through at most
        // 3 leading spaces — where line-start block syntax can begin.
        var lineContentStart = contentStartOf(text, 0)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '\n' || ch == '\r') {
                out.append(ch)
                index++
                if (ch == '\r' && index < text.length && text[index] == '\n') {
                    out.append('\n')
                    index++
                }
                lineStart = index
                lineHeadIsLineStart = true
                lineContentStart = contentStartOf(text, index)
                continue
            }

            val atLineStart = lineHeadIsLineStart && index == lineContentStart
            val escape = when (ch) {
                // Backslash could escape the next character; backtick opens
                // code spans (and line-start runs open fences); & could start
                // an entity; < opens autolinks and inline HTML anywhere.
                '\\', '`', '&', '<' -> true

                // Link syntax binds anywhere in inline text.
                '[', ']' -> true

                // Pipe tables and math blocks/spans are claimed by the default
                // profile's recognizers; unescaped '|'
                // or '$' in ordinary text could re-decode as an opaque
                // preserved block. Over-escaping is permitted and cheap.
                '|', '$' -> true

                // Emphasis-style delimiters: escape unless the run can neither
                // open nor close per flanking rules. Line-start '*' can also be
                // a bullet or thematic break; line-start '~' runs can open a
                // tilde fence.
                '*' -> canFormDelimiterRun(text, index) ||
                    (atLineStart && isListOrBreakMarker(text, index))

                '~' -> canFormDelimiterRun(text, index) || atLineStart
                '_' -> canFormDelimiterRun(text, index) && !isIntraword(text, index)

                // Line-start block syntax: ATX headings, blockquotes, setext
                // underlines, bullets, and thematic breaks.
                '#', '>', '=' -> atLineStart
                '-', '+' -> atLineStart && isListOrBreakMarker(text, index)

                // Ordered-list marker: escape the '.'/')' that closes a
                // digit run at the line's content start so "1. x" (with up to
                // 3 leading spaces) cannot re-parse as a list.
                '.', ')' -> lineHeadIsLineStart &&
                    isOrderedListMarkerClose(text, lineContentStart, index)

                else -> false
            }

            if (escape) out.append('\\')
            out.append(ch)
            index++
        }
        return out.toString()
    }

    /**
     * Compute the backtick fence and padding needed to emit [text] as an
     * inline code span.
     *
     * The fence is widened past the longest backtick run in [text]. Padding is
     * chosen conservatively (over-padding is safe): it is applied whenever the
     * content contains a backtick or starts/ends with a space, except when the
     * content is empty or all spaces — CommonMark's strip-one-space rule only
     * fires when content begins *and* ends with a space and is not all spaces,
     * so padding all-space content would corrupt it.
     */
    public fun codeSpanDelimiters(text: String): MarkdownCodeSpanDelimiters {
        var longestRun = 0
        var currentRun = 0
        var hasBacktick = false
        var allSpaces = true
        var hasLineBreak = false
        for (ch in text) {
            if (ch == '`') {
                hasBacktick = true
                currentRun++
                if (currentRun > longestRun) longestRun = currentRun
            } else {
                currentRun = 0
            }
            if (ch != ' ') allSpaces = false
            if (ch == '\n' || ch == '\r') hasLineBreak = true
        }

        val fence = "`".repeat(longestRun + 1)
        val padded = text.isNotEmpty() &&
            !allSpaces &&
            (hasBacktick || text.first() == ' ' || text.last() == ' ')
        return MarkdownCodeSpanDelimiters(
            fence = fence,
            padded = padded,
            requiresFallback = text.isEmpty() || hasLineBreak,
        )
    }

    /**
     * Escape [target] for emission as a link destination.
     *
     * Chooses the `<angle>` form whenever the bare form would be invalid or
     * ambiguous: empty targets, whitespace or control characters, parentheses,
     * angle brackets, or backslashes. Inside the angle form, `<`, `>`, and
     * `\` are backslash-escaped; line terminators (invalid even in the angle
     * form) are percent-encoded.
     */
    public fun escapeLinkDestination(target: String): String {
        var needsAngle = target.isEmpty()
        if (!needsAngle) {
            for (ch in target) {
                if (ch == '(' || ch == ')' || ch == '<' || ch == '>' || ch == '\\' ||
                    ch == ' ' || ch.code < 0x20 || ch.code == 0x7F
                ) {
                    needsAngle = true
                    break
                }
            }
        }
        if (!needsAngle) return target

        val out = StringBuilder(target.length + 8)
        out.append('<')
        for (ch in target) {
            when (ch) {
                '<', '>', '\\' -> out.append('\\').append(ch)
                '\n' -> out.append("%0A")
                '\r' -> out.append("%0D")
                else -> out.append(ch)
            }
        }
        out.append('>')
        return out.toString()
    }

    /**
     * Escape [text] for emission between `[` and `]` of a link.
     *
     * Equivalent to [escapeInline] mid-line with the link-text kind: brackets
     * are always escaped so nested bracket runs cannot terminate or restart
     * the link syntax.
     */
    public fun escapeLinkText(text: String): String =
        escapeInline(
            text,
            MarkdownEscapeContext(
                atLineStart = false,
                kind = MarkdownEscapeTextKind.LinkText,
            ),
        )

    /**
     * True when the delimiter-capable character at [index] could open or close
     * a delimiter run: escaped unless it is surrounded by whitespace on both
     * sides (such a run is neither left- nor right-flanking and can never
     * bind). Text boundaries count as unknown neighbors and force escaping —
     * the emitted text may sit adjacent to other emitted markers. Over-escaping
     * is deliberate; deciding true pairing requires the full emphasis
     * algorithm.
     */
    private fun canFormDelimiterRun(text: String, index: Int): Boolean {
        val marker = text[index]
        var runStart = index
        while (runStart > 0 && text[runStart - 1] == marker) runStart--
        var runEnd = index
        while (runEnd + 1 < text.length && text[runEnd + 1] == marker) runEnd++

        val beforeIsWhitespace = runStart > 0 && text[runStart - 1].isMarkdownWhitespace()
        val afterIsWhitespace = runEnd < text.length - 1 && text[runEnd + 1].isMarkdownWhitespace()
        return !(beforeIsWhitespace && afterIsWhitespace)
    }

    /** True when `_` at [index] sits between two alphanumerics (intraword). */
    private fun isIntraword(text: String, index: Int): Boolean {
        var runStart = index
        while (runStart > 0 && text[runStart - 1] == '_') runStart--
        var runEnd = index
        while (runEnd + 1 < text.length && text[runEnd + 1] == '_') runEnd++

        val before = if (runStart == 0) null else text[runStart - 1]
        val after = if (runEnd == text.length - 1) null else text[runEnd + 1]
        return before != null && after != null &&
            before.isLetterOrDigit() && after.isLetterOrDigit()
    }

    /**
     * True when `-`/`+`/`*` at a line start would form a bullet marker or a
     * thematic-break/setext line: followed by space/tab, end of line, or more
     * of the same character.
     */
    private fun isListOrBreakMarker(text: String, index: Int): Boolean {
        val marker = text[index]
        val next = if (index + 1 < text.length) text[index + 1] else null
        return next == null || next == ' ' || next == '\t' ||
            next == '\n' || next == '\r' || next == marker
    }

    /**
     * True when `.`/`)` at [index] closes an ordered-list marker: the current
     * physical line's content (after at most 3 leading spaces) up to [index] is
     * exactly a 1–9 digit run and the marker is followed by space, tab, or end
     * of line.
     */
    private fun isOrderedListMarkerClose(text: String, contentStart: Int, index: Int): Boolean {
        val digits = index - contentStart
        if (digits < 1 || digits > 9) return false
        for (cursor in contentStart until index) {
            if (!text[cursor].isAsciiDigit()) return false
        }
        val next = if (index + 1 < text.length) text[index + 1] else null
        return next == null || next == ' ' || next == '\t' || next == '\n' || next == '\r'
    }

    /**
     * First index at or after [lineStart] that is not one of up to 3 leading
     * spaces — the position where line-start block syntax can begin. A line
     * indented 4+ spaces has its content start pinned at the fourth space, so
     * no later character can qualify as line-start.
     */
    private fun contentStartOf(text: String, lineStart: Int): Int {
        var cursor = lineStart
        while (cursor < text.length && cursor - lineStart < 3 && text[cursor] == ' ') cursor++
        return cursor
    }

    private fun Char.isMarkdownWhitespace(): Boolean =
        this == ' ' || this == '\t' || this == '\n' || this == '\r'

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
