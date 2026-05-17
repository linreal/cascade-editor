package io.github.linreal.cascade.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_arrow_back
import cascadeeditor.sample.generated.resources.ic_dark_mode
import cascadeeditor.sample.generated.resources.ic_edit
import cascadeeditor.sample.generated.resources.ic_edit_off
import cascadeeditor.sample.generated.resources.ic_light_mode
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.editor.ui.LocalCascadeEditorConfig
import io.github.linreal.cascade.editor.ui.ToolbarSlot
import io.github.linreal.cascade.editor.ui.visibleSelection
import io.github.linreal.cascade.ui.PageScaffold
import io.github.linreal.cascade.ui.nonFocusableTap
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock

private val TrackedStyles = listOf(
    SpanStyle.Bold,
    SpanStyle.Italic,
    SpanStyle.Underline,
    SpanStyle.StrikeThrough,
    SpanStyle.InlineCode,
)

// Screen

@Composable
fun CustomToolbarScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val editorTheme = if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()

    val textStates = remember { BlockTextStates() }
    val spanStates = remember { BlockSpanStates() }
    val editorState = rememberEditorState(buildToolbarDemoBlocks())
    var isReadOnly by remember { mutableStateOf(false) }

    PageScaffold {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Custom Toolbar",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isReadOnly = !isReadOnly },
                    modifier = Modifier.size(40.dp),
                ) {
                    Image(
                        painter = painterResource(
                            if (isReadOnly) Res.drawable.ic_edit_off else Res.drawable.ic_edit
                        ),
                        contentDescription = if (isReadOnly) "Read-only" else "Editable",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(40.dp),
                ) {
                    Image(
                        painter = painterResource(
                            if (isDark) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode
                        ),
                        contentDescription = if (isDark) "Switch to light" else "Switch to dark",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        CascadeEditor(
            stateHolder = editorState,
            textStates = textStates,
            spanStates = spanStates,
            theme = editorTheme,
            toolbar = ToolbarSlot.Custom(
                trackedStyles = TrackedStyles,
                content = { formattingState, actions ->
                    val editorConfig = LocalCascadeEditorConfig.current
                    WritersPaletteToolbar(
                        formattingState = formattingState,
                        actions = actions,
                        readOnly = editorConfig.readOnly,
                        onInsertDate = {
                            if (!editorConfig.readOnly) {
                                val blockId = formattingState.value.focusedBlockId
                                val tfs = blockId?.let { textStates.get(it) }
                                if (blockId != null && tfs != null) {
                                    val sel = tfs.visibleSelection()
                                    val now = Clock.System.now()
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                    val formatted = now.toString()
                                        .substringBefore('.')
                                        .replace('T', ' ')
                                    textStates.replaceVisibleRange(
                                        blockId = blockId,
                                        start = sel.min,
                                        endExclusive = sel.max,
                                        replacement = formatted,
                                    )
                                }
                            }
                        },
                    )
                },
            ),
            config = CascadeEditorConfig(readOnly = isReadOnly),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// Writer's Palette Toolbar

@Composable
private fun WritersPaletteToolbar(
    formattingState: State<FormattingState>,
    actions: FormattingActions,
    readOnly: Boolean,
    onInsertDate: () -> Unit,
) {
    val state = formattingState.value
    val canFormat = state.canFormat

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .background(
                surfaceColor,
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .graphicsLayer { alpha = if (canFormat) 1f else 0.4f }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Group 1: Text Style
        ButtonGroup {
            StyleToggleButton(
                label = "B",
                textStyle = TextStyle(fontWeight = FontWeight.Bold),
                status = state.styleStatusOf(SpanStyle.Bold),
                enabled = canFormat,
                onClick = { actions.toggleStyle(SpanStyle.Bold) },
            )
            StyleToggleButton(
                label = "I",
                textStyle = TextStyle(fontStyle = FontStyle.Italic),
                status = state.styleStatusOf(SpanStyle.Italic),
                enabled = canFormat,
                onClick = { actions.toggleStyle(SpanStyle.Italic) },
            )
            StyleToggleButton(
                label = "U",
                textStyle = TextStyle(textDecoration = TextDecoration.Underline),
                status = state.styleStatusOf(SpanStyle.Underline),
                enabled = canFormat,
                onClick = { actions.toggleStyle(SpanStyle.Underline) },
            )
        }

        // Group 2: Decoration
        ButtonGroup {
            StyleToggleButton(
                label = "S",
                textStyle = TextStyle(textDecoration = TextDecoration.LineThrough),
                status = state.styleStatusOf(SpanStyle.StrikeThrough),
                enabled = canFormat,
                onClick = { actions.toggleStyle(SpanStyle.StrikeThrough) },
            )
            StyleToggleButton(
                label = "<>",
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                status = state.styleStatusOf(SpanStyle.InlineCode),
                enabled = canFormat,
                onClick = { actions.toggleStyle(SpanStyle.InlineCode) },
            )
        }

        // Group 3: Insert
        ButtonGroup {
            ActionButton(
                label = "Date",
                enabled = canFormat && !readOnly,
                onClick = onInsertDate,
            )
        }

        // Clear button
        ClearFormattingButton(
            state = state,
            actions = actions,
        )
    }
}

// Button Group (pill container)

@Composable
private fun ButtonGroup(
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp),
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

// Style Toggle Button

@Composable
private fun StyleToggleButton(
    label: String,
    textStyle: TextStyle,
    status: StyleStatus,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val targetBackground = when {
        !enabled -> Color.Transparent
        status == StyleStatus.FullyActive -> primary
        status == StyleStatus.Partial -> primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val targetContentColor = when {
        !enabled -> onSurface.copy(alpha = 0.25f)
        status == StyleStatus.FullyActive -> onPrimary
        status == StyleStatus.Partial -> primary
        else -> onSurface.copy(alpha = 0.4f)
    }

    val targetBorderColor = when {
        !enabled -> Color.Transparent
        status == StyleStatus.FullyActive -> Color.Transparent
        status == StyleStatus.Partial -> primary.copy(alpha = 0.3f)
        else -> onSurface.copy(alpha = 0.15f)
    }

    val backgroundColor = animateColorAsState(targetBackground, tween(200))
    val contentColor = animateColorAsState(targetContentColor, tween(200))
    val borderColor = animateColorAsState(targetBorderColor, tween(200))

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape)
            .background(backgroundColor.value, shape)
            .then(
                if (borderColor.value != Color.Transparent) {
                    Modifier.border(1.dp, borderColor.value, shape)
                } else {
                    Modifier
                }
            )
            .nonFocusableTap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                fontStyle = textStyle.fontStyle,
                textDecoration = textStyle.textDecoration,
                fontFamily = textStyle.fontFamily,
                color = contentColor.value,
            ),
        )
    }
}

// Action Button (non-toggle)

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val contentColor = animateColorAsState(
        if (enabled) primary else onSurface.copy(alpha = 0.25f),
        tween(200),
    )

    val borderColor = animateColorAsState(
        if (enabled) primary.copy(alpha = 0.3f) else onSurface.copy(alpha = 0.15f),
        tween(200),
    )

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape)
            .border(1.dp, borderColor.value, shape)
            .nonFocusableTap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.value,
            ),
        )
    }
}

// Clear Formatting Button

@Composable
private fun ClearFormattingButton(
    state: FormattingState,
    actions: FormattingActions,
) {
    val hasAnyFormatting = state.styles.any { (_, status) ->
        status != StyleStatus.Absent
    }
    val enabled = state.canFormat && hasAnyFormatting

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val contentColor = animateColorAsState(
        if (enabled) primary else onSurface.copy(alpha = 0.25f),
        tween(200),
    )

    val borderColor = animateColorAsState(
        if (enabled) primary.copy(alpha = 0.3f) else onSurface.copy(alpha = 0.1f),
        tween(200),
    )

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape)
            .border(1.dp, borderColor.value, shape)
            .nonFocusableTap(enabled = enabled) {
                state.styles.forEach { (style, status) ->
                    if (status != StyleStatus.Absent) {
                        actions.removeStyle(style)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "CLR",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = contentColor.value,
            ),
        )
    }
}

// Demo Document

private fun buildToolbarDemoBlocks(): List<Block> = listOf(
    Block.heading(1, "Writer's Palette"),
    Block.paragraph(
        "A custom toolbar built with ToolbarSlot.Custom \u2014 grouped buttons, " +
                "animated states, and a date insertion button."
    ),
    Block.paragraph(""),

    Block.heading(2, "Three-State Feedback"),
    Block.paragraph(
        text = "Select the word bold below to see the solid fill state.",
    ),
    Block.paragraph(
        text = "This word is bold and this is not \u2014 select across both to see the partial state.",
        spans = listOf(
            TextSpan(start = 13, end = 17, style = SpanStyle.Bold),
        ),
    ),
    Block.paragraph(""),

    Block.heading(2, "Text Decorations"),
    Block.paragraph(
        text = "Try strikethrough, inline code, and underline on any text you like.",
        spans = listOf(
            TextSpan(start = 4, end = 17, style = SpanStyle.StrikeThrough),
            TextSpan(start = 19, end = 30, style = SpanStyle.InlineCode),
            TextSpan(start = 36, end = 45, style = SpanStyle.Underline),
        ),
    ),
    Block.paragraph(""),

    Block.heading(2, "Insert Date"),
    Block.paragraph(
        "Tap the Date button in the toolbar to insert the current date and time at the cursor."
    ),
    Block.paragraph("Try it here: "),
    Block.paragraph(""),

    Block.heading(2, "Clear Formatting"),
    Block.paragraph(
        text = "This paragraph has bold, italic, and strikethrough \u2014 select and then tap CLR to remove them all.",
        spans = listOf(
            TextSpan(start = 19, end = 23, style = SpanStyle.Bold),
            TextSpan(start = 25, end = 31, style = SpanStyle.Italic),
            TextSpan(start = 37, end = 50, style = SpanStyle.StrikeThrough),
        ),
    ),
    Block.paragraph(""),
    Block.paragraph(""),
)
