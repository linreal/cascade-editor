package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.normalizeIndentationOutline
import io.github.linreal.cascade.editor.core.renumberNumberedLists

/**
 * Internal decode orchestration. JetBrains Markdown owns CommonMark/GFM
 * recognition; Cascade owns source normalization, AST-to-editor lowering,
 * preservation, policy behavior, diagnostics, and resource limits. After
 * lowering, the core normalization pipeline runs in the mandated order:
 *    `normalizeIndentationOutline` then `renumberNumberedLists`.
 *
 * Warnings from every stage share one bounded collection
 * ([MarkdownParseState]); an abort in any stage yields an aborted result with
 * no partial payload. The public no-throw boundary around consumer code lives
 * in the parser/encoders; the public `MarkdownSchema` entry points
 * add the final top-level guard.
 */
internal object MarkdownDecodeEngine {

    fun decode(
        input: String,
        profile: MarkdownProfile,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    ): MarkdownDecodeResult {
        if (input.length > limits.maxInputChars) {
            return MarkdownDecodeResult.aborted(
                listOf(MarkdownDecodeWarning.InputLimitExceeded(limits.maxInputChars, input.length)),
                MarkdownSourceLocator(IntArray(0), 0),
            )
        }
        val parseInput = MarkdownParseInput.of(input)
        val locator = parseInput.locator
        val state = MarkdownParseState(limits)
        enforceDelimiterLimit(input, limits, state)
        if (state.isAborted) return MarkdownDecodeResult.aborted(state.warnings.toList(), locator)

        val tree = parseMarkdownTree(parseInput.text)
        val blocks = MarkdownAstDecoder(parseInput, tree, profile, limits, state).decode()
        if (state.isAborted) return MarkdownDecodeResult.aborted(state.warnings.toList(), locator)

        val normalized = renumberNumberedLists(normalizeIndentationOutline(blocks))
        return MarkdownDecodeResult.success(
            blocks = normalized,
            warnings = state.warnings.toList(),
            sourceLocator = locator,
        )
    }

    private fun enforceDelimiterLimit(
        input: String,
        limits: MarkdownCodecLimits,
        state: MarkdownParseState,
    ) {
        var runs = 0
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (char != '*' && char != '_' && char != '~') {
                index++
                continue
            }
            runs++
            if (runs > limits.maxDelimiterRuns) {
                state.abort(
                    MarkdownDecodeWarning.LimitExceeded(
                        MarkdownCodecLimitKind.DelimiterRuns,
                        limits.maxDelimiterRuns,
                        MarkdownSourceRange(index, index + 1),
                    ),
                )
                return
            }
            while (index < input.length && input[index] == char) index++
        }
    }
}
