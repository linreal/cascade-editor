package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile

/**
 * Policy that controls what happens to raw HTML embedded in Markdown source.
 *
 * Detection of HTML blocks and inline tags is always active regardless of the
 * chosen policy — something must consume those constructs — the policy decides
 * what the detected range becomes.
 */
@ExperimentalCascadeMarkdownApi
public sealed interface HtmlInMarkdown {

    /** Drop HTML tags, keep inner text, emit [MarkdownDecodeWarning.HtmlStripped]. */
    public data object WarnAndStrip : HtmlInMarkdown

    /**
     * Drop HTML tags and keep inner text without a user-facing warning. The
     * destructive impact is still recorded for fidelity analysis — policy choice
     * never makes fidelity appear better than the actual transform.
     */
    public data object Strip : HtmlInMarkdown

    /**
     * Decode embedded HTML through the internal HTML fragment bridge using
     * [htmlProfile]. Every bridged range emits
     * [MarkdownDecodeWarning.HtmlBridged]. Default (with [HtmlProfile.Default]).
     */
    public data class Bridge(val htmlProfile: HtmlProfile) : HtmlInMarkdown

    /**
     * Preserve block-level HTML as a renderable opaque block with custom
     * content `typeId = "md.preservedHtml"`.
     */
    public data object Preserve : HtmlInMarkdown
}

/**
 * Policy that controls what happens to well-known Markdown constructs the
 * editor cannot model (pipe tables, front matter, math blocks, footnotes,
 * block images, and unrepresentable metadata).
 */
@ExperimentalCascadeMarkdownApi
public sealed interface UnsupportedSyntax {

    /**
     * Preservation recognizers are active: a recognized construct becomes an
     * opaque `md.preserved` block carrying its character-exact source slice,
     * with a [MarkdownDecodeWarning.PreservedSyntax] warning. Default —
     * every surveyed Markdown-native host treats silent data loss as a
     * pilot blocker.
     */
    public data object Preserve : UnsupportedSyntax

    /**
     * Recognizers stand down: constructs decode through ordinary syntaxes with
     * degraded content (a table becomes paragraph text, an image its alt text)
     * plus a [MarkdownDecodeWarning.UnsupportedSyntax] warning.
     */
    public data object WarnAndDegrade : UnsupportedSyntax
}

/**
 * Umbrella policy for the newline dialect of a profile.
 *
 * The two modes decode with different active block-syntax orders:
 *
 * | Slot | CommonMark | HardBreak |
 * |---|---|---|
 * | headings | `atxHeading`, `setextHeading` | `atxHeading` only |
 * | code | `fence`, `indentedCode` | `fence` only |
 * | breaks | single `\n` = soft break, blank line = separator | single `\n` = literal line break, blank line = empty paragraph |
 * | everything else | same order | same order (lists, quotes, fences, thematic breaks, recognizers) |
 *
 * `setextHeading` and `indentedCode` are dropped in HardBreak so `text\n---`
 * stays a paragraph run plus a thematic break and a 4-space indent is content,
 * not code.
 */
@ExperimentalCascadeMarkdownApi
public sealed interface NewlineSemantics {

    /**
     * CommonMark behavior: a single `\n` is a soft break (see [SoftBreak]),
     * blank lines separate blocks and collapse, hard breaks encode per
     * [HardBreakEncode]. Empty paragraph blocks are not encodable in this
     * mode. Active block syntaxes include `setextHeading` and `indentedCode`.
     * Default.
     */
    public data object CommonMark : NewlineSemantics

    /**
     * Chat-style dialect: every source newline inside a text leaf lowers to a
     * literal `\n` in one paragraph-shaped leaf, embedded `\n` encodes as a
     * plain newline (no `\`/two-space marker), and blank-line runs are
     * represented explicitly — each blank line becomes one empty `Paragraph`
     * block, so `N` empty paragraphs between two non-empty blocks encode to
     * exactly `N + 1` newlines and trailing empties preserve trailing blank
     * lines. `setextHeading` and `indentedCode` are removed from the active
     * order. This is the mode that fixes the Moe Memos blank-line loss (#339)
     * and numbered-continuation (#348) bug classes.
     */
    public data object HardBreak : NewlineSemantics
}

/**
 * Decode-side policy for what a single `\n` inside a paragraph becomes under
 * [NewlineSemantics.CommonMark].
 */
@ExperimentalCascadeMarkdownApi
public sealed interface SoftBreak {

    /** A soft break lowers to a single space. Default. */
    public data object Space : SoftBreak

    /** A soft break lowers to a literal `\n`. */
    public data object LineBreak : SoftBreak
}

/**
 * Encode-side policy for embedded `\n` in non-Code text blocks under
 * [NewlineSemantics.CommonMark]. Decode accepts both forms
 * regardless of this policy.
 */
@ExperimentalCascadeMarkdownApi
public sealed interface HardBreakEncode {

    /** Encode hard breaks as a trailing backslash. Survives whitespace trimming. Default. */
    public data object Backslash : HardBreakEncode

    /** Encode hard breaks as two trailing spaces. */
    public data object TwoSpaces : HardBreakEncode
}

/**
 * Policy that controls entity-reference decoding outside code spans and fenced
 * code.
 *
 * The supported named-entity set is a documented subset, not the full HTML5
 * table; numeric references decode fully. Unknown names stay literal text with
 * an informational warning.
 */
@ExperimentalCascadeMarkdownApi
public sealed interface EntityDecode {

    /** Decode numeric references and the documented named subset. Default. */
    public data object Standard : EntityDecode

    /** Pass all entity references through as literal text. */
    public data object None : EntityDecode
}
