package io.github.linreal.cascade.editor.richtext

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import kotlin.math.min

/**
 * Applies span maintenance based on committed visible-text changes.
 *
 * This avoids mutating external state from InputTransformation while still
 * keeping spans coherent during typing/delete/replace/paste.
 */
internal class SpanMaintenanceTextObserver(
    private val blockId: BlockId,
    private val textStates: BlockTextStates,
    private val spanStates: BlockSpanStates,
    initialVisibleText: String,
) {
    private var previousVisibleText: String = initialVisibleText

    internal fun onCommittedVisibleText(currentVisibleText: String) {
        val expectedProgrammaticText = textStates.consumeProgrammaticCommit(blockId)
        if (expectedProgrammaticText != null) {
            if (currentVisibleText == expectedProgrammaticText) {
                previousVisibleText = currentVisibleText
                return
            }
            previousVisibleText = expectedProgrammaticText
        }

        val edit = computeEdit(previousVisibleText, currentVisibleText) ?: return
        previousVisibleText = currentVisibleText

        spanStates.adjustForUserEdit(
            blockId = blockId,
            editStart = edit.start,
            deletedLength = edit.deletedLength,
            insertedLength = edit.insertedLength,
        )

        applyPendingStyles(
            editStart = edit.start,
            insertedLength = edit.insertedLength,
            visibleTextLength = currentVisibleText.length,
        )
    }

    private fun applyPendingStyles(
        editStart: Int,
        insertedLength: Int,
        visibleTextLength: Int,
    ) {
        if (insertedLength <= 0) return

        val insertStart = editStart.coerceIn(0, visibleTextLength)
        val insertEnd = (editStart + insertedLength).coerceIn(insertStart, visibleTextLength)
        if (insertStart >= insertEnd) return

        val explicitPending = spanStates.getPendingStyles(blockId)
        if (explicitPending != null) {
            spanStates.clearPendingStyles(blockId)
        }

        val resolvedStyles = explicitPending ?: spanStates.resolveStylesForInsertion(
            blockId = blockId,
            position = insertStart,
        )
        val currentStyles = spanStates.activeStylesAt(blockId, insertStart)

        for (style in currentStyles - resolvedStyles) {
            spanStates.removeStyle(
                blockId = blockId,
                rangeStart = insertStart,
                rangeEnd = insertEnd,
                style = style,
            )
        }

        for (style in resolvedStyles - currentStyles) {
            spanStates.applyStyle(
                blockId = blockId,
                rangeStart = insertStart,
                rangeEnd = insertEnd,
                style = style,
                textLength = visibleTextLength,
            )
        }
    }

    private fun computeEdit(
        previous: String,
        current: String,
    ): TextEdit? {
        if (previous == current) return null

        val minLength = min(previous.length, current.length)
        var prefixLength = 0
        while (prefixLength < minLength && previous[prefixLength] == current[prefixLength]) {
            prefixLength++
        }

        val maxSuffix = min(
            previous.length - prefixLength,
            current.length - prefixLength,
        )
        var suffixLength = 0
        while (
            suffixLength < maxSuffix &&
            previous[previous.length - 1 - suffixLength] == current[current.length - 1 - suffixLength]
        ) {
            suffixLength++
        }

        val deletedLength = previous.length - prefixLength - suffixLength
        val insertedLength = current.length - prefixLength - suffixLength
        if (deletedLength == 0 && insertedLength == 0) return null

        return TextEdit(
            start = prefixLength,
            deletedLength = deletedLength.coerceAtLeast(0),
            insertedLength = insertedLength.coerceAtLeast(0),
        )
    }

    private data class TextEdit(
        val start: Int,
        val deletedLength: Int,
        val insertedLength: Int,
    )
}
