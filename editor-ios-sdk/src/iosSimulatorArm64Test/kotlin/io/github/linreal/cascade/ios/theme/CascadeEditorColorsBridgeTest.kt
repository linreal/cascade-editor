package io.github.linreal.cascade.ios.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.linreal.cascade.editor.theme.CascadeEditorColors as CoreCascadeEditorColors
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.ios.controller.CascadeEditorController
import io.github.linreal.cascade.ios.controller.resolveEditorTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CascadeEditorColorsBridgeTest {
    @Test
    fun constructorsExposeCompleteBuiltInPresets() {
        assertEquals(CoreCascadeEditorColors.light(), CascadeEditorColors().snapshot())
        assertEquals(CoreCascadeEditorColors.light(), CascadeEditorColors(isDark = false).snapshot())
        assertEquals(CoreCascadeEditorColors.dark(), CascadeEditorColors(isDark = true).snapshot())
    }

    @Test
    fun everySwiftFacingSlotMapsToTheCorePalette() {
        val colors = CascadeEditorColors()
        colors.primary = 0xFF000001L
        colors.onPrimary = 0xFF000002L
        colors.text = 0xFF000003L
        colors.popupBackground = 0xFF000004L
        colors.unknownBlockBackground = 0xFF000005L
        colors.toolbarIcon = 0xFF000006L
        colors.toolbarIconDisabled = 0xFF000007L
        colors.slashItemTitle = 0xFF000008L
        colors.slashChevron = 0xFF000009L
        colors.unknownBlockText = 0xFF00000AL
        colors.uiDivider = 0xFF00000BL
        colors.contentDivider = 0xFF00000CL
        colors.slashSelectedItem = 0xFF00000DL
        colors.inlineCodeBackground = 0xFF00000EL
        colors.highlight = 0xFF00000FL
        colors.cursor = 0xFF000010L
        colors.textSelectionBackground = 0xFF000011L
        colors.quoteBorder = 0xFF000012L
        colors.quoteBackground = 0xFF000013L
        colors.selectionOverlay = 0xFF000014L
        colors.linkText = 0xFF000015L
        colors.error = 0xFF000016L
        colors.codeBlockBackground = 0xFF000017L
        colors.toolbarBackground = 0xFF000018L

        assertEquals(
            listOf(
                0xFF000001L,
                0xFF000002L,
                0xFF000003L,
                0xFF000004L,
                0xFF000005L,
                0xFF000006L,
                0xFF000007L,
                0xFF000008L,
                0xFF000009L,
                0xFF00000AL,
                0xFF00000BL,
                0xFF00000CL,
                0xFF00000DL,
                0xFF00000EL,
                0xFF00000FL,
                0xFF000010L,
                0xFF000011L,
                0xFF000012L,
                0xFF000013L,
                0xFF000014L,
                0xFF000015L,
                0xFF000016L,
                0xFF000017L,
                0xFF000018L,
            ),
            colors.snapshot().argbSlots(),
        )
    }

    @Test
    fun controllerSnapshotsRuntimeColorsAndCanRestorePresetSelection() {
        val controller = CascadeEditorController()
        val colors = CascadeEditorColors()
        colors.primary = 0xFF123456L

        controller.setColors(colors)
        val applied = controller.customColorsSnapshot.value
        assertEquals(0xFF123456L, applied?.primary?.toArgbLong())
        assertEquals(
            applied,
            controller.configurationSnapshot.value.resolveEditorTheme(applied).colors,
        )

        colors.primary = 0xFFABCDEFL
        assertEquals(0xFF123456L, controller.customColorsSnapshot.value?.primary?.toArgbLong())

        controller.setDarkMode(true)
        assertEquals(
            applied,
            controller.configurationSnapshot.value.resolveEditorTheme(applied).colors,
        )

        controller.clearCustomColors()
        assertNull(controller.customColorsSnapshot.value)
        assertEquals(
            CascadeEditorTheme.dark(),
            controller.configurationSnapshot.value.resolveEditorTheme(),
        )
    }
}

private fun CoreCascadeEditorColors.argbSlots(): List<Long> = listOf(
    primary.toArgbLong(),
    onPrimary.toArgbLong(),
    text.toArgbLong(),
    popupBackground.toArgbLong(),
    unknownBlockBackground.toArgbLong(),
    toolbarIcon.toArgbLong(),
    toolbarIconDisabled.toArgbLong(),
    slashItemTitle.toArgbLong(),
    slashChevron.toArgbLong(),
    unknownBlockText.toArgbLong(),
    uiDivider.toArgbLong(),
    contentDivider.toArgbLong(),
    slashSelectedItem.toArgbLong(),
    inlineCodeBackground.toArgbLong(),
    highlight.toArgbLong(),
    cursor.toArgbLong(),
    textSelectionBackground.toArgbLong(),
    quoteBorder.toArgbLong(),
    quoteBackground.toArgbLong(),
    selectionOverlay.toArgbLong(),
    linkText.toArgbLong(),
    error.toArgbLong(),
    codeBlockBackground.toArgbLong(),
    toolbarBackground.toArgbLong(),
)

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFF_FFFFL
