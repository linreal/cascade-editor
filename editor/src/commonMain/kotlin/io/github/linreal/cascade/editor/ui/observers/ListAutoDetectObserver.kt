package io.github.linreal.cascade.editor.ui.observers

import io.github.linreal.cascade.editor.core.BlockType

/**
 * Observes committed visible-text changes and detects list trigger patterns.
 *
 * Bullet trigger: `- ` (dash + space) at position 0, fired by a single Space insertion.
 * Numbered trigger: `\d+. ` (digits + dot + space) at position 0, fired by a single Space insertion.
 *
 * Only single-character Space insertions trigger detection. Paste, programmatic changes,
 * and edits in blocks that are already list types are excluded.
 *
 * @param isListBlock Returns true if the current block is already a list type.
 * @param onListDetected Called when a list pattern is detected. Receives the [BlockType]
 *        to convert to and the number of characters to remove from the start (the trigger prefix).
 * @param initialVisibleText The visible text at observer creation time.
 */
internal class ListAutoDetectObserver(
    private val isListBlock: () -> Boolean,
    private val onListDetected: (newType: BlockType, prefixLength: Int) -> Unit,
    initialVisibleText: String,
) {
    private var previousVisibleText: String = initialVisibleText

    /**
     * Processes a committed visible-text change.
     *
     * @param currentVisibleText The new visible text (without ZWSP sentinel).
     * @param isProgrammatic True when a programmatic commit is pending for this block.
     */
    internal fun onTextChanged(
        currentVisibleText: String,
        isProgrammatic: Boolean,
    ) {
        val prev = previousVisibleText
        previousVisibleText = currentVisibleText

        // Never trigger on programmatic changes or if already a list block.
        if (isProgrammatic) return
        if (isListBlock()) return

        val edit = computeEdit(prev, currentVisibleText) ?: return

        // Only trigger on single Space insertion (no deletion, no paste).
        if (edit.deletedLength != 0 || edit.insertedLength != 1) return
        if (currentVisibleText[edit.start] != ' ') return

        // Check bullet trigger: "- " at position 0
        if (edit.start == 1 && currentVisibleText.startsWith("- ")) {
            onListDetected(BlockType.BulletList, 2)
            return
        }

        // Check numbered trigger: "N. " at position 0, where N >= 1
        val dotIndex = currentVisibleText.indexOf('.')
        if (dotIndex > 0 && edit.start == dotIndex + 1 && currentVisibleText.length > dotIndex + 1) {
            val prefix = currentVisibleText.substring(0, dotIndex)
            val number = prefix.toIntOrNull()
            if (number != null && number >= 1 && currentVisibleText[dotIndex + 1] == ' ') {
                onListDetected(BlockType.NumberedList(number), dotIndex + 2)
                return
            }
        }
    }

    private fun computeEdit(previous: String, current: String): TextEdit? {
        if (previous == current) return null

        val minLength = minOf(previous.length, current.length)
        var prefixLength = 0
        while (prefixLength < minLength && previous[prefixLength] == current[prefixLength]) {
            prefixLength++
        }

        val maxSuffix = minOf(
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
