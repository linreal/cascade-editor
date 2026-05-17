package io.github.linreal.cascade.screens.external_toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.richtext.LinkActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkValidationError
import io.github.linreal.cascade.editor.richtext.LinkValidationResult

/**
 * Remembers the host-owned link editor session used by the external toolbar.
 *
 * Link targets are captured when the toolbar button is pressed so the user can
 * type into the link form without relying on live editor selection staying stable.
 */
@Composable
internal fun rememberExternalLinkEditorState(): ExternalLinkEditorState =
    remember { ExternalLinkEditorState() }

/**
 * Mutable UI state for the external link editor.
 *
 * The editor library supplies [LinkState] and [LinkActions]; this state owns only
 * host chrome concerns such as field text, visibility, validation messages, and
 * the captured target for apply/remove actions.
 */
@Stable
internal class ExternalLinkEditorState {
    var visible by mutableStateOf(false)
        private set

    var url by mutableStateOf("")
        private set

    var title by mutableStateOf("")
        private set

    var canRemove by mutableStateOf(false)
        private set

    var validationError by mutableStateOf<LinkValidationError?>(null)
        private set

    private var target by mutableStateOf<LinkTarget?>(null)

    val canApply: Boolean
        get() = target != null

    fun open(linkState: LinkState) {
        val editingExistingLink = linkState.selectionCollapsed &&
            linkState.existingLinkRange != null

        target = if (editingExistingLink) {
            linkState.existingLinkRange
        } else {
            linkState.target
        }
        canRemove = linkState.existingUrl != null || linkState.intersectsLink
        url = linkState.existingUrl
            ?: linkState.targetText.takeIf { it.looksLikeUrl() }
            ?: ""
        title = if (editingExistingLink) {
            linkState.existingLinkText ?: linkState.targetText
        } else {
            linkState.targetText
        }
        validationError = null
        visible = true
    }

    fun onUrlChange(value: String) {
        url = value
        validationError = null
    }

    fun onTitleChange(value: String) {
        title = value
    }

    fun apply(linkActions: LinkActions) {
        val currentTarget = target
        if (currentTarget == null) {
            dismiss()
            return
        }

        when (
            val result = linkActions.applyLink(
                target = currentTarget,
                url = url,
                title = title.takeIf { it.isNotBlank() },
            )
        ) {
            is LinkValidationResult.Valid -> dismiss()
            is LinkValidationResult.Invalid -> validationError = result.error
        }
    }

    fun remove(linkActions: LinkActions) {
        target?.let(linkActions::removeLink)
        dismiss()
    }

    fun dismiss() {
        validationError = null
        target = null
        visible = false
    }
}

/**
 * Link editor panel rendered under the toolbar buttons.
 */
@Composable
internal fun ExternalLinkEditor(
    state: ExternalLinkEditorState,
    linkState: LinkState,
    linkActions: LinkActions,
) {
    ExternalLinkEditorContent(
        url = state.url,
        title = state.title,
        canApply = state.canApply && linkState.canLink,
        canRemove = state.canRemove && linkState.canLink,
        validationError = state.validationError,
        onUrlChange = state::onUrlChange,
        onTitleChange = state::onTitleChange,
        onApply = { state.apply(linkActions) },
        onRemove = { state.remove(linkActions) },
        onCancel = state::dismiss,
    )
}

@Composable
private fun ExternalLinkEditorContent(
    url: String,
    title: String,
    canApply: Boolean,
    canRemove: Boolean,
    validationError: LinkValidationError?,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ExternalToolbarTokens.LinkEditorHorizontalPadding,
                top = ExternalToolbarTokens.LinkEditorTopPadding,
                end = ExternalToolbarTokens.LinkEditorHorizontalPadding,
                bottom = ExternalToolbarTokens.LinkEditorBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(ExternalToolbarTokens.LinkEditorSpacing),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (validationError != null) {
            Text(
                text = linkValidationMessage(validationError),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canRemove) {
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            TextButton(
                enabled = canApply,
                onClick = onApply,
            ) {
                Text("Apply")
            }
        }
    }
}

private fun linkValidationMessage(error: LinkValidationError): String = when (error) {
    LinkValidationError.Blank -> "Enter a URL"
}

private fun String.looksLikeUrl(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.any { it.isWhitespace() }) return false
    return trimmed.contains("://") || trimmed.contains('.')
}
