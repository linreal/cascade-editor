package io.github.linreal.cascade.screens.external_toolbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_arrow_back
import cascadeeditor.sample.generated.resources.ic_dark_mode
import cascadeeditor.sample.generated.resources.ic_edit
import cascadeeditor.sample.generated.resources.ic_edit_off
import cascadeeditor.sample.generated.resources.ic_light_mode
import org.jetbrains.compose.resources.painterResource

/**
 * Screen-level navigation and demo controls.
 */
@Composable
internal fun ExternalToolbarScreenHeader(
    isReadOnly: Boolean,
    isDark: Boolean,
    onBack: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ExternalToolbarTokens.HeaderBottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(ExternalToolbarTokens.HeaderIconButtonSize),
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.size(ExternalToolbarTokens.HeaderBackIconSize),
                )
            }
            Text(
                text = "External Toolbar",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleReadOnly,
                modifier = Modifier.size(ExternalToolbarTokens.HeaderIconButtonSize),
            ) {
                Image(
                    painter = painterResource(
                        if (isReadOnly) Res.drawable.ic_edit_off else Res.drawable.ic_edit
                    ),
                    contentDescription = if (isReadOnly) "Read-only" else "Editable",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(ExternalToolbarTokens.HeaderActionIconSize),
                )
            }
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.size(ExternalToolbarTokens.HeaderIconButtonSize),
            ) {
                Image(
                    painter = painterResource(
                        if (isDark) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode
                    ),
                    contentDescription = if (isDark) "Switch to light" else "Switch to dark",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(ExternalToolbarTokens.HeaderActionIconSize),
                )
            }
        }
    }
}
