package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography configuration for the CascadeEditor.
 *
 * Styles define font properties only (size, weight, family).
 * Color is always applied at use-site from [CascadeEditorColors] to avoid
 * duplication between light and dark themes.
 *
 * Use [default] for the built-in preset that matches the original hardcoded values.
 */
@Immutable
public data class CascadeEditorTypography(
    /** Paragraph, todo, and default body text (16sp). */
    val body: TextStyle,
    /** Heading level 1 (32sp). */
    val heading1: TextStyle,
    /** Heading level 2 (28sp). */
    val heading2: TextStyle,
    /** Heading level 3 (24sp). */
    val heading3: TextStyle,
    /** Heading level 4 (20sp). */
    val heading4: TextStyle,
    /** Heading level 5 (18sp). */
    val heading5: TextStyle,
    /** Heading level 6 (16sp). */
    val heading6: TextStyle,
    /** Code block text (14sp, Monospace). */
    val code: TextStyle,
    /** Slash popup item title (12sp). */
    val slashItemTitle: TextStyle,
    /** Slash popup back button (14sp). */
    val slashBackButton: TextStyle,
    /** Toolbar button base style (16sp, Medium weight). */
    val toolbarButton: TextStyle,
) {
    public companion object {
        /**
         * Default typography preset.
         *
         * Values match the original hardcoded sizes and font families.
         */
        public fun default(): CascadeEditorTypography = CascadeEditorTypography(
            body = TextStyle(fontSize = 16.sp),
            heading1 = TextStyle(fontSize = 32.sp),
            heading2 = TextStyle(fontSize = 28.sp),
            heading3 = TextStyle(fontSize = 24.sp),
            heading4 = TextStyle(fontSize = 20.sp),
            heading5 = TextStyle(fontSize = 18.sp),
            heading6 = TextStyle(fontSize = 16.sp),
            code = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            slashItemTitle = TextStyle(fontSize = 12.sp),
            slashBackButton = TextStyle(fontSize = 14.sp),
            toolbarButton = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
    }
}
