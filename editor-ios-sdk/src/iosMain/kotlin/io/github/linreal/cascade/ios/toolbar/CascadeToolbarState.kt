@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.toolbar

import io.github.linreal.cascade.editor.richtext.StyleStatus
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@ObjCName("CascadeStyleState", exact = true)
public enum class CascadeStyleState {
    active,
    mixed,
    inactive,
}

@ObjCName("CascadeToolbarState", exact = true)
public data class CascadeToolbarState(
    public val focused: Boolean,
    public val canFormat: Boolean,
    public val bold: CascadeStyleState,
    public val italic: CascadeStyleState,
    public val underline: CascadeStyleState,
    public val strikeThrough: CascadeStyleState,
    public val inlineCode: CascadeStyleState,
    public val highlight: CascadeStyleState,
    public val canIndentForward: Boolean,
    public val canIndentBackward: Boolean,
    public val canLink: Boolean,
    public val existingUrl: String?,
) {
    public companion object {
        public val Empty: CascadeToolbarState = CascadeToolbarState(
            focused = false,
            canFormat = false,
            bold = CascadeStyleState.inactive,
            italic = CascadeStyleState.inactive,
            underline = CascadeStyleState.inactive,
            strikeThrough = CascadeStyleState.inactive,
            inlineCode = CascadeStyleState.inactive,
            highlight = CascadeStyleState.inactive,
            canIndentForward = false,
            canIndentBackward = false,
            canLink = false,
            existingUrl = null,
        )
    }
}

internal fun StyleStatus.toCascadeStyleState(): CascadeStyleState = when (this) {
    StyleStatus.FullyActive -> CascadeStyleState.active
    StyleStatus.Partial -> CascadeStyleState.mixed
    StyleStatus.Absent -> CascadeStyleState.inactive
}
