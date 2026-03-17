package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Serializes the current editor state to a JSON string.
 *
 * Runtime text/span state (from [textStates]/[spanStates]) takes priority over
 * snapshot values in [EditorStateHolder.state]. This captures live editing state
 * for blocks that are currently on-screen, while off-screen blocks use their
 * snapshot content.
 */
public fun EditorStateHolder.toJson(
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    options: DocumentEncodeOptions = DocumentEncodeOptions(),
    typeCodec: BlockTypeCodec? = null,
    contentCodec: BlockContentCodec? = null,
): String {
    val resolvedBlocks = state.blocks.map { block ->
        val content = block.content
        if (content !is BlockContent.Text) return@map block

        val hasRuntimeText = textStates.get(block.id) != null
        val hasRuntimeSpans = spanStates.get(block.id) != null
        if (!hasRuntimeText && !hasRuntimeSpans) return@map block

        val resolvedText = if (hasRuntimeText) {
            textStates.getVisibleText(block.id) ?: content.text
        } else {
            content.text
        }
        val resolvedSpans = if (hasRuntimeSpans) {
            spanStates.getSpans(block.id)
        } else {
            content.spans
        }
        block.withContent(BlockContent.Text(resolvedText, resolvedSpans))
    }
    return DocumentSchema.encodeToString(resolvedBlocks, options, typeCodec, contentCodec)
}

/**
 * Loads a JSON document into the editor, replacing all current content.
 *
 * Clears runtime text and span state before setting new blocks, so that
 * Compose renderers initialize fresh state from the new snapshot blocks.
 */
public fun EditorStateHolder.loadFromJson(
    jsonString: String,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    options: DocumentDecodeOptions = DocumentDecodeOptions(),
    typeCodec: BlockTypeCodec? = null,
    contentCodec: BlockContentCodec? = null,
): DocumentDecodeResult {
    val result = DocumentSchema.decodeFromStringWithReport(jsonString, options, typeCodec, contentCodec)
    textStates.clear()
    spanStates.clear()
    setState(EditorState.withBlocks(result.blocks))
    return result
}
