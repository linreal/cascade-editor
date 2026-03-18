package io.github.linreal.cascade.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandItem

/**
 * Pure utility functions and constants for the slash command popup.
 */
internal object SlashPopupDefaults {

    /** Maximum popup height in dp. */
    const val MAX_HEIGHT_DP: Int = 150

    /** Popup width in dp. */
    const val WIDTH_DP: Int = 280

    /** Gap between caret and popup edge in dp. */
    const val CARET_GAP_DP: Float = 4f

    /** Padding inside the popup in dp. */
    const val CONTENT_PADDING_DP: Int = 8

    /** Row height in dp. */
    const val ROW_HEIGHT_DP: Int = 32

    /** Back header height in dp. */
    const val BACK_HEADER_HEIGHT_DP: Int = 40

    /** Approximate fallback row height for an empty-state label in dp. */
    const val EMPTY_STATE_HEIGHT_DP: Int = 32

    /**
     * Calculates the popup offset relative to the popup container origin.
     *
     * Places the popup below the caret. If insufficient space below, flips above.
     * The X coordinate follows the caret's left edge, clamped so the popup stays
     * fully within the viewport width.
     *
     * @param caretRect The caret rectangle in the popup container's coordinate space.
     * @param popupHeight The measured or estimated popup height in px.
     * @param popupWidth The popup width in px.
     * @param viewportHeight The available viewport height in px.
     * @param viewportWidth The available viewport width in px.
     * @param gap Vertical gap between caret and popup edge.
     * @return The top-left offset for the popup.
     */
    fun calculatePopupOffset(
        caretRect: Rect,
        popupHeight: Float,
        popupWidth: Float,
        viewportHeight: Float,
        viewportWidth: Float,
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
        val x = caretRect.left.coerceIn(0f, (viewportWidth - popupWidth).coerceAtLeast(0f))

        return Offset(x, y)
    }

    /**
     * Resolves the next highlighted item ID for Up/Down keyboard navigation.
     *
     * @param currentId The currently highlighted item ID, or null if none.
     * @param flatItems The flat list of items in display order.
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
     * Estimates popup height in dp from item count.
     *
     * Used for placement decisions so short lists do not flip above unnecessarily.
     */
    fun estimatePopupHeightDp(
        itemCount: Int,
        hasBackHeader: Boolean,
    ): Int {
        var totalDp = CONTENT_PADDING_DP * 2

        if (hasBackHeader) {
            totalDp += BACK_HEADER_HEIGHT_DP + 1 // header + divider
        }

        if (itemCount == 0) {
            totalDp += EMPTY_STATE_HEIGHT_DP
            return totalDp.coerceAtMost(MAX_HEIGHT_DP)
        }

        totalDp += itemCount * ROW_HEIGHT_DP

        return totalDp.coerceAtMost(MAX_HEIGHT_DP)
    }
}
