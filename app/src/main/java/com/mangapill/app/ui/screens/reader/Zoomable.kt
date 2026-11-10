package com.mangapill.app.ui.screens.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

const val MAX_ZOOM = 5f
const val DOUBLE_TAP_ZOOM = 2.5f

/** Pinch / pan / double-tap zoom state for the reader. Scale 1 = fit; pan is clamped to the content. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    val isZoomed get() = scale > 1.01f

    fun reset() {
        scale = 1f; offsetX = 0f; offsetY = 0f
    }

    /**
     * Zoom by [zoom] around [centroid] and pan by [pan] (screen px). Returns the part of the pan that
     * could not be applied because an edge was reached, so the caller can scroll the page underneath.
     */
    fun transform(zoom: Float, pan: Offset, centroid: Offset, size: IntSize): Offset {
        val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        val z = newScale / scale
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Keep the point under the fingers fixed while scaling about the view centre.
        val wantX = (centroid.x - cx) * (1 - z) + z * offsetX + pan.x
        val wantY = (centroid.y - cy) * (1 - z) + z * offsetY + pan.y
        val maxX = size.width * (newScale - 1f) / 2f
        val maxY = size.height * (newScale - 1f) / 2f
        scale = newScale
        offsetX = wantX.coerceIn(-maxX, maxX)
        offsetY = wantY.coerceIn(-maxY, maxY)
        return Offset(wantX - offsetX, wantY - offsetY)
    }

    fun toggle(at: Offset, size: IntSize) {
        if (isZoomed) reset() else transform(DOUBLE_TAP_ZOOM, Offset.Zero, at, size)
    }
}

@Composable
fun rememberZoomState() = remember { ZoomState() }

/**
 * Makes the content zoomable: pinch, double-tap, and one-finger pan while zoomed. At scale 1 a single
 * finger is left alone so the list / pager underneath scrolls normally. When a pan hits the edge of the
 * zoomed content the leftover is passed to [onOverflow] (screen px) so the reader can keep scrolling.
 */
fun Modifier.zoomable(
    state: ZoomState,
    /** Where the tap landed and the size of the reader, so edges can turn pages. */
    onTap: (Offset, IntSize) -> Unit = { _, _ -> },
    onOverflow: (Offset) -> Unit = {}
): Modifier = this
    .pointerInput(state) {
        detectTapGestures(
            onTap = { onTap(it, size) },
            onDoubleTap = { state.toggle(it, size) }
        )
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val multiTouch = event.changes.count { it.pressed } > 1
                if (multiTouch || state.isZoomed) {
                    val zoom = if (multiTouch) event.calculateZoom() else 1f
                    val pan = event.calculatePan()
                    if (zoom != 1f || pan != Offset.Zero) {
                        val overflow = state.transform(zoom, pan, event.calculateCentroid(useCurrent = false), size)
                        if (!multiTouch && overflow != Offset.Zero) onOverflow(overflow)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offsetX
        translationY = state.offsetY
    }
