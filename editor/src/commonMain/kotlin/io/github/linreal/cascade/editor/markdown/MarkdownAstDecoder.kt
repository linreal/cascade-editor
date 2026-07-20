package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.InlineFragment
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl.AutolinkParser
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser
import org.intellij.markdown.parser.sequentialparsers.impl.EmphStrongDelimiterParser
import org.intellij.markdown.parser.sequentialparsers.impl.ImageParser
import org.intellij.markdown.parser.sequentialparsers.impl.InlineLinkParser
import org.intellij.markdown.parser.sequentialparsers.impl.ReferenceLinkParser

/** Custom typeId carried by opaque preserved-Markdown blocks. */
internal const val MARKDOWN_PRESERVED_TYPE_ID: String = "md.preserved"

/** Custom typeId carried by opaque preserved block-level HTML. */
internal const val MARKDOWN_PRESERVED_HTML_TYPE_ID: String = "md.preservedHtml"

/** GFM block/inline grammar with upstream's overly broad dollar-math parser removed. */
internal class CascadeMarkdownFlavour : GFMFlavourDescriptor() {
    override val sequentialParserManager: SequentialParserManager = object : SequentialParserManager() {
        override fun getParserSequence(): List<SequentialParser> = listOf(
            AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
            BacktickParser(),
            ImageParser(),
            InlineLinkParser(),
            ReferenceLinkParser(),
            EmphasisLikeParser(EmphStrongDelimiterParser(), StrikeThroughDelimiterParser()),
        )
    }
}

/** One assertion-free, non-cancellable parser entry point for all decode/verification paths. */
internal fun parseMarkdownTree(text: CharSequence): ASTNode =
    MarkdownParser(
        CascadeMarkdownFlavour(),
        assertionsEnabled = false,
        cancellationToken = CancellationToken.NonCancellable,
    ).buildMarkdownTreeFromString(text)

/**
 * A parser-facing projection of the source.
 *
 * JetBrains Markdown splits physical lines only on `\n` and treats a BOM as
 * ordinary text. Cascade accepts BOM, CRLF, and lone CR, so the parser sees a
 * normalized projection while every AST boundary maps back to the original
 * UTF-16 source. Preserved payloads always slice [MarkdownSource.text].
 */
internal class MarkdownParseInput private constructor(
    val text: String,
    private val originalBoundary: IntArray,
) {
    fun originalOffset(parseOffset: Int): Int =
        originalBoundary[parseOffset.coerceIn(0, originalBoundary.lastIndex)]

    fun sourceRange(start: Int, endExclusive: Int): MarkdownSourceRange {
        val from = originalOffset(start)
        val to = originalOffset(endExclusive).coerceAtLeast(from)
        return MarkdownSourceRange(from, to)
    }

    fun sourceRange(node: ASTNode, trimTrailingNewline: Boolean = false): MarkdownSourceRange {
        var end = node.endOffset
        if (trimTrailingNewline) {
            while (end > node.startOffset && text[end - 1] == '\n') end--
        }
        return sourceRange(node.startOffset, end)
    }

    companion object {
        fun of(source: String): MarkdownParseInput {
            val normalized = StringBuilder(source.length)
            val boundaries = ArrayList<Int>(source.length + 1)
            var index = if (source.startsWith('\uFEFF')) 1 else 0
            boundaries += index
            while (index < source.length) {
                when (source[index]) {
                    '\r' -> {
                        normalized.append('\n')
                        index += if (index + 1 < source.length && source[index + 1] == '\n') 2 else 1
                    }
                    else -> {
                        normalized.append(source[index])
                        index++
                    }
                }
                boundaries += index
            }
            return MarkdownParseInput(normalized.toString(), boundaries.toIntArray())
        }
    }
}

/** JetBrains AST -> Cascade editor model. JetBrains types never cross the public API. */
internal class MarkdownAstDecoder(
    private val source: MarkdownSource,
    private val input: MarkdownParseInput,
    private val root: ASTNode,
    private val profile: MarkdownProfile,
    private val limits: MarkdownCodecLimits,
    private val state: MarkdownParseState,
) {
    private val preserve: Boolean get() = profile.unsupportedSyntax == UnsupportedSyntax.Preserve
    private val titledDefinitions = HashSet<String>()
    private var escalationKind: String? = null
    private var rootOwnerRange: MarkdownSourceRange = MarkdownSourceRange(0, 0)

    fun decode(): List<Block> {
        collectDefinitions(root)
        if (state.isAborted) return emptyList()

        val frontMatter = recognizeFrontMatter(input.text)
        val out = ArrayList<Block>()
        var previousEnd = 0
        var frontMatterHandled = false
        for (node in root.children) {
            if (state.isAborted) break
            if (isTrivia(node.type)) continue

            if (frontMatter != null && node.startOffset < frontMatter.endExclusive) {
                if (!frontMatterHandled) {
                    frontMatterHandled = true
                    if (preserve) {
                        addPreserved(out, "frontMatter", input.sourceRange(0, frontMatter.endExclusive))
                    } else {
                        state.warn(
                            MarkdownDecodeWarning.UnsupportedSyntax(
                                construct = "frontMatter",
                                detail = "recognized construct decodes through ordinary syntaxes with degraded content",
                                range = input.sourceRange(0, frontMatter.endExclusive),
                            ),
                        )
                    }
                }
                if (preserve) {
                    previousEnd = maxOf(previousEnd, node.endOffset)
                    continue
                }
            }

            if (profile.newlineSemantics == NewlineSemantics.HardBreak) {
                addHardBreakGap(out, previousEnd, node.startOffset, leading = previousEnd == 0)
            }
            val range = input.sourceRange(node, trimTrailingNewline = true)
            rootOwnerRange = range
            escalationKind = null
            val buffer = ArrayList<Block>()
            lowerNode(node, buffer, depth = 0, quote = false)
            val escalation = escalationKind
            if (!state.isAborted) {
                if (escalation != null) addPreserved(out, escalation, range) else out += buffer
            }
            previousEnd = maxOf(previousEnd, node.endOffset)
        }
        if (!state.isAborted && profile.newlineSemantics == NewlineSemantics.HardBreak) {
            addHardBreakGap(
                out,
                previousEnd,
                input.text.length,
                leading = previousEnd == 0 && out.isEmpty(),
            )
        }
        return out
    }

    private fun lowerNode(node: ASTNode, out: MutableList<Block>, depth: Int, quote: Boolean) {
        if (state.isAborted || escalationKind != null) return
        if (depth > limits.maxBlockNesting) {
            state.abort(
                MarkdownDecodeWarning.LimitExceeded(
                    MarkdownCodecLimitKind.BlockNesting,
                    limits.maxBlockNesting,
                    input.sourceRange(node, trimTrailingNewline = true),
                ),
            )
            return
        }
        when (node.type) {
            MarkdownElementTypes.PARAGRAPH -> lowerParagraph(node, out, quote)
            MarkdownElementTypes.ATX_1 -> lowerHeading(node, 1, out, quote)
            MarkdownElementTypes.ATX_2 -> lowerHeading(node, 2, out, quote)
            MarkdownElementTypes.ATX_3 -> lowerHeading(node, 3, out, quote)
            MarkdownElementTypes.ATX_4 -> lowerHeading(node, 4, out, quote)
            MarkdownElementTypes.ATX_5 -> lowerHeading(node, 5, out, quote)
            MarkdownElementTypes.ATX_6 -> lowerHeading(node, 6, out, quote)
            MarkdownElementTypes.SETEXT_1 -> lowerSetext(node, 1, out, quote)
            MarkdownElementTypes.SETEXT_2 -> lowerSetext(node, 2, out, quote)
            MarkdownTokenTypes.HORIZONTAL_RULE -> addBlock(
                out,
                Block(BlockId.generate(), BlockType.Divider, BlockContent.Empty),
                input.sourceRange(node),
            )
            MarkdownElementTypes.CODE_FENCE -> lowerFence(node, out, quote)
            MarkdownElementTypes.CODE_BLOCK -> lowerIndentedCode(node, out, quote)
            MarkdownElementTypes.BLOCK_QUOTE -> lowerQuote(node, out, depth)
            MarkdownElementTypes.UNORDERED_LIST -> lowerList(node, out, depth, ordered = false)
            MarkdownElementTypes.ORDERED_LIST -> lowerList(node, out, depth, ordered = true)
            MarkdownElementTypes.HTML_BLOCK -> lowerHtmlBlock(node, out)
            MarkdownElementTypes.LINK_DEFINITION -> lowerDefinition(node, out)
            GFMElementTypes.TABLE -> lowerTable(node, out, quote)
            else -> {
                if (node.children.isNotEmpty()) {
                    for (child in node.children) lowerNode(child, out, depth + 1, quote)
                } else if (node.endOffset > node.startOffset) {
                    lowerLiteralBlock(node, out, quote)
                }
            }
        }
    }

    private fun lowerParagraph(node: ASTNode, out: MutableList<Block>, quote: Boolean) {
        val raw = input.text.substring(node.startOffset, node.endOffset).trimEnd('\n')
        val range = input.sourceRange(node, trimTrailingNewline = true)
        recognizeFootnoteDefinition(raw)?.let {
            unsupportedBlock(out, "footnoteDefinition", range)
            return
        }
        if (recognizeMathBlock(raw)) {
            unsupportedBlock(out, "mathBlock", range)
            return
        }
        if (isStandaloneImage(node, raw)) {
            unsupportedBlock(out, "blockImage", range)
            return
        }
        val inline = MarkdownAstInlineDecoder(input, profile, limits, state, state.definitions, titledDefinitions)
            .decode(node)
        if (inline.escalationKind != null) {
            requestEscalation(inline.escalationKind)
            return
        }
        val hardBreakAtxLevel = if (profile.newlineSemantics == NewlineSemantics.HardBreak) {
            hardBreakAtxLevel(raw)
        } else null
        if (hardBreakAtxLevel != null) {
            val prefixLength = raw.indexOfFirst { it != ' ' && it != '\t' } + hardBreakAtxLevel + 1
            addTextBlock(
                out,
                BlockType.Heading(hardBreakAtxLevel),
                dropInlinePrefix(inline, prefixLength.coerceAtMost(inline.text.length)),
                range,
            )
            return
        }
        val structuralLeadingWhitespace = node.children.firstOrNull()?.type == MarkdownTokenTypes.WHITE_SPACE
        addTextBlock(
            out,
            if (quote) BlockType.Quote else BlockType.Paragraph,
            if (structuralLeadingWhitespace) trimLeadingInline(inline) else inline,
            range,
        )
    }

    private fun lowerHeading(
        node: ASTNode,
        level: Int,
        out: MutableList<Block>,
        quote: Boolean,
    ) {
        if (quote) {
            unsupportedQuoteChild(node, out, level)
            return
        }
        val content = node.children.firstOrNull {
            it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
        } ?: node
        val inline = MarkdownAstInlineDecoder(input, profile, limits, state, state.definitions, titledDefinitions)
            .decode(content)
        if (inline.escalationKind != null) {
            requestEscalation(inline.escalationKind)
            return
        }
        addTextBlock(out, BlockType.Heading(level), trimLeadingInline(inline), input.sourceRange(node, true))
    }

    private fun lowerSetext(node: ASTNode, level: Int, out: MutableList<Block>, quote: Boolean) {
        if (profile.newlineSemantics != NewlineSemantics.HardBreak) {
            lowerHeading(node, level, out, quote)
            return
        }
        val content = node.children.firstOrNull { it.type == MarkdownTokenTypes.SETEXT_CONTENT } ?: node
        val inline = MarkdownAstInlineDecoder(input, profile, limits, state, state.definitions, titledDefinitions)
            .decode(content)
        if (inline.escalationKind != null) {
            requestEscalation(inline.escalationKind)
            return
        }
        val rawContent = input.text.substring(content.startOffset, content.endOffset).trimEnd('\n')
        val atxLevel = hardBreakAtxLevel(rawContent)
        if (atxLevel != null) {
            val prefixLength = rawContent.indexOfFirst { it != ' ' && it != '\t' } + atxLevel + 1
            addTextBlock(
                out,
                BlockType.Heading(atxLevel),
                dropInlinePrefix(inline, prefixLength.coerceAtMost(inline.text.length)),
                input.sourceRange(content, true),
            )
            if (level == 2) {
                addBlock(
                    out,
                    Block(BlockId.generate(), BlockType.Divider, BlockContent.Empty),
                    input.sourceRange(node, true),
                )
            }
            return
        }
        if (level == 1) {
            val underline = input.text.substring(content.endOffset, node.endOffset).trimEnd('\n')
            val visible = trimLeadingInline(inline)
            addTextBlock(
                out,
                if (quote) BlockType.Quote else BlockType.Paragraph,
                MarkdownInlineParseResult(visible.text + underline, visible.spans, null),
                input.sourceRange(node, true),
            )
        } else {
            addTextBlock(
                out,
                if (quote) BlockType.Quote else BlockType.Paragraph,
                trimLeadingInline(inline),
                input.sourceRange(content),
            )
            addBlock(
                out,
                Block(BlockId.generate(), BlockType.Divider, BlockContent.Empty),
                input.sourceRange(node, true),
            )
        }
    }

    private fun lowerFence(node: ASTNode, out: MutableList<Block>, quote: Boolean) {
        if (quote) {
            unsupportedQuoteChild(node, out, null)
            return
        }
        val range = input.sourceRange(node, true)
        val info = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
        if (info != null && info.endOffset > info.startOffset) {
            if (preserve) {
                requestEscalation("fencedCode")
                return
            }
            state.warn(
                MarkdownDecodeWarning.DroppedAttribute(
                    "fencedCode",
                    "infoString",
                    "BlockType.Code has no language/info-string field",
                    range,
                ),
            )
        }
        val contents = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        var text = contents.joinToString("") { input.text.substring(it.startOffset, it.endOffset) }
        // Content tokens exclude EOL leaves; use the structural range between
        // the opener's EOL and closer to retain leading/trailing blank lines.
        val opener = node.children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_START }
        val closer = node.children.lastOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_END }
        if (opener != null) {
            val contentStart = input.text.indexOf('\n', opener.endOffset).let { if (it < 0) opener.endOffset else it + 1 }
            val contentEnd = closer?.startOffset ?: node.endOffset
            val lineStart = input.text.lastIndexOf('\n', opener.startOffset - 1).let { if (it < 0) 0 else it + 1 }
            val openerIndent = (opener.startOffset - lineStart).coerceAtLeast(0)
            text = input.text.substring(contentStart.coerceAtMost(contentEnd), contentEnd)
                .split('\n')
                .joinToString("\n") { line -> stripIndentColumns(line, openerIndent) }
                .removeSuffix("\n")
        }
        addBlock(out, Block(BlockId.generate(), BlockType.Code, BlockContent.Text(text)), range)
    }

    private fun lowerIndentedCode(node: ASTNode, out: MutableList<Block>, quote: Boolean) {
        if (quote) {
            unsupportedQuoteChild(node, out, null)
            return
        }
        val lines = input.text.substring(node.startOffset, node.endOffset).trimEnd('\n').split('\n')
        val text = lines.joinToString("\n") { stripIndentColumns(it, 4) }
        val type = if (profile.newlineSemantics == NewlineSemantics.HardBreak) BlockType.Paragraph else BlockType.Code
        addBlock(out, Block(BlockId.generate(), type, BlockContent.Text(text)), input.sourceRange(node, true))
    }

    private fun lowerQuote(node: ASTNode, out: MutableList<Block>, depth: Int) {
        val structural = node.children.filterNot { isTrivia(it.type) || it.type == MarkdownTokenTypes.BLOCK_QUOTE }
        for (child in structural) {
            when (child.type) {
                MarkdownElementTypes.PARAGRAPH -> lowerParagraph(child, out, quote = true)
                MarkdownElementTypes.BLOCK_QUOTE -> {
                    if (preserve) requestEscalation("blockquote") else {
                        state.warn(
                            MarkdownDecodeWarning.DroppedAttribute(
                                "blockquote",
                                "nestingDepth",
                                "nested blockquotes flatten to one level",
                                input.sourceRange(child, true),
                            ),
                        )
                        lowerQuote(child, out, depth + 1)
                    }
                }
                else -> unsupportedQuoteChild(child, out, null)
            }
            if (state.isAborted || escalationKind != null) return
        }
    }

    private fun unsupportedQuoteChild(node: ASTNode, out: MutableList<Block>, level: Int?) {
        if (preserve) {
            requestEscalation("blockquote")
            return
        }
        state.warn(
            MarkdownDecodeWarning.UnsupportedSyntax(
                "blockquote",
                "a flat Quote cannot contain this construct; it was lowered outside the quote",
                input.sourceRange(node, true),
            ),
        )
        if (level != null) lowerHeading(node, level, out, quote = false)
        else lowerNode(node, out, depth = 1, quote = false)
    }

    private fun lowerList(node: ASTNode, out: MutableList<Block>, depth: Int, ordered: Boolean) {
        val listDepth = depth.coerceAtMost(BlockAttributes.MAX_INDENTATION_LEVEL)
        if (depth > BlockAttributes.MAX_INDENTATION_LEVEL) {
            if (preserve) {
                requestEscalation("list")
                return
            }
            state.warn(
                MarkdownDecodeWarning.DroppedAttribute(
                    "list",
                    "indentationLevel",
                    "nesting depth $depth exceeds the module range and was clamped to $listDepth",
                    input.sourceRange(node, true),
                ),
            )
        }
        val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
        val firstMarker = items.firstOrNull()?.children?.firstOrNull {
            it.type == MarkdownTokenTypes.LIST_NUMBER || it.type == MarkdownTokenTypes.LIST_BULLET
        }
        val startNumber = firstMarker?.let { markerNumber(it) }
        if (ordered && startNumber != null && startNumber != 1) {
            if (preserve) {
                requestEscalation("orderedList")
                return
            }
            state.warn(
                MarkdownDecodeWarning.UnsupportedSyntax(
                    "orderedList",
                    "start value $startNumber is not representable; the list was renumbered from 1",
                    input.sourceRange(node, true),
                ),
            )
        }

        var number = 1
        for (item in items) {
            val children = item.children.filterNot {
                isTrivia(it.type) || it.type == MarkdownTokenTypes.LIST_BULLET ||
                    it.type == MarkdownTokenTypes.LIST_NUMBER || it.type == GFMTokenTypes.CHECK_BOX
            }
            val firstParagraph = children.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH }
            val checkbox = item.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
            val type = when {
                checkbox != null -> {
                    val raw = input.text.substring(checkbox.startOffset, checkbox.endOffset)
                    BlockType.Todo(raw.length > 1 && raw[1] != ' ')
                }
                ordered -> BlockType.NumberedList(number)
                else -> BlockType.BulletList
            }
            number++
            val inline = if (firstParagraph != null) {
                MarkdownAstInlineDecoder(input, profile, limits, state, state.definitions, titledDefinitions)
                    .decode(firstParagraph)
            } else MarkdownInlineParseResult("", emptyList(), null)
            if (inline.escalationKind != null) {
                requestEscalation(inline.escalationKind)
                return
            }
            addBlock(
                out,
                Block(
                    BlockId.generate(),
                    type,
                    BlockContent.Text(inline.text, inline.spans),
                    BlockAttributes(indentationLevel = listDepth),
                ),
                input.sourceRange(item, true),
                spansAlreadyCounted = true,
            )

            for (child in children) {
                if (child === firstParagraph) continue
                if (child.type == MarkdownElementTypes.UNORDERED_LIST || child.type == MarkdownElementTypes.ORDERED_LIST) {
                    lowerList(child, out, depth + 1, child.type == MarkdownElementTypes.ORDERED_LIST)
                } else {
                    if (preserve) {
                        requestEscalation("listItem")
                        return
                    }
                    state.warn(
                        MarkdownDecodeWarning.UnsupportedSyntax(
                            "listItem",
                            "multi-block list-item content cannot be expressed in the outline; it was lowered as a separate block",
                            input.sourceRange(child, true),
                        ),
                    )
                    lowerNode(child, out, depth + 1, quote = false)
                }
                if (state.isAborted || escalationKind != null) return
            }
        }
    }

    private fun lowerTable(node: ASTNode, out: MutableList<Block>, quote: Boolean) {
        val range = input.sourceRange(node, true)
        if (preserve) {
            requestEscalation("pipeTable")
            return
        }
        state.warn(
            MarkdownDecodeWarning.UnsupportedSyntax(
                "pipeTable",
                "recognized construct decodes through ordinary syntaxes with degraded content",
                range,
            ),
        )
        val rows = node.children.filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
        for (row in rows) {
            val cells = row.children.filter { it.type == GFMTokenTypes.CELL }
            val rendered = cells.map {
                MarkdownAstInlineDecoder(input, profile, limits, state, state.definitions, titledDefinitions)
                    .decode(it)
            }
            val text = rendered.joinToString(" | ") { it.text.trim() }
            addBlock(
                out,
                Block(BlockId.generate(), if (quote) BlockType.Quote else BlockType.Paragraph, BlockContent.Text(text)),
                input.sourceRange(row, true),
            )
        }
    }

    private fun lowerDefinition(node: ASTNode, out: MutableList<Block>) {
        val title = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TITLE }
        if (title != null && preserve) {
            addPreserved(out, "linkReferenceDefinition", input.sourceRange(node, true))
        }
    }

    private fun lowerHtmlBlock(node: ASTNode, out: MutableList<Block>) {
        val range = input.sourceRange(node, true)
        val raw = source.slice(range)
        val firstLine = raw.substringBefore('\n').substringBefore('\r')
        if (!MarkdownHtmlBridge.looksLikeHtmlBlockStart(firstLine)) {
            addBlock(out, Block.paragraph(raw), range)
            return
        }
        when (val policy = profile.htmlInMarkdown) {
            is HtmlInMarkdown.Bridge -> {
                val result = MarkdownHtmlBridge.bridgeBlock(raw, policy.htmlProfile, ::reparseHtmlLeaf)
                if (result.blocks.isEmpty()) {
                    if (preserve) addPreservedHtml(out, range, raw)
                    else state.warn(MarkdownDecodeWarning.UnsupportedSyntax("html", "the HTML produced no editable content and was dropped", range))
                    return
                }
                val destructive = result.warnings.filter { MarkdownHtmlBridge.isDestructiveHtmlWarning(it) }
                if (destructive.isEmpty()) state.warn(MarkdownDecodeWarning.HtmlBridged(range))
                else destructive.forEach { warning ->
                    state.warn(
                        MarkdownDecodeWarning.HtmlStripped(
                            MarkdownHtmlBridge.htmlWarningTag(warning),
                            rebase(range, warning.charOffset),
                        ),
                    )
                }
                result.blocks.forEach { addBlock(out, it.copy(id = BlockId.generate()), range) }
            }
            HtmlInMarkdown.Preserve -> addPreservedHtml(out, range, raw)
            HtmlInMarkdown.WarnAndStrip, HtmlInMarkdown.Strip -> {
                val result = MarkdownHtmlBridge.bridgeBlock(raw, HtmlProfile.Default, ::reparseHtmlLeaf)
                state.warn(MarkdownDecodeWarning.HtmlStripped(null, range))
                result.blocks.forEach { block ->
                    val content = block.content
                    val stripped = if (content is BlockContent.Text) block.copy(
                        id = BlockId.generate(),
                        content = BlockContent.Text(content.text),
                    ) else block.copy(id = BlockId.generate())
                    addBlock(out, stripped, range)
                }
            }
        }
    }

    private fun lowerLiteralBlock(node: ASTNode, out: MutableList<Block>, quote: Boolean) {
        val raw = input.text.substring(node.startOffset, node.endOffset).trimEnd('\n')
        addBlock(
            out,
            Block(BlockId.generate(), if (quote) BlockType.Quote else BlockType.Paragraph, BlockContent.Text(raw)),
            input.sourceRange(node, true),
        )
    }

    private fun addTextBlock(
        out: MutableList<Block>,
        type: BlockType,
        inline: MarkdownInlineParseResult,
        range: MarkdownSourceRange,
    ) {
        addBlock(
            out,
            Block(BlockId.generate(), type, BlockContent.Text(inline.text, if (type.supportsSpans) inline.spans else emptyList())),
            range,
            spansAlreadyCounted = true,
        )
    }

    private fun addBlock(
        out: MutableList<Block>,
        block: Block,
        range: MarkdownSourceRange,
        spansAlreadyCounted: Boolean = false,
    ) {
        if (!state.noteBlock(range)) return
        if (!spansAlreadyCounted) {
            val count = (block.content as? BlockContent.Text)?.spans?.size ?: 0
            if (!state.noteSpans(count, range)) return
        }
        out += block
    }

    private fun unsupportedBlock(out: MutableList<Block>, kind: String, range: MarkdownSourceRange) {
        if (preserve) addPreserved(out, kind, range)
        else state.warn(
            MarkdownDecodeWarning.UnsupportedSyntax(
                kind,
                "recognized construct decodes through ordinary syntaxes with degraded content",
                range,
            ),
        )
    }

    private fun addPreserved(out: MutableList<Block>, kind: String, range: MarkdownSourceRange) {
        state.warn(MarkdownDecodeWarning.PreservedSyntax(kind, range))
        addBlock(
            out,
            Block(
                BlockId.generate(),
                UnknownBlockType(MARKDOWN_PRESERVED_TYPE_ID, rawTypeJsonForAst(MARKDOWN_PRESERVED_TYPE_ID)),
                BlockContent.Custom(
                    MARKDOWN_PRESERVED_TYPE_ID,
                    mapOf("kind" to kind, "rawMarkdown" to source.slice(range)),
                ),
            ),
            range,
        )
    }

    private fun addPreservedHtml(out: MutableList<Block>, range: MarkdownSourceRange, raw: String) {
        state.warn(MarkdownDecodeWarning.PreservedSyntax("html", range))
        addBlock(
            out,
            Block(
                BlockId.generate(),
                UnknownBlockType(MARKDOWN_PRESERVED_HTML_TYPE_ID, rawTypeJsonForAst(MARKDOWN_PRESERVED_HTML_TYPE_ID)),
                BlockContent.Custom(
                    MARKDOWN_PRESERVED_HTML_TYPE_ID,
                    mapOf("kind" to "html", "rawMarkdown" to raw),
                ),
            ),
            range,
        )
    }

    private fun requestEscalation(kind: String) {
        if (escalationKind == null) escalationKind = kind
    }

    private fun collectDefinitions(root: ASTNode) {
        data class Pending(val node: ASTNode, val depth: Int)

        val pending = ArrayList<Pending>()
        pending += Pending(root, 0)
        while (pending.isNotEmpty() && !state.isAborted) {
            val (node, depth) = pending.removeAt(pending.lastIndex)
            if (depth > limits.maxBlockNesting) {
                state.abort(
                    MarkdownDecodeWarning.LimitExceeded(
                        MarkdownCodecLimitKind.BlockNesting,
                        limits.maxBlockNesting,
                        input.sourceRange(node, trimTrailingNewline = true),
                    ),
                )
                return
            }
            if (node.type == MarkdownElementTypes.LINK_DEFINITION) {
                collectDefinition(node)
            } else {
                for (index in node.children.lastIndex downTo 0) {
                    val child = node.children[index]
                    pending += Pending(child, depth + blockNestingIncrement(child.type))
                }
            }
        }
    }

    private fun collectDefinition(node: ASTNode) {
        val labelNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL } ?: return
        val destinationNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION } ?: return
        val titleNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TITLE }
        val label = stripBrackets(input.text.substring(labelNode.startOffset, labelNode.endOffset))
        val destination = decodeDestination(input.text.substring(destinationNode.startOffset, destinationNode.endOffset))
        val title = titleNode?.let { stripTitle(input.text.substring(it.startOffset, it.endOffset)) }
        state.registerDefinition(label, MarkdownLinkReferenceDefinition(destination, title), input.sourceRange(node, true))
        if (title != null) {
            titledDefinitions += MarkdownDefinitionTable.normalizeLabel(label)
            if (!preserve) {
                state.warn(
                    MarkdownDecodeWarning.DroppedAttribute(
                        "linkReferenceDefinition",
                        "title",
                        "link titles have no editor representation",
                        input.sourceRange(node, true),
                    ),
                )
            }
        }
    }

    private fun reparseHtmlLeaf(text: String): InlineFragment {
        val nestedInput = MarkdownParseInput.of(text)
        val nestedRoot = parseMarkdownTree(nestedInput.text)
        val container = nestedRoot.children.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH } ?: nestedRoot
        val nestedState = MarkdownParseState(limits)
        val parsed = MarkdownAstInlineDecoder(
            nestedInput,
            profile,
            limits,
            nestedState,
            MarkdownDefinitionTable(),
            emptySet(),
        ).decode(container)
        return InlineFragment(parsed.text, parsed.spans)
    }

    private fun addHardBreakGap(out: MutableList<Block>, start: Int, end: Int, leading: Boolean) {
        if (end <= start) return
        val newlines = input.text.substring(start, end).count { it == '\n' }
        val blankLines = if (leading) newlines else (newlines - 1).coerceAtLeast(0)
        repeat(blankLines) {
            addBlock(
                out,
                Block.paragraph(),
                input.sourceRange(start, end),
            )
        }
    }

    private fun rebase(range: MarkdownSourceRange, relative: Int): MarkdownSourceRange {
        val start = (range.start + relative).coerceIn(range.start, range.endExclusive)
        return MarkdownSourceRange(start, (start + 1).coerceAtMost(range.endExclusive))
    }

    private fun markerNumber(marker: ASTNode): Int? =
        input.text.substring(marker.startOffset, marker.endOffset)
            .trimStart(' ', '\t')
            .takeWhile { it.isDigit() }
            .toIntOrNull()

    private fun trimLeadingInline(inline: MarkdownInlineParseResult): MarkdownInlineParseResult {
        var count = 0
        while (count < inline.text.length && (inline.text[count] == ' ' || inline.text[count] == '\t')) count++
        if (count == 0) return inline
        val shifted = inline.spans.mapNotNull { span ->
            val start = (span.start - count).coerceAtLeast(0)
            val end = span.end - count
            if (end > start) TextSpan(start, end, span.style) else null
        }
        return MarkdownInlineParseResult(inline.text.substring(count), shifted, inline.escalationKind)
    }

    private fun dropInlinePrefix(inline: MarkdownInlineParseResult, count: Int): MarkdownInlineParseResult {
        if (count <= 0) return inline
        val shifted = inline.spans.mapNotNull { span ->
            val start = (span.start - count).coerceAtLeast(0)
            val end = span.end - count
            if (end > start) TextSpan(start, end, span.style) else null
        }
        return MarkdownInlineParseResult(inline.text.drop(count), shifted, inline.escalationKind)
    }
}

/** Inline AST lowering shared by decode and encode-side ambiguity verification. */
internal class MarkdownAstInlineDecoder(
    private val input: MarkdownParseInput,
    private val profile: MarkdownProfile,
    private val limits: MarkdownCodecLimits,
    private val state: MarkdownParseState,
    private val definitions: MarkdownDefinitionTable,
    private val titledDefinitions: Set<String>,
) {
    private val text = StringBuilder()
    private val spans = ArrayList<TextSpan>()
    private var escalation: String? = null
    private var hardBreakPendingEol = false
    private var lastEmissionWasCodeSpan = false

    fun decode(node: ASTNode): MarkdownInlineParseResult {
        preflightInlineMath(node)
        if (escalation != null) return MarkdownInlineParseResult("", emptyList(), escalation)
        renderChildren(node.children.ifEmpty { listOf(node) })
        if (!lastEmissionWasCodeSpan) trimTrailingBlanks()
        if (escalation != null) return MarkdownInlineParseResult("", emptyList(), escalation)
        val normalized = SpanAlgorithms.normalize(spans, text.length)
        val range = input.sourceRange(node.startOffset, node.endOffset)
        if (normalized.size > limits.maxSpansPerBlock) {
            state.abort(MarkdownDecodeWarning.LimitExceeded(MarkdownCodecLimitKind.SpansPerBlock, limits.maxSpansPerBlock, range))
            return MarkdownInlineParseResult("", emptyList(), null)
        }
        if (!state.noteSpans(normalized.size, range)) return MarkdownInlineParseResult("", emptyList(), null)
        return MarkdownInlineParseResult(text.toString(), normalized, null)
    }

    private fun preflightInlineMath(node: ASTNode) {
        val excluded = ArrayList<IntRange>()
        val pending = ArrayList<ASTNode>()
        pending += node
        while (pending.isNotEmpty()) {
            val current = pending.removeAt(pending.lastIndex)
            if (current.type == MarkdownElementTypes.CODE_SPAN || current.type == MarkdownElementTypes.IMAGE) {
                excluded += current.startOffset until current.endOffset
            } else {
                pending.addAll(current.children)
            }
        }
        excluded.sortBy { it.first }
        var excludedIndex = 0
        var offset = node.startOffset
        while (offset < node.endOffset) {
            while (excludedIndex < excluded.size && excluded[excludedIndex].last < offset) excludedIndex++
            val excludedRange = excluded.getOrNull(excludedIndex)
            if (excludedRange != null && offset in excludedRange) {
                offset = excludedRange.last + 1
                continue
            }
            if (input.text[offset] != '$') {
                offset++
                continue
            }
            val end = recognizeInlineMathEnd(input.text, offset)
            if (end <= offset || end > node.endOffset) {
                offset++
                continue
            }
            val range = input.sourceRange(offset, end)
            if (profile.unsupportedSyntax == UnsupportedSyntax.Preserve) {
                escalation = "inlineMath"
                return
            }
            state.warn(
                MarkdownDecodeWarning.UnsupportedSyntax(
                    "inlineMath",
                    "inline math has no editor representation; the expression was kept as literal text",
                    range,
                ),
            )
            offset = end
        }
    }

    private fun renderChildren(children: List<ASTNode>) {
        var index = 0
        while (index < children.size && escalation == null && !state.isAborted) {
            val child = children[index]
            if (
                child.type == MarkdownTokenTypes.LT &&
                children.getOrNull(index + 1)?.type == MarkdownTokenTypes.EMAIL_AUTOLINK &&
                children.getOrNull(index + 2)?.type == MarkdownTokenTypes.GT
            ) {
                renderEmailToken(children[index + 1])
                index += 3
                continue
            }
            if (child.type == MarkdownTokenTypes.HTML_TAG && input.text.startsWith("<", child.startOffset)) {
                val island = MarkdownHtmlBridge.matchInlinePairedTag(input.text, child.startOffset)
                if (island != null) {
                    renderHtmlIsland(child.startOffset, island)
                    index++
                    while (index < children.size && children[index].startOffset < island.endExclusive) index++
                    continue
                }
            }
            render(child)
            index++
        }
    }

    private fun render(node: ASTNode) {
        when (node.type) {
            MarkdownElementTypes.EMPH -> styled(node, SpanStyle.Italic, MarkdownTokenTypes.EMPH)
            MarkdownElementTypes.STRONG -> styled(node, SpanStyle.Bold, MarkdownTokenTypes.EMPH)
            GFMElementTypes.STRIKETHROUGH -> styled(node, SpanStyle.StrikeThrough, GFMTokenTypes.TILDE)
            MarkdownElementTypes.CODE_SPAN -> renderCodeSpan(node)
            MarkdownElementTypes.INLINE_LINK -> renderInlineLink(node)
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK,
            -> renderReferenceLink(node)
            MarkdownElementTypes.IMAGE -> renderImage(node)
            MarkdownElementTypes.AUTOLINK -> renderAutolink(node)
            GFMElementTypes.INLINE_MATH,
            GFMElementTypes.BLOCK_MATH,
            -> unsupportedInline("inlineMath", node)
            MarkdownTokenTypes.HARD_LINE_BREAK -> {
                trimTrailingBlanks()
                text.append('\n')
                lastEmissionWasCodeSpan = false
                hardBreakPendingEol = true
            }
            MarkdownTokenTypes.EOL -> {
                if (hardBreakPendingEol) hardBreakPendingEol = false
                else {
                    trimTrailingBlanks()
                    text.append(
                        if (profile.newlineSemantics == NewlineSemantics.HardBreak || profile.softBreak == SoftBreak.LineBreak) '\n' else ' ',
                    )
                    lastEmissionWasCodeSpan = false
                }
            }
            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.WHITE_SPACE,
            MarkdownTokenTypes.ATX_CONTENT,
            MarkdownTokenTypes.SETEXT_CONTENT,
            GFMTokenTypes.CELL,
            -> if (node.children.isEmpty()) appendPlain(node) else renderChildren(node.children)
            MarkdownTokenTypes.EMAIL_AUTOLINK -> renderEmailToken(node)
            GFMTokenTypes.GFM_AUTOLINK -> appendRaw(node)
            MarkdownTokenTypes.HTML_TAG -> appendRaw(node)
            else -> if (node.children.isEmpty()) appendRaw(node) else renderChildren(node.children)
        }
    }

    private fun styled(node: ASTNode, style: SpanStyle, markerType: IElementType) {
        val start = text.length
        renderChildren(node.children.filterNot { it.type == markerType })
        if (text.length > start) spans += TextSpan(start, text.length, style)
    }

    private fun renderCodeSpan(node: ASTNode) {
        val ticks = node.children.filter { it.type == MarkdownTokenTypes.BACKTICK }
        if (ticks.size < 2) {
            appendRaw(node)
            return
        }
        var content = input.text.substring(ticks.first().endOffset, ticks.last().startOffset).replace('\n', ' ')
        if (content.length >= 2 && content.first() == ' ' && content.last() == ' ' && content.any { it != ' ' }) {
            content = content.substring(1, content.length - 1)
        }
        val start = text.length
        text.append(content)
        lastEmissionWasCodeSpan = true
        if (text.length > start) spans += TextSpan(start, text.length, SpanStyle.InlineCode)
    }

    private fun renderInlineLink(node: ASTNode) {
        val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        if (label != null && renderNestedInlineLinkCompatibility(node, label)) return
        // JetBrains Markdown 0.7.7 represents an angle-form destination as an
        // AUTOLINK child, while a bare destination is LINK_DESTINATION.
        val destinationNode = node.children.firstOrNull {
            it.type == MarkdownElementTypes.LINK_DESTINATION || it.type == MarkdownElementTypes.AUTOLINK
        }
        if (label == null || destinationNode == null) {
            appendRaw(node)
            return
        }
        val title = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TITLE }
        if (title != null) {
            if (profile.unsupportedSyntax == UnsupportedSyntax.Preserve) {
                escalation = "linkTitle"
                return
            }
            state.warn(
                MarkdownDecodeWarning.DroppedAttribute(
                    "link",
                    "title",
                    "link titles have no editor representation",
                    input.sourceRange(title.startOffset, title.endOffset),
                ),
            )
        }
        val destination = decodeDestination(input.text.substring(destinationNode.startOffset, destinationNode.endOffset))
        if (destination.isBlank()) {
            appendRaw(node)
            return
        }
        val start = text.length
        renderLinkLabel(label)
        if (text.length > start) spans += TextSpan(start, text.length, SpanStyle.Link(destination))
    }

    /**
     * CommonMark forbids links inside links. JetBrains 0.7.7 instead wraps
     * `[a [b](u2)](u1)` in the outer link and tokenizes the inner link as a
     * short reference plus literal destination. Recover the CommonMark result
     * from that distinctive AST shape without maintaining a general parser.
     */
    private fun renderNestedInlineLinkCompatibility(node: ASTNode, label: ASTNode): Boolean {
        val children = label.children
        val nestedIndex = children.indexOfFirst { it.type == MarkdownElementTypes.SHORT_REFERENCE_LINK }
        if (nestedIndex < 0 || children.getOrNull(nestedIndex + 1)?.type != MarkdownTokenTypes.LPAREN) return false
        val closeIndex = (nestedIndex + 2 until children.size).firstOrNull {
            children[it].type == MarkdownTokenTypes.RPAREN
        } ?: return false
        val nested = children[nestedIndex]
        val destinationStart = children[nestedIndex + 1].endOffset
        val destinationEnd = children[closeIndex].startOffset
        if (destinationEnd <= destinationStart) return false
        val destination = decodeDestination(input.text.substring(destinationStart, destinationEnd).trim())
        if (destination.isBlank()) return false

        text.append('[')
        renderChildren(children.subList(1, nestedIndex))
        val start = text.length
        val nestedLabel = nested.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL } ?: nested
        renderLinkLabel(nestedLabel)
        if (text.length > start) spans += TextSpan(start, text.length, SpanStyle.Link(destination))
        renderChildren(children.subList(closeIndex + 1, (children.size - 1).coerceAtLeast(closeIndex + 1)))
        val outerClose = children.lastOrNull { it.type == MarkdownTokenTypes.RBRACKET }?.startOffset ?: label.endOffset
        text.append(input.text, outerClose, node.endOffset)
        lastEmissionWasCodeSpan = false
        return true
    }

    private fun renderReferenceLink(node: ASTNode) {
        val raw = input.text.substring(node.startOffset, node.endOffset)
        if (isFootnoteReference(raw)) {
            unsupportedInline("footnoteRef", node)
            return
        }
        val labelNode = node.children.lastOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
            ?: node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        val rawLabel = labelNode?.let { stripBrackets(input.text.substring(it.startOffset, it.endOffset)) }
            ?: raw.substringAfter('[').substringBefore(']')
        val definition = definitions.lookup(rawLabel)
        if (definition == null) {
            appendRaw(node)
            return
        }
        if (MarkdownDefinitionTable.normalizeLabel(rawLabel) in titledDefinitions) {
            if (profile.unsupportedSyntax == UnsupportedSyntax.Preserve) {
                escalation = "linkReferenceTitle"
                return
            }
        }
        val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
            ?: node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
        val start = text.length
        if (linkText != null) renderLinkLabel(linkText) else text.append(rawLabel)
        if (definition.destination.isNotBlank() && text.length > start) {
            spans += TextSpan(start, text.length, SpanStyle.Link(definition.destination))
        }
    }

    private fun renderLinkLabel(node: ASTNode) {
        val filtered = node.children.filterNot {
            it.type == MarkdownTokenTypes.LBRACKET || it.type == MarkdownTokenTypes.RBRACKET
        }
        if (filtered.isEmpty()) {
            text.append(stripBrackets(input.text.substring(node.startOffset, node.endOffset)))
        } else renderChildren(filtered)
    }

    private fun renderImage(node: ASTNode) {
        if (profile.unsupportedSyntax == UnsupportedSyntax.Preserve) {
            escalation = "inlineImage"
            return
        }
        state.warn(
            MarkdownDecodeWarning.UnsupportedSyntax(
                "inlineImage",
                "images have no editor representation; the alt text was retained",
                input.sourceRange(node.startOffset, node.endOffset),
            ),
        )
        val link = node.children.firstOrNull {
            it.type == MarkdownElementTypes.INLINE_LINK || it.type == MarkdownElementTypes.FULL_REFERENCE_LINK ||
                it.type == MarkdownElementTypes.SHORT_REFERENCE_LINK
        }
        val label = link?.children?.firstOrNull {
            it.type == MarkdownElementTypes.LINK_TEXT || it.type == MarkdownElementTypes.LINK_LABEL
        }
        if (label != null) renderLinkLabel(label)
    }

    private fun renderAutolink(node: ASTNode) {
        val token = node.children.firstOrNull {
            it.type == MarkdownTokenTypes.AUTOLINK || it.type == MarkdownTokenTypes.EMAIL_AUTOLINK
        }
        if (token == null) {
            appendRaw(node)
            return
        }
        if (token.type == MarkdownTokenTypes.EMAIL_AUTOLINK) renderEmailToken(token)
        else {
            val value = input.text.substring(token.startOffset, token.endOffset)
            appendLinkText(value, value)
        }
    }

    private fun renderEmailToken(node: ASTNode) {
        val value = input.text.substring(node.startOffset, node.endOffset)
        appendLinkText(value, "mailto:$value")
    }

    private fun appendLinkText(value: String, target: String) {
        val start = text.length
        text.append(value)
        lastEmissionWasCodeSpan = false
        if (value.isNotEmpty()) spans += TextSpan(start, text.length, SpanStyle.Link(target))
    }

    private fun unsupportedInline(kind: String, node: ASTNode) {
        if (profile.unsupportedSyntax == UnsupportedSyntax.Preserve) {
            escalation = kind
            return
        }
        state.warn(
            MarkdownDecodeWarning.UnsupportedSyntax(
                kind,
                "$kind has no editor representation; source text was retained",
                input.sourceRange(node.startOffset, node.endOffset),
            ),
        )
        appendRaw(node)
    }

    private fun renderHtmlIsland(startOffset: Int, island: MarkdownHtmlBridge.InlineIsland) {
        val range = input.sourceRange(startOffset, island.endExclusive)
        val raw = input.text.substring(startOffset, island.endExclusive)
        val inner = input.text.substring(island.innerStart, island.innerEnd)
        when (val policy = profile.htmlInMarkdown) {
            is HtmlInMarkdown.Bridge -> {
                val result = MarkdownHtmlBridge.bridgeInline(raw, policy.htmlProfile) { leaf ->
                    val nestedInput = MarkdownParseInput.of(leaf)
                    val nestedRoot = parseMarkdownTree(nestedInput.text)
                    val container = nestedRoot.children.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH } ?: nestedRoot
                    val parsed = MarkdownAstInlineDecoder(
                        nestedInput,
                        profile,
                        limits,
                        MarkdownParseState(limits),
                        MarkdownDefinitionTable(),
                        emptySet(),
                    ).decode(container)
                    InlineFragment(parsed.text, parsed.spans)
                }
                val fragment = result.fragment
                if (fragment.text.isEmpty() && fragment.spans.isEmpty()) {
                    if (profile.unsupportedSyntax == UnsupportedSyntax.Preserve) escalation = "htmlInline"
                    else state.warn(MarkdownDecodeWarning.HtmlStripped(island.tagName, range))
                    return
                }
                val destructive = result.warnings.filter { MarkdownHtmlBridge.isDestructiveHtmlWarning(it) }
                if (destructive.isEmpty()) state.warn(MarkdownDecodeWarning.HtmlBridged(range))
                else destructive.forEach { warning ->
                    val at = input.originalOffset((startOffset + warning.charOffset).coerceAtMost(island.endExclusive))
                    state.warn(
                        MarkdownDecodeWarning.HtmlStripped(
                            MarkdownHtmlBridge.htmlWarningTag(warning),
                            MarkdownSourceRange(at, (at + 1).coerceAtMost(range.endExclusive)),
                        ),
                    )
                }
                val base = text.length
                text.append(fragment.text)
                lastEmissionWasCodeSpan = false
                fragment.spans.forEach { spans += TextSpan(it.start + base, it.end + base, it.style) }
            }
            HtmlInMarkdown.Preserve -> escalation = "htmlInline"
            HtmlInMarkdown.WarnAndStrip, HtmlInMarkdown.Strip -> {
                state.warn(MarkdownDecodeWarning.HtmlStripped(island.tagName, range))
                val nestedInput = MarkdownParseInput.of(inner)
                val nestedRoot = parseMarkdownTree(nestedInput.text)
                val container = nestedRoot.children.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH } ?: nestedRoot
                val parsed = MarkdownAstInlineDecoder(
                    nestedInput,
                    profile,
                    limits,
                    MarkdownParseState(limits),
                    MarkdownDefinitionTable(),
                    emptySet(),
                ).decode(container)
                val base = text.length
                text.append(parsed.text)
                lastEmissionWasCodeSpan = false
                parsed.spans.forEach { spans += TextSpan(it.start + base, it.end + base, it.style) }
            }
        }
    }

    private fun appendPlain(node: ASTNode) {
        val raw = input.text.substring(node.startOffset, node.endOffset)
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length && raw[index + 1].isMarkdownPunctuationAst()) {
                text.append(raw[index + 1])
                lastEmissionWasCodeSpan = false
                index += 2
                continue
            }
            if (char == '&' && profile.entityDecode != EntityDecode.None) {
                val entity = MarkdownEntities.matchAt(raw, index)
                if (entity != null) {
                    if (entity.replacement != null) text.append(entity.replacement)
                    else {
                        text.append(raw, index, index + entity.length)
                        entity.name?.let {
                            state.warn(
                                MarkdownDecodeWarning.UnsupportedEntity(
                                    it,
                                    input.sourceRange(node.startOffset + index, node.startOffset + index + entity.length),
                                ),
                            )
                        }
                    }
                    index += entity.length
                    lastEmissionWasCodeSpan = false
                    continue
                }
            }
            text.append(char)
            lastEmissionWasCodeSpan = false
            index++
        }
    }

    private fun appendRaw(node: ASTNode) {
        text.append(input.text, node.startOffset, node.endOffset)
        lastEmissionWasCodeSpan = false
    }

    private fun trimTrailingBlanks() {
        while (text.isNotEmpty() && (text.last() == ' ' || text.last() == '\t')) text.setLength(text.length - 1)
    }
}

private data class RecognizedRange(val endExclusive: Int)

private fun recognizeFrontMatter(text: String): RecognizedRange? {
    val firstEnd = text.indexOf('\n').let { if (it < 0) text.length else it }
    if (text.substring(0, firstEnd).trimEnd(' ', '\t') != "---") return null
    var start = if (firstEnd < text.length) firstEnd + 1 else text.length
    while (start < text.length) {
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val line = text.substring(start, end).trimEnd(' ', '\t')
        if (line.isEmpty()) return null
        if (line == "---" || line == "...") return RecognizedRange(end)
        start = if (end < text.length) end + 1 else text.length
    }
    return null
}

private fun recognizeMathBlock(raw: String): Boolean {
    val lines = raw.split('\n')
    val first = lines.firstOrNull()?.trim(' ', '\t') ?: return false
    if (!first.startsWith("$$")) return false
    if (first.length >= 4 && first.endsWith("$$")) return true
    if (first != "$$") return false
    for (line in lines.drop(1)) {
        val trimmed = line.trimEnd(' ', '\t')
        if (trimmed.isEmpty()) return false
        if (trimmed.endsWith("$$")) return true
    }
    return false
}

/** Cascade's conservative single-dollar inline-math recognizer. */
private fun recognizeInlineMathEnd(text: String, start: Int): Int {
    if (start + 1 >= text.length || text[start] != '$') return -1
    val first = text[start + 1]
    if (first == '$' || first == ' ' || first == '\t' || first == '\n') return -1
    // An escaped opener is literal.
    var backslashes = 0
    var before = start - 1
    while (before >= 0 && text[before] == '\\') {
        backslashes++
        before--
    }
    if (backslashes % 2 == 1) return -1
    var index = start + 2
    while (index < text.length) {
        when (text[index]) {
            '\n' -> return -1
            '\\' -> index += 2
            '$' -> {
                val previous = text[index - 1]
                if (previous == ' ' || previous == '\t') return -1
                if (index + 1 < text.length && text[index + 1].isDigit()) return -1
                return index + 1
            }
            else -> index++
        }
    }
    return -1
}

private fun recognizeFootnoteDefinition(raw: String): String? {
    val first = raw.lineSequence().firstOrNull()?.trimStart(' ', '\t') ?: return null
    if (!first.startsWith("[^")) return null
    val close = first.indexOf("]:", 2)
    if (close < 3) return null
    val label = first.substring(2, close)
    return label.takeIf { it.isNotEmpty() && it.none { ch -> ch == '[' || ch == '^' || ch.isWhitespace() } }
}

private fun hardBreakAtxLevel(raw: String): Int? {
    if ('\n' in raw) return null
    var index = 0
    while (index < raw.length && index < 3 && raw[index] == ' ') index++
    val markerStart = index
    while (index < raw.length && raw[index] == '#' && index - markerStart < 6) index++
    val level = index - markerStart
    if (level !in 1..6) return null
    if (index >= raw.length || (raw[index] != ' ' && raw[index] != '\t')) return null
    return level
}

private fun isFootnoteReference(raw: String): Boolean {
    if (!raw.startsWith("[^") || !raw.endsWith(']')) return false
    val label = raw.substring(2, raw.length - 1)
    return label.isNotEmpty() && label.none { it == '[' || it == '^' || it.isWhitespace() }
}

private fun isStandaloneImage(node: ASTNode, raw: String): Boolean {
    if (!raw.trim().startsWith("![") || !raw.trim().endsWith(')')) return false
    val structural = node.children.filterNot { isTrivia(it.type) }
    return structural.size == 1 && structural.single().type == MarkdownElementTypes.IMAGE
}

private fun isTrivia(type: IElementType): Boolean =
    type == MarkdownTokenTypes.EOL || type == MarkdownTokenTypes.WHITE_SPACE

private fun blockNestingIncrement(type: IElementType): Int = when (type) {
    MarkdownElementTypes.BLOCK_QUOTE,
    MarkdownElementTypes.ORDERED_LIST,
    MarkdownElementTypes.UNORDERED_LIST,
    MarkdownElementTypes.LIST_ITEM,
    -> 1
    else -> 0
}

private fun stripBrackets(raw: String): String =
    if (raw.length >= 2 && raw.first() == '[' && raw.last() == ']') raw.substring(1, raw.length - 1) else raw

private fun stripTitle(raw: String): String {
    if (raw.length < 2) return raw
    val first = raw.first()
    val last = raw.last()
    return if ((first == '"' && last == '"') || (first == '\'' && last == '\'') || (first == '(' && last == ')')) {
        raw.substring(1, raw.length - 1)
    } else raw
}

private fun decodeDestination(raw: String): String {
    var value = raw
    if (value.length >= 2 && value.first() == '<' && value.last() == '>') value = value.substring(1, value.length - 1)
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '\\' && index + 1 < value.length && value[index + 1].isMarkdownPunctuationAst()) {
            out.append(value[index + 1])
            index += 2
        } else {
            out.append(value[index])
            index++
        }
    }
    return MarkdownEntities.decode(out.toString(), EntityDecode.Standard)
}

private fun stripIndentColumns(line: String, columns: Int): String {
    var index = 0
    var consumed = 0
    while (index < line.length && consumed < columns) {
        when (line[index]) {
            ' ' -> {
                consumed++
                index++
            }
            '\t' -> {
                consumed += 4 - (consumed % 4)
                index++
            }
            else -> break
        }
    }
    return line.substring(index)
}

/** Minimal JSON escaping for unknown custom-block type metadata. */
private fun rawTypeJsonForAst(typeId: String): String {
    val escaped = StringBuilder(typeId.length + 2)
    for (char in typeId) {
        when (char) {
            '\\' -> escaped.append("\\\\")
            '"' -> escaped.append("\\\"")
            '\n' -> escaped.append("\\n")
            '\r' -> escaped.append("\\r")
            '\t' -> escaped.append("\\t")
            else -> if (char.code < 0x20) escaped.append("\\u").append(char.code.toString(16).padStart(4, '0')) else escaped.append(char)
        }
    }
    return "{\"typeId\":\"$escaped\"}"
}

private fun Char.isMarkdownPunctuationAst(): Boolean = when (this) {
    '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
    ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '|', '}', '~',
    -> true
    else -> false
}
