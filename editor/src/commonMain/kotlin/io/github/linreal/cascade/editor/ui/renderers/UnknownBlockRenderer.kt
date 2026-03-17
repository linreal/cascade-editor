package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer

/**
 * Placeholder renderer for [UnknownBlockType] blocks.
 *
 * Displays a muted box with the unrecognized type ID. Non-focusable, non-editable.
 * Registered as the [BlockRegistry][io.github.linreal.cascade.editor.registry.BlockRegistry]
 * unknown-block fallback via [setUnknownBlockRenderer][io.github.linreal.cascade.editor.registry.BlockRegistry.setUnknownBlockRenderer].
 */
internal object UnknownBlockRenderer : BlockRenderer<UnknownBlockType> {

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFFF0F0F0),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text = "Unsupported block type: ${block.type.typeId}",
                color = Color(0xFF888888),
                fontSize = 14.sp,
            )
        }
    }
}
