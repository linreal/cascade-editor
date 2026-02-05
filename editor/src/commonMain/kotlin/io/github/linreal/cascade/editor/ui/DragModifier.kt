package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes an element draggable after a long press gesture.
 *
 * This modifier detects a long press to initiate drag, then tracks drag movement
 * until release or cancellation. It's designed to be reusable across different
 * draggable elements.
 *
 * ## Coordinate System
 * - `onDragStart` receives the touch position in the element's local coordinates
 * - `onDrag` receives the drag delta (movement since last callback)
 *
 * ## Usage
 * ```kotlin
 * Box(
 *     modifier = Modifier.draggableAfterLongPress(
 *         key = itemId,
 *         onDragStart = { touchPosition ->
 *             // touchPosition is where the long press occurred within this element
 *         },
 *         onDrag = { delta ->
 *             // delta is the movement since last callback
 *         },
 *         onDragEnd = {
 *             // Drag completed - commit the operation
 *         },
 *         onDragCancel = {
 *             // Drag cancelled - abort the operation
 *         }
 *     )
 * )
 * ```
 *
 * @param key Key for the pointer input scope. Should change when drag-relevant
 *        state changes to reset the gesture detector.
 * @param enabled Whether drag detection is enabled. When false, no gestures are detected.
 * @param onDragStart Called when drag starts after long press.
 *        Receives the touch position within the element (local coordinates).
 * @param onDrag Called during drag with the movement delta since the last callback.
 * @param onDragEnd Called when drag completes (finger lifted).
 * @param onDragCancel Called when drag is cancelled (e.g., gesture interrupted).
 */
public fun Modifier.draggableAfterLongPress(
    key: Any,
    enabled: Boolean = true,
    onDragStart: (touchPosition: Offset) -> Unit,
    onDrag: (delta: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = onDragEnd
): Modifier {
    if (!enabled) return this

    return this.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                onDragStart(offset)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            },
            onDragEnd = {
                onDragEnd()
            },
            onDragCancel = {
                onDragCancel()
            }
        )
    }
}
