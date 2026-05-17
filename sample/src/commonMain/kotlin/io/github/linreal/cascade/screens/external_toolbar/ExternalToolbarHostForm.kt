package io.github.linreal.cascade.screens.external_toolbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/**
 * Read-only host-form field used to show CascadeEditor as one field among many.
 */
@Composable
internal fun ExternalToolbarReadOnlyField(field: ExternalToolbarFormField) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ExternalToolbarTokens.FieldLabelSpacing),
    ) {
        ExternalToolbarFieldLabel(field.label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ExternalToolbarTokens.FieldHeight)
                .background(
                    externalToolbarFieldBackgroundColor(),
                    ExternalToolbarTokens.FieldShape,
                )
                .padding(horizontal = ExternalToolbarTokens.FieldHorizontalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = field.value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun ExternalToolbarEditorField(
    focused: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    editor: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ExternalToolbarTokens.FieldLabelSpacing),
    ) {
        ExternalToolbarFieldLabel("Text field")
        ExternalToolbarEditorContainer(
            focused = focused,
            onBoundsChanged = onBoundsChanged,
            content = editor,
        )
    }
}

@Composable
private fun ExternalToolbarFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun ExternalToolbarEditorContainer(
    focused: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    content: @Composable () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (focused) ExternalToolbarTokens.FocusedBorderColor else Color.Transparent,
        animationSpec = tween(ExternalToolbarTokens.FocusAnimationMillis),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ExternalToolbarTokens.EditorFieldHeight)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .clip(ExternalToolbarTokens.FieldShape)
            .background(externalToolbarFieldBackgroundColor(), ExternalToolbarTokens.FieldShape)
            .border(1.dp, borderColor, ExternalToolbarTokens.FieldShape)
            .padding(
                horizontal = ExternalToolbarTokens.EditorFieldHorizontalPadding,
                vertical = ExternalToolbarTokens.EditorFieldVerticalPadding,
            ),
    ) {
        content()
    }
}

@Composable
private fun externalToolbarFieldBackgroundColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ExternalToolbarTokens.FieldBackgroundAlpha)
