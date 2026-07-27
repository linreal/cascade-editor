@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.linreal.cascade.editor.theme.CascadeEditorColors as CoreCascadeEditorColors
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Complete Swift-facing color palette for CascadeEditor.
 *
 * Construct from the built-in light or dark preset, customize any fields, then
 * apply it through `CascadeEditorController.setColors` or
 * `CascadeDocumentPreviewController.setColors`. Every value is a 32-bit
 * `0xAARRGGBB` color stored in a Swift-friendly `Int64`.
 *
 * Values are snapshotted when applied: mutating this bag afterwards has no
 * effect until `setColors` is called again.
 */
@ObjCName("CascadeEditorColors", exact = true)
public class CascadeEditorColors public constructor(
    isDark: Boolean,
) {
    public constructor() : this(isDark = false)

    private val preset: CoreCascadeEditorColors =
        if (isDark) CoreCascadeEditorColors.dark() else CoreCascadeEditorColors.light()

    /** Accent color: active toolbar background, drop indicator, and popup actions. */
    public var primary: Long = preset.primary.toBridgeArgb()

    /** Content rendered on [primary]-colored surfaces. */
    public var onPrimary: Long = preset.onPrimary.toBridgeArgb()

    /** Default body, heading, and list text. */
    public var text: Long = preset.text.toBridgeArgb()

    /** Slash-command popup background. */
    public var popupBackground: Long = preset.popupBackground.toBridgeArgb()

    /** Unknown/error block background. */
    public var unknownBlockBackground: Long = preset.unknownBlockBackground.toBridgeArgb()

    /** Enabled formatting-toolbar icon. */
    public var toolbarIcon: Long = preset.toolbarIcon.toBridgeArgb()

    /** Disabled formatting-toolbar icon. */
    public var toolbarIconDisabled: Long = preset.toolbarIconDisabled.toBridgeArgb()

    /** Slash-command row title. */
    public var slashItemTitle: Long = preset.slashItemTitle.toBridgeArgb()

    /** Slash-command submenu chevron. */
    public var slashChevron: Long = preset.slashChevron.toBridgeArgb()

    /** Unknown block message text. */
    public var unknownBlockText: Long = preset.unknownBlockText.toBridgeArgb()

    /** Toolbar and slash-popup divider lines. */
    public var uiDivider: Long = preset.uiDivider.toBridgeArgb()

    /** Divider block line. */
    public var contentDivider: Long = preset.contentDivider.toBridgeArgb()

    /** Selected slash-command row background. */
    public var slashSelectedItem: Long = preset.slashSelectedItem.toBridgeArgb()

    /** Inline-code span background. */
    public var inlineCodeBackground: Long = preset.inlineCodeBackground.toBridgeArgb()

    /** Default rich-text highlight background. */
    public var highlight: Long = preset.highlight.toBridgeArgb()

    /** Text cursor/caret. */
    public var cursor: Long = preset.cursor.toBridgeArgb()

    /** Text selection background. */
    public var textSelectionBackground: Long = preset.textSelectionBackground.toBridgeArgb()

    /** Quote block leading border. */
    public var quoteBorder: Long = preset.quoteBorder.toBridgeArgb()

    /** Quote block background. */
    public var quoteBackground: Long = preset.quoteBackground.toBridgeArgb()

    /** Overlay behind selected blocks. */
    public var selectionOverlay: Long = preset.selectionOverlay.toBridgeArgb()

    /** Link text. */
    public var linkText: Long = preset.linkText.toBridgeArgb()

    /** Validation and error messages. */
    public var error: Long = preset.error.toBridgeArgb()

    /** Full code-block background. */
    public var codeBlockBackground: Long = preset.codeBlockBackground.toBridgeArgb()

    /** Floating formatting-toolbar surface. */
    public var toolbarBackground: Long = preset.toolbarBackground.toBridgeArgb()

    /** Empty-document input hint text. */
    public var placeholderText: Long = preset.placeholderText.toBridgeArgb()

    internal fun snapshot(): CoreCascadeEditorColors = CoreCascadeEditorColors(
        primary = primary.toComposeColor(),
        onPrimary = onPrimary.toComposeColor(),
        text = text.toComposeColor(),
        popupBackground = popupBackground.toComposeColor(),
        unknownBlockBackground = unknownBlockBackground.toComposeColor(),
        toolbarIcon = toolbarIcon.toComposeColor(),
        toolbarIconDisabled = toolbarIconDisabled.toComposeColor(),
        slashItemTitle = slashItemTitle.toComposeColor(),
        slashChevron = slashChevron.toComposeColor(),
        unknownBlockText = unknownBlockText.toComposeColor(),
        uiDivider = uiDivider.toComposeColor(),
        contentDivider = contentDivider.toComposeColor(),
        slashSelectedItem = slashSelectedItem.toComposeColor(),
        inlineCodeBackground = inlineCodeBackground.toComposeColor(),
        highlight = highlight.toComposeColor(),
        cursor = cursor.toComposeColor(),
        textSelectionBackground = textSelectionBackground.toComposeColor(),
        quoteBorder = quoteBorder.toComposeColor(),
        quoteBackground = quoteBackground.toComposeColor(),
        selectionOverlay = selectionOverlay.toComposeColor(),
        linkText = linkText.toComposeColor(),
        error = error.toComposeColor(),
        codeBlockBackground = codeBlockBackground.toComposeColor(),
        toolbarBackground = toolbarBackground.toComposeColor(),
        placeholderText = placeholderText.toComposeColor(),
    )
}

private fun Color.toBridgeArgb(): Long = toArgb().toLong() and 0xFFFF_FFFFL

private fun Long.toComposeColor(): Color = Color((this and 0xFFFF_FFFFL).toInt())
