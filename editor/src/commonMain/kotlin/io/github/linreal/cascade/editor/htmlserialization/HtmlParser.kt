package io.github.linreal.cascade.editor.htmlserialization

internal object HtmlParser {

    /**
     * Parse [rawSource] into internal nodes and parser warnings.
     *
     * The parser is tolerant by construction: malformed input produces a partial
     * tree plus warnings rather than throwing.
     */
    internal fun parse(
        rawSource: String,
        entityDecode: EntityDecode = EntityDecode.Standard,
    ): HtmlParseResult {
        val tokens = HtmlTokenizer.tokenize(rawSource, entityDecode)
        val tree = HtmlTreeBuilder.build(rawSource, tokens)
        return HtmlParseResult(
            nodes = tree.nodes,
            warnings = tree.warnings,
        )
    }

    /**
     * Parse [rawSource] and apply the parser policies carried by [profile].
     *
     * This is the decode-engine entry point: the returned nodes are shaped for codec
     * dispatch, but no tag decoder has run yet.
     */
    internal fun parse(rawSource: String, profile: HtmlProfile): HtmlParseResult {
        val parsed = parse(rawSource, profile.entityDecode)
        val policyResult = HtmlPolicyApplier.apply(rawSource, parsed.nodes, profile)
        return HtmlParseResult(
            nodes = policyResult.nodes,
            warnings = parsed.warnings + policyResult.warnings,
        )
    }
}

internal data class HtmlParseResult(
    val nodes: List<HtmlNode>,
    val warnings: List<HtmlDecodeWarning>,
)
