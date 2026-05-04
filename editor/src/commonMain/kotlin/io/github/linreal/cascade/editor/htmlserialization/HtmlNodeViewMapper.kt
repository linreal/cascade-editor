package io.github.linreal.cascade.editor.htmlserialization

internal object HtmlNodeViewMapper {

    @OptIn(ExperimentalCascadeHtmlApi::class)
    internal fun toView(node: HtmlNode): HtmlNodeView = when (node) {
        is HtmlNode.Element -> HtmlNodeView.Element(
            tag = node.tag,
            attrs = node.attrs,
            children = node.children.map(::toView),
            sourceStart = node.sourceStart,
            sourceEndExclusive = node.sourceEndExclusive,
        )

        is HtmlNode.Text -> HtmlNodeView.Text(
            text = node.text,
            sourceStart = node.sourceStart,
            sourceEndExclusive = node.sourceEndExclusive,
        )
    }
}
