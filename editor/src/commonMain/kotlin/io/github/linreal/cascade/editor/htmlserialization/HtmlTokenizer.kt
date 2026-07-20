package io.github.linreal.cascade.editor.htmlserialization

internal object HtmlTokenizer {

    /**
     * Tokenize [rawSource] without throwing for malformed tags or truncated input.
     *
     * Entity decoding is applied only to text and attribute values; all source ranges
     * remain offsets into [rawSource].
     */
    internal fun tokenize(
        rawSource: String,
        entityDecode: EntityDecode = EntityDecode.Standard,
    ): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        var index = 0
        while (index < rawSource.length) {
            val tagStart = rawSource.indexOf('<', startIndex = index)
            if (tagStart < 0) {
                addTextToken(tokens, rawSource, index, rawSource.length, entityDecode)
                index = rawSource.length
                continue
            }

            if (tagStart > index) {
                addTextToken(tokens, rawSource, index, tagStart, entityDecode)
            }

            index = when {
                startsWithAt(rawSource, "<!--", tagStart) ->
                    skipComment(rawSource, tagStart)

                isClosingTagStart(rawSource, tagStart) ->
                    parseCloseTag(rawSource, tagStart, tokens)

                isOpeningTagStart(rawSource, tagStart) ->
                    parseOpenTag(rawSource, tagStart, entityDecode, tokens)

                isMarkupDeclarationStart(rawSource, tagStart) ->
                    skipMarkupDeclaration(rawSource, tagStart)

                else -> {
                    addTextToken(tokens, rawSource, tagStart, tagStart + 1, entityDecode)
                    tagStart + 1
                }
            }
        }

        return tokens
    }

    private fun addTextToken(
        tokens: MutableList<HtmlToken>,
        rawSource: String,
        sourceStart: Int,
        sourceEndExclusive: Int,
        entityDecode: EntityDecode,
    ) {
        if (sourceStart >= sourceEndExclusive) return
        val rawText = rawSource.substring(sourceStart, sourceEndExclusive)
        val text = decodeIfNeeded(rawText, entityDecode)
        tokens += HtmlToken.Text(
            text = text,
            sourceStart = sourceStart,
            sourceEndExclusive = sourceEndExclusive,
        )
    }

    private fun skipComment(
        rawSource: String,
        sourceStart: Int,
    ): Int {
        val textStart = sourceStart + 4
        val closingStart = rawSource.indexOf("-->", startIndex = textStart)
        return if (closingStart >= 0) closingStart + 3 else rawSource.length
    }

    private fun skipMarkupDeclaration(
        rawSource: String,
        sourceStart: Int,
    ): Int {
        val closingIndex = rawSource.indexOf('>', startIndex = sourceStart + 2)
        return if (closingIndex >= 0) closingIndex + 1 else rawSource.length
    }

    private fun parseCloseTag(
        rawSource: String,
        sourceStart: Int,
        tokens: MutableList<HtmlToken>,
    ): Int {
        var index = sourceStart + 2
        val nameStart = index
        while (index < rawSource.length && isNameChar(rawSource[index])) {
            index++
        }
        val name = rawSource.substring(nameStart, index).lowercase()
        while (index < rawSource.length && rawSource[index] != '>') {
            index++
        }
        val sourceEndExclusive = if (index < rawSource.length) index + 1 else rawSource.length
        tokens += HtmlToken.CloseTag(
            name = name,
            sourceStart = sourceStart,
            sourceEndExclusive = sourceEndExclusive,
        )
        return sourceEndExclusive
    }

    private fun parseOpenTag(
        rawSource: String,
        sourceStart: Int,
        entityDecode: EntityDecode,
        tokens: MutableList<HtmlToken>,
    ): Int {
        var index = sourceStart + 1
        val nameStart = index
        while (index < rawSource.length && isNameChar(rawSource[index])) {
            index++
        }
        val name = rawSource.substring(nameStart, index).lowercase()
        val attributes = mutableListOf<HtmlAttribute>()
        var selfClosing = false

        while (index < rawSource.length) {
            index = skipWhitespace(rawSource, index)
            if (index >= rawSource.length) break

            when {
                rawSource[index] == '>' -> {
                    index++
                    break
                }

                rawSource[index] == '/' && index + 1 < rawSource.length && rawSource[index + 1] == '>' -> {
                    selfClosing = true
                    index += 2
                    break
                }

                rawSource[index] == '/' && index + 1 == rawSource.length -> {
                    selfClosing = true
                    index++
                    break
                }

                else -> {
                    val parsed = parseAttribute(rawSource, index, entityDecode)
                    if (parsed == null) {
                        index++
                    } else {
                        attributes += parsed.attribute
                        index = parsed.nextIndex
                    }
                }
            }
        }

        tokens += HtmlToken.OpenTag(
            name = name,
            attributes = attributes,
            selfClosing = selfClosing,
            sourceStart = sourceStart,
            sourceEndExclusive = index,
        )
        return index
    }

    private fun parseAttribute(
        rawSource: String,
        sourceStart: Int,
        entityDecode: EntityDecode,
    ): ParsedAttribute? {
        var index = sourceStart
        while (index < rawSource.length && isAttributeNameChar(rawSource[index])) {
            index++
        }
        if (index == sourceStart) return null

        val name = rawSource.substring(sourceStart, index).lowercase()
        index = skipWhitespace(rawSource, index)
        if (index >= rawSource.length || rawSource[index] != '=') {
            return ParsedAttribute(
                attribute = HtmlAttribute(
                    name = name,
                    value = null,
                    sourceStart = sourceStart,
                    sourceEndExclusive = index,
                ),
                nextIndex = index,
            )
        }

        index++
        index = skipWhitespace(rawSource, index)
        if (index >= rawSource.length) {
            return ParsedAttribute(
                attribute = HtmlAttribute(
                    name = name,
                    value = "",
                    sourceStart = sourceStart,
                    sourceEndExclusive = index,
                ),
                nextIndex = index,
            )
        }

        val value: String
        if (rawSource[index] == '"' || rawSource[index] == '\'') {
            val quote = rawSource[index]
            index++
            val valueStart = index
            while (index < rawSource.length && rawSource[index] != quote) {
                index++
            }
            value = decodeIfNeeded(rawSource.substring(valueStart, index), entityDecode)
            if (index < rawSource.length) index++
        } else {
            val valueStart = index
            while (
                index < rawSource.length &&
                !rawSource[index].isWhitespace() &&
                rawSource[index] != '>' &&
                !(rawSource[index] == '/' && index + 1 < rawSource.length && rawSource[index + 1] == '>')
            ) {
                index++
            }
            value = decodeIfNeeded(rawSource.substring(valueStart, index), entityDecode)
        }

        return ParsedAttribute(
            attribute = HtmlAttribute(
                name = name,
                value = value,
                sourceStart = sourceStart,
                sourceEndExclusive = index,
            ),
            nextIndex = index,
        )
    }

    private fun decodeIfNeeded(rawText: String, entityDecode: EntityDecode): String =
        when (entityDecode) {
            EntityDecode.None -> rawText
            EntityDecode.Standard -> HtmlEntityDecoder.decode(rawText)
        }

    private fun isClosingTagStart(rawSource: String, index: Int): Boolean =
        index + 2 < rawSource.length &&
            rawSource[index + 1] == '/' &&
            isNameStart(rawSource[index + 2])

    private fun isOpeningTagStart(rawSource: String, index: Int): Boolean =
        index + 1 < rawSource.length && isNameStart(rawSource[index + 1])

    private fun isMarkupDeclarationStart(rawSource: String, index: Int): Boolean =
        index + 1 < rawSource.length && rawSource[index + 1] == '!'

    private fun startsWithAt(source: String, prefix: String, index: Int): Boolean {
        if (index + prefix.length > source.length) return false
        for (offset in prefix.indices) {
            if (source[index + offset] != prefix[offset]) return false
        }
        return true
    }

    private fun skipWhitespace(source: String, index: Int): Int {
        var current = index
        while (current < source.length && source[current].isWhitespace()) {
            current++
        }
        return current
    }

    private fun isNameStart(char: Char): Boolean = char in 'A'..'Z' || char in 'a'..'z'

    private fun isNameChar(char: Char): Boolean =
        isNameStart(char) || char in '0'..'9' || char == '-' || char == '_' || char == ':' || char == '.'

    private fun isAttributeNameChar(char: Char): Boolean =
        !char.isWhitespace() && char != '=' && char != '/' && char != '>'

    private data class ParsedAttribute(
        val attribute: HtmlAttribute,
        val nextIndex: Int,
    )
}

internal object HtmlEntityDecoder {
    // Shared with the Markdown codec's entity table (markdown/MarkdownEntities.kt),
    // which extends this base set with additional documented names.
    internal val namedEntities: Map<String, String> = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0",
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "ndash" to "\u2013",
        "mdash" to "\u2014",
        "hellip" to "\u2026",
        "lsquo" to "\u2018",
        "rsquo" to "\u2019",
        "ldquo" to "\u201C",
        "rdquo" to "\u201D",
        "bull" to "\u2022",
    )

    internal fun decodeWithSourceMap(input: String, sourceStart: Int): HtmlDecodedText {
        val firstAmpersand = input.indexOf('&')
        if (firstAmpersand < 0) {
            return HtmlDecodedText(
                text = input,
                sourceBoundaryByTextOffset = IntArray(input.length + 1) { offset ->
                    sourceStart + offset
                },
            )
        }

        val builder = StringBuilder(input.length)
        val boundaries = mutableListOf(sourceStart)
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (char != '&') {
                builder.append(char)
                index++
                boundaries += sourceStart + index
                continue
            }

            val semicolon = findEntitySemicolon(input, index + 1)
            if (semicolon < 0) {
                builder.append(char)
                index++
                boundaries += sourceStart + index
                continue
            }

            val body = input.substring(index + 1, semicolon)
            val decoded = decodeEntityBody(body)
            if (decoded == null) {
                val rawEndExclusive = semicolon + 1
                builder.append(input.substring(index, rawEndExclusive))
                for (rawIndex in index until rawEndExclusive) {
                    boundaries += sourceStart + rawIndex + 1
                }
            } else {
                builder.append(decoded)
                val sourceEndExclusive = sourceStart + semicolon + 1
                repeat(decoded.length) {
                    boundaries += sourceEndExclusive
                }
            }
            index = semicolon + 1
        }
        return HtmlDecodedText(
            text = builder.toString(),
            sourceBoundaryByTextOffset = boundaries.toIntArray(),
        )
    }

    /**
     * Decode the small entity set the editor parser understands.
     *
     * Unknown, unterminated, or invalid entities are copied verbatim so malformed
     * input cannot drop user-visible text.
     */
    internal fun decode(input: String): String {
        val firstAmpersand = input.indexOf('&')
        if (firstAmpersand < 0) return input

        val builder = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (char != '&') {
                builder.append(char)
                index++
                continue
            }

            val semicolon = findEntitySemicolon(input, index + 1)
            if (semicolon < 0) {
                builder.append(char)
                index++
                continue
            }

            val body = input.substring(index + 1, semicolon)
            val decoded = decodeEntityBody(body)
            if (decoded == null) {
                builder.append(input.substring(index, semicolon + 1))
            } else {
                builder.append(decoded)
            }
            index = semicolon + 1
        }
        return builder.toString()
    }

    private fun findEntitySemicolon(input: String, startIndex: Int): Int {
        var index = startIndex
        var scanned = 0
        while (index < input.length && scanned <= MAX_ENTITY_BODY_LENGTH) {
            val char = input[index]
            if (char == ';') return index
            if (char == '&' || char == '<' || char.isWhitespace()) return -1
            index++
            scanned++
        }
        return -1
    }

    private fun decodeEntityBody(body: String): String? {
        if (body.isEmpty()) return null
        if (body[0] != '#') return namedEntities[body]

        val codePoint = when {
            body.length > 2 && (body[1] == 'x' || body[1] == 'X') ->
                parseUnsignedInt(body, startIndex = 2, radix = 16)

            body.length > 1 ->
                parseUnsignedInt(body, startIndex = 1, radix = 10)

            else -> null
        } ?: return null

        return codePointToStringOrNull(codePoint)
    }

    private fun parseUnsignedInt(body: String, startIndex: Int, radix: Int): Int? {
        if (startIndex >= body.length) return null
        var result = 0
        for (index in startIndex until body.length) {
            val digit = body[index].digitToIntOrNull(radix) ?: return null
            if (result > (MAX_CODE_POINT - digit) / radix) return null
            result = result * radix + digit
        }
        return result
    }

    private fun codePointToStringOrNull(codePoint: Int): String? {
        if (codePoint <= 0 || codePoint > MAX_CODE_POINT) return null
        if (codePoint in HIGH_SURROGATE_START..LOW_SURROGATE_END) return null
        if (codePoint <= BMP_MAX) return codePoint.toChar().toString()

        val shifted = codePoint - SUPPLEMENTARY_PLANE_START
        val high = HIGH_SURROGATE_START + (shifted shr 10)
        val low = LOW_SURROGATE_START + (shifted and LOW_SURROGATE_MASK)
        return high.toChar().toString() + low.toChar().toString()
    }

    private const val BMP_MAX: Int = 0xFFFF
    private const val MAX_ENTITY_BODY_LENGTH: Int = 32
    private const val MAX_CODE_POINT: Int = 0x10FFFF
    private const val SUPPLEMENTARY_PLANE_START: Int = 0x10000
    private const val HIGH_SURROGATE_START: Int = 0xD800
    private const val LOW_SURROGATE_START: Int = 0xDC00
    private const val LOW_SURROGATE_END: Int = 0xDFFF
    private const val LOW_SURROGATE_MASK: Int = 0x3FF
}

internal class HtmlDecodedText(
    val text: String,
    val sourceBoundaryByTextOffset: IntArray,
)
