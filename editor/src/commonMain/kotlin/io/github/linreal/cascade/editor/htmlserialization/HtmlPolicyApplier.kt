package io.github.linreal.cascade.editor.htmlserialization

internal object HtmlPolicyApplier {

    /**
     * Apply profile parser policies to root nodes before codec dispatch.
     *
     * The policy phase only rewrites root-level shape. Element children remain parser
     * output so tag decoders own element-specific whitespace behavior.
     */
    internal fun apply(
        rawSource: String,
        nodes: List<HtmlNode>,
        profile: HtmlProfile,
    ): HtmlPolicyResult {
        val warnings = mutableListOf<HtmlDecodeWarning>()
        val parts = when (profile.blockSeparator) {
            BlockSeparator.BlockTags -> nodes.map { PolicyPart.Node(it) }

            BlockSeparator.Newline -> splitRootTextOnNewlines(rawSource, nodes, profile.entityDecode)
        }

        val policyNodes = applyInlineRoot(parts, profile.inlineRoot, warnings)
        return HtmlPolicyResult(nodes = policyNodes, warnings = warnings)
    }

    private fun splitRootTextOnNewlines(
        rawSource: String,
        nodes: List<HtmlNode>,
        entityDecode: EntityDecode,
    ): List<PolicyPart> {
        val parts = mutableListOf<PolicyPart>()
        for (node in nodes) {
            if (node is HtmlNode.Text) {
                splitRootTextNode(rawSource, node, entityDecode, parts)
            } else {
                parts += PolicyPart.Node(node)
            }
        }
        return parts
    }

    private fun splitRootTextNode(
        rawSource: String,
        node: HtmlNode.Text,
        entityDecode: EntityDecode,
        parts: MutableList<PolicyPart>,
    ) {
        if (node.text.indexOf('\n') < 0) {
            parts += PolicyPart.Node(node)
            return
        }

        val text = textWithSourceBoundaries(rawSource, node, entityDecode)
        var segmentStart = 0
        var index = 0
        while (index < text.value.length) {
            if (text.value[index] != '\n') {
                index++
                continue
            }

            val segmentEnd = trimTrailingSpacesAndTabs(text.value, segmentStart, index)
            addTextPart(text, segmentStart, segmentEnd, parts)

            val newlineStart = index
            while (index < text.value.length && text.value[index] == '\n') {
                index++
            }
            parts += PolicyPart.NewlineBreak(
                newlines = (newlineStart until index).map { newlineIndex ->
                    SourceRange(
                        sourceStart = text.sourceBoundaryByTextOffset[newlineIndex],
                        sourceEndExclusive = text.sourceBoundaryByTextOffset[newlineIndex + 1],
                    )
                },
            )

            segmentStart = skipSpacesAndTabs(text.value, index, text.value.length)
            index = segmentStart
        }

        addTextPart(text, segmentStart, text.value.length, parts)
    }

    private fun addTextPart(
        text: TextWithSourceBoundaries,
        start: Int,
        endExclusive: Int,
        parts: MutableList<PolicyPart>,
    ) {
        if (start >= endExclusive) return
        val value = text.value.substring(start, endExclusive)
        if (value.isEmpty()) return
        parts += PolicyPart.Node(
            HtmlNode.Text(
                text = value,
                sourceStart = text.sourceBoundaryByTextOffset[start],
                sourceEndExclusive = text.sourceBoundaryByTextOffset[endExclusive],
            )
        )
    }

    private fun textWithSourceBoundaries(
        rawSource: String,
        node: HtmlNode.Text,
        entityDecode: EntityDecode,
    ): TextWithSourceBoundaries =
        when (entityDecode) {
            EntityDecode.None -> TextWithSourceBoundaries(
                value = node.text,
                sourceBoundaryByTextOffset = IntArray(node.text.length + 1) { offset ->
                    node.sourceStart + offset
                },
            )

            EntityDecode.Standard -> {
                val decoded = HtmlEntityDecoder.decodeWithSourceMap(
                    rawSource.substring(node.sourceStart, node.sourceEndExclusive),
                    sourceStart = node.sourceStart,
                )
                TextWithSourceBoundaries(
                    value = decoded.text,
                    sourceBoundaryByTextOffset = decoded.sourceBoundaryByTextOffset,
                )
            }
        }

    private fun applyInlineRoot(
        parts: List<PolicyPart>,
        inlineRoot: InlineRoot,
        warnings: MutableList<HtmlDecodeWarning>,
    ): List<HtmlNode> {
        val output = mutableListOf<HtmlNode>()
        val inlineRun = mutableListOf<HtmlNode>()

        fun flushInlineRun() {
            if (inlineRun.isEmpty()) return
            val run = inlineRun.dropIgnorableRootWhitespace()
            inlineRun.clear()
            if (run.isEmpty()) return

            when (inlineRoot) {
                InlineRoot.Drop -> {
                    val offset = run.firstMeaningfulOffset()
                    warnings += HtmlDecodeWarning.DroppedContent(
                        reason = "Dropped root inline content",
                        charOffset = offset,
                    )
                }

                InlineRoot.WrapInParagraph -> {
                    output += HtmlNode.Element(
                        tag = SYNTHETIC_PARAGRAPH_TAG,
                        attrs = emptyMap(),
                        children = run,
                        sourceStart = run.first().sourceStart,
                        sourceEndExclusive = run.last().sourceEndExclusive,
                    )
                }
            }
        }

        for (part in parts) {
            when (part) {
                is PolicyPart.Node -> {
                    if (part.node.isRootBlockElement()) {
                        flushInlineRun()
                        output += part.node
                    } else {
                        inlineRun += part.node
                    }
                }

                is PolicyPart.NewlineBreak -> {
                    flushInlineRun()
                    if (inlineRoot == InlineRoot.WrapInParagraph) {
                        output.addEmptyParagraphsFor(part)
                    }
                }
            }
        }

        flushInlineRun()
        return output
    }

    private fun MutableList<HtmlNode>.addEmptyParagraphsFor(part: PolicyPart.NewlineBreak) {
        for (newlineOffset in 1 until part.newlines.size) {
            val sourceRange = part.newlines[newlineOffset]
            this += HtmlNode.Element(
                tag = SYNTHETIC_PARAGRAPH_TAG,
                attrs = emptyMap(),
                children = emptyList(),
                sourceStart = sourceRange.sourceStart,
                sourceEndExclusive = sourceRange.sourceEndExclusive,
            )
        }
    }

    private fun List<HtmlNode>.dropIgnorableRootWhitespace(): List<HtmlNode> {
        val first = indexOfFirst { !it.isIgnorableRootWhitespace() }
        if (first == -1) return emptyList()
        val last = indexOfLast { !it.isIgnorableRootWhitespace() }
        return subList(first, last + 1).toList()
    }

    private fun List<HtmlNode>.firstMeaningfulOffset(): Int {
        val meaningful = firstOrNull { !it.isIgnorableRootWhitespace() }
        return meaningful?.sourceStart ?: first().sourceStart
    }

    private fun HtmlNode.isRootBlockElement(): Boolean =
        this is HtmlNode.Element && tag !in rootInlineTags

    private fun HtmlNode.isIgnorableRootWhitespace(): Boolean =
        this is HtmlNode.Text && text.isBlank()

    private fun trimTrailingSpacesAndTabs(source: String, start: Int, endExclusive: Int): Int {
        var index = endExclusive
        while (index > start && (source[index - 1] == ' ' || source[index - 1] == '\t')) {
            index--
        }
        return index
    }

    private fun skipSpacesAndTabs(source: String, start: Int, endExclusive: Int): Int {
        var index = start
        while (index < endExclusive && (source[index] == ' ' || source[index] == '\t')) {
            index++
        }
        return index
    }

    private sealed interface PolicyPart {
        data class Node(val node: HtmlNode) : PolicyPart

        data class NewlineBreak(
            val newlines: List<SourceRange>,
        ) : PolicyPart
    }

    private data class TextWithSourceBoundaries(
        val value: String,
        val sourceBoundaryByTextOffset: IntArray,
    )

    private data class SourceRange(
        val sourceStart: Int,
        val sourceEndExclusive: Int,
    )

    private const val SYNTHETIC_PARAGRAPH_TAG: String = "p"

    private val rootInlineTags: Set<String> = setOf(
        "a",
        "abbr",
        "b",
        "br",
        "cite",
        "code",
        "del",
        "em",
        "i",
        "img",
        "ins",
        "kbd",
        "mark",
        "s",
        "small",
        "span",
        "strike",
        "strong",
        "sub",
        "sup",
        "u",
        "var",
        "wbr",
    )
}

internal data class HtmlPolicyResult(
    val nodes: List<HtmlNode>,
    val warnings: List<HtmlDecodeWarning>,
)
