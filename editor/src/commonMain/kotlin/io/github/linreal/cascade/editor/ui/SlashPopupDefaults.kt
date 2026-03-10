package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.linreal.cascade.editor.slash.SlashCommandGroup
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandItem

/**
 * Grouped slash command items for rendering in the popup.
 *
 * @property groupLabel The group header label, or null for ungrouped items.
 * @property items The items in this group, in search/registration order.
 */
@Immutable
internal data class SlashGroupedItems(
    val groupLabel: String?,
    val items: List<SlashCommandItem>,
)

/**
 * Pure utility functions and constants for the slash command popup.
 */
internal object SlashPopupDefaults {

    /** Maximum popup height in dp. */
    const val MAX_HEIGHT_DP: Int = 300

    /** Popup width in dp. */
    const val WIDTH_DP: Int = 280

    /** Gap between caret and popup edge in dp. */
    const val CARET_GAP_DP: Float = 4f

    /** Padding inside the popup in dp. */
    const val CONTENT_PADDING_DP: Int = 8

    /** Row height in dp. */
    const val ROW_HEIGHT_DP: Int = 48

    /** Back header height in dp. */
    const val BACK_HEADER_HEIGHT_DP: Int = 40

    /** Approximate group label row height in dp. */
    const val GROUP_HEADER_HEIGHT_DP: Int = 20

    /** Approximate visual height added by group divider + vertical padding in dp. */
    const val GROUP_DIVIDER_BLOCK_HEIGHT_DP: Int = 9

    /** Approximate fallback row height for an empty-state label in dp. */
    const val EMPTY_STATE_HEIGHT_DP: Int = 32

    /**
     * Groups items by [SlashCommandGroup.order] then [SlashCommandGroup.label].
     * Items with null group come first with a null label.
     * Within each group, items retain their original order (search/registration order).
     */
    fun groupSlashItems(items: List<SlashCommandItem>): List<SlashGroupedItems> {
        if (items.isEmpty()) return emptyList()

        val ungrouped = mutableListOf<SlashCommandItem>()
        val grouped = linkedMapOf<SlashCommandGroup, MutableList<SlashCommandItem>>()

        for (item in items) {
            val group = item.group
            if (group == null) {
                ungrouped.add(item)
            } else {
                grouped.getOrPut(group) { mutableListOf() }.add(item)
            }
        }

        val result = mutableListOf<SlashGroupedItems>()

        if (ungrouped.isNotEmpty()) {
            result.add(SlashGroupedItems(groupLabel = null, items = ungrouped))
        }

        val sortedGroups = grouped.entries.sortedWith(
            compareBy<Map.Entry<SlashCommandGroup, MutableList<SlashCommandItem>>> { it.key.order }
                .thenBy { it.key.label }
        )

        for ((group, groupItems) in sortedGroups) {
            result.add(SlashGroupedItems(groupLabel = group.label, items = groupItems))
        }

        return result
    }

    /**
     * Calculates the popup offset relative to the popup container origin.
     *
     * Places the popup below the caret. If insufficient space below, flips above.
     * The X coordinate follows the caret's left edge.
     *
     * @param caretRect The caret rectangle in the popup container's coordinate space.
     * @param popupHeight The measured or estimated popup height.
     * @param viewportHeight The available viewport height.
     * @param gap Vertical gap between caret and popup edge.
     * @return The top-left offset for the popup.
     */
    fun calculatePopupOffset(
        caretRect: Rect,
        popupHeight: Float,
        viewportHeight: Float,
        gap: Float = 0f,
    ): Offset {
        val belowY = caretRect.bottom + gap
        val aboveY = caretRect.top - gap - popupHeight

        val y = if (belowY + popupHeight <= viewportHeight) {
            belowY
        } else if (aboveY >= 0f) {
            aboveY
        } else {
            // Neither fits perfectly; prefer below, clamped to 0.
            belowY.coerceAtLeast(0f)
        }

        return Offset(caretRect.left, y)
    }

    /**
     * Resolves the next highlighted item ID for Up/Down keyboard navigation.
     *
     * @param currentId The currently highlighted item ID, or null if none.
     * @param flatItems The flat (ungrouped) list of items in display order.
     * @param direction -1 for up, +1 for down.
     * @return The new highlighted item ID, or null if the list is empty.
     */
    fun resolveNextHighlight(
        currentId: SlashCommandId?,
        flatItems: List<SlashCommandItem>,
        direction: Int,
    ): SlashCommandId? {
        if (flatItems.isEmpty()) return null

        if (currentId == null) {
            return if (direction > 0) flatItems.first().id else flatItems.last().id
        }

        val currentIndex = flatItems.indexOfFirst { it.id == currentId }
        if (currentIndex == -1) {
            return flatItems.first().id
        }

        val nextIndex = (currentIndex + direction).coerceIn(0, flatItems.lastIndex)
        return flatItems[nextIndex].id
    }

    /**
     * Estimates popup height in dp from currently visible grouped items.
     *
     * Used for placement decisions so short lists do not flip above unnecessarily.
     */
    fun estimatePopupHeightDp(
        groupedItems: List<SlashGroupedItems>,
        hasBackHeader: Boolean,
    ): Int {
        var totalDp = CONTENT_PADDING_DP * 2

        if (hasBackHeader) {
            totalDp += BACK_HEADER_HEIGHT_DP + 1 // header + divider
        }

        if (groupedItems.isEmpty()) {
            totalDp += EMPTY_STATE_HEIGHT_DP
            return totalDp.coerceAtMost(MAX_HEIGHT_DP)
        }

        groupedItems.forEachIndexed { index, group ->
            if (index > 0) {
                totalDp += GROUP_DIVIDER_BLOCK_HEIGHT_DP
            }
            if (group.groupLabel != null) {
                totalDp += GROUP_HEADER_HEIGHT_DP
            }
            totalDp += group.items.size * ROW_HEIGHT_DP
        }

        return totalDp.coerceAtMost(MAX_HEIGHT_DP)
    }
}
