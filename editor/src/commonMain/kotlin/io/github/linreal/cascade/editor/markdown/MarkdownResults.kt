package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block

/**
 * Outcome status of a Markdown codec operation.
 *
 * Status is always explicit: an empty block list and an empty output string are
 * both valid [Success] payloads, so abort must never be inferred from payload
 * emptiness — only from [Aborted].
 */
@ExperimentalCascadeMarkdownApi
public enum class MarkdownCodecStatus {
    Success,
    Aborted,
}

/**
 * Result of a Markdown decode.
 *
 * [blocks] is non-null exactly when [status] is [MarkdownCodecStatus.Success];
 * a partial payload is never representable. An aborted result always carries at
 * least one [MarkdownFidelityImpact.Fatal] warning explaining the abort.
 *
 * Warning [MarkdownDecodeWarning.range]s resolve to line/column positions in
 * the original input through [sourceLocator].
 */
@ExperimentalCascadeMarkdownApi
public class MarkdownDecodeResult private constructor(
    public val status: MarkdownCodecStatus,
    public val blocks: List<Block>?,
    public val warnings: List<MarkdownDecodeWarning>,
    public val sourceLocator: MarkdownSourceLocator,
) {

    public val isSuccess: Boolean get() = status == MarkdownCodecStatus.Success

    public val isAborted: Boolean get() = status == MarkdownCodecStatus.Aborted

    override fun toString(): String =
        "MarkdownDecodeResult(status=$status, blocks=${blocks?.size}, warnings=${warnings.size})"

    public companion object {

        /**
         * A completed decode. [blocks] may be empty — empty input decodes to an
         * empty, successful payload, never to an abort. Rejects
         * [MarkdownFidelityImpact.Fatal] warnings: a Fatal impact means the
         * operation aborted, which is only representable through [aborted].
         */
        public fun success(
            blocks: List<Block>,
            warnings: List<MarkdownDecodeWarning> = emptyList(),
            sourceLocator: MarkdownSourceLocator = emptyLocator(),
        ): MarkdownDecodeResult {
            require(warnings.none { it.impact == MarkdownFidelityImpact.Fatal }) {
                "A successful decode result cannot carry Fatal-impact warnings"
            }
            return MarkdownDecodeResult(
                status = MarkdownCodecStatus.Success,
                blocks = blocks,
                warnings = warnings,
                sourceLocator = sourceLocator,
            )
        }

        /**
         * An aborted decode. Requires at least one
         * [MarkdownFidelityImpact.Fatal] warning; the payload is always null.
         */
        public fun aborted(
            warnings: List<MarkdownDecodeWarning>,
            sourceLocator: MarkdownSourceLocator = emptyLocator(),
        ): MarkdownDecodeResult {
            require(warnings.any { it.impact == MarkdownFidelityImpact.Fatal }) {
                "An aborted decode result must carry at least one Fatal-impact warning"
            }
            return MarkdownDecodeResult(
                status = MarkdownCodecStatus.Aborted,
                blocks = null,
                warnings = warnings,
                sourceLocator = sourceLocator,
            )
        }

        private fun emptyLocator(): MarkdownSourceLocator =
            MarkdownSourceLocator(lineContentStarts = IntArray(0), lastContentEnd = 0)
    }
}

/**
 * Result of a Markdown encode.
 *
 * [markdown] is non-null exactly when [status] is
 * [MarkdownCodecStatus.Success]; a partial payload is never representable. An
 * aborted result always carries at least one [MarkdownFidelityImpact.Fatal]
 * warning explaining the abort.
 */
@ExperimentalCascadeMarkdownApi
public class MarkdownEncodeResult private constructor(
    public val status: MarkdownCodecStatus,
    public val markdown: String?,
    public val warnings: List<MarkdownEncodeWarning>,
) {

    public val isSuccess: Boolean get() = status == MarkdownCodecStatus.Success

    public val isAborted: Boolean get() = status == MarkdownCodecStatus.Aborted

    override fun toString(): String =
        "MarkdownEncodeResult(status=$status, markdown=${markdown?.length}, warnings=${warnings.size})"

    public companion object {

        /**
         * A completed encode. [markdown] may be empty — an empty document
         * encodes to an empty, successful payload, never to an abort. Rejects
         * [MarkdownFidelityImpact.Fatal] warnings: a Fatal impact means the
         * operation aborted, which is only representable through [aborted].
         */
        public fun success(
            markdown: String,
            warnings: List<MarkdownEncodeWarning> = emptyList(),
        ): MarkdownEncodeResult {
            require(warnings.none { it.impact == MarkdownFidelityImpact.Fatal }) {
                "A successful encode result cannot carry Fatal-impact warnings"
            }
            return MarkdownEncodeResult(
                status = MarkdownCodecStatus.Success,
                markdown = markdown,
                warnings = warnings,
            )
        }

        /**
         * An aborted encode. Requires at least one
         * [MarkdownFidelityImpact.Fatal] warning; the payload is always null.
         */
        public fun aborted(
            warnings: List<MarkdownEncodeWarning>,
        ): MarkdownEncodeResult {
            require(warnings.any { it.impact == MarkdownFidelityImpact.Fatal }) {
                "An aborted encode result must carry at least one Fatal-impact warning"
            }
            return MarkdownEncodeResult(
                status = MarkdownCodecStatus.Aborted,
                markdown = null,
                warnings = warnings,
            )
        }
    }
}
