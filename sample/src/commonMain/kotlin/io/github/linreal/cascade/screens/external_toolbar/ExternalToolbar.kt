package io.github.linreal.cascade.screens.external_toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_code
import cascadeeditor.sample.generated.resources.ic_format_bold
import cascadeeditor.sample.generated.resources.ic_format_indent_decrease
import cascadeeditor.sample.generated.resources.ic_format_indent_increase
import cascadeeditor.sample.generated.resources.ic_format_italic
import cascadeeditor.sample.generated.resources.ic_format_list_bulleted
import cascadeeditor.sample.generated.resources.ic_format_list_numbered
import cascadeeditor.sample.generated.resources.ic_hide_keyboard
import cascadeeditor.sample.generated.resources.ic_link
import cascadeeditor.sample.generated.resources.ic_strikethrough_s
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.indentation.IndentationState
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.CascadeEditorToolbarController
import io.github.linreal.cascade.ui.nonFocusableTap
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * App-owned toolbar chrome rendered outside [io.github.linreal.cascade.editor.ui.CascadeEditor].
 */
@Composable
internal fun ExternalToolbar(
    controller: CascadeEditorToolbarController,
    editorState: EditorStateHolder,
    isReadOnly: Boolean,
    onHideKeyboard: () -> Unit,
    onToggleBasicList: () -> Unit,
    onToggleNumberedList: () -> Unit,
) {
    val formattingState = controller.formattingState.value
    val indentationState = controller.indentationState.value
    val linkState = controller.linkState.value
    val focusedBlockType = editorState.state.focusedBlock?.type
    val canConvertFocusedBlock = !isReadOnly &&
        !editorState.state.hasSelection &&
        editorState.state.dragState == null &&
        focusedBlockType?.isConvertible == true
    val linkEditorState = rememberExternalLinkEditorState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = ExternalToolbarTokens.ToolbarShadowElevation,
                shape = ExternalToolbarTokens.ToolbarShape,
                clip = false,
            )
            .clip(ExternalToolbarTokens.ToolbarShape)
            .background(
                ExternalToolbarTokens.ToolbarSurfaceColor,
                ExternalToolbarTokens.ToolbarShape,
            )
            .border(
                width = 1.dp,
                color = ExternalToolbarTokens.ToolbarBorderColor,
                shape = ExternalToolbarTokens.ToolbarShape,
            ),
    ) {
        ExternalToolbarButtonStrip(
            groups = externalToolbarButtonGroups(
                controller = controller,
                formattingState = formattingState,
                indentationState = indentationState,
                linkState = linkState,
                focusedBlockType = focusedBlockType,
                canConvertFocusedBlock = canConvertFocusedBlock,
                onToggleBasicList = onToggleBasicList,
                onToggleNumberedList = onToggleNumberedList,
                onOpenLinkEditor = { linkEditorState.open(linkState) },
            ),
            onHideKeyboard = onHideKeyboard,
        )

        AnimatedVisibility(
            visible = linkEditorState.visible,
            enter = slideInVertically(
                animationSpec = tween(ExternalToolbarTokens.LinkEditorEnterMillis),
                initialOffsetY = { -it },
            ) + expandVertically(
                animationSpec = tween(ExternalToolbarTokens.LinkEditorEnterMillis),
                expandFrom = Alignment.Top,
            ),
            exit = slideOutVertically(
                animationSpec = tween(ExternalToolbarTokens.LinkEditorExitMillis),
                targetOffsetY = { -it },
            ) + shrinkVertically(
                animationSpec = tween(ExternalToolbarTokens.LinkEditorExitMillis),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            ExternalLinkEditor(
                state = linkEditorState,
                linkState = linkState,
                linkActions = controller.linkActions,
            )
        }
    }
}

private data class ExternalToolbarButton(
    val icon: DrawableResource,
    val contentDescription: String,
    val status: StyleStatus = StyleStatus.Absent,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private fun externalToolbarButtonGroups(
    controller: CascadeEditorToolbarController,
    formattingState: FormattingState,
    indentationState: IndentationState,
    linkState: LinkState,
    focusedBlockType: BlockType?,
    canConvertFocusedBlock: Boolean,
    onToggleBasicList: () -> Unit,
    onToggleNumberedList: () -> Unit,
    onOpenLinkEditor: () -> Unit,
): List<List<ExternalToolbarButton>> = listOf(
    listOf(
        ExternalToolbarButton(
            icon = Res.drawable.ic_format_bold,
            contentDescription = "Bold",
            status = formattingState.styleStatusOf(SpanStyle.Bold),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.Bold) },
        ),
        ExternalToolbarButton(
            icon = Res.drawable.ic_format_italic,
            contentDescription = "Italic",
            status = formattingState.styleStatusOf(SpanStyle.Italic),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.Italic) },
        ),
        ExternalToolbarButton(
            icon = Res.drawable.ic_strikethrough_s,
            contentDescription = "Strikethrough",
            status = formattingState.styleStatusOf(SpanStyle.StrikeThrough),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.StrikeThrough) },
        ),
        ExternalToolbarButton(
            icon = Res.drawable.ic_code,
            contentDescription = "Code span",
            status = formattingState.styleStatusOf(SpanStyle.InlineCode),
            enabled = formattingState.canFormat,
            onClick = { controller.formattingActions.toggleStyle(SpanStyle.InlineCode) },
        ),
    ),
    listOf(
        ExternalToolbarButton(
            icon = Res.drawable.ic_format_list_bulleted,
            contentDescription = "Basic list",
            status = if (focusedBlockType == BlockType.BulletList) {
                StyleStatus.FullyActive
            } else {
                StyleStatus.Absent
            },
            enabled = canConvertFocusedBlock,
            onClick = onToggleBasicList,
        ),
        ExternalToolbarButton(
            icon = Res.drawable.ic_format_list_numbered,
            contentDescription = "Numbered list",
            status = if (focusedBlockType is BlockType.NumberedList) {
                StyleStatus.FullyActive
            } else {
                StyleStatus.Absent
            },
            enabled = canConvertFocusedBlock,
            onClick = onToggleNumberedList,
        ),
    ),
    listOf(
        ExternalToolbarButton(
            icon = Res.drawable.ic_format_indent_decrease,
            contentDescription = "Indent backward",
            enabled = indentationState.canIndentBackward,
            onClick = controller.indentationActions::indentBackward,
        ),
        ExternalToolbarButton(
            icon = Res.drawable.ic_format_indent_increase,
            contentDescription = "Indent forward",
            enabled = indentationState.canIndentForward,
            onClick = controller.indentationActions::indentForward,
        ),
    ),
    listOf(
        ExternalToolbarButton(
            icon = Res.drawable.ic_link,
            contentDescription = "Link",
            status = linkButtonStatus(linkState),
            enabled = linkState.canLink && linkState.target != null,
            onClick = onOpenLinkEditor,
        ),
    ),
)

@Composable
private fun ExternalToolbarButtonStrip(
    groups: List<List<ExternalToolbarButton>>,
    onHideKeyboard: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            groups.forEachIndexed { index, group ->
                ToolbarButtonGroup {
                    group.forEach { button ->
                        ExternalToolbarIconButton(
                            icon = button.icon,
                            contentDescription = button.contentDescription,
                            status = button.status,
                            enabled = button.enabled,
                            onClick = button.onClick,
                        )
                    }
                }

                if (index != groups.lastIndex) {
                    ToolbarGroupDivider()
                }
            }

            Spacer(modifier = Modifier.width(ExternalToolbarTokens.ToolbarButtonSize))
        }

        ToolbarGroupDivider(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = ExternalToolbarTokens.ToolbarButtonSize),
        )
        ExternalToolbarIconButton(
            icon = Res.drawable.ic_hide_keyboard,
            contentDescription = "Hide keyboard",
            status = StyleStatus.Absent,
            enabled = true,
            onClick = onHideKeyboard,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .background(ExternalToolbarTokens.ToolbarSurfaceColor),
        )
    }
}

@Composable
private fun ToolbarButtonGroup(
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun ToolbarGroupDivider(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(ExternalToolbarTokens.ToolbarButtonSize)
            .background(ExternalToolbarTokens.ToolbarDividerColor),
    )
}

@Composable
private fun ExternalToolbarIconButton(
    icon: DrawableResource,
    contentDescription: String,
    status: StyleStatus,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val targetBackground = when {
        !enabled -> Color.Transparent
        status == StyleStatus.FullyActive -> primary.copy(alpha = 0.5f)
        status == StyleStatus.Partial -> primary.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val targetContent = when {
        !enabled -> ExternalToolbarTokens.ToolbarDisabledIconColor
        status == StyleStatus.Partial -> primary
        else -> ExternalToolbarTokens.ToolbarIconColor
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(ExternalToolbarTokens.FocusAnimationMillis),
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(ExternalToolbarTokens.FocusAnimationMillis),
    )

    Box(
        modifier = modifier
            .sizeIn(
                minWidth = ExternalToolbarTokens.ToolbarButtonSize,
                minHeight = ExternalToolbarTokens.ToolbarButtonSize,
            )
            .clip(ExternalToolbarTokens.ToolbarButtonShape)
            .background(backgroundColor, ExternalToolbarTokens.ToolbarButtonShape)
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier.size(ExternalToolbarTokens.ToolbarIconSize),
        )
    }
}

private fun linkButtonStatus(linkState: LinkState): StyleStatus = when {
    linkState.existingUrl != null -> StyleStatus.FullyActive
    linkState.intersectsLink -> StyleStatus.Partial
    else -> StyleStatus.Absent
}
