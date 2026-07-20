package io.github.linreal.cascade.editor.markdown

/**
 * Defensive resource bounds for the Markdown codec.
 *
 * Markdown can amplify both parsed state (delimiter runs, nesting) and emitted
 * text (escaping, prefixing), so limits apply to decode, encode, and analyze.
 * Exceeding any limit aborts the operation: the result carries
 * `status = Aborted`, a `null` payload, and a [MarkdownFidelityImpact.Fatal]
 * warning — an aborted operation is never reported as successful empty content.
 *
 * The defaults are generous for the bounded-field v1 target (task/note fields up
 * to tens of KB) while hard-stopping adversarial input.
 */
@ExperimentalCascadeMarkdownApi
public data class MarkdownCodecLimits(
    /** Maximum decode/analyze input length in UTF-16 code units. */
    val maxInputChars: Int = DEFAULT_MAX_INPUT_CHARS,
    /** Maximum container nesting depth accepted from the Markdown AST. */
    val maxBlockNesting: Int = DEFAULT_MAX_BLOCK_NESTING,
    /** Maximum number of blocks produced by one decode. */
    val maxBlocks: Int = DEFAULT_MAX_BLOCKS,
    /** Maximum number of spans lowered onto a single block. */
    val maxSpansPerBlock: Int = DEFAULT_MAX_SPANS_PER_BLOCK,
    /** Maximum total number of spans across the whole document. */
    val maxTotalSpans: Int = DEFAULT_MAX_TOTAL_SPANS,
    /** Maximum number of link-reference definitions collected in the AST prepass. */
    val maxReferenceDefinitions: Int = DEFAULT_MAX_REFERENCE_DEFINITIONS,
    /** Maximum number of emphasis-capable delimiter runs accepted before parsing. */
    val maxDelimiterRuns: Int = DEFAULT_MAX_DELIMITER_RUNS,
    /**
     * Maximum number of warnings collected before the operation aborts. The
     * exhaustion itself is reported as one final [MarkdownFidelityImpact.Fatal]
     * warning, so the warning list never grows unboundedly.
     */
    val maxWarnings: Int = DEFAULT_MAX_WARNINGS,
    /** Maximum encode/analyze output length in UTF-16 code units. */
    val maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS,
) {
    init {
        require(maxInputChars > 0) { "maxInputChars must be positive" }
        require(maxBlockNesting > 0) { "maxBlockNesting must be positive" }
        require(maxBlocks > 0) { "maxBlocks must be positive" }
        require(maxSpansPerBlock > 0) { "maxSpansPerBlock must be positive" }
        require(maxTotalSpans > 0) { "maxTotalSpans must be positive" }
        require(maxReferenceDefinitions > 0) { "maxReferenceDefinitions must be positive" }
        require(maxDelimiterRuns > 0) { "maxDelimiterRuns must be positive" }
        require(maxWarnings > 0) { "maxWarnings must be positive" }
        require(maxOutputChars > 0) { "maxOutputChars must be positive" }
    }

    public companion object {
        // Mirrors HtmlDecodeLimits.DEFAULT_MAX_INPUT_CHARS.
        public const val DEFAULT_MAX_INPUT_CHARS: Int = 4_000_000
        public const val DEFAULT_MAX_BLOCK_NESTING: Int = 32
        public const val DEFAULT_MAX_BLOCKS: Int = 50_000
        public const val DEFAULT_MAX_SPANS_PER_BLOCK: Int = 1_000
        public const val DEFAULT_MAX_TOTAL_SPANS: Int = 50_000
        public const val DEFAULT_MAX_REFERENCE_DEFINITIONS: Int = 5_000
        public const val DEFAULT_MAX_DELIMITER_RUNS: Int = 10_000
        public const val DEFAULT_MAX_WARNINGS: Int = 1_000
        // 4x the input bound: Markdown escaping/prefixing amplifies output.
        public const val DEFAULT_MAX_OUTPUT_CHARS: Int = 16_000_000

        public val Default: MarkdownCodecLimits = MarkdownCodecLimits()
    }
}
