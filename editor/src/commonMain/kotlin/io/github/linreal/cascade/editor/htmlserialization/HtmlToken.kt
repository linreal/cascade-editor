package io.github.linreal.cascade.editor.htmlserialization

/**
 * Internal token stream produced directly from the original HTML source.
 *
 * Source ranges are UTF-16 half-open offsets into the raw input. Text token content
 * may be entity-decoded, but its range always points at the pre-decoded source slice.
 */
internal sealed interface HtmlToken {
    val sourceStart: Int
    val sourceEndExclusive: Int

    data class Text(
        val text: String,
        override val sourceStart: Int,
        override val sourceEndExclusive: Int,
    ) : HtmlToken

    data class OpenTag(
        val name: String,
        val attributes: List<HtmlAttribute>,
        val selfClosing: Boolean,
        override val sourceStart: Int,
        override val sourceEndExclusive: Int,
    ) : HtmlToken {
        internal val closesImmediately: Boolean
            get() = selfClosing || HtmlVoidElements.contains(name)
    }

    data class CloseTag(
        val name: String,
        override val sourceStart: Int,
        override val sourceEndExclusive: Int,
    ) : HtmlToken
}

/**
 * Attribute parsed from an opening tag.
 *
 * [value] is `null` for boolean-style attributes written without `=...`; the tree
 * builder converts that shape to an empty string because decoder attributes use
 * `Map<String, String>`.
 */
internal data class HtmlAttribute(
    val name: String,
    val value: String?,
    val sourceStart: Int,
    val sourceEndExclusive: Int,
)

internal object HtmlVoidElements {
    private val names: Set<String> = setOf(
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "param",
        "source",
        "track",
        "wbr",
    )

    internal fun contains(name: String): Boolean = name in names
}
