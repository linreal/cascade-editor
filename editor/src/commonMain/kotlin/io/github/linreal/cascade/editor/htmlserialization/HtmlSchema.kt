package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block

/**
 * Public entry point for HTML import/export.
 *
 * Mirrors `DocumentSchema`: stateless object with `encode`, `encodeWithReport`,
 * `decode`, and `decodeWithReport` variants. The `encode` / `decode` variants discard
 * warnings; the `*WithReport` variants surface them via [HtmlEncodeResult] /
 * [HtmlDecodeResult].
 *
 * Decode and encode entry points are wired. Default canonical encode mappings are
 * registered separately from the generic encode engine.
 */
@ExperimentalCascadeHtmlApi
public object HtmlSchema {

    /**
     * Encode [blocks] to an HTML string using [profile]. Discards warnings.
     *
     */
    public fun encode(blocks: List<Block>, profile: HtmlProfile): String =
        encodeWithReport(blocks, profile).html

    /**
     * Encode [blocks] to an HTML string using [profile], returning warnings.
     */
    public fun encodeWithReport(blocks: List<Block>, profile: HtmlProfile): HtmlEncodeResult =
        HtmlEncodeEngine.encodeWithReport(blocks, profile)

    /** Decode [html] to a list of blocks using [profile]. Discards warnings. */
    public fun decode(html: String, profile: HtmlProfile): List<Block> =
        decodeWithReport(html, profile).blocks

    /**
     * Decode [html] to a list of blocks using [profile], returning warnings.
     */
    public fun decodeWithReport(html: String, profile: HtmlProfile): HtmlDecodeResult =
        HtmlDecodeEngine.decodeWithReport(html, profile)
}
