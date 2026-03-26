package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import io.github.linreal.cascade.editor.action.CancelDrag
import io.github.linreal.cascade.editor.action.CompleteDrag
import io.github.linreal.cascade.editor.action.ToggleBlockSelection
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.ui.utils.calculateDropTargetIndex
import io.github.linreal.cascade.editor.ui.utils.findItemAtPosition
import kotlinx.coroutines.CancellationException

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
 * @param longPressTimeoutMillis Custom long-press timeout in milliseconds.
 *        If null, uses the platform default from ViewConfiguration.
 */
internal fun Modifier.blockDragGesture(
    lazyListState: LazyListState,
    dragOffsetY: MutableFloatState,
    stateProvider: () -> EditorState,
    callbacks: BlockCallbacks,
    longPressTimeoutMillis: Long? = null,
): Modifier = this.pointerInput(Unit) {
    var longPressedBlockId: BlockId? = null
    val timeout = longPressTimeoutMillis ?: viewConfiguration.longPressTimeoutMillis

    fun finishDrag(fallbackAction: () -> Unit) {
        val blockId = longPressedBlockId
        longPressedBlockId = null
        val dragState = stateProvider().dragState
        if (dragState != null) {
            if (blockId != null && isDropAtOriginalPosition(
                    dragState.targetIndex,
                    dragState.primaryBlockOriginalIndex
                )
            ) {
                callbacks.dispatch(CancelDrag)
                callbacks.dispatch(ToggleBlockSelection(blockId))
            } else {
                fallbackAction()
            }
        }
    }

    detectDragAfterLongPress(
        longPressTimeoutMillis = timeout,
        onTap = { offset ->
            if (stateProvider().hasSelection) {
                val item = findItemAtPosition(lazyListState.layoutInfo, offset.y)
                val blockId = (item?.key as? String)?.let(::BlockId)
                if (blockId != null) {
                    callbacks.dispatch(ToggleBlockSelection(blockId))
                }
                true
            } else {
                false
            }
        },
        onDragStart = { offset ->
            longPressedBlockId = null

            val item = findItemAtPosition(lazyListState.layoutInfo, offset.y)
            val blockId = (item?.key as? String)?.let(::BlockId)
            if (item != null && blockId != null) {
                longPressedBlockId = blockId
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
        onDragEnd = { finishDrag { callbacks.dispatch(CompleteDrag) } },
        onDragCancel = { finishDrag { callbacks.dispatch(CancelDrag) } },
    )
}

/**
 * Returns `true` if the drop target is the same as the block's original position,
 * meaning the block was not moved and the gesture should be treated as a selection toggle.
 *
 * A visual gap of N or N+1 for a block at index N both mean "same position" because
 * gap N is "before this block" and gap N+1 is "after this block" — neither results
 * in actual movement.
 */
internal fun isDropAtOriginalPosition(targetIndex: Int?, originalIndex: Int): Boolean {
    if (targetIndex == null) return true
    return targetIndex == originalIndex || targetIndex == originalIndex + 1
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
 * @param longPressTimeoutMillis Custom long-press timeout in milliseconds.
 *        If null, uses the platform default from ViewConfiguration.
 * @param onDragStart Called when drag starts after long press.
 *        Receives the touch position within the element (local coordinates).
 * @param onDrag Called during drag with the movement delta since the last callback.
 * @param onDragEnd Called when drag completes (finger lifted).
 * @param onDragCancel Called when drag is cancelled (e.g., gesture interrupted).
 */
public fun Modifier.draggableAfterLongPress(
    key: Any,
    enabled: Boolean = true,
    longPressTimeoutMillis: Long? = null,
    onDragStart: (touchPosition: Offset) -> Unit,
    onDrag: (delta: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = onDragEnd
): Modifier {
    return this.pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
        val timeout = longPressTimeoutMillis ?: viewConfiguration.longPressTimeoutMillis
        detectDragAfterLongPress(
            longPressTimeoutMillis = timeout,
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

/**
 * Detects drag gestures after a long press with a configurable timeout.
 *
 * Mirrors the behavior of Compose Foundation's detectDragGesturesAfterLongPress
 * but allows specifying a custom [longPressTimeoutMillis] instead of relying on
 * the platform default.
 */
private suspend fun PointerInputScope.detectDragAfterLongPress(
    longPressTimeoutMillis: Long,
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onTap: ((Offset) -> Boolean)? = null,
) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitCustomLongPress(down.id, longPressTimeoutMillis)
            if (longPress != null) {
                onDragStart(longPress.position)
                if (
                    drag(longPress.id) {
                        onDrag(it, it.positionChange())
                        it.consume()
                    }
                ) {
                    currentEvent.changes.forEach { if (it.changedToUp()) it.consume() }
                    onDragEnd()
                } else {
                    // Long-press can end up in cancel path (e.g. no actual move on some
                    // platforms)
                    // Consume the release so child tap handlers don't request
                    // focus while we convert this gesture into block selection
                    currentEvent.changes.forEach { if (it.changedToUpIgnoreConsumed()) it.consume() }
                    onDragCancel()
                }
            } else if (onTap != null) {
                // Only treat as a tap if the finger was actually lifted.
                // If scroll consumed the pointer events instead, this is
                // a scroll gesture
                val wasFingerLifted = currentEvent.changes.any { it.changedToUpIgnoreConsumed() }
                if (wasFingerLifted && onTap(down.position)) {
                    currentEvent.changes.forEach { it.consume() }
                }
            }
        } catch (c: CancellationException) {
            onDragCancel()
            throw c
        }
    }
}

/**
 * Waits for a long press with a custom timeout.
 *
 * Returns the [PointerInputChange] at the long-press point if the pointer is held
 * down for [timeoutMillis] without being lifted or consumed. Returns null if the
 * gesture is cancelled before the timeout.
 */
private suspend fun AwaitPointerEventScope.awaitCustomLongPress(
    pointerId: PointerId,
    timeoutMillis: Long,
): PointerInputChange? {
    val initialDown = currentEvent.changes.firstOrNull { it.id == pointerId } ?: return null
    if (!initialDown.pressed) return null

    var longPress: PointerInputChange? = null
    var currentDown = initialDown

    return try {
        withTimeout(timeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)

                if (event.changes.all { it.changedToUpIgnoreConsumed() }) {
                    break
                }
                if (event.changes.any { it.isConsumed }) {
                    break
                }

                val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
                if (consumeCheck.changes.any { it.isConsumed }) {
                    break
                }

                if (!event.changes.any { it.id == currentDown.id && it.pressed }) {
                    val newPressed = event.changes.firstOrNull { it.pressed }
                    if (newPressed != null) {
                        currentDown = newPressed
                        longPress = currentDown
                    } else {
                        break
                    }
                } else {
                    longPress = event.changes.firstOrNull { it.id == currentDown.id }
                }
            }
        }
        null
    } catch (_: PointerEventTimeoutCancellationException) {
        longPress ?: initialDown
    }
}
