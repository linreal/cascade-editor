package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi

/** Stateless preview counterpart to [DividerBlockRenderer]. */
@OptIn(ExperimentalCascadePreviewApi::class)
internal object PreviewDividerBlockRenderer : BlockPreviewRenderer<BlockType.Divider> {

    @Composable
    override fun RenderPreview(
        block: Block,
        modifier: Modifier,
        scope: BlockPreviewScope,
    ) {
        DividerBlockVisual(modifier)
    }
}
