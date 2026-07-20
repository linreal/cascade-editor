package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.serialization.resolveCurrentBlocks
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Serialize the current editor state to Markdown using [profile]. Returns
 * `null` only when the encode aborted; discards warnings.
 *
 * Runtime text/span state takes priority over snapshot content, matching the
 * JSON/HTML export paths.
 */
@ExperimentalCascadeMarkdownApi
public fun EditorStateHolder.toMarkdown(
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    profile: MarkdownProfile = MarkdownProfile.Default,
    limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    lineEnding: MarkdownLineEnding = MarkdownLineEnding.Lf,
): String? = toMarkdownWithReport(textStates, spanStates, profile, limits, lineEnding).markdown

/**
 * Serialize the current editor state to Markdown using [profile], returning the
 * full [MarkdownEncodeResult].
 *
 * The synthetic trailing editor scaffold is handled per mode:
 * in CommonMark mode a single depth-0, span-free empty trailing paragraph is
 * dropped when it is the sole block or follows a non-text block (divider,
 * custom); in HardBreak mode every trailing empty paragraph is retained because
 * it carries a meaningful blank line.
 */
@ExperimentalCascadeMarkdownApi
public fun EditorStateHolder.toMarkdownWithReport(
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    profile: MarkdownProfile = MarkdownProfile.Default,
    limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    lineEnding: MarkdownLineEnding = MarkdownLineEnding.Lf,
): MarkdownEncodeResult {
    val resolved = resolveCurrentBlocks(this, textStates, spanStates)
    val trimmed = stripTrailingEditorScaffold(resolved, profile)
    return MarkdownSchema.encodeWithReport(trimmed, profile, limits, lineEnding)
}

/**
 * Decode [markdown] with [profile] and replace the editor document with the
 * result — but only after a non-aborted decode.
 *
 * On success the runtime holders are cleared, `setState` replaces the document
 * (resetting focus, selection, transient UI state, and undo/redo history like
 * every hard load), and the successful result is returned. On abort
 * ([MarkdownDecodeWarning.InputLimitExceeded], output/warning limit exhaustion,
 * or an engine-fatal result) the current document and history are left
 * untouched and the aborted report is returned.
 *
 * App-owned import is not gated by read-only mode (consistent with the
 * documented read-only boundary).
 */
@ExperimentalCascadeMarkdownApi
public fun EditorStateHolder.loadFromMarkdown(
    markdown: String,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    profile: MarkdownProfile = MarkdownProfile.Default,
    limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
): MarkdownDecodeResult {
    val result = MarkdownSchema.decodeWithReport(markdown, profile, limits)
    if (result.isSuccess) {
        applyMarkdownDecodeResult(result, textStates, spanStates)
    }
    return result
}

/**
 * Apply an already-decoded, successful [result] as a hard document replacement.
 *
 * Hosts decode/analyze off the UI thread and call this on the UI thread to
 * perform only the final state swap. An aborted result is rejected with an
 * [IllegalArgumentException] — the payload would be `null`, and aborting must
 * never mutate the current document.
 */
@ExperimentalCascadeMarkdownApi
public fun EditorStateHolder.applyMarkdownDecodeResult(
    result: MarkdownDecodeResult,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
) {
    val blocks = result.blocks
    require(blocks != null) {
        "applyMarkdownDecodeResult requires a successful (non-aborted) decode result"
    }
    textStates.clear()
    spanStates.clear()
    setState(EditorState.withBlocks(blocks))
}

private fun stripTrailingEditorScaffold(
    blocks: List<Block>,
    profile: MarkdownProfile,
): List<Block> {
    if (profile.newlineSemantics == NewlineSemantics.HardBreak) return blocks
    if (blocks.isEmpty()) return blocks

    val last = blocks.last()
    val content = last.content
    val isEmptyTrailingParagraph = last.type == BlockType.Paragraph &&
        last.attributes.indentationLevel == 0 &&
        content is BlockContent.Text &&
        content.text.isEmpty() &&
        content.spans.isEmpty()
    if (!isEmptyTrailingParagraph) return blocks

    // Sole block, or the preceding block is non-text (divider/custom): the
    // trailing empty paragraph is the editor's cursor scaffold, not content.
    if (blocks.size == 1) return emptyList()
    val previous = blocks[blocks.size - 2]
    return if (!previous.type.supportsText) blocks.dropLast(1) else blocks
}
