package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.action.ToggleTodo
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer

/**
 * Renderer for Todo blocks — a checkbox alongside formattable text.
 *
 * Delegates text editing to [TextBlockField] and renders a [Checkbox]
 * that dispatches [ToggleTodo] on state change.
 */
public class TodoBlockRenderer : BlockRenderer<BlockType.Todo> {

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    ) {
        val todoType = block.type as? BlockType.Todo ?: return

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = todoType.checked,
                onCheckedChange = { callbacks.dispatch(ToggleTodo(block.id)) },
                modifier = Modifier.background(Color.Black),
            )
            TextBlockField(
                block = block,
                isFocused = isFocused,
                textStyle = TextStyle(fontSize = 16.sp),
                modifier = Modifier.weight(1f),
                callbacks = callbacks,
            )
        }
    }
}
