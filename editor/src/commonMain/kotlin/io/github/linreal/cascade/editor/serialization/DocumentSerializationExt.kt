package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.Block
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
    val resolvedBlocks = resolveCurrentBlocks(this, textStates, spanStates)
    return DocumentSchema.encodeToString(resolvedBlocks, options, typeCodec, contentCodec)
}

/**
 * Resolves the current authoritative document blocks without JSON encoding.
 *
 * Runtime text/span state takes priority over snapshot content exactly like
 * [toJson], while unchanged blocks keep their original object identity.
 */
internal fun resolveCurrentBlocks(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
): List<Block> {
    return resolveCurrentBlocks(stateHolder.state.blocks, textStates, spanStates)
}

internal fun resolveCurrentBlocks(
    snapshotBlocks: List<Block>,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
): List<Block> {
    return snapshotBlocks.map { block ->
        val content = block.content
        if (content !is BlockContent.Text) return@map block

        val hasRuntimeText = textStates.get(block.id) != null
        val hasRuntimeSpans = spanStates.get(block.id) != null
        val supportsSpans = block.type.supportsSpans

        // Non-spans blocks (e.g. Code) must always emit empty spans regardless of
        // runtime state. This catches malformed snapshots and stale runtime span
        // state that survived a same-id conversion.
        if (!hasRuntimeText && !hasRuntimeSpans) {
            return@map if (!supportsSpans && content.spans.isNotEmpty()) {
                block.withContent(content.copy(spans = emptyList()))
            } else {
                block
            }
        }

        val resolvedText = if (hasRuntimeText) {
            textStates.getVisibleText(block.id) ?: content.text
        } else {
            content.text
        }
        val resolvedSpans = if (!supportsSpans) {
            emptyList()
        } else if (hasRuntimeSpans) {
            spanStates.getSpans(block.id)
        } else {
            content.spans
        }

        val resolvedContent = BlockContent.Text(resolvedText, resolvedSpans)
        if (resolvedContent == content) block else block.withContent(resolvedContent)
    }
}

/**
 * Loads a JSON document into the editor, replacing all current content.
 *
 * Clears runtime text and span state before setting new blocks, so that
 * Compose renderers initialize fresh state from the new snapshot blocks.
 * This is treated as a hard replacement and therefore clears undo/redo history.
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
