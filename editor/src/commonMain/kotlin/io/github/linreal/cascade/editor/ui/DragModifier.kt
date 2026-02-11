package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import io.github.linreal.cascade.editor.action.CancelDrag
import io.github.linreal.cascade.editor.action.CompleteDrag
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.ui.utils.calculateDropTargetIndex
import io.github.linreal.cascade.editor.ui.utils.findItemAtPosition

/**
 * Block-level drag gesture for the editor.
 *
 * Detects a long-press drag, identifies which block is under the touch
 * point via [LazyListState.layoutInfo], tracks drag position, computes
 * drop targets, and dispatches drag actions through [callbacks].
 *
 * Applied to the editor's outer Box (not individual LazyColumn items)
 * so the gesture coroutine survives item recycling during auto-scroll.
 *
 * @param lazyListState LazyColumn state for hit-testing and layout queries
 * @param dragOffsetY Mutable state tracking the current drag Y position.
 *        Updated during drag; read by [AutoScrollDuringDrag] and [DragPreview].
 * @param stateProvider Provides the current [EditorState] for drag status
 *        and block count.
 * @param callbacks Block callbacks for dispatching drag actions.
 */
internal fun Modifier.blockDragGesture(
    lazyListState: LazyListState,
    dragOffsetY: MutableFloatState,
    stateProvider: () -> EditorState,
    callbacks: BlockCallbacks,
): Modifier = this.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            val item = findItemAtPosition(lazyListState.layoutInfo, offset.y)
            if (item != null) {
                val blockId = BlockId(item.key as String)
                val touchWithinBlock = offset.y - item.offset
                dragOffsetY.floatValue = offset.y
                callbacks.onDragStart(blockId, touchWithinBlock)
            }
        },
        onDrag = { change, dragAmount ->
            change.consume()
            if (stateProvider().dragState != null) {
                dragOffsetY.floatValue += dragAmount.y
                val newTarget = calculateDropTargetIndex(
                    lazyListState.layoutInfo,
                    dragOffsetY.floatValue,
                    stateProvider().blocks.size
                )
                callbacks.dispatch(UpdateDragTarget(newTarget))
            }
        },
        onDragEnd = {
            if (stateProvider().dragState != null) {
                callbacks.dispatch(CompleteDrag)
            }
        },
        onDragCancel = {
            if (stateProvider().dragState != null) {
                callbacks.dispatch(CancelDrag)
            }
        }
    )
}

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
    return this.pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
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
