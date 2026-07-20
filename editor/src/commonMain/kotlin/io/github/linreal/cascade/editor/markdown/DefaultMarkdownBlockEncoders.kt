package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType

/**
 * Canonical default block encoders: ATX headings, `> ` per
 * physical line, backtick fences with no info string widened past internal
 * runs, `---` dividers. The list outline (`BulletList` / `NumberedList` /
 * `Todo`) is claimed by [DefaultMarkdownListOutlineEncoder]; block separation
 * belongs to [MarkdownEncodeEngine], so every emit here is content-only.
 */
internal object DefaultMarkdownBlockEncoders {

    internal val Paragraph: MarkdownBlockEncoder<BlockType.Paragraph> =
        MarkdownBlockEncoder { ctx, block, content ->
            val text = (content as? BlockContent.Text)?.text.orEmpty()
            if (text.isEmpty() && ctx.newlineSemantics == NewlineSemantics.CommonMark) {
                // Empty paragraphs are not encodable in CommonMark mode; defer
                // to the warning fallback.
                MarkdownEmit.Skip
            } else {
                if (block.attributes.indentationLevel > BlockAttributes.MIN_INDENTATION_LEVEL) {
                    ctx.warn(
                        MarkdownEncodeWarning.DroppedAttribute(
                            attr = "indentationLevel",
                            reason = "Markdown has no indented-paragraph syntax; " +
                                "encoded at depth 0",
                            blockId = block.id,
                        ),
                    )
                }
                MarkdownEmit.Raw(ctx.encodeInlineLines(block).joinToString("\n"))
            }
        }

    internal val Heading: MarkdownBlockEncoder<BlockType.Heading> =
        MarkdownBlockEncoder { ctx, block, _ ->
            val level = (block.type as BlockType.Heading).level
            val lines = ctx.encodeInlineLines(block)
            // A heading is one physical line; embedded newlines encode as a
            // single space with a DataLoss warning.
            val text = if (lines.size > 1) {
                ctx.warn(
                    MarkdownEncodeWarning.DroppedAttribute(
                        attr = "lineBreak",
                        reason = "an ATX heading cannot contain line breaks; " +
                            "each embedded newline was encoded as a space",
                        blockId = block.id,
                    ),
                )
                // Strip exactly the marker the context appended to non-final
                // lines; final lines (and HardBreak mode) carry none.
                val marker = ctx.hardBreakMarker
                lines.mapIndexed { index, line ->
                    if (marker != null && index < lines.lastIndex && line.endsWith(marker)) {
                        line.dropLast(marker.length)
                    } else {
                        line
                    }
                }.joinToString(" ")
            } else {
                lines.first()
            }
            val marker = "#".repeat(level)
            MarkdownEmit.Raw(if (text.isEmpty()) marker else "$marker $text")
        }

    internal val Quote: MarkdownBlockEncoder<BlockType.Quote> =
        MarkdownBlockEncoder { ctx, block, content ->
            val text = (content as? BlockContent.Text)?.text.orEmpty()
            if (text.isEmpty() && ctx.newlineSemantics == NewlineSemantics.CommonMark) {
                // "> " re-decodes to zero blocks; empty quotes are not
                // encodable in CommonMark mode — defer to the warning fallback
                // like empty paragraphs, with no silent fallback.
                MarkdownEmit.Skip
            } else {
                val prefixed = ctx.prefixLines(
                    lines = ctx.encodeInlineLines(block),
                    firstLinePrefix = "> ",
                    continuationPrefix = "> ",
                )
                MarkdownEmit.Raw(prefixed.joinToString("\n"))
            }
        }

    internal val Code: MarkdownBlockEncoder<BlockType.Code> =
        MarkdownBlockEncoder { ctx, block, content ->
            // Code content is byte-exact — never escaped. The fence owns one
            // structural newline after the opener and before the closer, and
            // is widened past every backtick run in the
            // content so no content line can close it early.
            val text = (content as? BlockContent.Text)?.text.orEmpty()
            // A `\r` in code does not round-trip: the source layer treats it as
            // a line terminator, so re-decode normalizes it away.
            if (text.indexOf('\r') >= 0) {
                ctx.warn(
                    MarkdownEncodeWarning.DroppedAttribute(
                        attr = "carriageReturn",
                        reason = "code content contains a carriage return, which does not " +
                            "round-trip as Markdown code",
                        blockId = block.id,
                    ),
                )
            }
            val fence = "`".repeat(maxOf(3, longestBacktickRun(text) + 1))
            val body = if (text.isEmpty()) "" else text + "\n"
            MarkdownEmit.Raw("$fence\n$body$fence")
        }

    internal val Divider: MarkdownBlockEncoder<BlockType.Divider> =
        MarkdownBlockEncoder { _, _, _ -> MarkdownEmit.Raw("---") }

    /**
     * Pre-registered verbatim encoder for `md.preserved` blocks: the
     * character-exact `rawMarkdown` slice is emitted through the
     * distinct [MarkdownEmit.Verbatim] path. A payload without the slice
     * defers to the warning fallback.
     */
    internal val Preserved: MarkdownBlockEncoder<BlockType> =
        MarkdownBlockEncoder { _, _, content ->
            val raw = ((content as? BlockContent.Custom)?.data?.get("rawMarkdown") as? String)
            if (raw == null) MarkdownEmit.Skip else MarkdownEmit.Verbatim(raw)
        }

    /**
     * Strict-mode `md.preserved` encoder for [MarkdownProfile.withoutHtmlBridge]:
     * emits the verbatim slice **except** when the preserved kind is HTML **or**
     * the slice contains a `<` — a strict profile can never emit raw HTML
     * through the preserved-block path, including a `<script>` smuggled inside a
     * preserved table cell or front-matter block. Such a slice is dropped
     * through the fallback (with an `UnsupportedBlock` warning).
     */
    internal val StrictPreserved: MarkdownBlockEncoder<BlockType> =
        MarkdownBlockEncoder { _, _, content ->
            val custom = content as? BlockContent.Custom
            val raw = custom?.data?.get("rawMarkdown") as? String
            val kind = custom?.data?.get("kind") as? String
            when {
                raw == null -> MarkdownEmit.Skip
                kind != null && kind.startsWith("html") -> MarkdownEmit.Skip
                raw.indexOf('<') >= 0 -> MarkdownEmit.Skip
                else -> MarkdownEmit.Verbatim(raw)
            }
        }

    /**
     * Strict-mode `md.preservedHtml` encoder: always defers to the fallback
     * (dropped with a warning) so raw HTML is never emitted.
     */
    internal val StrictDropHtml: MarkdownBlockEncoder<BlockType> =
        MarkdownBlockEncoder { _, _, _ -> MarkdownEmit.Skip }
}

private fun longestBacktickRun(text: String): Int {
    var longest = 0
    var current = 0
    for (ch in text) {
        if (ch == '`') {
            current++
            if (current > longest) longest = current
        } else {
            current = 0
        }
    }
    return longest
}
