package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens for CascadeEditor layout.
 *
 * [indentUnit] is the single source of truth for visual outline depth. Renderers
 * multiply a supported block's indentation level by this value instead of
 * hardcoding indentation spacing in each block row.
 */
@Immutable
public data class CascadeEditorDimensions(
    val indentUnit: Dp,
    val blockHorizontalPadding: Dp,
) {
    public companion object {
        /** Default dimensions matching the editor's pre-dimensions block padding. */
        public fun default(): CascadeEditorDimensions = CascadeEditorDimensions(
            indentUnit = 24.dp,
            blockHorizontalPadding = 16.dp,
        )
    }
}
