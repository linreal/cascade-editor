package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block

/**
 * Result of [HtmlSchema.decodeWithReport]. The plain [HtmlSchema.decode] entry point
 * discards [warnings].
 *
 * Decoding an empty or malformed input still produces a valid result (possibly with
 * empty [blocks] and populated warnings).
 */
@ExperimentalCascadeHtmlApi
public data class HtmlDecodeResult(
    val blocks: List<Block>,
    val warnings: List<HtmlDecodeWarning>,
)

/**
 * Result of [HtmlSchema.encodeWithReport]. The plain [HtmlSchema.encode] entry point
 * discards [warnings].
 */
@ExperimentalCascadeHtmlApi
public data class HtmlEncodeResult(
    val html: String,
    val warnings: List<HtmlEncodeWarning>,
)
