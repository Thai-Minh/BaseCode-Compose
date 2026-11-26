package com.looper.base.screen.common.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * Long-press then drag: once a press that starts where [startsIn] accepts is held still for the
 * long-press timeout, [onDragStart] is called and every following move is reported to [onDrag]
 * (pixels) until the finger lifts.
 *
 * Meant for a container that doesn't move itself (e.g. the stage around a movable window), so
 * drag amounts aren't affected by the dragged element moving under the finger. Works on the
 * Initial pass, before the children: taps and short presses still reach them (e.g. a click that
 * picks another video, or a platform view), but once dragging starts the gesture is consumed so
 * they don't react to it too. The gesture restarts when [key] changes.
 */
fun Modifier.dragAfterLongPress(
    key: Any?,
    startsIn: (Offset) -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!startsIn(down.position)) return@awaitEachGesture

        // Non-null: the press ended, moved or was taken before the long-press timeout.
        val notLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                ) {
                    break
                }
            }
            true
        }
        if (notLongPress != null) return@awaitEachGesture

        onDragStart()
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == down.id } ?: break
            val delta = change.positionChange()
            change.consume()
            if (!change.pressed) break
            if (delta != Offset.Zero) onDrag(delta)
        }
    }
}
