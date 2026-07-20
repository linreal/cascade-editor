package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.htmlserialization.HtmlEntityDecoder

/**
 * One matched entity reference, produced by [MarkdownEntities.matchAt].
 *
 * [replacement] is non-null for numeric references and for named references in
 * the documented supported subset. A named reference that is entity-shaped but
 * outside the subset yields a match with a null [replacement]: the reference
 * stays literal text and the call site reports it through
 * [MarkdownDecodeWarning.UnsupportedEntity].
 */
internal class MarkdownEntityMatch(
    /** Total matched length including the leading `&` and trailing `;`. */
    val length: Int,
    /** Decoded text, or null when the named reference is unsupported. */
    val replacement: String?,
    /** Reference name for named references (known or unknown); null for numeric. */
    val name: String?,
)

/**
 * Entity-reference decoding for the Markdown codec.
 *
 * This is deliberately **not** CommonMark-conformant entity decoding: CommonMark
 * requires the full HTML5 named-entity table (~2200 names), which is out of
 * scope for a bounded-field codec. What this
 * table implements instead:
 *
 * - **Numeric references decode fully**: `&#35;` (1–7 decimal digits) and
 *   `&#x1F600;` (1–6 hex digits), with the CommonMark invalid-codepoint rule —
 *   U+0000, values above U+10FFFF, and surrogate code points decode to U+FFFD.
 * - **Named references decode from the documented subset** in
 *   [supportedNames]: the base `htmlserialization` set plus common
 *   punctuation/symbol/arrow/fraction/currency names. Unknown names stay
 *   literal text and are reported through the [decode] callback (the call site
 *   wraps that into an Informational [MarkdownDecodeWarning.UnsupportedEntity]
 *   with the exact source range).
 * - Anything not entity-shaped (`&` without a terminated body, over-long
 *   bodies, malformed numerics) stays literal text with no warning, matching
 *   CommonMark's treatment of such text as ordinary characters.
 *
 * All functions are pure, single-pass, position-agnostic, and regex-free.
 * The no-decoding-inside-code-spans rule is enforced at the call site (the
 * inline parser), not here. [EntityDecode.None] disables decoding
 * entirely (handled in [decode]).
 *
 * The table is internal: consumers cannot extend it in v1.
 */
internal object MarkdownEntities {

    /**
     * The documented named-entity subset. Base names shared with the HTML
     * codec ([HtmlEntityDecoder.namedEntities]: amp, lt, gt, quot, apos, nbsp,
     * copy, reg, trade, ndash, mdash, hellip, lsquo, rsquo, ldquo, rdquo,
     * bull) plus the Markdown-side extensions below. This list is the
     * reviewable data referenced by the feature/deviation matrix.
     */
    val supportedNames: Map<String, String> = HtmlEntityDecoder.namedEntities + mapOf(
        // Arrows
        "rarr" to "→",
        "larr" to "←",
        "uarr" to "↑",
        "darr" to "↓",
        "harr" to "↔",
        // Math and signs
        "times" to "×",
        "divide" to "÷",
        "minus" to "−",
        "plusmn" to "±",
        "infin" to "∞",
        "ne" to "≠",
        "le" to "≤",
        "ge" to "≥",
        "asymp" to "≈",
        "deg" to "°",
        "micro" to "µ",
        "middot" to "·",
        "sect" to "§",
        "para" to "¶",
        // Quotes and daggers
        "laquo" to "«",
        "raquo" to "»",
        "sbquo" to "‚",
        "bdquo" to "„",
        "prime" to "′",
        "Prime" to "″",
        "dagger" to "†",
        "Dagger" to "‡",
        "permil" to "‰",
        // Currency
        "euro" to "€",
        "pound" to "£",
        "yen" to "¥",
        "cent" to "¢",
        // Fractions and superscripts
        "frac12" to "½",
        "frac14" to "¼",
        "frac34" to "¾",
        "sup1" to "¹",
        "sup2" to "²",
        "sup3" to "³",
        // Invisible-but-common whitespace
        "shy" to "\u00AD",
        "ensp" to "\u2002",
        "emsp" to "\u2003",
        "thinsp" to "\u2009",
    )

    /**
     * Match an entity reference starting at [index] in [text].
     *
     * [index] must point at a `&`. Returns null when the text at [index] is
     * not entity-shaped (no such construct — the `&` is literal, no warning).
     * Returns a match with a null replacement when the body is a well-formed
     * named reference outside [supportedNames].
     */
    fun matchAt(text: String, index: Int): MarkdownEntityMatch? {
        if (index >= text.length || text[index] != '&') return null
        var cursor = index + 1
        if (cursor >= text.length) return null

        if (text[cursor] == '#') {
            cursor++
            val hex = cursor < text.length && (text[cursor] == 'x' || text[cursor] == 'X')
            if (hex) cursor++
            val digitsStart = cursor
            val radix = if (hex) 16 else 10
            val maxDigits = if (hex) MAX_HEX_DIGITS else MAX_DECIMAL_DIGITS
            var value = 0
            while (cursor < text.length) {
                val digit = text[cursor].digitToIntOrNull(radix) ?: break
                // Clamp instead of overflowing; anything past the max code
                // point is invalid and becomes U+FFFD anyway.
                value = if (value > MAX_CODE_POINT) MAX_CODE_POINT + 1 else value * radix + digit
                cursor++
            }
            val digits = cursor - digitsStart
            if (digits < 1 || digits > maxDigits) return null
            if (cursor >= text.length || text[cursor] != ';') return null
            return MarkdownEntityMatch(
                length = cursor + 1 - index,
                replacement = codePointToStringOrReplacement(value),
                name = null,
            )
        }

        val nameStart = cursor
        while (cursor < text.length && cursor - nameStart <= MAX_NAME_LENGTH) {
            val ch = text[cursor]
            if (ch == ';') {
                if (cursor == nameStart) return null
                val name = text.substring(nameStart, cursor)
                return MarkdownEntityMatch(
                    length = cursor + 1 - index,
                    replacement = supportedNames[name],
                    name = name,
                )
            }
            if (!ch.isAsciiAlphanumeric()) return null
            cursor++
        }
        return null
    }

    /**
     * Decode every entity reference in [text].
     *
     * Under [EntityDecode.None] the input is returned unchanged and
     * [onUnknownName] is never invoked. Unknown named references stay literal
     * and are reported through [onUnknownName] with their exact half-open
     * offsets **within [text]** — the call site re-bases them into source
     * coordinates (the helper stays position-agnostic).
     */
    fun decode(
        text: String,
        policy: EntityDecode,
        onUnknownName: ((name: String, start: Int, endExclusive: Int) -> Unit)? = null,
    ): String {
        if (policy == EntityDecode.None) return text
        val firstAmpersand = text.indexOf('&')
        if (firstAmpersand < 0) return text

        val out = StringBuilder(text.length)
        out.append(text, 0, firstAmpersand)
        var index = firstAmpersand
        while (index < text.length) {
            val ch = text[index]
            if (ch != '&') {
                out.append(ch)
                index++
                continue
            }
            val match = matchAt(text, index)
            if (match == null) {
                out.append(ch)
                index++
                continue
            }
            if (match.replacement != null) {
                out.append(match.replacement)
            } else {
                out.append(text, index, index + match.length)
                if (match.name != null) {
                    onUnknownName?.invoke(match.name, index, index + match.length)
                }
            }
            index += match.length
        }
        return out.toString()
    }

    /**
     * CommonMark invalid-codepoint rule: U+0000, out-of-range values, and
     * surrogate code points decode to U+FFFD REPLACEMENT CHARACTER.
     */
    private fun codePointToStringOrReplacement(codePoint: Int): String {
        if (codePoint <= 0 || codePoint > MAX_CODE_POINT) return REPLACEMENT
        if (codePoint in HIGH_SURROGATE_START..LOW_SURROGATE_END) return REPLACEMENT
        if (codePoint <= BMP_MAX) return codePoint.toChar().toString()
        val shifted = codePoint - SUPPLEMENTARY_PLANE_START
        val high = HIGH_SURROGATE_START + (shifted shr 10)
        val low = LOW_SURROGATE_START + (shifted and LOW_SURROGATE_MASK)
        return high.toChar().toString() + low.toChar().toString()
    }

    private fun Char.isAsciiAlphanumeric(): Boolean =
        this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'

    private const val REPLACEMENT: String = "�"
    private const val MAX_DECIMAL_DIGITS: Int = 7
    private const val MAX_HEX_DIGITS: Int = 6
    private const val MAX_NAME_LENGTH: Int = 32
    private const val BMP_MAX: Int = 0xFFFF
    private const val MAX_CODE_POINT: Int = 0x10FFFF
    private const val SUPPLEMENTARY_PLANE_START: Int = 0x10000
    private const val HIGH_SURROGATE_START: Int = 0xD800
    private const val LOW_SURROGATE_START: Int = 0xDC00
    private const val LOW_SURROGATE_END: Int = 0xDFFF
    private const val LOW_SURROGATE_MASK: Int = 0x3FF
}
