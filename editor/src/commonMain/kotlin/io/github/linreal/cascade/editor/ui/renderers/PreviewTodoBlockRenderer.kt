package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi

/**
 * Static todo renderer with checked semantics but no toggle action.
 */
@OptIn(ExperimentalCascadePreviewApi::class)
internal class PreviewTodoBlockRenderer : BlockPreviewRenderer<BlockType.Todo> {

    @Composable
    override fun RenderPreview(
        block: Block,
        modifier: Modifier,
        scope: BlockPreviewScope,
    ) {
        val todo = block.type as? BlockType.Todo ?: return
        // Gate on type only, matching TodoBlockRenderer: a todo whose content did
        // not decode as text must still show its checkbox.
        val content = block.previewTextContent()
        val theme = LocalCascadeTheme.current
        val indentationLevel = block.attributes.indentationLevel
        val baseDecoration = if (todo.checked) TextDecoration.LineThrough else null

        TodoBlockRowVisual(
            modifier = modifier.withStaticBlockIndentation(block),
            indicator = {
                TodoCheckboxIndicator(
                    progress = if (todo.checked) 1f else 0f,
                    primaryColor = theme.colors.primary,
                    onPrimaryColor = theme.colors.onPrimary,
                    borderColor = theme.colors.text.copy(alpha = 0.5f),
                    indentationLevel = indentationLevel,
                    inputModifier = Modifier.semantics {
                        role = Role.Checkbox
                        toggleableState = if (todo.checked) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        }
                    },
                )
            },
            content = { contentModifier ->
                PreviewBlockText(
                    content = content,
                    textStyle = theme.typography.body.copy(
                        color = theme.colors.text,
                        textDecoration = baseDecoration ?: TextDecoration.None,
                    ),
                    modifier = contentModifier,
                    scope = scope,
                    supportsSpans = block.type.supportsSpans,
                    baseDecoration = baseDecoration,
                )
            },
        )
    }
}
