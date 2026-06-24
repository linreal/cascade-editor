package io.github.linreal.cascade.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_arrow_back
import cascadeeditor.sample.generated.resources.ic_dark_mode
import cascadeeditor.sample.generated.resources.ic_delete
import cascadeeditor.sample.generated.resources.ic_edit
import cascadeeditor.sample.generated.resources.ic_edit_off
import cascadeeditor.sample.generated.resources.ic_light_mode
import cascadeeditor.sample.generated.resources.ic_redo
import cascadeeditor.sample.generated.resources.ic_undo
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Shared chrome for the sample editor screens. The top bar mirrors the
// "Cascade Editor Chrome Options" design: a floating back chip on the left, a
// grouped soft-container of editor actions in the center, and an accented
// Reset pill on the right. The design's fixed purple palette is mapped onto
// MaterialTheme.colorScheme so both light and dark themes stay coherent.

private val ChipShape = RoundedCornerShape(12.dp)
private val GroupShape = RoundedCornerShape(14.dp)
private val GroupButtonShape = RoundedCornerShape(10.dp)
private val PillShape = RoundedCornerShape(percent = 50)

// Success accent for the "Saved" indicator; intentionally fixed since M3 has
// no semantic success color.
private val SavedGreen = Color(0xFF34C77B)

@Composable
internal fun EditorTopBar(
    isReadOnly: Boolean,
    isDark: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onToggleTheme: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeChip(
            icon = Res.drawable.ic_arrow_back,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onBack,
        )
        ChromeActionGroup {
            GroupButton(
                icon = Res.drawable.ic_undo,
                contentDescription = "Undo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (!isReadOnly && canUndo) 1f else 0.38f,
                ),
                enabled = !isReadOnly && canUndo,
                onClick = onUndo,
            )
            GroupButton(
                icon = Res.drawable.ic_redo,
                contentDescription = "Redo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (!isReadOnly && canRedo) 1f else 0.38f,
                ),
                enabled = !isReadOnly && canRedo,
                onClick = onRedo,
            )
            GroupDivider()
            EditModeButton(isReadOnly = isReadOnly, onClick = onToggleReadOnly)
            ThemeButton(isDark = isDark, onClick = onToggleTheme)
        }
        ResetPill(enabled = !isReadOnly, onClick = onReset)
    }
}

// Titled variant for screens without undo/redo or reset (e.g. the custom
// toolbar demo): back chip plus screen title on the left, and the same grouped
// soft-container of edit/theme actions on the right.
@Composable
internal fun TitledEditorTopBar(
    title: String,
    isReadOnly: Boolean,
    isDark: Boolean,
    onBack: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChromeChip(
                icon = Res.drawable.ic_arrow_back,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        ChromeActionGroup {
            EditModeButton(isReadOnly = isReadOnly, onClick = onToggleReadOnly)
            ThemeButton(isDark = isDark, onClick = onToggleTheme)
        }
    }
}

@Composable
internal fun SelectionTopBar(
    selectedCount: Int,
    isReadOnly: Boolean,
    onCancelSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancelSelection) {
                Text("Cancel")
            }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        ChromeChip(
            icon = Res.drawable.ic_delete,
            contentDescription = "Delete selected blocks",
            tint = MaterialTheme.colorScheme.error.copy(alpha = if (isReadOnly) 0.38f else 1f),
            enabled = !isReadOnly,
            onClick = onDeleteSelected,
        )
    }
}

// Floating "Saved" indicator that sits below the top bar and fades in/out with
// the save status. Only EditorDemoScreen drives this; other screens omit it.
@Composable
internal fun SavedPill(
    status: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = status.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .shadow(6.dp, PillShape)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(SavedGreen.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SavedGreen),
                )
            }
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Floating indicator that echoes the last opened link, mirroring the Saved pill
// styling so the top bar stays uncluttered.
@Composable
internal fun OpenedLinkPill(
    link: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = link.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .shadow(6.dp, PillShape)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = "Opened: $link",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChromeChip(
    icon: DrawableResource,
    contentDescription: String,
    tint: Color,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(if (enabled) 3.dp else 0.dp, ChipShape)
            .clip(ChipShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(iconSize),
        )
    }
}

// Grouped soft-container that holds the editor action buttons.
@Composable
private fun ChromeActionGroup(
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .shadow(3.dp, GroupShape)
            .clip(GroupShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
private fun EditModeButton(
    isReadOnly: Boolean,
    onClick: () -> Unit,
) {
    GroupButton(
        icon = if (isReadOnly) Res.drawable.ic_edit_off else Res.drawable.ic_edit,
        contentDescription = if (isReadOnly) "Read-only" else "Editable",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
    )
}

@Composable
private fun ThemeButton(
    isDark: Boolean,
    onClick: () -> Unit,
) {
    GroupButton(
        icon = if (isDark) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode,
        contentDescription = if (isDark) "Switch to light" else "Switch to dark",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
    )
}

@Composable
private fun GroupButton(
    icon: DrawableResource,
    contentDescription: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(GroupButtonShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GroupDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun ResetPill(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = Modifier
            .shadow(if (enabled) 6.dp else 0.dp, PillShape)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_redo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "Reset",
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
