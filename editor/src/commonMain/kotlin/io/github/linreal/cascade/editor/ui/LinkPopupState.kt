package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.richtext.LinkValidationError

/**
 * UI-ready state for an editor-managed link popup.
 *
 * Custom popup content receives this state from the editor-owned popup session
 * so it can render title, URL, validation, and action availability without
 * re-resolving the current editor selection.
 */
@Immutable
public data class LinkPopupState(
    val title: String,
    val url: String,
    val normalizedUrl: String?,
    val validationError: LinkValidationError?,
    val existingUrl: String?,
    val canApply: Boolean,
    val canRemove: Boolean,
)
