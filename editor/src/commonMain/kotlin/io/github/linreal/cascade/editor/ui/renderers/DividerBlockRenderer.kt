package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.utils.Dividers

/**
 * Renderer for Divider blocks — a horizontal line separator.
 */
public class DividerBlockRenderer : BlockRenderer<BlockType.Divider> {

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Dividers.Horizontal(color = LocalCascadeTheme.current.colors.contentDivider)
        }
    }
}
