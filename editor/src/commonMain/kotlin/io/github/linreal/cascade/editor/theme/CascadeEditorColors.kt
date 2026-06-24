package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Color palette for the CascadeEditor.
 *
 * Each slot controls exactly one visual element, giving consumers full
 * granularity over the editor's appearance. The editor itself never
 * decides light vs. dark — the consumer chooses a preset or provides
 * custom values.
 *
 * Use [light] or [dark] for built-in presets.
 */
@Immutable
public data class CascadeEditorColors(
    /** Accent color: active toolbar button background, drop indicator, slash popup back button text. */
    val primary: Color,
    /** Content rendered on [primary]-colored surfaces (e.g. active toolbar icon). */
    val onPrimary: Color,
    /** Default body text color (paragraphs, headings, list items). */
    val text: Color,
    /** Slash command popup background. */
    val popupBackground: Color,
    /** Unknown/error block background. */
    val unknownBlockBackground: Color,
    /** Enabled toolbar icon color. */
    val toolbarIcon: Color,
    /** Disabled toolbar icon color. */
    val toolbarIconDisabled: Color,
    /** Slash command row title text. */
    val slashItemTitle: Color,
    /** Slash command submenu chevron icon. */
    val slashChevron: Color,
    /** Unknown block error message text. */
    val unknownBlockText: Color,
    /** Editor's UI divider lines (toolbar divider, slash popup dividers). */
    val uiDivider: Color,
    /** Divider block line color. */
    val contentDivider: Color,
    /** Background of the currently selected slash command row. */
    val slashSelectedItem: Color,
    /** Inline code span background tint. */
    val inlineCodeBackground: Color,
    /** Default text highlight color (used by [SpanStyle.Highlight]). */
    val highlight: Color,
    /** Text cursor (caret) color. */
    val cursor: Color,
    /** Text selection background color. */
    val textSelectionBackground: Color,
    /** Quote block left border stripe color. */
    val quoteBorder: Color,
    /** Quote block background tint. */
    val quoteBackground: Color,
    /** Semi-transparent overlay drawn behind selected blocks. */
    val selectionOverlay: Color,
    /**
     * Link text color used by [SpanStyle.Link] rendering.
     *
     * Defaults to [primary] only when constructing [CascadeEditorColors];
     * data-class [copy] retains the existing link color unless set explicitly.
     */
    val linkText: Color = primary,
    /**
     * Error/validation message color (link popup URL validation errors, etc.).
     *
     * Defaults to a Material-flavored red only when constructing
     * [CascadeEditorColors]; data-class [copy] retains the existing value
     * unless set explicitly.
     */
    val error: Color = Color(0xFFB3261E),
    /**
     * Block-level code surface tint used by `BlockType.Code` rendering.
     *
     * Distinct from [inlineCodeBackground] because the inline pill and the
     * full block surface typically want different shades — the inline pill
     * needs more visual punch to stand out within prose, while the larger
     * block surface reads better with a softer tint that doesn't compete
     * with the code text contrast.
     *
     * Defaults to [inlineCodeBackground] only when constructing
     * [CascadeEditorColors]; data-class [copy] retains the existing value
     * unless set explicitly.
     */
    val codeBlockBackground: Color = inlineCodeBackground,
    /**
     * Floating toolbar pill surface (default rich-text toolbar background).
     *
     * Defaults to [popupBackground] only when constructing [CascadeEditorColors];
     * data-class [copy] retains the existing value unless set explicitly.
     */
    val toolbarBackground: Color = popupBackground,
) {
    public companion object {
        /**
         * Light color preset.
         *
         * Values match the original hardcoded colors for a visual no-op
         * after migration.
         */
        public fun light(): CascadeEditorColors = CascadeEditorColors(
            primary = Color(0xFF1A73E8),
            onPrimary = Color.White,
            text = Color(0xFF1B1B1F),
            popupBackground = Color.White,
            unknownBlockBackground = Color(0xFFF0F0F0),
            toolbarIcon = Color(0xFF333333),
            toolbarIconDisabled = Color(0xFF999999),
            slashItemTitle = Color(0xFF212121),
            slashChevron = Color(0xFF9E9E9E),
            unknownBlockText = Color(0xFF888888),
            uiDivider = Color(0xFFE0E0E0),
            contentDivider = Color(0xFFE0E0E0),
            slashSelectedItem = Color(0xFFE3F2FD),
            inlineCodeBackground = Color(0x14000000),
            highlight = Color(0xCCFFEB3B),
            cursor = Color(0xFF1A73E8),
            textSelectionBackground = Color(0x661A73E8),
            quoteBorder = Color(0xFFBDBDBD),
            quoteBackground = Color(0x0A000000),
            selectionOverlay = Color(0x221A73E8),
            linkText = Color(0xFF1A73E8),
            error = Color(0xFFB3261E),
            codeBlockBackground = Color(0x0F000000),
            toolbarBackground = Color.White,
        )

        /**
         * Dark color preset.
         *
         * Provides reasonable dark-mode defaults. Consumers can refine
         * individual slots via [copy].
         */
        public fun dark(): CascadeEditorColors = CascadeEditorColors(
            primary = Color(0xFF8AB4F8),
            onPrimary = Color(0xFF1B1B1F),
            text = Color(0xFFE8E8ED),
            popupBackground = Color(0xFF1E1E28),
            unknownBlockBackground = Color(0xFF3A3A3E),
            toolbarIcon = Color(0xFFE0E0E0),
            toolbarIconDisabled = Color(0xFF666666),
            slashItemTitle = Color(0xFFE0E0E0),
            slashChevron = Color(0xFF888888),
            unknownBlockText = Color(0xFF999999),
            uiDivider = Color(0xFF3E3E42),
            contentDivider = Color(0xFF3E3E42),
            slashSelectedItem = Color(0xFF1A3352),
            inlineCodeBackground = Color(0x29FFFFFF),
            highlight = Color(0x99FFEB3B),
            cursor = Color(0xFF8AB4F8),
            textSelectionBackground = Color(0x668AB4F8),
            quoteBorder = Color(0xFF757575),
            quoteBackground = Color(0x14FFFFFF),
            selectionOverlay = Color(0x8000e2ff),
            linkText = Color(0xFF8AB4F8),
            error = Color(0xFFF2B8B5),
            codeBlockBackground = Color(0x1FFFFFFF),
            toolbarBackground = Color(0xFF26262E),
        )
    }
}
