package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.richtext.LinkValidationError

private const val DEFAULT_INDENT_FORWARD_LABEL = "Indent Forward"
private const val DEFAULT_INDENT_BACKWARD_LABEL = "Indent Backward"
private const val DEFAULT_LINK_LABEL = "Link"
private const val DEFAULT_LINK_APPLY_LABEL = "Apply Link"
private const val DEFAULT_LINK_CANCEL_LABEL = "Cancel"
private const val DEFAULT_LINK_REMOVE_LABEL = "Remove Link"
private const val DEFAULT_LINK_TITLE_LABEL = "Title"
private const val DEFAULT_LINK_URL_LABEL = "URL"

private val DEFAULT_LINK_VALIDATION_ERROR_LABEL: (LinkValidationError) -> String = { error ->
    when (error) {
        LinkValidationError.Blank -> "Enter a URL."
    }
}

/**
 * User-facing strings for the CascadeEditor UI chrome.
 *
 * Covers toolbar accessibility labels, slash popup chrome, and error
 * messages. Block names/descriptions live in [CascadeEditorBlockStrings].
 *
 * Use [default] for the built-in English preset.
 */
@Immutable
public data class CascadeEditorStrings(
    /** Slash popup submenu back button label. */
    val back: String,
    /** Error message for unknown/unsupported blocks. Receives the block's typeId. */
    val unsupportedBlock: (typeId: String) -> String,
    /** Toolbar accessibility label for the Bold button. */
    val bold: String,
    /** Toolbar accessibility label for the Italic button. */
    val italic: String,
    /** Toolbar accessibility label for the Underline button. */
    val underline: String,
    /** Toolbar accessibility label for the Strikethrough button. */
    val strikethrough: String,
    /** Toolbar accessibility label for the Inline Code button. */
    val inlineCode: String,
    /** Toolbar accessibility label for the Highlight button. */
    val highlight: String,
    /** Toolbar accessibility label for the Slash Command button. */
    val slashCommand: String,
    /** Toolbar accessibility label for the Hide Keyboard button. */
    val hideKeyboard: String,
    /** Toolbar accessibility label for the Indent Forward button. */
    val indentForward: String = DEFAULT_INDENT_FORWARD_LABEL,
    /** Toolbar accessibility label for the Indent Backward button. */
    val indentBackward: String = DEFAULT_INDENT_BACKWARD_LABEL,
    /** Toolbar accessibility label for the Link button. */
    val link: String = DEFAULT_LINK_LABEL,
    /** Label for applying a link from link popup UI. */
    val linkApply: String = DEFAULT_LINK_APPLY_LABEL,
    /** Label for canceling link popup UI. */
    val linkCancel: String = DEFAULT_LINK_CANCEL_LABEL,
    /** Label for removing a link from link popup UI. */
    val linkRemove: String = DEFAULT_LINK_REMOVE_LABEL,
    /** Label for the link title field in link popup UI. */
    val linkTitle: String = DEFAULT_LINK_TITLE_LABEL,
    /** Label for the link URL field in link popup UI. */
    val linkUrl: String = DEFAULT_LINK_URL_LABEL,
    /** Maps stable link validation errors to localized UI text. */
    val linkValidationError: (LinkValidationError) -> String = DEFAULT_LINK_VALIDATION_ERROR_LABEL,
) {
    public companion object {
        /** Default [unsupportedBlock] lambda — extracted as a singleton for stable equality. */
        private val defaultUnsupportedBlock: (String) -> String = { typeId ->
            "Unsupported block type: $typeId"
        }

        /** Default English strings matching current hardcoded values. */
        public fun default(): CascadeEditorStrings = CascadeEditorStrings(
            back = "\u2039 Back",
            unsupportedBlock = defaultUnsupportedBlock,
            bold = "Bold",
            italic = "Italic",
            underline = "Underline",
            strikethrough = "Strikethrough",
            inlineCode = "Inline Code",
            highlight = "Highlight",
            slashCommand = "Slash Command",
            hideKeyboard = "Hide Keyboard",
            indentForward = DEFAULT_INDENT_FORWARD_LABEL,
            indentBackward = DEFAULT_INDENT_BACKWARD_LABEL,
            link = DEFAULT_LINK_LABEL,
            linkApply = DEFAULT_LINK_APPLY_LABEL,
            linkCancel = DEFAULT_LINK_CANCEL_LABEL,
            linkRemove = DEFAULT_LINK_REMOVE_LABEL,
            linkTitle = DEFAULT_LINK_TITLE_LABEL,
            linkUrl = DEFAULT_LINK_URL_LABEL,
            linkValidationError = DEFAULT_LINK_VALIDATION_ERROR_LABEL,
        )
    }
}
