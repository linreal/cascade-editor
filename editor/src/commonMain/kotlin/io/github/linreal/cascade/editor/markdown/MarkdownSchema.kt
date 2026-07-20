package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import kotlin.coroutines.cancellation.CancellationException

/**
 * Public entry point for Markdown import/export.
 *
 * Mirrors [io.github.linreal.cascade.editor.htmlserialization.HtmlSchema] and
 * `DocumentSchema`: a stateless, synchronous, thread-agnostic object with
 * `encode` / `decode` conveniences (nullable payload — `null` only when the
 * operation aborted) and `*WithReport` variants that surface warnings through
 * [MarkdownEncodeResult] / [MarkdownDecodeResult]. Every path takes an explicit
 * [MarkdownCodecLimits]; [analyze] assembles a [MarkdownFidelityReport] for the
 * host edit-mode gate.
 *
 * ### No-throw boundary
 *
 * This object is the codec's public no-throw boundary. Consumer encoders that
 * throw are already contained inside the encode engine and surface as warnings;
 * any *unexpected* exception that still escapes the internal pipeline
 * is caught here and converted into an aborted result with a
 * [MarkdownDecodeWarning.EngineFailure] / [MarkdownEncodeWarning.EngineFailure]
 * (both [MarkdownFidelityImpact.Fatal]) — the codec never lets an exception
 * cross into caller code.
 *
 * ### Threading
 *
 * All entry points are pure and stateless. Decode, encode, and especially
 * [analyze] (decode + encode) can be non-trivial for large inputs; hosts should
 * run them off the main thread and apply the result on the UI thread (see
 * `MarkdownSerializationExt`).
 */
@ExperimentalCascadeMarkdownApi
public object MarkdownSchema {

    /**
     * Encode [blocks] to a Markdown string using [profile]. Returns `null` only
     * when the encode aborted (output/warning limit); an empty document encodes
     * to an empty, non-null string.
     */
    public fun encode(
        blocks: List<Block>,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
        lineEnding: MarkdownLineEnding = MarkdownLineEnding.Lf,
    ): String? = encodeWithReport(blocks, profile, limits, lineEnding).markdown

    /** Encode [blocks] to Markdown using [profile], returning warnings. */
    public fun encodeWithReport(
        blocks: List<Block>,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
        lineEnding: MarkdownLineEnding = MarkdownLineEnding.Lf,
    ): MarkdownEncodeResult = try {
        MarkdownEncodeEngine.encodeWithReport(blocks, profile, limits, lineEnding)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        MarkdownEncodeResult.aborted(
            listOf(MarkdownEncodeWarning.EngineFailure(engineFailureMessage(e))),
        )
    }

    /**
     * Decode [markdown] to blocks using [profile]. Returns `null` only when the
     * decode aborted (input/nesting/limit exhaustion); empty input decodes to a
     * non-null empty block list.
     */
    public fun decode(
        markdown: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    ): List<Block>? = decodeWithReport(markdown, profile, limits).blocks

    /** Decode [markdown] to blocks using [profile], returning warnings. */
    public fun decodeWithReport(
        markdown: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    ): MarkdownDecodeResult = try {
        MarkdownDecodeEngine.decode(markdown, profile, limits)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        MarkdownDecodeResult.aborted(
            listOf(MarkdownDecodeWarning.EngineFailure(engineFailureMessage(e))),
        )
    }

    /**
     * Assess whether [markdown] can be edited natively under [profile]. Runs
     * decode, then — on success — encode, and assembles a
     * [MarkdownFidelityReport]. This is the most expensive call (decode +
     * encode); run it off the main thread.
     *
     * Hosts keep the original [markdown] string. Opening, analyzing, or
     * read-only rendering must never persist canonical output: only a
     * user-initiated save writes back, gated on this report.
     */
    public fun analyze(
        markdown: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    ): MarkdownFidelityReport = MarkdownFidelityAnalyzer.analyze(markdown, profile, limits)

    private fun engineFailureMessage(e: Exception): String =
        e.message ?: (e::class.simpleName ?: "unexpected engine failure")
}
