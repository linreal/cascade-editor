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
 * Produced by [MarkdownSchema.analyze]: a source decode followed — on success —
 * by one canonical encode and one verification decode. The encode result is
 * reused for [wouldRewriteSource] and [nativeEditingSafe]; no second encode runs.
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
 *   / [MarkdownFidelityImpact.Fatal] from either direction ∧ the profile's
 *   support predicate accepts the blocks ∧ canonical output decodes to the same
 *   editor model (generated block IDs ignored).
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
 * Internal analyzer backing [MarkdownSchema.analyze]. Runs one source decode
 * and, on success, one encode plus one verification decode. The single encode
 * result serves both source-rewrite comparison and round-trip verification.
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
        val supportOk = documentSupported(profile, blocks, encodeResult, limits)
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
     * Support component of [MarkdownFidelityReport.nativeEditingSafe]. The
     * public support predicate remains a narrowing hook; the canonical output
     * is then decoded and compared with the editor model so no predicate can
     * falsely widen the round-trip claim. A default-built support set exposes
     * its cheap value/shape claim so the already-computed encode is reused.
     */
    private fun documentSupported(
        profile: MarkdownProfile,
        blocks: List<Block>,
        encodeResult: MarkdownEncodeResult,
        limits: MarkdownCodecLimits,
    ): Boolean {
        // A custom support-set predicate may throw; `analyze` must stay no-throw,
        // so a throwing predicate is treated as "not supported" → RawFallback.
        return try {
            val analyzerClaim = profile.supportSet.analyzerDocumentClaim
            val claimed = if (analyzerClaim != null) {
                analyzerClaim(blocks)
            } else {
                profile.supportSet.supportsDocument(blocks)
            }
            claimed && markdownRoundTripMatches(blocks, encodeResult, profile, limits)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
