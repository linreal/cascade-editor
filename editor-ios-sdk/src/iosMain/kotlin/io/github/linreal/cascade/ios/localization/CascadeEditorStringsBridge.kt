@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.localization

import io.github.linreal.cascade.editor.theme.BlockLocalizedStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Localized overrides for the editor's built-in UI chrome strings.
 *
 * Every field defaults to `null`, which keeps the editor's built-in English
 * value — supply only the strings the app localizes. The type is a mutable bag
 * (rather than a value with default arguments) because Objective-C initializers
 * cannot omit arguments; configure the fields and pass the bag to
 * [CascadeEditorLocalization].
 */
@ObjCName("CascadeLocalizedStrings", exact = true)
public class CascadeLocalizedStrings public constructor() {
    /** Slash popup submenu back button label. */
    public var back: String? = null

    /** Message for unknown/unsupported blocks; receives the block's type id. */
    public var unsupportedBlock: ((typeId: String) -> String)? = null

    /** Toolbar accessibility label for the Bold button. */
    public var bold: String? = null

    /** Toolbar accessibility label for the Italic button. */
    public var italic: String? = null

    /** Toolbar accessibility label for the Underline button. */
    public var underline: String? = null

    /** Toolbar accessibility label for the Strikethrough button. */
    public var strikethrough: String? = null

    /** Toolbar accessibility label for the Inline Code button. */
    public var inlineCode: String? = null

    /** Toolbar accessibility label for the Highlight button. */
    public var highlight: String? = null

    /** Toolbar accessibility label for the Slash Command button. */
    public var slashCommand: String? = null

    /** Toolbar accessibility label for the Hide Keyboard button. */
    public var hideKeyboard: String? = null

    /** Toolbar accessibility label for the Indent Forward button. */
    public var indentForward: String? = null

    /** Toolbar accessibility label for the Indent Backward button. */
    public var indentBackward: String? = null

    /** Toolbar accessibility label for the Link button. */
    public var link: String? = null

    /** Label for applying a link from the link popup. */
    public var linkApply: String? = null

    /** Label for canceling the link popup. */
    public var linkCancel: String? = null

    /** Label for removing a link from the link popup. */
    public var linkRemove: String? = null

    /** Label for the link title field in the link popup. */
    public var linkTitle: String? = null

    /** Label for the link URL field in the link popup. */
    public var linkUrl: String? = null

    /** Validation message shown when the link URL field is left blank. */
    public var linkValidationBlankUrl: String? = null
}

/**
 * Localized display strings for one block type, keyed by [typeId].
 *
 * Applies to built-in block types and to natively registered custom blocks
 * alike; the values surface in the slash-command menu (name, description, and
 * additional search keywords — keywords are additive to the English ones, so
 * English search keeps working in any locale).
 */
@ObjCName("CascadeLocalizedBlockStrings", exact = true)
public class CascadeLocalizedBlockStrings public constructor(
    public val typeId: String,
    public val displayName: String,
    public val description: String,
    public val keywords: List<String>,
) {
    public constructor(
        typeId: String,
        displayName: String,
        description: String,
    ) : this(typeId, displayName, description, keywords = emptyList())
}

/**
 * The localization payload a Swift app supplies to
 * `CascadeEditorController.setLocalization`.
 *
 * Values are snapshotted when applied: mutating a [CascadeLocalizedStrings] bag
 * after passing it here has no effect until `setLocalization` is called again.
 */
@ObjCName("CascadeEditorLocalization", exact = true)
public class CascadeEditorLocalization public constructor(
    public val strings: CascadeLocalizedStrings?,
    public val blockStrings: List<CascadeLocalizedBlockStrings>,
) {
    public constructor() : this(strings = null, blockStrings = emptyList())

    public constructor(strings: CascadeLocalizedStrings?) : this(strings, blockStrings = emptyList())

    public constructor(blockStrings: List<CascadeLocalizedBlockStrings>) : this(strings = null, blockStrings)
}

/**
 * Resolves the curated overrides onto the editor's string set. Unset fields keep
 * the built-in English defaults, so the result is always complete.
 */
internal fun CascadeEditorLocalization.toEditorStrings(): CascadeEditorStrings {
    val defaults = CascadeEditorStrings.default()
    val overrides = strings ?: return defaults
    return CascadeEditorStrings(
        back = overrides.back ?: defaults.back,
        unsupportedBlock = overrides.unsupportedBlock ?: defaults.unsupportedBlock,
        bold = overrides.bold ?: defaults.bold,
        italic = overrides.italic ?: defaults.italic,
        underline = overrides.underline ?: defaults.underline,
        strikethrough = overrides.strikethrough ?: defaults.strikethrough,
        inlineCode = overrides.inlineCode ?: defaults.inlineCode,
        highlight = overrides.highlight ?: defaults.highlight,
        slashCommand = overrides.slashCommand ?: defaults.slashCommand,
        hideKeyboard = overrides.hideKeyboard ?: defaults.hideKeyboard,
        indentForward = overrides.indentForward ?: defaults.indentForward,
        indentBackward = overrides.indentBackward ?: defaults.indentBackward,
        link = overrides.link ?: defaults.link,
        linkApply = overrides.linkApply ?: defaults.linkApply,
        linkCancel = overrides.linkCancel ?: defaults.linkCancel,
        linkRemove = overrides.linkRemove ?: defaults.linkRemove,
        linkTitle = overrides.linkTitle ?: defaults.linkTitle,
        linkUrl = overrides.linkUrl ?: defaults.linkUrl,
        linkValidationError = overrides.linkValidationBlankUrl?.let { message ->
            { _ -> message }
        } ?: defaults.linkValidationError,
    )
}

/**
 * Merges the localized block entries over the editor's default English set.
 * Entries for type ids the editor does not know yet (e.g. custom blocks) are
 * carried through so their slash items localize too.
 */
internal fun CascadeEditorLocalization.toEditorBlockStrings(): CascadeEditorBlockStrings {
    val defaults = CascadeEditorBlockStrings.default()
    if (blockStrings.isEmpty()) return defaults
    val merged = defaults.blocks.toMutableMap()
    for (entry in blockStrings) {
        merged[entry.typeId] = BlockLocalizedStrings(
            displayName = entry.displayName,
            description = entry.description,
            keywords = entry.keywords,
        )
    }
    return CascadeEditorBlockStrings(merged)
}
