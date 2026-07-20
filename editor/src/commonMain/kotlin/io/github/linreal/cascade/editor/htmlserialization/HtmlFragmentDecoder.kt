package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block

/**
 * Internal fragment-decode entry points over a given [HtmlProfile], extracted
 * for the Markdown HTML bridge.
 *
 * Differences from the full-document [HtmlDecodeEngine] path, by design:
 *
 * - Parser policies do not apply: root inline content is decoded, not dropped
 *   (`InlineRoot.Drop` would make `<u>x</u>` yield an empty inline result),
 *   and root text is not newline-split. Fragments are already scoped by the
 *   caller.
 * - No post-decode outline normalization or list renumbering — fragment blocks
 *   join a larger document whose decode owns normalization.
 * - Warning `charOffset`s are relative to the fragment string; callers re-base
 *   them into their own source coordinates (the bridge maps them into Markdown
 *   source ranges).
 *
 * [textLeafReparser] hands text leaves back to a caller-supplied inline
 * re-parser so nested non-HTML syntax survives (`<u>**bold**</u>` routes
 * `**bold**` through the Markdown inline parser). See [HtmlDecodeRunner].
 *
 * Everything here is internal to `:editor`; no public API surface changes.
 */
internal object HtmlFragmentDecoder {

    /**
     * Decode [html] as a block-level fragment: block elements produce blocks,
     * root-level inline runs flush into paragraphs. Whitespace-only inline
     * runs never flush into blank paragraphs (formatting whitespace between
     * block elements is noise), but whitespace between inline elements stays
     * part of the surrounding run — `<b>a</b> <i>b</i>` keeps its space.
     */
    internal fun decodeBlockFragment(
        html: String,
        profile: HtmlProfile,
        textLeafReparser: ((String) -> InlineFragment)? = null,
    ): HtmlBlockFragmentResult {
        return try {
            val parsed = HtmlParser.parse(html, profile.entityDecode)
            val warnings = parsed.warnings.toMutableList()
            val rootViews = parsed.nodes.map(HtmlNodeViewMapper::toView)
            val runner = HtmlDecodeRunner(
                rawSource = html,
                profile = profile,
                warnings = warnings,
                textLeafReparser = textLeafReparser,
                skipBlankInlineFlush = true,
            )
            HtmlBlockFragmentResult(
                blocks = runner.decodeBlocks(rootViews, parentTag = null),
                warnings = warnings,
            )
        } catch (throwable: Throwable) {
            HtmlBlockFragmentResult(
                blocks = emptyList(),
                warnings = listOf(decoderExceptionWarning(tag = null, throwable = throwable, charOffset = 0)),
            )
        }
    }

    /**
     * Decode [html] as an inline fragment: paired inline tags produce spans
     * over the collected text. Block-producing decoders degrade through the
     * usual [HtmlDecodeWarning.BlockInInlineContext] flattening.
     */
    internal fun decodeInlineFragment(
        html: String,
        profile: HtmlProfile,
        textLeafReparser: ((String) -> InlineFragment)? = null,
    ): HtmlInlineFragmentResult {
        return try {
            val parsed = HtmlParser.parse(html, profile.entityDecode)
            val warnings = parsed.warnings.toMutableList()
            val rootViews = parsed.nodes.map(HtmlNodeViewMapper::toView)
            val runner = HtmlDecodeRunner(
                rawSource = html,
                profile = profile,
                warnings = warnings,
                textLeafReparser = textLeafReparser,
            )
            HtmlInlineFragmentResult(
                fragment = runner.decodeInline(rootViews, parentTag = null),
                warnings = warnings,
            )
        } catch (throwable: Throwable) {
            HtmlInlineFragmentResult(
                fragment = InlineFragment("", emptyList()),
                warnings = listOf(decoderExceptionWarning(tag = null, throwable = throwable, charOffset = 0)),
            )
        }
    }
}

/** Result of [HtmlFragmentDecoder.decodeBlockFragment]. */
internal data class HtmlBlockFragmentResult(
    val blocks: List<Block>,
    val warnings: List<HtmlDecodeWarning>,
)

/** Result of [HtmlFragmentDecoder.decodeInlineFragment]. */
internal data class HtmlInlineFragmentResult(
    val fragment: InlineFragment,
    val warnings: List<HtmlDecodeWarning>,
)
