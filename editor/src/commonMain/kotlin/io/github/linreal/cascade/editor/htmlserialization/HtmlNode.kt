package io.github.linreal.cascade.editor.htmlserialization

/**
 * Internal HTML AST passed from the parser pipeline to later policy/decode stages.
 *
 * Source ranges are UTF-16 half-open offsets into the original source. Element tag
 * and attribute names are normalized to lowercase; text is entity-decoded according
 * to the parser's active [EntityDecode] mode.
 */
internal sealed interface HtmlNode {
    val sourceStart: Int
    val sourceEndExclusive: Int

    data class Element(
        val tag: String,
        val attrs: Map<String, String>,
        val children: List<HtmlNode>,
        override val sourceStart: Int,
        override val sourceEndExclusive: Int,
    ) : HtmlNode

    data class Text(
        val text: String,
        override val sourceStart: Int,
        override val sourceEndExclusive: Int,
    ) : HtmlNode
}

internal data class HtmlTreeBuilderResult(
    val nodes: List<HtmlNode>,
    val warnings: List<HtmlDecodeWarning>,
)
