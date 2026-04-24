package io.github.linreal.cascade.editor.ui.renderers

import io.github.linreal.cascade.editor.core.BlockType

/**
 * Formats the visible ordered-list prefix from the stored list number and outline depth.
 *
 * The document model stores only the decimal [number]; depth-specific alpha and roman
 * styles are presentation details and must not be persisted back into [BlockType].
 */
internal fun formatOrderedListPrefix(number: Int, depth: Int): String {
    val formattedNumber = when (depth) {
        1 -> formatAlphabetic(number, uppercase = false)
        2 -> formatRomanOrDecimal(number)
        3 -> formatAlphabetic(number, uppercase = true)
        else -> number.toString()
    }

    return "$formattedNumber."
}

private fun formatAlphabetic(number: Int, uppercase: Boolean): String {
    if (number <= 0) return number.toString()

    val firstLetter = if (uppercase) 'A' else 'a'
    val result = StringBuilder()
    var remaining = number

    while (remaining > 0) {
        remaining -= 1
        result.append((firstLetter.code + remaining % AlphabetSize).toChar())
        remaining /= AlphabetSize
    }

    return result.reverse().toString()
}

private fun formatRomanOrDecimal(number: Int): String {
    if (number !in RomanRange) return number.toString()

    var remaining = number
    val result = StringBuilder()

    for ((value, numeral) in RomanNumerals) {
        while (remaining >= value) {
            result.append(numeral)
            remaining -= value
        }
    }

    return result.toString()
}

private const val AlphabetSize = 26

private val RomanRange = 1..3999

private val RomanNumerals = listOf(
    1000 to "m",
    900 to "cm",
    500 to "d",
    400 to "cd",
    100 to "c",
    90 to "xc",
    50 to "l",
    40 to "xl",
    10 to "x",
    9 to "ix",
    5 to "v",
    4 to "iv",
    1 to "i",
)
