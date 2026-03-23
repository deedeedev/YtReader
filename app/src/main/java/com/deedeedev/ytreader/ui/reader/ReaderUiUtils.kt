package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.onUnconsumedTap(onTap: (ReaderTapPosition) -> Unit): Modifier =
    pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final) ?: return@awaitEachGesture
            if (!down.isConsumed && !up.isConsumed) {
                val width = size.width.coerceAtLeast(1)
                val height = size.height.coerceAtLeast(1)
                val tapPosition = ReaderTapPosition(
                    xFraction = (up.position.x / width.toFloat()).coerceIn(0f, 1f),
                    yFraction = (up.position.y / height.toFloat()).coerceIn(0f, 1f)
                )
                onTap(tapPosition)
            }
        }
    }

internal fun formatTime(millis: Long): String {
    val seconds = millis / 1000
    val m = seconds / 60
    val s = seconds % 60
    val h = m / 60
    val mm = m % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, mm, s)
    } else {
        String.format("%d:%02d", mm, s)
    }
}
