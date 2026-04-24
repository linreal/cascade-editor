package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.Immutable

/**
 * Top-level theme configuration for the CascadeEditor.
 *
 * Combines [colors], [typography], and [dimensions] into a single object passed to
 * `CascadeEditor(theme = ...)`. The editor never decides light vs. dark —
 * the consumer picks a preset or supplies custom values.
 *
 * Use [light] or [dark] for built-in presets.
 */
@Immutable
public data class CascadeEditorTheme(
    val colors: CascadeEditorColors,
    val typography: CascadeEditorTypography,
    val dimensions: CascadeEditorDimensions = CascadeEditorDimensions.default(),
) {
    public companion object {
        /** Light theme with default typography. */
        public fun light(): CascadeEditorTheme = CascadeEditorTheme(
            colors = CascadeEditorColors.light(),
            typography = CascadeEditorTypography.default(),
            dimensions = CascadeEditorDimensions.default(),
        )

        /** Dark theme with default typography. */
        public fun dark(): CascadeEditorTheme = CascadeEditorTheme(
            colors = CascadeEditorColors.dark(),
            typography = CascadeEditorTypography.default(),
            dimensions = CascadeEditorDimensions.default(),
        )
    }
}
