package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block

internal class TagDecodeContextImpl(
    private val engine: DecodeEngineHandle,
    private val currentNode: HtmlNodeView.Element,
    override val isBlockContext: Boolean,
    override val parentTag: String?,
) : TagDecodeContext() {

    override val rawSource: String
        get() = engine.rawSource

    override val charOffset: Int
        get() = currentNode.sourceStart

    override fun decodeInline(children: List<HtmlNodeView>): InlineFragment =
        engine.decodeInline(children, parentTag = currentElementTag)

    override fun decodeBlocks(children: List<HtmlNodeView>): List<Block> =
        engine.decodeBlocks(children, parentTag = currentElementTag)

    override fun collectInlineText(
        children: List<HtmlNodeView>,
        trimEdges: Boolean,
        trimSingleTrailingNewline: Boolean,
        collapseInternalSpaces: Boolean,
    ): InlineFragment {
        var fragment = decodeInline(children)
        if (trimSingleTrailingNewline) {
            fragment = fragment.dropSingleTrailingNewline()
        }
        if (trimEdges) {
            fragment = fragment.trimEdges()
        }
        if (collapseInternalSpaces) {
            fragment = fragment.collapseSpacesAndTabs()
        }
        return fragment
    }

    override fun rawSliceOf(node: HtmlNodeView): String =
        rawSource.substring(node.sourceStart, node.sourceEndExclusive)

    override fun tagDecoderFor(tag: String): TagDecoder? = engine.tagDecoderFor(tag)

    override fun warn(warning: HtmlDecodeWarning) {
        engine.warn(warning)
    }

    private val currentElementTag: String
        get() = currentNode.tag
}

private fun InlineFragment.dropSingleTrailingNewline(): InlineFragment {
    if (text.lastOrNull() != '\n') return this
    return sliceByOriginalRange(start = 0, endExclusive = text.length - 1)
}

private fun InlineFragment.trimEdges(): InlineFragment {
    var start = 0
    var endExclusive = text.length
    while (start < endExclusive && text[start].isWhitespace()) {
        start++
    }
    while (endExclusive > start && text[endExclusive - 1].isWhitespace()) {
        endExclusive--
    }
    return sliceByOriginalRange(start, endExclusive)
}

private fun InlineFragment.sliceByOriginalRange(start: Int, endExclusive: Int): InlineFragment {
    if (start == 0 && endExclusive == text.length) return this
    if (start >= endExclusive) return InlineFragment("", emptyList())
    val shiftedSpans = spans.mapNotNull { span ->
        val clampedStart = maxOf(span.start, start)
        val clampedEnd = minOf(span.end, endExclusive)
        if (clampedStart >= clampedEnd) {
            null
        } else {
            span.copy(start = clampedStart - start, end = clampedEnd - start)
        }
    }
    return InlineFragment(
        text = text.substring(start, endExclusive),
        spans = shiftedSpans,
    )
}

/**
 * Collapse consecutive spaces/tabs while remapping spans through the changed text.
 *
 * The boundary map records the transformed offset at every original text boundary,
 * which is enough to move half-open span ranges without inspecting style values.
 */
private fun InlineFragment.collapseSpacesAndTabs(): InlineFragment {
    if (!text.containsDoubleSpaceOrTab()) return this

    val boundaryMap = IntArray(text.length + 1)
    val builder = StringBuilder(text.length)
    var previousWasSpaceOrTab = false
    for (index in text.indices) {
        boundaryMap[index] = builder.length
        val char = text[index]
        if (char == ' ' || char == '\t') {
            if (!previousWasSpaceOrTab) {
                builder.append(' ')
                previousWasSpaceOrTab = true
            }
        } else {
            builder.append(char)
            previousWasSpaceOrTab = false
        }
    }
    boundaryMap[text.length] = builder.length

    val remappedSpans = spans.mapNotNull { span ->
        val start = boundaryMap[span.start.coerceIn(0, text.length)]
        val end = boundaryMap[span.end.coerceIn(0, text.length)]
        if (start >= end) null else span.copy(start = start, end = end)
    }
    return InlineFragment(builder.toString(), remappedSpans)
}

private fun String.containsDoubleSpaceOrTab(): Boolean {
    var previousWasSpaceOrTab = false
    for (char in this) {
        val isSpaceOrTab = char == ' ' || char == '\t'
        if (isSpaceOrTab && previousWasSpaceOrTab) return true
        if (char == '\t') return true
        previousWasSpaceOrTab = isSpaceOrTab
    }
    return false
}
