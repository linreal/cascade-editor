package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Immutable snapshot of the current formatting state for the focused text block.
 * Consumed by toolbar UI and external formatting state listeners.
 */
@Immutable
public data class FormattingState(
    val styles: Map<SpanStyle, StyleStatus>,
    val canFormat: Boolean,
    val focusedBlockId: BlockId?,
    val selectionCollapsed: Boolean,
) {
    public fun styleStatusOf(style: SpanStyle): StyleStatus =
        styles[style] ?: StyleStatus.Absent

    public companion object {
        public val Empty: FormattingState = FormattingState(
            styles = emptyMap(),
            canFormat = false,
            focusedBlockId = null,
            selectionCollapsed = true,
        )
    }
}
