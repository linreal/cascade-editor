package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.serialization.resolveCurrentBlocks
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Serializes the current editor state to HTML using [profile].
 *
 * Runtime text/span state takes priority over snapshot content, matching the JSON
 * export path. This captures live editing content for rendered blocks while
 * preserving snapshot content for blocks without runtime holders.
 */
@ExperimentalCascadeHtmlApi
public fun EditorStateHolder.toHtml(
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    profile: HtmlProfile,
): String {
    val resolvedBlocks = resolveCurrentBlocks(this, textStates, spanStates)
    return HtmlSchema.encode(resolvedBlocks, profile)
}

/**
 * Decodes [html] with [profile] and replaces the editor document with the result.
 *
 * Runtime text/span holders are cleared before the decoded blocks are applied so
 * renderers rebuild their runtime state from the imported snapshot. The replacement
 * uses [EditorStateHolder.setState], so focus, selection, transient UI state, and
 * undo/redo history are reset like other hard document loads.
 */
@ExperimentalCascadeHtmlApi
public fun EditorStateHolder.loadFromHtml(
    html: String,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    profile: HtmlProfile,
): HtmlDecodeResult {
    val result = HtmlSchema.decodeWithReport(html, profile)
    textStates.clear()
    spanStates.clear()
    setState(EditorState.withBlocks(result.blocks))
    return result
}
