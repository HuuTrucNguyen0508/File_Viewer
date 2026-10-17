package com.fileviewer.app.viewers.pdf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Two-finger pinch only: one-finger drags fall through to the scrolling list underneath.
 * Reports each scale step with its centroid, and the end of the gesture.
 */
fun Modifier.pinchToZoom(onScale: (factor: Float, centroid: Offset) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    pinching = true
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) onScale(zoom, event.calculateCentroid())
                    event.changes.forEach { it.consume() }
                }
            }
            if (pinching) onEnd()
        }
    }
