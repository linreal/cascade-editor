package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.htmlserialization.HtmlBlockFragmentResult
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlFragmentDecoder
import io.github.linreal.cascade.editor.htmlserialization.HtmlInlineFragmentResult
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.InlineFragment

/**
 * Internal HTML ⇄ Markdown bridge.
 *
 * Detection of HTML in Markdown source is **always active** regardless of the
 * [HtmlInMarkdown] policy — something must consume `<tag …>` / `</tag>` /
 * `<!-- … -->`; the policy decides what the detected range *becomes*:
 *
 * - [HtmlInMarkdown.Bridge] routes the island through the internal
 *   [HtmlFragmentDecoder], handing inner text back to the Markdown inline
 *   parser (so `<u>**bold**</u>` keeps Bold), and emits
 *   [MarkdownDecodeWarning.HtmlBridged] (Informational). Islands that lower to
 *   no content are **not** a successful bridge and follow the
 *   [UnsupportedSyntax] policy (Preserve → `md.preservedHtml`; WarnAndDegrade →
 *   DataLoss).
 * - [HtmlInMarkdown.Preserve] always preserves block HTML as an opaque
 *   `md.preservedHtml` block.
 * - [HtmlInMarkdown.WarnAndStrip] drops the tags, keeps inner text, and emits
 *   [MarkdownDecodeWarning.HtmlStripped] (DataLoss).
 * - [HtmlInMarkdown.Strip] does the same but the strip warning is recorded for
 *   fidelity analysis rather than surfaced as a user-facing loss.
 *
 * ### Security
 *
 * The codec is **not a sanitizer**. Under [HtmlInMarkdown.Bridge] /
 * [HtmlInMarkdown.Preserve] raw HTML can survive a decode/encode round-trip
 * verbatim. WebView or server hosts must sanitize at render time, or adopt a
 * strict profile via [MarkdownProfile.withoutHtmlBridge], which guarantees no
 * raw HTML is ever emitted through any path.
 */
internal object MarkdownHtmlBridge {

    /** typeId of the internal block-phase node carrying a raw HTML block slice. */
    const val HTML_BLOCK_NODE_TYPE_ID: String = "md.htmlBlockSource"

    /** Data key holding the raw HTML slice on an [HTML_BLOCK_NODE_TYPE_ID] node. */
    const val RAW_HTML_KEY: String = "rawHtml"

    // Inline detection

    /**
     * An inline HTML island `<tag …>inner</tag>` recognized at a `<` trigger,
     * or null. Only paired tags are recognized inline; comments, void tags, and
     * unmatched opens stay literal (the block path handles line-level HTML).
     */
    class InlineIsland(
        val endExclusive: Int,
        val innerStart: Int,
        val innerEnd: Int,
        val tagName: String,
    )

    /**
     * True when [line] begins an HTML *block* (pragmatic subset of the
     * CommonMark start conditions): an HTML comment, or a well-formed
     * open/close tag whose name is a **block-level** element (the CommonMark
     * type-6 list plus script/pre/style/textarea). Inline tags (`<u>`, `<mark>`,
     * `<span>`, `<b>`, …) deliberately do **not** start a block — they are
     * handled inline by `rawHtmlInline`, so a paragraph beginning with an inline
     * tag stays a paragraph. Up to three leading spaces are tolerated.
     */
    fun looksLikeHtmlBlockStart(line: String): Boolean {
        var start = 0
        var indent = 0
        while (start < line.length && line[start] == ' ' && indent < 4) {
            start++
            indent++
        }
        if (indent >= 4 || start >= line.length || line[start] != '<') return false
        if (line.startsWith("<!--", start)) return true
        val tag = scanTag(line, start) ?: return false
        return tag.name.lowercase() in BLOCK_TAG_NAMES
    }

    private val BLOCK_TAG_NAMES: Set<String> = setOf(
        "address", "article", "aside", "base", "basefont", "blockquote", "body",
        "caption", "center", "col", "colgroup", "dd", "details", "dialog", "dir",
        "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
        "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
        "hr", "html", "iframe", "legend", "li", "link", "main", "menu", "menuitem",
        "nav", "noframes", "ol", "optgroup", "option", "p", "param", "section",
        "summary", "table", "tbody", "td", "tfoot", "th", "thead", "title", "tr",
        "track", "ul", "script", "pre", "style", "textarea",
    )

    fun matchInlinePairedTag(text: String, pos: Int): InlineIsland? {
        val open = scanTag(text, pos) ?: return null
        if (open.isClose || open.selfClosing || open.name.isEmpty()) return null
        val name = open.name
        var index = open.endExclusive
        var depth = 1
        while (index < text.length) {
            // Bound the forward scan so a flood of unmatched `<tag …>` opens
            // stays linear overall instead of O(n^2) (each trigger re-scanning
            // to end-of-text). A legitimate inline island is far shorter.
            if (index - pos > MAX_INLINE_ISLAND_SCAN) return null
            if (text[index] == '<') {
                val tag = scanTag(text, index)
                if (tag != null && tag.name.equals(name, ignoreCase = true)) {
                    if (tag.isClose) {
                        depth--
                        if (depth == 0) {
                            return InlineIsland(
                                endExclusive = tag.endExclusive,
                                innerStart = open.endExclusive,
                                innerEnd = index,
                                tagName = name,
                            )
                        }
                    } else if (!tag.selfClosing) {
                        depth++
                    }
                    index = tag.endExclusive
                    continue
                }
            }
            index++
        }
        return null
    }

    private class TagToken(
        val name: String,
        val endExclusive: Int,
        val isClose: Boolean,
        val selfClosing: Boolean,
    )

    /** Scan one HTML tag at [pos] (which must be `<`), or null when not a tag. */
    private fun scanTag(text: String, pos: Int): TagToken? {
        if (pos >= text.length || text[pos] != '<') return null
        var index = pos + 1
        if (index >= text.length) return null
        val isClose = text[index] == '/'
        if (isClose) index++
        val nameStart = index
        if (index >= text.length || !text[index].isHtmlNameStart()) return null
        while (index < text.length && text[index].isHtmlNameChar()) index++
        val name = text.substring(nameStart, index)
        // The character right after the name must end the name legally —
        // whitespace, `>`, or `/`. This rejects autolink-shaped input like
        // `<http://x>` (`:` after the name is not a valid tag continuation).
        if (index < text.length) {
            val after = text[index]
            if (after != '>' && after != '/' && !after.isWhitespace()) return null
        }
        // Attributes / whitespace until '>' (quotes respected, no nested '>').
        var selfClosing = false
        while (index < text.length) {
            val ch = text[index]
            when (ch) {
                '>' -> return TagToken(name, index + 1, isClose, selfClosing)
                '/' -> {
                    selfClosing = true
                    index++
                }
                '"', '\'' -> {
                    val quote = ch
                    index++
                    while (index < text.length && text[index] != quote) index++
                    if (index >= text.length) return null
                    index++
                }
                '<' -> return null
                else -> {
                    if (ch != '/') selfClosing = false
                    index++
                }
            }
        }
        return null
    }

    private const val MAX_INLINE_ISLAND_SCAN: Int = 8192

    // Bridge routing

    /**
     * Decode an inline island's HTML through the fragment decoder, routing its
     * text leaves back through [reparseInner] (the Markdown inline parser) so
     * nested Markdown survives. Returns the full fragment result **including
     * inner warnings** — the caller re-bases and classifies them (a dropped
     * inner tag is data loss, not a clean bridge).
     */
    fun bridgeInline(
        islandHtml: String,
        htmlProfile: HtmlProfile,
        reparseInner: (String) -> InlineFragment,
    ): HtmlInlineFragmentResult =
        HtmlFragmentDecoder.decodeInlineFragment(islandHtml, htmlProfile, reparseInner)

    /** Decode a block-level HTML slice through the fragment decoder (with warnings). */
    fun bridgeBlock(
        html: String,
        htmlProfile: HtmlProfile,
        reparseInner: (String) -> InlineFragment,
    ): HtmlBlockFragmentResult =
        HtmlFragmentDecoder.decodeBlockFragment(html, htmlProfile, reparseInner)

    /**
     * True when an inner HTML decode warning represents **content or metadata
     * loss** (so the bridge was not clean). Straightened/auto-closed nesting is
     * canonicalization and preserves content, so it is treated as benign.
     */
    fun isDestructiveHtmlWarning(warning: HtmlDecodeWarning): Boolean = when (warning) {
        is HtmlDecodeWarning.MismatchedNesting,
        is HtmlDecodeWarning.UnclosedTag -> false
        else -> true
    }

    /** Best-effort tag name carried by a destructive inner warning, for the re-wrapped warning. */
    fun htmlWarningTag(warning: HtmlDecodeWarning): String? = when (warning) {
        is HtmlDecodeWarning.UnknownTag -> warning.tag
        is HtmlDecodeWarning.StrayClosingTag -> warning.tag
        is HtmlDecodeWarning.UnknownAttribute -> warning.tag
        is HtmlDecodeWarning.InvalidAttribute -> warning.tag
        is HtmlDecodeWarning.BlockInInlineContext -> warning.tag
        is HtmlDecodeWarning.DroppedAttribute -> warning.tag
        is HtmlDecodeWarning.DecoderException -> warning.tag
        else -> null
    }
}

/** Native inline-code / HTML span mark pairs used by the default profile encoders. */
internal object MarkdownHtmlSpanEncoders {

    /** `Underline` → `<u>…</u>` (mirrors the HTML codec spelling). */
    val Underline: MarkdownSpanEncoder<SpanStyle.Underline> =
        MarkdownSpanEncoder { MarkdownMarkPair(open = "<u>", close = "</u>") }

    /** `Highlight` → `<mark data-cascade-highlight="AARRGGBB">…</mark>`. */
    val Highlight: MarkdownSpanEncoder<SpanStyle.Highlight> =
        MarkdownSpanEncoder { style ->
            MarkdownMarkPair(
                open = "<mark data-cascade-highlight=\"${style.colorArgb.toEightDigitUpperHex()}\">",
                close = "</mark>",
            )
        }

    private fun Long.toEightDigitUpperHex(): String {
        val hex = (this and 0xFFFF_FFFFL).toString(16).uppercase()
        return hex.padStart(8, '0')
    }
}

private fun Char.isHtmlNameStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isHtmlNameChar(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-'
