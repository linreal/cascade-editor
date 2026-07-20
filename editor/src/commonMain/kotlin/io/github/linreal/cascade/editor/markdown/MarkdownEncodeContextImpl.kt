package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent

/**
 * Concrete [MarkdownEncodeContext] created once per encode by
 * [MarkdownEncodeEngine]. Warnings flow into the engine's bounded collector.
 *
 * Inline emission routes through [MarkdownInlineRenderer]: the
 * sweep-line walker over span boundaries with encode-side reparse
 * verification. The structured line-sequence contract holds — one entry per
 * physical output line, hard-break markers already attached to non-final
 * lines — so prefix-style encoders transform complete lines.
 */
internal class MarkdownEncodeContextImpl(
    private val profile: MarkdownProfile,
    private val limits: MarkdownCodecLimits,
    private val warningSink: (MarkdownEncodeWarning) -> Unit,
) : MarkdownEncodeContext() {

    private val inlineRenderer = MarkdownInlineRenderer(
        profile = profile,
        limits = limits,
        warningSink = warningSink,
    )

    override val newlineSemantics: NewlineSemantics get() = profile.newlineSemantics

    override val hardBreakMarker: String?
        get() = when (profile.newlineSemantics) {
            NewlineSemantics.CommonMark -> when (profile.hardBreakEncode) {
                HardBreakEncode.Backslash -> "\\"
                HardBreakEncode.TwoSpaces -> "  "
            }

            // HardBreak mode emits plain newlines.
            NewlineSemantics.HardBreak -> null
        }

    override fun encodeInlineLines(block: Block): List<String> {
        val content = block.content as? BlockContent.Text ?: return listOf("")
        val spans = if (block.type.supportsSpans) content.spans else emptyList()
        return inlineRenderer.renderLines(
            blockId = block.id,
            text = content.text,
            spans = spans,
            hardBreakMarker = hardBreakMarker,
        )
    }

    override fun encodeTextOnly(block: Block): String {
        val text = (block.content as? BlockContent.Text)?.text ?: return ""
        return Markdown.escapeInline(text, MarkdownEscapeContext.LineStart)
    }

    override fun prefixLines(
        lines: List<String>,
        firstLinePrefix: String,
        continuationPrefix: String,
    ): List<String> = lines.mapIndexed { index, line ->
        if (index == 0) firstLinePrefix + line else continuationPrefix + line
    }

    override fun warn(warning: MarkdownEncodeWarning) {
        warningSink(warning)
    }
}
