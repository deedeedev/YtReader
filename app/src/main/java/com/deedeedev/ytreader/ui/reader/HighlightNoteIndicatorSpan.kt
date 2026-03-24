package com.deedeedev.ytreader.ui.reader

import android.graphics.Color
import android.graphics.Paint
import android.text.style.LineBackgroundSpan

internal class HighlightNoteIndicatorSpan(
    private val spanStart: Int,
    private val spanEndExclusive: Int
) : LineBackgroundSpan {

    override fun drawBackground(
        canvas: android.graphics.Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {
        if (spanEndExclusive <= spanStart) return
        val lastCharacterOffset = (spanEndExclusive - 1).coerceAtLeast(spanStart)
        if (lastCharacterOffset !in start until end) return
        if (right <= left) return

        val originalColor = paint.color
        val originalStyle = paint.style
        paint.color = Color.argb(170, 60, 60, 60)
        paint.style = Paint.Style.FILL

        val radius = 4f
        val cx = right - radius - 3f
        val cy = top + radius + 3f
        canvas.drawCircle(cx, cy, radius, paint)

        paint.color = originalColor
        paint.style = originalStyle
    }
}
