package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi

/**
 * Safe fallback for unknown and custom blocks without a dedicated preview renderer.
 *
 * Plain text is preserved as a small excerpt. Opaque custom payloads are never inspected
 * and use the localized unsupported-block label instead.
 */
@OptIn(ExperimentalCascadePreviewApi::class)
internal object UnknownBlockPreviewRenderer : BlockPreviewRenderer<BlockType> {

    @Composable
    override fun RenderPreview(
        block: Block,
        modifier: Modifier,
        scope: BlockPreviewScope,
    ) {
        val strings = LocalCascadeStrings.current
        val text = (block.content as? BlockContent.Text)
            ?.text
            ?.takeIf(String::isNotBlank)
            ?: strings.unsupportedBlock(block.type.typeId)
        val configuredMaxLines = scope.config.maxLinesPerTextBlock ?: UnknownPreviewMaxLines

        UnknownBlockVisual(
            text = text,
            modifier = modifier,
            maxLines = minOf(configuredMaxLines, UnknownPreviewMaxLines),
            // The generic fallback is always paint-bounded, including when a caller
            // explicitly chooses TextOverflow.Visible for known text blocks.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val UnknownPreviewMaxLines = 3
