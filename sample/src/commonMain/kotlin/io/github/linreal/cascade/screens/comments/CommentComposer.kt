package io.github.linreal.cascade.screens.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_code
import cascadeeditor.sample.generated.resources.ic_format_bold
import cascadeeditor.sample.generated.resources.ic_format_italic
import cascadeeditor.sample.generated.resources.ic_format_underlined
import cascadeeditor.sample.generated.resources.ic_send
import cascadeeditor.sample.generated.resources.ic_strikethrough_s
import io.github.linreal.cascade.editor.action.ClearFocus
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.editor.ui.CascadeEditorToolbarController
import io.github.linreal.cascade.editor.ui.LinkPopupSlot
import io.github.linreal.cascade.editor.ui.SlashCommandSlot
import io.github.linreal.cascade.editor.ui.ToolbarSlot
import io.github.linreal.cascade.editor.ui.rememberCascadeEditorToolbarController
import io.github.linreal.cascade.ui.nonFocusableTap
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Styles tracked by the composer's formatting bar (matches the imported design). */
private val ComposerTrackedStyles: List<SpanStyle> = listOf(
    SpanStyle.Bold,
    SpanStyle.Italic,
    SpanStyle.Underline,
    SpanStyle.StrikeThrough,
    SpanStyle.InlineCode,
)

private val ComposerEditorHeight = 64.dp
private val SendButtonSize = 44.dp
private val FormatButtonSize = 38.dp

/**
 * The bottom composer: a [CascadeEditor] input with a formatting bar that fades in
 * while the editor is focused, plus a Send button that appends the composed comment
 * to the feed and clears focus.
 */
@Composable
internal fun CommentComposer(
    model: CommentsScreenModel,
    editorTheme: CascadeEditorTheme,
    modifier: Modifier = Modifier,
) {
    val editorState = model.editorState
    val textStates = model.textStates
    val spanStates = model.spanStates
    val focusManager = LocalFocusManager.current

    val editorConfig = remember {
        CascadeEditorConfig(
            blockSelectionEnabled = false,
            blockDraggingEnabled = false,
        )
    }

    val controller = rememberCascadeEditorToolbarController(
        stateHolder = editorState,
        textStates = textStates,
        spanStates = spanStates,
        trackedStyles = ComposerTrackedStyles,
        config = editorConfig,
    )

    val editorFocused by remember(editorState) {
        derivedStateOf { editorState.state.focusedBlockId != null }
    }
    val canSend by remember(editorState, textStates) {
        derivedStateOf {
            editorState.state.blocks.any { block ->
                !(textStates.getVisibleText(block.id) ?: "").isBlank()
            }
        }
    }

    val send = remember(model, focusManager) {
        {
            model.buildOwnComment()?.let(model::addComment)
            model.resetComposer()
            editorState.dispatch(ClearFocus)
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        AnimatedVisibility(
            visible = editorFocused,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            FormattingBar(controller = controller)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CascadeEditor(
                stateHolder = editorState,
                textStates = textStates,
                spanStates = spanStates,
                theme = editorTheme,
                toolbar = ToolbarSlot.None,
                slashCommand = SlashCommandSlot.None,
                linkPopup = LinkPopupSlot.None,
                config = editorConfig,
                modifier = Modifier
                    .weight(1f)
                    .height(ComposerEditorHeight),
            )

            SendButton(enabled = canSend, onClick = send)
        }
    }
}

@Composable
private fun FormattingBar(
    controller: CascadeEditorToolbarController,
) {
    val formattingState = controller.formattingState.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FormatButton(
            icon = Res.drawable.ic_format_bold,
            contentDescription = "Bold",
            status = formattingState.styleStatusOf(SpanStyle.Bold),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.Bold) },
        )
        FormatButton(
            icon = Res.drawable.ic_format_italic,
            contentDescription = "Italic",
            status = formattingState.styleStatusOf(SpanStyle.Italic),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.Italic) },
        )
        FormatButton(
            icon = Res.drawable.ic_format_underlined,
            contentDescription = "Underline",
            status = formattingState.styleStatusOf(SpanStyle.Underline),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.Underline) },
        )

        FormatDivider()

        FormatButton(
            icon = Res.drawable.ic_strikethrough_s,
            contentDescription = "Strikethrough",
            status = formattingState.styleStatusOf(SpanStyle.StrikeThrough),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.StrikeThrough) },
        )
        FormatButton(
            icon = Res.drawable.ic_code,
            contentDescription = "Inline code",
            status = formattingState.styleStatusOf(SpanStyle.InlineCode),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.InlineCode) },
        )
    }
}

@Composable
private fun FormatButton(
    icon: DrawableResource,
    contentDescription: String,
    status: StyleStatus,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val active = status == StyleStatus.FullyActive || status == StyleStatus.Partial
    val background = when {
        !enabled -> Color.Transparent
        active -> primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surface
    }
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        active -> primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Box(
        modifier = Modifier
            .size(FormatButtonSize)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FormatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}

@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = if (enabled) primary else primary.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .size(SendButtonSize)
            .clip(CircleShape)
            .background(background)
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = "Send comment" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_send),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier.size(20.dp),
        )
    }
}
