package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.core.BlockId
import kotlin.math.max
import kotlin.math.min

/**
 * Captured text-block range used by link popup sessions and link actions.
 *
 * The range uses visible-text coordinates and half-open `[rangeStart, rangeEnd)`
 * bounds, matching [io.github.linreal.cascade.editor.core.TextSpan]. It is safe
 * to keep this value while focus or cursor position changes; mutation actions
 * clamp it against the latest visible text before applying.
 */
@Immutable
public data class LinkTarget(
    val blockId: BlockId,
    val rangeStart: Int,
    val rangeEnd: Int,
) {
    init {
        require(rangeStart >= 0) { "rangeStart must be non-negative, got $rangeStart" }
        require(rangeEnd >= 0) { "rangeEnd must be non-negative, got $rangeEnd" }
    }

    public val normalizedStart: Int
        get() = min(rangeStart, rangeEnd)

    public val normalizedEnd: Int
        get() = max(rangeStart, rangeEnd)

    public val isCollapsed: Boolean
        get() = rangeStart == rangeEnd
}

/**
 * Immutable snapshot of the current link-editing context for the focused text block.
 *
 * [target] is the current cursor/selection range. [existingLinkRange] is the full
 * resolved link span when the target is covered by exactly one link URL.
 *
 * [selectionCollapsed] and [existingLinkText] are cached convenience values
 * derived with the same visible-text snapshot used to compute [target]. Keeping
 * them on the state avoids every popup or custom toolbar slicing text again.
 *
 * [isInsideLink] is only true for the collapsed-cursor case where the cursor is
 * strictly inside a link span. Ranged selections can still expose [existingUrl]
 * without being "inside" a link.
 */
@Immutable
public data class LinkState(
    val canLink: Boolean,
    val focusedBlockId: BlockId?,
    val target: LinkTarget?,
    val targetText: String,
    val selectionCollapsed: Boolean,
    val existingUrl: String?,
    val existingLinkRange: LinkTarget?,
    val existingLinkText: String?,
    val isInsideLink: Boolean,
    val intersectsLink: Boolean,
) {
    public companion object {
        public val Empty: LinkState = LinkState(
            canLink = false,
            focusedBlockId = null,
            target = null,
            targetText = "",
            selectionCollapsed = true,
            existingUrl = null,
            existingLinkRange = null,
            existingLinkText = null,
            isInsideLink = false,
            intersectsLink = false,
        )
    }
}
