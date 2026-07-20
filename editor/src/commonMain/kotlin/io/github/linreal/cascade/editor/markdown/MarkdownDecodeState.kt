package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.TextSpan

/** Shared, bounded mutable state for one AST decode. */
internal class MarkdownParseState(private val limits: MarkdownCodecLimits) {
    private val warningList = ArrayList<MarkdownDecodeWarning>()
    val warnings: List<MarkdownDecodeWarning> get() = warningList
    val definitions: MarkdownDefinitionTable = MarkdownDefinitionTable()

    private var fatal: MarkdownDecodeWarning? = null
    private var blockCount = 0
    private var spanCount = 0

    val isAborted: Boolean get() = fatal != null

    fun warn(warning: MarkdownDecodeWarning) {
        if (isAborted) return
        if (warningList.size >= limits.maxWarnings) {
            abort(
                MarkdownDecodeWarning.LimitExceeded(
                    MarkdownCodecLimitKind.Warnings,
                    limits.maxWarnings,
                    warning.range,
                ),
            )
        } else {
            warningList += warning
        }
    }

    fun abort(warning: MarkdownDecodeWarning) {
        if (fatal != null) return
        fatal = warning
        warningList += warning
    }

    fun noteBlock(range: MarkdownSourceRange): Boolean {
        if (isAborted) return false
        blockCount++
        if (blockCount <= limits.maxBlocks) return true
        abort(MarkdownDecodeWarning.LimitExceeded(MarkdownCodecLimitKind.Blocks, limits.maxBlocks, range))
        return false
    }

    fun noteSpans(count: Int, range: MarkdownSourceRange): Boolean {
        if (isAborted) return false
        spanCount += count
        if (spanCount <= limits.maxTotalSpans) return true
        abort(
            MarkdownDecodeWarning.LimitExceeded(
                MarkdownCodecLimitKind.TotalSpans,
                limits.maxTotalSpans,
                range,
            ),
        )
        return false
    }

    fun registerDefinition(
        label: String,
        definition: MarkdownLinkReferenceDefinition,
        range: MarkdownSourceRange,
    ) {
        if (isAborted) return
        if (!definitions.register(label, definition)) {
            warn(MarkdownDecodeWarning.DuplicateLinkDefinition(label, range))
            return
        }
        if (definitions.size > limits.maxReferenceDefinitions) {
            abort(
                MarkdownDecodeWarning.LimitExceeded(
                    MarkdownCodecLimitKind.ReferenceDefinitions,
                    limits.maxReferenceDefinitions,
                    range,
                ),
            )
        }
    }
}

/** Visible text and spans produced by one inline AST container. */
internal class MarkdownInlineParseResult(
    val text: String,
    val spans: List<TextSpan>,
    val escalationKind: String?,
)
