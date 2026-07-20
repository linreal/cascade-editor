package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockId

/**
 * Fidelity impact classification carried by every Markdown codec warning.
 *
 * `analyze` and host save guards classify results by impact alone — never by
 * hard-coded warning-subclass lists — so the classification below is the
 * contract each warning member commits to:
 *
 * | Impact | Meaning | Examples |
 * |---|---|---|
 * | [Informational] | Nothing was lost or hidden; purely advisory. | successful `HtmlBridged`, an unsupported entity name kept as literal text |
 * | [Canonicalization] | Semantics identical, spelling/trivia would change on re-encode (line endings, marker spelling, final newline). Never forces raw fallback on its own. | canonical bullet/heading spelling notes |
 * | [OpaquePreservation] | Content survives character-exactly but as an opaque, non-natively-editable block. | `PreservedSyntax` for tables, front matter, math, footnotes, images |
 * | [DataLoss] | Content or metadata was altered or dropped; a re-decode would not reproduce the input. | `UnsupportedSyntax` degradation, `HtmlStripped`, `DroppedSpanOverlap`, `AmbiguousEmphasis`, dropped attributes, encoder fallbacks |
 * | [Fatal] | The operation aborted; the result carries no payload. | input/output/nesting/block/span/definition/delimiter/warning limit exhaustion, engine failure |
 */
@ExperimentalCascadeMarkdownApi
public enum class MarkdownFidelityImpact {
    Informational,
    Canonicalization,
    OpaquePreservation,
    DataLoss,
    Fatal,
}

/**
 * Common supertype of [MarkdownDecodeWarning] and [MarkdownEncodeWarning] so
 * impact-driven consumers (e.g. `analyze`) can classify warnings from both
 * directions uniformly.
 */
@ExperimentalCascadeMarkdownApi
public sealed interface MarkdownWarning {
    /** Fidelity impact this warning commits to. See [MarkdownFidelityImpact]. */
    public val impact: MarkdownFidelityImpact
}

/**
 * Half-open UTF-16 range into a block's visible text, used by encode warnings
 * to point at the responsible text/span region. Unlike [MarkdownSourceRange]
 * this addresses editor block text, not a Markdown source string.
 */
@ExperimentalCascadeMarkdownApi
public data class MarkdownTextRange(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "start must be >= 0, was $start" }
        require(endExclusive >= start) {
            "endExclusive ($endExclusive) must be >= start ($start)"
        }
    }
}

/**
 * The codec limit a [MarkdownDecodeWarning.LimitExceeded] /
 * [MarkdownEncodeWarning.LimitExceeded] warning refers to.
 */
@ExperimentalCascadeMarkdownApi
public enum class MarkdownCodecLimitKind {
    BlockNesting,
    Blocks,
    SpansPerBlock,
    TotalSpans,
    ReferenceDefinitions,
    DelimiterRuns,
    Warnings,
}

/**
 * Warnings emitted while decoding Markdown.
 *
 * Every decode warning carries a [range]: a half-open UTF-16
 * [MarkdownSourceRange] into the original input string, resolvable to
 * line/column through the result's [MarkdownSourceLocator].
 *
 * This hierarchy is open by design — later codec phases add members. Consumers
 * should include an `else` branch in `when` expressions and prefer classifying
 * by [impact] rather than by concrete subclass.
 */
@ExperimentalCascadeMarkdownApi
public sealed class MarkdownDecodeWarning : MarkdownWarning {

    /** Source range of the construct responsible for the warning. */
    public abstract val range: MarkdownSourceRange

    /**
     * A construct the editor cannot model decoded through ordinary syntaxes
     * with degraded content (the `UnsupportedSyntax.WarnAndDegrade` path).
     */
    public data class UnsupportedSyntax(
        val construct: String,
        val detail: String,
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /**
     * A construct was preserved character-exactly as an opaque `md.preserved`
     * block (the `UnsupportedSyntax.Preserve` path).
     */
    public data class PreservedSyntax(
        val kind: String,
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.OpaquePreservation
    }

    /** Embedded HTML decoded successfully through the HTML bridge. */
    public data class HtmlBridged(
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Informational
    }

    /** Embedded HTML tags were dropped (inner text kept) under a strip policy. */
    public data class HtmlStripped(
        val tag: String?,
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /**
     * Source metadata that has no editor representation was dropped
     * (e.g. clamped indentation depth, dropped link title under degrade).
     */
    public data class DroppedAttribute(
        val construct: String,
        val attr: String,
        val reason: String,
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /**
     * A link-reference definition whose label was already defined; the first
     * definition wins (CommonMark) and the shadowed duplicate is dropped.
     * Semantics are unchanged — CommonMark lookup is defined as
     * first-definition-wins — but a re-encode would not reproduce the
     * shadowed line, hence [MarkdownFidelityImpact.Canonicalization].
     */
    public data class DuplicateLinkDefinition(
        val label: String,
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Canonicalization
    }

    /**
     * A named entity reference outside the documented supported subset was
     * kept as literal text. Nothing is lost — the character data is retained
     * exactly — so this never trips the raw-fallback gate on its own.
     */
    public data class UnsupportedEntity(
        val name: String,
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Informational
    }

    /** Input exceeded [MarkdownCodecLimits.maxInputChars]; the decode aborted. */
    public data class InputLimitExceeded(
        val limit: Int,
        val actual: Int,
        override val range: MarkdownSourceRange = MarkdownSourceRange(0, 0),
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Fatal
    }

    /**
     * A decode resource limit ([kind]) was exhausted; the decode aborted.
     * [MarkdownCodecLimitKind.Warnings] exhaustion is reported through exactly
     * one such warning appended after the retained warnings, so the list stays
     * bounded.
     */
    public data class LimitExceeded(
        val kind: MarkdownCodecLimitKind,
        val limit: Int,
        override val range: MarkdownSourceRange,
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Fatal
    }

    /**
     * An unexpected engine failure crossed the internal pipeline and was caught
     * at the public `MarkdownSchema` boundary. The decode aborted with no
     * payload; this is the last-resort net that keeps the codec no-throw.
     */
    public data class EngineFailure(
        val message: String,
        override val range: MarkdownSourceRange = MarkdownSourceRange(0, 0),
    ) : MarkdownDecodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Fatal
    }
}

/**
 * Warnings emitted while encoding blocks to Markdown.
 *
 * Encode warnings carry the responsible [blockId] when a specific block is
 * responsible, plus an optional [textRange] into that block's visible text for
 * inline losses. A lone `typeId` is deliberately not the addressing scheme — it
 * is not actionable when a document contains many blocks of the same type.
 *
 * This hierarchy is open by design — later codec phases add members. Consumers
 * should include an `else` branch in `when` expressions and prefer classifying
 * by [impact] rather than by concrete subclass.
 */
@ExperimentalCascadeMarkdownApi
public sealed class MarkdownEncodeWarning : MarkdownWarning {

    /** Id of the block responsible for the warning, when one is. */
    public abstract val blockId: BlockId?

    /** Optional range into the responsible block's visible text. */
    public abstract val textRange: MarkdownTextRange?

    /**
     * Emitted delimiter runs re-parsed to different spans than were encoded;
     * the weaker span was dropped to keep the output honest.
     */
    public data class AmbiguousEmphasis(
        override val blockId: BlockId?,
        override val textRange: MarkdownTextRange?,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /**
     * A span overlapping an inline-code range (or otherwise unrepresentable
     * overlap residue) was dropped.
     */
    public data class DroppedSpanOverlap(
        override val blockId: BlockId?,
        override val textRange: MarkdownTextRange?,
        val reason: String,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /**
     * A block attribute (e.g. paragraph `indentationLevel`) was dropped because
     * canonical Markdown has no encoding for it.
     */
    public data class DroppedAttribute(
        val attr: String,
        val reason: String,
        override val blockId: BlockId?,
        override val textRange: MarkdownTextRange? = null,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /** A block with no registered encoder fell back to a plain paragraph. */
    public data class UnsupportedBlock(
        val typeId: String?,
        val reason: String,
        override val blockId: BlockId?,
        override val textRange: MarkdownTextRange? = null,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /** A span with no registered encoder kept its text but dropped its marks. */
    public data class UnsupportedSpan(
        val typeId: String?,
        val reason: String,
        override val blockId: BlockId?,
        override val textRange: MarkdownTextRange?,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /**
     * A consumer-supplied encoder threw; its block/span degraded through the
     * fallback path.
     */
    public data class EncoderException(
        val typeId: String?,
        val message: String,
        override val blockId: BlockId?,
        override val textRange: MarkdownTextRange? = null,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.DataLoss
    }

    /** Output exceeded [MarkdownCodecLimits.maxOutputChars]; the encode aborted. */
    public data class OutputLimitExceeded(
        val limit: Int,
        override val blockId: BlockId? = null,
        override val textRange: MarkdownTextRange? = null,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Fatal
    }

    /**
     * An encode resource limit ([kind]) was exhausted; the encode aborted.
     * [MarkdownCodecLimitKind.Warnings] exhaustion is reported through exactly
     * one such warning appended after the retained warnings, so the list stays
     * bounded.
     */
    public data class LimitExceeded(
        val kind: MarkdownCodecLimitKind,
        val limit: Int,
        override val blockId: BlockId? = null,
        override val textRange: MarkdownTextRange? = null,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Fatal
    }

    /**
     * An unexpected engine failure crossed the internal pipeline and was caught
     * at the public `MarkdownSchema` boundary. The encode aborted with no
     * payload; this is the last-resort net that keeps the codec no-throw.
     */
    public data class EngineFailure(
        val message: String,
        override val blockId: BlockId? = null,
        override val textRange: MarkdownTextRange? = null,
    ) : MarkdownEncodeWarning() {
        override val impact: MarkdownFidelityImpact get() = MarkdownFidelityImpact.Fatal
    }
}
