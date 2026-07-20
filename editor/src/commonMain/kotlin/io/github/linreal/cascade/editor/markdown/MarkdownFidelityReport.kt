package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent

/**
 * Host edit-mode recommendation from [MarkdownSchema.analyze].
 */
@ExperimentalCascadeMarkdownApi
public enum class MarkdownEditModeRecommendation {
    /** The document round-trips losslessly; open it in the native editor. */
    Native,

    /**
     * The document cannot be edited without losing fidelity; open a raw text
     * fallback so the original source is preserved byte-for-byte.
     */
    RawFallback,
}

/**
 * Fidelity assessment of a Markdown document under a profile.
 *
 * Produced by [MarkdownSchema.analyze]: a decode followed — on success — by one
 * canonical encode, reusing that single encode result for both
 * [wouldRewriteSource] and the verification half of [nativeEditingSafe]
 * (no double encode).
 *
 * The report classifies by fidelity **impact**, never by concrete warning
 * subclass, so new warning members never require an analyze change.
 *
 * @property decodeResult the decode outcome.
 * @property encodeResult the encode outcome, or `null` when the decode aborted
 *   (no blocks to encode).
 * @property preservedBlockCount number of opaque preserved blocks
 *   (`md.preserved` / `md.preservedHtml`) in the decoded document.
 * @property dataLossWarnings every [MarkdownFidelityImpact.DataLoss] warning
 *   from either direction — the actionable "what would be lost" list.
 * @property wouldRewriteSource whether re-encoding differs from the source
 *   (line endings, BOM, final newline, and canonical spelling all count);
 *   `null` when no encode comparison was possible. A `true` value driven only
 *   by [MarkdownFidelityImpact.Canonicalization] never forces raw fallback on
 *   its own.
 * @property nativeEditingSafe successful decode ∧ completed encode ∧ no
 *   [MarkdownFidelityImpact.OpaquePreservation] / [MarkdownFidelityImpact.DataLoss]
 *   / [MarkdownFidelityImpact.Fatal] from either direction ∧
 *   `profile.supportSet.supportsDocument(blocks)`.
 * @property recommendedMode [MarkdownEditModeRecommendation.Native] iff
 *   [nativeEditingSafe].
 */
@ExperimentalCascadeMarkdownApi
public class MarkdownFidelityReport internal constructor(
    public val decodeResult: MarkdownDecodeResult,
    public val encodeResult: MarkdownEncodeResult?,
    public val preservedBlockCount: Int,
    public val dataLossWarnings: List<MarkdownWarning>,
    public val wouldRewriteSource: Boolean?,
    public val nativeEditingSafe: Boolean,
    public val recommendedMode: MarkdownEditModeRecommendation,
) {
    override fun toString(): String =
        "MarkdownFidelityReport(recommendedMode=$recommendedMode, " +
            "nativeEditingSafe=$nativeEditingSafe, preservedBlockCount=$preservedBlockCount, " +
            "dataLossWarnings=${dataLossWarnings.size}, wouldRewriteSource=$wouldRewriteSource)"
}

/**
 * Internal analyzer backing [MarkdownSchema.analyze]. Runs one decode and, on
 * success, one encode; the single encode result serves the source-rewrite
 * comparison and the verification half of the support check.
 */
internal object MarkdownFidelityAnalyzer {

    private val PRESERVED_TYPE_IDS = setOf(MARKDOWN_PRESERVED_TYPE_ID, MARKDOWN_PRESERVED_HTML_TYPE_ID)

    fun analyze(
        markdown: String,
        profile: MarkdownProfile,
        limits: MarkdownCodecLimits,
    ): MarkdownFidelityReport {
        val decodeResult = MarkdownSchema.decodeWithReport(markdown, profile, limits)
        val blocks = decodeResult.blocks
            ?: return MarkdownFidelityReport(
                decodeResult = decodeResult,
                encodeResult = null,
                preservedBlockCount = 0,
                dataLossWarnings = decodeResult.warnings.filter {
                    it.impact == MarkdownFidelityImpact.DataLoss
                },
                wouldRewriteSource = null,
                nativeEditingSafe = false,
                recommendedMode = MarkdownEditModeRecommendation.RawFallback,
            )

        val encodeResult = MarkdownSchema.encodeWithReport(blocks, profile, limits)
        val preservedBlockCount = blocks.count { block ->
            val content = block.content
            content is BlockContent.Custom && content.typeId in PRESERVED_TYPE_IDS
        }

        val combinedWarnings: List<MarkdownWarning> = decodeResult.warnings + encodeResult.warnings
        val dataLossWarnings = combinedWarnings.filter { it.impact == MarkdownFidelityImpact.DataLoss }
        val wouldRewriteSource = encodeResult.markdown?.let { it != markdown }

        val impactClean = combinedWarnings.none {
            it.impact == MarkdownFidelityImpact.OpaquePreservation ||
                it.impact == MarkdownFidelityImpact.DataLoss ||
                it.impact == MarkdownFidelityImpact.Fatal
        }
        val supportOk = documentSupported(profile, blocks, encodeResult)
        val nativeEditingSafe =
            decodeResult.isSuccess && encodeResult.isSuccess && impactClean && supportOk
        val mode = if (nativeEditingSafe) {
            MarkdownEditModeRecommendation.Native
        } else {
            MarkdownEditModeRecommendation.RawFallback
        }

        return MarkdownFidelityReport(
            decodeResult = decodeResult,
            encodeResult = encodeResult,
            preservedBlockCount = preservedBlockCount,
            dataLossWarnings = dataLossWarnings,
            wouldRewriteSource = wouldRewriteSource,
            nativeEditingSafe = nativeEditingSafe,
            recommendedMode = mode,
        )
    }

    /**
     * Support component of [MarkdownFidelityReport.nativeEditingSafe]. For a
     * default-built support set, the cheap structural claim is combined with
     * the already-computed [encodeResult] so no second encode runs. For a
     * custom support set (opaque predicate) the public `supportsDocument` is
     * consulted directly.
     */
    private fun documentSupported(
        profile: MarkdownProfile,
        blocks: List<Block>,
        encodeResult: MarkdownEncodeResult,
    ): Boolean {
        // A custom support-set predicate may throw; `analyze` must stay no-throw,
        // so a throwing predicate is treated as "not supported" → RawFallback.
        return try {
            val structural = profile.supportSet.structuralDocumentClaim
            if (structural != null) {
                structural(blocks) &&
                    !encodeResult.isAborted &&
                    encodeResult.warnings.none {
                        it.impact == MarkdownFidelityImpact.DataLoss ||
                            it.impact == MarkdownFidelityImpact.Fatal
                    }
            } else {
                profile.supportSet.supportsDocument(blocks)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
