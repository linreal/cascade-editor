package io.github.linreal.cascade.editor.htmlserialization

internal object HtmlTreeBuilder {

    /**
     * Build a best-effort node tree from [tokens].
     *
     * Malformed nesting is straightened by closing the innermost open nodes before
     * closing the matching ancestor. This preserves document order while keeping raw
     * source ranges usable for later warning offsets and raw-slice preservation.
     */
    internal fun build(rawSource: String, tokens: List<HtmlToken>): HtmlTreeBuilderResult {
        val rootChildren = mutableListOf<HtmlNode>()
        val stack = mutableListOf<ElementBuilder>()
        val warnings = mutableListOf<HtmlDecodeWarning>()
        // When a mismatched ancestor close auto-closes inner elements, the later
        // counterpart close for each inner tag is expected noise. Suppress by tag
        // name so nested malformed trees get one warning at the mismatch point
        // rather than a second stray-close warning for the tag we already closed.
        val suppressedClosingTags = mutableListOf<String>()

        fun addNode(node: HtmlNode) {
            if (stack.isEmpty()) {
                rootChildren += node
            } else {
                stack.last().children += node
            }
        }

        fun closeTop(sourceEndExclusive: Int) {
            val builder = stack.removeAt(stack.lastIndex)
            addNode(builder.toNode(sourceEndExclusive))
        }

        for (token in tokens) {
            when (token) {
                is HtmlToken.Text -> {
                    if (token.text.isNotEmpty()) {
                        addNode(
                            HtmlNode.Text(
                                text = token.text,
                                sourceStart = token.sourceStart,
                                sourceEndExclusive = token.sourceEndExclusive,
                            )
                        )
                    }
                }

                is HtmlToken.OpenTag -> {
                    val attrs = token.attributes.associate { it.name to it.value.orEmpty() }
                    if (token.closesImmediately) {
                        addNode(
                            HtmlNode.Element(
                                tag = token.name,
                                attrs = attrs,
                                children = emptyList(),
                                sourceStart = token.sourceStart,
                                sourceEndExclusive = token.sourceEndExclusive,
                            )
                        )
                    } else {
                        stack += ElementBuilder(
                            tag = token.name,
                            attrs = attrs,
                            sourceStart = token.sourceStart,
                        )
                    }
                }

                is HtmlToken.CloseTag -> {
                    val matchingIndex = stack.indexOfLast { it.tag == token.name }
                    when {
                        matchingIndex == -1 -> {
                            if (!suppressedClosingTags.removeLastMatching(token.name)) {
                                warnings += HtmlDecodeWarning.StrayClosingTag(
                                    tag = token.name,
                                    charOffset = token.sourceStart,
                                )
                            }
                        }

                        matchingIndex == stack.lastIndex -> {
                            closeTop(token.sourceEndExclusive)
                        }

                        else -> {
                            warnings += HtmlDecodeWarning.MismatchedNesting(
                                expected = stack.last().tag,
                                found = token.name,
                                charOffset = token.sourceStart,
                            )
                            while (stack.lastIndex > matchingIndex) {
                                val builder = stack.removeAt(stack.lastIndex)
                                suppressedClosingTags += builder.tag
                                addNode(builder.toNode(token.sourceStart))
                            }
                            closeTop(token.sourceEndExclusive)
                        }
                    }
                }
            }
        }

        while (stack.isNotEmpty()) {
            val builder = stack.removeAt(stack.lastIndex)
            warnings += HtmlDecodeWarning.UnclosedTag(
                tag = builder.tag,
                charOffset = builder.sourceStart,
            )
            addNode(builder.toNode(rawSource.length))
        }

        return HtmlTreeBuilderResult(nodes = rootChildren, warnings = warnings)
    }

    private fun MutableList<String>.removeLastMatching(value: String): Boolean {
        for (index in lastIndex downTo 0) {
            if (this[index] == value) {
                removeAt(index)
                return true
            }
        }
        return false
    }

    private data class ElementBuilder(
        val tag: String,
        val attrs: Map<String, String>,
        val sourceStart: Int,
        val children: MutableList<HtmlNode> = mutableListOf(),
    ) {
        fun toNode(sourceEndExclusive: Int): HtmlNode.Element =
            HtmlNode.Element(
                tag = tag,
                attrs = attrs,
                children = children.toList(),
                sourceStart = sourceStart,
                sourceEndExclusive = sourceEndExclusive,
            )
    }
}
