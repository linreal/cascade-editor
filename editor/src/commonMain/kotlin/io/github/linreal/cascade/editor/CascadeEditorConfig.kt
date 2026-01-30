package io.github.linreal.cascade.editor

import androidx.compose.runtime.Immutable

/**
 * Configuration options for the Cascade editor.
 */
@Immutable
public data class CascadeEditorConfig(
    /**
     * Whether to show drag handles for block reordering.
     */
    val showDragHandles: Boolean = true,

    /**
     * Whether to enable multi-selection via long press.
     */
    val enableMultiSelection: Boolean = true,

    /**
     * Whether to enable slash commands for block type conversion.
     */
    val enableSlashCommands: Boolean = true,

) {
    public companion object {
        /**
         * Default editor configuration.
         */
        public val Default: CascadeEditorConfig = CascadeEditorConfig()
    }
}
