package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.normalizeIndentationOutline
import io.github.linreal.cascade.editor.core.renumberNumberedLists

internal object HtmlDecodeEngine {

    internal fun decodeWithReport(
        html: String,
        profile: HtmlProfile,
        limits: HtmlDecodeLimits = HtmlDecodeLimits.Default,
    ): HtmlDecodeResult {
        if (html.length > limits.maxInputChars) {
            return HtmlDecodeResult(
                blocks = emptyList(),
                warnings = listOf(
                    HtmlDecodeWarning.InputLimitExceeded(limit = limits.maxInputChars, actual = html.length),
                ),
            )
        }
        return try {
            val parsed = HtmlParser.parse(html, profile)
            val warnings = parsed.warnings.toMutableList()
            val rootNodes = parsed.nodes.map(HtmlNodeViewMapper::toView)
            val runner = Runner(
                rawSource = html,
                profile = profile,
                warnings = warnings,
            )
            val decodedBlocks = runner.decodeBlocks(rootNodes, parentTag = null)
            val blocks = renumberNumberedLists(normalizeIndentationOutline(decodedBlocks))
            HtmlDecodeResult(blocks = blocks, warnings = warnings)
        } catch (throwable: Throwable) {
            HtmlDecodeResult(
                blocks = emptyList(),
                warnings = listOf(decoderExceptionWarning(tag = null, throwable = throwable, charOffset = 0)),
            )
        }
    }

    private class Runner(
        override val rawSource: String,
        private val profile: HtmlProfile,
        private val warnings: MutableList<HtmlDecodeWarning>,
    ) : DecodeEngineHandle {

        override fun warn(warning: HtmlDecodeWarning) {
            warnings += warning
        }

        override fun tagDecoderFor(tag: String): TagDecoder? = profile.tagDecoderFor(tag)

        override fun decodeBlocks(children: List<HtmlNodeView>, parentTag: String?): List<Block> {
            val blocks = mutableListOf<Block>()
            var pendingInline = InlineFragment("", emptyList())

            fun flushInline() {
                if (pendingInline.text.isEmpty() && pendingInline.spans.isEmpty()) return
                blocks += Block.paragraph(pendingInline.text, pendingInline.spans)
                pendingInline = InlineFragment("", emptyList())
            }

            for (child in children) {
                when (child) {
                    is HtmlNodeView.Text -> {
                        pendingInline = pendingInline.appendText(child.text)
                    }

                    is HtmlNodeView.Element -> {
                        when (val result = decodeElement(child, isBlockContext = true, parentTag = parentTag)) {
                            is TagDecodeResult.AsBlock -> {
                                flushInline()
                                blocks += result.block
                            }

                            is TagDecodeResult.AsBlocks -> {
                                flushInline()
                                blocks += result.blocks
                            }

                            is TagDecodeResult.AsText -> {
                                pendingInline = pendingInline.appendFragment(InlineFragment(result.text, result.spans))
                            }

                            TagDecodeResult.Drop -> Unit
                        }
                    }
                }
            }
            flushInline()
            return blocks
        }

        override fun decodeInline(children: List<HtmlNodeView>, parentTag: String?): InlineFragment {
            var inline = InlineFragment("", emptyList())
            for (child in children) {
                when (child) {
                    is HtmlNodeView.Text -> {
                        inline = inline.appendText(child.text)
                    }

                    is HtmlNodeView.Element -> {
                        when (val result = decodeElement(child, isBlockContext = false, parentTag = parentTag)) {
                            is TagDecodeResult.AsText -> {
                                inline = inline.appendFragment(InlineFragment(result.text, result.spans))
                            }

                            is TagDecodeResult.AsBlock -> {
                                warn(HtmlDecodeWarning.BlockInInlineContext(child.tag, child.sourceStart))
                                inline = inline.appendBlocks(listOf(result.block))
                            }

                            is TagDecodeResult.AsBlocks -> {
                                warn(HtmlDecodeWarning.BlockInInlineContext(child.tag, child.sourceStart))
                                inline = inline.appendBlocks(result.blocks)
                            }

                            TagDecodeResult.Drop -> Unit
                        }
                    }
                }
            }
            return inline
        }

        private fun decodeElement(
            element: HtmlNodeView.Element,
            isBlockContext: Boolean,
            parentTag: String?,
        ): TagDecodeResult {
            val context = TagDecodeContextImpl(
                engine = this,
                currentNode = element,
                isBlockContext = isBlockContext,
                parentTag = parentTag,
            )

            return try {
                val decoder = profile.tagDecoderFor(element.tag)
                if (decoder != null) {
                    decoder.decode(context, element.attrs, element.children)
                } else {
                    decodeUnknown(element, context, isBlockContext)
                }
            } catch (throwable: Throwable) {
                warn(
                    decoderExceptionWarning(
                        tag = element.tag,
                        throwable = throwable,
                        charOffset = element.sourceStart,
                    )
                )
                TagDecodeResult.Drop
            }
        }

        private fun decodeUnknown(
            element: HtmlNodeView.Element,
            context: TagDecodeContext,
            isBlockContext: Boolean,
        ): TagDecodeResult = when (val policy = profile.unknownTagPolicy) {
            UnknownTagPolicy.Strip -> stripUnknown(element, isBlockContext)
            UnknownTagPolicy.WarnAndStrip -> {
                warn(HtmlDecodeWarning.UnknownTag(element.tag, element.sourceStart))
                stripUnknown(element, isBlockContext)
            }

            UnknownTagPolicy.Preserve -> {
                if (isBlockContext) {
                    TagDecodeResult.AsBlock(preserveUnknownBlock(element, context))
                } else {
                    warn(HtmlDecodeWarning.UnknownTag(element.tag, element.sourceStart))
                    stripUnknown(element, isBlockContext = false)
                }
            }

            is UnknownTagPolicy.Custom -> policy.handler(element, context)
        }

        private fun stripUnknown(
            element: HtmlNodeView.Element,
            isBlockContext: Boolean,
        ): TagDecodeResult =
            if (isBlockContext) {
                TagDecodeResult.AsBlocks(decodeBlocks(element.children, parentTag = element.tag))
            } else {
                val inline = decodeInline(element.children, parentTag = element.tag)
                TagDecodeResult.AsText(inline.text, inline.spans)
            }

        private fun preserveUnknownBlock(
            element: HtmlNodeView.Element,
            context: TagDecodeContext,
        ): Block = Block(
            id = BlockId.generate(),
            type = PreservedHtmlBlockType,
            content = BlockContent.Custom(
                typeId = PreservedHtmlBlockType.typeId,
                data = mapOf(
                    "tagName" to element.tag,
                    "rawHtml" to context.rawSliceOf(element),
                ),
            ),
        )
    }
}

internal interface DecodeEngineHandle {
    val rawSource: String

    fun warn(warning: HtmlDecodeWarning)

    fun tagDecoderFor(tag: String): TagDecoder?

    fun decodeBlocks(children: List<HtmlNodeView>, parentTag: String?): List<Block>

    fun decodeInline(children: List<HtmlNodeView>, parentTag: String?): InlineFragment
}

private fun decoderExceptionWarning(
    tag: String?,
    throwable: Throwable,
    charOffset: Int,
): HtmlDecodeWarning.DecoderException =
    HtmlDecodeWarning.DecoderException(
        tag = tag,
        message = throwable.message ?: throwable.toString(),
        charOffset = charOffset,
    )

private fun InlineFragment.appendText(textToAppend: String): InlineFragment =
    if (textToAppend.isEmpty()) this else copy(text = text + textToAppend)

private fun InlineFragment.appendFragment(fragment: InlineFragment): InlineFragment {
    if (fragment.text.isEmpty() && fragment.spans.isEmpty()) return this
    val offset = text.length
    return InlineFragment(
        text = text + fragment.text,
        spans = spans + fragment.spans.mapNotNull { it.shiftByOrNull(offset, text.length + fragment.text.length) },
    )
}

private fun InlineFragment.appendBlocks(blocks: List<Block>): InlineFragment {
    var current = this
    for (block in blocks) {
        val content = block.content
        if (content is BlockContent.Text) {
            current = current.appendFragment(InlineFragment(content.text, content.spans))
        }
    }
    return current
}

/**
 * Shift a decoder-provided span into a larger inline fragment and drop impossible
 * ranges defensively so malformed consumer output cannot break sibling decoding.
 */
private fun TextSpan.shiftByOrNull(offset: Int, maxEnd: Int): TextSpan? {
    val shiftedStart = start + offset
    val shiftedEnd = end + offset
    if (shiftedStart < 0 || shiftedEnd <= shiftedStart || shiftedEnd > maxEnd) return null
    return copy(start = shiftedStart, end = shiftedEnd)
}
