package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme

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
        val strings = LocalCascadeStrings.current
        UnknownBlockVisual(
            text = strings.unsupportedBlock(block.type.typeId),
            modifier = modifier,
        )
    }
}

/**
 * Lightweight unsupported-block surface shared by editor and preview fallbacks.
 *
 * Preview mode may supply a safe text excerpt and a bounded line count; editor mode
 * retains the localized unsupported-type label and unbounded default.
 */
@Composable
internal fun UnknownBlockVisual(
    text: String,
    modifier: Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val colors = LocalCascadeTheme.current.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colors.unknownBlockBackground,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(12.dp),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = colors.unknownBlockText,
                fontSize = 14.sp,
            ),
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
