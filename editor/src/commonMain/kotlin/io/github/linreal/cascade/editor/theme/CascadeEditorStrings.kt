package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.Immutable

private const val DEFAULT_INDENT_FORWARD_LABEL = "Indent Forward"
private const val DEFAULT_INDENT_BACKWARD_LABEL = "Indent Backward"

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
        )
    }
}
