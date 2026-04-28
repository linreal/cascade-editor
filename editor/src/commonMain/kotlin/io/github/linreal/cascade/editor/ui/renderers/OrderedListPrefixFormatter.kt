package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType

/**
 * Ordered-list marker styles. Style is derived from numbered-list ancestry rather than
 * absolute indentation so free indentation lanes do not force nested marker styles.
 */
internal enum class OrderedListPrefixStyle {
    Decimal,
    LowerAlpha,
    LowerRoman,
}

@Immutable
internal data class OrderedListPrefixStyles(
    private val stylesByBlockId: Map<BlockId, OrderedListPrefixStyle>,
) {
    fun styleFor(blockId: BlockId): OrderedListPrefixStyle {
        return stylesByBlockId[blockId] ?: OrderedListPrefixStyle.Decimal
    }

    companion object {
        val Empty: OrderedListPrefixStyles = OrderedListPrefixStyles(emptyMap())
    }
}

internal val LocalOrderedListPrefixStyles: ProvidableCompositionLocal<OrderedListPrefixStyles> =
    compositionLocalOf { OrderedListPrefixStyles.Empty }

/**
 * Precomputes visible ordered-list styles for the current flat outline.
 *
 * The nearest shallower numbered-list ancestor drives a three-state cycle:
 * decimal -> lower alpha -> lower roman -> decimal. Non-numbered supported ancestors
 * may sit between numbered lists without resetting this ancestry. Unsupported blocks
 * reset the outline segment.
 *
 * This is an O(N) pass because indentation depth is bounded by
 * [BlockAttributes.MAX_INDENTATION_LEVEL]. Renderers should use
 * [OrderedListPrefixStyles.styleFor] for O(1) per-block lookup.
 */
internal fun resolveOrderedListPrefixStyles(
    blocks: List<Block>,
): OrderedListPrefixStyles {
    if (blocks.isEmpty()) return OrderedListPrefixStyles.Empty

    val ancestors = arrayOfNulls<OrderedListPrefixStyle>(MaxIndentationDepthCount)
    val stylesByBlockId = linkedMapOf<BlockId, OrderedListPrefixStyle>()

    for (block in blocks) {
        if (!block.type.supportsIndentation) {
            clearAncestors(ancestors)
            continue
        }

        val depth = block.attributes.indentationLevel
        clearAncestorsAtOrBelow(depth, ancestors)
        if (block.type is BlockType.NumberedList) {
            val style = nextStyle(nearestNumberedAncestorStyle(depth, ancestors))
            stylesByBlockId[block.id] = style
            ancestors[depth] = style
        }
    }

    if (stylesByBlockId.isEmpty()) return OrderedListPrefixStyles.Empty
    return OrderedListPrefixStyles(stylesByBlockId.toMap())
}

/**
 * Formats the visible ordered-list prefix from the stored list number and derived style.
 *
 * The document model stores only the decimal [number]; alpha and roman marker styles are
 * presentation details derived at render time.
 */
internal fun formatOrderedListPrefix(
    number: Int,
    style: OrderedListPrefixStyle,
): String {
    val formattedNumber = when (style) {
        OrderedListPrefixStyle.Decimal -> number.toString()
        OrderedListPrefixStyle.LowerAlpha -> formatAlphabetic(number)
        OrderedListPrefixStyle.LowerRoman -> formatRomanOrDecimal(number)
    }

    return "$formattedNumber."
}

private fun nextStyle(parentStyle: OrderedListPrefixStyle?): OrderedListPrefixStyle {
    return when (parentStyle) {
        null -> OrderedListPrefixStyle.Decimal
        OrderedListPrefixStyle.Decimal -> OrderedListPrefixStyle.LowerAlpha
        OrderedListPrefixStyle.LowerAlpha -> OrderedListPrefixStyle.LowerRoman
        OrderedListPrefixStyle.LowerRoman -> OrderedListPrefixStyle.Decimal
    }
}

private fun nearestNumberedAncestorStyle(
    depth: Int,
    ancestors: Array<OrderedListPrefixStyle?>,
): OrderedListPrefixStyle? {
    if (depth == 0) return null
    for (candidateDepth in (depth - 1) downTo 0) {
        ancestors[candidateDepth]?.let { return it }
    }
    return null
}

private fun clearAncestorsAtOrBelow(
    depth: Int,
    ancestors: Array<OrderedListPrefixStyle?>,
) {
    for (index in depth..ancestors.lastIndex) {
        ancestors[index] = null
    }
}

private fun clearAncestors(ancestors: Array<OrderedListPrefixStyle?>) {
    for (index in ancestors.indices) {
        ancestors[index] = null
    }
}

private fun formatAlphabetic(number: Int): String {
    if (number <= 0) return number.toString()

    val result = StringBuilder()
    var remaining = number

    while (remaining > 0) {
        remaining -= 1
        result.append(('a'.code + remaining % AlphabetSize).toChar())
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
private const val MaxIndentationDepthCount = BlockAttributes.MAX_INDENTATION_LEVEL + 1

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
