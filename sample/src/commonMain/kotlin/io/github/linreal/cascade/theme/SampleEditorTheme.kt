package io.github.linreal.cascade.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.linreal.cascade.editor.theme.CascadeEditorColors
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.theme.CascadeEditorTypography

/**
 * Sample-app override of the editor's [CascadeEditorTheme], retinted to the
 * violet design system so the editor surface is coherent with
 * [CascadeSampleColors] (Landing screen, app chrome).
 *
 * This intentionally lives in the sample module — the published editor presets
 * ([CascadeEditorColors.light] / [dark]) are left untouched. It overrides the
 * color slots and retypes editor *content* in the bundled [geistFontFamily]
 * (body + headings); `code` keeps its monospace face. Dimensions keep the
 * editor defaults.
 *
 * Reuse across demo screens via [light] / [dark]:
 * ```
 * val editorTheme = if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()
 * ```
 */
object SampleEditorTheme {

    /** Light violet editor theme. */
    @Composable
    fun light(): CascadeEditorTheme {
        val base = CascadeEditorTheme.light()
        return base.copy(colors = lightColors(), typography = base.typography.withGeist())
    }

    /** Dark violet editor theme. */
    @Composable
    fun dark(): CascadeEditorTheme {
        val base = CascadeEditorTheme.dark()
        return base.copy(colors = darkColors(), typography = base.typography.withGeist())
    }

    /**
     * Retypes editor content in custom font: applies the family to body and all six
     * heading levels while preserving each style's size and weight. `code` is
     * left untouched so code blocks stay monospace, as do the slash/toolbar UI
     * chrome styles.
     */
    @Composable
    private fun CascadeEditorTypography.withGeist(): CascadeEditorTypography {
        val geist = geistFontFamily()
        return copy(
            body = body.copy(fontFamily = geist),
            heading1 = heading1.copy(fontFamily = geist),
            heading2 = heading2.copy(fontFamily = geist),
            heading3 = heading3.copy(fontFamily = geist),
            heading4 = heading4.copy(fontFamily = geist),
            heading5 = heading5.copy(fontFamily = geist),
            heading6 = heading6.copy(fontFamily = geist),
        )
    }

    private fun lightColors(): CascadeEditorColors = CascadeEditorColors.light().copy(
        primary = Color(0xFF6C3DE8),
        onPrimary = Color.White,
        text = Color(0xFF1C1238),
        popupBackground = Color(0xFFFFFFFF),
        unknownBlockBackground = Color(0xFFF0E9FE),
        toolbarIcon = Color(0xFF5A5470),
        toolbarIconDisabled = Color(0xFFB7ADD0),
        slashItemTitle = Color(0xFF1C1238),
        slashChevron = Color(0xFFCBB8EC),
        unknownBlockText = Color(0xFF6B6580),
        uiDivider = Color(0xFFECE4FB),
        contentDivider = Color(0xFFE4DAFB),
        slashSelectedItem = Color(0xFFF0E9FE),
        inlineCodeBackground = Color(0xFFEDE6FB),
        cursor = Color(0xFF6C3DE8),
        textSelectionBackground = Color(0x666C3DE8),
        quoteBorder = Color(0xFFCBB8EC),
        quoteBackground = Color(0x0A6C3DE8),
        selectionOverlay = Color(0x226C3DE8),
        linkText = Color(0xFF6C3DE8),
        codeBlockBackground = Color(0x0F6C3DE8),
    )

    private fun darkColors(): CascadeEditorColors = CascadeEditorColors.dark().copy(
        primary = Color(0xFFA78BFA),
        onPrimary = Color(0xFF1B1230),
        text = Color(0xFFF4F1FB),
        popupBackground = Color(0xFF1E1832),
        unknownBlockBackground = Color(0xFF251C3D),
        toolbarIcon = Color(0xFFA99FC4),
        toolbarIconDisabled = Color(0xFF5A5278),
        slashItemTitle = Color(0xFFFFFFFF),
        slashChevron = Color(0xFF5A5278),
        unknownBlockText = Color(0xFF8A82A6),
        uiDivider = Color(0x12FFFFFF),
        contentDivider = Color(0x14FFFFFF),
        slashSelectedItem = Color(0x1F8B5CF6),
        inlineCodeBackground = Color(0x338B5CF6),
        cursor = Color(0xFFA78BFA),
        textSelectionBackground = Color(0x66A78BFA),
        quoteBorder = Color(0xFF5A5278),
        quoteBackground = Color(0x14FFFFFF),
        selectionOverlay = Color(0x4DA78BFA),
        linkText = Color(0xFFC4B5FD),
        codeBlockBackground = Color(0x66000000),
    )
}
