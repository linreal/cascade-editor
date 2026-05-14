package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Immutable

/**
 * Internal capability model derived from [CascadeEditorConfig].
 *
 * Built-in editor code depends on named capabilities instead of reading raw
 * config flags. That keeps future policy checks centralized and makes each
 * call site describe the interaction it is guarding.
 */
@Immutable
internal data class EditorInteractionPolicy(
    val canEditText: Boolean,
    val canEditBlockStructure: Boolean,
    val canEditBlockControls: Boolean,
    val canFormatText: Boolean,
    val canEditLinks: Boolean,
    val canUseEditorHistoryShortcuts: Boolean,
    val canUseSlashCommands: Boolean,
    val canSelectBlocks: Boolean,
    val canDragBlocks: Boolean,
) {
    internal companion object {
        internal val Editable: EditorInteractionPolicy = EditorInteractionPolicy(
            canEditText = true,
            canEditBlockStructure = true,
            canEditBlockControls = true,
            canFormatText = true,
            canEditLinks = true,
            canUseEditorHistoryShortcuts = true,
            canUseSlashCommands = true,
            canSelectBlocks = true,
            canDragBlocks = true,
        )

        internal val ReadOnly: EditorInteractionPolicy = EditorInteractionPolicy(
            canEditText = false,
            canEditBlockStructure = false,
            canEditBlockControls = false,
            canFormatText = false,
            canEditLinks = false,
            canUseEditorHistoryShortcuts = false,
            canUseSlashCommands = false,
            canSelectBlocks = false,
            canDragBlocks = false,
        )
    }
}

/**
 * Converts public editor configuration into the internal interaction policy.
 *
 * This is the only built-in code path that should inspect [CascadeEditorConfig.readOnly].
 */
internal fun CascadeEditorConfig.toInteractionPolicy(): EditorInteractionPolicy {
    return if (readOnly) {
        EditorInteractionPolicy.ReadOnly
    } else {
        EditorInteractionPolicy.Editable
    }
}
